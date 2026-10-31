// feature/record/RecordModels.kt
//
// iOS Features/Record/RecordModels.swift
// 웹 features/record/api.ts · journalText.ts · ui/pattern/format.ts
//
// 기록형(업무형) 목표 (백엔드 goal_mode = RECORD)
// 회사 업무처럼 "무엇을 이룰지"보다 "오늘 무엇을 했는지"를 쌓는 목표예요.
// 종료일·수치·마일스톤 없이 이름만으로 만들고, 기록은 "실제로 한 일"(ActualActivity)로 모아요.
//
// 웹 queryKey → Android DataKey
//   ['record'] → DataKey.record · ['activities'] → DataKey.activities · ['goals'] → DataKey.goals

package com.naleul.naleul.feature.record

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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// MARK: - 한 일 빠르게 기록하기 (POST /activities/logs)

@Serializable
data class RecordActivityLogItem(
    val clientKey: String,
    /** 사용자가 적은 문장 그대로 — 서버가 시각·걸린 시간을 읽어요 */
    val text: String,
    /** 아래는 AI 해석 결과를 쓸 때만 (보낸 값이 문장보다 우선) */
    val title: String? = null,
    val date: String? = null,
    /** "HH:mm" */
    val startTime: String? = null,
    val endTime: String? = null,
    val minutes: Int? = null,
    val goalCategoryId: Int? = null,
    val tempGoalKey: String? = null,
) {
    companion object {}
}

@Serializable
data class RecordActivityLogRequest(
    val items: List<RecordActivityLogItem>,
    /** 이번에 새로 만들 임시 목표 (Brain dump 의 TempGoalInput 과 같은 모양) */
    val tempGoals: List<TempGoalInput>? = null,
) {
    companion object {}
}

/** iOS 는 커스텀 init(from:) 으로 빠진 배열을 [] 로 채웠어요 → 여기선 기본값으로 같은 효과 */
@Serializable
data class RecordActivityLogResponse(
    /** 저장된 기록 (요청 순서) */
    val activities: List<ActualActivity> = emptyList(),
    /** 이번에 새로 만든 임시 목표 (Brain dump 의 CreatedTempGoal 과 같은 모양) */
    val tempGoals: List<CreatedTempGoal> = emptyList(),
) {
    companion object {}
}

// MARK: - 이 목표에 대한 나의 패턴 (GET /activities/goals/{id}/pattern)

