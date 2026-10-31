// Features/Work/WorkTopicSheet.swift
//
// 웹 WorkBoardView.tsx AddTopic · TopicEditor
// 주제 추가 · 고치기 — 이름(30자) · 마감. 고칠 때는 "끝냈어요 / 다시 열기"와 "주제 지우기"도.

import SwiftUI

@MainActor
struct WorkTopicSheet: View {
    let goalId: Int
    /// nil 이면 새 주제
    let topic: WorkTopic?
    /// 지금 주제 수 (앰플리튜드 add_topic_submit 의 topic_count)
    let topicCount: Int
    private let isPreview: Bool

    @State private var name: String
    @State private var noDue: Bool
    @State private var dueDate: String
    @State private var saving = false
    @State private var confirmDelete = false
    /// 어떻게 닫혔는지 (저장 · 끝냈어요 · 지우기면 취소 이벤트를 보내지 않아요)
    @State private var outcome: String?

    @Environment(\.dismiss) private var dismiss

    init(goalId: Int, topic: WorkTopic?, topicCount: Int = 0, isPreview: Bool = false) {
        self.goalId = goalId
        self.topic = topic
        self.topicCount = topicCount
        self.isPreview = isPreview
        _name = State(initialValue: topic?.name ?? "")
        if let topic {
            _noDue = State(initialValue: topic.dueDate == nil)
            _dueDate = State(initialValue: topic.dueDate ?? KST.addDays(KST.today(), 7))
        } else {
            _noDue = State(initialValue: false)
            _dueDate = State(initialValue: KST.addDays(KST.today(), 7))
        }
    }

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var isEdit: Bool { topic != nil }
    private var newDue: String? { noDue ? nil : dueDate }

    /// 처음 연 상태에서 바뀌었는지
    private var isDirty: Bool {
        if let topic { return trimmed != topic.name || newDue != topic.dueDate }
        return !trimmed.isEmpty
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        WK.track(event, goalId: goalId, props)
    }

    private func trackResult(_ action: String, _ ok: Bool) {
        track("work_roadmap.topic_result", GD.result(ok).merging([
            "topic_id": topic?.topicId, "topic_action": action,
        ]) { $1 })
    }

