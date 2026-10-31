// Features/Work/WorkRoadmapSection.swift
//
// 웹 WorkBoardView.tsx Roadmap · RoadmapItem · TopicTasks · RoadmapTask · NoTopicItem · TodayMarker
// 로드맵 — 주제를 마감 순서대로 세로 선 위에 놓아요. 끝난 주제는 채운 점, 다음 마감은 강조,
// 마감 지난 주제는 빨간 점, 마감 없는 주제는 점선. 점을 누르면 "끝냈어요".
// 점과 점 사이에는 그 주제의 남은 할 일을 보여줘요.

import SwiftUI

@MainActor
struct WorkRoadmapSection: View {
    let board: WorkBoard
    let model: WorkBoardModel
    let onOpenTask: (WorkTask) -> Void
    /// 이 주제(nil = 주제 없음)에 할 일 적기
    let onAddTask: (Int?) -> Void
    let onEditTopic: (WorkTopic) -> Void
    let onAddTopic: () -> Void

    init(board: WorkBoard, model: WorkBoardModel,
         onOpenTask: @escaping (WorkTask) -> Void,
         onAddTask: @escaping (Int?) -> Void,
         onEditTopic: @escaping (WorkTopic) -> Void,
         onAddTopic: @escaping () -> Void) {
        self.board = board
        self.model = model
        self.onOpenTask = onOpenTask
        self.onAddTask = onAddTask
        self.onEditTopic = onEditTopic
        self.onAddTopic = onAddTopic
    }

    /// "오늘" 표시는 마감이 오늘 이후인 첫 주제 앞 (마감 없는 주제는 맨 뒤라 그 앞)
    private var todayAt: Int {
        let today = board.today
        let i = board.timeline.firstIndex { t in
            guard let d = t.dueDate else { return true }
            return d >= today
        }
        return i ?? board.timeline.count
    }

    var body: some View {
        let s = board.summary
        SectionCard("로드맵", aside: {
            if s.topicsTotal > 0 { Text("세부 목표 \(s.topicsDone) / \(s.topicsTotal) 끝") }
        }) {
            VStack(alignment: .leading, spacing: 0) {
                if board.timeline.isEmpty {
                    Text("포트폴리오 · 이력서 제출처럼 큰 단계를 세부 목표로 만들고, 세부 목표마다 마감을 정해 보세요.")
                        .font(.pt(14))
                        .foregroundStyle(Color.ink3)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 12)
                } else {
                    timeline
                }
                addTopicButton.padding(.top, 16)
                if !board.timeline.isEmpty {
                    Text("세부 목표 점을 누르면 '끝냈어요'로 표시돼요")
                        .font(.pt(11))
                        .foregroundStyle(Color.ink4)
                        .padding(.top, 8)
                }
            }
        }
    }

    private var timeline: some View {
        let at = todayAt
        let noTopic = board.openTasks(topicId: nil)
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(board.timeline.enumerated()), id: \.element.id) { pair in
                let t = pair.element
                if pair.offset == at { WorkRoadmapTodayMarker() }
                WorkRoadmapTopicRow(
                    topic: t,
                    toggling: model.togglingTopicId != nil && model.togglingTopicId == t.topicId,
                    onToggle: { Task { await model.toggleTopic(t) } },
                    onEdit: { onEditTopic(t) }
                ) {
                    WorkRoadmapTopicTasks(
                        open: board.openTasks(topicId: t.topicId),
                        done: board.doneTasks(topicId: t.topicId),
                        today: board.today,
                        collapsed: t.isDone,
                        onOpen: onOpenTask,
                        onToggle: { task in Task { await model.toggle(task, surface: "roadmap") } },
                        onAdd: { onAddTask(t.topicId) },
                        topicId: t.topicId,
                        goalId: board.goalCategoryId
                    )
                }
            }
            if at == board.timeline.count { WorkRoadmapTodayMarker() }
            if !noTopic.isEmpty {
                WorkRoadmapNoTopicRow(count: noTopic.count) {
                    WorkRoadmapTopicTasks(
                        open: noTopic,
                        done: [],
                        today: board.today,
                        collapsed: false,
                        onOpen: onOpenTask,
                        onToggle: { task in Task { await model.toggle(task, surface: "roadmap") } },
                        onAdd: { onAddTask(nil) },
                        topicId: nil,
                        goalId: board.goalCategoryId
                    )
                }
            }
            WorkRoadmapFinishRow(done: board.summary.topicsDone, total: board.summary.topicsTotal)
        }
    }

    private var addTopicButton: some View {
        Button { onAddTopic() } label: {
            HStack(spacing: 4) {
                NSymbol("plus", size: 12, weight: .semibold)
                Text("세부 목표 추가")
            }
            .font(.pt(13, .semibold))
            .foregroundStyle(Color.ink3)
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl)
                    .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.xl))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 세로 선 한 칸 (점 + 내용, 점 아래로 선이 이어져요)

