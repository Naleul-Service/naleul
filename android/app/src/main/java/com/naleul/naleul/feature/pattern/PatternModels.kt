// feature/pattern/PatternModels.kt
//
// iOS Features/Pattern/PatternModels.swift
// 웹 features/pattern/types.ts · api.ts · format.ts · ui/Emphasis.tsx 를 옮겼어요.
// 백엔드 PatternReportResponse (domain/pattern/dto) 와 1:1 이에요.
//
// 카드 하나가 계산에 실패하면 그 필드가 null 이라 카드 필드는 전부 nullable 이에요.
// 목록(배열)도 혹시 null 이 와서 화면 전체가 디코딩 실패로 안 뜨는 일이 없게 nullable 로 받고,
// 화면에서는 `?: emptyList()` 로 써요.

package com.naleul.naleul.feature.pattern

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

// MARK: - enum

/** 분석 기간 (최근 4주 / 전체) */
@Serializable(with = PatternPeriod.Serializer::class)
enum class PatternPeriod {
    RECENT_4W, ALL;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            RECENT_4W -> "최근 4주"
            ALL -> "전체"
        }

    object Serializer : FallbackEnumSerializer<PatternPeriod>("PatternPeriod", PatternPeriod.entries.toTypedArray(), PatternPeriod.RECENT_4W)

    companion object {
        val fallback: PatternPeriod get() = RECENT_4W
        fun fromRaw(raw: String?): PatternPeriod? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 평일 / 주말 / 매일 */
@Serializable(with = PatternDayGroup.Serializer::class)
enum class PatternDayGroup {
    WEEKDAY, WEEKEND, ALL;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            WEEKDAY -> "평일"
            WEEKEND -> "주말"
            ALL -> "매일"
        }

    object Serializer : FallbackEnumSerializer<PatternDayGroup>("PatternDayGroup", PatternDayGroup.entries.toTypedArray(), PatternDayGroup.ALL)

    companion object {
        val fallback: PatternDayGroup get() = ALL
        fun fromRaw(raw: String?): PatternDayGroup? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 히트맵 칸: 값 있음 / 고정 시간(빗금) / 블록 없음 */
@Serializable(with = PatternHeatCellState.Serializer::class)
enum class PatternHeatCellState {
    VALUE, FIXED, EMPTY;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<PatternHeatCellState>("PatternHeatCellState", PatternHeatCellState.entries.toTypedArray(), PatternHeatCellState.EMPTY)

    companion object {
        val fallback: PatternHeatCellState get() = EMPTY
        fun fromRaw(raw: String?): PatternHeatCellState? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = PatternSuggestionType.Serializer::class)
enum class PatternSuggestionType {
    ROUTINE_TIME, ROUTINE_DAY, unknown;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<PatternSuggestionType>("PatternSuggestionType", PatternSuggestionType.entries.toTypedArray(), PatternSuggestionType.unknown)

    companion object {
        val fallback: PatternSuggestionType get() = unknown
        fun fromRaw(raw: String?): PatternSuggestionType? = entries.firstOrNull { it.rawValue == raw }
    }
}

@Serializable(with = PatternAiStatus.Serializer::class)
enum class PatternAiStatus {
    AI, TEMPLATE;

    val rawValue: String get() = name

    object Serializer : FallbackEnumSerializer<PatternAiStatus>("PatternAiStatus", PatternAiStatus.entries.toTypedArray(), PatternAiStatus.TEMPLATE)

    companion object {
        val fallback: PatternAiStatus get() = TEMPLATE
        fun fromRaw(raw: String?): PatternAiStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - 리포트

@Serializable
data class PatternReport(
    val period: PatternPeriod,
    val range: PatternRange,
    val timeBlockCount: Int,
    val evaluatedCount: Int,
    /** 이 리포트를 계산한 시각 (한국 벽시계 "2026-10-09T13:42:00") */
    val generatedAt: String,
    val aiStatus: PatternAiStatus,
    /** 계산에 쓴 기록의 버전 (서버 캐시 확인용) */
    val dataVersion: String? = null,
    val style: PatternStyle? = null,
    val summary: PatternSummary? = null,
    val heatmap: PatternHeatmap? = null,
    val weekday: PatternWeekday? = null,
    val weekly: PatternWeekly? = null,
    val timeUsage: PatternTimeUsage? = null,
    val blockKeeping: PatternBlockKeeping? = null,
    val routines: PatternRoutines? = null,
    val taskHabits: PatternTaskHabits? = null,
    val suggestion: PatternSuggestionBar? = null,
) {
    companion object {}
}

@Serializable
data class PatternRange(
    val start: String,
    val end: String,
    val days: Int,
) {
    companion object {}
}

/** text 안의 **…** 는 강조 */
@Serializable
data class PatternInsight(
    val key: String,
    val icon: String,
    val text: String,
) {
    companion object {}
}

@Serializable
data class PatternStyleProgress(
    val evaluated: Int,
    val activeDays: Int,
    val requiredTasks: Int,
    val requiredDays: Int,
    val remainingTasks: Int,
    val remainingDays: Int,
) {
    companion object {}
}

@Serializable
data class PatternStyle(
    /** MORNING · DAYTIME · EVENING · EVEN (화면에선 안 써서 문자열 그대로) */
    val timeStrength: String? = null,
    /** WEEKDAY_FOCUSED · WEEKEND_RECHARGE · STEADY · BURST */
    val rhythm: String? = null,
    val title: String? = null,
    val highlight: String? = null,
    val insights: List<PatternInsight>? = null,
    /** 스타일을 보여주려면 더 필요한 Task 수 (충분하면 null) */
    val remainingForStyle: Int? = null,
    /** 스타일을 열기까지의 진행 — "Task 15개 · 4일 더" */
    val progress: PatternStyleProgress? = null,
) {
    companion object {}
}

@Serializable
data class PatternSummary(
    val executionRate: Int? = null,
    val firstWeekDelta: Int? = null,
    val bestStreak: Int,
    val currentStreak: Int,
    val avgStartDelayMinutes: Int? = null,
    val morningDelayMinutes: Int? = null,
    val eveningDelayMinutes: Int? = null,
    val aiPlacementEditRate: Int? = null,
    val aiPlacementKeptPerTen: Int? = null,
    val engineBlockCount: Int,
    val insufficient: List<String>? = null,
) {
    companion object {}
}

@Serializable
data class PatternHeatCell(
    val hour: Int,
    val state: PatternHeatCellState,
    val rate: Int? = null,
    val samples: Int? = null,
    /** 1~5 — 가장 많이 한 칸(maxCount)에 견준 진하기 */
    val level: Int? = null,
    /** 그 요일 · 시간에 실제로 한 Task · 루틴 수 (계획한 시간과 상관없이) */
    val count: Int? = null,
) {
    companion object {}
}

@Serializable
data class PatternHeatRow(
    val dayOfWeek: JavaDayOfWeek,
    val cells: List<PatternHeatCell>? = null,
) {
    companion object {}
}

@Serializable
data class PatternTimeWindow(
    val dayGroup: PatternDayGroup,
    val startHour: Int,
    /** 포함하지 않음 (19~22 = 19, 20, 21시) */
    val endHour: Int,
    val rate: Int,
    val samples: Int,
) {
    companion object {}
}

@Serializable
data class PatternHeatmap(
    val hours: List<Int>? = null,
    val rows: List<PatternHeatRow>? = null,
    /** 가장 많이 한 칸의 횟수 */
    val maxCount: Int? = null,
    val fixedLabel: String? = null,
    val goldenTime: PatternTimeWindow? = null,
    val weakTime: PatternTimeWindow? = null,
) {
    companion object {}
}

@Serializable
data class PatternWeekdayDay(
    val dayOfWeek: JavaDayOfWeek,
    val rate: Int? = null,
    val samples: Int,
) {
    companion object {}
}

@Serializable
data class PatternWeekday(
    val average: Int? = null,
    val days: List<PatternWeekdayDay>? = null,
    val lowest: JavaDayOfWeek? = null,
    val lowestMissed: String? = null,
    val insight: String? = null,
) {
    companion object {}
}

@Serializable
data class PatternWeeklyPoint(
    val index: Int,
    val weekStart: String,
    val rate: Int? = null,
    val samples: Int,
) {
    companion object {}
}

@Serializable
data class PatternWeekly(
    val points: List<PatternWeeklyPoint>? = null,
    val maxIndex: Int? = null,
) {
    companion object {}
}

@Serializable
data class PatternGoalUsage(
    val goalCategoryId: Int,
    val name: String,
    val emoji: String? = null,
    val colorCode: String? = null,
    val actualHoursPerWeek: Double,
    val plannedHoursPerWeek: Double,
    val planRatio: Int? = null,
    val temporary: Boolean,
    val newTemporary: Boolean,
) {
    companion object {}
}

@Serializable
data class PatternTimeUsage(
    val weeklyTotalHours: Double,
    val goals: List<PatternGoalUsage>? = null,
) {
    companion object {}
}

@Serializable
data class PatternFrequentMove(
    val routineId: Int? = null,
    val title: String,
    val emoji: String? = null,
    val fromLabel: String,
    val toLabel: String,
    val dayChange: Boolean,
    val description: String,
    val count: Int,
) {
    companion object {}
}

@Serializable
data class PatternBlockKeeping(
    val total: Int,
    val onTime: Int? = null,
    val late: Int? = null,
    val missed: Int? = null,
    val frequentMoves: List<PatternFrequentMove>? = null,
    /** 블록(루틴 · Task)별 제시간 / 늦게 / 놓침 횟수 — 많은 순 */
    val items: List<PatternBlockKeepItem>? = null,
) {
    companion object {}
}

@Serializable
data class PatternBlockKeepItem(
    val key: String,
    val title: String,
    val emoji: String? = null,
    val goalName: String? = null,
    val goalColorCode: String? = null,
    val routine: Boolean,
    val onTime: Int,
    val late: Int,
    val missed: Int,
) {
    val id: String get() = key
    val total: Int get() = onTime + late + missed

    companion object {}
}

@Serializable
data class PatternKeptRoutine(
    val routineId: Int,
    val title: String,
    val emoji: String? = null,
    val rate: Int,
    val samples: Int,
) {
    companion object {}
}

@Serializable
data class PatternMissedRoutine(
    val routineId: Int,
    val routineName: String,
    val emoji: String? = null,
    val label: String,
    val description: String,
    val rate: Int,
    val samples: Int,
) {
    companion object {}
}

@Serializable
data class PatternRoutines(
    val kept: List<PatternKeptRoutine>? = null,
    val missed: List<PatternMissedRoutine>? = null,
) {
    companion object {}
}

@Serializable
data class PatternTaskHabits(
    val taskCount: Int,
    /** 백엔드 Double (소수 가능) */
    val deadlineLeadDays: Double? = null,
    val deadlineLabel: String? = null,
    val postponedRate: Int? = null,
    val postponedTopGoal: String? = null,
    val brainDumpPerWeek: Double? = null,
    val brainDumpAvgItems: Double? = null,
    val brainDumpUsualDay: JavaDayOfWeek? = null,
    val brainDumpUsualLabel: String? = null,
    val brainDumpUsualRange: String? = null,
) {
    companion object {}
}

@Serializable
data class PatternSuggestionChange(
    /** "HH:mm" */
    val startTime: String? = null,
    val endTime: String? = null,
    val days: List<JavaDayOfWeek>? = null,
) {
    companion object {}
}

@Serializable
data class PatternSuggestionItem(
    val key: String,
    val type: PatternSuggestionType,
    val routineId: Int,
    val routineName: String,
    val before: PatternSuggestionChange,
    val after: PatternSuggestionChange,
) {
    val id: String get() = key

    companion object {}
}

@Serializable
data class PatternSuggestionBar(
    val text: String,
    val expectedGain: Int? = null,
    val items: List<PatternSuggestionItem>? = null,
) {
    companion object {}
}

// MARK: - API (웹 pattern/api.ts)

object PatternAPI {
    /**
     * GET /patterns?period= — 서버가 캐시해 두지만 기록이 바뀌면 바로 다시 계산해요
     * refresh: "지금 다시 계산" — 캐시를 무시하고 새로 계산
     */
    suspend fun report(period: PatternPeriod, refresh: Boolean = false): PatternReport? =
        API.get<PatternReport>("/v1/patterns?period=${period.rawValue}" + (if (refresh) "&refresh=true" else ""))

    /** key 에 ":" 가 들어가요 (ROUTINE_TIME:31:12:40) → 경로에 넣을 때 인코딩 (웹 encodeURIComponent) */
    fun suggestionPath(key: String, action: String): String {
        // URLEncoder 는 공백을 "+" 로 바꾸고 !'()~ 까지 인코딩해서, encodeURIComponent 와 같게 되돌려요
        val encoded = java.net.URLEncoder.encode(key, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
        return "/v1/patterns/suggestions/$encoded/$action"
    }

    /** 제안 하나 반영 (응답은 화면에서 쓰지 않아요) */
    suspend fun applySuggestion(key: String) {
        API.send("POST", suggestionPath(key, "apply"))
    }

    /** 제안 하나 숨기기 (일주일) */
    suspend fun dismissSuggestion(key: String) {
        API.send("POST", suggestionPath(key, "dismiss"))
    }
}

// MARK: - format (웹 pattern/format.ts)

object PatternFormat {
    /** "월" */
    fun dayShort(d: JavaDayOfWeek): String = d.label

    private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

    /** "2026-10-01" → "10.01" */
    fun dotDate(ymd: String): String {
        if (ymd.length < 10) return ymd
        return ymd.substring(5, 7) + "." + ymd.substring(8, 10)
    }

    /** 1234 → "1,234" (웹 toLocaleString) */
    fun number(n: Int): String = String.format(Locale.US, "%,d", n)

    /** 헤더 문구: "10.01 – 11.18 · 49일, TimeBlock 412개 기록 기반" */
    fun rangeLabel(r: PatternReport): String =
        "${dotDate(r.range.start)} – ${dotDate(r.range.end)} · ${r.range.days}일, TimeBlock ${number(r.timeBlockCount)}개 기록 기반"

    /** "10월 9일 13:42" — 서버가 준 한국 시간 문자열 그대로 */
    fun updatedLabel(iso: String): String {
        val parts = iso.split("T", limit = 2)
        if (parts.size != 2 || parts[0].length < 10) return iso
        val d = parts[0]
        return "${KST.monthOf(d)}월 ${KST.dayOfMonth(d)}일 ${parts[1].take(5)}"
    }

    /** 골든/취약 시간: "평일 19–22시" */
    fun windowLabel(w: PatternTimeWindow): String =
        "${w.dayGroup.label} ${pad(w.startHour)}–${pad(w.endHour)}시"

    /** +12분 / −3분 / 0분 (− 는 웹과 같은 유니코드 빼기 기호) */
    fun signedMinutes(m: Int): String {
        if (m > 0) return "+${m}분"
        if (m < 0) return "−${abs(m)}분"
        return "0분"
    }

    /** 0.0 → "0", 7.5 → "7.5" */
    fun hours1(h: Double): String {
        if (h == Math.rint(h)) return "${h.toLong()}"
        return String.format(Locale.US, "%.1f", h)
    }

    /** 인사이트 아이콘 키 → 이모지 */
    val insightIcon: Map<String, String> = mapOf(
        "moon" to "🌙", "sun" to "☀️", "sunrise" to "🌅", "calendar" to "📅",
        "alarm" to "⏰", "chart-down" to "📉", "fire" to "🔥", "sparkle" to "✨",
    )

    /** 실행 스타일 카드의 연한 파랑 (웹도 늘 어두운 카드 위에 #8c9bff 고정값을 써요) */
    val styleAccent: Color = colorHex("#8C9BFF")
}

// MARK: - 강조 (웹 ui/Emphasis.tsx)

/**
 * 백엔드 문장의 **…** 부분을 굵게(+색) 바꾼 글자(AnnotatedString)를 만들어요.
 * (사용자가 입력한 루틴 이름이 들어가므로 마크다운으로 해석하지 않고 ** 만 직접 찾아요)
 *   Text(PatternEmphasis.text("금요일에는 **운동**을 자주 놓쳐요", color = NC.textBrand), style = pt(14))
 */
object PatternEmphasis {
    private val re = Regex("""\*\*(.+?)\*\*""")

    fun text(s: String, color: Color? = null): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in re.findAll(s)) {
            if (m.range.first > last) append(s.substring(last, m.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color ?: Color.Unspecified)) {
                append(m.groupValues[1])
            }
            last = m.range.last + 1
        }
        if (last < s.length) append(s.substring(last))
    }
}

// MARK: - 고정 시간 빗금 (웹 HATCH: 135° line / subtle 줄무늬)

@Composable
fun PatternHatchFill(modifier: Modifier = Modifier) {
    // NC 색은 Canvas 그리기 블록 안에서 못 읽어서 밖에서 꺼내 둬요
    val fill = NC.subtle
    val stripe = NC.line
    Canvas(modifier) {
        drawRect(fill)
        val step = 7.dp.toPx()
        val w = size.width
        val h = size.height
        var x = -h
        while (x < w + h) {
            drawLine(stripe, start = Offset(x, h), end = Offset(x + h, 0f), strokeWidth = 3.dp.toPx())
            x += step
        }
    }
}

// MARK: - 미리보기 샘플

object PatternSampleData {
    /** 7요일 × 6~23시 칸 */
    private val heatRows: String
        get() {
            val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
            val rows = days.mapIndexed { di, d ->
                val cells = (6..23).map { h ->
                    if (di < 5 && h in 9..17) return@map """{"hour": $h, "state": "FIXED"}"""
                    if ((h + di) % 4 == 0) return@map """{"hour": $h, "state": "EMPTY", "count": 0}"""
                    val count = 1 + ((h * 7 + di * 11) % 6)   // 1~6 (maxCount 6)
                    val level = maxOf(1, minOf(5, ceil(5.0 * count / 6.0).toInt()))
                    """{"hour": $h, "state": "VALUE", "count": $count, "level": $level}"""
                }
                """{"dayOfWeek": "$d", "cells": [${cells.joinToString(",")}]}"""
            }
            return "[" + rows.joinToString(",") + "]"
        }

    val reportJSON: String
        get() = """
    {
      "period": "RECENT_4W",
      "range": {"start": "2026-09-11", "end": "2026-10-08", "days": 28},
      "timeBlockCount": 1412, "evaluatedCount": 186,
      "generatedAt": "2026-10-09T13:42:10", "aiStatus": "AI", "dataVersion": "v1",
      "style": {
        "timeStrength": "EVENING", "rhythm": "STEADY",
        "title": "저녁에 강한 꾸준형", "highlight": "저녁에 강한",
        "insights": [
          {"key": "a", "icon": "moon", "text": "저녁 7시 이후 실행률이 **87%**로 가장 높아요"},
          {"key": "b", "icon": "calendar", "text": "주말에도 평일만큼 **꾸준히** 해요"},
          {"key": "c", "icon": "alarm", "text": "아침 블록은 평균 **12분** 늦게 시작해요"}
        ],
        "remainingForStyle": null, "progress": null
      },
      "summary": {
        "executionRate": 78, "firstWeekDelta": 12, "bestStreak": 9, "currentStreak": 3,
        "avgStartDelayMinutes": 8, "morningDelayMinutes": 12, "eveningDelayMinutes": -3,
        "aiPlacementEditRate": 24, "aiPlacementKeptPerTen": 8, "engineBlockCount": 40, "insufficient": []
      },
      "heatmap": {
        "hours": [6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23],
        "rows": $heatRows,
        "maxCount": 6,
        "fixedLabel": "회사 업무",
        "goldenTime": {"dayGroup": "WEEKDAY", "startHour": 19, "endHour": 22, "rate": 91, "samples": 40},
        "weakTime": {"dayGroup": "WEEKEND", "startHour": 7, "endHour": 9, "rate": 42, "samples": 12}
      },
      "weekday": {
        "average": 76,
        "days": [
          {"dayOfWeek": "MONDAY", "rate": 82, "samples": 30}, {"dayOfWeek": "TUESDAY", "rate": 79, "samples": 28},
          {"dayOfWeek": "WEDNESDAY", "rate": 80, "samples": 31}, {"dayOfWeek": "THURSDAY", "rate": 74, "samples": 26},
          {"dayOfWeek": "FRIDAY", "rate": 58, "samples": 25}, {"dayOfWeek": "SATURDAY", "rate": 77, "samples": 22},
          {"dayOfWeek": "SUNDAY", "rate": null, "samples": 2}
        ],
        "lowest": "FRIDAY", "lowestMissed": "운동",
        "insight": "금요일에는 **운동**을 자주 놓쳐요"
      },
      "weekly": {
        "points": [
          {"index": 1, "weekStart": "2026-09-07", "rate": 66, "samples": 30},
          {"index": 2, "weekStart": "2026-09-14", "rate": 72, "samples": 40},
          {"index": 3, "weekStart": "2026-09-21", "rate": null, "samples": 2},
          {"index": 4, "weekStart": "2026-09-28", "rate": 85, "samples": 44},
          {"index": 5, "weekStart": "2026-10-05", "rate": 78, "samples": 20}
        ],
        "maxIndex": 4
      },
      "timeUsage": {
        "weeklyTotalHours": 14.5,
        "goals": [
          {"goalCategoryId": 1, "name": "토익 900", "emoji": "📚", "colorCode": "3D5AFE",
           "actualHoursPerWeek": 6.5, "plannedHoursPerWeek": 8, "planRatio": 81, "temporary": false, "newTemporary": false},
          {"goalCategoryId": 2, "name": "건강", "emoji": "💪", "colorCode": "12B76A",
           "actualHoursPerWeek": 5, "plannedHoursPerWeek": 4, "planRatio": 125, "temporary": false, "newTemporary": false},
          {"goalCategoryId": 3, "name": "블로그", "emoji": null, "colorCode": null,
           "actualHoursPerWeek": 3, "plannedHoursPerWeek": 0, "planRatio": null, "temporary": true, "newTemporary": true}
        ]
      },
      "blockKeeping": {
        "total": 1240, "onTime": 61, "late": 24, "missed": 15,
        "frequentMoves": [
          {"routineId": 11, "title": "아침 러닝", "emoji": "🏃", "fromLabel": "07:00", "toLabel": "19:00",
           "dayChange": false, "description": "평일 아침 → 저녁으로 자주 옮겨요", "count": 6}
        ],
        "items": [
          {"key": "R12", "title": "영어 단어", "emoji": "📖", "goalName": "토익 900", "goalColorCode": "3D5AFE",
           "routine": true, "onTime": 14, "late": 4, "missed": 2},
          {"key": "R11", "title": "아침 러닝", "emoji": "🏃", "goalName": "건강", "goalColorCode": "12B76A",
           "routine": true, "onTime": 3, "late": 3, "missed": 6},
          {"key": "T보고서 정리", "title": "보고서 정리", "emoji": null, "goalName": null, "goalColorCode": null,
           "routine": false, "onTime": 1, "late": 2, "missed": 1}
        ]
      },
      "routines": {
        "kept": [{"routineId": 12, "title": "영어 단어", "emoji": "📖", "rate": 92, "samples": 20}],
        "missed": [{"routineId": 11, "routineName": "아침 러닝", "emoji": "🏃", "label": "금요일 아침 러닝",
                    "description": "금 07시 · 10번 중 4번", "rate": 40, "samples": 10}]
      },
      "taskHabits": {
        "taskCount": 64, "deadlineLeadDays": 1.5, "deadlineLabel": "마감 하루 반 전에 끝내요",
        "postponedRate": 18, "postponedTopGoal": "토익 900",
        "brainDumpPerWeek": 3.2, "brainDumpAvgItems": 4.5,
        "brainDumpUsualDay": "SUNDAY", "brainDumpUsualLabel": "일요일 밤", "brainDumpUsualRange": "21–23시"
      },
      "suggestion": {
        "text": "아침 러닝을 **저녁 7시**로 옮기면 실행률이 **+18%p** 오를 것 같아요",
        "expectedGain": 18,
        "items": [
          {"key": "ROUTINE_TIME:11:19:00", "type": "ROUTINE_TIME", "routineId": 11, "routineName": "아침 러닝",
           "before": {"startTime": "07:00", "endTime": "07:40"}, "after": {"startTime": "19:00", "endTime": "19:40"}},
          {"key": "ROUTINE_DAY:12", "type": "ROUTINE_DAY", "routineId": 12, "routineName": "영어 단어",
           "before": {"days": ["MONDAY", "FRIDAY"]}, "after": {"days": ["MONDAY", "THURSDAY"]}}
        ]
      }
    }
    """

    val report: PatternReport
        get() = NJson.decodeFromString(PatternReport.serializer(), reportJSON)

    /** 기록이 하나도 없을 때 (스타일 잠김) */
    val emptyReport: PatternReport
        get() = report.copy(
            evaluatedCount = 0,
            style = PatternStyle(
                insights = emptyList(),
                progress = PatternStyleProgress(
                    evaluated = 3, activeDays = 2, requiredTasks = 18,
                    requiredDays = 6, remainingTasks = 15, remainingDays = 4,
                ),
            ),
        )
}

@Preview(showBackground = true)
@Composable
private fun PatternModelsPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.background(NC.canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PatternHatchFill(Modifier.size(width = 120.dp, height = 28.dp).clip(RoundedCornerShape(4.dp)))
            Text(PatternEmphasis.text("금요일에는 **운동**을 자주 놓쳐요", color = NC.textBrand), style = pt(14), color = NC.ink)
            Text(PatternFormat.rangeLabel(PatternSampleData.report), style = pt(13), color = NC.ink)
        }
    }
}
