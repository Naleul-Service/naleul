// Features/Work/WorkKanbanSection.swift
//
// 웹 WorkBoardView.tsx TopicBar · Kanban · BoardCard
// 보드 — 시작 전 · 진행 중 · 완료. 휴대폰은 칸 세 개를 나란히 둘 자리가 없어서
// 세그먼트로 칸을 고르고, 끌어서 옮기기 대신 카드 오른쪽 … 메뉴(옮기기 · 지우기)를 써요.

import SwiftUI

/// 주제 필터: 전체 · 주제 없음 · 주제 id
enum WorkTopicFilter: Hashable {
    case all
    case noTopic
    case topic(Int)

    func matches(_ t: WorkTask) -> Bool {
        switch self {
        case .all: return true
        case .noTopic: return t.topicId == nil
        case .topic(let id): return t.topicId == id
        }
    }

    static func of(_ topic: WorkTopic) -> WorkTopicFilter {
        if let id = topic.topicId { return .topic(id) }
        return .noTopic
    }
}

@MainActor
struct WorkKanbanSection: View {
    let board: WorkBoard
    let model: WorkBoardModel
    let onOpenTask: (WorkTask) -> Void

    @State private var filter: WorkTopicFilter = .all
    @State private var column: WorkColumn = .NOT_STARTED
    @State private var deleting: WorkTask?
    @State private var confirmDelete = false
    /// 빈 칸 안내를 이미 보낸 칸·필터 조합
    @State private var emptySeen: Set<String> = []

    init(board: WorkBoard, model: WorkBoardModel, onOpenTask: @escaping (WorkTask) -> Void) {
        self.board = board
        self.model = model
        self.onOpenTask = onOpenTask
    }

    private func items(_ c: WorkColumn) -> [WorkTask] {
        board.board.tasks(c).filter { filter.matches($0) }
    }

    private func count(_ c: WorkColumn) -> Int {
        c == .DONE && filter == .all ? board.board.doneTotal : items(c).count
    }

    private var columnOptions: [(value: WorkColumn, label: String)] {
        WorkColumn.allCases.map { c in (value: c, label: "\(c.label) \(count(c))") }
    }

    private var emptyText: String {
        switch column {
        case .IN_PROGRESS: return "하고 있는 일을 여기로 옮겨 오세요"
        case .DONE: return "끝낸 일이 여기 쌓여요"
        case .NOT_STARTED: return "할 일이 없어요"
        }
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if model.isPreview { return }
        WK.track(event, goalId: board.goalCategoryId, props)
    }

    private func pickFilter(_ f: WorkTopicFilter) {
        filter = f
        track("work_board.topic_filter_change", [
            "topic_filter": f.analyticsName, "topic_id": f.analyticsTopicId, "entry_point": "topic_bar",
            "result_count": WorkColumn.allCases.reduce(0) { $0 + board.board.tasks($1).filter { f.matches($0) }.count },
        ])
    }

    var body: some View {
        SectionCard("보드", aside: { Text("눌러서 자세히 고쳐요") }) {
            VStack(alignment: .leading, spacing: 14) {
                topicChips
                SegmentPicker(selection: Binding(get: { column }, set: { c in
                    guard c != column else { return }
                    column = c
                    track("work_board.column_select", [
                        "to_status": c.rawValue, "topic_filter": filter.analyticsName, "item_count": items(c).count,
                    ])
                }), options: columnOptions, fullWidth: true)
                cards
            }
        }
        .confirmDialog(isPresented: $confirmDelete, title: "이 Task를 지울까요?", message: "되돌릴 수 없어요.",
                       confirmLabel: "지우기", tone: .danger, onCancel: {
            if let t = deleting { track("work_board.card_delete_cancel", ["task_id": t.taskId]) }
        }) {
            if let t = deleting {
                track("work_board.card_delete_confirm", ["task_id": t.taskId, "task_status": t.status.rawValue])
                Task { await model.delete(t) }
            }
        }
    }

