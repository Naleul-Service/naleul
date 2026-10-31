// Features/Goal/GoalRoutineHeatmapSection.swift
//
// 웹 features/goal/ui/RoutineHeatmapSection.tsx — 루틴 실천 히트맵.
// 루틴마다 "해야 하는 요일만" 줄로 그려요 (월·수·금 루틴이면 월·수·금 3줄).
// 실천한 날은 색, 지나갔는데 안 한 날은 회색, 아직 안 온 날은 옅게. 모든 루틴이 같은 주(열)를 같이 써요.

import SwiftUI

@MainActor
struct GoalRoutineHeatmapSection: View {
    /// nil = 불러오는 중
    let routines: [RoutineHeatmap]?

    /// 손가락으로 보기 좋게 웹(13)보다 크게
    fileprivate static let cell: CGFloat = 18
    private static let gap: CGFloat = 4

    fileprivate enum CellState {
        case empty, done, missed, today, future

        var label: String {
            switch self {
            case .done: return "실천"
            case .missed: return "놓침"
            case .today: return "오늘"
            case .future: return "예정"
            case .empty: return ""
            }
        }
    }

    private struct Stat {
        var due: Int
        var completed: Int
        var rate: Int?
    }

    private var today: String { KST.today() }

    private var sorted: [RoutineHeatmap] {
        (routines ?? []).filter { !$0.repeatDays.isEmpty }.sorted { $0.repeatStartDate < $1.repeatStartDate }
    }

    /// 모든 루틴이 함께 쓰는 주(열) 목록: 가장 이른 시작 주 ~ 가장 늦은 종료 주 (최대 106주)
    private func weeks(_ list: [RoutineHeatmap]) -> [String] {
        guard let firstStart = list.map(\.repeatStartDate).min() else { return [] }
        let first = KST.startOfWeek(firstStart)
        var lastEnd = today
        for r in list {
            let e = r.repeatEndDate ?? KST.addDays(today, 28)
            if e > lastEnd { lastEnd = e }
        }
        var out: [String] = []
        var w = first
        while w <= lastEnd && out.count < 106 {
            out.append(w)
            w = KST.addDays(w, 7)
        }
        return out
    }

    /// 이 루틴이 그날 해야 하는 날인지 (기간 안 + 반복 요일)
    private func isScheduled(_ r: RoutineHeatmap, _ ymd: String, _ dayIndex: Int) -> Bool {
        ymd >= r.repeatStartDate && (r.repeatEndDate.map { ymd <= $0 } ?? true)
            && r.repeatDays.contains(JavaDayOfWeek.from(index: dayIndex))
    }

    private func stat(_ r: RoutineHeatmap) -> Stat {
        let done = Set(r.completedDates)
        var due = 0
        var completed = 0
        let end = (r.repeatEndDate.map { $0 < today } ?? false) ? r.repeatEndDate! : today
        var d = r.repeatStartDate
        // 너무 긴 기간에서 멈추지 않게 안전장치 (약 3년)
        var guardCount = 0
        while d <= end && guardCount < 1200 {
            if r.repeatDays.contains(JavaDayOfWeek.of(d)) {
                due += 1
                if done.contains(d) { completed += 1 }
            } else if done.contains(d) {
                // 반복 요일이 아닌 날에 "다른 날 추가"로 한 날도 실천으로 센다
                due += 1
                completed += 1
            }
            d = KST.addDays(d, 1)
            guardCount += 1
        }
        return Stat(due: due, completed: completed, rate: due > 0 ? Int((Double(completed) / Double(due) * 100).rounded()) : nil)
    }

