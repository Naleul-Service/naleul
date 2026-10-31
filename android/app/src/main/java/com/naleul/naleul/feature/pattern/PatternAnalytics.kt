// feature/pattern/PatternAnalytics.kt
//
// iOS Features/Pattern/PatternAnalytics.swift — 나의 패턴 이벤트 (Tracking Plan 분류 "나의 패턴" · 웹 pattern.* 과 같은 이름).
// 카드들은 기간을 몰라서 PT.period 를 같이 붙여요 (PatternScreen 이 기간을 바꿀 때 맞춰 둬요).
// 그래프 만지기는 "카드마다 방문당 한 번" — 탭이 보일 때(pageview) PT.resetVisit() 로 새로 세요.
//
//   PT.dead("summary_tile")      // 눌러도 반응 없는 곳
//   PT.chart("heatmap")          // 그래프를 처음 누름

package com.naleul.naleul.feature.pattern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.feature.goal.EventProps
import com.naleul.naleul.feature.goal.GD
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

object PT {
    fun track(event: String, props: EventProps = emptyMap()) = GD.track(event, props)

    /** 요청 실패의 HTTP 상태 (0 = 네트워크) */
    fun status(e: Throwable): Int = (e as? ApiError)?.httpStatus ?: 0

    /** 지금 보고 있는 기간 (RECENT_4W · ALL) */
    var period: PatternPeriod = PatternPeriod.RECENT_4W

    /** 나의 패턴을 열기 직전에 정해 둬요 (nudge) → pageview 의 entry_source (없으면 tab_bar) */
    var pendingEntry: String? = null

    private val seen = mutableSetOf<String>()
    fun resetVisit() = seen.clear()

    /** 방문당 한 번만 */
    fun once(key: String, fn: () -> Unit) {
        if (!seen.add(key)) return
        fn()
    }

    /** 그래프를 처음 누름 (heatmap · weekday · weekly · time_usage) — 카드마다 방문당 한 번 */
    fun chart(kind: String) = once("chart:$kind") {
        track("pattern.chart_interact", mapOf("chart_kind" to kind, "period" to period.name))
    }

    /** 눌렀지만 아무 일도 없는 곳 (summary_tile · style_sentence · time_usage_row · moved_routine · kept_routine · missed_block · golden_time) */
    fun dead(target: String) = track("pattern.dead_click", mapOf("dead_target" to target, "period" to period.name))

    /** 마지막으로 계산한 뒤 지난 분 (generatedAt 은 시간대 없는 한국 시간일 수 있어요) */
    fun minutesSince(iso: String): Int? {
        val t = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.of("Asia/Seoul")).toInstant() }.getOrNull()
            ?: return null
        return maxOf(0L, (System.currentTimeMillis() - t.toEpochMilli()) / 60_000L).toInt()
    }

    /** 제안 항목 종류, 쉼표로 (ROUTINE_TIME,ROUTINE_DAY) */
    fun suggestionTypes(items: List<PatternSuggestionItem>): String = items.map { it.type.name }.distinct().joinToString(",")

    private val allSections = listOf("summary", "heatmap", "weekday", "weekly", "time_usage", "block_keeping", "routines", "task_habits")

    /** '기록이 쌓이면 보여드려요'로 비어 보인 카드 — 웹 emptySectionsOf 와 같은 조건 */
    fun emptySections(r: PatternReport): String {
        if (r.evaluatedCount == 0) return allSections.joinToString(",")
        val empty = mutableListOf<String>()
        if (r.summary?.executionRate == null) empty += "summary"
        if (r.heatmap?.rows.orEmpty().none { row -> row.cells.orEmpty().any { it.state == PatternHeatCellState.VALUE } }) empty += "heatmap"
        if (r.weekday?.days.orEmpty().none { it.rate != null }) empty += "weekday"
        if (r.weekly?.points.orEmpty().count { it.rate != null } < 2) empty += "weekly"
        if (r.timeUsage?.goals.isNullOrEmpty()) empty += "time_usage"
        if (!((r.blockKeeping?.total ?: 0) > 0 && r.blockKeeping?.onTime != null)) empty += "block_keeping"
        if (r.routines?.kept.isNullOrEmpty() && r.routines?.missed.isNullOrEmpty()) empty += "routines"
        if (r.taskHabits?.postponedRate == null && r.taskHabits?.brainDumpPerWeek == null) empty += "task_habits"
        return empty.joinToString(",")
    }
}

/** section 값 — 웹 empty_sections 와 같은 이름 */
val PatternSection.analyticsKey: String
    get() = when (this) {
        PatternSection.usage -> "time_usage"
        PatternSection.block -> "block_keeping"
        PatternSection.habits -> "task_habits"
        else -> name
    }

/** keep_kind 값 (on_time · late · missed) */
val PatternKeepKind.analyticsKey: String get() = if (this == PatternKeepKind.onTime) "on_time" else name

/** 눌러도 반응 없는 곳 → pattern.dead_click (물결 없이) */
fun Modifier.patternDeadTap(target: String): Modifier = composed {
    if (LocalInspectionMode.current) return@composed this
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { PT.dead(target) }
}

/** 그래프를 누름 → pattern.chart_interact (방문당 한 번). 손가락을 대는 순간만 엿보고 다른 탭 동작은 그대로 받아요 */
fun Modifier.patternChartTap(kind: String): Modifier = composed {
    if (LocalInspectionMode.current) return@composed this
    pointerInput(kind) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            PT.chart(kind)
        }
    }
}
