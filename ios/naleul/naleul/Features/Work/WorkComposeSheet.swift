// Features/Work/WorkComposeSheet.swift
//
// 웹 WorkBoardView.tsx ComposePanel · TaskCompose · RetroCompose
// 적기 — 할 일 · 실제로 한 일 · 회고를 한곳에서 적어요.
//  - 할 일: 브레인덤프처럼 여러 줄을 붙여 넣으면 한 번에 추가. 추가해도 시트는 그대로 (계속 적을 수 있게)
//  - 한 일: RecordQuickLogView (업무 일지에 남아요)
//  - 회고: 적기 + 최근 회고
// 보드에서도, 목표 상세의 ✏️ 버튼에서도 열 수 있게 주제 · 회고는 시트가 직접 불러와요.

import SwiftUI
import UIKit

enum WorkComposeTab: String, CaseIterable, Hashable {
    case task, log, retro

    var label: String {
        switch self {
        case .task: return "할 일"
        case .log: return "한 일"
        case .retro: return "회고"
        }
    }

    var icon: String {
        switch self {
        case .task: return "checklist"
        case .log: return "pencil.line"
        case .retro: return "text.bubble"
        }
    }
}

/// 언제 할지 — 나중에(할 일 목록) · 오늘 · 날짜
private enum WorkComposeWhen: Hashable { case later, today, date }

@MainActor
struct WorkComposeSheet: View {
    let goal: GoalCategory
    private let isPreview: Bool

    @State private var tab: WorkComposeTab
    @State private var board: WorkBoard?

    // 할 일
    @State private var topicId: Int?
    @State private var text = ""
    @State private var when: WorkComposeWhen = .later
    @State private var date: String = KST.today()
    @State private var hasDue = false
    @State private var dueDate: String = KST.today()
    @State private var saving = false

    // 회고
    @State private var retroText = ""
    @State private var savingRetro = false
    @State private var deletingRetro: WorkRetro?
    @Environment(\.dismiss) private var dismissSheet
    @Environment(AppRouter.self) private var router
    @Environment(GoalDetailViewModel.self) private var detail: GoalDetailViewModel?
    @State private var confirmRetroDelete = false
    // 앰플리튜드 — 닫힐 때 몇 개를 적었는지 · 적던 글이 남았는지
    @State private var addedCount = 0
    @State private var logDraft = ""
    @State private var closedByNext = false

    /// board 를 주면 바로 보여주고, 시트가 열려 있는 동안 새로 불러와요
    init(goal: GoalCategory, initialTab: WorkComposeTab = .task, topicId: Int? = nil,
         board: WorkBoard? = nil, isPreview: Bool = false) {
        self.goal = goal
        self.isPreview = isPreview
        _tab = State(initialValue: initialTab)
        _topicId = State(initialValue: topicId)
        _board = State(initialValue: board)
    }

    private var tabOptions: [(value: WorkComposeTab, label: String)] {
        WorkComposeTab.allCases.map { t in (value: t, label: t.label) }
    }

    private var tabIcons: [WorkComposeTab: String] {
        var m: [WorkComposeTab: String] = [:]
        for t in WorkComposeTab.allCases { m[t] = t.icon }
        return m
    }

    private var whenOptions: [(value: WorkComposeWhen, label: String)] {
        [(value: WorkComposeWhen.later, label: "나중에"),
         (value: WorkComposeWhen.today, label: "오늘"),
         (value: WorkComposeWhen.date, label: "날짜")]
    }

