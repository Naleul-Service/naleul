// Features/Work/WorkBoardModel.swift
//
// 웹 features/work/api.ts useWorkBoard · useMoveWorkTask(moveLocally) · useUpdateWorkTopic
// 업무형 목표의 "보드" 탭 상태. 칸 옮기기는 누르자마자 먼저 옮겨 보여주고, 실패하면 되돌려요.

import SwiftUI
import UIKit
import Observation

@MainActor
@Observable
final class WorkBoardModel {
    let goalId: Int
    var phase: LoadPhase<WorkBoard> = .loading
    /// "끝냈어요" 표시 중인 주제
    var togglingTopicId: Int?
    /// 미리보기용 (네트워크 안 씀)
    var isPreview = false
    /// 목표의 반복 루틴 수 (work_board.view 의 routine_count)
    var routineCount = 0
    /// 보드 탭을 연 뒤 처음 그렸는지 (다시 불러올 땐 view 를 보내지 않아요)
    private var viewSent = false
    /// 마지막으로 못 불러온 HTTP 상태 (다시 시도 버튼에 붙여요)
    private(set) var loadErrorStatus: Int?

    init(goalId: Int, routineCount: Int = 0) {
        self.goalId = goalId
        self.routineCount = routineCount
    }

    private func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        WK.track(event, goalId: goalId, props)
    }

    private func trackView(_ b: WorkBoard) {
        guard !viewSent else { return }
        viewSent = true
        let s = b.summary
        track("work_board.view", [
            "topic_count": s.topicsTotal, "topic_done_count": s.topicsDone,
            "overdue_topic_count": b.timeline.filter { $0.status == .OVERDUE }.count,
            "work_total_count": s.total, "work_done_count": s.done, "open_task_count": s.remaining,
            "in_progress_count": s.inProgress, "overdue_count": s.overdue, "due_this_week_count": s.dueThisWeek,
            "has_next_due": s.nextTopic?.dueDate != nil, "d_day": s.nextTopic?.dDay, "routine_count": routineCount,
        ])
    }

    var board: WorkBoard? { phase.value }

    func load() async {
        if isPreview { return }
        do {
            if let b = try await WorkAPI.board(goalId: goalId) {
                phase = .loaded(b)
                trackView(b)
            } else if phase.value == nil {
                phase = .failed("보드를 불러오지 못했어요")
                loadErrorStatus = 404
                track("work_board.load_fail", ["error_status": 404])
            }
        } catch is CancellationError {
            return
        } catch {
            // 이미 보여주던 보드가 있으면 그대로 둬요
            if phase.value == nil {
                phase = .failed(errorMessage(error))
                loadErrorStatus = (error as? APIError)?.httpStatus ?? 0
                track("work_board.load_fail", ["error_status": loadErrorStatus])
            }
        }
    }

    // MARK: Task

    /// 체크 — 완료 ↔ 시작 전 (surface: roadmap · board)
    func toggle(_ task: WorkTask, surface: String) async {
        await move(task, to: task.isDone ? .NOT_STARTED : .DONE, surface: surface, method: "check")
    }

    /// 보드 칸 옮기기 (먼저 옮겨 보여주고, 실패하면 되돌려요). method: check · menu
    func move(_ task: WorkTask, to: WorkColumn, surface: String, method: String) async {
        guard task.status != to else { return }
        track("work_task.status_change", WK.taskProps(task, surface: surface).merging([
            "from_status": task.status.rawValue, "to_status": to.rawValue, "move_method": method,
        ]) { $1 })
        if task.routine {
            await toggleRoutine(task, surface: surface, method: method)
            return
        }
        guard let prev = board else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.easeOut(duration: 0.2)) {
            phase = .loaded(Self.moveLocally(prev, task: task, to: to))
        }
        if isPreview { return }
        let gid = goalId
        let tid = task.taskId
        let ok = await Mutation.run(invalidate: WorkAPI.changed) {
            try await WorkAPI.move(goalId: gid, taskId: tid, to: to)
        }
        track("work_task.move_result", GD.result(ok != nil).merging([
            "surface": surface, "task_id": tid, "to_status": to.rawValue, "move_method": method,
        ]) { $1 })
        if ok == nil {
            withAnimation(.easeOut(duration: 0.2)) { phase = .loaded(prev) }
        }
    }

    /// 루틴 Task 는 보드 칸이 없어서 캘린더와 같은 완료 · 완료 취소로
    func toggleRoutine(_ task: WorkTask, surface: String, method: String) async {
        if isPreview { return }
        let msg = task.isDone ? "완료를 취소했어요." : "완료했어요."
        let current: TaskStatus = task.isDone ? .COMPLETED : .TODO
        let tid = task.taskId
        let ok = await Mutation.run(invalidate: WorkAPI.changed, successMessage: msg) {
            try await GoalAPI.toggleTask(taskId: tid, currentStatus: current)
        }
        track("work_task.move_result", GD.result(ok != nil).merging([
            "surface": surface, "task_id": tid, "to_status": (task.isDone ? WorkColumn.NOT_STARTED : .DONE).rawValue,
            "move_method": method,
        ]) { $1 })
    }

    /// 보드 카드 … 메뉴 '지우기' → 알림창 '지우기'
    func delete(_ task: WorkTask) async {
        if isPreview { return }
        let tid = task.taskId
        let ok = await Mutation.run(invalidate: WorkAPI.changed, successMessage: "Task를 지웠어요.") {
            try await WorkAPI.deleteTask(taskId: tid)
        }
        track("work_board.card_delete_result", GD.result(ok != nil).merging(["task_id": tid]) { $1 })
    }

    // MARK: 주제

    /// 로드맵 점 — "끝냈어요" ↔ "다시 열기". Task 를 모두 끝내서 끝난 주제는 되돌릴 게 없어요
    func toggleTopic(_ topic: WorkTopic) async {
        guard let id = topic.topicId, togglingTopicId == nil else { return }
        if topic.isDone && !topic.completed {
            // Task 를 모두 끝내 자동으로 끝난 주제 — 눌러도 반응이 없어요
            track("work_roadmap.topic_locked_click", ["topic_id": id])
            return
        }
        if isPreview { return }
        togglingTopicId = id
        defer { togglingTopicId = nil }
        let completed = !topic.completed
        let gid = goalId
        track("work_roadmap.topic_complete_toggle", [
            "topic_id": id, "checked": completed, "topic_task_count": topic.remaining, "d_day": topic.dDay,
            "entry_point": "node",
        ])
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let ok = await Mutation.run(invalidate: WorkAPI.changed,
                           successMessage: completed ? "세부 목표를 끝냈어요. 수고했어요!" : "세부 목표를 다시 열었어요.") {
            try await WorkAPI.updateTopic(goalId: gid, topicId: id, WorkTopicPatch(completed: completed))
        }
        track("work_roadmap.topic_result", GD.result(ok != nil).merging([
            "topic_id": id, "topic_action": completed ? "complete" : "reopen",
        ]) { $1 })
    }

    // MARK: 서버 응답 전에 보드 칸만 먼저 옮겨 보여주기 (웹 moveLocally)

    static func moveLocally(_ b: WorkBoard, task: WorkTask, to: WorkColumn) -> WorkBoard {
        var moved = task
        moved.status = to
        let id = task.taskId
        var cols = b.board
        cols.notStarted = cols.notStarted.filter { $0.taskId != id }
        cols.inProgress = cols.inProgress.filter { $0.taskId != id }
        cols.done = cols.done.filter { $0.taskId != id }
        cols.doneTotal = b.board.doneTotal + (to == .DONE ? 1 : 0) - (task.status == .DONE ? 1 : 0)
        switch to {
        case .DONE: cols.done = [moved] + cols.done
        case .IN_PROGRESS: cols.inProgress.append(moved)
        case .NOT_STARTED: cols.notStarted.append(moved)
        }
        var out = b
        out.board = cols
        let swap: ([WorkTask]) -> [WorkTask] = { list in list.map { $0.taskId == id ? moved : $0 } }
        out.todayTasks = swap(b.todayTasks)
        out.upcoming = swap(b.upcoming)
        out.backlog = swap(b.backlog)
        return out
    }

    /// 미리보기용 (샘플 데이터)
    static func preview(_ board: WorkBoard = WorkSampleData.board) -> WorkBoardModel {
        let m = WorkBoardModel(goalId: board.goalCategoryId)
        m.isPreview = true
        m.phase = .loaded(board)
        return m
    }
}

