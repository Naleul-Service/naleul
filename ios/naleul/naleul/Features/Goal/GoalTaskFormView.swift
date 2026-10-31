// Features/Goal/GoalTaskFormView.swift
//
// 웹 features/goal/ui/GoalTaskForm.tsx — 목표의 Task 추가 · 수정 폼.
// 이름과 날짜만 넣고 저장하면 그날 "시간 미정"으로 들어가고, 자동 배치가 빈 시간에 넣어 줘요.
// 시간을 넣으면 그 시간에 바로 고정(📌)돼요 — 다른 고정 일정과 겹치면 저장되지 않아요.
//
// 목표 상세(시트 안)와 Task 추가 화면(직접 추가)에서 같이 써요.
// 시트 안에서는 .goalSheetForm() 을 붙이면 테두리 없이 그려져요.

import SwiftUI

@MainActor
struct GoalTaskFormView: View {
    let goal: GoalCategory
    /// nil 이면 새로 추가
    let task: TimeBlockTask?
    let onDone: () -> Void
    /// 새로 만든 Task (추가일 때만) — 예: "이날 빈 시간에 배치하기" 안내
    let onCreated: ((TimeBlockTask) -> Void)?
    /// 앰플리튜드 surface (goal_detail / habit) — nil 이면 이벤트를 보내지 않아요 (Task 추가 화면은 자기 이벤트를 써요)
    let analyticsSurface: String?
    /// false 면 goal_task_list.edit_cancel 을 안 보내요 (Task 추가 › 직접 추가는 폼을 다시 만들며 비워서)
    let tracksCancel: Bool
    /// 처음 값과 달라졌는지 바뀔 때마다 (Task 추가 시트의 has_input)
    let onDirtyChange: ((Bool) -> Void)?

    @State private var emoji: String
    @State private var name: String
    @State private var date: String
    @State private var startTime: String
    @State private var endTime: String
    @State private var sub: Int?
    @State private var milestone: Int?
    @State private var duration: String
    @State private var dueDate: String
    @State private var more: Bool
    @State private var saving = false
    @State private var outcome: String?
    @State private var initialKey = ""

    init(goal: GoalCategory, task: TimeBlockTask?, onDone: @escaping () -> Void,
         onCreated: ((TimeBlockTask) -> Void)? = nil, analyticsSurface: String? = nil,
         tracksCancel: Bool = true, onDirtyChange: ((Bool) -> Void)? = nil, prefill: TaskAddPrefill? = nil) {
        self.goal = goal
        self.task = task
        self.onDone = onDone
        self.onCreated = onCreated
        self.analyticsSurface = analyticsSurface
        self.tracksCancel = tracksCancel
        self.onDirtyChange = onDirtyChange
        let areas = GoalDetailLogic.areas(goal)
        _emoji = State(initialValue: task?.emoji ?? "")
        _name = State(initialValue: task?.taskName ?? "")
        let planned = task?.plannedStartAt.map { String($0.prefix(10)) }
        _date = State(initialValue: planned ?? task?.date ?? task?.scheduledDate ?? KST.today())
        _startTime = State(initialValue: KST.hm(task?.plannedStartAt))
        _endTime = State(initialValue: KST.hm(task?.plannedEndAt))
        // 캘린더 빈 칸에서 열면 그 날짜 · 시각으로 1시간 (새 Task 일 때만)
        if task == nil, let p = prefill {
            let overDay = p.startMinutes >= 1440
            _date = State(initialValue: overDay ? KST.addDays(p.date, 1) : p.date)
            _startTime = State(initialValue: KST.minutesToTime(p.startMinutes))
            _endTime = State(initialValue: KST.minutesToTime(p.startMinutes + 60))
        }
        if let gid = task?.generalCategoryId, areas.contains(where: { $0.generalCategoryId == gid }) {
            _sub = State(initialValue: gid)
        } else {
            _sub = State(initialValue: nil)
        }
        _milestone = State(initialValue: task?.milestoneId)
        if let d = task?.plannedDurationMinutes, d > 0, task?.plannedStartAt == nil {
            _duration = State(initialValue: String(d))
        } else {
            _duration = State(initialValue: "")
        }
        _dueDate = State(initialValue: task?.dueDate ?? "")
        let showMore = task?.milestoneId != nil || task?.dueDate != nil || (task != nil && task?.plannedStartAt == nil)
        _more = State(initialValue: showMore)
    }

