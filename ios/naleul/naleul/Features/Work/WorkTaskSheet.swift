// Features/Work/WorkTaskSheet.swift
//
// 웹 features/work/ui/WorkTaskModal.tsx
// Task 자세히 보기 · 고치기 — 이름 · 칸 · 주제 · 하는 날 · 마감 · 메모.
// 바뀐 값만 보내요 (지운 값은 clear 플래그로). 반복 루틴 Task 는 완료 · 완료 취소만.

import SwiftUI
import UIKit

@MainActor
struct WorkTaskSheet: View {
    let goalId: Int
    let task: WorkTask
    let topics: [WorkTopic]
    /// 어디서 열었는지 (roadmap · board) — 앰플리튜드 surface
    let surface: String
    private let isPreview: Bool

    @State private var name: String
    @State private var topicId: Int?
    @State private var status: WorkColumn
    @State private var hasDate: Bool
    @State private var date: String
    @State private var hasDue: Bool
    @State private var dueDate: String
    @State private var memo: String
    @State private var saving = false
    @State private var confirmDelete = false
    /// 어떻게 닫혔는지 (저장 · 지우기 · 완료면 닫기 이벤트를 보내지 않아요)
    @State private var outcome: String?

    @Environment(\.dismiss) private var dismiss

    init(goalId: Int, task: WorkTask, topics: [WorkTopic], surface: String = "board", isPreview: Bool = false) {
        self.goalId = goalId
        self.task = task
        self.topics = topics
        self.surface = surface
        self.isPreview = isPreview
        let today = KST.today()
        _name = State(initialValue: task.taskName)
        _topicId = State(initialValue: task.topicId)
        _status = State(initialValue: task.status)
        _hasDate = State(initialValue: task.date != nil)
        _date = State(initialValue: task.date ?? today)
        _hasDue = State(initialValue: task.dueDate != nil)
        _dueDate = State(initialValue: task.dueDate ?? (task.date ?? today))
        _memo = State(initialValue: task.memo ?? "")
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var realTopics: [WorkTopic] { topics.filter { $0.topicId != nil } }

    private var statusOptions: [(value: WorkColumn, label: String)] {
        WorkColumn.allCases.map { c in (value: c, label: c.label) }
    }

    private var newDate: String? { hasDate ? date : nil }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        WK.track(event, ["task_id": task.taskId].merging(props) { $1 })
    }

    /// 바뀐 칸만 담은 요청 + 바뀐 칸 이름들 (changed_fields)
    private func buildPatch() -> (WorkTaskPatch, [String]) {
        var patch = WorkTaskPatch()
        var changed: [String] = []
        let n = trimmedName
        if !n.isEmpty && n != task.taskName { patch.taskName = n; changed.append("name") }
        if topicId != task.topicId {
            if let t = topicId { patch.topicId = t } else { patch.clearTopic = true }
            changed.append("topic")
        }
        let d = newDate
        if d != task.date {
            if let d { patch.date = d } else { patch.clearDate = true }
            changed.append("date")
        }
        let due: String? = hasDue ? dueDate : nil
        if due != task.dueDate {
            if let due { patch.dueDate = due } else { patch.clearDueDate = true }
            changed.append("due_date")
        }
        let m = memo.trimmingCharacters(in: .whitespacesAndNewlines)
        if m != (task.memo ?? "") { patch.memo = m; changed.append("memo") }
        if status != task.status { patch.status = status; changed.append("status") }
        return (patch, changed)
    }

