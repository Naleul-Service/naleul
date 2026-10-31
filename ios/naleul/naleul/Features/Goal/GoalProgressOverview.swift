// Features/Goal/GoalProgressOverview.swift
//
// 목표형 "진행" 탭 위 칩 줄 + 합친 그래프 (DESIGN_SPEC 13.7)
//  - 칩(SM · 하나만): 전체 · 지금 어디쯤? · Task 실천률 · 루틴 실천 (기본 전체)
//  - 전체 = 주(週)마다 세 값을 % 로 한 그래프에: 지금 어디쯤?(목표까지 몇 %) · Task 실천률 · 루틴 실천률
//  - 다른 칩은 원래 카드(그래프 + 설명) 하나만
//  - goal_detail.progress_view_change { view, from_view, goal_mode }

import SwiftUI
import Charts

/// 진행 탭에서 보는 것 (분석 값 = rawValue)
enum GoalProgressViewKind: String, CaseIterable, Hashable {
    case all
    case position
    case taskRate = "task_rate"
    case routineRate = "routine_rate"

    var label: String {
        switch self {
        case .all: return "전체"
        case .position: return "지금 어디쯤?"
        case .taskRate: return "Task 실천률"
        case .routineRate: return "루틴 실천"
        }
    }
}

/// 칩 줄 (Chip SM · 하나만 고르기)
@MainActor
struct GoalProgressViewChips: View {
    @Binding var selection: GoalProgressViewKind
    let onPick: (GoalProgressViewKind, GoalProgressViewKind) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(GoalProgressViewKind.allCases, id: \.self) { k in
                    Chip(k.label, selected: selection == k, size: .sm) {
                        guard selection != k else { return }
                        UISelectionFeedbackGenerator().selectionChanged()
                        onPick(selection, k)
                        selection = k
                    }
                }
            }
        }
    }
}

/// 전체: 세 값을 한 그래프에 (주별 · 0–100%)
@MainActor
struct GoalCombinedProgressCard: View {
    let progress: GoalProgress?
    let tasks: [TimeBlockTask]
    let routines: [RoutineHeatmap]

    fileprivate static let positionName = "지금 어디쯤?"
    fileprivate static let taskName = "Task 실천률"
    fileprivate static let routineName = "루틴 실천"

    struct Point: Identifiable {
        let week: String
        let label: String
        let series: String
        let value: Double
        var id: String { series + week }
    }

    var body: some View {
        let today = progress?.today ?? KST.today()
        let pts = Self.points(progress: progress, tasks: tasks, routines: routines, today: today)
        SectionCard("한눈에 보기", aside: { Text("주별 · %") }) {
            VStack(alignment: .leading, spacing: 16) {
                summaryRow(today: today)
                if pts.isEmpty {
                    NotEnoughView()
                } else {
                    chart(pts)
                }
            }
        }
    }

    // ── 숫자 세 개 (지금 값) ──

    private func summaryRow(today: String) -> some View {
        HStack(spacing: 0) {
            stat(Self.positionName, Self.positionNow(progress), color: .brand)
            divider
            stat(Self.taskName, Self.taskRateNow(tasks, today: today), color: .accentMint)
            divider
            stat(Self.routineName, Self.routineRateNow(routines, today: today), color: .warning)
        }
    }

    private var divider: some View {
        Rectangle().fill(Color.borderSoft).frame(width: 1, height: 36)
    }