    private var areas: [SubGoalInfo] { GoalDetailLogic.areas(goal) }
    private var milestones: [MilestoneInfo] { GoalDetailLogic.sortedMilestones(goal) }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var hasTime: Bool { !startTime.isEmpty && !endTime.isEmpty }
    private var durationNum: Double? { GoalDetailLogic.toNum(duration) }
    /// 시작~종료 분 (자정을 넘기면 다음 날까지)
    private var span: Int? {
        guard hasTime else { return nil }
        return ((KST.timeToMinutes(endTime) - KST.timeToMinutes(startTime)) % 1440 + 1440) % 1440
    }

    private var error: String? {
        if trimmedName.isEmpty { return nil }
        if date.isEmpty { return "날짜를 입력해 주세요." }
        if startTime.isEmpty != endTime.isEmpty { return "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요." }
        if hasTime && span == 0 { return "시작과 종료 시간이 같아요." }
        if !hasTime, let d = durationNum, d.isNaN || d < 5 || d > 1440 { return "소요 시간은 5~1440분(24시간)으로 입력해 주세요." }
        return nil
    }

    private var subOptions: [(value: Int?, label: String)] {
        var o: [(value: Int?, label: String)] = [(value: nil, label: "세부 목표 없음")]
        for a in areas { o.append((value: a.generalCategoryId, label: a.generalCategoryName)) }
        return o
    }

    private var milestoneOptions: [(value: Int?, label: String)] {
        var o: [(value: Int?, label: String)] = [(value: nil, label: "없음")]
        for m in milestones { o.append((value: m.milestoneId, label: m.title)) }
        return o
    }

    private var hint: String {
        guard hasTime, let span else { return "시간을 비워 두면 그날 빈 시간에 자동으로 배치돼요." }
        let overnight = KST.timeToMinutes(endTime) < KST.timeToMinutes(startTime) ? " (다음 날까지)" : ""
        return "\(KST.formatDuration(span)) · 이 시간에 고정돼요\(overnight)."
    }

    var body: some View {
        GoalInlineForm(submitLabel: task == nil ? "추가" : "저장", saving: saving,
                       valid: !trimmedName.isEmpty && error == nil, error: error,
                       onCancel: {
                           outcome = "cancel_button"
                           onDone()
                       }, onSubmit: { submit(method: "button") }) {
            fields
        } extra: {
            Button {
                more.toggle()
                track("goal_task_list.more_toggle", ["is_new": task == nil, "expanded": more])
            } label: {
                HStack(spacing: 4) {
                    NSymbol(more ? "chevron.up" : "chevron.down", size: 11, weight: .semibold)
                    Text(more ? "간단히" : "더 설정하기")
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.ink3)
            }
            .buttonStyle(.plain)
        }
        .onAppear { if initialKey.isEmpty { initialKey = formKey } }
        .onChange(of: error) { _, e in
            if let e { track("goal_task_list.edit_error", ["is_new": task == nil, "validation_error": e]) }
        }
        .onChange(of: formKey) { _, key in onDirtyChange?(!initialKey.isEmpty && key != initialKey) }
        .onDisappear {
            guard analyticsSurface != nil, tracksCancel, let m = GD.closeMethod(outcome) else { return }
            track("goal_task_list.edit_cancel", ["is_new": task == nil, "close_method": m, "is_dirty": formKey != initialKey])
        }
    }

    /// 입력이 바뀌었는지 비교용
    private var formKey: String {
        [emoji, name, date, startTime, endTime, String(sub ?? 0), String(milestone ?? 0), duration, dueDate].joined(separator: "|")
    }

    private func track(_ event: String, _ props: EventProps) {
        guard let s = analyticsSurface else { return }
        GD.track(event, ["surface": s, "goal_category_id": goal.goalCategoryId], props)
    }

