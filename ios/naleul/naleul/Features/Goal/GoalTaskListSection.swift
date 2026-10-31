// Features/Goal/GoalTaskListSection.swift
//
// 웹 features/goal/ui/GoalTaskList.tsx — 목표 상세 · 관련 Task 전체.
// AI 가 만든 Task · 직접 추가한 Task · 루틴 Task 를 한 번에 보고, 완료/미완료 · 할 일/루틴으로 거를 수 있어요.
// 정렬은 서버 순서 그대로: 미완료 위 · 오늘과 가까운 날짜일수록 위 · 완료는 아래.

import SwiftUI

private enum GoalTaskStatusFilter: Hashable {
    case all, todo, done
    var analyticsName: String { switch self { case .all: "ALL"; case .todo: "TODO"; case .done: "DONE" } }
}
private enum GoalTaskKindFilter: Hashable {
    case all, oneTime, routine
    var analyticsName: String { switch self { case .all: "ALL"; case .oneTime: "ONE_TIME"; case .routine: "ROUTINE" } }
}
/// 언제 할 Task 를 볼지 — 기본은 이번 주 (전체는 몇 달치가 쌓여 지금 할 일이 묻혀요)
enum GoalTaskPeriodFilter: Hashable {
    case today, week, all
    var analyticsName: String { switch self { case .today: "TODAY"; case .week: "WEEK"; case .all: "ALL" } }
}

@MainActor
struct GoalTaskListSection: View {
    let goal: GoalCategory
    /// 제목 · 안내 · 추가 버튼 · 빈 목록 문구 (기본 생활 습관 화면은 "할 일"로 바꿔 써요)
    let title: String
    let hint: String?
    let addLabel: String
    let emptyMessage: String
    @Environment(GoalDetailViewModel.self) private var vm

    @State private var period: GoalTaskPeriodFilter
    @State private var status: GoalTaskStatusFilter = .all
    @State private var kind: GoalTaskKindFilter = .all
    @State private var limit = 20

    private static let page = 20

    init(goal: GoalCategory,
         title: String = "관련 Task",
         hint: String? = nil,
         addLabel: String = "Task 추가",
         emptyText: String = "이 목표에 연결된 Task가 아직 없어요. 위에서 바로 추가해 보세요.",
         initialPeriod: GoalTaskPeriodFilter = .week) {
        self.goal = goal
        self.title = title
        self.hint = hint
        self.addLabel = addLabel
        self.emptyMessage = emptyText
        _period = State(initialValue: initialPeriod)
    }

    private var today: String { KST.today() }

    private func inPeriod(_ t: TimeBlockTask, _ p: GoalTaskPeriodFilter) -> Bool {
        if p == .all { return true }
        guard let day = GoalDetailLogic.dayOf(t) else { return false }
        if p == .today { return day == today }
        let ws = KST.startOfWeek(today)
        return day >= ws && day <= KST.addDays(ws, 6)
    }

    private func matchKind(_ t: TimeBlockTask, _ k: GoalTaskKindFilter) -> Bool {
        switch k {
        case .all: return true
        case .routine: return t.sourceType == .ROUTINE
        case .oneTime: return t.sourceType != .ROUTINE
        }
    }

