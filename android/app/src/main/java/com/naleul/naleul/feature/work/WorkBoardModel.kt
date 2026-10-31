// feature/work/WorkBoardModel.kt
//
// iOS Features/Work/WorkBoardModel.swift
// 웹 features/work/api.ts useWorkBoard · useMoveWorkTask(moveLocally) · useUpdateWorkTopic
// 업무형 목표의 "보드" 탭 상태. 칸 옮기기는 누르자마자 먼저 옮겨 보여주고, 실패하면 되돌려요.
// (iOS 의 진동 피드백은 화면 쪽에서 LocalHapticFeedback 으로 해요 — 모델은 화면을 몰라서)

package com.naleul.naleul.feature.work

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

class WorkBoardModel(val goalId: Int, var routineCount: Int = 0) : ScreenModel() {
    var phase by mutableStateOf<LoadPhase<WorkBoard>>(LoadPhase.Loading)
    /** "끝냈어요" 표시 중인 주제 */
    var togglingTopicId by mutableStateOf<Int?>(null)
    /** 미리보기용 (네트워크 안 씀) */
    var isPreview = false

    /** 보드 탭을 연 뒤 처음 그렸는지 (다시 불러올 땐 view 를 보내지 않아요) */
    private var viewSent = false
    /** 마지막으로 못 불러온 HTTP 상태 (다시 시도 버튼에 붙여요) */
    var loadErrorStatus: Int? = null
        private set

    val board: WorkBoard? get() = phase.value

    private fun track(event: String, props: EventProps = emptyMap()) {
        if (isPreview) return
        WK.track(event, goalId, props)
    }

    private fun trackView(b: WorkBoard) {
        if (viewSent) return
        viewSent = true
        val s = b.summary
        track("work_board.view", mapOf(
            "topic_count" to s.topicsTotal, "topic_done_count" to s.topicsDone,
            "overdue_topic_count" to b.timeline.count { it.status == WorkTopicStatus.OVERDUE },
            "work_total_count" to s.total, "work_done_count" to s.done, "open_task_count" to s.remaining,
            "in_progress_count" to s.inProgress, "overdue_count" to s.overdue, "due_this_week_count" to s.dueThisWeek,
            "has_next_due" to (s.nextTopic?.dueDate != null), "d_day" to s.nextTopic?.dDay, "routine_count" to routineCount,
        ))
    }