    @ViewBuilder private var fields: some View {
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("이모지") {
                TextField("📝", text: $emoji)
                    .multilineTextAlignment(.center)
                    .inputStyle()
                    .goalMaxLength($emoji, 16)
            }
            .frame(width: 64)
            GoalFormField("Task 이름") {
                TextField("예: 영단어 50개 외우기", text: $name)
                    .inputStyle()
                    .goalMaxLength($name, 100)
                    .submitLabel(.done)
                    .onSubmit { submit(method: "keyboard_done") }
            }
        }
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("날짜") { GoalDateInput(value: $date) }
            if !areas.isEmpty {
                GoalFormField("세부 목표 (선택)") { GoalSelectMenu(selection: $sub, options: subOptions) }
            }
        }
        HStack(alignment: .top, spacing: 8) {
            GoalFormField("시작 (선택)") { GoalTimeInput(value: $startTime) }
            GoalFormField("종료 (선택)") {
                GoalTimeInput(value: $endTime,
                              suggested: startTime.isEmpty ? "09:30" : KST.minutesToTime(KST.timeToMinutes(startTime) + 30))
            }
        }
        if more {
            HStack(alignment: .top, spacing: 8) {
                GoalFormField("소요 시간 (분)") {
                    if hasTime {
                        Text(span.map { String($0) } ?? "")
                            .font(.pt(15))
                            .foregroundStyle(Color.ink3)
                            .padding(.horizontal, 14)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
                    } else {
                        TextField("30", text: $duration)
                            .keyboardType(.numberPad)
                            .inputStyle()
                    }
                }
                GoalFormField("마감일") { GoalDateInput(value: $dueDate, optional: true) }
            }
            // 마일스톤이 없는 목표(예: 기본 생활 습관)에선 고를 게 없어서 숨겨요
            if !milestones.isEmpty {
                GoalFormField("마일스톤") { GoalSelectMenu(selection: $milestone, options: milestoneOptions) }
            }
        }
        Text(hint).font(.pt(12)).foregroundStyle(Color.ink3)
    }

    private func submit(method: String) {
        guard !trimmedName.isEmpty, error == nil, !saving else { return }
        track("goal_task_list.save_click", [
            "is_new": task == nil, "days_from_today": GD.daysFromToday(date), "has_time": hasTime,
            "has_duration": !hasTime && durationNum != nil, "has_due_date": !dueDate.isEmpty,
            "has_milestone": milestone != nil, "has_sub_goal": sub != nil, "submit_method": method,
        ])
        let e = emoji.trimmingCharacters(in: .whitespacesAndNewlines)
        let input = GoalTaskInput(
            taskName: trimmedName,
            emoji: e.isEmpty ? nil : e,
            generalCategoryId: sub,
            milestoneId: milestone,
            date: date,
            startTime: hasTime ? startTime : nil,
            endTime: hasTime ? endTime : nil,
            durationMinutes: hasTime ? nil : durationNum.map { Int($0) },
            dueDate: dueDate.isEmpty ? nil : dueDate
        )
        let goalId = goal.goalCategoryId
        let taskId = task?.taskId
        saving = true
        Task { @MainActor in
            if let taskId {
                let r = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "Task를 수정했어요.") {
                    try await GoalAPI.updateGoalTask(goalId: goalId, taskId: taskId, input)
                }
                saving = false
                track("goal_task_list.result", GD.result(r != nil).merging(["task_action": "update"]) { $1 })
                if r != nil {
                    outcome = "saved"
                    onDone()
                }
            } else {
                let r = await Mutation.run(invalidate: [.goals, .timetable], successMessage: "Task를 추가했어요.") {
                    try await GoalAPI.createGoalTask(goalId: goalId, input)
                }
                saving = false
                track("goal_task_list.result", GD.result(r != nil).merging(["task_action": "create"]) { $1 })
                if let r {
                    outcome = "saved"
                    if let created = r { onCreated?(created) }
                    onDone()
                }
            }
        }
    }
}

#Preview("Task 추가") {
    ScrollView {
        VStack(spacing: 16) {
            GoalTaskFormView(goal: GoalPreviewData.goal, task: nil, onDone: {})
            GoalTaskFormView(goal: GoalPreviewData.goal, task: GoalPreviewData.tasks.tasks[0], onDone: {})
        }
        .padding(16)
    }
    .canvasBackground()
}
