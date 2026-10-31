// Features/Work/WorkBoardTab.swift
//
// 웹 features/work/ui/WorkBoardView.tsx — 업무형 목표의 "보드" 탭 (모바일 한 줄 배치)
//  1. 다음 마감 · 진행 요약 (+ 공유)
//  2. 적기 — 할 일 · 한 일 · 회고 (시트)
//  3. 반복 루틴 (아래에 있으면 잘 안 보여서 위로)
//  4. 로드맵 — 주제를 마감 순서대로, 점 사이에 그 주제의 남은 할 일
//  5. 보드 (시작 전 · 진행 중 · 완료)
//  6. 업무 일지 · 7. 메모
// GoalDetailScreen 의 탭 영역(ScrollView · VStack(spacing: 14) · 좌우 16) 안에 들어가요.

import SwiftUI

/// 적기 시트 열기 (탭 · 주제)
struct WorkComposeRequest: Identifiable {
    let id = UUID()
    var tab: WorkComposeTab
    var topicId: Int?
}

/// 열린 Task 시트 + 어디서 열었는지 (roadmap · board)
struct WorkOpenedTask: Identifiable {
    var id: Int { task.taskId }
    let task: WorkTask
    let surface: String
}

/// 주제 시트 — 새로 만들기 / 고치기
enum WorkTopicSheetTarget: Identifiable {
    case create
    case edit(WorkTopic)

    var id: String {
        switch self {
        case .create: return "create"
        case .edit(let t): return "edit-\(t.id)"
        }
    }
}

@MainActor
struct WorkBoardTab: View {
    let goal: GoalCategory

    @State private var model: WorkBoardModel
    @State private var compose: WorkComposeRequest?
    @State private var openedTask: WorkOpenedTask?
    @State private var topicTarget: WorkTopicSheetTarget?
    @State private var sharing = false

    /// false = 요약 카드는 목표 상세 맨 위(WorkGoalSummaryCard)가 그리고 불러와요 (DESIGN_SPEC 12.1)
    private var showsSummary = true

    init(goal: GoalCategory) {
        self.goal = goal
        _model = State(initialValue: WorkBoardModel(goalId: goal.goalCategoryId, routineCount: goal.analyticsRoutineCount))
    }

    /// 목표 상세가 만든 모델을 같이 써요 — 요약 카드는 탭 위에서 따로 그려요
    init(goal: GoalCategory, sharedModel: WorkBoardModel) {
        self.goal = goal
        _model = State(initialValue: sharedModel)
        showsSummary = false
    }

    /// 미리보기 · 테스트용 (네트워크 안 씀)
    init(goal: GoalCategory, previewModel: WorkBoardModel) {
        self.goal = goal
        _model = State(initialValue: previewModel)
    }

