// feature/habit/HabitModels.kt
//
// iOS Features/Habit/HabitModels.swift
// 웹 features/habit/api.ts — 기본 생활 습관 (백엔드 V52 · /api/v1/habits)
//
// 사용자마다 "기본 생활 습관" 목표(goalMode = HABIT)가 하나씩 자동으로 생기고, 그 안에
// 영양제 먹기 · 샤워하기 같은 "했는지만 체크하는" 습관을 담아요. 마감 · 수치가 없고, 쉬었다 다시 시작할 수 있어요.
// 병원 가기처럼 한 번만 할 일은 같은 목표에 일회성 Task 로 넣어요 (GoalAPI.createGoalTask).
//
// 바꾸면 [habits] (+ 홈 · 캘린더) 를 다시 불러와요.
//
// iOS Habit 은 직접 만든 init(from decoder:) 로 "빠진 값은 기본값"으로 읽었어요.
// Kotlin 은 같은 효과를 필드 기본값(+ NJson 의 coerceInputValues)으로 내요.
// (daily 의 기본값은 iOS 처럼 "repeatDays 가 비어 있으면 매일")
// 필드는 var 라서 iOS 처럼 x.checked = … 도 되지만, 화면 상태 안의 값은 copy() 로 바꿔 주세요.

package com.naleul.naleul.feature.habit

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
import kotlinx.serialization.json.JsonObject

@Serializable(with = HabitStatus.Serializer::class)
enum class HabitStatus {
    ACTIVE, PAUSED, DELETED;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<HabitStatus>("HabitStatus", HabitStatus.entries.toTypedArray(), HabitStatus.ACTIVE)