    private var topicChips: some View {
        let total = board.topics.reduce(0) { $0 + $1.remaining }
        return FlowLayout(spacing: 6) {
            Chip("전체 \(total)", selected: filter == .all, size: .sm) {
                if filter != .all { pickFilter(.all) }
            }
            ForEach(board.topics) { t in
                let f = WorkTopicFilter.of(t)
                Chip("\(t.title) \(t.remaining)", selected: filter == f, size: .sm) {
                    pickFilter(filter == f ? .all : f)
                }
            }
        }
    }

    @ViewBuilder
    private var cards: some View {
        let list = items(column)
        if list.isEmpty {
            let key = "\(column.rawValue):\(filter.analyticsName):\(filter.analyticsTopicId ?? 0)"
            Text(emptyText)
                .onAppear {
                    guard !emptySeen.contains(key) else { return }
                    emptySeen.insert(key)
                    track("work_board.empty_column_view", [
                        "to_status": column.rawValue, "topic_filter": filter.analyticsName,
                        "unfiltered_count": board.board.tasks(column).count,
                    ])
                }
                .id(key)
                .font(.pt(12))
                .foregroundStyle(Color.ink4)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .background(Color.subtle.opacity(0.6), in: RoundedRectangle(cornerRadius: Radius.xxl))
        } else {
            VStack(spacing: 8) {
                ForEach(list) { t in
                    WorkKanbanCard(
                        task: t,
                        today: board.today,
                        showTopic: filter == .all,
                        onOpen: { onOpenTask(t) },
                        onToggle: { Task { await model.toggle(t, surface: "board") } },
                        onMove: { to in Task { await model.move(t, to: to, surface: "board", method: "menu") } },
                        onDelete: {
                            track("work_board.card_delete_click", [
                                "task_id": t.taskId, "task_status": t.status.rawValue, "is_routine": t.routine,
                            ])
                            deleting = t
                            confirmDelete = true
                        }
                    )
                }
            }
        }
    }
}

// MARK: - 카드

@MainActor
private struct WorkKanbanCard: View {
    let task: WorkTask
    let today: String
    let showTopic: Bool
    let onOpen: () -> Void
    let onToggle: () -> Void
    let onMove: (WorkColumn) -> Void
    let onDelete: () -> Void

    var body: some View {
        let done = task.isDone
        HStack(alignment: .top, spacing: 10) {
            WorkCheckCircle(done: done) { onToggle() }
            Button { onOpen() } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text(task.taskName)
                        .font(.pt(14, .semibold))
                        .strikethrough(done, color: Color.ink3)
                        .foregroundStyle(done ? Color.ink3 : Color.ink)
                        .multilineTextAlignment(.leading)
                    WorkTaskMetaRow(task: task, today: today, showTopic: showTopic)
                    if task.date == nil && !done {
                        Text("할 일 목록").font(.pt(11)).foregroundStyle(Color.ink4)
                    }
                    if let memo = task.memo, !memo.isEmpty {
                        Text(memo)
                            .font(.pt(12))
                            .foregroundStyle(Color.ink3)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                            .padding(.top, 2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            menu
        }
        .padding(12)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.xl)
                .stroke(task.overdue ? Color.danger.opacity(0.4) : Color.line, lineWidth: 1)
        )
        .cardShadow()
    }

    private var menu: some View {
        Menu {
            Section("옮기기") {
                ForEach(WorkColumn.allCases.filter { $0 != task.status }, id: \.self) { c in
                    Button { onMove(c) } label: { Label(c.label, systemImage: c.icon) }
                }
            }
            Button(role: .destructive) { onDelete() } label: { Label("지우기", systemImage: "trash") }
        } label: {
            NSymbol("ellipsis", size: 14, weight: .semibold)
                .foregroundStyle(Color.ink3)
                .frame(width: 32, height: 28)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("\(task.taskName) 옮기기")
    }
}

#Preview("보드") {
    let board = WorkSampleData.board
    return ScrollView {
        WorkKanbanSection(board: board, model: WorkBoardModel.preview(board), onOpenTask: { _ in })
            .padding(16)
    }
    .canvasBackground()
}

#Preview("보드 · 비어 있음") {
    let board = WorkSampleData.emptyBoard
    return ScrollView {
        WorkKanbanSection(board: board, model: WorkBoardModel.preview(board), onOpenTask: { _ in })
            .padding(16)
    }
    .canvasBackground()
}
