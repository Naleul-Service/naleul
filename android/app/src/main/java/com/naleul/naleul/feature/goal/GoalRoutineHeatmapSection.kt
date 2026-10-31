// Features/Goal/GoalRoutineHeatmapSection.kt
//
// iOS GoalRoutineHeatmapSection.swift — 웹 features/goal/ui/RoutineHeatmapSection.tsx — 루틴 실천 히트맵.
// 루틴마다 "해야 하는 요일만" 줄로 그려요 (월·수·금 루틴이면 월·수·금 3줄).
// 실천한 날은 색, 지나갔는데 안 한 날은 회색, 아직 안 온 날은 옅게. 모든 루틴이 같은 주(열)를 같이 써요.
// 칸이 수백 개라 줄마다 Canvas 하나로 그려요 (작은 상자를 하나씩 만드는 것보다 가벼워요).

package com.naleul.naleul.feature.goal

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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** 손가락으로 보기 좋게 웹(13)보다 크게 */
private val GOAL_HEAT_CELL = 18.dp
private val GOAL_HEAT_GAP = 4.dp
private val GOAL_HEAT_LABEL = 26.dp

private enum class GoalHeatCellState {
    empty, done, missed, today, future;

    val label: String
        get() = when (this) {
            done -> "실천"
            missed -> "놓침"
            today -> "오늘"
            future -> "예정"
            empty -> ""
        }
}

private data class GoalHeatStat(val due: Int, val completed: Int, val rate: Int?)

/** 칸 색 (Canvas 안에서는 NC 를 못 읽어서 밖에서 꺼내 둬요) */
private data class GoalHeatColors(val brand: Color, val lineStrong: Color, val surface: Color, val subtle: Color)

/** 모든 루틴이 함께 쓰는 주(열) 목록: 가장 이른 시작 주 ~ 가장 늦은 종료 주 (최대 106주) */
private fun goalHeatWeeks(list: List<RoutineHeatmap>, today: String): List<String> {
    val firstStart = list.minOfOrNull { it.repeatStartDate } ?: return emptyList()
    val first = KST.startOfWeek(firstStart)
    var lastEnd = today
    for (r in list) {
        val e = r.repeatEndDate ?: KST.addDays(today, 28)
        if (e > lastEnd) lastEnd = e
    }
    val out = mutableListOf<String>()
    var w = first
    while (w <= lastEnd && out.size < 106) {
        out.add(w)
        w = KST.addDays(w, 7)
    }
    return out
}

/** 이 루틴이 그날 해야 하는 날인지 (기간 안 + 반복 요일) */
private fun goalHeatIsScheduled(r: RoutineHeatmap, ymd: String, dayIndex: Int): Boolean =
    ymd >= r.repeatStartDate && (r.repeatEndDate?.let { ymd <= it } ?: true) &&
        r.repeatDays.contains(JavaDayOfWeek.from(dayIndex))

private fun goalHeatStat(r: RoutineHeatmap, today: String): GoalHeatStat {
    val done = r.completedDates.toSet()
    var due = 0
    var completed = 0
    val rEnd = r.repeatEndDate
    val end = if (rEnd != null && rEnd < today) rEnd else today
    var d = r.repeatStartDate
    // 너무 긴 기간에서 멈추지 않게 안전장치 (약 3년)
    var guardCount = 0
    while (d <= end && guardCount < 1200) {
        if (r.repeatDays.contains(JavaDayOfWeek.of(d))) {
            due += 1
            if (done.contains(d)) completed += 1
        } else if (done.contains(d)) {
            // 반복 요일이 아닌 날에 "다른 날 추가"로 한 날도 실천으로 센다
            due += 1
            completed += 1
        }
        d = KST.addDays(d, 1)
        guardCount += 1
    }
    return GoalHeatStat(due, completed, if (due > 0) (completed.toDouble() / due.toDouble() * 100).roundToInt() else null)
}

private fun goalHeatState(r: RoutineHeatmap, ymd: String, dayIndex: Int, done: Set<String>, today: String): GoalHeatCellState {
    if (done.contains(ymd)) return GoalHeatCellState.done
    if (!goalHeatIsScheduled(r, ymd, dayIndex)) return GoalHeatCellState.empty
    if (done.contains(ymd)) return GoalHeatCellState.done
    if (ymd > today) return GoalHeatCellState.future
    if (ymd == today) return GoalHeatCellState.today
    return GoalHeatCellState.missed
}

