// Features/Habit/HabitGoalBody.swift
//
// 웹 features/habit/ui/HabitView.tsx — 기본 생활 습관 (goalMode = HABIT) 목표 상세.
// GoalDetailScreen 이 머리 카드 + 탭 대신 이 화면을 보여줘요 (스크롤은 GoalDetailScreen 의 ScrollView).
//
//  - 체크 카드: 날짜(지난 날도 체크할 수 있어요) + 몇 개 했는지 + 그날 할 습관 · 안 해도 되는 습관 · 쉬는 습관
//  - 습관별 잔디 (한 달씩)
//  - 일회성 할 일 (병원 가기 · 친구 만나기) — 습관과 달리 한 번 하고 끝나요 (GoalTaskListSection 재사용)

import SwiftUI
import UIKit

/// 습관 시트: 새로 추가 · 수정
enum HabitFormTarget: Identifiable, Hashable {
    case create
    case edit(Habit)

    var id: String {
        switch self {
        case .create: return "new"
        case .edit(let h): return "habit:\(h.habitId)"
        }
    }

    var habit: Habit? {
        if case .edit(let h) = self { return h }
        return nil
    }
}

@MainActor
struct HabitGoalBody: View {
    let goal: GoalCategory

    @State private var model: HabitModel
    @State private var showOff = false
    @State private var sheet: HabitFormTarget?
    /// 오늘 · 진행 · 할 일 (DESIGN_SPEC 12.5)
    @State private var tab: HabitTab = .today
    @Environment(GoalDetailViewModel.self) private var vm

    init(goal: GoalCategory) {
        self.goal = goal
        _model = State(initialValue: HabitModel())
    }

    /// 미리보기용
    init(goal: GoalCategory, previewModel: HabitModel) {
        self.goal = goal
        _model = State(initialValue: previewModel)
    }

    private var titleText: String {
        if let e = goal.emoji, !e.isEmpty { return e + " " + goal.goalCategoryName }
        return goal.goalCategoryName
    }

    // 오늘(습관) · 할 일 · 진행 (2026-10 — 할 일을 진행 앞으로, 웹과 같은 순서)
    // 2026-10: "오늘" → "루틴" (값은 분석 연속성 때문에 today 그대로)
    private let tabs: [(HabitTab, String)] = [(.today, "루틴"), (.tasks, "할 일"), (.progress, "진행")]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text(titleText)
                    .font(.pt(20, .semibold))
                    .foregroundStyle(Color.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text("했는지만 체크하는 생활 습관과, 병원 가기처럼 한 번만 할 일을 여기서 챙겨요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .reportGoalHeroBottom()

            // 목표형과 같은 밑줄 탭 — 위에 붙지 않아요 (DESIGN_SPEC 12.4 · 12.5)
            GoalDetailTabBar(selection: $tab, items: tabs) { from, to in
                vm.track("goal_detail.tab_click", ["goal_mode": goal.analyticsMode, "tab": to.rawValue, "from_tab": from.rawValue])
            }
            .reportGoalTabs(current: tabs.first(where: { $0.0 == tab })?.1 ?? "")

            VStack(alignment: .leading, spacing: 14) {
                tabContent
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 32)
        }
        .reloadOn([.habits], id: model.day) { await model.load(day: model.day) }
        .sheet(item: $sheet) { target in
            HabitFormSheet(habit: target.habit, existingNames: model.habits.map(\.name)) { sheet = nil }
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch tab {
        case .today:
            HabitCheckCard(model: model, showOff: $showOff) { sheet = $0 }
        case .progress:
            if model.home != nil {
                HabitGrassSection(model: model)
            } else if case .loading = model.phase {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 160)
            }
        case .tasks:
            GoalTaskListSection(
                goal: goal,
                title: "할 일",
                hint: "병원 가기 · 친구 만나기처럼 한 번만 할 일이에요. 날짜만 정하면 캘린더 빈 시간에 넣어 드려요.",
                addLabel: "할 일 추가",
                emptyText: "아직 할 일이 없어요. 위에서 바로 추가해 보세요.",
                initialPeriod: .all
            )
        }
    }
}

/// 생활 습관 목표 탭 (분석 값 = rawValue)
enum HabitTab: String, Hashable {
    case today, progress, tasks
}

// MARK: - 체크 카드 (날짜 · 진행 · 습관 목록 · 추가)

@MainActor
private struct HabitCheckCard: View {
    let model: HabitModel
    @Binding var showOff: Bool
    let open: (HabitFormTarget) -> Void

    private var today: String { model.today }

