// feature/home/HomeModels.kt
//
// iOS Features/Home/HomeModels.swift
// 웹 features/home/api.ts · today.ts — 홈 "오늘" (백엔드 GET /api/v1/home/today)
//  - 다가오는 마감: Task 마감(지난 7일 ~ 앞으로 14일) · 업무형 주제 마감 · 목표형 종료일(앞으로 1년)
//  - 진행 중인 목표별 오늘: 끝낸 일 · 못 한 일 · 오늘 쓴 회고
// 오늘 일정 · 루틴 Task 는 /timetable, 생활 습관은 /habits 를 그대로 써요.

package com.naleul.naleul.feature.home

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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = HomeDeadlineType.Serializer::class)
enum class HomeDeadlineType {
    TASK, TOPIC, GOAL;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            TASK -> "Task 마감"
            TOPIC -> "세부 목표 마감"
            GOAL -> "목표 종료"
        }

    object Serializer : FallbackEnumSerializer<HomeDeadlineType>("HomeDeadlineType", HomeDeadlineType.entries.toTypedArray(), HomeDeadlineType.TASK)

    companion object {
        val fallback: HomeDeadlineType get() = TASK
        fun fromRaw(raw: String?): HomeDeadlineType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class HomeDeadline(
    val type: HomeDeadlineType = HomeDeadlineType.TASK,
    /** taskId · workTopicId · goalCategoryId (서버 JSON 이름은 "id") */
    @SerialName("id") val itemId: Int,
    val title: String = "",
    val emoji: String? = null,
    val date: String,
    /** 오늘 0, 지났으면 음수 */
    val dDay: Int = 0,
    val overdue: Boolean = false,
    val goalCategoryId: Int? = null,
    val goalName: String? = null,
    val goalEmoji: String? = null,
    val colorCode: String? = null,
    val goalMode: GoalMode? = null,
    /** TOPIC: 그 주제의 남은 Task 수 */
    val remaining: Int? = null,
) {
    val id: String get() = "${type.rawValue}-$itemId"

    /** "오늘" · "내일" · "D-3" · "2일 지남" */
    val dDayText: String
        get() {
            if (dDay == 0) return "오늘"
            if (dDay == 1) return "내일"
            return if (dDay < 0) "${-dDay}일 지남" else "D-$dDay"
        }

    companion object {}
}

@Serializable
data class HomeRetro(
    val retrospectiveId: Int,
    val reviewType: String? = null,
    val content: String = "",
) {
    val id: Int get() = retrospectiveId

    companion object {}
}

/**
 * 목표형(ACHIEVEMENT) 그날 기록 — 목표 상세 "오늘 기록"과 같은 데이터 (PUT /goal-categories/{id}/check-in)
 *  - hasMetric: 수치 목표인지 (아니면 기분 · 한 줄 회고만)
 *  - value: 그날 기록한 수치
 */
@Serializable
data class HomeCheckIn(
    val hasMetric: Boolean = false,
    val metricName: String? = null,
    val unit: String? = null,
    val currentValue: Double? = null,
    val value: Double? = null,
    val mood: ReflectionMood? = null,
    val note: String? = null,
) {
    val written: Boolean get() = value != null || mood != null || !(note ?: "").isEmpty()

    companion object {}
}

@Serializable
data class HomeGoalToday(
    val goalCategoryId: Int,
    val goalName: String = "",
    val emoji: String? = null,
    val colorCode: String? = null,
    val goalMode: GoalMode? = null,
    /** 오늘 할 일 · 끝낸 일 · 회고가 하나라도 있으면 true */
    val activeToday: Boolean = false,
    val planned: Int = 0,
    val done: Int = 0,
    val doneTasks: List<String> = emptyList(),
    val openTasks: List<String> = emptyList(),
    val retros: List<HomeRetro> = emptyList(),
    /** 목표형만 (업무형은 null — 회고 글로 남겨요). 아직 배포 전 서버면 없어요 */
    val checkIn: HomeCheckIn? = null,
) {
    val id: Int get() = goalCategoryId

    /** 그날 남긴 게 있는지 — 목표형은 "오늘 기록"(수치 · 기분 · 회고), 업무형은 회고 글 */
    val hasRecord: Boolean get() = retros.isNotEmpty() || (checkIn?.written ?: false)

    val title: String get() = (emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + goalName

    /** 목표 형태별 회고 질문 — 빈 칸 앞에서 막막하지 않게 */
    val prompt: String
        get() {
            if (goalMode == GoalMode.RECORD) return "오늘 일하며 막힌 점 · 배운 점 · 내일 바꿀 점"
            if (planned > 0 && done == 0) return "오늘 못 한 이유는 뭐였나요? 내일은 어떻게 해 볼까요?"
            return "오늘 목표에 얼마나 다가갔나요? 잘 된 점 · 아쉬운 점"
        }

    companion object {}
}

@Serializable
data class HomeToday(
    val today: String,
    val deadlines: List<HomeDeadline> = emptyList(),
    val goals: List<HomeGoalToday> = emptyList(),
) {
    companion object {}
}

object HomeAPI {
    /** date 를 주면 그날 (홈에서 날짜 ‹ › 로 지난날 · 앞날 보기). 안 주면 오늘 */
    suspend fun today(date: String? = null): HomeToday? =
        API.get<HomeToday>("/v1/home/today" + (date?.let { "?date=$it" } ?: ""))

    /** 오늘 회고 남기기 (POST /retrospectives · DAILY) — 업무형 */
    suspend fun createRetro(goalId: Int, content: String, date: String) {
        WorkAPI.createRetro(goalId = goalId, content = content, date = date)
    }

    /** 목표형 그날 기록 (수치 + 기분 + 한 줄 회고) — 목표 상세 "오늘 기록"과 같은 API */
    suspend fun checkIn(goalId: Int, date: String, value: Double?, mood: ReflectionMood?, note: String?) {
        GoalEditAPI.checkIn(goalId = goalId, date = date, value = value, mood = mood, note = note)
    }

    /** 회고 글 고치기 (PATCH /retrospectives/{id}) — 목표는 그대로 (null 이면 서버가 기존 값 유지) */
    suspend fun updateRetro(retrospectiveId: Int, content: String, goalId: Int?) {
        API.send("PATCH", "/v1/retrospectives/$retrospectiveId", jsonCompact("content" to content, "goalCategoryId" to goalId))
    }

    /** 회고 글 지우기 (DELETE /retrospectives/{id}) */
    suspend fun deleteRetro(retrospectiveId: Int) {
        API.send("DELETE", "/v1/retrospectives/$retrospectiveId")
    }

    /** 회고 모아보기 (GET /home/reflections) — before 의 전날까지 days 일치 */
    suspend fun reflections(before: String? = null, days: Int = 30, goalId: Int? = null): ReflectionFeedPage? {
        val q = mutableListOf("days=$days")
        if (before != null) q.add("before=$before")
        if (goalId != null) q.add("goalCategoryId=$goalId")
        return API.get<ReflectionFeedPage>("/v1/home/reflections?" + q.joinToString("&"))
    }
}

// MARK: - 회고 모아보기

@Serializable(with = ReflectionKind.Serializer::class)
enum class ReflectionKind {
    /** 목표형 그날 기록 (수치 · 기분 · 한 줄 회고) */
    CHECK_IN,
    /** 회고 글 (업무형 등) */
    RETRO;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<ReflectionKind>("ReflectionKind", ReflectionKind.entries.toTypedArray(), ReflectionKind.RETRO)

    companion object {
        val fallback: ReflectionKind get() = RETRO
        fun fromRaw(raw: String?): ReflectionKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class ReflectionEntry(
    val kind: ReflectionKind = ReflectionKind.RETRO,
    val date: String,
    val goalCategoryId: Int,
    val goalName: String = "",
    val goalEmoji: String? = null,
    val colorCode: String? = null,
    val goalMode: GoalMode? = null,
    /** RETRO 만 */
    val retrospectiveId: Int? = null,
    val reviewType: String? = null,
    /** CHECK_IN 만 */
    val mood: ReflectionMood? = null,
    /** CHECK_IN: 한 줄 회고 · RETRO: 회고 내용 */
    val note: String? = null,
    val value: Double? = null,
    val metricName: String? = null,
    val unit: String? = null,
) {
    val id: String get() = "${kind.rawValue}-$goalCategoryId-${retrospectiveId?.toString() ?: date}"

    val goalTitle: String get() = (goalEmoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + goalName

    companion object {}
}

@Serializable
data class ReflectionFeedPage(
    val from: String,
    val to: String,
    val entries: List<ReflectionEntry> = emptyList(),
    val hasMore: Boolean = false,
) {
    companion object {}
}

/** 웹 home/today.ts — TimeTable 하루에서 "그날 하기로 한" Task 고르기 */
object HomeDay {
    /** 계획 시작일 또는 시간 미정 날짜가 그날이거나, 그날 끝낸 것 (전날 밤부터 이어진 블록은 빼요) */
    fun tasksOfDay(day: TimetableDay?): List<TimeBlockTask> {
        if (day == null) return emptyList()
        val seen = mutableSetOf<Int>()
        return (day.tasks + day.unscheduledTasks).filter { t ->
            if (seen.contains(t.taskId) || t.taskStatus == TaskStatus.SKIPPED || t.taskStatus == TaskStatus.DELETED) return@filter false
            seen.add(t.taskId)
            val planDay = t.plannedStartAt?.take(10) ?: t.date ?: t.scheduledDate
            if (planDay == null) return@filter true
            planDay == day.date || t.completedAt?.take(10) == day.date
        }
    }

    /** 그날 0시부터 시작 분 (시간 미정은 맨 뒤) */
    fun startMinutes(day: String, t: TimeBlockTask): Int =
        t.plannedStartAt?.let { KST.minutesFrom(day, it) } ?: Int.MAX_VALUE

    fun endMinutes(day: String, t: TimeBlockTask): Int {
        val s = t.plannedStartAt ?: return Int.MAX_VALUE
        val e = t.plannedEndAt
        if (e != null) return KST.minutesFrom(day, e)
        return KST.minutesFrom(day, s) + (t.plannedDurationMinutes ?: 30)
    }

    fun isRoutine(t: TimeBlockTask): Boolean = t.sourceType == TaskSourceType.ROUTINE

    /** "40분 뒤" · "1시간 20분 뒤" */
    fun inLabel(minutes: Int): String {
        if (minutes <= 0) return "지금"
        val h = minutes / 60
        val m = minutes % 60
        if (h > 0 && m > 0) return "${h}시간 ${m}분 뒤"
        if (h > 0) return "${h}시간 뒤"
        return "${m}분 뒤"
    }
}