    suspend fun load() {
        if (isPreview) return
        try {
            val b = WorkAPI.board(goalId = goalId)
            if (b != null) {
                phase = LoadPhase.Loaded(b)
                trackView(b)
            } else if (phase.value == null) {
                phase = LoadPhase.Failed("보드를 불러오지 못했어요")
                loadErrorStatus = 404
                track("work_board.load_fail", mapOf("error_status" to 404))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 이미 보여주던 보드가 있으면 그대로 둬요
            if (phase.value == null) {
                phase = LoadPhase.Failed(errorMessage(e))
                loadErrorStatus = (e as? ApiError)?.httpStatus ?: 0
                track("work_board.load_fail", mapOf("error_status" to loadErrorStatus))
            }
        }
    }

    // MARK: Task

    /** 체크 — 완료 ↔ 시작 전 (surface: roadmap · board) */
    suspend fun toggle(task: WorkTask, surface: String = "board") {
        move(task, to = if (task.isDone) WorkColumn.NOT_STARTED else WorkColumn.DONE, surface = surface, method = "check")
    }

    /** 보드 칸 옮기기 (먼저 옮겨 보여주고, 실패하면 되돌려요). method: check · menu */
    suspend fun move(task: WorkTask, to: WorkColumn, surface: String = "board", method: String = "menu") {
        if (task.status == to) return
        track("work_task.status_change", WK.taskProps(task, surface) + mapOf(
            "from_status" to task.status.name, "to_status" to to.name, "move_method" to method,
        ))
        if (task.routine) {
            toggleRoutine(task, surface, method)
            return
        }
        val prev = board ?: return
        phase = LoadPhase.Loaded(moveLocally(prev, task = task, to = to))
        if (isPreview) return
        val gid = goalId
        val tid = task.taskId
        val ok = Mutation.run(invalidate = WorkAPI.changed) {
            WorkAPI.move(goalId = gid, taskId = tid, to = to)
        }
        val failed = Mutation.lastErrorStatus != null
        track("work_task.move_result", GD.result() + mapOf(
            "surface" to surface, "task_id" to tid, "to_status" to to.name, "move_method" to method,
        ))
        if (ok == null && failed) {
            phase = LoadPhase.Loaded(prev)
        }
    }

    /** 루틴 Task 는 보드 칸이 없어서 캘린더와 같은 완료 · 완료 취소로 */
    suspend fun toggleRoutine(task: WorkTask, surface: String = "board", method: String = "check") {
        if (isPreview) return
        val msg = if (task.isDone) "완료를 취소했어요." else "완료했어요."
        val current = if (task.isDone) TaskStatus.COMPLETED else TaskStatus.TODO
        val tid = task.taskId
        Mutation.run(invalidate = WorkAPI.changed, successMessage = msg) {
            GoalAPI.toggleTask(taskId = tid, currentStatus = current)
        }
        track("work_task.move_result", GD.result() + mapOf(
            "surface" to surface, "task_id" to tid,
            "to_status" to (if (task.isDone) WorkColumn.NOT_STARTED else WorkColumn.DONE).name, "move_method" to method,
        ))
    }

    /** 보드 카드 … 메뉴 '지우기' → 알림창 '지우기' */
    suspend fun delete(task: WorkTask) {
        if (isPreview) return
        val tid = task.taskId
        Mutation.run(invalidate = WorkAPI.changed, successMessage = "Task를 지웠어요.") {
            WorkAPI.deleteTask(taskId = tid)
        }
        track("work_board.card_delete_result", GD.result() + mapOf("task_id" to tid))
    }

    // MARK: 주제

    /** 로드맵 점 — "끝냈어요" ↔ "다시 열기". Task 를 모두 끝내서 끝난 주제는 되돌릴 게 없어요 */
    suspend fun toggleTopic(topic: WorkTopic) {
        val id = topic.topicId ?: return
        if (togglingTopicId != null) return
        if (topic.isDone && !topic.completed) {
            // Task 를 모두 끝내 자동으로 끝난 주제 — 눌러도 반응이 없어요
            track("work_roadmap.topic_locked_click", mapOf("topic_id" to id))
            return
        }
        if (isPreview) return
        togglingTopicId = id
        try {
            val completed = !topic.completed
            val gid = goalId
            track("work_roadmap.topic_complete_toggle", mapOf(
                "topic_id" to id, "checked" to completed, "topic_task_count" to topic.remaining, "d_day" to topic.dDay,
                "entry_point" to "node",
            ))
            Mutation.run(
                invalidate = WorkAPI.changed,
                successMessage = if (completed) "세부 목표를 끝냈어요. 수고했어요!" else "세부 목표를 다시 열었어요.",
            ) {
                WorkAPI.updateTopic(goalId = gid, topicId = id, patch = WorkTopicPatch(completed = completed))
            }
            track("work_roadmap.topic_result", GD.result() + mapOf(
                "topic_id" to id, "topic_action" to if (completed) "complete" else "reopen",
            ))
        } finally {
            togglingTopicId = null
        }
    }

    companion object {
        // MARK: 서버 응답 전에 보드 칸만 먼저 옮겨 보여주기 (웹 moveLocally)

        fun moveLocally(b: WorkBoard, task: WorkTask, to: WorkColumn): WorkBoard {
            val moved = task.copy(status = to)
            val id = task.taskId
            val notStarted = b.board.notStarted.filter { it.taskId != id }
            val inProgress = b.board.inProgress.filter { it.taskId != id }
            val done = b.board.done.filter { it.taskId != id }
            val doneTotal = b.board.doneTotal + (if (to == WorkColumn.DONE) 1 else 0) - (if (task.status == WorkColumn.DONE) 1 else 0)
            val cols = when (to) {
                WorkColumn.DONE -> WorkBoardColumns(notStarted, inProgress, listOf(moved) + done, doneTotal)
                WorkColumn.IN_PROGRESS -> WorkBoardColumns(notStarted, inProgress + moved, done, doneTotal)
                WorkColumn.NOT_STARTED -> WorkBoardColumns(notStarted + moved, inProgress, done, doneTotal)
            }
            val swap: (List<WorkTask>) -> List<WorkTask> = { list -> list.map { if (it.taskId == id) moved else it } }
            return b.copy(
                board = cols,
                todayTasks = swap(b.todayTasks),
                upcoming = swap(b.upcoming),
                backlog = swap(b.backlog),
            )
        }

        /** 미리보기용 (샘플 데이터) */
        fun preview(board: WorkBoard = WorkSampleData.board): WorkBoardModel {
            val m = WorkBoardModel(goalId = board.goalCategoryId)
            m.isPreview = true
            m.phase = LoadPhase.Loaded(board)
            return m
        }
    }
}

// MARK: - 미리보기 데이터

object WorkSampleData {
    val today: String get() = KST.today()