    private var lines: [String] {
        text.split(separator: "\n", omittingEmptySubsequences: true)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    var body: some View {
        SheetScaffold(title: "적기", detents: [.large]) {
            VStack(alignment: .leading, spacing: 18) {
                SegmentPicker(selection: Binding(get: { tab }, set: { t in
                    guard t != tab else { return }
                    track("work_compose.tab_click", ["compose_tab": t.rawValue, "from_tab": tab.rawValue, "is_dirty": draftOf(tab)])
                    tab = t
                }), options: tabOptions, icons: tabIcons, fullWidth: true)
                switch tab {
                case .task:
                    taskForm
                case .log:
                    RecordQuickLogView(goal: goal, sheetMode: true, onDone: nil,
                                       analytics: !isPreview, onDraftChange: { logDraft = $0 }, onLogged: { addedCount += 1 })
                case .retro:
                    retroForm
                }
            }
        }
        .reloadOn([.work], id: goal.goalCategoryId) { await loadBoard() }
        .confirmDialog(isPresented: $confirmRetroDelete, title: "회고를 지울까요?", message: "되돌릴 수 없어요.",
                       confirmLabel: "지우기", tone: .danger, onCancel: { track("work_compose.retro_delete_cancel") }) {
            if let r = deletingRetro {
                track("work_compose.retro_delete_confirm", ["days_ago": GD.daysAgo(r.reviewDate)])
                Task { await deleteRetro(r) }
            }
        }
        .onDisappear {
            // '모두 보기'로 넘어가면 next, 아니면 X · 아래로 쓸어 닫기 (적기 시트는 저장해도 닫히지 않아요)
            let hint = Analytics.sheetCloseHint
            Analytics.sheetCloseHint = nil
            let method = closedByNext ? "next" : (hint ?? "swipe_down")
            track("work_compose.close", [
                "compose_tab": tab.rawValue, "close_method": method,
                "is_dirty": draftOf(.task) || draftOf(.log) || draftOf(.retro), "added_count": addedCount,
            ])
        }
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        WK.track(event, goalId: goal.goalCategoryId, props)
    }

    /// 그 탭에 적던 글이 있는지
    private func draftOf(_ t: WorkComposeTab) -> Bool {
        switch t {
        case .task: return !lines.isEmpty
        case .log: return !logDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .retro: return !retroText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    private var whenAnalytics: String {
        switch when {
        case .later: return "BACKLOG"
        case .today: return "TODAY"
        case .date: return "DATE"
        }
    }

    // MARK: 할 일

    private var taskForm: some View {
        let count = lines.count
        return VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("세부 목표")
                WorkTopicMenu(selection: Binding(get: { topicId }, set: { id in
                    topicId = id
                    track("work_compose.topic_select", ["topic_id": id, "has_topic": id != nil])
                }), topics: board?.realTopics ?? [], showDue: true)
            }

            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("해야 할 일", hint: count > 1 ? "\(count)개" : nil)
                WorkTextEditor(text: $text,
                               placeholder: "해야 할 일을 한 줄에 하나씩 적어 주세요\n여러 줄을 붙여 넣으면 한 번에 추가돼요",
                               minHeight: 128)
            }

            VStack(alignment: .leading, spacing: 8) {
                FieldLabel("언제")
                SegmentPicker(selection: Binding(get: { when }, set: { w in
                    guard w != when else { return }
                    when = w
                    track("work_compose.when_select", ["when_option": whenAnalytics])
                }), options: whenOptions, fullWidth: true)
                if when == .date {
                    TimetableDateField(ymd: $date)
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

            VStack(alignment: .leading, spacing: 6) {
                NButton(count > 1 ? "\(count)개 추가" : "할 일 추가", variant: .brand, size: .lg,
                        loading: saving, fullWidth: true) {
                    Task { await submitTask() }
                }
                .disabled(count == 0)
                Text("'나중에'는 캘린더에 안 올리고 로드맵 · 보드에만 담아요")
                    .font(.pt(11))
                    .foregroundStyle(Color.ink4)
            }
        }
    }

    @MainActor
    private func submitTask() async {
        let ls = lines
        guard !ls.isEmpty, !saving else { return }
        let day: String?
        switch when {
        case .later: day = nil
        case .today: day = KST.today()
        case .date: day = date
        }
        let due: String? = hasDue ? dueDate : nil
        track("work_compose.task_submit", [
            "line_count": ls.count, "has_topic": topicId != nil, "when_option": whenAnalytics, "has_due_date": due != nil,
            "is_truncated": ls.contains { $0.count > 100 }, "due_before_date": (due != nil && day != nil && due! < day!),
            "submit_method": "button",
        ])
        if isPreview {
            text = ""
            return
        }
        saving = true
        defer { saving = false }
        let gid = goal.goalCategoryId
        let tid = topicId
        if ls.count == 1 {
            let name = String(ls[0].prefix(100))
            let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "Task를 추가했어요.") {
                try await WorkAPI.createTask(goalId: gid, taskName: name, topicId: tid, date: day, dueDate: due)
            }
            track("work_compose.task_result", GD.result(r != nil).merging([
                "line_count": 1, "created_count": r != nil ? 1 : 0,
            ]) { $1 })
            if r != nil { finishAdd(1) }
        } else {
            let full = ls.joined(separator: "\n")
            let r = await Mutation.run(invalidate: WorkAPI.changed,
                                       success: { (list: [WorkTask]) -> String? in "Task \(list.count)개를 추가했어요." }) {
                try await WorkAPI.createTasks(goalId: gid, text: full, topicId: tid, date: day, dueDate: due)
            }
            let created = r?.count ?? 0
            track("work_compose.task_result", GD.result(r != nil).merging([
                "line_count": ls.count, "created_count": created,
            ]) { $1 })
            if r != nil { finishAdd(created) }
        }
    }

    /// 추가하면 글만 비우고 시트는 그대로 (계속 적을 수 있게)
    private func finishAdd(_ count: Int) {
        addedCount += count
        text = ""
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    // MARK: 회고

    private var retroForm: some View {
        let value = retroText.trimmingCharacters(in: .whitespacesAndNewlines)
        let retros = board?.retrospectives ?? []
        let today = board?.today ?? KST.today()
        return VStack(alignment: .leading, spacing: 12) {
            WorkTextEditor(text: $retroText, placeholder: "오늘 일하며 배운 점 · 막힌 점 · 다음에 바꿀 점", minHeight: 120)
            NButton("회고 남기기", variant: .brand, size: .lg, loading: savingRetro, fullWidth: true) {
                Task { await submitRetro() }
            }
            .disabled(value.isEmpty)

            if !retros.isEmpty {
                HStack {
                    Text("최근 회고")
                        .font(.pt(12, .semibold))
                        .foregroundStyle(Color.ink3)
                    Spacer(minLength: 0)
                    Button {
                        track("work_compose.retro_all_click", ["item_count": retros.count])
                        closedByNext = true
                        dismissSheet()
                        // 목표 상세에서 열었으면 "회고" 탭으로, 아니면 회고 모아보기(이 목표)로
                        if let detail { detail.reflectionsTabRequested = true }
                        else {
                            RF.pendingEntry = "work_compose"
                            router.openReflections(goalId: goal.goalCategoryId)
                        }
                    } label: {
                        Text("모두 보기").font(.pt(12, .semibold)).foregroundStyle(Color.textBrand)
                            .frame(minHeight: 28).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 10)
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(retros) { r in
                        HStack(alignment: .top, spacing: 8) {
                            Text(WorkFormat.dayLabel(r.reviewDate, today: today))
                                .font(.pt(12))
                                .foregroundStyle(Color.ink3)
                                .frame(width: 56, alignment: .leading)
                            Text(r.content)
                                .font(.pt(13))
                                .foregroundStyle(Color.ink)
                                .lineSpacing(3)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            IconButton(systemName: "trash", size: 13, color: .ink4, label: "회고 지우기") {
                                track("work_compose.retro_delete_click", ["days_ago": GD.daysAgo(r.reviewDate)])
                                deletingRetro = r
                                confirmRetroDelete = true
                            }
                            .padding(.top, -6)
                        }
                        .padding(.vertical, 10)
                        .overlay(alignment: .bottom) {
                            if r.id != retros.last?.id {
                                Rectangle().fill(Color.borderSoft).frame(height: 1)
                            }
                        }
                    }
                }
            }
        }
    }

    @MainActor
    private func submitRetro() async {
        let content = retroText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty, !savingRetro else { return }
        track("work_compose.retro_submit", ["content_length": content.count, "submit_method": "button"])
        if isPreview {
            retroText = ""
            return
        }
        savingRetro = true
        defer { savingRetro = false }
        let gid = goal.goalCategoryId
        let r = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "회고를 남겼어요.") {
            try await WorkAPI.createRetro(goalId: gid, content: content)
        }
        track("work_compose.retro_result", GD.result(r != nil).merging(["retro_action": "create"]) { $1 })
        if r != nil {
            retroText = ""
            addedCount += 1
        }
    }

    @MainActor
    private func deleteRetro(_ r: WorkRetro) async {
        if isPreview { return }
        let id = r.retrospectiveId
        let ok = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "회고를 지웠어요.") {
            try await WorkAPI.deleteRetro(retrospectiveId: id)
        }
        track("work_compose.retro_result", GD.result(ok != nil).merging(["retro_action": "delete"]) { $1 })
    }