    companion object {
        val fallback: HabitStatus get() = ACTIVE
        fun fromRaw(raw: String?): HabitStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 잔디 한 칸 — DONE 함 · MISSED 놓침 · TODO 오늘 아직 · OFF 반복 요일 아님 · PAUSED 쉬는 중 · BEFORE_START · FUTURE */
@Serializable(with = HabitDayState.Serializer::class)
enum class HabitDayState {
    DONE, MISSED, TODO, OFF, PAUSED, BEFORE_START, FUTURE;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            DONE -> "했어요"
            MISSED -> "놓쳤어요"
            TODO -> "오늘 아직"
            OFF -> "안 하는 날"
            PAUSED -> "쉬는 중"
            BEFORE_START -> "시작 전"
            FUTURE -> ""
        }

    object Serializer : FallbackEnumSerializer<HabitDayState>("HabitDayState", HabitDayState.entries.toTypedArray(), HabitDayState.OFF)

    companion object {
        val fallback: HabitDayState get() = OFF
        fun fromRaw(raw: String?): HabitDayState? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class HabitRecentDay(
    var date: String,
    var state: HabitDayState = HabitDayState.OFF,
) {
    companion object {}
}

@Serializable
data class Habit(
    var habitId: Int,
    var name: String = "",
    var emoji: String? = null,
    /** 비어 있으면 매일 (daily = true) */
    var repeatDays: List<JavaDayOfWeek> = emptyList(),
    var daily: Boolean = repeatDays.isEmpty(),
    /** "08:30" */
    var remindTime: String? = null,
    var status: HabitStatus = HabitStatus.ACTIVE,
    var startDate: String = KST.today(),
    var sortOrder: Int = 0,
    /** 기준 날짜(date)에 해야 하는 날인지 */
    var scheduled: Boolean = false,
    /** 기준 날짜에 체크했는지 */
    var checked: Boolean = false,
    var state: HabitDayState = HabitDayState.OFF,
    /** 연속으로 지킨 "해야 하는 날" 수 (오늘 아직 안 했으면 어제까지) */
    var streak: Int = 0,
    /** 최근 30일 실천률(%) — 해야 하는 날이 없었으면 null */
    var rate30: Int? = null,
    /** 최근 7일 (오래된 날 → 기준 날짜) */
    var recent7: List<HabitRecentDay> = emptyList(),
    /** 하는 시간대 "20:00" ~ "20:30" (선택) — 있으면 캘린더에 블록으로 보여요 */
    var startTime: String? = null,
    var endTime: String? = null,
) {
    val id: Int get() = habitId

    val hasTimeRange: Boolean get() = !startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()
    val timeRangeLabel: String?
        get() {
            val s = startTime
            val e = endTime
            if (!hasTimeRange || s == null || e == null) return null
            return "${s.take(5)}–${e.take(5)}"
        }

    val isActive: Boolean get() = status == HabitStatus.ACTIVE
    val isPaused: Boolean get() = status == HabitStatus.PAUSED
    val title: String get() = (emoji?.let { if (it.isEmpty()) "" else "$it " } ?: "") + name

    companion object {}
}

/** GET /habits?date= — 그날 습관 목록 */
@Serializable
data class HabitHome(
    var goalCategoryId: Int,
    var goalName: String,
    var emoji: String? = null,
    var date: String,
    var today: String,
    var scheduledCount: Int,
    var doneCount: Int,
    var habits: List<Habit>,
) {
    companion object {}
}

/** GET /habits/heatmap?from= — 습관별 잔디 (cells[i] = from + i 일) */
@Serializable
data class HabitHeatmap(
    var from: String,
    var to: String,
    var today: String,
    var days: List<Day>,
    var habits: List<Row>,
) {
    @Serializable
    data class Day(
        var date: String,
        var done: Int,
        var scheduled: Int,
        var level: Int,
    ) {
        companion object {}
    }

    @Serializable
    data class Row(
        var habitId: Int,
        var name: String,
        var emoji: String? = null,
        var status: HabitStatus,
        var streak: Int,
        var rate: Int? = null,
        var doneDays: Int,
        var cells: List<HabitDayState>,
    ) {
        val id: Int get() = habitId

        companion object {}
    }

    /** 습관별 날짜 → 상태 */
    fun states(of: Int): Map<String, HabitDayState> {
        val row = habits.firstOrNull { it.habitId == of } ?: return emptyMap()
        val out = HashMap<String, HabitDayState>()
        row.cells.forEachIndexed { i, s -> out[KST.addDays(from, i)] = s }
        return out
    }

    companion object {}
}

/** 습관 추가 · 수정 입력. repeatDays 가 비어 있으면 매일 */
data class HabitInput(
    var name: String,
    var emoji: String,
    var repeatDays: List<JavaDayOfWeek>,
    /** "HH:mm" — null 이면 알림 없음 */
    var remindTime: String? = null,
    /** 수정할 때 원래 알림이 있었는지 (지우려면 clearRemindTime 을 보내요) */
    var hadRemind: Boolean = false,
    /** 하는 시간대 "HH:mm" (둘 다 있거나 둘 다 null) */
    var startTime: String? = null,
    var endTime: String? = null,
    /** 수정할 때 원래 시간대가 있었는지 (지우려면 clearTimeRange) */
    var hadTimeRange: Boolean = false,
) {
    val json: JsonElement
        get() {
            val o = linkedMapOf<String, Any?>(
                "name" to name.trim(),
                "emoji" to emoji.trim(),
                "repeatDays" to repeatDays.map { it.name },
            )
            val r = remindTime
            if (r != null) o["remindTime"] = r else if (hadRemind) o["clearRemindTime"] = true
            val s = startTime
            val e = endTime
            if (s != null && e != null) {
                o["startTime"] = s
                o["endTime"] = e
            } else if (hadTimeRange) {
                o["clearTimeRange"] = true
            }
            return JsonObject(o.mapValues { (_, v) -> toJsonValue(v) })
        }

    companion object {}
}

/** 빠르게 고를 수 있는 기본 습관 — 누르면 바로 추가돼요 (웹 HABIT_SUGGESTIONS) */
data class HabitSuggestion(
    val emoji: String,
    val name: String,
    val repeatDays: List<JavaDayOfWeek> = emptyList(),
) {
    val id: String get() = name

    companion object {
        val all: List<HabitSuggestion> = listOf(
            HabitSuggestion(emoji = "💊", name = "영양제 먹기"),
            HabitSuggestion(emoji = "💧", name = "물 2L 마시기"),
            HabitSuggestion(emoji = "🚿", name = "샤워하기"),
            HabitSuggestion(emoji = "🧹", name = "집안일 하기"),
            HabitSuggestion(emoji = "🦷", name = "치실하기"),
            HabitSuggestion(emoji = "📓", name = "일기 쓰기"),
            HabitSuggestion(emoji = "🧘", name = "스트레칭 5분"),
            HabitSuggestion(emoji = "🚭", name = "금연 지키기"),
            HabitSuggestion(emoji = "🍺", name = "금주 지키기"),
            HabitSuggestion(emoji = "🥗", name = "식단 지키기"),
            HabitSuggestion(emoji = "🌙", name = "자정 전에 자기"),
            HabitSuggestion(emoji = "🗑️", name = "분리수거", repeatDays = listOf(JavaDayOfWeek.WEDNESDAY, JavaDayOfWeek.SUNDAY)),
        )
    }
}

object HabitAPI {
    /** 그날 습관 목록 — date 가 null 이면 오늘. 처음 부르면 서버가 기본 생활 습관 그릇을 만들어요 */
    suspend fun home(date: String? = null): HabitHome? =
        API.get<HabitHome>("/v1/habits" + (date?.let { "?date=$it" } ?: ""))

    /** 잔디 — from(포함) ~ 오늘. 서버는 최대 400일까지 받아요 */
    suspend fun heatmap(from: String? = null): HabitHeatmap? =
        API.get<HabitHeatmap>("/v1/habits/heatmap" + (from?.let { "?from=$it" } ?: ""))

    suspend fun create(input: HabitInput): Habit? =
        API.post<Habit>("/v1/habits", input.json)

    suspend fun update(habitId: Int, input: HabitInput): Habit? =
        API.patch<Habit>("/v1/habits/$habitId", input.json)

    suspend fun pause(habitId: Int) {
        API.send("PATCH", "/v1/habits/$habitId/pause")
    }

    suspend fun resume(habitId: Int) {
        API.send("PATCH", "/v1/habits/$habitId/resume")
    }

    suspend fun delete(habitId: Int) {
        API.send("DELETE", "/v1/habits/$habitId")
    }

    /** 체크 · 체크 풀기 (그날) */
    suspend fun setChecked(habitId: Int, day: String, checked: Boolean) {
        API.send(if (checked) "PUT" else "DELETE", "/v1/habits/$habitId/checks/$day")
    }
}

/** "매일" · "평일" · "주말" · "월·수·금" (웹 daysLabel) */
object HabitFormat {
    fun daysLabel(h: Habit): String = daysLabel(daily = h.daily, days = h.repeatDays)

    fun daysLabel(daily: Boolean, days: List<JavaDayOfWeek>): String {
        val set = days.toSet()
        if (daily || set.isEmpty() || set.size == 7) return "매일"
        val weekdays = setOf(
            JavaDayOfWeek.MONDAY, JavaDayOfWeek.TUESDAY, JavaDayOfWeek.WEDNESDAY,
            JavaDayOfWeek.THURSDAY, JavaDayOfWeek.FRIDAY,
        )
        if (set == weekdays) return "평일"
        if (set == setOf(JavaDayOfWeek.SATURDAY, JavaDayOfWeek.SUNDAY)) return "주말"
        return JavaDayOfWeek.entries.filter { set.contains(it) }.joinToString("·") { it.label }
    }
}
