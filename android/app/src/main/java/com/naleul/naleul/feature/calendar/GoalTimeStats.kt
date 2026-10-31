// feature/calendar/GoalTimeStats.kt
//
// 목표별 실천 시간 (2026-10) — 기간 동안 완료한 Task 시간을 목표별로 나눈 얇은 막대. 참고용이라 작게.
// 웹 원본: features/timetable/ui/GoalTimeStats.tsx · iOS GoalTimeStatsView.swift
//  - 칸을 누르면 위에 말풍선으로 어떤 목표인지 (다시 누르면 닫혀요)
//  - 일간 · 3일 · 주간: 격자 아래(오늘 할 일 아래) · 월간: 달력 아래
//  - Task 를 완료 · 수정하면(DataKey.timetable) 다시 불러와요

package com.naleul.naleul.feature.calendar

import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 80 → "1시간 20분", 45 → "45분", 120 → "2시간" */
fun goalTimeDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    if (h == 0) return "${m}분"
    return if (m > 0) "${h}시간 ${m}분" else "${h}시간"
}

private fun goalLabel(g: GoalTimeEntry): String {
    if (g.goalCategoryId == null) return "목표 없음"
    val name = g.goalCategoryName ?: "이름 없는 목표"
    return if (!g.goalEmoji.isNullOrEmpty()) "${g.goalEmoji} $name" else name
}

/**
 * @param viewMode 분석 view_mode (day · three_day · week · month)
 * @param periodLabel "오늘" · "이번 주" · "10월" … 합계 앞에 붙여요
 */