    /// "오늘" · "어제" · "10월 11일 (토)"
    private func dayTitle(_ ymd: String) -> String {
        if ymd == today { return "오늘" }
        if ymd == KST.addDays(today, -1) { return "어제" }
        return KST.formatMonthDay(ymd)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            switch model.phase {
            case .loading:
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 160)
            case .failed(let message):
                VStack(spacing: 6) {
                    Text("습관을 불러오지 못했어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Text(message).font(.pt(14)).foregroundStyle(Color.ink3).multilineTextAlignment(.center)
                    NButton("다시 시도", size: .sm) {
                        model.track("habit.retry_click", ["days_ago": model.daysAgo])
                        let d = model.day
                        Task { @MainActor in await model.load(day: d) }
                    }
                    .padding(.top, 10)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
            case .loaded(let home):
                list(home)
                HabitAddArea(model: model, existing: home.habits) {
                    model.track("habit.add_click", ["habit_count": home.habits.count])
                    open(.create)
                }
                    .padding(.top, 16)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    // ── 날짜 + 진행 ──

    private var header: some View {
        let isToday = model.isToday
        let due = model.due
        let done = model.doneCount
        let ratio: Double = due.isEmpty ? 0 : Double(done) / Double(due.count)
        let loaded = model.home != nil
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 2) {
                IconButton(systemName: "chevron.left", size: 17, color: Color.ink3, label: "전날") {
                    UISelectionFeedbackGenerator().selectionChanged()
                    model.day = KST.addDays(model.day, -1)
                    model.track("habit.date_change", ["date_action": "prev", "days_ago": model.daysAgo])
                }
                Text(dayTitle(model.day))
                    .font(.pt(16, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                    .frame(minWidth: 64)
                IconButton(systemName: "chevron.right", size: 17, color: isToday ? Color.ink4 : Color.ink3, label: "다음 날") {
                    guard !isToday else { return }
                    UISelectionFeedbackGenerator().selectionChanged()
                    let next = KST.addDays(model.day, 1)
                    model.day = next > today ? today : next
                    model.track("habit.date_change", ["date_action": "next", "days_ago": model.daysAgo])
                }
                .disabled(isToday)
                if !isToday {
                    Button("오늘로") {
                        model.day = today
                        model.track("habit.date_change", ["date_action": "today", "days_ago": 0])
                    }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.textBrand)
                        .buttonStyle(.plain)
                        .padding(.leading, 4)
                }
                Spacer(minLength: 0)
                if loaded {
                    (Text("\(done)").foregroundColor(Color.ink)
                     + Text(" / \(due.count)").foregroundColor(Color.ink3))
                        .font(.ptMono(15, .bold))
                }
            }
            .padding(.leading, -8)
            ProgressBar(value: ratio, color: Color.progressFill, height: 6)
            if loaded, !due.isEmpty, done == due.count {
                Text(isToday ? "오늘 할 습관을 모두 챙겼어요 🎉" : "이날 할 습관을 모두 챙겼어요")
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.success)
            }
        }
    }

    // ── 습관 목록 ──

    @ViewBuilder private func list(_ home: HabitHome) -> some View {
        if home.habits.isEmpty {
            VStack(spacing: 4) {
                Text("아직 습관이 없어요").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Text("영양제 · 물 마시기처럼 매일 챙길 것을 아래에서 골라 보세요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 28)
        } else {
            let due = model.due
            let off = model.off
            let paused = model.paused
            if due.isEmpty {
                Text("\(dayTitle(model.day))은 해야 하는 습관이 없어요.")
                    .font(.pt(14))
                    .foregroundStyle(Color.ink3)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            } else {
                rows(due).padding(.top, 6)
            }

            if !off.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    divider
                    Button {
                        withAnimation(.easeOut(duration: 0.18)) { showOff.toggle() }
                        model.track("habit.off_list_toggle", ["days_ago": model.daysAgo, "expanded": showOff, "item_count": off.count])
                    } label: {
                        HStack(spacing: 4) {
                            Text("\(dayTitle(model.day))은 안 해도 되는 습관 \(off.count)")
                            NSymbol("chevron.down", size: 11, weight: .semibold)
                                .rotationEffect(.degrees(showOff ? 180 : 0))
                        }
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                        .frame(height: 36)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(showOff ? .isSelected : [])
                    if showOff { rows(off) }
                }
            }

            if !paused.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    divider
                    Text("쉬는 중 \(paused.count)")
                        .font(.pt(13, .semibold))
                        .foregroundStyle(Color.ink3)
                        .frame(height: 36)
                    rows(paused)
                }
            }
        }
    }

    private var divider: some View {
        Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.top, 8).padding(.bottom, 4)
    }

    private func rows(_ habits: [Habit]) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(habits.enumerated()), id: \.element.habitId) { i, h in
                if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                HabitCheckRow(
                    habit: h,
                    onToggle: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        Task { @MainActor in await model.toggle(h) }
                    },
                    onEdit: {
                        model.track("habit.edit_open", ["habit_id": h.habitId])
                        open(.edit(h))
                    }
                )
            }
        }
    }
}

