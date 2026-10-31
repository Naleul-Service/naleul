// feature/goal/GoalProgressOverview.kt
//
// 스펙 13.7: 목표형 "진행" 탭 — 칩(전체 · 지금 어디쯤? · Task 실천률 · 루틴 실천) + 전체 = 주별 합친 그래프
package com.naleul.naleul.feature.goal

import com.naleul.naleul.app.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.calendar.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 진행 탭 칩 — analytics 는 goal_detail.progress_view_change 의 view 값 */
internal enum class GoalProgressView(val analytics: String, val title: String) {
    all("all", "전체"),
    position("position", "지금 어디쯤?"),
    taskRate("task_rate", "Task 실천률"),
    routineRate("routine_rate", "루틴 실천"),
}

/** 칩 한 줄 (Chip SM · 하나만 고르기). 바뀌면 goal_detail.progress_view_change */
@Composable
internal fun GoalProgressViewChips(
    views: List<GoalProgressView>,
    selected: GoalProgressView,
    goal: GoalCategory,
    vm: GoalDetailViewModel,
    onSelect: (GoalProgressView) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (v in views) {
            NChip(v.title, selected = v == selected, size = ChipSize.sm) {
                if (v != selected) {
                    vm.track(
                        "goal_detail.progress_view_change",
                        mapOf("view" to v.analytics, "from_view" to selected.analytics, "goal_mode" to goal.analyticsMode),
                    )
                    onSelect(v)
                }
            }
        }
    }
}

/** 주(월요일 시작) 하나의 세 값 (0~100, 없으면 null) */
internal data class GoalWeekPoint(val week: String, val position: Double?, val task: Double?, val routine: Double?) {
    val label: String get() = "${KST.monthOf(week)}/${KST.dayOfMonth(week)}"
}

/** 합친 그래프 데이터 — 목표 시작 주(없으면 데이터 첫 주)부터 이번 주까지, 최근 12주 (iOS · 웹과 같게) */
internal fun goalCombinedWeekly(
    progress: GoalProgress?,
    tasks: List<TimeBlockTask>,
    routines: List<RoutineHeatmap>,
    today: String,
): List<GoalWeekPoint> {
    val thisWeek = KST.startOfWeek(today)
    val taskBars = goalTaskRateWeekly(tasks, today).associateBy { it.week }
    val starts = listOfNotNull(
        progress?.startDate,
        taskBars.keys.minOrNull(),
        routines.minOfOrNull { it.repeatStartDate },
    ).filter { it <= today }
    val first = KST.startOfWeek(starts.minOrNull() ?: today)
    val weeks = mutableListOf<String>()
    var w = first
    while (w <= thisWeek) { weeks.add(w); w = KST.addDays(w, 7) }
    val recent = weeks.takeLast(12)
    val m = progress?.metric
    val logs = m?.logs?.sortedBy { it.date }.orEmpty()
    val series = progress?.tasks?.series?.sortedBy { it.date }.orEmpty()
    val total = progress?.tasks?.total ?: 0
    return recent.map { wk ->
        val end = minOf(KST.addDays(wk, 6), today)
        // 지금 어디쯤: 수치 목표면 (그 주 끝 값 - 시작)/(목표 - 시작), 아니면 누적 완료 Task / 전체
        val position: Double? = if (m != null) {
            val span = m.targetValue - m.startValue
            val v = logs.lastOrNull { it.date <= end }?.value ?: if (wk == thisWeek) m.currentValue else null
            v?.let { if (span == 0.0) 100.0 else ((it - m.startValue) / span * 100).coerceIn(0.0, 100.0) }
        } else if (total > 0) {
            series.lastOrNull { it.date <= end && it.completed != null }?.completed?.let { (it.toDouble() / total * 100).coerceIn(0.0, 100.0) }
        } else null
        val tb = taskBars[wk]
        val task = tb?.takeIf { it.due > 0 }?.let { it.rate * 100 }
        // 루틴: 그 주에 하기로 한 날 (반복 요일 · 기간 · 오늘까지) 중 완료한 날
        var due = 0
        var done = 0
        for (r in routines) {
            val doneSet = r.completedDates.toHashSet()
            for (i in 0 until 7) {
                val d = KST.addDays(wk, i)
                if (d > today || d < r.repeatStartDate) continue
                val endDate = r.repeatEndDate
                if (endDate != null && d > endDate) continue
                if (!r.repeatDays.contains(JavaDayOfWeek.of(d))) continue
                due++
                if (doneSet.contains(d)) done++
            }
        }
        val routine = if (due > 0) done.toDouble() / due * 100 else null
        GoalWeekPoint(wk, position, task, routine)
    }
}

