// Features/Calendar/TimetableTaskDetailSheet.swift
//
// Task 블록을 누르면 뜨는 상세 시트.
// 웹 원본: features/timetable/ui/TaskDetail.tsx
//  - 상태 표시 (완료 · 놓침 · 루틴 · 미션 · 약속 · 직접 정한 시간 / 자동 배치)
//  - 이름 · 시간 · 세부 목표 · 마일스톤 · 마감일 — 누르면 그 자리에서 수정 (노션처럼)
//  - 배치 이유 (명세: 항상 설명)
//  - 완료 / 완료 취소 · ⋯ (이어진 N개 묶어 옮기기 · 고정 풀기 · 시간 미정으로 · 삭제)
//  - "계획과 다른 시간에 했어요" · "이 시간에 다른 일을 했어요"
// 웹의 키보드 안내(Enter 저장 · Esc 취소 · Del)는 아이폰에 없어서 뺐어요.

import SwiftUI

private enum TimetableDetailEdit { case title, time, due, priority }

/// 상세 시트에 보여줄 시간 문장
enum TimetableDetailText {
    /// "11월 18일 (수) · 09:00 – 10:30 (1시간 30분)"
    static func rangeText(_ startAt: String, _ endAt: String?) -> String {
        let base = String(startAt.prefix(10))
        let s = KST.minutesFrom(base, startAt)
        let e = endAt.map { KST.minutesFrom(base, $0) }
        var range = KST.minutesToTime(s)
        if let e {
            range = "\(KST.minutesToTime(s)) – \(e >= 1440 ? "다음 날 \(KST.minutesToTime(e))" : KST.minutesToTime(e))"
        }
        var out = "\(KST.formatMonthDay(base)) · \(range)"
        if let e, e > s { out += " (\(KST.formatDuration(e - s)))" }
        return out
    }

    static func timeLine(_ t: TimeBlockTask) -> String {
        guard let ps = t.plannedStartAt else {
            if let day = t.date ?? t.scheduledDate { return "\(KST.formatMonthDay(day)) · 시간 미정" }
            return "날짜 미정"
        }
        return rangeText(ps, t.plannedEndAt)
    }
}

struct TimetableTaskDetailSheet: View {
    let board: TimetableBoardModel
    let item: TimeBlockTask

    @State private var editing: TimetableDetailEdit?
    @State private var draftEmoji = ""
    @State private var draftName = ""
    /// 세부 목표 · 마일스톤을 고를 때 쓰는 목표 정보
    @State private var goal: GoalCategory?
    /// 업무형 목표의 세부 목표 (로드맵 · WorkTopic)
    @State private var workTopics: [WorkTopic] = []
    @State private var saving = false
    /// 시트가 닫힌 이유 — 작업이 끝나 닫히거나 다른 시트로 넘어가면 "next" (task_detail.close 안 보냄)
    @State private var outcome: String?
    /// "다른 날로 → 날짜 지정"을 눌러 날짜 고르기를 펼쳤는지
    @State private var pickingDate = false
    @State private var pickedDate = Date()

    init(board: TimetableBoardModel, task: TimeBlockTask) {
        self.board = board
        self.item = task
    }

    var body: some View {
        let t = item
        let perm = TimetableRules.permissions(t)
        SheetScaffold(title: nil) {
            VStack(alignment: .leading, spacing: 0) {
                TimetableTaskBadges(task: t)
                titleSection(t, perm).padding(.top, 10)
                propsSection(t, perm).padding(.top, 14)
                TimetableReasonSection(task: t).padding(.top, 16)
                actionSection(t, perm).padding(.top, 20)
            }
        }
        .task(id: t.taskId) { await loadGoal(t, perm) }
        .onAppear {
            // [UX] 배치 이유를 실제로 보는지 (자동 배치 · 고정 안 한 Task)
            if t.placementReason != nil && !t.locked {
                board.sheetTrack("task_detail.reason_view", [
                    "task_id": t.taskId, "reason_codes": (t.placementReasonCodes ?? []).joined(separator: ","),
                ])
            }
        }
        .onDisappear {
            if let e = editing { board.sheetTrack("task_detail.field_cancel", ["edit_field": fieldName(e), "close_method": "sheet_close"]) }
            guard let m = GD.closeMethod(outcome) else { return }
            board.sheetTrack("task_detail.close", ["task_id": t.taskId, "close_method": m])
        }
        .accessibilityLabel("Task 상세")
    }

    // MARK: 제목