    fun task(
        id: Int,
        name: String,
        topic: Int?,
        topicName: String?,
        status: WorkColumn,
        date: String? = null,
        startTime: String? = null,
        dueIn: Int? = null,
        memo: String? = null,
        routine: Boolean = false,
    ): WorkTask {
        val t = today
        val due: String? = dueIn?.let { KST.addDays(t, it) }
        return WorkTask(
            taskId = id, taskName = name, emoji = null, topicId = topic, topicName = topicName,
            status = status, date = date, startTime = startTime, endTime = null,
            dueDate = due, dueInDays = dueIn,
            overdue = (dueIn ?: 0) < 0 && status != WorkColumn.DONE,
            memo = memo, routine = routine,
            completedAt = if (status == WorkColumn.DONE) "${t}T10:00:00" else null,
        )
    }

    fun topic(
        id: Int?,
        name: String,
        order: Int,
        dueIn: Int?,
        status: WorkTopicStatus,
        completed: Boolean = false,
        total: Int,
        done: Int,
        perDay: Double? = null,
    ): WorkTopic {
        val t = today
        val percent: Int? = if (total > 0) Math.round(done.toDouble() / total.toDouble() * 100).toInt() else null
        return WorkTopic(
            topicId = id, name = name, emoji = null, sortOrder = order,
            dueDate = dueIn?.let { KST.addDays(t, it) }, dDay = dueIn,
            status = status, completed = completed,
            total = total, done = done, remaining = total - done,
            progressPercent = percent, remainingPerDay = perDay,
        )
    }

    val timeline: List<WorkTopic>
        get() = listOf(
            topic(1, "기획서 정리", order = 0, dueIn = -10, status = WorkTopicStatus.DONE, completed = true, total = 3, done = 3),
            topic(2, "디자인 시안", order = 1, dueIn = -2, status = WorkTopicStatus.OVERDUE, total = 0, done = 0),
            topic(3, "API 연동", order = 2, dueIn = 3, status = WorkTopicStatus.NEXT, total = 5, done = 2, perDay = 1.0),
            topic(4, "출시 준비", order = 3, dueIn = 12, status = WorkTopicStatus.UPCOMING, total = 2, done = 0, perDay = 0.17),
            topic(5, "아이디어 메모", order = 4, dueIn = null, status = WorkTopicStatus.NO_DUE, total = 1, done = 0),
        )

