// feature/work/WorkModels.kt
//
// iOS Features/Work/WorkModels.swift
// 웹 features/work/api.ts · format.ts — 업무형 목표 보드 (백엔드 V52 · V53 · /api/v1/goal-categories/{id}/work-*)
//
// 업무형은 목표 전체 마감이 없고 주제마다 마감이 있어요 (V53) → 주제를 마감 순서대로 잇는 세로 로드맵.
// Task 는 주제로 묶고, 시작 전 · 진행 중 · 완료 보드로 옮겨요. 날짜를 안 정한 Task 는 "할 일 목록"에 쌓여요.
//
// 보드를 바꾸면 [work, goals, timetable, record, home] 을 다시 불러와요 (WorkAPI.changed).
//
// iOS 는 WorkTask 에 직접 만든 init(from decoder:) 가 있어서 "빠진 값은 기본값"으로 읽었어요.
// Kotlin 은 같은 효과를 필드 기본값(+ NJson 의 coerceInputValues)으로 내요.
// (필드를 var 로 둔 건 iOS 처럼 x.status = … 로 바꿔 쓰는 곳이 있어도 컴파일되게. 상태 안 값은 copy() 로 바꿔요)

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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.math.floor

/** 보드 칸 — 시작 전 · 진행 중 · 완료 (모르는 값이 오면 NOT_STARTED) */
@Serializable(with = WorkColumn.Serializer::class)
enum class WorkColumn {
    NOT_STARTED, IN_PROGRESS, DONE;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            NOT_STARTED -> "시작 전"
            IN_PROGRESS -> "진행 중"
            DONE -> "완료"
        }

    val icon: String
        get() = when (this) {
            NOT_STARTED -> "circle.dashed"
            IN_PROGRESS -> "circle.lefthalf.filled"
            DONE -> "checkmark.circle.fill"
        }

    object Serializer : FallbackEnumSerializer<WorkColumn>("WorkColumn", WorkColumn.entries.toTypedArray(), WorkColumn.NOT_STARTED)

    companion object {
        val fallback: WorkColumn get() = NOT_STARTED
        fun fromRaw(raw: String?): WorkColumn? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class WorkTask(
    var taskId: Int,
    var taskName: String = "",
    var emoji: String? = null,
    var topicId: Int? = null,
    var topicName: String? = null,
    var status: WorkColumn = WorkColumn.NOT_STARTED,
    /** 하기로 한 날 (null = 할 일 목록) */
    var date: String? = null,
    /** "HH:mm" — 시간이 정해졌을 때만 */
    var startTime: String? = null,
    var endTime: String? = null,
    var dueDate: String? = null,
    /** 마감까지 남은 날 (오늘 0, 지났으면 음수) */
    var dueInDays: Int? = null,
    var overdue: Boolean = false,
    var memo: String? = null,
    /** 반복 루틴의 그날 Task — 보드 칸에는 안 나와요 */
    var routine: Boolean = false,
    var completedAt: String? = null,
) {
    val id: Int get() = taskId

    val isDone: Boolean get() = status == WorkColumn.DONE

    companion object {}
}

/** DONE 끝남 · OVERDUE 마감 지남 · NEXT 다음 마감 · UPCOMING 그 뒤 · NO_DUE 마감 없음 */
@Serializable(with = WorkTopicStatus.Serializer::class)
enum class WorkTopicStatus {
    DONE, OVERDUE, NEXT, UPCOMING, NO_DUE;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<WorkTopicStatus>("WorkTopicStatus", WorkTopicStatus.entries.toTypedArray(), WorkTopicStatus.NO_DUE)

    companion object {
        val fallback: WorkTopicStatus get() = NO_DUE
        fun fromRaw(raw: String?): WorkTopicStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 주제 — 업무형은 목표 전체 마감 대신 주제마다 마감이 있어요 (V53) */
@Serializable
data class WorkTopic(
    /** null = "주제 없음" (보드 필터에만) */
    var topicId: Int? = null,
    var name: String,
    var emoji: String? = null,
    var sortOrder: Int,
    var dueDate: String? = null,
    /** 마감까지 남은 날 (오늘 0, 지났으면 음수) */
    var dDay: Int? = null,
    var status: WorkTopicStatus,
    /** 사용자가 "끝냈어요"로 표시했는지 */
    var completed: Boolean,
    var total: Int,
    var done: Int,
    var remaining: Int,
    var progressPercent: Int? = null,
    /** 마감까지 하루에 끝내야 하는 Task 수 */
    var remainingPerDay: Double? = null,
) {
    val id: String get() = topicId?.toString() ?: "none"

    val isDone: Boolean get() = status == WorkTopicStatus.DONE
    val title: String get() = (emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + name

    companion object {}
}

@Serializable
data class WorkSummary(
    var startDate: String? = null,
    var total: Int,
    var notStarted: Int,
    var inProgress: Int,
    var done: Int,
    var remaining: Int,
    var overdue: Int,
    var dueThisWeek: Int,
    var backlog: Int,
    var progressPercent: Int? = null,
    /** 끝난 주제 수 / 전체 주제 수 */
    var topicsDone: Int,
    var topicsTotal: Int,
    /** 아직 안 끝난 주제 중 마감이 가장 가까운 것 (지난 것 포함). 없으면 null */
    var nextTopic: WorkTopic? = null,
) {
    companion object {}
}

@Serializable
data class WorkRetro(
    var retrospectiveId: Int,
    var reviewDate: String,
    var reviewType: String? = null,
    var content: String,
) {
    val id: Int get() = retrospectiveId

    companion object {}
}

@Serializable
data class WorkBoardColumns(
    var notStarted: List<WorkTask>,
    var inProgress: List<WorkTask>,
    var done: List<WorkTask>,
    var doneTotal: Int,
) {
    fun tasks(c: WorkColumn): List<WorkTask> = when (c) {
        WorkColumn.NOT_STARTED -> notStarted
        WorkColumn.IN_PROGRESS -> inProgress
        WorkColumn.DONE -> done
    }

    companion object {}
}

@Serializable
data class WorkArchiveDay(
    var date: String,
    var count: Int,
    var tasks: List<WorkTask>,
) {
    val id: String get() = date

    companion object {}
}

/** GET /goal-categories/{id}/work-board */
@Serializable
data class WorkBoard(
    var goalCategoryId: Int,
    var goalName: String,
    var emoji: String? = null,
    var description: String? = null,
    var today: String,
    var summary: WorkSummary,
    /** 주제를 마감 순서대로 (마감 없는 주제는 맨 뒤) — 세로 로드맵 */
    var timeline: List<WorkTopic>,
    var todayTasks: List<WorkTask>,
    var doneToday: List<WorkTask>,
    var upcoming: List<WorkTask>,
    /** 보드 필터용 주제 (맨 끝에 "주제 없음") */
    var topics: List<WorkTopic>,
    var board: WorkBoardColumns,
    var backlog: List<WorkTask>,
    var archive: List<WorkArchiveDay>,
    var retrospectives: List<WorkRetro>,
) {
    /** 주제가 있는 것만 (주제 고르기용) */
    val realTopics: List<WorkTopic> get() = topics.filter { it.topicId != null }

    /** 주제별 남은 할 일 (진행 중 먼저) · 끝낸 일 — 로드맵 점 사이에 보여줘요 */
    fun openTasks(topicId: Int?): List<WorkTask> =
        (board.inProgress + board.notStarted).filter { it.topicId == topicId }

    fun doneTasks(topicId: Int?): List<WorkTask> =
        board.done.filter { it.topicId == topicId }

    companion object {}
}

/** 공유 범위 — 오늘 · 남은 일 · 전체 */
enum class WorkShareScope {
    TODAY, REMAINING, ALL;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            TODAY -> "오늘"
            REMAINING -> "남은 일"
            ALL -> "전체"
        }

    companion object {
        fun fromRaw(raw: String?): WorkShareScope? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Task 수정 — 보낼 것만 (null 은 안 보내요). clear* 는 지우기 */
data class WorkTaskPatch(
    var taskName: String? = null,
    var topicId: Int? = null,
    var clearTopic: Boolean? = null,
    var date: String? = null,
    var clearDate: Boolean? = null,
    var dueDate: String? = null,
    var clearDueDate: Boolean? = null,
    var memo: String? = null,
    var status: WorkColumn? = null,
) {
    /** null 인 키는 빼고 보내요 (iOS JSONValue.compact) */
    val json: JsonElement
        get() = jsonCompact(
            "taskName" to taskName,
            "topicId" to topicId,
            "clearTopic" to clearTopic,
            "date" to date,
            "clearDate" to clearDate,
            "dueDate" to dueDate,
            "clearDueDate" to clearDueDate,
            "memo" to memo,
            "status" to status?.rawValue,
        )

    companion object {}
}

/** 주제 수정 — 보낼 것만 */
data class WorkTopicPatch(
    var name: String? = null,
    var emoji: String? = null,
    var dueDate: String? = null,
    var clearDueDate: Boolean? = null,
    /** true 끝냈어요 · false 다시 열기 */
    var completed: Boolean? = null,
) {
    val json: JsonElement
        get() = jsonCompact(
            "name" to name,
            "emoji" to emoji,
            "dueDate" to dueDate,
            "clearDueDate" to clearDueDate,
            "completed" to completed,
        )

    companion object {}
}

/** 공유 텍스트 응답 { text } */
@Serializable
private data class WorkShareTextResponse(val text: String = "")

object WorkAPI {
    /** 보드를 바꾸면 같이 다시 불러올 것들 */
    val changed: List<DataKey> = listOf(DataKey.work, DataKey.goals, DataKey.timetable, DataKey.record, DataKey.home)

    suspend fun board(goalId: Int, archiveDays: Int = 30): WorkBoard? =
        API.get<WorkBoard>("/v1/goal-categories/$goalId/work-board?archiveDays=$archiveDays")

    /** 공유 텍스트 (카톡 · 슬랙에 붙여 넣기) */
    suspend fun shareText(goalId: Int, scope: WorkShareScope): String {
        val r = API.get<WorkShareTextResponse>("/v1/goal-categories/$goalId/work-board/share-text?scope=${scope.rawValue}")
        return r?.text ?: ""
    }

    // MARK: Task

    /** 한 개 추가 (null 은 JSON null 로 보내요 — iOS .of(nil)) */
    suspend fun createTask(goalId: Int, taskName: String, topicId: Int?, date: String?, dueDate: String?): WorkTask? =
        API.post<WorkTask>(
            "/v1/goal-categories/$goalId/work-tasks",
            json("taskName" to taskName, "topicId" to topicId, "date" to date, "dueDate" to dueDate),
        )

    /** 여러 줄을 한 번에 (한 줄 = Task 하나) */
    suspend fun createTasks(goalId: Int, text: String, topicId: Int?, date: String?, dueDate: String?): List<WorkTask> {
        val r = API.post<List<WorkTask>>(
            "/v1/goal-categories/$goalId/work-tasks/bulk",
            json("text" to text, "topicId" to topicId, "date" to date, "dueDate" to dueDate),
        )
        return r ?: emptyList()
    }

    suspend fun updateTask(goalId: Int, taskId: Int, patch: WorkTaskPatch): WorkTask? =
        API.patch<WorkTask>("/v1/goal-categories/$goalId/work-tasks/$taskId", patch.json)

    /** 보드 칸 옮기기 (시작 전 · 진행 중 · 완료) */
    suspend fun move(goalId: Int, taskId: Int, to: WorkColumn) {
        API.send("PATCH", "/v1/goal-categories/$goalId/work-tasks/$taskId/status", json("status" to to.rawValue))
    }

    suspend fun deleteTask(taskId: Int) {
        API.send("DELETE", "/v1/tasks/$taskId")
    }

    // MARK: 주제

    suspend fun createTopic(goalId: Int, name: String, dueDate: String?) {
        API.send("POST", "/v1/goal-categories/$goalId/work-topics", json("name" to name, "dueDate" to dueDate))
    }

    suspend fun updateTopic(goalId: Int, topicId: Int, patch: WorkTopicPatch) {
        API.send("PATCH", "/v1/goal-categories/$goalId/work-topics/$topicId", patch.json)
    }

    /** 들어 있던 Task 는 "주제 없음"으로 옮겨져요 */
    suspend fun deleteTopic(goalId: Int, topicId: Int) {
        API.send("DELETE", "/v1/goal-categories/$goalId/work-topics/$topicId")
    }

    // MARK: 회고 (기존 /v1/retrospectives)

    suspend fun createRetro(goalId: Int, content: String, date: String? = null) {
        API.send(
            "POST", "/v1/retrospectives",
            jsonCompact(
                "reviewType" to "DAILY",
                "reviewDate" to date,
                "content" to content,
                "goalCategoryId" to goalId,
            ),
        )
    }

    suspend fun updateRetro(retrospectiveId: Int, content: String) {
        API.send("PATCH", "/v1/retrospectives/$retrospectiveId", json("content" to content))
    }

    suspend fun deleteRetro(retrospectiveId: Int) {
        API.send("DELETE", "/v1/retrospectives/$retrospectiveId")
    }
}

/** 웹 work/format.ts */
object WorkFormat {
    /** Swift .rounded() 와 같은 반올림 (0.5 는 0 에서 먼 쪽으로). Kotlin round() 는 짝수 쪽이라 직접 */
    private fun roundHalfAway(v: Double): Double = if (v >= 0) floor(v + 0.5) else -floor(-v + 0.5)

    /** "오늘" · "내일" · "어제" · "10.14 (화)" (해가 다르면 "2027.01.03 (일)") */
    fun dayLabel(ymd: String, today: String): String {
        if (ymd == today) return "오늘"
        if (ymd == KST.addDays(today, 1)) return "내일"
        if (ymd == KST.addDays(today, -1)) return "어제"
        val p = KST.parts(ymd)
        val md = "%02d.%02d".format(p.m, p.d)
        val date = if (p.y == KST.yearOf(today)) md else "${p.y}.$md"
        return "$date (${KST.weekdayLabel[KST.weekdayIndex(ymd)]})"
    }

    /** Task 마감 — "D-3" · "오늘 마감" · "2일 지남" (완료면 null) */
    fun dueLabel(t: WorkTask): String? {
        val d = t.dueInDays ?: return null
        if (t.status == WorkColumn.DONE) return null
        if (d == 0) return "오늘 마감"
        return if (d > 0) "D-$d" else "${-d}일 지남"
    }

    /** 주제 마감 — "D-3" · "오늘 마감" · "2일 지남" */
    fun topicDue(dDay: Int?): String? {
        val d = dDay ?: return null
        if (d == 0) return "오늘 마감"
        return if (d > 0) "D-$d" else "${-d}일 지남"
    }

    /** "D-12" · "D-DAY" · "D+3" */
    fun dDay(d: Int?): String? {
        if (d == null) return null
        if (d == 0) return "D-DAY"
        return if (d > 0) "D-$d" else "D+${-d}"
    }

    /** "10/16(금)" */
    fun shortDate(ymd: String): String =
        "${KST.monthOf(ymd)}/${KST.dayOfMonth(ymd)}(${KST.weekdayLabel[KST.weekdayIndex(ymd)]})"

    /** "2026.11.14 (금)" */
    fun fullDate(ymd: String): String =
        "${ymd.replace("-", ".")} (${KST.weekdayLabel[KST.weekdayIndex(ymd)]})"

    /** 마감까지 속도 — 하루 1개 이상이면 "하루 2개씩", 적으면 "3일에 1개씩" */
    fun pace(perDay: Double): String {
        // 0 이하 · 무한대면 Int 변환에서 이상해지지 않게
        if (!perDay.isFinite() || perDay <= 0) return ""
        if (perDay >= 1) {
            val v = roundHalfAway(perDay * 10) / 10
            val s = if (v == roundHalfAway(v)) v.toInt().toString() else v.toString()
            return "하루 ${s}개씩"
        }
        val days = minOf(roundHalfAway(1 / perDay), 9999.0)
        return "${maxOf(days.toInt(), 2)}일에 1개씩"
    }
}