// MARK: - 습관 한 줄

@MainActor
private struct HabitCheckRow: View {
    let habit: Habit
    let onToggle: () -> Void
    let onEdit: () -> Void

    private var meta: String {
        var parts: [String] = [HabitFormat.daysLabel(habit)]
        if let t = habit.timeRangeLabel { parts.append("🕘" + t) }
        if let r = habit.remindTime, !r.isEmpty { parts.append("🔔" + String(r.prefix(5))) }
        if let rate = habit.rate30 { parts.append("30일 \(rate)%") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        let checked = habit.checked
        let paused = habit.isPaused
        HStack(spacing: 12) {
            Button(action: onToggle) {
                // DESIGN_SPEC 8.6: 체크 버튼 22pt 원 (아이콘 14) — 누르는 영역은 44 그대로
                ZStack {
                    Circle().fill(checked ? Color.actionPrimary : Color.clear)
                    Circle().strokeBorder(checked ? Color.actionPrimary : Color.borderControl, lineWidth: 1.5)
                    NIcon.check.image(size: 14)
                        .foregroundStyle(checked ? Color.actionPrimaryFg : Color.clear)
                }
                .frame(width: 22, height: 22)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: checked)
            }
            .buttonStyle(.plain)
            .disabled(paused)
            .accessibilityLabel("\(habit.name) \(checked ? "체크 풀기" : "했어요")")
            .accessibilityAddTraits(checked ? .isSelected : [])

            // 누르면 수정 시트
            Button(action: onEdit) {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(habit.title)
                            .font(.pt(15, .semibold))
                            .foregroundStyle(checked ? Color.ink3 : Color.ink)
                            .strikethrough(checked, color: Color.ink3)
                            .lineLimit(1)
                        HStack(spacing: 6) {
                            Text(meta).lineLimit(1)
                            if paused {
                                Text("쉬는 중").font(.pt(12, .semibold)).foregroundStyle(Color.warningInk)
                            }
                        }
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    VStack(alignment: .trailing, spacing: 5) {
                        if habit.streak > 1 {
                            Text("🔥 \(habit.streak)일")
                                .font(.pt(12, .semibold))
                                .foregroundStyle(GoalPalette.orange.tone.fg)
                        }
                        // 최근 7일
                        HStack(spacing: 3) {
                            ForEach(habit.recent7, id: \.date) { c in
                                HabitGrassCell(state: c.state, size: 8, radius: 2)
                            }
                        }
                        .accessibilityHidden(true)
                    }
                }
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(habit.name) 수정")
        }
        .opacity(paused ? 0.6 : 1)
    }
}

// MARK: - 추가 (점선 버튼 + 바로 추가 칩)

@MainActor
private struct HabitAddArea: View {
    let model: HabitModel
    let existing: [Habit]
    let onAdd: () -> Void

    var body: some View {
        let names = Set(existing.map(\.name))
        let suggestions = HabitSuggestion.all.filter { !names.contains($0.name) }
        VStack(alignment: .leading, spacing: 12) {
            GoalAddButton("습관 추가", action: onAdd)
            if !suggestions.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("눌러서 바로 추가").font(.pt(12, .medium)).foregroundStyle(Color.ink3)
                    FlowLayout(spacing: 6) {
                        ForEach(Array(suggestions.prefix(10).enumerated()), id: \.element.id) { i, s in
                            Chip("\(s.emoji) \(s.name)", size: .sm) {
                                model.track("habit.suggestion_click", ["suggestion_index": i, "habit_count": existing.count])
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                Task { @MainActor in await model.add(s) }
                            }
                            .disabled(model.addingSuggestion)
                            .opacity(model.addingSuggestion ? 0.5 : 1)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기

#Preview("기본 생활 습관") {
    let vm = GoalDetailViewModel.preview(goal: HabitPreviewData.goal, progress: nil)
    vm.tasks = HabitPreviewData.tasks
    return NavigationStack {
        ScrollView {
            HabitGoalBody(goal: HabitPreviewData.goal, previewModel: HabitModel.preview())
        }
        .canvasBackground()
    }
    .environment(vm)
    .environment(AppRouter.shared)
}

#Preview("습관 없음") {
    let vm = GoalDetailViewModel.preview(goal: HabitPreviewData.goal, progress: nil)
    vm.tasks = GoalTasksResponse(totalCount: 0, todoCount: 0, completedCount: 0, tasks: [])
    return ScrollView {
        HabitGoalBody(goal: HabitPreviewData.goal, previewModel: HabitModel.preview(empty: true))
    }
    .canvasBackground()
    .environment(vm)
    .environment(AppRouter.shared)
}