    var body: some View {
        SheetScaffold(
            title: isEdit ? "세부 목표 고치기" : "세부 목표 추가",
            description: isEdit ? nil : "포트폴리오 · 이력서 제출처럼 큰 단계를 세부 목표로 만들고, 마감을 정해 보세요.",
            dismissible: !saving,
            detents: [.medium, .large]
        ) {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    FieldLabel("이름", hint: "\(name.count)/30", required: true)
                    TextField("예: 이력서 제출", text: $name)
                        .inputStyle()
                        .submitLabel(.done)
                        .onSubmit { Task { await save(method: "keyboard_done") } }
                        .onChange(of: name) {
                            if name.count > 30 { name = String(name.prefix(30)) }
                        }
                }

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        FieldLabel("마감 없이")
                        NSwitch(isOn: $noDue, label: "마감 없이")
                    }
                    if noDue {
                        FieldMessage(text: "마감 없는 세부 목표는 로드맵 맨 뒤에 놓여요.")
                    } else {
                        TimetableDateField(ymd: $dueDate)
                    }
                }

                if let topic {
                    editActions(topic)
                }
            }
        } footer: {
            NButton("취소", variant: .secondary) {
                outcome = "cancel_button"
                dismiss()
            }
                .disabled(saving)
            NButton(isEdit ? "저장" : "만들기", loading: saving) { Task { await save(method: "button") } }
                .disabled(trimmed.isEmpty)
        }
        .confirmDialog(isPresented: $confirmDelete, title: "이 세부 목표를 지울까요?",
                       message: "들어 있던 Task는 '세부 목표 없음'으로 옮겨져요",
                       confirmLabel: "지우기", tone: .danger, onCancel: {
            track("work_roadmap.topic_delete_cancel", ["topic_id": topic?.topicId])
        }) {
            track("work_roadmap.topic_delete_confirm", ["topic_id": topic?.topicId, "topic_task_count": topic?.remaining])
            Task { await remove() }
        }
        .onDisappear {
            guard let m = GD.closeMethod(outcome) else { return }
            if let topic {
                track("work_roadmap.topic_edit_cancel", ["topic_id": topic.topicId, "close_method": m, "is_dirty": isDirty])
            } else {
                track("work_roadmap.add_topic_cancel", ["close_method": m, "is_dirty": isDirty])
            }
        }
    }

    private func editActions(_ t: WorkTopic) -> some View {
        // Task 를 모두 끝내서 "끝"인 주제는 되돌릴 게 없어요
        let autoDone = t.isDone && !t.completed
        return VStack(alignment: .leading, spacing: 10) {
            Rectangle().fill(Color.borderSoft).frame(height: 1)
            if autoDone {
                Label("Task를 모두 끝내서 끝난 세부 목표예요", systemImage: "checkmark.circle.fill")
                    .font(.pt(13, .medium))
                    .foregroundStyle(Color.success)
            } else {
                NButton(t.completed ? "다시 열기" : "끝냈어요",
                        variant: t.completed ? NButtonVariant.secondary : NButtonVariant.brand,
                        icon: t.completed ? "arrow.uturn.backward" : "checkmark",
                        loading: saving, fullWidth: true) {
                    Task { await toggleCompleted(t) }
                }
            }
            Button {
                track("work_roadmap.topic_delete_click", [
                    "topic_id": t.topicId, "topic_task_count": t.remaining, "has_due_date": t.dueDate != nil,
                ])
                confirmDelete = true
            } label: {
                HStack(spacing: 4) {
                    NSymbol("trash", size: 12, weight: .semibold)
                    Text("세부 목표 지우기")
                }
                .font(.pt(13, .semibold))
                .foregroundStyle(Color.danger)
                .frame(height: 36)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(saving)
        }
    }

    // MARK: 저장

    @MainActor
    private func save(method: String) async {
        let n = trimmed
        guard !n.isEmpty, !saving else { return }
        let due: String? = noDue ? nil : dueDate
        if let topic {
            var changed: [String] = []
            if n != topic.name { changed.append("name") }
            if due != topic.dueDate { changed.append("due_date") }
            track("work_roadmap.topic_edit_save", [
                "topic_id": topic.topicId, "has_due_date": due != nil, "changed_fields": changed.joined(separator: ","),
                "submit_method": method,
            ])
        } else {
            track("work_roadmap.add_topic_submit", ["has_due_date": due != nil, "topic_count": topicCount, "submit_method": method])
        }
        outcome = "saved"
        if isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let gid = goalId
        if let topic, let id = topic.topicId {
            var patch = WorkTopicPatch()
            if n != topic.name { patch.name = n }
            if due != topic.dueDate {
                if let due { patch.dueDate = due } else { patch.clearDueDate = true }
            }
            if patch == WorkTopicPatch() {
                dismiss()
                return
            }
            let p = patch
            let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "세부 목표를 고쳤어요.") {
                try await WorkAPI.updateTopic(goalId: gid, topicId: id, p)
            }
            trackResult("update", r != nil)
            if r != nil { dismiss() } else { outcome = nil }
        } else {
            let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "'\(n)' 세부 목표를 만들었어요.") {
                try await WorkAPI.createTopic(goalId: gid, name: n, dueDate: due)
            }
            trackResult("create", r != nil)
            if r != nil { dismiss() } else { outcome = nil }
        }
    }

    @MainActor
    private func toggleCompleted(_ t: WorkTopic) async {
        guard let id = t.topicId, !saving else { return }
        let completed = !t.completed
        track("work_roadmap.topic_complete_toggle", [
            "topic_id": id, "checked": completed, "topic_task_count": t.remaining, "d_day": t.dDay, "entry_point": "sheet",
        ])
        if isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let gid = goalId
        let r = await Mutation.run(invalidate: WorkAPI.changed,
                                   successMessage: completed ? "세부 목표를 끝냈어요. 수고했어요!" : "세부 목표를 다시 열었어요.") {
            try await WorkAPI.updateTopic(goalId: gid, topicId: id, WorkTopicPatch(completed: completed))
        }
        trackResult(completed ? "complete" : "reopen", r != nil)
        if r != nil {
            outcome = "saved"
            dismiss()
        }
    }

    @MainActor
    private func remove() async {
        guard let id = topic?.topicId else { return }
        if isPreview {
            dismiss()
            return
        }
        saving = true
        defer { saving = false }
        let gid = goalId
        let r = await Mutation.run(invalidate: WorkAPI.changed,
                                   successMessage: "세부 목표를 지웠어요. 들어 있던 Task는 '세부 목표 없음'으로 옮겼어요.") {
            try await WorkAPI.deleteTopic(goalId: gid, topicId: id)
        }
        trackResult("delete", r != nil)
        if r != nil {
            outcome = "deleted"
            dismiss()
        }
    }
}

#Preview("세부 목표 추가") {
    WorkTopicSheet(goalId: 2, topic: nil, isPreview: true)
}

#Preview("세부 목표 고치기") {
    WorkTopicSheet(goalId: 2, topic: WorkSampleData.timeline[2], isPreview: true)
}

#Preview("끝낸 세부 목표") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            WorkTopicSheet(goalId: 2, topic: WorkSampleData.timeline[0], isPreview: true)
        }
}