@MainActor
private struct WorkRoadmapRail<Node: View, Content: View>: View {
    var showsLine = true
    let node: () -> Node
    let content: () -> Content

    init(showsLine: Bool = true, @ViewBuilder node: @escaping () -> Node, @ViewBuilder content: @escaping () -> Content) {
        self.showsLine = showsLine
        self.node = node
        self.content = content
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            node().frame(width: 28, height: 28)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 3)
                .padding(.bottom, 20)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(alignment: .leading) {
            if showsLine {
                Rectangle()
                    .fill(Color.line)
                    .frame(width: 2)
                    .padding(.top, 28)
                    .padding(.leading, 13)
            }
        }
    }
}

// MARK: - 주제 점

struct WorkTopicNode: View {
    let status: WorkTopicStatus

    private var fill: Color {
        switch status {
        case .DONE: return Color.brand
        case .OVERDUE: return Color.dangerSoft
        default: return Color.surface
        }
    }

    private var stroke: Color {
        switch status {
        case .DONE, .NEXT: return Color.brand
        case .OVERDUE: return Color.danger
        case .UPCOMING, .NO_DUE: return Color.lineStrong
        }
    }

    var body: some View {
        ZStack {
            if status == .NEXT {
                Circle().stroke(Color.brand.opacity(0.15), lineWidth: 4).frame(width: 34, height: 34)
            }
            Circle().fill(fill)
            if status == .NO_DUE {
                Circle().strokeBorder(stroke, style: StrokeStyle(lineWidth: 2, dash: [3, 3]))
            } else {
                Circle().strokeBorder(stroke, lineWidth: 2)
            }
            if status == .DONE {
                NSymbol("checkmark", size: 12, weight: .heavy).foregroundStyle(Color.white)
            } else if status == .OVERDUE {
                Image(systemName: "exclamationmark.triangle").font(.system(size: 11, weight: .bold)).foregroundStyle(Color.danger)
            }
        }
        .frame(width: 28, height: 28)
    }
}

// MARK: - 주제 한 줄

@MainActor
private struct WorkRoadmapTopicRow<Tasks: View>: View {
    let topic: WorkTopic
    let toggling: Bool
    let onToggle: () -> Void
    let onEdit: () -> Void
    let tasks: () -> Tasks

    init(topic: WorkTopic, toggling: Bool, onToggle: @escaping () -> Void, onEdit: @escaping () -> Void,
         @ViewBuilder tasks: @escaping () -> Tasks) {
        self.topic = topic
        self.toggling = toggling
        self.onToggle = onToggle
        self.onEdit = onEdit
        self.tasks = tasks
    }

    private var done: Bool { topic.isDone }
    /// Task 를 모두 끝내서 "끝"인 주제는 되돌릴 게 없어요 (직접 끝냈다고 표시한 것만 다시 열기)
    private var autoDone: Bool { topic.isDone && !topic.completed }

    private var dueColor: Color {
        switch topic.status {
        case .OVERDUE: return Color.danger
        case .NEXT: return Color.brand
        default: return (topic.dDay ?? 99) <= 3 ? Color.warningInk : Color.ink3
        }
    }

    var body: some View {
        WorkRoadmapRail {
            Button { onToggle() } label: {
                WorkTopicNode(status: topic.status)
                    .opacity(toggling ? 0.5 : 1)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            // 자동으로 끝난 주제도 눌리게 두고(반응 없음) 앰플리튜드 topic_locked_click 만 보내요
            .disabled(toggling)
            .accessibilityLabel(autoDone ? "\(topic.name): Task를 모두 끝냈어요" : "\(topic.name) \(topic.completed ? "다시 열기" : "끝냈어요")")
        } content: {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .top, spacing: 6) {
                    Text(topic.title)
                        .font(.pt(15, .bold))
                        .strikethrough(done, color: Color.ink3)
                        .foregroundStyle(done ? Color.ink3 : Color.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    IconButton(systemName: "pencil", size: 13, color: .ink4, label: "\(topic.name) 고치기") { onEdit() }
                        .padding(.vertical, -6)
                        .padding(.trailing, -6)
                }
                meta
                if topic.total > 0 {
                    HStack(spacing: 8) {
                        ProgressBar(value: Double(topic.progressPercent ?? 0) / 100,
                                    color: done ? Color.success : Color.brand, height: 6)
                        Text("\(topic.done)/\(topic.total)")
                            .font(.ptMono(11))
                            .foregroundStyle(Color.ink3)
                    }
                    .padding(.top, 2)
                }
                tasks()
            }
        }
    }