    @MainActor
    private func loadBoard() async {
        if isPreview { return }
        if let b = try? await WorkAPI.board(goalId: goal.goalCategoryId) {
            board = b
        }
    }
}

// MARK: - 공용: 주제 고르기 · 여러 줄 입력칸 (Task 시트에서도 써요)

/// 주제 없음 + 주제들 (마감이 있으면 "(~10/16(금))")
struct WorkTopicMenu: View {
    @Binding var selection: Int?
    let topics: [WorkTopic]
    var showDue = false
    var disabled = false

    private func title(for t: WorkTopic) -> String {
        guard showDue, let d = t.dueDate else { return t.title }
        return "\(t.title) (~\(WorkFormat.shortDate(d)))"
    }

    private var current: String {
        guard let id = selection, let t = topics.first(where: { $0.topicId == id }) else { return "세부 목표 없음" }
        return title(for: t)
    }

    var body: some View {
        Menu {
            Button { selection = nil } label: {
                if selection == nil { Label("세부 목표 없음", systemImage: "checkmark") } else { Text("세부 목표 없음") }
            }
            ForEach(topics) { t in
                Button { selection = t.topicId } label: {
                    if selection != nil && selection == t.topicId {
                        Label(title(for: t), systemImage: "checkmark")
                    } else {
                        Text(title(for: t))
                    }
                }
            }
        } label: {
            HStack(spacing: 8) {
                Text(current)
                    .font(.pt(15, .semibold))
                    .foregroundStyle(selection == nil ? Color.ink3 : Color.ink)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.ink3)
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .background(disabled ? Color.subtle : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.borderSubtle, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: Radius.xl))
        }
        .disabled(disabled)
        .accessibilityLabel("세부 목표")
    }
}