    val columns: WorkBoardColumns
        get() {
            val t = today
            return WorkBoardColumns(
                notStarted = listOf(
                    task(31, "결제 화면 연동", topic = 3, "API 연동", WorkColumn.NOT_STARTED, date = t, dueIn = 3),
                    task(32, "푸시 알림 테스트", topic = 3, "API 연동", WorkColumn.NOT_STARTED),
                    task(41, "스토어 스크린샷", topic = 4, "출시 준비", WorkColumn.NOT_STARTED, date = KST.addDays(t, 5), dueIn = 10),
                    task(42, "심사 메모 작성", topic = 4, "출시 준비", WorkColumn.NOT_STARTED),
                    task(51, "온보딩 개선 아이디어", topic = 5, "아이디어 메모", WorkColumn.NOT_STARTED, memo = "첫 화면에서 목표 예시를 3개 보여주기"),
                    task(91, "회의록 정리", topic = null, null, WorkColumn.NOT_STARTED, date = t),
                ),
                inProgress = listOf(
                    task(33, "로그인 API 붙이기", topic = 3, "API 연동", WorkColumn.IN_PROGRESS, date = t, startTime = "14:00", dueIn = 1,
                        memo = "토큰 갱신까지"),
                ),
                done = listOf(
                    task(35, "네트워크 계층", topic = 3, "API 연동", WorkColumn.DONE, date = KST.addDays(t, -1)),
                    task(34, "API 명세 확인", topic = 3, "API 연동", WorkColumn.DONE, date = KST.addDays(t, -3)),
                    task(13, "일정표", topic = 1, "기획서 정리", WorkColumn.DONE, date = KST.addDays(t, -11)),
                    task(12, "화면 흐름도", topic = 1, "기획서 정리", WorkColumn.DONE, date = KST.addDays(t, -12)),
                    task(11, "요구사항 정리", topic = 1, "기획서 정리", WorkColumn.DONE, date = KST.addDays(t, -13)),
                ),
                doneTotal = 5,
            )
        }

    val board: WorkBoard
        get() {
            val t = today
            val line = timeline
            val cols = columns
            val noTopic = topic(null, "세부 목표 없음", order = 99, dueIn = null, status = WorkTopicStatus.NO_DUE, total = 1, done = 0)
            val summary = WorkSummary(
                startDate = KST.addDays(t, -30), total = 12, notStarted = 6, inProgress = 1, done = 5,
                remaining = 7, overdue = 0, dueThisWeek = 2, backlog = 3, progressPercent = 42,
                topicsDone = 1, topicsTotal = 5, nextTopic = line[2],
            )
            val todayTasks = (cols.inProgress + cols.notStarted).filter { it.date == t }
            return WorkBoard(
                goalCategoryId = 2, goalName = "나를 업무", emoji = "💼", description = null, today = t,
                summary = summary, timeline = line, todayTasks = todayTasks, doneToday = emptyList(), upcoming = emptyList(),
                topics = line + noTopic, board = cols,
                backlog = cols.notStarted.filter { it.date == null },
                archive = emptyList(),
                retrospectives = listOf(
                    WorkRetro(
                        retrospectiveId = 1, reviewDate = KST.addDays(t, -1), reviewType = "DAILY",
                        content = "로그인 흐름에서 토큰 갱신이 막혔다. 내일은 문서부터 다시 보기.",
                    ),
                    WorkRetro(
                        retrospectiveId = 2, reviewDate = KST.addDays(t, -4), reviewType = "DAILY",
                        content = "기획서를 일찍 끝내서 여유가 생겼다.",
                    ),
                ),
            )
        }

    /** 마감 지난 주제가 다음 마감일 때 */
    val overdueBoard: WorkBoard
        get() {
            val b = board
            return b.copy(summary = b.summary.copy(nextTopic = timeline[1]))
        }

    /** 아직 주제 · Task 가 없을 때 */
    val emptyBoard: WorkBoard
        get() {
            val t = today
            return WorkBoard(
                goalCategoryId = 2, goalName = "나를 업무", emoji = null, description = null, today = t,
                summary = WorkSummary(
                    startDate = null, total = 0, notStarted = 0, inProgress = 0, done = 0, remaining = 0,
                    overdue = 0, dueThisWeek = 0, backlog = 0, progressPercent = null,
                    topicsDone = 0, topicsTotal = 0, nextTopic = null,
                ),
                timeline = emptyList(), todayTasks = emptyList(), doneToday = emptyList(), upcoming = emptyList(), topics = emptyList(),
                board = WorkBoardColumns(notStarted = emptyList(), inProgress = emptyList(), done = emptyList(), doneTotal = 0),
                backlog = emptyList(), archive = emptyList(), retrospectives = emptyList(),
            )
        }

    val shareText: String = """
        [나를 업무] 남은 일 (10/13)

        ■ API 연동 (~10/16 · D-3)
        - [진행 중] 로그인 API 붙이기 (D-1)
        - 결제 화면 연동
        - 푸시 알림 테스트

        ■ 출시 준비 (~10/25)
        - 스토어 스크린샷
        - 심사 메모 작성
    """.trimIndent()
}