    var body: some View {
        SheetScaffold(
            title: task.routine ? "반복 루틴 Task" : "Task",
            description: task.routine ? "루틴 Task는 목표의 반복 루틴에서 바꿔 주세요. 여기서는 완료만 할 수 있어요." : nil,
            dismissible: !saving,
            detents: task.routine ? Set([PresentationDetent.medium]) : Set([PresentationDetent.large])
        ) {
            if task.routine {
                routineBody
            } else {
                form
            }
        } footer: {
            if task.routine {
                NButton("닫기", variant: .secondary) {
                    outcome = "cancel_button"
                    dismiss()
                }
                NButton(task.isDone ? "완료 취소" : "완료", variant: .brand, icon: task.isDone ? nil : "checkmark",
                        loading: saving) {
                    Task { await toggleRoutine() }
                }
            } else {
                Button {
                    track("work_task_modal.delete_click", ["task_status": task.status.rawValue])
                    confirmDelete = true
                } label: {
                    HStack(spacing: 4) {
                        NSymbol("trash", size: 12, weight: .semibold)
                        Text("지우기")
                    }
                    .font(.pt(13, .semibold))
                    .foregroundStyle(Color.danger)
                    .padding(.horizontal, 8)
                    .frame(height: 40)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(saving)
                Spacer(minLength: 0)
                NButton("취소", variant: .secondary) {
                    outcome = "cancel_button"
                    dismiss()
                }
                    .disabled(saving)
                NButton("저장", loading: saving) { Task { await save() } }
                    .disabled(trimmedName.isEmpty)
            }
        }
        .confirmDialog(isPresented: $confirmDelete, title: "이 Task를 지울까요?", message: "되돌릴 수 없어요.",
                       confirmLabel: "지우기", tone: .danger, onCancel: { track("work_task_modal.delete_cancel") }) {
            track("work_task_modal.delete_confirm", ["task_status": task.status.rawValue])
            Task { await remove() }
        }
        .onChange(of: status) { old, new in
            track("work_task_modal.column_select", ["from_status": old.rawValue, "to_status": new.rawValue])
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            track("work_task_modal.close", [
                "is_routine": task.routine, "close_method": m, "is_dirty": !task.routine && !buildPatch().1.isEmpty,
            ])
        }
    }

    // MARK: 루틴 (읽기 전용)

    private var routineBody: some View {
        let today = KST.today()
        return VStack(alignment: .leading, spacing: 10) {
            Text(task.taskName)
                .font(.pt(16, .semibold))
                .strikethrough(task.isDone, color: Color.ink3)
                .foregroundStyle(task.isDone ? Color.ink3 : Color.ink)
            WorkTaskMetaRow(task: task, today: today, showTopic: true)
            if let d = task.date {
                let time: String = task.startTime.map { " · " + $0 } ?? ""
                Text(WorkFormat.dayLabel(d, today: today) + time)
                    .font(.pt(14))
                    .foregroundStyle(Color.ink2)
            }
        }
    }

    // MARK: 고치기

    private var form: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("이름", required: true)
                TextField("Task 이름", text: $name)
                    .inputStyle()
                    .submitLabel(.done)
                    .onChange(of: name) {
                        if name.count > 100 { name = String(name.prefix(100)) }
                    }
            }

            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("칸")
                SegmentPicker(selection: $status, options: statusOptions, fullWidth: true)
            }

            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("세부 목표")
                WorkTopicMenu(selection: $topicId, topics: realTopics, showDue: true)
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    FieldLabel("하는 날", hint: hasDate ? nil : "할 일 목록")
                    NSwitch(isOn: $hasDate, label: "하는 날")
                }
                if hasDate {
                    TimetableDateField(ymd: $date)
                }
                HStack(spacing: 6) {
                    WorkQuickDateChip(title: "오늘 하기") {
                        track("work_task_modal.quick_date_click", ["quick_date": "today"])
                        hasDate = true
                        date = KST.today()
                    }
                    if hasDate {
                        WorkQuickDateChip(title: "할 일 목록으로") {
                            track("work_task_modal.quick_date_click", ["quick_date": "backlog"])
                            hasDate = false
                        }
                    }
                }
                if task.startTime != nil && newDate != task.date {
                    FieldMessage(text: "날짜를 바꾸면 정해 둔 시간(\(task.startTime ?? ""))은 풀리고 그날 자동으로 다시 배치돼요.")
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    FieldLabel("마감", hint: hasDue ? nil : "선택")
                    NSwitch(isOn: $hasDue, label: "마감")
                }
                if hasDue {
                    TimetableDateField(ymd: $dueDate)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("메모", hint: "공유할 때 같이 보여요")
                WorkTextEditor(text: $memo, placeholder: "예: 디자인 시안 v2 기준으로", minHeight: 88, maxLength: 500)
            }
        }
    }

    // MARK: 저장

    @MainActor
    private func save() async {
        guard !saving else { return }
        let (patch, changed) = buildPatch()
        track("work_task_modal.save_click", [
            "changed_fields": changed.joined(separator: ","),
            "time_released": task.startTime != nil && newDate != task.date, "submit_method": "button",
        ])
        if let to = patch.status {
            track("work_task.status_change", WK.taskProps(task, surface: surface).merging([
                "goal_category_id": goalId, "from_status": task.status.rawValue, "to_status": to.rawValue,
                "move_method": "modal",
            ]) { $1 })
        }
        outcome = "saved"
        if patch == WorkTaskPatch() || isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let p = patch
        let gid = goalId
        let tid = task.taskId
        let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "Task를 수정했어요.") {
            try await WorkAPI.updateTask(goalId: gid, taskId: tid, p)
        }
        track("work_task_modal.result", GD.result(r != nil).merging(["task_action": "update"]) { $1 })
        if r != nil { dismiss() } else { outcome = nil }
    }

    @MainActor
    private func remove() async {
        if isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let tid = task.taskId
        let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "Task를 지웠어요.") {
            try await WorkAPI.deleteTask(taskId: tid)
        }
        track("work_task_modal.result", GD.result(r != nil).merging(["task_action": "delete"]) { $1 })
        if r != nil {
            outcome = "deleted"
            dismiss()
        }
    }

    @MainActor
    private func toggleRoutine() async {
        let to: WorkColumn = task.isDone ? .NOT_STARTED : .DONE
        track("work_task.status_change", WK.taskProps(task, surface: surface).merging([
            "goal_category_id": goalId, "from_status": task.status.rawValue, "to_status": to.rawValue, "move_method": "modal",
        ]) { $1 })
        outcome = "saved"
        if isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let tid = task.taskId
        let current: TaskStatus = task.isDone ? .COMPLETED : .TODO
        let msg = task.isDone ? "완료를 취소했어요." : "완료했어요."
        let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: msg) {
            try await GoalAPI.toggleTask(taskId: tid, currentStatus: current)
        }
        track("work_task.move_result", GD.result(r != nil).merging([
            "surface": surface, "to_status": to.rawValue, "move_method": "modal",
        ]) { $1 })
        if r == nil { outcome = nil }
        if r != nil {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            dismiss()
        }
    }
}

/// "오늘 하기" · "할 일 목록으로"
private struct WorkQuickDateChip: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .padding(.horizontal, 10)
                .frame(height: 28)
                .overlay(Capsule().stroke(Color.line, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

#Preview("Task") {
    let b = WorkSampleData.board
    return WorkTaskSheet(goalId: 2, task: b.board.inProgress[0], topics: b.topics, isPreview: true)
}

#Preview("할 일 목록 Task") {
    let b = WorkSampleData.board
    return WorkTaskSheet(goalId: 2, task: b.board.notStarted[1], topics: b.topics, isPreview: true)
}

#Preview("반복 루틴") {
    let t = WorkSampleData.task(77, "주간 회의", topic: nil, nil, .NOT_STARTED, date: KST.today(), startTime: "10:00",
                                routine: true)
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            WorkTaskSheet(goalId: 2, task: t, topics: [], isPreview: true)
        }
}