/// placeholder 가 있는 TextEditor (.inputStyle)
struct WorkTextEditor: View {
    @Binding var text: String
    let placeholder: String
    var minHeight: CGFloat = 96
    var maxLength: Int? = nil

    var body: some View {
        ZStack(alignment: .topLeading) {
            TextEditor(text: $text)
                .inputStyle(minHeight: minHeight)
                .onChange(of: text) {
                    if let maxLength, text.count > maxLength { text = String(text.prefix(maxLength)) }
                }
            if text.isEmpty {
                Text(placeholder)
                    .font(.pt(15))
                    .foregroundStyle(Color.ink4)
                    .lineSpacing(3)
                    .padding(.horizontal, 19)
                    .padding(.vertical, 18)
                    .allowsHitTesting(false)
            }
        }
    }
}

#Preview("할 일") {
    WorkComposeSheet(goal: GoalPreviewData.recordGoal, initialTab: .task, topicId: 3,
                     board: WorkSampleData.board, isPreview: true)
        .environment(AppRouter.shared)
}

#Preview("회고") {
    WorkComposeSheet(goal: GoalPreviewData.recordGoal, initialTab: .retro,
                     board: WorkSampleData.board, isPreview: true)
        .environment(AppRouter.shared)
}

#Preview("시트로") {
    Color.canvas
        .sheet(isPresented: .constant(true)) {
            WorkComposeSheet(goal: GoalPreviewData.recordGoal, initialTab: .log,
                             board: WorkSampleData.board, isPreview: true)
                .environment(AppRouter.shared)
        }
}
