// Features/Pattern/PatternUsageCards.swift
//
// 웹 features/pattern/ui/TimeUsage.tsx · BlockKeeping.tsx · RoutineLists.tsx · TaskHabits.tsx
// 리포트 아래쪽 카드들 (시간 사용 · 블록 지키기 · 루틴 · Task 처리 습관)

import SwiftUI
import UIKit

// MARK: - 어디에 시간을 쓰고 있나요? (목표별 주 평균 실제 시간, 세로선 = 계획)

struct PatternTimeUsageCard: View {
    let usage: PatternTimeUsage?

    var body: some View {
        let goals = usage?.goals ?? []
        let maxHours = goals.map { max($0.actualHoursPerWeek, $0.plannedHoursPerWeek) }.max().map { max(1, $0) } ?? 1
        SectionCard("어디에 시간을 쓰고 있나요?", aside: {
            if let usage, !goals.isEmpty { Text("주 평균 \(PatternFormat.hours1(usage.weeklyTotalHours))h · ┃ 계획") }
        }) {
            if goals.isEmpty {
                NotEnoughView(text: "목표에 연결된 Task를 실행하면 보여드려요")
            } else {
                VStack(alignment: .leading, spacing: 20) {
                    ForEach(goals, id: \.goalCategoryId) { g in
                        PatternGoalUsageRow(goal: g, maxHours: maxHours)
                            .patternChartTap("time_usage")
                            .patternDeadTap("time_usage_row")
                    }
                }
            }
        }
    }
}

private struct PatternGoalUsageRow: View {
    let goal: PatternGoalUsage
    let maxHours: Double

    private var ratioText: String {
        if goal.newTemporary { return "새 임시 목표" }
        if let r = goal.planRatio { return "계획의 \(r)%" }
        return "계획 없음"
    }

    var body: some View {
        let color = goal.colorCode.map { Color.goal($0) } ?? Color.brand
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text((goal.emoji.map { "\($0) " } ?? "") + goal.name)
                    .font(.pt(15, .bold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                Spacer(minLength: 0)
                (Text("\(PatternFormat.hours1(goal.actualHoursPerWeek))h").font(.pt(16, .semibold)).foregroundColor(Color.ink)
                 + Text(" / 주 · \(ratioText)"))
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .lineLimit(1)
                    .layoutPriority(1)
            }
            GeometryReader { geo in
                let w = geo.size.width
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.progressTrack).frame(height: 12)
                    Capsule().fill(color).frame(width: w * CGFloat(goal.actualHoursPerWeek / maxHours), height: 12)
                    if goal.plannedHoursPerWeek > 0 {
                        RoundedRectangle(cornerRadius: 1)
                            .fill(Color.ink)
                            .frame(width: 2, height: 20)
                            .offset(x: w * CGFloat(goal.plannedHoursPerWeek / maxHours) - 1)
                            .accessibilityLabel("계획 \(PatternFormat.hours1(goal.plannedHoursPerWeek))h / 주")
                    }
                }
                .frame(height: 20)
            }
            .frame(height: 20)
        }
    }
}

// MARK: - 블록을 어떻게 지키나요? (제시간 / 늦게 / 놓침 + 자주 옮기는 루틴)

/// 제시간 / 늦게 / 놓침 필터
enum PatternKeepKind: String, CaseIterable, Identifiable {
    case onTime, late, missed
    var id: String { rawValue }
    var label: String {
        switch self {
        case .onTime: return "제시간 시작"
        case .late: return "늦게 시작"
        case .missed: return "놓침"
        }
    }
    var color: Color {
        switch self {
        case .onTime: return .success
        case .late: return .warning
        case .missed: return .danger
        }
    }
    func count(_ i: PatternBlockKeepItem) -> Int {
        switch self {
        case .onTime: return i.onTime
        case .late: return i.late
        case .missed: return i.missed
        }
    }
    func percent(_ b: PatternBlockKeeping) -> Int {
        switch self {
        case .onTime: return b.onTime ?? 0
        case .late: return b.late ?? 0
        case .missed: return b.missed ?? 0
        }
    }
}

struct PatternBlockKeepingCard: View {
    let block: PatternBlockKeeping?
    /// 누른 칩 — 그렇게 된 루틴 · Task 를 많은 순으로 보여줘요 (다시 누르면 접혀요)
    @State private var kind: PatternKeepKind?

    // private @State 가 있어서 다른 파일에서 부를 수 있게 init 을 직접 만들어요
    init(block: PatternBlockKeeping?, initialKind: PatternKeepKind? = nil) {
        self.block = block
        _kind = State(initialValue: initialKind)
    }