/** 전체: 지금 어디쯤 · Task 실천률 · 루틴 실천을 같은 주 축에 (범례 포함) */
@Composable
internal fun GoalCombinedProgressCard(goal: GoalCategory, vm: GoalDetailViewModel, modifier: Modifier = Modifier) {
    val today = KST.today()
    val points = remember(vm.progress, vm.tasks, vm.heatmap, today) {
        goalCombinedWeekly(vm.progress, vm.tasks?.tasks.orEmpty(), vm.heatmap.orEmpty(), today)
    }
    val positionColor = NC.actionPrimary
    val taskColor = goalTone(goal.colorCode).accent
    val routineColor = NC.statusSuccessFg
    val grid = NC.borderSoft
    val hasPosition = points.any { it.position != null }
    val hasTask = points.any { it.task != null }
    val hasRoutine = points.any { it.routine != null }
    SectionCard(title = "한눈에 보기", modifier = modifier) {
        if (!vm.progressLoaded) {
            LoadingView(minHeight = 160.dp)
            return@SectionCard
        }
        if (!hasPosition && !hasTask && !hasRoutine) {
            Text("기록이 쌓이면 주마다 어떻게 했는지 보여드려요", style = NType.bodySM, color = NC.textSecondary)
            return@SectionCard
        }
        // 범례
        FlowLayout(spacing = 12.dp) {
            if (hasPosition) GoalLegendItem(if (goal.startValue != null && goal.targetValue != null) "지금 어디쯤?" else "Task 누적 완료", positionColor)
            if (hasTask) GoalLegendItem("Task 실천률", taskColor)
            if (hasRoutine) GoalLegendItem("루틴 실천", routineColor)
        }
        Column {
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            Column(Modifier.width(24.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                for (t in listOf("100", "50", "0")) Text(t, style = NType.captionSM, color = NC.textTertiary)
            }
            Canvas(Modifier.padding(start = 6.dp).weight(1f).fillMaxHeight().padding(vertical = 6.dp)) {
                val n = points.size
                // 칸 가운데에 점 (아래 주 이름과 맞게)
                fun x(i: Int) = size.width * (i + 0.5f) / n
                fun y(v: Double) = size.height * (1f - (v / 100.0).toFloat())
                for (g in listOf(0.0, 50.0, 100.0)) {
                    drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1.dp.toPx(),
                        pathEffect = if (g == 50.0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null)
                }
                fun line(values: List<Double?>, c: Color) {
                    val path = Path()
                    var started = false
                    values.forEachIndexed { i, v ->
                        if (v == null) { started = false; return@forEachIndexed }
                        if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                        started = true
                    }
                    drawPath(path, c, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    values.forEachIndexed { i, v -> if (v != null) drawCircle(c, radius = 3.dp.toPx(), center = Offset(x(i), y(v))) }
                }
                if (hasPosition) line(points.map { it.position }, positionColor)
                if (hasTask) line(points.map { it.task }, taskColor)
                if (hasRoutine) line(points.map { it.routine }, routineColor)
            }
        }
        // 주 축
        Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 6.dp)) {
            points.forEachIndexed { i, p ->
                Text(
                    if (i == points.lastIndex) "이번 주" else p.label,
                    style = NType.captionSM, color = NC.textTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        }
        // 아래 한 줄 요약 (이번 주 값)
        val last = points.lastOrNull()
        val summary = listOfNotNull(
            last?.position?.let { "지금 ${it.toInt()}%" },
            last?.task?.let { "이번 주 Task ${it.toInt()}%" },
            last?.routine?.let { "루틴 ${it.toInt()}%" },
        ).joinToString(" · ")
        if (summary.isNotEmpty()) {
            Text(summary, style = NType.bodySM, color = NC.textSecondary)
        }
    }
}

@Composable
private fun GoalLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = NType.captionMD, color = NC.textSecondary)
    }
}