// MARK: - 미리보기 데이터

enum WorkSampleData {
    static var today: String { KST.today() }

    static func task(_ id: Int, _ name: String, topic: Int?, _ topicName: String?, _ status: WorkColumn,
                     date: String? = nil, startTime: String? = nil, dueIn: Int? = nil,
                     memo: String? = nil, routine: Bool = false) -> WorkTask {
        let t = today
        let due: String? = dueIn.map { KST.addDays(t, $0) }
        return WorkTask(taskId: id, taskName: name, emoji: nil, topicId: topic, topicName: topicName,
                        status: status, date: date, startTime: startTime, endTime: nil,
                        dueDate: due, dueInDays: dueIn,
                        overdue: (dueIn ?? 0) < 0 && status != .DONE,
                        memo: memo, routine: routine,
                        completedAt: status == .DONE ? "\(t)T10:00:00" : nil)
    }

    static func topic(_ id: Int?, _ name: String, order: Int, dueIn: Int?, status: WorkTopicStatus,
                      completed: Bool = false, total: Int, done: Int, perDay: Double? = nil) -> WorkTopic {
        let t = today
        let percent: Int? = total > 0 ? Int((Double(done) / Double(total) * 100).rounded()) : nil
        return WorkTopic(topicId: id, name: name, emoji: nil, sortOrder: order,
                         dueDate: dueIn.map { KST.addDays(t, $0) }, dDay: dueIn,
                         status: status, completed: completed,
                         total: total, done: done, remaining: total - done,
                         progressPercent: percent, remainingPerDay: perDay)
    }