    var body: some View {
        let hasRatio = (block?.total ?? 0) > 0 && block?.onTime != nil
        let moves = block?.frequentMoves ?? []
        SectionCard("블록을 어떻게 지키나요?", aside: {
            if let total = block?.total, total > 0 { Text("\(PatternFormat.number(total))개 기준") }
        }) {
            VStack(alignment: .leading, spacing: 0) {
                if let block, hasRatio {
                    PatternKeepingBar(onTime: block.onTime ?? 0, late: block.late ?? 0, missed: block.missed ?? 0)
                    FlowLayout(spacing: 8, lineSpacing: 8) {
                        ForEach(PatternKeepKind.allCases) { k in
                            PatternKeepChip(kind: k, value: k.percent(block), on: kind == k) {
                                UISelectionFeedbackGenerator().selectionChanged()
                                let expanded = kind != k
                                PT.track("pattern.keep_filter_click", [
                                    "keep_kind": k.analyticsKey, "expanded": expanded,
                                    "item_count": (block.items ?? []).filter { k.count($0) > 0 }.count,
                                    "period": PT.period.rawValue,
                                ])
                                kind = expanded ? k : nil
                            }
                        }
                    }
                    .padding(.top, 12)
                    keepList(block)
                } else {
                    NotEnoughView(text: "시간을 정한 블록이 쌓이면 보여드려요")
                }

                Text("자주 옮기는 루틴").font(.pt(15, .bold)).foregroundStyle(Color.ink).padding(.top, 24)
                Text("반복되는 루틴만 봐요 — 늘 같은 방향으로 옮긴다면 루틴 시간을 바꾸는 게 나아요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
                if moves.isEmpty {
                    Text("캘린더에서 루틴 블록을 옮긴 기록이 쌓이면 보여드려요")
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .padding(.top, 8)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(moves.enumerated()), id: \.offset) { i, m in
                            if i > 0 { Divider().overlay(Color.borderSoft) }
                            PatternMoveRow(move: m)
                                .patternDeadTap("moved_routine")
                        }
                    }
                    .padding(.top, 8)
                }
            }
        }
    }
}

private struct PatternKeepingBar: View {
    let onTime: Int
    let late: Int
    let missed: Int

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            HStack(spacing: 0) {
                Color.success.frame(width: w * CGFloat(onTime) / 100)
                Color.warning.frame(width: w * CGFloat(late) / 100)
                Color.danger.frame(width: w * CGFloat(missed) / 100)
                Spacer(minLength: 0)
            }
        }
        .frame(height: 14)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

extension PatternBlockKeepingCard {
    @ViewBuilder
    fileprivate func keepList(_ block: PatternBlockKeeping) -> some View {
        let items = block.items ?? []
        if let kind {
            let list = items.filter { kind.count($0) > 0 }
                .sorted { a, b in
                    kind.count(a) != kind.count(b) ? kind.count(a) > kind.count(b) : a.total > b.total
                }
            if list.isEmpty {
                Text(kind == .missed ? "놓친 블록이 없어요 👏" : "\(kind.label)한 블록이 없어요")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .padding(.top, 12)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(list.enumerated()), id: \.element.id) { i, item in
                        if i > 0 { Divider().overlay(Color.borderSoft) }
                        PatternKeepItemRow(item: item, kind: kind)
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: Radius.xxl).stroke(Color.line, lineWidth: 1))
                .padding(.top, 12)
            }
        } else if !items.isEmpty {
            Text("칩을 누르면 어떤 루틴 · Task였는지 볼 수 있어요")
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
                .padding(.top, 8)
        }
    }
}

private struct PatternKeepChip: View {
    let kind: PatternKeepKind
    let value: Int
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 2).fill(kind.color).frame(width: 10, height: 10)
                Text(kind.label)
                Text("\(value)%").bold().foregroundStyle(on ? Color.actionPrimaryFg : Color.ink)
            }
            .font(.pt(13))
            .foregroundStyle(on ? Color.actionPrimaryFg : Color.ink2)
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(on ? Color.actionPrimary : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(on ? Color.actionPrimary : Color.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

private struct PatternKeepItemRow: View {
    let item: PatternBlockKeepItem
    let kind: PatternKeepKind

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    if item.routine {
                        NSymbol("repeat", size: 11, weight: .semibold)
                            .foregroundStyle(Color.textBrand)
                            .accessibilityLabel("루틴")
                    }
                    Text((item.emoji.map { "\($0) " } ?? "") + item.title)
                        .font(.pt(14, .semibold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                }
                if let goal = item.goalName {
                    HStack(spacing: 4) {
                        Circle().fill(Color.goal(item.goalColorCode)).frame(width: 7, height: 7)
                        Text(goal).font(.pt(12)).foregroundStyle(Color.ink3).lineLimit(1)
                    }
                }
            }
            Spacer(minLength: 0)
            (Text("\(kind.count(item))번").bold().foregroundColor(kind.color)
                + Text(" / \(item.total)번").foregroundColor(Color.ink4))
                .font(.pt(13))
                .fixedSize()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }
}

private struct PatternMoveRow: View {
    let move: PatternFrequentMove

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text((move.emoji.map { "\($0) " } ?? "") + move.title)
                    .font(.pt(15, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                Text(move.description)
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Text(move.fromLabel).strikethrough().foregroundStyle(Color.ink4)
                Text("→").accessibilityLabel("에서")
                Text(move.toLabel).fontWeight(.bold).foregroundStyle(Color.ink)
                Text("\(move.count)회")
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color.ink2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: 6))
            }
            .font(.pt(14))
            .fixedSize()
        }
        .padding(.vertical, 12)
    }
}