    private var gid: Int { goal.goalCategoryId }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if model.isPreview { return }
        WK.track(event, goalId: gid, props)
    }

    private func openCompose(_ tab: WorkComposeTab, topicId: Int?, entry: String) {
        track("work_compose.open", ["compose_tab": tab.rawValue, "entry_point": entry, "topic_id": topicId])
        compose = WorkComposeRequest(tab: tab, topicId: topicId)
    }

    private func openTask(_ t: WorkTask, surface: String) {
        track("work_task.open", WK.taskProps(t, surface: surface).merging(["task_status": t.status.rawValue]) { $1 })
        openedTask = WorkOpenedTask(task: t, surface: surface)
    }

    var body: some View {
        VStack(spacing: 14) {
            if showsSummary {
                summary
            }
            // "적기" 카드는 없앴어요 — 떠 있는 적기 버튼으로 (DESIGN_SPEC 12.2)
            GoalRoutinesSection(goal: goal)
            if let board = model.board {
                WorkRoadmapSection(
                    board: board,
                    model: model,
                    onOpenTask: { t in openTask(t, surface: "roadmap") },
                    onAddTask: { topicId in
                        track("work_roadmap.add_task_click", ["topic_id": topicId, "compose_tab": "task"])
                        openCompose(.task, topicId: topicId, entry: "roadmap")
                    },
                    onEditTopic: { t in
                        track("work_roadmap.topic_edit_open", ["topic_id": t.topicId])
                        topicTarget = .edit(t)
                    },
                    onAddTopic: {
                        track("work_roadmap.add_topic_click", ["topic_count": board.summary.topicsTotal])
                        topicTarget = .create
                    }
                )
                WorkKanbanSection(board: board, model: model, onOpenTask: { t in openTask(t, surface: "board") })
            }
            journal
            if goal.motive != nil || goal.aiNote != nil {
                GoalNotesCard(goal: goal)
            }
        }
        .modifier(WorkBoardReload(enabled: showsSummary, goalId: goal.goalCategoryId, model: model))
        .sheet(item: $compose) { req in
            WorkComposeSheet(goal: goal, initialTab: req.tab, topicId: req.topicId, board: model.board,
                             isPreview: model.isPreview)
        }
        .sheet(item: $openedTask) { o in
            WorkTaskSheet(goalId: gid, task: o.task, topics: model.board?.topics ?? [], surface: o.surface,
                          isPreview: model.isPreview)
        }
        .sheet(item: $topicTarget) { target in
            switch target {
            case .create:
                WorkTopicSheet(goalId: gid, topic: nil, topicCount: model.board?.summary.topicsTotal ?? 0,
                               isPreview: model.isPreview)
            case .edit(let t):
                WorkTopicSheet(goalId: gid, topic: t, isPreview: model.isPreview)
            }
        }
        .sheet(isPresented: $sharing) {
            WorkShareSheet(goalId: gid, previewText: model.isPreview ? WorkSampleData.shareText : nil)
        }
    }

    @ViewBuilder
    private var summary: some View {
        switch model.phase {
        case .loading:
            LoadingView(minHeight: 200)
        case .failed(let message):
            ErrorView(message: message) {
                track("work_board.retry_click", ["error_status": model.loadErrorStatus])
                await model.load()
            }
                .card()
        case .loaded(let board):
            WorkSummaryCard(board: board, colorCode: goal.colorCode, onShare: {
                track("work_board.share_click", ["open_task_count": board.summary.remaining])
                sharing = true
            }, onPillTap: { kind in track("work_board.pill_dead_click", ["pill_kind": kind]) })
        }
    }

    @ViewBuilder
    private var journal: some View {
        if model.isPreview {
            RecordJournalView(goalId: gid, goalName: goal.goalCategoryName, preview: RecordSampleData.journal)
        } else {
            RecordJournalView(goalId: gid, goalName: goal.goalCategoryName)
        }
    }
}

/// 보드가 직접 불러올 때만 (요약 카드를 탭 위에서 그리면 그쪽이 불러와요)
private struct WorkBoardReload: ViewModifier {
    let enabled: Bool
    let goalId: Int
    let model: WorkBoardModel

    func body(content: Content) -> some View {
        if enabled {
            content.reloadOn([.work, .goals, .timetable], id: goalId) { await model.load() }
        } else {
            content
        }
    }
}

// MARK: - 업무형 목표 맨 위 요약 카드 (머리 카드 + 다음 마감 요약을 하나로 · DESIGN_SPEC 12.1)

@MainActor
struct WorkGoalSummaryCard: View {
    let goal: GoalCategory
    let model: WorkBoardModel
    @State private var sharing = false

    private var gid: Int { goal.goalCategoryId }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if model.isPreview { return }
        WK.track(event, goalId: gid, props)
    }

    private var header: WorkSummaryHeader {
        let title = (goal.emoji.map { $0.isEmpty ? "" : $0 + " " } ?? "") + goal.goalCategoryName
        // 태그 두 개 + 글자 "2026.10.06 시작 · 마감은 주제마다" (DESIGN_SPEC 13.8)
        let start = goal.goalCategoryStartDate.map { GoalFormat.formatDot($0) + " 시작 · " } ?? ""
        return WorkSummaryHeader(title: title, tags: [goal.goalCategoryStatus.label, "업무형"],
                                 meta: start + "마감은 세부 목표마다")
    }

    var body: some View {
        Group {
            switch model.phase {
            case .loading:
                LoadingView(minHeight: 200)
            case .failed(let message):
                ErrorView(message: message) {
                    track("work_board.retry_click", ["error_status": model.loadErrorStatus])
                    await model.load()
                }
                .card()
            case .loaded(let board):
                WorkSummaryCard(board: board, colorCode: goal.colorCode, header: header, onShare: {
                    track("work_board.share_click", ["open_task_count": board.summary.remaining])
                    sharing = true
                }, onPillTap: { kind in track("work_board.pill_dead_click", ["pill_kind": kind]) })
            }
        }
        .reloadOn([.work, .goals, .timetable], id: gid) { await model.load() }
        .sheet(isPresented: $sharing) {
            WorkShareSheet(goalId: gid, previewText: model.isPreview ? WorkSampleData.shareText : nil)
        }
    }
}