    static var timeline: [WorkTopic] {
        [
            topic(1, "기획서 정리", order: 0, dueIn: -10, status: .DONE, completed: true, total: 3, done: 3),
            topic(2, "디자인 시안", order: 1, dueIn: -2, status: .OVERDUE, total: 0, done: 0),
            topic(3, "API 연동", order: 2, dueIn: 3, status: .NEXT, total: 5, done: 2, perDay: 1),
            topic(4, "출시 준비", order: 3, dueIn: 12, status: .UPCOMING, total: 2, done: 0, perDay: 0.17),
            topic(5, "아이디어 메모", order: 4, dueIn: nil, status: .NO_DUE, total: 1, done: 0),
        ]
    }

    static var columns: WorkBoardColumns {
        let t = today
        return WorkBoardColumns(
            notStarted: [
                task(31, "결제 화면 연동", topic: 3, "API 연동", .NOT_STARTED, date: t, dueIn: 3),
                task(32, "푸시 알림 테스트", topic: 3, "API 연동", .NOT_STARTED),
                task(41, "스토어 스크린샷", topic: 4, "출시 준비", .NOT_STARTED, date: KST.addDays(t, 5), dueIn: 10),
                task(42, "심사 메모 작성", topic: 4, "출시 준비", .NOT_STARTED),
                task(51, "온보딩 개선 아이디어", topic: 5, "아이디어 메모", .NOT_STARTED, memo: "첫 화면에서 목표 예시를 3개 보여주기"),
                task(91, "회의록 정리", topic: nil, nil, .NOT_STARTED, date: t),
            ],
            inProgress: [
                task(33, "로그인 API 붙이기", topic: 3, "API 연동", .IN_PROGRESS, date: t, startTime: "14:00", dueIn: 1,
                     memo: "토큰 갱신까지"),
            ],
            done: [
                task(35, "네트워크 계층", topic: 3, "API 연동", .DONE, date: KST.addDays(t, -1)),
                task(34, "API 명세 확인", topic: 3, "API 연동", .DONE, date: KST.addDays(t, -3)),
                task(13, "일정표", topic: 1, "기획서 정리", .DONE, date: KST.addDays(t, -11)),
                task(12, "화면 흐름도", topic: 1, "기획서 정리", .DONE, date: KST.addDays(t, -12)),
                task(11, "요구사항 정리", topic: 1, "기획서 정리", .DONE, date: KST.addDays(t, -13)),
            ],
            doneTotal: 5
        )
    }