// MARK: - 잘 지키는 루틴 / 자주 놓치는 블록

struct PatternRoutineLists: View {
    let routines: PatternRoutines?

    var body: some View {
        let kept = routines?.kept ?? []
        let missed = routines?.missed ?? []
        VStack(alignment: .leading, spacing: 0) {
            Text("💪 잘 지키는 루틴").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            if kept.isEmpty {
                Text("4번 이상 한 루틴 중 75% 넘게 지킨 루틴이 여기에 보여요")
                    .font(.pt(13)).foregroundStyle(Color.ink3).padding(.top, 12)
            } else {
                VStack(spacing: 12) {
                    ForEach(kept, id: \.routineId) { r in
                        HStack(spacing: 12) {
                            Text((r.emoji.map { "\($0)  " } ?? "") + r.title)
                                .font(.pt(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                            Spacer(minLength: 0)
                            Text("\(r.rate)%").font(.pt(15, .bold)).foregroundStyle(Color.success)
                        }
                        .patternDeadTap("kept_routine")
                    }
                }
                .padding(.top, 12)
            }

            Divider().overlay(Color.borderSoft).padding(.vertical, 20)

            Text("🫠 자주 놓치는 블록").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            if missed.isEmpty {
                Text("3번 이상 한 루틴 중 자주 놓친 게 없어요 👏").font(.pt(13)).foregroundStyle(Color.ink3).padding(.top, 12)
            } else {
                VStack(spacing: 16) {
                    ForEach(missed, id: \.self) { r in
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text((r.emoji.map { "\($0)  " } ?? "") + r.label)
                                    .font(.pt(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                                Text(r.description).font(.pt(13)).foregroundStyle(Color.ink3)
                            }
                            Spacer(minLength: 0)
                            Text("\(r.rate)%").font(.pt(15, .bold)).foregroundStyle(Color.danger)
                        }
                        .patternDeadTap("missed_block")
                    }
                }
                .padding(.top, 12)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

// MARK: - Task 처리 습관 (루틴이 아닌 Task)

struct PatternTaskHabitsCard: View {
    let habits: PatternTaskHabits?

    private static let dash = "–"

    private var postponedSub: String? {
        if let top = habits?.postponedTopGoal { return "대부분 \(top)" }
        return habits?.postponedRate != nil ? "미룬 Task가 거의 없어요" : nil
    }

    var body: some View {
        let h = habits
        SectionCard("Task 처리 습관", aside: {
            if let h { Text("Task \(h.taskCount)개") }
        }) {
            HStack(alignment: .top, spacing: 0) {
                PatternHabitCell(label: "미룬 Task", value: h?.postponedRate.map { "\($0)%" } ?? Self.dash,
                                 sub: postponedSub)
                Rectangle().fill(Color.borderSoft).frame(width: 1)
                PatternHabitCell(label: "brain dump",
                                 value: h?.brainDumpPerWeek.map { "주 \(PatternFormat.hours1($0))회" } ?? Self.dash,
                                 sub: h?.brainDumpAvgItems.map { "한 번에 평균 \(PatternFormat.hours1($0))개" }
                                     ?? "Task 추가를 2번 이상 하면 보여요")
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct PatternHabitCell: View {
    let label: String
    let value: String
    let sub: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.captionMD).foregroundStyle(Color.textSecondary)
            Text(value)
                .font(.pt(20, .semibold))
                .tracking(-0.4)
                .foregroundStyle(Color.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .padding(.top, 6)
            if let sub {
                Text(sub)
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

#Preview {
    let r = PatternSampleData.report
    return ScrollView {
        VStack(spacing: 16) {
            PatternTimeUsageCard(usage: r.timeUsage)
            PatternBlockKeepingCard(block: r.blockKeeping)
            PatternBlockKeepingCard(block: r.blockKeeping, initialKind: .missed)
            PatternRoutineLists(routines: r.routines)
            PatternTaskHabitsCard(habits: r.taskHabits)
            PatternTimeUsageCard(usage: nil)
            PatternBlockKeepingCard(block: nil)
            PatternRoutineLists(routines: nil)
            PatternTaskHabitsCard(habits: nil)
        }
        .padding(16)
    }
    .canvasBackground()
}