    private func matchStatus(_ t: TimeBlockTask, _ s: GoalTaskStatusFilter) -> Bool {
        switch s {
        case .all: return true
        case .done: return GoalDetailLogic.isDone(t)
        case .todo: return !GoalDetailLogic.isDone(t)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(title).font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                    Spacer(minLength: 0)
                    if let d = vm.tasks {
                        Text("\(d.completedCount) / \(d.totalCount) 완료").font(.pt(13)).foregroundStyle(Color.ink3)
                    }
                }
                if let hint {
                    Text(hint)
                        .font(.pt(13))
                        .foregroundStyle(Color.ink3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            // 추가: 이름·날짜만 넣고 저장
            GoalAddButton(addLabel) {
                vm.track("goal_task_list.add_click", ["surface": vm.taskSurface, "task_count": vm.tasks?.totalCount ?? 0])
                vm.sheet = .task(nil)
            }

            if let data = vm.tasks {
                if data.tasks.isEmpty {
                    Text(emptyMessage)
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                } else {
                    listBody(data.tasks)
                }
            } else if vm.tasksFailed {
                HStack(spacing: 4) {
                    Text("Task를 불러오지 못했어요.").foregroundStyle(Color.ink3)
                    Button("다시 시도") {
                        vm.track("goal_task_list.retry_click", ["surface": vm.taskSurface])
                        Task { @MainActor in await vm.reloadTasks() }
                    }
                        .foregroundStyle(Color.ink2)
                        .underline()
                        .buttonStyle(.plain)
                }
                .font(.pt(14))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
            } else {
                ProgressView().tint(Color.ink3).frame(maxWidth: .infinity, minHeight: 120)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    @ViewBuilder private func listBody(_ all: [TimeBlockTask]) -> some View {
        let periodCounts: [GoalTaskPeriodFilter: Int] = [
            .today: all.filter { inPeriod($0, .today) }.count,
            .week: all.filter { inPeriod($0, .week) }.count,
            .all: all.count,
        ]
        let byKind = all.filter { inPeriod($0, period) && matchKind($0, kind) }
        let shown = byKind.filter { matchStatus($0, status) }
        let visible = Array(shown.prefix(limit))
        let doneCount = byKind.filter { GoalDetailLogic.isDone($0) }.count
        let hasRoutine = all.contains { $0.sourceType == .ROUTINE }
        let firstDoneId: Int? = status == .all ? visible.first(where: { GoalDetailLogic.isDone($0) })?.taskId : nil

        // 기간: 오늘 · 이번 주(기본) · 전체
        SegmentPicker(
            selection: Binding(get: { period }, set: { p in
                guard p != period else { return }
                period = p
                limit = Self.page
                vm.track("goal_task_list.period_select", [
                    "surface": vm.taskSurface, "period": p.analyticsName,
                    "result_count": all.filter { inPeriod($0, p) && matchKind($0, kind) && matchStatus($0, status) }.count,
                ])
            }),
            options: [
                (GoalTaskPeriodFilter.today, "오늘 \(periodCounts[.today] ?? 0)"),
                (GoalTaskPeriodFilter.week, "이번 주 \(periodCounts[.week] ?? 0)"),
                (GoalTaskPeriodFilter.all, "전체 기간 \(periodCounts[.all] ?? 0)"),
            ]
        )

        // 필터
        FlowLayout(spacing: 6) {
            Chip("전체 \(byKind.count)", selected: status == .all, size: .sm) { pickStatus(.all, all) }
            Chip("미완료 \(byKind.count - doneCount)", selected: status == .todo, size: .sm) { pickStatus(.todo, all) }
            Chip("완료 \(doneCount)", selected: status == .done, size: .sm) { pickStatus(.done, all) }
            if hasRoutine {
                Rectangle().fill(Color.line).frame(width: 1, height: 16)
                Chip("모두", selected: kind == .all, size: .sm) { pickKind(.all, all) }
                Chip("할 일", selected: kind == .oneTime, size: .sm) { pickKind(.oneTime, all) }
                Chip("루틴", selected: kind == .routine, size: .sm) { pickKind(.routine, all) }
            }
        }

        if shown.isEmpty {
            Text(emptyText(byKindEmpty: byKind.isEmpty))
                .font(.pt(14))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .onAppear {
                    guard vm.once("task_empty_filter:\(period):\(status):\(kind)") else { return }
                    vm.track("goal_task_list.empty_filter_view", [
                        "surface": vm.taskSurface, "period": period.analyticsName,
                        "task_filter": status.analyticsName, "unfiltered_count": all.count,
                    ])
                }
        } else {
            VStack(spacing: 0) {
                ForEach(Array(visible.enumerated()), id: \.element.taskId) { i, t in
                    // 전체 보기에서 미완료와 완료 사이 구분선
                    if t.taskId == firstDoneId {
                        Text("완료한 Task")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.ink3)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 20)
                            .padding(.bottom, 4)
                    } else if i > 0 {
                        Rectangle().fill(Color.borderSoft).frame(height: 1)
                    }
                    GoalTaskRow(
                        task: t,
                        today: today,
                        toggling: vm.togglingTaskId == t.taskId,
                        onToggle: { Task { @MainActor in await vm.toggleTask(t) } },
                        onEdit: t.sourceType == .MANUAL ? {
                            vm.track("goal_task_list.edit_open", [
                                "surface": vm.taskSurface, "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                            ])
                            vm.sheet = .task(t)
                        } : nil,
                        onDelete: t.sourceType != .MISSION ? { vm.ask(.deleteTask(t)) } : nil,
                        onNameTap: { day in
                            vm.track("goal_task_list.task_name_click", [
                                "surface": vm.taskSurface, "task_id": t.taskId, "days_from_today": GD.daysFromToday(day),
                            ])
                        }
                    )
                }
            }
            if shown.count > visible.count {
                Button {
                    vm.track("goal_task_list.load_more_click", [
                        "surface": vm.taskSurface, "shown_count": visible.count, "remaining_count": shown.count - visible.count,
                    ])
                    limit += Self.page
                } label: {
                    HStack(spacing: 4) {
                        Text("더 보기 (\(shown.count - visible.count)개 남음)")
                        NSymbol("chevron.down", size: 12, weight: .semibold)
                    }
                    .font(.pt(14, .semibold))
                    .foregroundStyle(Color.ink2)
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func emptyText(byKindEmpty: Bool) -> String {
        if byKindEmpty {
            switch period {
            case .today: return "오늘 할 Task가 없어요."
            case .week: return "이번 주 Task가 없어요."
            case .all: return "조건에 맞는 Task가 없어요."
            }
        }
        return status == .done ? "아직 완료한 Task가 없어요." : "남은 Task가 없어요. 모두 끝냈어요! 🎉"
    }

    private func pickStatus(_ s: GoalTaskStatusFilter, _ all: [TimeBlockTask]) {
        guard s != status else { return }
        status = s
        limit = Self.page
        vm.track("goal_task_list.status_filter_select", [
            "surface": vm.taskSurface, "task_filter": s.analyticsName,
            "result_count": all.filter { inPeriod($0, period) && matchKind($0, kind) && matchStatus($0, s) }.count,
        ])
    }

    private func pickKind(_ k: GoalTaskKindFilter, _ all: [TimeBlockTask]) {
        guard k != kind else { return }
        kind = k
        limit = Self.page
        vm.track("goal_task_list.kind_filter_select", [
            "surface": vm.taskSurface, "task_kind": k.analyticsName,
            "result_count": all.filter { inPeriod($0, period) && matchKind($0, k) && matchStatus($0, status) }.count,
        ])
    }
}

// MARK: - 한 줄

@MainActor
private struct GoalTaskRow: View {
    let task: TimeBlockTask
    let today: String
    let toggling: Bool
    let onToggle: () -> Void
    let onEdit: (() -> Void)?
    let onDelete: (() -> Void)?
    var onNameTap: ((String) -> Void)? = nil

    @Environment(AppRouter.self) private var router

    private var done: Bool { GoalDetailLogic.isDone(task) }
    private var day: String? { GoalDetailLogic.dayOf(task) }

    private var timeText: String {
        guard let shown = TimetableLayout.shownTime(task) else { return "시간 미정" }
        let end = shown.endAt.map { "–\(KST.hm($0))" } ?? ""
        return (shown.actual ? "실제 " : "") + KST.hm(shown.startAt) + end
    }

    private var dayColor: Color {
        if day == today && !done { return .brand }
        if task.missed { return .danger }
        return done ? .ink4 : .ink2
    }

    private var title: String {
        if let e = task.emoji, !e.isEmpty { return e + " " + task.taskName }
        return task.taskName
    }

    private var subLine: String {
        [task.generalCategoryName, task.milestoneTitle].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onToggle) {
                ZStack {
                    Circle()
                        .fill(done ? Color.actionPrimary : Color.clear)
                    Circle()
                        .strokeBorder(done ? Color.actionPrimary : Color.borderControl, lineWidth: 1.5)
                    if toggling {
                        ProgressView().controlSize(.mini)
                    } else if done {
                        NSymbol("checkmark", size: 11, weight: .heavy).foregroundStyle(.white)
                    }
                }
                .frame(width: 24, height: 24)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .disabled(toggling)
            .accessibilityLabel(done ? "\(task.taskName) 완료 취소" : "\(task.taskName) 완료")

            // 날짜
            VStack(alignment: .leading, spacing: 1) {
                Text(day.map { GoalDetailLogic.dayLabel($0, today: today) } ?? "날짜 미정")
                    .font(.pt(13, .semibold))
                    .foregroundStyle(dayColor)
                    .lineLimit(1)
                Text(timeText)
                    .font(.ptMono(11))
                    .foregroundStyle(Color.ink4)
                    .lineLimit(1)
            }
            .frame(width: 80, alignment: .leading)

            Circle().fill(Color.goal(task.goalColorCode)).frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                // 날짜가 있으면 누르면 그날 캘린더로
                if let day {
                    Button {
                        onNameTap?(day)
                        router.openCalendar(view: .day, date: day)
                    } label: { titleText }
                        .buttonStyle(.plain)
                } else {
                    titleText
                }
                if !subLine.isEmpty {
                    Text(subLine).font(.pt(12)).foregroundStyle(Color.ink3).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            GoalRowActions(label: task.taskName, onEdit: onEdit, onDelete: onDelete)
        }
        .padding(.vertical, 10)
    }

    private var titleText: some View {
        Text(title)
            .font(.pt(15, .semibold))
            .foregroundStyle(done ? Color.ink4 : Color.ink)
            .strikethrough(done, color: Color.ink4)
            .lineLimit(1)
    }
}

#Preview {
    ScrollView {
        GoalTaskListSection(goal: GoalPreviewData.goal).padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview())
    .environment(AppRouter.shared)
}