    private var meta: some View {
        let due: String? = done ? nil : WorkFormat.topicDue(topic.dDay)
        return FlowLayout(spacing: 8, lineSpacing: 2) {
            if let d = topic.dueDate {
                Text("~" + WorkFormat.shortDate(d))
            } else {
                Text("마감 없음")
            }
            if let due {
                Text(due).font(.pt(12, .semibold)).foregroundStyle(dueColor)
            }
            if done {
                Text("끝").font(.pt(12, .semibold)).foregroundStyle(Color.success)
            }
            if !done && topic.total > 0 {
                Text("남은 \(topic.remaining)개")
            }
            if topic.status == .NEXT, let p = topic.remainingPerDay, p > 0 {
                Text(WorkFormat.pace(p))
            }
        }
        .font(.pt(12))
        .foregroundStyle(Color.ink3)
    }
}

// MARK: - 주제 아래 할 일 (남은 일은 펼치고, 끝낸 일은 접어 둬요)

@MainActor
private struct WorkRoadmapTopicTasks: View {
    let open: [WorkTask]
    let done: [WorkTask]
    let today: String
    let onOpen: (WorkTask) -> Void
    let onToggle: (WorkTask) -> Void
    let onAdd: () -> Void
    /// 앰플리튜드용 (nil = 주제 없음)
    let topicId: Int?
    let goalId: Int

    @State var showOpen: Bool
    @State var showDone = false

    init(open: [WorkTask], done: [WorkTask], today: String, collapsed: Bool,
         onOpen: @escaping (WorkTask) -> Void, onToggle: @escaping (WorkTask) -> Void, onAdd: @escaping () -> Void,
         topicId: Int? = nil, goalId: Int = 0) {
        self.open = open
        self.done = done
        self.today = today
        self.onOpen = onOpen
        self.onToggle = onToggle
        self.onAdd = onAdd
        self.topicId = topicId
        self.goalId = goalId
        _showOpen = State(initialValue: !collapsed)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !open.isEmpty {
                if showOpen {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(open) { t in
                            WorkRoadmapTaskRow(task: t, today: today, onOpen: { onOpen(t) }, onToggle: { onToggle(t) })
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(Color.subtle.opacity(0.5), in: RoundedRectangle(cornerRadius: Radius.xl))
                    .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.line, lineWidth: 1))
                } else {
                    Button {
                        WK.track("work_roadmap.open_tasks_expand", goalId: goalId, ["topic_id": topicId, "item_count": open.count])
                        withAnimation(.easeOut(duration: 0.15)) { showOpen = true }
                    } label: {
                        Text("남은 할 일 \(open.count)개 보기")
                            .font(.pt(12, .semibold))
                            .foregroundStyle(Color.ink3)
                    }
                    .buttonStyle(.plain)
                }
            }
            HStack(spacing: 14) {
                Button { onAdd() } label: {
                    HStack(spacing: 2) {
                        NSymbol("plus", size: 11, weight: .bold)
                        Text("할 일")
                    }
                    .font(.pt(12, .semibold))
                    .foregroundStyle(Color.ink3)
                    .frame(height: 24)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if !done.isEmpty {
                    Button {
                        WK.track("work_roadmap.done_tasks_toggle", goalId: goalId, [
                            "topic_id": topicId, "expanded": !showDone, "item_count": done.count,
                        ])
                        withAnimation(.easeOut(duration: 0.15)) { showDone.toggle() }
                    } label: {
                        HStack(spacing: 2) {
                            Text("끝낸 일 \(done.count)")
                            NSymbol("chevron.down", size: 10, weight: .bold)
                                .rotationEffect(.degrees(showDone ? 180 : 0))
                        }
                        .font(.pt(12, .semibold))
                        .foregroundStyle(Color.success)
                        .frame(height: 24)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            if showDone && !done.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(done) { t in
                        WorkRoadmapTaskRow(task: t, today: today, onOpen: { onOpen(t) }, onToggle: { onToggle(t) })
                    }
                }
                .padding(.horizontal, 12)
            }
        }
        .padding(.top, 8)
    }
}

/// 로드맵 주제 아래 할 일 한 줄 — 체크하면 완료, 이름을 누르면 자세히
@MainActor
private struct WorkRoadmapTaskRow: View {
    let task: WorkTask
    let today: String
    let onOpen: () -> Void
    let onToggle: () -> Void

    var body: some View {
        let done = task.isDone
        let title: String = (task.emoji.map { $0.isEmpty ? "" : "\($0) " } ?? "") + task.taskName
        HStack(alignment: .top, spacing: 10) {
            WorkCheckCircle(done: done, size: 20) { onToggle() }
            Button { onOpen() } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.pt(14, done ? PTWeight.regular : PTWeight.medium))
                        .strikethrough(done, color: Color.ink3)
                        .foregroundStyle(done ? Color.ink3 : Color.ink)
                        .multilineTextAlignment(.leading)
                    if !done {
                        WorkTaskMetaRow(task: task, today: today, showTopic: false)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 6)
    }
}