    static var board: WorkBoard {
        let t = today
        let line = timeline
        let cols = columns
        let noTopic = topic(nil, "세부 목표 없음", order: 99, dueIn: nil, status: .NO_DUE, total: 1, done: 0)
        let summary = WorkSummary(startDate: KST.addDays(t, -30), total: 12, notStarted: 6, inProgress: 1, done: 5,
                                  remaining: 7, overdue: 0, dueThisWeek: 2, backlog: 3, progressPercent: 42,
                                  topicsDone: 1, topicsTotal: 5, nextTopic: line[2])
        let todayTasks = (cols.inProgress + cols.notStarted).filter { $0.date == t }
        return WorkBoard(
            goalCategoryId: 2, goalName: "나를 업무", emoji: "💼", description: nil, today: t,
            summary: summary, timeline: line, todayTasks: todayTasks, doneToday: [], upcoming: [],
            topics: line + [noTopic], board: cols,
            backlog: (cols.notStarted).filter { $0.date == nil },
            archive: [],
            retrospectives: [
                WorkRetro(retrospectiveId: 1, reviewDate: KST.addDays(t, -1), reviewType: "DAILY",
                          content: "로그인 흐름에서 토큰 갱신이 막혔다. 내일은 문서부터 다시 보기."),
                WorkRetro(retrospectiveId: 2, reviewDate: KST.addDays(t, -4), reviewType: "DAILY",
                          content: "기획서를 일찍 끝내서 여유가 생겼다."),
            ]
        )
    }

    /// 마감 지난 주제가 다음 마감일 때
    static var overdueBoard: WorkBoard {
        var b = board
        b.summary.nextTopic = timeline[1]
        return b
    }

    /// 아직 주제 · Task 가 없을 때
    static var emptyBoard: WorkBoard {
        let t = today
        return WorkBoard(
            goalCategoryId: 2, goalName: "나를 업무", emoji: nil, description: nil, today: t,
            summary: WorkSummary(startDate: nil, total: 0, notStarted: 0, inProgress: 0, done: 0, remaining: 0,
                                 overdue: 0, dueThisWeek: 0, backlog: 0, progressPercent: nil,
                                 topicsDone: 0, topicsTotal: 0, nextTopic: nil),
            timeline: [], todayTasks: [], doneToday: [], upcoming: [], topics: [],
            board: WorkBoardColumns(notStarted: [], inProgress: [], done: [], doneTotal: 0),
            backlog: [], archive: [], retrospectives: []
        )
    }

    static let shareText = """
    [나를 업무] 남은 일 (10/13)

    ■ API 연동 (~10/16 · D-3)
    - [진행 중] 로그인 API 붙이기 (D-1)
    - 결제 화면 연동
    - 푸시 알림 테스트

    ■ 출시 준비 (~10/25)
    - 스토어 스크린샷
    - 심사 메모 작성
    """
}
