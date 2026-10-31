// model/BrainDumpModels.kt
//
// iOS Models/BrainDumpModels.swift 를 옮긴 파일.
// 웹 features/brain-dump/types.ts · api.ts · parseLine.ts
// "할 일을 적으면 AI 가 정리해서 캘린더에 넣어 주는" Task 추가 기능의 모델 (백엔드 BE-5 DTO 와 1:1).
// 시간은 "HH:mm" 또는 "HH:mm:ss" (LocalTime), 날짜는 "YYYY-MM-DD"
//
// Swift → Kotlin 메모
//  - Swift Optional → Kotlin nullable + 기본값 null. 보낼 때(NJson explicitNulls = false) null 필드는 빠져요
//    (Swift Encodable 이 nil 을 안 보내는 것과 같아요).
//  - 서버 응답 모델은 val (바꿀 땐 copy(...)). 화면에서 필드를 직접 바꾸는 TempGoalInput · ParsedLine 만 var 예요.
//    ⚠️ Kotlin 객체는 참조라서, 상태에 들어 있는 값을 바꿀 땐 copy() 로 복사본을 만든 뒤 바꾸세요.

package com.naleul.naleul.model

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
import java.util.UUID

/** ON: 그날 하는 일 · DUE: 그날까지 · NONE: 날짜 없음 */
@Serializable(with = DateType.Serializer::class)
enum class DateType {
    ON, DUE, NONE;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<DateType>("DateType", DateType.entries.toTypedArray(), DateType.NONE)

    companion object {
        val fallback: DateType get() = NONE
        fun fromRaw(raw: String?): DateType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = GoalMatchType.Serializer::class)
enum class GoalMatchType {
    EXISTING, TEMP, NONE;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<GoalMatchType>("GoalMatchType", GoalMatchType.entries.toTypedArray(), GoalMatchType.NONE)