/// "주제 없음" 할 일 — 로드맵 맨 끝(🏁 앞)에 점선 점으로
@MainActor
private struct WorkRoadmapNoTopicRow<Tasks: View>: View {
    let count: Int
    let tasks: () -> Tasks

    init(count: Int, @ViewBuilder tasks: @escaping () -> Tasks) {
        self.count = count
        self.tasks = tasks
    }

    var body: some View {
        WorkRoadmapRail {
            ZStack {
                Circle().fill(Color.surface)
                Circle().strokeBorder(Color.borderSubtle, style: StrokeStyle(lineWidth: 2, dash: [1, 3]))
            }
        } content: {
            VStack(alignment: .leading, spacing: 2) {
                Text("세부 목표 없음").font(.pt(15, .bold)).foregroundStyle(Color.ink2)
                Text("아직 세부 목표를 정하지 않은 할 일 \(count)개").font(.pt(12)).foregroundStyle(Color.ink3)
                tasks()
            }
        }
    }
}

/// 오늘 — 점 + 가로 선
private struct WorkRoadmapTodayMarker: View {
    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(Color.ink).frame(width: 10, height: 10).frame(width: 28)
            Text("오늘").font(.pt(12, .bold)).foregroundStyle(Color.ink)
            Rectangle().fill(Color.borderSoft).frame(height: 1)
        }
        .padding(.bottom, 20)
        .background(alignment: .leading) {
            Rectangle().fill(Color.line).frame(width: 2).padding(.leading, 13)
        }
        .accessibilityElement(children: .combine)
    }
}

/// 🏁 끝 점
private struct WorkRoadmapFinishRow: View {
    let done: Int
    let total: Int

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.ink)
                Image(systemName: "flag.fill").font(.system(size: 12)).foregroundStyle(Color.onInk)
            }
            .frame(width: 28, height: 28)
            Text(done == total ? "모두 끝냈어요" : "\(total - done)개 남았어요")
                .font(.pt(13, .bold))
                .foregroundStyle(Color.ink)
        }
    }
}

// MARK: - 공용: 체크 동그라미 · Task 메타 (보드에서도 써요)

struct WorkCheckCircle: View {
    let done: Bool
    var size: CGFloat = 22
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(done ? Color.brand : Color.clear)
                Circle().strokeBorder(done ? Color.actionPrimary : Color.borderControl, lineWidth: 1.5)
                if done {
                    NSymbol("checkmark", size: size * 0.5, weight: .heavy)
                        .foregroundStyle(Color.white)
                }
            }
            .frame(width: size, height: size)
            .padding(2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(done ? "완료 취소" : "완료")
    }
}

/// 주제 · 반복 · 진행 중 · 하는 날 · 시각 · 마감
struct WorkTaskMetaRow: View {
    let task: WorkTask
    let today: String
    var showTopic = true

    private var dueColor: Color {
        if task.overdue { return Color.danger }
        return (task.dueInDays ?? 99) <= 2 ? Color.warningInk : Color.ink3
    }

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 2) {
            if showTopic, let name = task.topicName {
                Text(name)
                    .font(.pt(11, .medium))
                    .foregroundStyle(Color.ink2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1)
                    .background(Color.subtle, in: RoundedRectangle(cornerRadius: 4))
            }
            if task.routine {
                Text("반복").font(.pt(12, .semibold)).foregroundStyle(Color.textBrand)
            }
            if task.status == .IN_PROGRESS {
                Text("진행 중").font(.pt(12, .semibold)).foregroundStyle(Color.textBrand)
            }
            if let d = task.date, d != today {
                Text(WorkFormat.dayLabel(d, today: today))
            }
            if let s = task.startTime {
                Text(s)
            }
            if let due = WorkFormat.dueLabel(task) {
                Text(due).font(.pt(12, .semibold)).foregroundStyle(dueColor)
            }
        }
        .font(.pt(12))
        .foregroundStyle(Color.ink3)
    }
}

#Preview("로드맵") {
    let board = WorkSampleData.board
    return ScrollView {
        WorkRoadmapSection(board: board, model: WorkBoardModel.preview(board),
                           onOpenTask: { _ in }, onAddTask: { _ in }, onEditTopic: { _ in }, onAddTopic: {})
            .padding(16)
    }
    .canvasBackground()
}

#Preview("로드맵 · 비어 있음") {
    let board = WorkSampleData.emptyBoard
    return ScrollView {
        WorkRoadmapSection(board: board, model: WorkBoardModel.preview(board),
                           onOpenTask: { _ in }, onAddTask: { _ in }, onEditTopic: { _ in }, onAddTopic: {})
            .padding(16)
    }
    .canvasBackground()
}