/// 요약 카드 맨 위 두 줄 (이름 · 태그)
struct WorkSummaryHeader {
    let title: String
    let tags: [String]
    /// 태그 옆 보조 글 (Caption/MD)
    var meta: String? = nil
}

// MARK: - 1. 다음 마감 · 진행 요약 (웹 SummaryHero)

private enum WorkPillTone { case normal, danger, warning }

@MainActor
private struct WorkSummaryCard: View {
    let board: WorkBoard
    /// 목표 색 (NowCard 스타일 바탕 · 진행 막대)
    var colorCode: String? = nil
    /// 있으면 맨 위에 이름 · 태그 줄 + 구분선 (공유 버튼은 이름 줄 오른쪽으로)
    var header: WorkSummaryHeader? = nil
    let onShare: () -> Void
    /// 알약을 눌렀을 때 (지금은 아무 동작 없음 — 앰플리튜드 pill_dead_click 만)
    var onPillTap: (String) -> Void = { _ in }

    private var s: WorkSummary { board.summary }

    var body: some View {
        if let header {
            // 목표 상세 맨 위: 새 요약 카드 (DESIGN_SPEC 13.8)
            redesigned(header)
        } else {
            legacy
        }
    }

    // MARK: 목표 상세 요약 카드 (13.8)

    private var tone: GoalTone { Color.goalTone(colorCode) }

    private func redesigned(_ header: WorkSummaryHeader) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // 1줄: 이모지 + 이름 (H3 · 1–2줄) · 오른쪽 공유 아이콘
            HStack(alignment: .top, spacing: 8) {
                Text(header.title)
                    .textStyle(.h3)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                IconButton(systemName: "square.and.arrow.up", size: 18, label: "공유하기") { onShare() }
                    .padding(.top, -4)
                    .padding(.trailing, -6)
            }
            // 2줄 (4 아래): 태그 · 보조 글
            FlowLayout(spacing: 6) {
                ForEach(header.tags, id: \.self) { t in Tag(t) }
                if let meta = header.meta {
                    Text(meta)
                        .font(.captionMD)
                        .foregroundStyle(Color.textSecondary)
                        .frame(height: 24)
                }
            }
            .padding(.top, 4)

            Rectangle().fill(Color.borderSoft).frame(height: 1).padding(.vertical, 16)

            statsRow
            progressRow.padding(.top, 16)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.bg, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    /// 다음 마감 | 진행 | 남은 Task — 같은 너비 3칸 · 사이 1px 세로선
    private var statsRow: some View {
        let next = s.nextTopic
        return HStack(alignment: .top, spacing: 0) {
            statColumn("다음 마감") {
                if let next {
                    Text(next.title).font(.labelMD).foregroundStyle(Color.textPrimary).lineLimit(1)
                    Text(WorkFormat.dDay(next.dDay) ?? "–")
                        .font(.pt(20, .semibold))
                        .foregroundStyle((next.dDay ?? 0) < 0 ? Color.statusErrorFg : tone.fg)
                } else {
                    Text(emptyTitle).font(.labelMD).foregroundStyle(Color.textSecondary).lineLimit(2)
                }
            }
            statDivider
            statColumn("진행") {
                Text("\(s.done)/\(s.total)").font(.ptMono(20, .semibold)).foregroundStyle(Color.textPrimary)
            }
            statDivider
            statColumn("남은 Task") {
                Text("\(s.remaining)개").font(.pt(20, .semibold)).foregroundStyle(Color.textPrimary)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var statDivider: some View {
        Rectangle().fill(Color.borderSoft).frame(width: 1).frame(maxHeight: .infinity)
    }

    private func statColumn<V: View>(_ label: String, @ViewBuilder _ value: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.captionMD).foregroundStyle(Color.textSecondary)
            value()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
    }

    /// "Task 완료 74%" + 막대 h6 + "끝낸 주제 0/1 · 2026.10.31(토)까지 · 마감까지 2일에 1개씩"
    private var progressRow: some View {
        let pct = s.progressPercent ?? (s.total > 0 ? Int((Double(s.done) / Double(s.total) * 100).rounded()) : 0)
        var parts = ["끝낸 세부 목표 \(s.topicsDone)/\(s.topicsTotal)"]
        if let next = s.nextTopic, let due = next.dueDate {
            parts.append("\(due.replacingOccurrences(of: "-", with: "."))(\(KST.weekdayLabel[KST.weekdayIndex(due)]))까지")
            if let p = next.remainingPerDay, p > 0, (next.dDay ?? -1) >= 0 {
                parts.append("마감까지 \(WorkFormat.pace(p))")
            }
        }
        let caption = parts.joined(separator: " · ")
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Task 완료").font(.labelMD).foregroundStyle(Color.textPrimary)
                Spacer(minLength: 0)
                Text("\(pct)%").font(.labelMD).foregroundStyle(Color.textPrimary)
            }
            ProgressBar(value: Double(pct) / 100, color: tone.accent, track: Color.progressTrack, height: 6)
            Text(caption)
                .font(.captionMD)
                .foregroundStyle(Color.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: 보드 탭 단독 (예전 모양)

    private var legacy: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let header {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center, spacing: 8) {
                        Text(header.title)
                            .font(.pt(20, .semibold))
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                        Circle().fill(Color.goal(colorCode)).frame(width: 10, height: 10)
                        Spacer(minLength: 0)
                        shareButton
                    }
                    FlowLayout(spacing: 6) {
                        ForEach(header.tags, id: \.self) { t in
                            Text(t)
                                .font(.labelSM)
                                .foregroundStyle(Color.textSecondary)
                                .padding(.horizontal, 10)
                                .frame(height: 24)
                                .background(Color.bgSurface.opacity(0.7), in: Capsule())
                        }
                    }
                }
                Rectangle().fill(Color.borderSoft).frame(height: 1)
            }
            HStack(alignment: .top, spacing: 12) {
                nextBlock
                Spacer(minLength: 0)
                if header == nil {
                    shareButton
                }
            }
            progressBlock
            pills
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.goalTone(colorCode).bg, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var shareButton: some View {
        NButton("공유", variant: .secondary, size: .sm, icon: "square.and.arrow.up") { onShare() }
            .accessibilityLabel("공유하기")
    }

    @ViewBuilder
    private var nextBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 5) {
                Image(systemName: "flag").font(.system(size: 12, weight: .semibold))
                Text("다음 마감")
            }
            .font(.pt(13))
            .foregroundStyle(Color.textSecondary)