    companion object {
        val fallback: GoalMatchType get() = NONE
        fun fromRaw(raw: String?): GoalMatchType? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 오늘 남은 Task 추가 개수 (Free 하루 2개) */
@Serializable
data class TaskQuota(
    val premium: Boolean,
    /** Pro 는 null (무제한) */
    val dailyLimit: Int? = null,
    val usedToday: Int,
    val remaining: Int? = null,
) {
    companion object {}
}

// MARK: 1단계 → 2단계 (AI 해석)

/** AI 해석 요청 한 줄 (보내기 전용 — iOS Encodable) */
@Serializable
data class ParseItemRequest(
    val clientKey: String,
    val text: String,
    val date: String? = null,
    val dateType: DateType? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    /** 사용자가 정한 걸리는 시간(분) — AI 추정보다 우선 */
    val estimatedMinutes: Int? = null,
) {
    companion object {}
}

/**
 * AI 가 고른 목표 연결.
 * iOS 커스텀 init(from:) 처럼 빠진 필드는 기본값으로 채워요 (type 이 없으면 NONE, temporaryGoal 이 없으면 false).
 */
@Serializable
data class GoalLink(
    val type: GoalMatchType = GoalMatchType.NONE,
    val goalCategoryId: Int? = null,
    val goalName: String? = null,
    val goalEmoji: String? = null,
    val goalColorCode: String? = null,
    val temporaryGoal: Boolean = false,
    val generalCategoryId: Int? = null,
    val generalCategoryName: String? = null,
    val milestoneId: Int? = null,
    val milestoneTitle: String? = null,
    val tempKey: String? = null,
    val reason: String? = null,
) {
    companion object {}
}

@Serializable
data class ParsedItem(
    val clientKey: String,
    val title: String,
    val dateType: DateType,
    val scheduledDate: String? = null,
    val dueDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val estimatedMinutes: Int,
    val priority: TaskPriority? = null,
    val kind: TaskKind? = null,
    val goal: GoalLink,
) {
    companion object {}
}

@Serializable
data class TempGoalInfo(
    val tempKey: String,
    val name: String,
    val emoji: String? = null,
) {
    companion object {}
}

@Serializable
data class ParseResponse(
    val items: List<ParsedItem>,
    val tempGoals: List<TempGoalInfo>,
    /** true 면 AI 없이 규칙으로만 해석 (목표 연결이 비어 있을 수 있음) */
    val aiFailed: Boolean,
    /** true 면 오늘 AI 정리 횟수를 다 써서 기본 정리로만 나눴어요 (aiFailed 도 true) */
    val aiLimited: Boolean? = null,
) {
    companion object {}
}

// MARK: 2단계 → 3단계 (저장 + 배치)

/** 확정 요청 한 줄 (보내기 전용 — iOS Encodable) */
@Serializable
data class ConfirmItem(
    val clientKey: String,
    val title: String,
    val dateType: DateType,
    val scheduledDate: String? = null,
    val dueDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val estimatedMinutes: Int? = null,
    val priority: TaskPriority? = null,
    val kind: TaskKind? = null,
    val goalCategoryId: Int? = null,
    val generalCategoryId: Int? = null,
    val milestoneId: Int? = null,
    val tempGoalKey: String? = null,
    /** 업무형 목표의 주제 (WorkTopic) — 2단계 줄에서 바로 골라요 (2026-10) */
    val workTopicId: Int? = null,
) {
    companion object {}
}

/**
 * 새로 만들 임시 목표. 화면에서 `trimmed.name = …` 처럼 이름을 직접 고치는 곳이 있어서 var 예요.
 * ⚠️ 목록 안의 값을 바꿀 땐 copy() 한 복사본에서: val t2 = t.copy(); t2.name = "…"
 */
@Serializable
data class TempGoalInput(
    var tempKey: String,
    var name: String,
    var emoji: String? = null,
    var colorId: Int? = null,
) {
    companion object {}
}

@Serializable
data class BrainDumpConfirmRequest(
    val items: List<ConfirmItem>,
    val tempGoals: List<TempGoalInput>,
    /** true: 시간을 정하지 않은 Task 는 저장만 하고(pending) 배치는 점선 미리보기 → 확인 후에 */
    val placeLater: Boolean? = null,
) {
    companion object {}
}

@Serializable
data class Placement(
    val taskId: Int,
    val clientKey: String,
    val title: String,
    val date: String,
    val start: String,
    val end: String,
    val reason: String? = null,
    val locked: Boolean,
) {
    companion object {}
}

@Serializable
data class BrainDumpTaskRef(
    val taskId: Int,
    val clientKey: String,
    val title: String,
    val date: String? = null,
) {
    companion object {}
}

@Serializable
data class BrainDumpConflict(
    val clientKey: String,
    val title: String,
    val start: String,
    val end: String,
    val conflictTaskId: Int? = null,
    val conflictTitle: String? = null,
) {
    companion object {}
}

@Serializable
data class CreatedTempGoal(
    val tempKey: String,
    val goalCategoryId: Int,
    val name: String,
    val emoji: String? = null,
    val colorCode: String? = null,
) {
    companion object {}
}

/**
 * 저장 + 배치 결과.
 * iOS 커스텀 init(from:) 처럼 빠진 필드는 기본값(false / 빈 목록)으로 채워요 — 모든 필드가 없어도 읽혀요.
 */
@Serializable
data class ConfirmResponse(
    /** false 면 아무것도 저장하지 않음 → conflicts 를 보여주고 시간을 고쳐 다시 요청 */
    val created: Boolean = false,
    val placements: List<Placement> = emptyList(),
    val movedToOtherDays: List<Placement> = emptyList(),
    val unscheduled: List<BrainDumpTaskRef> = emptyList(),
    /** placeLater 로 저장만 한 Task — 이 id 들로 배치 미리보기를 받아 "이대로 진행할까요?"를 물어요 */
    val pending: List<BrainDumpTaskRef>? = null,
    val conflicts: List<BrainDumpConflict> = emptyList(),
    val tempGoals: List<CreatedTempGoal> = emptyList(),
) {
    companion object {}
}

// MARK: - API

object BrainDumpAPI {
    /** 오늘 남은 Task 추가 개수 */
    suspend fun quota(): TaskQuota? =
        API.get<TaskQuota>("/v1/tasks/quota")

    /** 1단계 → 2단계: AI 해석 (최대 15초 정도 걸릴 수 있어요) */
    suspend fun parse(items: List<ParseItemRequest>): ParseResponse? =
        API.post<ParseResponse>("/v1/brain-dump/parse", json("items" to JSONValueUtil.from(items)))

    /** 2단계 → 3단계: 저장 + 배치. created 면 timetable · usage (임시 목표가 있으면 goals) 다시 불러오기 */
    suspend fun confirm(body: BrainDumpConfirmRequest): ConfirmResponse? =
        API.requestEncodable<ConfirmResponse, BrainDumpConfirmRequest>("POST", "/v1/brain-dump/confirm", body)

    /** 화면에서 쓰는 고유 키 (clientKey) */
    fun newClientKey(): String = UUID.randomUUID().toString().lowercase()
}

// MARK: - parseLine (한 줄을 바로 읽어서 칩 채우기)

/**
 * Brain dump 한 줄을 Enter 하는 순간 바로 읽어서 칩을 채워요 (서버 KoreanTaskTextParser 와 같은 규칙의 일부).
 *  - 시각: "저녁 8시", "오후 3시 반", "15:00", "3시~4시", "14:00-15:30"
 *  - 소요 시간: "3시간", "1시간 반", "90분"
 *  - 날짜: "오늘", "내일", "모레"
 * (화면 상태로 고쳐 쓰기도 해서 var — 바꿀 땐 copy() 한 복사본에서)
 */
data class ParsedLine(
    var date: String? = null,
    /** "HH:mm" */
    var startTime: String? = null,
    var endTime: String? = null,
    var minutes: Int? = null,
) {
    companion object {}
}

object LineParser {
    // "1시간"의 "1시"를 시각으로 읽지 않도록 '시' 뒤에 '간'이 오면 제외
    // 그룹: 1 오전/오후… · 2 시 · 3 ":분" · 4 "N분" · 5 "반"
    private const val hm = """(오전|오후|저녁|밤|아침)?\s*(\d{1,2})(?::(\d{2})|\s*시(?!간)(?:\s*(\d{1,2})\s*분|\s*(반))?)"""
    private val timeRange = Regex("""$hm\s*[~\-]\s*$hm""")
    private val timeOne = Regex(hm)

    // "7시 30분"의 "30분"은 시각이라 소요 시간으로 보지 않아요 ('시' 바로 뒤의 분은 제외)
    // (iOS 와 같게 공백은 최대 3개까지 — Java 정규식도 길이가 정해진 lookbehind 만 돼요)
    private val duration = Regex("""(\d{1,2})\s*시간(\s*반)?|(?<!시\s{0,3})(\d{2,3})\s*분""")
    private val relative = Regex("(오늘|내일|모레)")
    private val eveningRe = Regex("저녁|밤")

    private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

    /** 매치의 i 번째 그룹 (없으면 null) — 참여하지 않은 그룹은 null 이에요 */
    private fun group(m: MatchResult, i: Int): String? =
        if (i < m.groups.size) m.groups[i]?.value else null

    private fun toTime(
        ampm: String?,
        h: String?,
        colonMin: String?,
        min: String?,
        half: String?,
        after: Int?,
        evening: Boolean,
    ): Int? {
        var hour = h?.toIntOrNull() ?: return null
        val minute = colonMin?.toIntOrNull() ?: min?.toIntOrNull() ?: (if (half != null) 30 else 0)
        if (hour > 24 || minute > 59) return null
        if (ampm != null && listOf("오후", "저녁", "밤").contains(ampm) && hour < 12) {
            hour += 12
        } else if (ampm == null && colonMin == null && hour >= 1 && hour <= 6) {
            hour += 12 // "3시 미팅" → 15시
        } else if (ampm == null && evening && hour >= 7 && hour < 12) {
            hour += 12 // "저녁 약속 7시" → 19시
        }
        if (after != null && hour * 60 < after && hour + 12 < 24) hour += 12 // "3시~4시" 뒤 시간 보정
        if (hour == 24) hour = 0
        return hour * 60 + minute
    }

    private fun fmt(m: Int?): String? {
        if (m == null) return null
        return "${pad(m / 60)}:${pad(m % 60)}"
    }

    fun parse(text: String, today: String): ParsedLine {
        var rest = text
        val out = ParsedLine()

        val rel = relative.find(rest)
        val word = rel?.let { group(it, 1) }
        if (word != null) {
            out.date = if (word == "내일") KST.addDays(today, 1) else (if (word == "모레") KST.addDays(today, 2) else today)
        }

        // 소요 시간 먼저 (그래야 "2시간"을 2시로 읽지 않아요)
        val d = duration.find(rest)
        if (d != null) {
            val hours = group(d, 1)?.toIntOrNull()
            val mins = group(d, 3)
            if (hours != null) {
                out.minutes = hours * 60 + (if (group(d, 2) != null) 30 else 0)
            } else if (mins != null) {
                out.minutes = mins.toIntOrNull()
            }
            // 읽은 부분은 공백으로 지워요 (뒤에서 시각으로 다시 읽지 않게)
            rest = rest.substring(0, d.range.first) + " " + rest.substring(d.range.last + 1)
        }
        val mm = out.minutes
        if (mm != null && (mm < 5 || mm > 480)) out.minutes = null

        val evening = eveningRe.containsMatchIn(text)
        val r = timeRange.find(rest)
        if (r != null) {
            val g = { i: Int -> group(r, i) }
            val s = toTime(g(1), g(2), g(3), g(4), g(5), after = null, evening = evening)
            val e = if (s == null) null else toTime(g(6) ?: g(1), g(7), g(8), g(9), g(10), after = s, evening = evening)
            out.startTime = fmt(s)
            if (s != null && e != null && e > s) out.endTime = fmt(e)
        } else {
            val one = timeOne.find(rest)
            if (one != null) {
                val g = { i: Int -> group(one, i) }
                // 숫자만 있는 건 시각이 아니에요 ("3곳") — '시'나 ':' 가 있어야 해요
                if (g(3) != null || (g(0) ?: "").contains("시")) {
                    out.startTime = fmt(toTime(g(1), g(2), g(3), g(4), g(5), after = null, evening = evening))
                }
            }
        }
        return out
    }
}
