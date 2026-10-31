// Features/Habit/HabitGrassSection.swift
//
// 웹 features/habit/ui/HabitHeatmap.tsx — 습관별 잔디 (한 달씩 ‹ › 로 넘겨 봐요).
// 세로는 요일(월~일), 가로는 그 달의 주. 습관을 추가한 달부터 볼 수 있어요 (최근 12달까지 — 서버 최대 400일).
// 전체를 합친 잔디는 없어요: 습관마다 시작한 날이 달라서 합치면 "언제부터 했는지"가 안 보여요.

import SwiftUI

@MainActor
struct HabitGrassSection: View {
    let model: HabitModel

    @State private var month: String = HabitGrassSection.firstOfMonth(KST.today())

    /// 칸 크기 · 간격
    static let cell: CGFloat = 18
    static let gap: CGFloat = 3
    /// 최근 12달 + 이번 달
    private static let maxMonthsBack = 12

    nonisolated static func firstOfMonth(_ ymd: String) -> String {
        let p = KST.parts(ymd)
        return KST.ymd(year: p.y, month: p.m, day: 1)
    }

    nonisolated static func lastOfMonth(_ ymd: String) -> String {
        let p = KST.parts(ymd)
        return KST.ymd(year: p.y, month: p.m, day: KST.lastDayOfMonth(year: p.y, month: p.m))
    }

    private var today: String { KST.today() }
    private var thisMonth: String { Self.firstOfMonth(today) }

    /// 지운 습관은 빼고, 쉬는 습관은 아래로 (순서는 그대로)
    private var shown: [Habit] {
        let live = model.habits.filter { $0.status != .DELETED }
        return live.filter { !$0.isPaused } + live.filter { $0.isPaused }
    }

    /// 가장 먼저 시작한 습관의 그 달 1일부터 (최근 12달까지)
    private var from: String {
        let limit = KST.addMonths(thisMonth, -Self.maxMonthsBack)
        var earliest = today
        for h in shown where h.startDate < earliest { earliest = h.startDate }
        let first = Self.firstOfMonth(earliest)
        return first < limit ? limit : first
    }

    /// 그 달을 월요일부터 시작하는 주(열)로 — 그 달이 아닌 날은 nil
    private var weeks: [[String?]] {
        let p = KST.parts(month)
        let last = Self.lastOfMonth(month)
        return KST.monthWeeks(year: p.y, month: p.m).map { week in
            week.map { d -> String? in (d >= month && d <= last) ? d : nil }
        }
    }

    var body: some View {
        let list = shown
        let start = from
        Group {
            if !list.isEmpty {
                grassCard(list, from: start)
            }
        }
        .reloadOn([.habits], id: start) {
            if !shown.isEmpty { await model.loadHeatmap(from: start) }
        }
    }

    private func grassCard(_ list: [Habit], from start: String) -> some View {
        let canPrev = month > start
        let canNext = month < thisMonth
        let ws = weeks
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 4) {
                Text("습관별 잔디").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Spacer(minLength: 0)
                IconButton(systemName: "chevron.left", size: 15, color: canPrev ? Color.ink3 : Color.ink4, label: "지난달") {
                    if canPrev {
                        month = KST.addMonths(month, -1)
                        trackMonth("prev")
                    }
                }
                .disabled(!canPrev)
                Text(monthTitle)
                    .font(.ptMono(15, .bold))
                    .foregroundStyle(Color.ink)
                    .frame(minWidth: 44)
                IconButton(systemName: "chevron.right", size: 15, color: canNext ? Color.ink3 : Color.ink4, label: "다음 달") {
                    if canNext {
                        month = KST.addMonths(month, 1)
                        trackMonth("next")
                    }
                }
                .disabled(!canNext)
                if month != thisMonth {
                    Button("이번 달") {
                        month = thisMonth
                        trackMonth("today")
                    }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textBrand)
                        .buttonStyle(.plain)
                }
            }

            if let hm = model.heatmap {
                VStack(spacing: 0) {
                    ForEach(Array(list.enumerated()), id: \.element.habitId) { i, h in
                        if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                        HabitGrassRow(habit: h, today: today, month: month, weeks: ws, states: hm.states(of: h.habitId))
                            .padding(.vertical, 12)
                    }
                }
            } else if model.heatmapFailed {
                Text("잔디를 불러오지 못했어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .frame(maxWidth: .infinity, minHeight: 100)
            } else {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 120)
            }

            FlowLayout(spacing: 12) {
                legend("했어요") { RoundedRectangle(cornerRadius: 3).fill(Color.brand) }
                legend("놓쳤어요") { RoundedRectangle(cornerRadius: 3).fill(Color.lineStrong) }
                legend("안 하는 날") { RoundedRectangle(cornerRadius: 3).fill(Color.heatEmpty) }
                legend("쉬는 중") { HabitPausedSwatch(radius: 3) }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .onAppear {
            // 볼 수 있는 달 밖이면 맞춰요
            if month < start { month = start }
            if month > thisMonth { month = thisMonth }
        }
    }

    private func trackMonth(_ action: String) {
        let a = KST.parts(month), b = KST.parts(thisMonth)
        model.track("habit.heatmap_month_click", ["date_action": action, "months_ago": (b.y - a.y) * 12 + (b.m - a.m)])
    }

    private var monthTitle: String {
        let sameYear = KST.yearOf(month) == KST.yearOf(today)
        return (sameYear ? "" : "\(KST.yearOf(month))년 ") + "\(KST.monthOf(month))월"
    }

    private func legend<S: View>(_ label: String, @ViewBuilder swatch: () -> S) -> some View {
        HStack(spacing: 4) {
            swatch().frame(width: 11, height: 11)
            Text(label).font(.pt(11)).foregroundStyle(Color.ink3)
        }
    }
}