            if let next = s.nextTopic, let due = next.dueDate {
                let overdue = (next.dDay ?? 0) < 0
                Text(next.title)
                    .font(.labelLG)
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                Text(WorkFormat.dDay(next.dDay) ?? "")
                    .font(.pt(28, .bold))
                    .foregroundStyle(overdue ? Color.statusErrorFg : Color.textPrimary)
                    .tracking(-0.5)
                Text(WorkFormat.fullDate(due) + "까지" + (next.remaining > 0 ? " · 남은 \(next.remaining)개" : ""))
                    .font(.pt(14))
                    .foregroundStyle(Color.textSecondary)
            } else {
                Text(emptyTitle)
                    .font(.pt(24, .semibold))
                    .foregroundStyle(Color.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(emptyHint)
                    .font(.pt(13))
                    .foregroundStyle(Color.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var emptyTitle: String {
        if s.topicsTotal == 0 { return "아직 세부 목표가 없어요" }
        if s.topicsDone == s.topicsTotal { return "모든 세부 목표를 끝냈어요" }
        return "마감 정한 세부 목표가 없어요"
    }

    private var emptyHint: String {
        if s.topicsTotal == 0 { return "로드맵에 세부 목표를 만들고 마감을 정해 보세요" }
        if s.topicsDone == s.topicsTotal { return "수고했어요! 다음 세부 목표를 이어서 만들어도 돼요" }
        return "세부 목표에 마감을 정하면 하루에 몇 개씩 해야 하는지 알려드려요"
    }

    private var progressBlock: some View {
        let topicPercent: Int? = s.topicsTotal > 0
            ? Int((Double(s.topicsDone) / Double(s.topicsTotal) * 100).rounded())
            : nil
        return VStack(alignment: .leading, spacing: 10) {
            Text("진행").font(.pt(13)).foregroundStyle(Color.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("\(s.done)").font(.ptMono(20, .semibold)).foregroundStyle(Color.textPrimary)
                Text("/ \(s.total)개 완료").font(.pt(17, .semibold)).foregroundStyle(Color.textSecondary)
            }
            VStack(spacing: 10) {
                WorkSummaryMeter(label: "Task 완료", value: s.progressPercent, right: nil, accent: Color.goalTone(colorCode).accent)
                WorkSummaryMeter(label: "끝낸 세부 목표", value: topicPercent, right: "\(s.topicsDone) / \(s.topicsTotal)", accent: Color.goalTone(colorCode).accent)
            }
            .padding(.top, 4)
        }
    }

    private var pills: some View {
        let next = s.nextTopic
        let overdueTopics = board.timeline.filter { $0.status == .OVERDUE }.count
        let pace: String? = {
            guard let n = next, let p = n.remainingPerDay, p > 0, (n.dDay ?? -1) >= 0 else { return nil }
            return "\(n.name) 마감까지 \(WorkFormat.pace(p))"
        }()
        return FlowLayout(spacing: 8) {
            pill("남은 Task \(s.remaining)개", .normal, kind: "remaining")
            if s.inProgress > 0 { pill("진행 중 \(s.inProgress)개", .normal, kind: "in_progress") }
            if let pace { pill(pace, .normal, kind: "pace") }
            if overdueTopics > 0 { pill("마감 지난 세부 목표 \(overdueTopics)개", .danger, kind: "overdue_topic") }
            if s.overdue > 0 { pill("마감 지난 Task \(s.overdue)개", .danger, kind: "overdue_task") }
            if s.dueThisWeek > 0 { pill("이번 주 마감 \(s.dueThisWeek)개", .warning, kind: "due_this_week") }
        }
    }

    private func pill(_ text: String, _ tone: WorkPillTone, kind: String) -> some View {
        let bg: Color
        let fg: Color
        switch tone {
        case .normal:
            bg = Color.bgSurface.opacity(0.7)
            fg = Color.textSecondary
        case .danger:
            bg = Color.statusErrorBg
            fg = Color.statusErrorFg
        case .warning:
            bg = Color.statusWarningBg
            fg = Color.statusWarningFg
        }
        return Text(text)
            .font(.pt(13, .semibold))
            .foregroundStyle(fg)
            .lineLimit(1)
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(bg, in: Capsule())
            .contentShape(Capsule())
            .onTapGesture { onPillTap(kind) }
    }
}

private struct WorkSummaryMeter: View {
    let label: String
    let value: Int?
    let right: String?
    /// 막대 색 (목표 accent)
    var accent: Color = .progressFill

    var body: some View {
        if let value {
            VStack(spacing: 4) {
                HStack {
                    Text(label).font(.pt(12)).foregroundStyle(Color.textSecondary)
                    Spacer(minLength: 0)
                    Text(right ?? "\(value)%").font(.ptMono(12, .semibold)).foregroundStyle(Color.textPrimary)
                }
                ProgressBar(value: Double(value) / 100, color: accent, track: Color.bgSurface.opacity(0.7), height: 6)
            }
        }
    }
}

// MARK: - 2. 적기 (할 일 · 한 일 · 회고)

@MainActor
private struct WorkComposeEntryRow: View {
    let onPick: (WorkComposeTab) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                NSymbol("square.and.pencil", size: 15, weight: .semibold)
                    .foregroundStyle(Color.textBrand)
                    .frame(width: 36, height: 36)
                    .background(Color.brandSoft, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("적기").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    Text("할 일 · 실제로 한 일 · 회고를 한곳에서 적어요")
                        .font(.pt(12))
                        .foregroundStyle(Color.ink3)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                ForEach(WorkComposeTab.allCases, id: \.self) { tab in
                    Button { onPick(tab) } label: {
                        VStack(spacing: 4) {
                            NSymbol(tab.icon, size: 15, weight: .semibold)
                            Text(tab.label).font(.pt(13, .semibold))
                        }
                        .foregroundStyle(Color.textBrand)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
                        .contentShape(RoundedRectangle(cornerRadius: Radius.xl))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(tab.label) 적기")
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.card)
                .stroke(Color.borderSubtle, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
        )
    }
}

#Preview("업무형 보드") {
    ScrollView {
        VStack(spacing: 14) {
            WorkBoardTab(goal: GoalPreviewData.recordGoal, previewModel: WorkBoardModel.preview(WorkSampleData.board))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview(goal: GoalPreviewData.recordGoal))
    .environment(AppRouter.shared)
}

#Preview("마감 지남") {
    ScrollView {
        VStack(spacing: 14) {
            WorkBoardTab(goal: GoalPreviewData.recordGoal, previewModel: WorkBoardModel.preview(WorkSampleData.overdueBoard))
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview(goal: GoalPreviewData.recordGoal))
    .environment(AppRouter.shared)
}

#Preview("비어 있음") {
    ScrollView {
        VStack(spacing: 14) {
            WorkBoardTab(goal: GoalPreviewData.recordGoal, previewModel: WorkBoardModel.preview(WorkSampleData.emptyBoard))
        }
        .padding(16)
    }
    .canvasBackground()
    .environment(GoalDetailViewModel.preview(goal: GoalPreviewData.recordGoal))
    .environment(AppRouter.shared)
}