    var body: some View {
        if routines == nil {
            SectionCard("루틴 실천") {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 96)
            }
        } else {
            let list = sorted
            if !list.isEmpty {
                content(list)
            }
        }
    }

    private func content(_ list: [RoutineHeatmap]) -> some View {
        let cols = weeks(list)
        let stats = list.map { stat($0) }
        let allDue = stats.reduce(0) { $0 + $1.due }
        let allDone = stats.reduce(0) { $0 + $1.completed }
        let aside = allDue > 0 ? "전체 \(allDone) / \(allDue)회 · \(Int((Double(allDone) / Double(allDue) * 100).rounded()))%" : ""

        return SectionCard("루틴 실천", aside: { Text(aside) }) {
            VStack(alignment: .leading, spacing: 12) {
                Text("해야 하는 요일만 칸으로 보여줘요. 실천한 날은 색이 채워지고, 놓친 날은 회색이에요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)

                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 20) {
                            monthRow(cols)
                            ForEach(Array(list.enumerated()), id: \.element.routineId) { i, r in
                                routineBlock(r, stats[i], cols)
                            }
                        }
                        .padding(.bottom, 8)
                    }
                    .onAppear {
                        // 처음 열면 오늘이 보이게
                        let idx = (cols.firstIndex { $0 > today } ?? cols.count) - 1
                        let col = idx < 0 ? cols.count - 1 : idx
                        if col >= 0 && col < cols.count {
                            proxy.scrollTo("w-\(cols[col])", anchor: UnitPoint(x: 0.85, y: 0))
                        }
                    }
                }

                timeStatsBlock(list)

                legend
            }
        }
    }

    // MARK: 주로 실천한 시간대

    private func timeStatsBlock(_ list: [RoutineHeatmap]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("주로 실천한 시간")
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink)
            ForEach(list, id: \.routineId) { r in
                timeStatsRow(r)
            }
        }
        .padding(.top, 4)
    }

    private func timeStatsRow(_ r: RoutineHeatmap) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            VStack(alignment: .leading, spacing: 2) {
                Text(r.routineName)
                    .font(.pt(13, .medium))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                if let t = r.timeStats, let sentence = t.practiceSentence, t.practiceCount > 0 {
                    Text("\(sentence) · \(t.practiceCount)회 기록")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                } else {
                    Text("아직 시간 기록이 없어요")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                }
            }
            if let t = r.timeStats, let hourly = t.hourly, hourly.count == 24, t.practiceCount > 0 {
                hourBars(hourly, best: t)
            }
        }
    }

    private func hourBars(_ hourly: [Int], best: RoutineTimeStats) -> some View {
        let maxV = max(hourly.max() ?? 1, 1)
        let sH = Int(best.bestStart?.prefix(2) ?? "") ?? -1
        let eParts = (best.bestEnd ?? "").split(separator: ":").compactMap { Int($0) }
        // 끝 시각이 정각이면 그 시간은 포함하지 않는다 (21:00~23:00 → 21, 22시)
        let eH: Int = eParts.count == 2 ? (eParts[1] > 0 ? eParts[0] : eParts[0] - 1) : -1
        func inBest(_ h: Int) -> Bool {
            guard sH >= 0, eH >= 0 else { return false }
            return sH <= eH ? (h >= sH && h <= eH) : (h >= sH || h <= eH)
        }
        return VStack(spacing: 2) {
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(0..<24, id: \.self) { h in
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(hourly[h] == 0 ? Color.borderSubtle
                              : (inBest(h) ? Color.actionPrimary : Color.actionPrimary.opacity(0.35)))
                        .frame(maxWidth: .infinity)
                        .frame(height: hourly[h] == 0 ? 2 : max(3, 24 * CGFloat(hourly[h]) / CGFloat(maxV)))
                }
            }
            .frame(height: 24, alignment: .bottom)
            HStack {
                Text("0시"); Spacer(); Text("6시"); Spacer(); Text("12시"); Spacer(); Text("18시"); Spacer(); Text("24시")
            }
            .font(.pt(10))
            .foregroundStyle(Color.ink3)
        }
    }

    private func monthRow(_ cols: [String]) -> some View {
        HStack(spacing: Self.gap) {
            ForEach(Array(cols.enumerated()), id: \.element) { i, w in
                let showMonth = i == 0 || KST.monthOf(w) != KST.monthOf(cols[i - 1])
                Color.clear
                    .frame(width: Self.cell, height: 14)
                    .overlay(alignment: .leading) {
                        if showMonth {
                            Text("\(KST.monthOf(w))월")
                                .font(.pt(11))
                                .foregroundStyle(Color.ink3)
                                .fixedSize()
                        }
                    }
                    .id("w-\(w)")
            }
        }
        .padding(.leading, 26 + Self.gap)
    }

    private func routineBlock(_ r: RoutineHeatmap, _ s: Stat, _ cols: [String]) -> some View {
        let done = Set(r.completedDates)
        // 반복 요일 + "다른 날 추가"로 한 날이 있는 요일 (2026-10: 반복 요일이 아닌 날도 히트맵에 보이게)
        let extraDays = Set(r.completedDates.map { JavaDayOfWeek.of($0) })
        let rows = JavaDayOfWeek.allCases.filter { r.repeatDays.contains($0) || extraDays.contains($0) }
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(r.routineName).font(.pt(14, .semibold)).foregroundStyle(Color.ink)
                Text("\(s.completed) / \(s.due)회" + (s.rate.map { " · \($0)%" } ?? ""))
                    .font(.ptMono(12))
                    .foregroundStyle(Color.ink3)
                if r.currentStreak > 1 {
                    HStack(spacing: 2) {
                        Image(systemName: "flame.fill").font(.system(size: 11))
                        Text("\(r.currentStreak)회 연속")
                    }
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color(hex: "#EA580C"))
                }
            }
            if let rate = s.rate {
                ProgressBar(value: Double(rate) / 100, height: 4)
                    .frame(maxWidth: 160)
            }
            VStack(alignment: .leading, spacing: Self.gap) {
                ForEach(rows) { d in
                    HStack(spacing: Self.gap) {
                        Text(d.label)
                            .font(.pt(11))
                            .foregroundStyle(Color.ink3)
                            .frame(width: 26, alignment: .leading)
                        ForEach(cols, id: \.self) { w in
                            let ymd = KST.addDays(w, d.index)
                            cellView(state(r, ymd: ymd, dayIndex: d.index, done: done))
                        }
                    }
                }
            }
        }
    }

    private func state(_ r: RoutineHeatmap, ymd: String, dayIndex: Int, done: Set<String>) -> CellState {
        if done.contains(ymd) { return .done }
        if !isScheduled(r, ymd, dayIndex) { return .empty }
        if ymd > today { return .future }
        if ymd == today { return .today }
        return .missed
    }

    @ViewBuilder fileprivate func cellView(_ s: CellState, size: CGFloat = GoalRoutineHeatmapSection.cell) -> some View {
        let shape = RoundedRectangle(cornerRadius: size > 14 ? 4.5 : 3)
        switch s {
        case .empty:
            Color.clear.frame(width: size, height: size)
        case .done:
            shape.fill(Color.brand).frame(width: size, height: size)
        case .missed:
            shape.fill(Color.lineStrong).frame(width: size, height: size)
        case .today:
            shape.fill(Color.surface)
                .overlay(shape.strokeBorder(Color.brand, lineWidth: 1.5))
                .frame(width: size, height: size)
        case .future:
            shape.fill(Color.heatEmpty).frame(width: size, height: size)
        }
    }

    private var legend: some View {
        HStack(spacing: 12) {
            ForEach([CellState.done, .missed, .today, .future], id: \.self) { k in
                HStack(spacing: 6) {
                    cellView(k, size: 11)
                    Text(k.label)
                }
            }
        }
        .font(.pt(12))
        .foregroundStyle(Color.ink3)
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            GoalRoutineHeatmapSection(routines: GoalPreviewData.heatmap)
            GoalRoutineHeatmapSection(routines: nil)
        }
        .padding(16)
    }
    .canvasBackground()
}