@Composable
fun GoalTimeStats(
    start: String,
    end: String,
    viewMode: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
    previewData: GoalTimeResponse? = null,
) {
    val preview = LocalInspectionMode.current || previewData != null
    var data by remember { mutableStateOf(previewData) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember(start, end) { mutableStateOf<Int?>(null) }
    val seen = remember(start, end) { mutableSetOf<String>() }
    val rangeDays = KST.diffDays(start, end) + 1

    if (!preview) {
        ReloadOn(listOf(DataKey.timetable), id = "${start}_$end") {
            try {
                val r = TimetableAPI.goalTime(start, end)
                data = r
                failed = r == null
                if (r != null && seen.add("view")) {
                    CAL.track(
                        "calendar.goal_time_view",
                        mapOf(
                            "view_mode" to viewMode, "range_days" to rangeDays,
                            "goal_count" to r.goals.count { it.goalCategoryId != null },
                            "total_minutes" to r.totalMinutes, "has_data" to (r.totalMinutes > 0),
                        ),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    val current = data?.takeIf { it.startDate == start && it.endDate == end }
    val goals = current?.goals ?: emptyList()
    val total = current?.totalMinutes ?: 0
    val summary = when {
        current == null -> if (failed) "불러오지 못했어요" else "…"
        else -> "$periodLabel ${goalTimeDuration(total)}"
    }

    fun tap(i: Int) {
        if (selected == i) {
            selected = null
            return
        }
        selected = i
        val g = goals[i]
        if (!preview && seen.add("seg:${g.goalCategoryId ?: "none"}")) {
            CAL.track(
                "calendar.goal_time_segment_view",
                mapOf(
                    "view_mode" to viewMode, "range_days" to rangeDays,
                    "goal_category_id" to g.goalCategoryId, "has_goal" to (g.goalCategoryId != null),
                    "minutes" to g.minutes,
                    "share_percent" to if (total > 0) (g.minutes * 100.0 / total).roundToInt() else 0,
                    "rank" to i + 1, "trigger" to "tap",
                ),
            )
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(NC.surface, RoundedCornerShape(16.dp))
            .border(1.dp, NC.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        // 막대 칸이 위아래로 넉넉해서(28dp) 따로 간격을 두지 않아요
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("목표별 실천 시간", style = pt(13, PTWeight.semibold), color = NC.ink2)
            Spacer(Modifier.weight(1f))
            Text(summary, style = pt(12), color = NC.ink3)
        }
        GoalTimeBar(goals = goals, total = total, selected = selected, onTap = ::tap)
        if (current != null && total == 0) {
            Text("완료한 Task가 생기면 목표별로 나눠 보여드려요", style = pt(12), color = NC.ink4)
        }
    }
}

@Composable
private fun GoalTimeBar(goals: List<GoalTimeEntry>, total: Int, selected: Int?, onTap: (Int) -> Unit) {
    val density = LocalDensity.current
    // 처음 보일 때 왼쪽에서 쭉 차올라요
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
    // 막대는 8dp 지만 누르기 쉽게 칸은 28dp — 막대는 가운데
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
        val width = maxWidth
        val gap = 2.dp
        val usable = (width - gap * (goals.size - 1).coerceAtLeast(0)).coerceAtLeast(0.dp)
        val widths = goals.map { g -> (usable * (g.minutes.toFloat() / total.coerceAtLeast(1))).coerceAtLeast(3.dp) }

        val barModifier = Modifier.align(Alignment.Center).fillMaxWidth().height(8.dp)
        Box(barModifier.clip(CircleShape).background(NC.progressTrack))
        if (total > 0) {
            Row(
                barModifier
                    .graphicsLayer { scaleX = grow.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                goals.forEachIndexed { i, g ->
                    val alpha by animateFloatAsState(if (selected == null || selected == i) 1f else 0.4f, label = "seg")
                    Box(
                        Modifier
                            .width(widths[i])
                            .fillMaxHeight()
                            .alpha(alpha)
                            .background(if (g.goalCategoryId == null) NC.borderSubtle else goalColor(g.goalColorCode)),
                    )
                }
            }
            // 누른 x 로 어느 칸인지 찾아요
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = goals.joinToString(", ") { "${goalLabel(it)} ${goalTimeDuration(it.minutes)}" } }
                    .pointerInput(goals, total) {
                        detectTapGestures { p ->
                            var acc = 0f
                            var hit = goals.size - 1
                            for (i in goals.indices) {
                                acc += with(density) { (widths[i] + gap).toPx() }
                                if (p.x < acc) { hit = i; break }
                            }
                            if (hit >= 0) onTap(hit)
                        }
                    },
            )
        }

        // 말풍선 — 칸 가운데 위 (가장자리면 안쪽으로 붙여요)
        val i = selected
        AnimatedVisibility(
            visible = i != null && i < goals.size,
            enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            if (i != null && i < goals.size) {
                val before = widths.take(i).fold(0.dp) { a, w -> a + w + gap }
                val center = before + widths[i] / 2
                Layout(content = { GoalTimeBubble(goals[i], total) }) { measurables, constraints ->
                    val p = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = with(density) { 220.dp.roundToPx() }))
                    val wPx = with(density) { width.roundToPx() }
                    val x = (with(density) { center.roundToPx() } - p.width / 2).coerceIn(0, (wPx - p.width).coerceAtLeast(0))
                    // 막대 윗변(10dp) 위로 6dp 띄워요
                    layout(wPx, 0) { p.place(x, with(density) { 4.dp.roundToPx() } - p.height) }
                }
            }
        }
    }
}

@Composable
private fun GoalTimeBubble(g: GoalTimeEntry, total: Int) {
    val pct = if (total > 0) (g.minutes * 100.0 / total).roundToInt() else 0
    Column(
        Modifier
            .background(NC.bgInverse, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (g.goalCategoryId == null) NC.borderSubtle else goalColor(g.goalColorCode), CircleShape),
            )
            Text(goalLabel(g), style = pt(12, PTWeight.semibold), color = NC.textInverse, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${goalTimeDuration(g.minutes)} · $pct% · Task ${g.taskCount}개",
            style = pt(12),
            color = NC.textInverse.copy(alpha = 0.8f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalTimeStatsPreview() {
    NaleulTheme(dark = false) {
        GoalTimeStats(
            start = "2026-10-25", end = "2026-10-25", viewMode = "day", periodLabel = "오늘",
            modifier = Modifier.padding(16.dp),
            previewData = GoalTimeResponse(
                startDate = "2026-10-25", endDate = "2026-10-25", totalMinutes = 200,
                goals = listOf(
                    GoalTimeEntry(1, "운동", "🏃", "#4BC47C", 120, 3),
                    GoalTimeEntry(2, "영어", "📚", "#5586F0", 60, 2),
                    GoalTimeEntry(null, null, null, null, 20, 1),
                ),
            ),
        )
    }
}