// MARK: - 습관 한 줄 (왼쪽 이름 · 기록, 오른쪽 그 달 잔디)

@MainActor
private struct HabitGrassRow: View {
    let habit: Habit
    let today: String
    let month: String
    let weeks: [[String?]]
    let states: [String: HabitDayState]

    private func stateAt(_ d: String) -> HabitDayState {
        if d > today { return .FUTURE }
        if d < habit.startDate { return .BEFORE_START }
        return states[d] ?? .BEFORE_START
    }

    var body: some View {
        let days: [String] = weeks.flatMap { $0.compactMap { $0 } }
        let cells: [HabitDayState] = days.map { stateAt($0) }
        let doneDays = cells.filter { $0 == .DONE }.count
        let dueDays = cells.filter { $0 == .DONE || $0 == .MISSED || $0 == .TODO }.count
        let notYet = habit.startDate > HabitGrassSection.lastOfMonth(month)

        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(habit.title)
                    .font(.pt(14, .bold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                Text(notYet ? "아직 시작 전" : (dueDays > 0 ? "이 달 \(doneDays)/\(dueDays)일" : "이 달 할 날 없음"))
                    .font(.ptMono(12))
                    .foregroundStyle(Color.ink3)
                Text("\(KST.monthOf(habit.startDate))월 \(KST.dayOfMonth(habit.startDate))일부터")
                    .font(.pt(11))
                    .foregroundStyle(Color.ink4)
                if habit.streak > 1 {
                    Text("🔥 \(habit.streak)일 연속")
                        .font(.pt(12, .semibold))
                        .foregroundStyle(GoalPalette.orange.tone.fg)
                        .padding(.top, 1)
                }
                if habit.isPaused {
                    Text("쉬는 중").font(.pt(12)).foregroundStyle(Color.ink3)
                }
            }
            .frame(width: 130, alignment: .leading)

            grid
            Spacer(minLength: 0)
        }
        .opacity(habit.isPaused ? 0.6 : 1)
        .accessibilityElement(children: .combine)
    }

    private var grid: some View {
        let size = HabitGrassSection.cell
        let gap = HabitGrassSection.gap
        return HStack(alignment: .top, spacing: gap) {
            // 요일 눈금 (월 · 수 · 금 · 일)
            VStack(alignment: .trailing, spacing: gap) {
                ForEach(0..<7, id: \.self) { i in
                    Text(i % 2 == 0 ? KST.weekdayLabel[i] : "")
                        .font(.pt(10))
                        .foregroundStyle(Color.ink4)
                        .frame(width: 12, height: size, alignment: .trailing)
                }
            }
            ForEach(weeks.indices, id: \.self) { wi in
                VStack(spacing: gap) {
                    ForEach(0..<7, id: \.self) { di in
                        if let d = weeks[wi][di] {
                            HabitGrassCell(state: stateAt(d), isToday: d == today)
                        } else {
                            Color.clear.frame(width: size, height: size)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 잔디 한 칸

@MainActor
struct HabitGrassCell: View {
    let state: HabitDayState
    var isToday = false
    var size: CGFloat = HabitGrassSection.cell
    var radius: CGFloat = 3

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius)
        ZStack {
            switch state {
            case .DONE:
                shape.fill(Color.brand)
            case .MISSED:
                shape.fill(Color.lineStrong)
            case .TODO:
                shape.fill(Color.surface)
                shape.strokeBorder(Color.brand, lineWidth: 1.5)
            case .OFF:
                shape.fill(Color.heatEmpty)
            case .PAUSED:
                HabitPausedSwatch(radius: radius)
            case .BEFORE_START:
                shape.fill(Color.subtle.opacity(0.4))
            case .FUTURE:
                shape.fill(Color.clear)
                shape.strokeBorder(Color.line, lineWidth: 1)
            }
        }
        .frame(width: size, height: size)
        .overlay {
            if isToday {
                RoundedRectangle(cornerRadius: radius + 1.5)
                    .stroke(Color.ink2, lineWidth: 1)
                    .padding(-2)
            }
        }
    }
}

/// 쉬는 중 칸 — 연한 바탕 + 빗금 + 테두리
@MainActor
struct HabitPausedSwatch: View {
    var radius: CGFloat = 3

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius)
        ZStack {
            shape.fill(Color.hatch)
            Canvas { ctx, size in
                var p = Path()
                var x: CGFloat = -size.height
                while x < size.width {
                    p.move(to: CGPoint(x: x, y: size.height))
                    p.addLine(to: CGPoint(x: x + size.height, y: 0))
                    x += 4
                }
                ctx.stroke(p, with: .color(Color.hatchLine), lineWidth: 1)
            }
            .clipShape(shape)
            shape.strokeBorder(Color.hatchLine, lineWidth: 1)
        }
    }
}

#Preview {
    ScrollView {
        HabitGrassSection(model: HabitModel.preview()).padding(16)
    }
    .canvasBackground()
}