/** @param routines null = 불러오는 중 */
@Composable
fun GoalRoutineHeatmapSection(routines: List<RoutineHeatmap>?, modifier: Modifier = Modifier) {
    if (routines == null) {
        SectionCard("루틴 실천", modifier = modifier) {
            Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
        return
    }
    val list = routines.filter { it.repeatDays.isNotEmpty() }.sortedBy { it.repeatStartDate }
    if (list.isEmpty()) return

    val today = KST.today()
    val cols = goalHeatWeeks(list, today)
    val stats = list.map { goalHeatStat(it, today) }
    val allDue = stats.sumOf { it.due }
    val allDone = stats.sumOf { it.completed }
    val aside = if (allDue > 0) "전체 $allDone / ${allDue}회 · ${(allDone.toDouble() / allDue.toDouble() * 100).roundToInt()}%" else ""

    val colors = GoalHeatColors(brand = NC.brand, lineStrong = NC.lineStrong, surface = NC.surface, subtle = NC.subtle)
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    SectionCard("루틴 실천", modifier = modifier, aside = { Text(aside) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "해야 하는 요일만 칸으로 보여줘요. 실천한 날은 색이 채워지고, 놓친 날은 회색이에요.",
                style = pt(13),
                color = NC.ink3,
            )

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val viewport = maxWidth
                LaunchedEffect(cols.size) {
                    // 처음 열면 오늘이 보이게 (오늘 주가 화면 오른쪽 85% 쯤에 오도록)
                    // maxValue 는 레이아웃 전엔 Int.MAX_VALUE 라서, 실제 길이가 정해질 때까지 기다려요
                    val idx = (cols.indexOfFirst { it > today }.let { if (it < 0) cols.size else it }) - 1
                    val col = if (idx < 0) cols.size - 1 else idx
                    if (col >= 0 && col < cols.size) {
                        val max = snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
                        val x = with(density) {
                            (GOAL_HEAT_LABEL + GOAL_HEAT_GAP + (GOAL_HEAT_CELL + GOAL_HEAT_GAP) * col + GOAL_HEAT_CELL - viewport * 0.85f).toPx()
                        }
                        scroll.scrollTo(x.roundToInt().coerceIn(0, maxOf(max, 0)))
                    }
                }
                Column(
                    Modifier.horizontalScroll(scroll).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    GoalHeatMonthRow(cols)
                    list.forEachIndexed { i, r ->
                        key(r.routineId) {
                            GoalHeatRoutineBlock(r, stats[i], cols, today, colors)
                        }
                    }
                }
            }

            // 범례
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in listOf(GoalHeatCellState.done, GoalHeatCellState.missed, GoalHeatCellState.today, GoalHeatCellState.future)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GoalHeatLegendCell(k)
                        Text(k.label, style = pt(12), color = NC.ink3)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalHeatMonthRow(cols: List<String>) {
    Box(
        Modifier
            .padding(start = GOAL_HEAT_LABEL + GOAL_HEAT_GAP)
            .width((GOAL_HEAT_CELL + GOAL_HEAT_GAP) * cols.size)
            .height(14.dp),
    ) {
        cols.forEachIndexed { i, w ->
            val showMonth = i == 0 || KST.monthOf(w) != KST.monthOf(cols[i - 1])
            if (showMonth) {
                Text(
                    "${KST.monthOf(w)}월",
                    style = pt(11),
                    color = NC.ink3,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(x = (GOAL_HEAT_CELL + GOAL_HEAT_GAP) * i),
                )
            }
        }
    }
}

@Composable
private fun GoalHeatRoutineBlock(
    r: RoutineHeatmap,
    s: GoalHeatStat,
    cols: List<String>,
    today: String,
    colors: GoalHeatColors,
) {
    val done = remember(r.completedDates) { r.completedDates.toSet() }
    // 반복 요일 + "다른 날 추가"로 한 날이 있는 요일 (2026-10: 반복 요일이 아닌 날도 히트맵에 보이게)
    val extraDays = remember(r.completedDates) { r.completedDates.map { JavaDayOfWeek.of(it) }.toSet() }
    val rows = JavaDayOfWeek.entries.filter { r.repeatDays.contains(it) || extraDays.contains(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.routineName, style = pt(14, PTWeight.semibold), color = NC.ink)
            Text(
                "${s.completed} / ${s.due}회" + (s.rate?.let { " · $it%" } ?: ""),
                style = ptMono(12),
                color = NC.ink3,
            )
            if (r.currentStreak > 1) {
                val orange = NC.statusWarningFg
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SfImage("flame.fill", size = 11.dp, tint = orange)
                    Text("${r.currentStreak}회 연속", style = pt(12, PTWeight.semibold), color = orange)
                }
            }
        }
        s.rate?.let { rate ->
            ProgressBar(value = rate.toDouble() / 100, height = 4.dp, modifier = Modifier.width(160.dp))
        }
        GoalHeatTimeStatsRow(r.timeStats, colors)
        Column(verticalArrangement = Arrangement.spacedBy(GOAL_HEAT_GAP)) {
            for (d in rows) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GOAL_HEAT_GAP)) {
                    Text(d.label, style = pt(11), color = NC.ink3, modifier = Modifier.width(GOAL_HEAT_LABEL))
                    val states = cols.map { w -> goalHeatState(r, KST.addDays(w, d.index), d.index, done, today) }
                    Canvas(
                        Modifier
                            .width((GOAL_HEAT_CELL + GOAL_HEAT_GAP) * cols.size - GOAL_HEAT_GAP)
                            .height(GOAL_HEAT_CELL),
                    ) {
                        val cell = GOAL_HEAT_CELL.toPx()
                        val step = (GOAL_HEAT_CELL + GOAL_HEAT_GAP).toPx()
                        val radius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx())
                        states.forEachIndexed { i, st ->
                            val tl = Offset(i * step, 0f)
                            val sz = Size(cell, cell)
                            when (st) {
                                GoalHeatCellState.empty -> Unit
                                GoalHeatCellState.done -> drawRoundRect(colors.brand, tl, sz, radius)
                                GoalHeatCellState.missed -> drawRoundRect(colors.lineStrong, tl, sz, radius)
                                GoalHeatCellState.future -> drawRoundRect(colors.subtle, tl, sz, radius)
                                GoalHeatCellState.today -> {
                                    val sw = 1.5.dp.toPx()
                                    drawRoundRect(colors.surface, tl, sz, radius)
                                    // strokeBorder 처럼 안쪽으로 테두리
                                    drawRoundRect(
                                        color = colors.brand,
                                        topLeft = Offset(tl.x + sw / 2, sw / 2),
                                        size = Size(cell - sw, cell - sw),
                                        cornerRadius = radius,
                                        style = Stroke(width = sw),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 주로 실천한 시간대: "주로 21:00~23:00에 실천 · 12회 중 9회" + 0~23시 작은 막대 */
@Composable
private fun GoalHeatTimeStatsRow(stats: RoutineTimeStats?, colors: GoalHeatColors) {
    if (stats == null || !stats.hasData) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SfImage("clock", size = 11.dp, tint = NC.ink4)
            Text("아직 시간 기록이 없어요", style = pt(12), color = NC.ink4)
        }
        return
    }
    val hourly = if (stats.hourly.size == 24) stats.hourly else List(24) { 0 }
    val peak = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    val startHour = stats.bestStart?.take(2)?.toIntOrNull() ?: -1
    val endHm = stats.bestEnd ?: ""
    val endHour = endHm.take(2).toIntOrNull()?.let { h -> if (endHm.endsWith(":00")) h - 1 else h } ?: -1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SfImage("clock", size = 11.dp, tint = NC.ink3)
            Text(
                "${stats.practiceSentence ?: "주로 ${stats.bestStart}~${stats.bestEnd}에 실천했어요"} · ${stats.practiceCount}회 기록",
                style = pt(12, PTWeight.medium),
                color = NC.ink2,
            )
        }
        Canvas(
            Modifier
                .width((24 * 5).dp)
                .height(18.dp)
                .semantics { contentDescription = "시간대별 실천 횟수" },
        ) {
            val step = size.width / 24f
            val barW = step * 0.7f
            hourly.forEachIndexed { h, v ->
                val inWindow = if (startHour <= endHour) h in startHour..endHour else (h >= startHour || h <= endHour)
                val bh = if (v <= 0) 1.dp.toPx() else size.height * (v.toFloat() / peak.toFloat())
                drawRoundRect(
                    color = if (inWindow && v > 0) colors.brand else colors.lineStrong,
                    topLeft = Offset(h * step, size.height - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }
        }
    }
}

/** 범례용 작은 칸 (11dp) */
@Composable
private fun GoalHeatLegendCell(s: GoalHeatCellState) {
    val shape = RoundedCornerShape(3.dp)
    val m = Modifier.size(11.dp)
    when (s) {
        GoalHeatCellState.empty -> Spacer(m)
        GoalHeatCellState.done -> Box(m.background(NC.brand, shape))
        GoalHeatCellState.missed -> Box(m.background(NC.lineStrong, shape))
        GoalHeatCellState.today -> Box(m.background(NC.surface, shape).border(1.5.dp, NC.brand, shape))
        GoalHeatCellState.future -> Box(m.background(NC.subtle, shape))
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun GoalRoutineHeatmapSectionPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalRoutineHeatmapSection(routines = GoalPreviewData.heatmap)
            GoalRoutineHeatmapSection(routines = null)
        }
    }
}