    private func stat(_ name: String, _ value: Int?, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Circle().fill(color).frame(width: 6, height: 6)
                Text(name).font(.captionMD).foregroundStyle(Color.textSecondary).lineLimit(1)
            }
            Text(value.map { "\($0)%" } ?? "–").font(.ptMono(20, .semibold)).foregroundStyle(Color.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 8)
    }

    // ── 그래프 ──

    private func chart(_ pts: [Point]) -> some View {
        Chart {
            ForEach(pts) { p in
                LineMark(x: .value("주", p.label), y: .value("%", p.value))
                    .foregroundStyle(by: .value("지표", p.series))
                    .symbol(by: .value("지표", p.series))
                    .interpolationMethod(.monotone)
            }
            RuleMark(y: .value("목표", 100))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                .foregroundStyle(Color.borderSubtle)
        }
        .chartForegroundStyleScale([
            Self.positionName: Color.brand,
            Self.taskName: Color.accentMint,
            Self.routineName: Color.warning,
        ])
        .chartYScale(domain: 0...100)
        .chartYAxis {
            AxisMarks(position: .leading, values: [0.0, 50.0, 100.0]) { v in
                AxisGridLine().foregroundStyle(Color.borderSoft)
                AxisValueLabel {
                    if let d = v.as(Double.self) { Text("\(Int(d))%").font(.captionSM) }
                }
            }
        }
        .chartXAxis {
            AxisMarks { v in
                AxisValueLabel {
                    if let w = v.as(String.self) { Text(w).font(.captionSM) }
                }
            }
        }
        .chartLegend(position: .bottom, alignment: .leading, spacing: 12)
        .frame(height: 220)
        .accessibilityLabel("지금 어디쯤 · Task 실천률 · 루틴 실천을 주별로 합친 그래프")
    }

    // MARK: 계산 (주 = KST.startOfWeek · 최근 12주)

    static func points(progress: GoalProgress?, tasks: [TimeBlockTask], routines: [RoutineHeatmap],
                       today: String) -> [Point] {
        let weeks = weekList(progress: progress, tasks: tasks, routines: routines, today: today)
        var out: [Point] = []
        for w in weeks {
            let end = min(KST.addDays(w, 6), today)
            let label = "\(KST.monthOf(w))/\(KST.dayOfMonth(w))"
            if let v = position(progress, upTo: end) {
                out.append(Point(week: w, label: label, series: positionName, value: v))
            }
            if let v = taskRate(tasks, from: w, to: end) {
                out.append(Point(week: w, label: label, series: taskName, value: v))
            }
            if let v = routineRate(routines, from: w, to: end) {
                out.append(Point(week: w, label: label, series: routineName, value: v))
            }
        }
        return out
    }

    private static func weekList(progress: GoalProgress?, tasks: [TimeBlockTask], routines: [RoutineHeatmap],
                                 today: String) -> [String] {
        var starts: [String] = []
        if let s = progress?.startDate { starts.append(s) }
        starts += tasks.compactMap { GoalDetailLogic.dayOf($0) }
        starts += routines.map(\.repeatStartDate)
        guard let first = starts.filter({ $0 <= today }).min() else { return [] }
        let last = KST.startOfWeek(today)
        var w = KST.startOfWeek(first)
        var out: [String] = []
        var guardCount = 0
        while w <= last && guardCount < 400 {
            out.append(w)
            w = KST.addDays(w, 7)
            guardCount += 1
        }
        return Array(out.suffix(12))
    }

    /// 지금 어디쯤: 수치 목표면 (기록 − 시작)/(목표 − 시작) · 아니면 완료 Task / 전체 Task
    private static func position(_ p: GoalProgress?, upTo end: String) -> Double? {
        guard let p else { return nil }
        if let m = p.metric {
            let logs = m.logs.filter { $0.date <= end }.sorted { $0.date < $1.date }
            guard let v = logs.last?.value else { return nil }
            let span = m.targetValue - m.startValue
            let pct = span == 0 ? 100 : (v - m.startValue) / span * 100
            return min(max(pct, 0), 100)
        }
        guard p.tasks.total > 0,
              let pt = p.tasks.series.filter({ $0.date <= end && $0.completed != nil }).last,
              let done = pt.completed else { return nil }
        return min(Double(done) / Double(p.tasks.total) * 100, 100)
    }

    /// Task 실천률: 그 주에 하기로 한(오늘까지) 일회성 Task 중 완료 (Task 실천률 카드 기본값과 같아요)
    private static func taskRate(_ tasks: [TimeBlockTask], from: String, to: String) -> Double? {
        var due = 0
        var done = 0
        for t in tasks where t.sourceType != .ROUTINE {
            guard let d = GoalDetailLogic.dayOf(t), d >= from, d <= to else { continue }
            due += 1
            if GoalDetailLogic.isDone(t) { done += 1 }
        }
        return due > 0 ? Double(done) / Double(due) * 100 : nil
    }

    /// 루틴 실천: 그 주 해야 했던 날(오늘까지) 중 한 날
    private static func routineRate(_ routines: [RoutineHeatmap], from: String, to: String) -> Double? {
        var due = 0
        var done = 0
        for r in routines where !r.repeatDays.isEmpty {
            let doneSet = Set(r.completedDates)
            var d = from
            while d <= to {
                if d >= r.repeatStartDate, r.repeatEndDate.map({ d <= $0 }) ?? true,
                   r.repeatDays.contains(JavaDayOfWeek.of(d)) {
                    due += 1
                    if doneSet.contains(d) { done += 1 }
                }
                d = KST.addDays(d, 1)
            }
        }
        return due > 0 ? Double(done) / Double(due) * 100 : nil
    }

    // ── 지금 값 (숫자 줄) ──

    static func positionNow(_ p: GoalProgress?) -> Int? {
        guard let p else { return nil }
        if let m = p.metric {
            if let pct = m.progressPercent { return Int(min(max(pct, 0), 100).rounded()) }
            return nil
        }
        guard p.tasks.total > 0 else { return nil }
        return Int((Double(p.tasks.completed) / Double(p.tasks.total) * 100).rounded())
    }

    static func taskRateNow(_ tasks: [TimeBlockTask], today: String) -> Int? {
        taskRate(tasks, from: "0000-00-00", to: today).map { Int($0.rounded()) }
    }

    static func routineRateNow(_ routines: [RoutineHeatmap], today: String) -> Int? {
        // 시작일부터 오늘까지 (루틴 실천 카드와 같은 기준)
        guard let first = routines.map(\.repeatStartDate).min(), first <= today else { return nil }
        return routineRate(routines, from: first, to: today).map { Int($0.rounded()) }
    }
}