    @ViewBuilder
    private func titleSection(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        if editing == .title {
            VStack(alignment: .trailing, spacing: 8) {
                HStack(spacing: 6) {
                    TextField("🙂", text: $draftEmoji)
                        .multilineTextAlignment(.center)
                        .frame(width: 28)
                        .inputStyle()
                        .accessibilityLabel("이모지")
                    TextField("", text: $draftName)
                        .font(.pt(16, .semibold))
                        .submitLabel(.done)
                        .onSubmit { saveTitle(t) }
                        .inputStyle()
                        .accessibilityLabel("Task 이름")
                }
                HStack(spacing: 8) {
                    NButton("취소", variant: .ghost, size: .sm) { cancelEdit(.title, "cancel_button") }
                    NButton("저장", size: .sm, loading: saving) { saveTitle(t) }
                }
            }
            .onChange(of: draftEmoji) { _, v in
                if v.count > 16 { draftEmoji = String(v.prefix(16)) }
            }
            .onChange(of: draftName) { _, v in
                if v.count > 100 { draftName = String(v.prefix(100)) }
            }
        } else if perm.editContent {
            Button { startTitle(t) } label: {
                HStack(alignment: .top, spacing: 6) {
                    titleText(t)
                    NSymbol("pencil", size: 13)
                        .foregroundStyle(Color.ink4)
                        .padding(.top, 5)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Task 이름 수정")
        } else {
            titleText(t)
        }
    }

    private func titleText(_ t: TimeBlockTask) -> some View {
        let done = t.taskStatus == .COMPLETED
        return Text(TimetableRules.title(t.emoji, t.taskName))
            .font(.pt(16, .semibold))
            .foregroundStyle(done ? Color.ink3 : Color.ink)
            .strikethrough(done)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func startTitle(_ t: TimeBlockTask) {
        draftEmoji = t.emoji ?? ""
        draftName = t.taskName
        openEdit(.title, t)
    }

    // MARK: 앰플리튜드 (그 자리 고치기)

    private func fieldName(_ e: TimetableDetailEdit) -> String {
        switch e {
        case .title: return "title"
        case .time: return "time"
        case .due: return "due"
        case .priority: return "priority"
        }
    }

    /// 항목 고치기 열기 — 다른 항목을 고치던 중이면 그건 취소(other_open)
    private func openEdit(_ e: TimetableDetailEdit, _ t: TimeBlockTask) {
        if let cur = editing, cur != e {
            board.sheetTrack("task_detail.field_cancel", ["edit_field": fieldName(cur), "close_method": "other_open"])
        }
        board.sheetTrack("task_detail.field_edit_open", ["task_id": t.taskId, "edit_field": fieldName(e),
                                                         "source_type": t.sourceType.rawValue])
        editing = e
    }

    private func cancelEdit(_ e: TimetableDetailEdit, _ method: String) {
        board.sheetTrack("task_detail.field_cancel", ["edit_field": fieldName(e), "close_method": method])
        editing = nil
    }

    private func trackSave(_ t: TimeBlockTask, _ field: String) {
        board.sheetTrack("task_detail.field_save", ["task_id": t.taskId, "edit_field": field, "source_type": t.sourceType.rawValue])
    }

    private func saveTitle(_ t: TimeBlockTask) {
        if saving { return }
        let name = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        let e = draftEmoji.trimmingCharacters(in: .whitespacesAndNewlines)
        let emoji: String? = e.isEmpty ? nil : e
        if name.isEmpty {
            // [UX] 비우고 저장하면 안내 없이 원래 이름으로
            board.sheetTrack("task_detail.title_empty_cancel", ["task_id": t.taskId])
            editing = nil
            return
        }
        if name == t.taskName && emoji == t.emoji {
            editing = nil
            return
        }
        var input = TimetableRules.currentInput(t)
        input.taskName = name
        input.emoji = emoji
        trackSave(t, "title")
        saveContent(t, input, field: "title")
    }

    // MARK: 속성 (시간 · 세부 목표 · 마일스톤 · 마감일 …)

    private func propsSection(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        let actual = t.taskStatus == .COMPLETED && t.actualStartAt != nil && t.actualEndAt != nil
        return VStack(alignment: .leading, spacing: 10) {
            if actual, let s = t.actualStartAt {
                TimetablePropRow(icon: {
                    NSymbol("checkmark", size: 13, weight: .bold).foregroundStyle(Color.success)
                }) {
                    HStack(alignment: .top, spacing: 4) {
                        Text("실제").foregroundStyle(Color.ink3)
                        if let segs = t.actualSegments, segs.count > 1 {
                            Text(segs.map { TimetableDetailText.rangeText($0.start, $0.end) }.joined(separator: " · "))
                        } else {
                            Text(TimetableDetailText.rangeText(s, t.actualEndAt))
                        }
                    }
                }
            }
            timeProp(t, perm, actual: actual)
            priorityProp(t, perm)
            goalProp(t, perm)
            milestoneProp(t, perm)
            dueProp(t, perm)
            if t.carryOverCount > 0 {
                TimetablePropRow(icon: { icon("arrow.uturn.backward") }) {
                    Text("\(t.carryOverCount)번 미뤄진 Task예요")
                }
            }
            if t.sourceType == .ROUTINE {
                TimetablePropRow(icon: { icon("square.stack.3d.up") }) {
                    Text("이름은 목표 상세의 루틴에서 바꿀 수 있어요.").font(.pt(13)).foregroundStyle(Color.ink3)
                }
            }
        }
    }

    private func icon(_ name: String) -> some View {
        NSymbol(name, size: 13).foregroundStyle(Color.ink3)
    }

    @ViewBuilder
    private func timeProp(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions, actual: Bool) -> some View {
        if editing == .time {
            TimetableInlineTimeEditor(board: board, task: t, onDone: { editing = nil },
                                      onCancel: { cancelEdit(.time, "cancel_button") })
        } else {
            let row = TimetablePropRow(editable: perm.editTime, icon: { icon("clock") }) {
                HStack(alignment: .top, spacing: 4) {
                    if actual {
                        Text("계획").foregroundStyle(Color.ink3)
                    }
                    Text(TimetableDetailText.timeLine(t)).foregroundStyle(actual ? Color.ink3 : Color.ink2)
                }
            }
            if perm.editTime {
                Button { openEdit(.time, t) } label: { row }
                    .buttonStyle(.plain)
                    .accessibilityLabel("시간 수정")
            } else {
                row
            }
        }
    }

    /// 중요도 — A 는 TimeBlock 에서 배지 + 테두리로 강조돼요 (웹 TaskDetail 중요도 행)
    @ViewBuilder
    private func priorityProp(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        let p = t.taskPriority ?? .fallback
        if editing == .priority {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 4) {
                    ForEach(TaskPriority.allCases, id: \.self) { c in
                        Chip(c.rawValue, selected: c == p, size: .sm) { pickPriority(t, c) }
                            .accessibilityLabel("중요도 \(c.rawValue) \(c.label)")
                    }
                }
                .disabled(saving)
                HStack(spacing: 6) {
                    Text(t.sourceType == .MANUAL ? "A는 캘린더에서 눈에 띄게 보여요" : "이 날 Task만 바뀌어요 · A는 눈에 띄게 보여요")
                        .font(.pt(11))
                        .foregroundStyle(Color.ink3)
                    Spacer(minLength: 0)
                    NButton("닫기", variant: .ghost, size: .sm) { cancelEdit(.priority, "cancel_button") }
                }
            }
            .padding(10)
            .background(Color.brandSoft.opacity(0.4), in: RoundedRectangle(cornerRadius: Radius.xl))
        } else {
            let row = TimetablePropRow(editable: perm.editPriority, icon: {
                TimetablePriorityMark(priority: p, color: Color.goal(t.goalColorCode))
            }) {
                (Text("중요도 \(p.rawValue)") + Text(" · \(p.label)").foregroundColor(Color.ink3))
            }
            if perm.editPriority {
                Button { openEdit(.priority, t) } label: { row }
                    .buttonStyle(.plain)
                    .accessibilityLabel("중요도 수정")
            } else {
                row
            }
        }
    }

    private func pickPriority(_ t: TimeBlockTask, _ p: TaskPriority) {
        let prev = t.taskPriority ?? .fallback
        if p == prev {
            cancelEdit(.priority, "same_select")
            return
        }
        guard !saving else { return }
        board.sheetTrack("task_detail.field_save", [
            "task_id": t.taskId, "edit_field": "priority", "source_type": t.sourceType.rawValue,
            "priority": p.rawValue, "prev_priority": prev.rawValue,
        ])
        saving = true
        Task {
            let ok = await board.changePriority(taskId: t.taskId, p)
            saving = false
            board.trackFieldResult("priority", ok: ok)
            if ok { editing = nil }
        }
    }

    /// 업무형 목표인지 (목표 정보를 불러왔으면 그걸로, 아니면 세부 목표가 붙어 있는지로)
    private func isWork(_ t: TimeBlockTask) -> Bool { goal?.isWorkGoal ?? (t.workTopicId != nil) }

    @ViewBuilder
    private func goalProp(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        if let gname = t.goalCategoryName, isWork(t) {
            let row = TimetablePropRow(editable: perm.editContent, icon: {
                Circle().fill(Color.goal(t.goalColorCode)).frame(width: 11, height: 11).padding(.top, 2)
            }) {
                Text(TimetableRules.title(t.goalEmoji, gname))
                    + Text(" › \(t.workTopicName ?? "세부 목표 없음")").foregroundColor(Color.ink3)
            }
            if perm.editContent, let gid = t.goalCategoryId {
                let choices = workTopics.filter { $0.topicId != nil && (!$0.completed || $0.topicId == t.workTopicId) }
                Menu {
                    Button { pickWorkTopic(t, gid, nil) } label: {
                        if t.workTopicId == nil { Label("세부 목표 없음", systemImage: "checkmark") } else { Text("세부 목표 없음") }
                    }
                    ForEach(choices, id: \.topicId) { x in
                        let title = [x.emoji, x.name].compactMap { $0 }.joined(separator: " ")
                        Button { pickWorkTopic(t, gid, x.topicId) } label: {
                            if x.topicId == t.workTopicId { Label(title, systemImage: "checkmark") } else { Text(title) }
                        }
                    }
                } label: { row }
                .disabled(saving)
                .accessibilityLabel("세부 목표 수정")
                .task(id: gid) { workTopics = (try? await WorkAPI.board(goalId: gid))?.topics ?? [] }
            } else {
                row
            }
        } else if let gname = t.goalCategoryName {
            let row = TimetablePropRow(editable: perm.editContent, icon: {
                Circle().fill(Color.goal(t.goalColorCode)).frame(width: 11, height: 11).padding(.top, 2)
            }) {
                goalText(t, gname)
            }
            if perm.editContent {
                Menu {
                    if let g = goal {
                        ForEach(g.generalCategories.filter { $0.generalCategoryStatus != .DELETED }) { s in
                            Button { pickSub(t, s.generalCategoryId) } label: {
                                if s.generalCategoryId == t.generalCategoryId {
                                    Label(s.generalCategoryName, systemImage: "checkmark")
                                } else {
                                    Text(s.generalCategoryName)
                                }
                            }
                        }
                    } else {
                        Text("불러오는 중…")
                    }
                } label: { row }
                .disabled(saving)
                .accessibilityLabel("세부 목표 수정")
            } else {
                row
            }
        }
    }

    private func goalText(_ t: TimeBlockTask, _ name: String) -> Text {
        var text = Text(TimetableRules.title(t.goalEmoji, name))
        if t.goalTemporary { text = text + Text(" · 임시 목표").foregroundColor(Color.ink3) }
        if let g = t.generalCategoryName { text = text + Text(" › \(g)").foregroundColor(Color.ink3) }
        return text
    }

    @ViewBuilder
    private func milestoneProp(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        if t.milestoneTitle != nil || perm.editContent {
            let row = TimetablePropRow(editable: perm.editContent, icon: { icon("flag") }) {
                if let m = t.milestoneTitle {
                    Text(m)
                } else {
                    Text("마일스톤 없음").foregroundStyle(Color.ink4)
                }
            }
            if perm.editContent {
                Menu {
                    Button("없음") { pickMilestone(t, nil) }
                    if let g = goal {
                        let list = (g.milestones ?? []).sorted { $0.dueDate < $1.dueDate }
                        ForEach(list) { m in
                            Button { pickMilestone(t, m.milestoneId) } label: {
                                if m.milestoneId == t.milestoneId {
                                    Label(m.title, systemImage: "checkmark")
                                } else {
                                    Text(m.title)
                                }
                            }
                        }
                    } else {
                        Text("불러오는 중…")
                    }
                } label: { row }
                .disabled(saving)
                .accessibilityLabel("마일스톤 수정")
            } else {
                row
            }
        }
    }

    @ViewBuilder
    private func dueProp(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        if t.dueDate != nil || perm.editContent {
            if editing == .due {
                HStack(spacing: 8) {
                    icon("calendar.badge.clock").frame(width: 18)
                    TimetableDateField(ymd: Binding(get: { t.dueDate ?? KST.today() },
                                                    set: { v in if v != t.dueDate { pickDue(t, v) } }))
                    Spacer(minLength: 0)
                    if t.dueDate != nil {
                        NButton("지우기", variant: .ghost, size: .sm, icon: "xmark") { pickDue(t, nil) }
                            .disabled(saving)
                    }
                    NButton("닫기", variant: .ghost, size: .sm) { cancelEdit(.due, "close_button") }
                }
            } else {
                let row = TimetablePropRow(editable: perm.editContent, icon: { icon("calendar.badge.clock") }) {
                    if let d = t.dueDate {
                        Text("\(KST.formatMonthDay(d))까지")
                    } else {
                        Text("마감일 없음").foregroundStyle(Color.ink4)
                    }
                }
                if perm.editContent {
                    Button { openEdit(.due, t) } label: { row }
                        .buttonStyle(.plain)
                        .accessibilityLabel("마감일 수정")
                } else {
                    row
                }
            }
        }
    }

    // MARK: 저장 (목표 상세 Task 수정 API)

    private func saveContent(_ t: TimeBlockTask, _ input: GoalTaskInput, field: String) {
        guard let gid = t.goalCategoryId, !saving else { return }
        saving = true
        Task {
            let ok = await board.updateTaskContent(goalId: gid, taskId: t.taskId, input)
            saving = false
            board.trackFieldResult(field, ok: ok)
            if ok { editing = nil }
        }
    }

    private func pickWorkTopic(_ t: TimeBlockTask, _ goalId: Int, _ topicId: Int?) {
        if topicId == t.workTopicId { return }
        guard !saving else { return }
        trackSave(t, "sub")
        saving = true
        Task {
            let ok = await board.updateWorkTopic(goalId: goalId, taskId: t.taskId, topicId: topicId)
            saving = false
            board.trackFieldResult("sub", ok: ok)
        }
    }

    private func pickSub(_ t: TimeBlockTask, _ id: Int) {
        if id == t.generalCategoryId { return }
        var input = TimetableRules.currentInput(t)
        input.generalCategoryId = id
        trackSave(t, "sub")
        saveContent(t, input, field: "sub")
    }

    private func pickMilestone(_ t: TimeBlockTask, _ id: Int?) {
        if id == t.milestoneId { return }
        var input = TimetableRules.currentInput(t)
        input.milestoneId = id
        trackSave(t, "milestone")
        saveContent(t, input, field: "milestone")
    }

    private func pickDue(_ t: TimeBlockTask, _ date: String?) {
        var input = TimetableRules.currentInput(t)
        input.dueDate = date
        trackSave(t, "due")
        saveContent(t, input, field: "due")
    }

    private func loadGoal(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) async {
        // 세부 목표 · 마일스톤을 고칠 수 있을 때만 목표 정보를 불러와요
        guard perm.editContent, let gid = t.goalCategoryId, !board.isOffline, goal == nil else { return }
        goal = try? await GoalAPI.goal(gid)
    }

    // MARK: 아래 버튼

    @ViewBuilder
    private func actionSection(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions) -> some View {
        let done = t.taskStatus == .COMPLETED
        let chain = board.chainSize(t)
        let now = KST.now()
        let nowAt = KST.toDateTime(now.date, now.minutes)
        VStack(spacing: 4) {
            if board.confirmDeleteId == t.taskId {
                TimetableInlineConfirm(
                    message: "'\(t.taskName)'을 삭제할까요?",
                    detail: t.sourceType == .ROUTINE ? "이 날짜의 루틴 Task 하나만 지워져요." : "되돌릴 수 없어요.",
                    loading: board.deleting,
                    onConfirm: {
                        board.sheetTrack("task_detail.delete_confirm", ["task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                                                        "submit_method": "button"])
                        outcome = "next"
                        Task { await board.confirmDelete() }
                    },
                    onCancel: {
                        board.sheetTrack("task_detail.delete_cancel", ["task_id": t.taskId, "close_method": "cancel_button"])
                        board.cancelDelete()
                    }
                )
            } else {
                HStack(spacing: 8) {
                    NButton(done ? "완료 취소" : "완료", variant: done ? .secondary : .brand,
                            icon: done ? nil : "checkmark",
                            loading: board.togglingId == t.taskId || board.busyTask, fullWidth: true) {
                        board.sheetTrack("task_detail.complete_click", [
                            "task_id": t.taskId, "source_type": t.sourceType.rawValue,
                            "check_action": done ? "uncomplete" : "complete",
                            "is_missed": t.missed, "is_scheduled": t.plannedStartAt != nil,
                        ])
                        outcome = "next"
                        Task { await board.toggleComplete(t, closeOnSuccess: true) }
                    }
                    // 계획과 다른 시간에 · 나눠서 했을 때 — 완료만큼 자주 써서 같은 크기 버튼으로 (2026-10, 웹과 같게)
                    if t.plannedStartAt != nil && t.sourceType != .MISSION {
                        NButton(done ? "실제 시간 고치기" : "다른 시간에 했어요", variant: .secondary, icon: "clock", fullWidth: true) {
                            board.sheetTrack("task_detail.actual_time_click", ["task_id": t.taskId, "task_status": t.taskStatus.rawValue])
                            outcome = "next"
                            board.completeWithTime(t)
                        }
                    }
                    if perm.unlock || perm.unschedule || perm.delete || chain > 1 {
                        moreMenu(t, perm, chain: chain)
                    }
                }
                if canMoveDate(t) {
                    moveDateRow(t)
                }
                // 계획 시각이 이미 시작됐는데 안 했으면: 그 시간에 실제로 한 일을 남길 수 있게
                if !done, let ps = t.plannedStartAt, ps <= nowAt {
                    Button {
                        board.sheetTrack("task_detail.record_instead_click", ["task_id": t.taskId, "source_type": t.sourceType.rawValue])
                        outcome = "next"
                        board.recordInstead(t)
                    } label: {
                        Text("이 시간에 다른 일을 했어요")
                            .font(.pt(13, .semibold))
                            .foregroundStyle(Color.success)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(Color.successSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: Radius.lg))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: 다른 날로 (2026-10)

    /// 완료 · 미션은 못 옮겨요. 루틴은 그날 인스턴스라 시간이 있을 때만 (같은 시각으로)
    private func canMoveDate(_ t: TimeBlockTask) -> Bool {
        guard TimetableRules.canDragTask(t) else { return false }
        if t.sourceType == .ROUTINE && t.plannedStartAt == nil { return false }
        return true
    }

    @ViewBuilder
    private func moveDateRow(_ t: TimeBlockTask) -> some View {
        let today = KST.today()
        let current = TimetableRules.routineDay(t)
        let tomorrow = KST.addDays(today, 1)
        let dayAfter = KST.addDays(today, 2)
        VStack(spacing: 8) {
            Menu {
                Button { move(t, tomorrow, "tomorrow") } label: {
                    Label("내일 (\(KST.formatMonthDay(tomorrow)))", systemImage: "arrow.right")
                }
                .disabled(current == tomorrow)
                Button { move(t, dayAfter, "day_after") } label: {
                    Label("모레 (\(KST.formatMonthDay(dayAfter)))", systemImage: "arrow.right.to.line")
                }
                .disabled(current == dayAfter)
                Button {
                    pickedDate = KST.date(from: max(current ?? tomorrow, tomorrow))
                    pickingDate = true
                } label: { Label("날짜 지정…", systemImage: "calendar") }
            } label: {
                HStack(spacing: 6) {
                    NSymbol("calendar.badge.clock", size: 13, weight: .semibold)
                    Text("다른 날로 옮기기").font(.pt(14, .semibold))
                }
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
            }
            .simultaneousGesture(TapGesture().onEnded {
                board.sheetTrack("task_detail.move_date_click", ["task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                                                 "is_scheduled": t.plannedStartAt != nil])
            })
            .disabled(board.busyTask)
            .accessibilityLabel("다른 날로 옮기기")

            if pickingDate {
                VStack(alignment: .leading, spacing: 8) {
                    DatePicker("옮길 날짜", selection: $pickedDate,
                               in: KST.date(from: today)...,
                               displayedComponents: .date)
                        .datePickerStyle(.graphical)
                        .environment(\.locale, Locale(identifier: "ko_KR"))
                        .tint(Color.actionPrimary)
                    Text(t.plannedStartAt != nil ? "같은 시간으로 옮겨요. 그 시간에 자리가 없으면 그날 시간 미정으로 들어가요."
                                                 : "그날 시간 미정으로 옮겨요.")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                    HStack(spacing: 8) {
                        NButton("취소", variant: .ghost, size: .sm) { pickingDate = false }
                        Spacer()
                        NButton("이 날로 옮기기", size: .sm, loading: board.busyTask) {
                            move(t, KST.ymd(from: pickedDate), "custom")
                        }
                        .disabled(KST.ymd(from: pickedDate) == current)
                    }
                }
                .padding(12)
                .background(Color.bgSubtle, in: RoundedRectangle(cornerRadius: Radius.lg))
            }
        }
        .padding(.top, 4)
    }

    private func move(_ t: TimeBlockTask, _ date: String, _ pick: String) {
        board.sheetTrack("task_detail.move_date_select", [
            "task_id": t.taskId, "source_type": t.sourceType.rawValue, "move_option": pick,
            "day_offset": KST.diffDays(KST.today(), date), "is_scheduled": t.plannedStartAt != nil,
        ])
        outcome = "next"
        pickingDate = false
        Task { await board.moveToDate(t, date: date, pick: pick) }
    }

    private func moreMenu(_ t: TimeBlockTask, _ perm: TimetableTaskPermissions, chain: Int) -> some View {
        Menu {
            if chain > 1 {
                Button {
                    board.sheetTrack("task_detail.chain_select_click", ["task_id": t.taskId, "group_size": chain])
                    outcome = "next"
                    board.selectChain(t)
                } label: { Label("이어진 \(chain)개 묶어 옮기기", systemImage: "link") }
            }
            if perm.unlock {
                Button {
                    board.sheetTrack("task_detail.unlock_click", ["task_id": t.taskId])
                    outcome = "next"
                    Task { await board.unlock(t) }
                } label: { Label("AI 자동 배치에 맡기기", systemImage: "pin.slash") }
            }
            if perm.unschedule {
                Button {
                    board.sheetTrack("task_detail.unschedule_click", ["task_id": t.taskId])
                    outcome = "next"
                    Task { await board.unschedule(t) }
                } label: { Label("시간 미정으로", systemImage: "clock") }
            }
            if perm.delete {
                Button(role: .destructive) {
                    board.sheetTrack("task_detail.delete_click", ["task_id": t.taskId, "source_type": t.sourceType.rawValue,
                                                                  "entry_point": "menu"])
                    board.askDelete(t)
                } label: { Label("삭제", systemImage: "trash") }
            }
        } label: {
            NSymbol("ellipsis", size: 15, weight: .semibold)
                .foregroundStyle(Color.ink)
                .frame(width: 40, height: 40)
                .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
                .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
        }
        .accessibilityLabel("다른 작업")
    }
}

// MARK: - 상태 표시

private struct TimetableTaskBadges: View {
    let task: TimeBlockTask

    var body: some View {
        let t = task
        FlowLayout(spacing: 6) {
            if t.taskStatus == .COMPLETED { Badge("완료", tone: .success) }
            if t.missed { Badge("놓침", tone: .danger) }
            if t.sourceType == .ROUTINE { Badge("루틴", icon: "repeat") }
            if t.sourceType == .MISSION { Badge("미션") }
            if t.taskKind == .APPOINTMENT { Badge("약속", tone: .warning) }
            if t.locked {
                Badge("직접 정한 시간")
            } else if t.placedBy == .ENGINE {
                Badge("자동 배치", tone: .ai, icon: "sparkles")
            }
        }
    }
}

// MARK: - 한 줄 속성 (누르면 그 자리에서 수정)

private struct TimetablePropRow<Icon: View, Content: View>: View {
    let editable: Bool
    let icon: Icon
    let content: Content

    init(editable: Bool = false, @ViewBuilder icon: () -> Icon, @ViewBuilder content: () -> Content) {
        self.editable = editable
        self.icon = icon()
        self.content = content()
    }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            icon.frame(width: 18).padding(.top, 2)
            content
                .font(.pt(14))
                .foregroundStyle(Color.ink2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            if editable {
                NSymbol("pencil", size: 11)
                    .foregroundStyle(Color.ink4)
                    .padding(.top, 3)
            }
        }
        .contentShape(Rectangle())
    }
}

// MARK: - 시간 바로 고치기 (날짜 · 시작 · 종료 + 루틴이면 적용 범위)

private struct TimetableInlineTimeEditor: View {
    let board: TimetableBoardModel
    let item: TimeBlockTask
    let onDone: () -> Void
    /// '취소' (task_detail.field_cancel)
    var onCancel: (() -> Void)? = nil
    private let originalDay: String

    @State private var date: String
    @State private var start: Int
    @State private var end: Int
    @State private var scope: ChangeScope = .DAY
    @State private var saving = false

    init(board: TimetableBoardModel, task: TimeBlockTask, onDone: @escaping () -> Void, onCancel: (() -> Void)? = nil) {
        self.board = board
        self.item = task
        self.onDone = onDone
        self.onCancel = onCancel
        let day = TimetableRules.blockDay(task) ?? task.date ?? task.scheduledDate ?? KST.today()
        let s0 = task.plannedStartAt.map { KST.minutesFrom(day, $0) } ?? 9 * 60
        let e0 = task.plannedEndAt.map { KST.minutesFrom(day, $0) } ?? s0 + (task.plannedDurationMinutes ?? 60)
        originalDay = day
        _date = State(initialValue: day)
        _start = State(initialValue: ((s0 % 1440) + 1440) % 1440)
        _end = State(initialValue: ((e0 % 1440) + 1440) % 1440)
    }

    var body: some View {
        let s = start
        // 종료가 시작보다 이르면 다음 날로 넘어가는 것으로 봐요 (예: 23:00 ~ 01:00)
        let e = end <= start ? end + 1440 : end
        let valid = e - s > 0 && e - s < 1440
        let routine = item.sourceType == .ROUTINE
        let options: [TimetableScopeOption]? = routine
            ? TimetableScope.options(kind: .routine, isToday: date == KST.today(), sameDay: date == originalDay,
                                     crossesMidnight: e >= 1440)
            : nil
        let effective = TimetableScope.effective(scope, in: options)
        let info = valid ? "\(KST.formatDuration(e - s))\(e >= 1440 ? " · 다음 날까지" : "")" : "시간을 확인해 주세요"

        VStack(alignment: .leading, spacing: 10) {
            TimetableDateField(ymd: $date)
            HStack(spacing: 6) {
                TimetableClockPicker(minutes: $start, label: "시작")
                Text("–").font(.pt(12)).foregroundStyle(Color.ink3)
                TimetableClockPicker(minutes: $end, label: "종료")
            }
            if let options {
                TimetableScopeChips(options: options, value: effective, onChange: { scope = $0 })
            }
            HStack(spacing: 6) {
                Text(info).font(.pt(11)).foregroundStyle(Color.ink3)
                Spacer(minLength: 0)
                NButton("취소", variant: .ghost, size: .sm) { (onCancel ?? onDone)() }.disabled(saving)
                NButton("저장", size: .sm, loading: saving) { save(s, e, effective) }
                    .disabled(!valid)
            }
        }
        .padding(10)
        .background(Color.brandSoft.opacity(0.4), in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
    }

    private func save(_ s: Int, _ e: Int, _ scope: ChangeScope) {
        if saving { return }
        saving = true
        let d = date
        board.sheetTrack("task_detail.field_save", [
            "task_id": item.taskId, "edit_field": "time", "source_type": item.sourceType.rawValue,
            "change_scope": item.sourceType == .ROUTINE ? scope.rawValue : nil,
        ])
        Task {
            let ok = await board.saveTaskTime(item, date: d, start: s, end: e, scope: scope)
            saving = false
            board.trackFieldResult("time", ok: ok)
            if ok { onDone() }
        }
    }
}

// MARK: - 배치 이유

private struct TimetableReasonSection: View {
    let task: TimeBlockTask

    var body: some View {
        let t = task
        let done = t.taskStatus == .COMPLETED
        VStack(alignment: .leading, spacing: 0) {
            if let reason = t.placementReason, !t.locked {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        NSymbol("sparkles", size: 11, weight: .bold).foregroundStyle(Color.aiGradient)
                        Text("이 시간에 놓은 이유").font(.pt(12, .bold))
                    }
                    .foregroundStyle(Color.textBrand)
                    TimetablePlacementReason(text: reason, codes: t.placementReasonCodes ?? [])
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
            }
            if t.locked && !done {
                Text("직접 정한 시간이라 자동 배치가 옮기지 않아요.")
                    .font(.pt(12))
                    .foregroundStyle(Color.ink3)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: Radius.xl))
            }
        }
    }
}

/// 배치 이유: 첫 문장 = 왜 이 시간에, 뒤 문장들 = 내 기록 근거 ("최근 4주 … 12개 중 10개를 해냈어요").
/// 근거가 눈에 들어오도록 문장을 나눠 보여주고, 이유 코드는 꼬리표로.
private struct TimetablePlacementReason: View {
    let text: String
    let codes: [String]

    /// 배치 이유 코드 → 짧은 근거 꼬리표 (웹 REASON_TAG)
    static let tagLabel: [String: String] = [
        "OVERDUE": "⏰ 마감 지남",
        "DEADLINE_TODAY": "⏰ 오늘 마감",
        "DEADLINE_SOON": "⏰ 마감 임박",
        "CARRIED_OVER": "↩️ 어제 못 한 일",
        "MILESTONE_SOON": "🚩 마일스톤 임박",
        "HIGH_EFFICIENCY": "📈 잘 지키는 시간",
        "BEST_REMAINING": "📈 남은 시간 중 최선",
        "PREFERRED_TIME": "⭐ 고른 선호 시간",
        "LOW_LOAD_SLOT": "🗓️ 마감 여유",
        "GOAL_TIME_FIT": "🎯 이 목표와 잘 맞는 시간",
        "MOVE_HABIT": "↔️ 자주 옮겨 온 시간",
        "DURATION_ADJUSTED": "⏱️ 실제 걸린 시간 반영",
    ]

    /// 문장 끝("요." · "요)." 등) 기준으로 나눠요 (웹과 같은 정규식)
    static func sentences(_ text: String) -> [String] {
        guard let re = try? NSRegularExpression(pattern: #"[^.]+?(?:요\)?\.|$)"#) else { return [text] }
        let ns = text as NSString
        let matches = re.matches(in: text, range: NSRange(location: 0, length: ns.length))
        let list = matches
            .map { ns.substring(with: $0.range).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return list.isEmpty ? [text] : list
    }

    var body: some View {
        let list = TimetablePlacementReason.sentences(text)
        let head = list.first ?? text
        let evidence = Array(list.dropFirst())
        let tags = codes.compactMap { TimetablePlacementReason.tagLabel[$0] }
        VStack(alignment: .leading, spacing: 6) {
            Text(head)
                .font(.pt(13))
                .foregroundStyle(Color.ink2)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            if !evidence.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(evidence.indices, id: \.self) { i in
                        HStack(alignment: .top, spacing: 6) {
                            Text("•").foregroundStyle(Color.textBrand)
                            Text(evidence[i]).fixedSize(horizontal: false, vertical: true)
                        }
                        .font(.pt(12))
                        .foregroundStyle(Color.ink2)
                    }
                }
            }
            if !tags.isEmpty {
                FlowLayout(spacing: 4) {
                    ForEach(tags.indices, id: \.self) { i in
                        Text(tags[i])
                            .font(.pt(11, .semibold))
                            .foregroundStyle(Color.ink2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background(Color.surface, in: Capsule())
                    }
                }
            }
        }
    }
}

#Preview {
    let d = KST.today()
    let board = TimetableBoardModel.sample(start: d, end: d)
    return Color.canvas
        .sheet(isPresented: .constant(true)) {
            TimetableTaskDetailSheet(board: board, task: TimetableSampleData.task)
        }
}

// MARK: - 중요도 표시 (A 는 목표 색으로 채움)

struct TimetablePriorityMark: View {
    let priority: TaskPriority
    let color: Color
    var size: CGFloat = 16

    var body: some View {
        Text(priority.rawValue)
            .font(.system(size: size * 0.62, weight: .bold))
            .foregroundStyle(priority.isTop ? Color.white : Color.ink3)
            .frame(width: size, height: size)
            .background(priority.isTop ? color : Color.subtle, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
            .accessibilityLabel("중요도 \(priority.rawValue) \(priority.label)")
    }
}