@Serializable(with = RecordTimeBand.Serializer::class)
enum class RecordTimeBand {
    DAWN, MORNING, AFTERNOON, EVENING;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            DAWN -> "새벽"
            MORNING -> "오전"
            AFTERNOON -> "오후"
            EVENING -> "저녁"
        }

    val range: String
        get() = when (this) {
            DAWN -> "0–6시"
            MORNING -> "6–12시"
            AFTERNOON -> "12–18시"
            EVENING -> "18–24시"
        }

    object Serializer : FallbackEnumSerializer<RecordTimeBand>("RecordTimeBand", RecordTimeBand.entries.toTypedArray(), RecordTimeBand.MORNING)

    companion object {
        val fallback: RecordTimeBand get() = MORNING
        fun fromRaw(raw: String?): RecordTimeBand? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class RecordPeriodStat(
    val start: String,
    val end: String,
    /** 완료한 Task 수 */
    val count: Int,
    /** 시간을 알 수 있는 Task 의 시간 합 */
    val minutes: Int,
    val activeDays: Int,
) {
    companion object {}
}

@Serializable
data class RecordPatternSummary(
    val totalCount: Int,
    val totalMinutes: Int,
    val activeDays: Int,
    val firstDoneDate: String? = null,
    /** 최근 4주 (오늘 포함 28일) */
    val recent: RecordPeriodStat,
    /** 그 전 4주 */
    val previous: RecordPeriodStat,
    /** 완료한 Task 가 있는 주가 몇 주째 이어지는지 (이번 주가 비었으면 지난주까지) */
    val weekStreak: Int,
    val thisWeekDone: Boolean,
) {
    companion object {}
}

@Serializable
data class RecordDayStat(
    val date: String,
    val count: Int,
    val minutes: Int,
) {
    companion object {}
}

@Serializable
data class RecordWeekStat(
    val weekStart: String,
    val count: Int,
    val minutes: Int,
    val cumulativeCount: Int,
    val cumulativeMinutes: Int,
) {
    companion object {}
}

@Serializable
data class RecordRhythm(
    /** 잔디 시작일 (월요일) */
    val heatmapStart: String,
    /** 완료가 있는 날만 */
    val days: List<RecordDayStat>,
    /** 최근 12주, 오래된 것부터 (빈 주 포함) */
    val weeks: List<RecordWeekStat>,
) {
    companion object {}
}

@Serializable
data class RecordWhenCell(
    val dayOfWeek: JavaDayOfWeek,
    val band: RecordTimeBand,
    val count: Int,
    val minutes: Int,
) {
    companion object {}
}

@Serializable
data class RecordBandStat(
    val band: RecordTimeBand,
    val count: Int,
    val minutes: Int,
) {
    companion object {}
}

@Serializable
data class RecordWhen(
    val cells: List<RecordWhenCell>,
    val bands: List<RecordBandStat>,
    val peakDay: JavaDayOfWeek? = null,
    val peakBand: RecordTimeBand? = null,
    /** 시각을 몰라서 표에서 뺀 Task 수 (시간 미정으로 완료) */
    val untimedCount: Int,
) {
    companion object {}
}

/** 완료한 루틴 Task 기준. 시간은 실제 시각 → 계획 시간 → 루틴 기본 시간 순 */
@Serializable
data class RecordRoutineStat(
    val routineId: Int,
    val routineName: String,
    val emoji: String? = null,
    val minutes: Int,
    val completedCount: Int,
    /** 최근 4주 */
    val recentMinutes: Int,
    val recentCount: Int,
    /** 그 전 4주 */
    val previousMinutes: Int,
    val previousCount: Int,
) {
    companion object {}
}

@Serializable
data class RecordRoutines(
    /** 쌓인 시간이 많은 순 (완료가 없는 루틴도 포함). 목표에 루틴이 없으면 비어 있어요 → 카드를 숨겨요 */
    val items: List<RecordRoutineStat>,
    /** 최근 4주에 그 전 4주보다 눈에 띄게 더 한 루틴 */
    val risingRoutineId: Int? = null,
) {
    companion object {}
}

/**
 * 이 목표의 완료한 Task(일반 + 루틴) 기준 패턴. 직접 남긴 "한 일" 기록은 섞지 않아요 (업무 일지에서 함께 보여줘요)
 * `when` 은 Kotlin 예약어라 백틱으로 감싸요 (JSON 키는 그대로 "when").
 */
@Serializable
data class RecordPattern(
    val summary: RecordPatternSummary,
    val rhythm: RecordRhythm,
    val `when`: RecordWhen,
    val routines: RecordRoutines,
) {
    companion object {}
}

// MARK: - 업무 일지 (GET /activities/goals/{id}/journal)

/** TASK: 완료한 Task · RECORD: 직접 남긴 기록 */
@Serializable(with = RecordJournalItemType.Serializer::class)
enum class RecordJournalItemType {
    TASK, RECORD;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<RecordJournalItemType>("RecordJournalItemType", RecordJournalItemType.entries.toTypedArray(), RecordJournalItemType.TASK)

    companion object {
        val fallback: RecordJournalItemType get() = TASK
        fun fromRaw(raw: String?): RecordJournalItemType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable
data class RecordJournalItem(
    val type: RecordJournalItemType,
    val id: Int,
    val title: String,
    val emoji: String? = null,
    /** 모르면 null (시간 미정으로 완료한 Task) */
    val startAt: String? = null,
    val endAt: String? = null,
    val minutes: Int? = null,
    /** 루틴 Task 인지 */
    val routine: Boolean,
    val memo: String? = null,
) {
    /** 목록 키 (Task 와 기록의 id 가 겹칠 수 있어서 종류를 붙여요) */
    val key: String get() = "${type.rawValue}-$id"

    companion object {}
}

@Serializable
data class RecordJournalDay(
    val date: String,
    val taskCount: Int,
    val recordCount: Int,
    /** 시간을 알 수 있는 항목의 합 */
    val minutes: Int,
    /** 시각순 (시각 없는 Task 는 뒤로) */
    val items: List<RecordJournalItem>,
) {
    val id: String get() = date

    companion object {}
}

@Serializable
data class RecordJournal(
    /** 최근 날짜부터 */
    val days: List<RecordJournalDay>,
    /** 더 오래된 일지가 있으면 다음 쪽을 부를 날짜 (없으면 null) */
    val nextBefore: String? = null,
) {
    companion object {}
}

// MARK: - API

object RecordAPI {
    /**
     * 업무형 목표 만들기 → 성공하면 DataKey.goals 다시 불러오기
     * 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류(goalType · goalSubType)를 보내지 않아요 (서버에서 선택)
     */
    suspend fun createRecordGoal(name: String, emoji: String?, colorId: Int?, description: String?): GoalCategory? {
        // null 도 명시적으로 보내요 (iOS .of(nil) → null)
        val body = json(
            "name" to name,
            "emoji" to emoji,
            "colorId" to colorId,
            "description" to description,
        )
        return API.post<GoalCategory>("/v1/goal-categories/record", body)
    }

    /**
     * 임시 목표 → 기록형 목표로 그대로 두기 (구체화 없이). 웹 useKeepAsRecord
     * 성공 문구: "업무형 목표로 바꿨어요." · 끝나면 DataKey.goals 다시 불러오기
     */
    suspend fun keepAsRecord(goalId: Int): GoalCategory? =
        API.patch<GoalCategory>("/v1/goal-categories/$goalId/keep-as-record")

    /** 한 일 기록. 성공하면 activities · record (임시 목표가 생겼으면 goals) 다시 불러오기 */
    suspend fun logActivities(body: RecordActivityLogRequest): RecordActivityLogResponse? =
        API.requestEncodable<RecordActivityLogResponse, RecordActivityLogRequest>("POST", "/v1/activities/logs", body)

    /** 직접 남긴 기록 지우기 */
    suspend fun deleteRecord(activityId: Int) {
        API.send("DELETE", "/v1/activities/$activityId")
    }

    suspend fun pattern(goalId: Int): RecordPattern? =
        API.get<RecordPattern>("/v1/activities/goals/$goalId/pattern")

    /** 업무 일지 — 2주씩, before 로 과거를 이어 불러요 */
    suspend fun journal(goalId: Int, before: String?): RecordJournal? =
        API.get<RecordJournal>("/v1/activities/goals/$goalId/journal?days=14" + (before?.let { "&before=$it" } ?: ""))

    private var seq = 0

    /** 화면에서 쓰는 고유 키 (웹 `r${Date.now().toString(36)}${seq.toString(36)}`) */
    fun newClientKey(): String {
        val now = System.currentTimeMillis().toString(36)
        val key = "r$now${seq.toString(36)}"
        seq += 1
        return key
    }
}

// MARK: - 업무 일지 → 글 (웹 journalText.ts)

object RecordJournalText {
    /** "· 09:00–09:30 코드 리뷰 (30분) — 메모" / "· 메일 정리" */
    private fun itemLine(i: RecordJournalItem): String {
        var time = ""
        val s = i.startAt
        val e = i.endAt
        if (s != null && e != null) time = "${KST.hm(s)}–${KST.hm(e)} "
        val m = i.minutes ?: 0
        val dur = if (m > 0) " (${KST.formatDuration(m)})" else ""
        val memo = if (!i.memo.isNullOrEmpty()) " — ${i.memo}" else ""
        return "· $time${i.title}$dur$memo"
    }

    private fun dayBlock(d: RecordJournalDay): String {
        val total = if (d.minutes > 0) " · ${KST.formatDuration(d.minutes)}" else ""
        return (listOf("■ ${KST.formatMonthDay(d.date)}$total") + d.items.map { itemLine(it) }).joinToString("\n")
    }

    /**
     * 업무 일지를 메신저·문서에 바로 붙여 넣을 수 있는 글로 바꿔요.
     * 날짜는 오래된 것부터 (보고서처럼 위에서 아래로 읽히게).
     */
    fun text(title: String, days: List<RecordJournalDay>): String {
        val ordered = days.sortedBy { it.date }
        return listOf("[$title]", "", ordered.joinToString("\n\n") { dayBlock(it) }).joinToString("\n")
    }
}

// MARK: - 패턴 카드 도우미 (웹 ui/pattern/format.ts)

object RecordPatternFormat {
    /** 칸 안에 들어갈 짧은 표기: 45 → "45분", 150 → "2.5시간" */
    fun shortDuration(minutes: Int): String {
        if (minutes < 60) return "${minutes}분"
        val h = Math.round(minutes.toDouble() / 60 * 10).toDouble() / 10
        return "${PatternFormat.hours1(h)}시간"
    }

    /** 1~4 단계 (0 은 기록 없음). 가장 많이 한 날을 기준으로 나눠요 (기록형은 목표 시간이 없어서 내 기록끼리 비교) */
    fun levelOf(value: Int, max: Int): Int {
        if (value <= 0 || max <= 0) return 0
        val r = value.toDouble() / max.toDouble()
        return if (r > 0.75) 4 else if (r > 0.5) 3 else if (r > 0.25) 2 else 1
    }

    /** 단계별 색 (나의 패턴 히트맵과 같은 파란 계열: 1·2·4·5 단계) */
    @Composable
    fun levelColor(level: Int): Color = when (level) {
        1 -> NC.heat1
        2 -> NC.heat2
        3 -> NC.heat4
        4 -> NC.heat5
        else -> NC.subtle
    }
}

// MARK: - 미리보기 샘플

object RecordSampleData {
    val goal: GoalCategory
        get() {
            val json = """
            {"goalCategoryId": 7, "goalCategoryName": "나를 업무", "goalCategoryStatus": "IN_PROGRESS",
             "colorCode": "3D5AFE", "emoji": "💼", "goalMode": "RECORD", "goalType": "WORK", "goalSubType": "COMPANY_WORK",
             "generalCategories": []}
            """
            return NJson.decodeFromString(GoalCategory.serializer(), json)
        }

    val goals: List<GoalCategory>
        get() {
            val json = """
            [{"goalCategoryId": 7, "goalCategoryName": "나를 업무", "goalCategoryStatus": "IN_PROGRESS", "emoji": "💼",
              "goalMode": "RECORD", "generalCategories": []},
             {"goalCategoryId": 3, "goalCategoryName": "토익 900", "goalCategoryStatus": "IN_PROGRESS", "emoji": "📚",
              "generalCategories": []}]
            """
            return NJson.decodeFromString(ListSerializer(GoalCategory.serializer()), json)
        }

    val journal: RecordJournal
        get() {
            val today = KST.today()
            val y = KST.addDays(today, -1)
            val d3 = KST.addDays(today, -3)
            val json = """
            {"days": [
              {"date": "$today", "taskCount": 2, "recordCount": 1, "minutes": 150, "items": [
                {"type": "TASK", "id": 11, "title": "주간 회의", "emoji": "👥", "startAt": "${today}T10:00:00",
                 "endAt": "${today}T11:00:00", "minutes": 60, "routine": true, "memo": null},
                {"type": "RECORD", "id": 11, "title": "기획서 초안 작성", "emoji": null, "startAt": "${today}T13:00:00",
                 "endAt": "${today}T14:30:00", "minutes": 90, "routine": false, "memo": "목차까지"},
                {"type": "TASK", "id": 12, "title": "메일 정리", "emoji": null, "startAt": null, "endAt": null,
                 "minutes": null, "routine": false, "memo": null}
              ]},
              {"date": "$y", "taskCount": 1, "recordCount": 0, "minutes": 30, "items": [
                {"type": "TASK", "id": 13, "title": "코드 리뷰", "emoji": "🧑‍💻", "startAt": "${y}T16:00:00",
                 "endAt": "${y}T16:30:00", "minutes": 30, "routine": false, "memo": null}
              ]},
              {"date": "$d3", "taskCount": 0, "recordCount": 1, "minutes": 0, "items": [
                {"type": "RECORD", "id": 14, "title": "고객 문의 답변", "emoji": null, "startAt": null, "endAt": null,
                 "minutes": null, "routine": false, "memo": null}
              ]}
            ], "nextBefore": "${KST.addDays(today, -14)}"}
            """
            return NJson.decodeFromString(RecordJournal.serializer(), json)
        }

    val pattern: RecordPattern
        get() {
            val today = KST.today()
            val thisWeek = KST.startOfWeek(today)
            val heatStart = KST.addDays(thisWeek, -15 * 7)
            // 잔디: 최근 16주 중 띄엄띄엄
            val days = mutableListOf<String>()
            var d = heatStart
            var i = 0
            while (d <= today) {
                if (i % 3 != 0 && i % 7 < 5) {
                    days.add("""{"date": "$d", "count": ${1 + i % 4}, "minutes": ${30 * (1 + i % 3)}}""")
                }
                d = KST.addDays(d, 1)
                i += 1
            }
            // 주차: 최근 12주
            val weeks = mutableListOf<String>()
            var cum = 0
            var cumMin = 0
            val counts = listOf(2, 3, 0, 4, 5, 3, 6, 4, 7, 5, 8, 3)
            for (w in 0 until 12) {
                val count = counts[w]
                cum += count
                cumMin += count * 45
                weeks.add(
                    """{"weekStart": "${KST.addDays(thisWeek, (w - 11) * 7)}", "count": $count, "minutes": ${count * 45}, "cumulativeCount": $cum, "cumulativeMinutes": $cumMin}""",
                )
            }
            // 언제: 요일 × 시간대
            val dayNames = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
            val bands = listOf("DAWN", "MORNING", "AFTERNOON", "EVENING")
            val cells = mutableListOf<String>()
            dayNames.forEachIndexed { di, dn ->
                bands.forEachIndexed { bi, b ->
                    val c = if (di < 5) listOf(0, 3, 5, 1)[bi] + di % 2 else listOf(0, 1, 0, 2)[bi]
                    cells.add("""{"dayOfWeek": "$dn", "band": "$b", "count": $c, "minutes": ${c * 40}}""")
                }
            }
            val json = """
            {
              "summary": {"totalCount": $cum, "totalMinutes": $cumMin, "activeDays": 38, "firstDoneDate": "$heatStart",
                "recent": {"start": "${KST.addDays(today, -27)}", "end": "$today", "count": 23, "minutes": 1035, "activeDays": 15},
                "previous": {"start": "${KST.addDays(today, -55)}", "end": "${KST.addDays(today, -28)}", "count": 18, "minutes": 810, "activeDays": 12},
                "weekStreak": 4, "thisWeekDone": true},
              "rhythm": {"heatmapStart": "$heatStart", "days": [${days.joinToString(",")}], "weeks": [${weeks.joinToString(",")}]},
              "when": {"cells": [${cells.joinToString(",")}],
                "bands": [{"band": "DAWN", "count": 0, "minutes": 0}, {"band": "MORNING", "count": 17, "minutes": 680},
                          {"band": "AFTERNOON", "count": 27, "minutes": 1080}, {"band": "EVENING", "count": 9, "minutes": 360}],
                "peakDay": "TUESDAY", "peakBand": "AFTERNOON", "untimedCount": 3},
              "routines": {"items": [
                {"routineId": 1, "routineName": "주간 회의", "emoji": "👥", "minutes": 600, "completedCount": 10,
                 "recentMinutes": 240, "recentCount": 4, "previousMinutes": 180, "previousCount": 3},
                {"routineId": 2, "routineName": "코드 리뷰", "emoji": "🧑‍💻", "minutes": 300, "completedCount": 10,
                 "recentMinutes": 150, "recentCount": 5, "previousMinutes": 60, "previousCount": 2},
                {"routineId": 3, "routineName": "회고 쓰기", "emoji": null, "minutes": 0, "completedCount": 0,
                 "recentMinutes": 0, "recentCount": 0, "previousMinutes": 0, "previousCount": 0}
              ], "risingRoutineId": 2}
            }
            """
            return NJson.decodeFromString(RecordPattern.serializer(), json)
        }

    /** 완료한 Task 가 아직 없을 때 */
    val emptyPattern: RecordPattern
        get() {
            val p = pattern
            return p.copy(
                summary = p.summary.copy(totalCount = 0, totalMinutes = 0),
                rhythm = p.rhythm.copy(days = emptyList()),
            )
        }
}
