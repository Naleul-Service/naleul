// feature/goal/GoalProgressSection.kt
//
// iOS Features/Goal/GoalProgressSection.swift
// 웹 features/goal/ui/progress/ProgressSection.tsx — 목표 상세 "지금 어디쯤?"
//
// - 수치 목표(체중 72 → 69kg 등): 시작 → 목표 막대 위 현재 위치 + 계획선 대비 기록 꺾은선 + 지난 기록 목록
// - 수치가 없는 목표: 오늘까지 해야 했던 Task 대비 완료한 Task 누적 그래프
// 오늘 기록은 위 "오늘 기록" 카드에서 해요. 여기서는 지난 기록을 고칠 때만 시트를 열어요.

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun GoalProgressSection(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    val p = vm.progress
    if (!vm.progressLoaded) {
        SectionCard(title = "지금 어디쯤?", modifier = modifier) {
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp)
            }
        }
    } else if (p != null) {
        val m = p.metric
        if (m != null) {
            SectionCard(title = "지금 어디쯤?", modifier = modifier, aside = { GoalProgressStatusChip(status = m.status) }) {
                GoalMetricProgressView(p = p, m = m, vm = vm)
            }
        } else {
            SectionCard(title = "지금 어디쯤?", modifier = modifier) {
                GoalTaskProgressView(p = p, vm = vm)
            }
        }
    }
}

// MARK: - 상태 칩

@Composable
fun GoalProgressStatusChip(status: ProgressStatus, modifier: Modifier = Modifier) {
    val (label, fg, bg) = when (status) {
        ProgressStatus.ACHIEVED -> Triple("목표 달성 🎉", NC.success, NC.successSoft)
        ProgressStatus.AHEAD -> Triple("계획보다 앞서요", NC.success, NC.successSoft)
        ProgressStatus.ON_TRACK -> Triple("계획대로 가는 중", NC.brand, NC.brandSoft)
        ProgressStatus.BEHIND -> Triple("계획보다 조금 늦어요", NC.warning, NC.warningSoft)
        ProgressStatus.NOT_STARTED -> Triple("시작 전", NC.ink3, NC.subtle)
        ProgressStatus.NO_PLAN -> Triple("종료일 없음", NC.ink3, NC.subtle)
    }
    Text(
        label,
        style = pt(12, PTWeight.semibold),
        color = fg,
        maxLines = 1,
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// MARK: - 수치 목표

@Composable
private fun GoalMetricProgressView(p: GoalProgress, m: MetricProgress, vm: GoalDetailViewModel) {
    var showAll by remember { mutableStateOf(false) }
    Column {
        // 수치 마감일 · 수치 수정 (목표 수정과 따로, 2026-10)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val td = m.targetDate
            Text(
                if (td != null) "${KST.formatMonthDay(td)}까지 ${GoalDetailLogic.fmtNum(m.targetValue)}${m.unit ?: ""}" else "마감 없이 ${GoalDetailLogic.fmtNum(m.targetValue)}${m.unit ?: ""}",
                style = pt(13, PTWeight.medium),
                color = NC.ink2,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clickable(role = Role.Button) {
                        vm.sheet = GoalDetailSheet.editMetric("progress_section")
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SfImage("pencil", size = 12.dp, tint = NC.textBrand)
                Text("수치 수정", style = pt(13, PTWeight.semibold), color = NC.textBrand)
            }
        }
        GoalMetricTrackBar(m = m)
        GoalMetricInsight(p = p, m = m)
        Box(Modifier.padding(top = 20.dp)) {
            GoalMetricChart(p = p, m = m, height = 260, onInteract = { vm.trackChartInteract("main_metric") })
        }
        Box(Modifier.padding(top = 8.dp)) {
            GoalChartLegend(items = listOf("계획" to GoalChartSeries.Kind.plan, "내 기록" to GoalChartSeries.Kind.actual))
        }
        GoalMetricRecentLogs(p = p, m = m, vm = vm, showAll = showAll, onShowAllChange = { showAll = it })
        GoalTaskProgressLine(t = p.tasks)
    }
}

/** 시작 → 목표 사이 위치 (0~1) */
private fun goalMetricPos(m: MetricProgress, v: Double): Double {
    val span = m.targetValue - m.startValue
    if (span == 0.0) return 1.0
    return ((v - m.startValue) / span).coerceIn(0.0, 1.0)
}

// ── 시작 → 목표 막대 위의 현재 위치 ──

@Composable
private fun GoalMetricTrackBar(m: MetricProgress) {
    val L = GoalDetailLogic
    val now = goalMetricPos(m, m.currentValue)
    val expected = m.expectedValue?.let { goalMetricPos(m, it) }
    val percent = m.progressPercent ?: 0.0
    val ink = NC.ink
    val ink3 = NC.ink3
    val success = NC.success
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ink3)) { append("시작 ") }
                    withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append(L.withUnit(m.startValue, m.unit)) }
                },
                style = pt(13),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${percent.coerceIn(0.0, 999.0).roundToInt()}", style = ptMono(28, PTWeight.bold), color = NC.ink)
                Text("%", style = pt(15, PTWeight.semibold), color = NC.ink3, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ink3)) { append("목표 ") }
                    withStyle(SpanStyle(color = success, fontWeight = FontWeight.Bold)) { append(L.withUnit(m.targetValue, m.unit)) }
                },
                style = pt(13),
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(NC.subtle, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(w * now.toFloat())
                    .height(12.dp)
                    .background(NC.brand, CircleShape),
            )
            if (expected != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = w * expected.toFloat() - 1.dp)
                        .size(width = 2.dp, height = 20.dp)
                        .background(NC.ink2, RoundedCornerShape(1.dp)),
                )
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * now.toFloat() - 10.dp)
                    .size(20.dp)
                    .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
                    .background(NC.brand, CircleShape)
                    .border(3.dp, NC.surface, CircleShape),
            )
        }

        FlowLayout(spacing = 12.dp) {
            Text(
                buildAnnotatedString {
                    append("지금 ")
                    withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append(L.withUnit(m.currentValue, m.unit)) }
                    if (m.status != ProgressStatus.ACHIEVED) {
                        append(" · 목표까지 ${L.withUnit(abs(m.targetValue - m.currentValue), m.unit)} 남음")
                    }
                },
                style = pt(12),
                color = NC.ink3,
            )
            val e = m.expectedValue
            if (e != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).background(NC.ink2, RoundedCornerShape(1.dp)))
                    Text("오늘 계획 ${L.withUnit1(e, m.unit)}", style = pt(12), color = NC.ink3)
                }
            }
        }
    }
}

// ── "오늘 계획보다 0.3kg 늦어요 · 이 속도면 10월 16일 도달" ──

private fun goalMetricInsightLines(p: GoalProgress, m: MetricProgress): List<String> {
    val L = GoalDetailLogic
    val lines = mutableListOf<String>()
    val dir = if (m.targetValue - m.startValue >= 0) 1.0 else -1.0
    val e = m.expectedValue
    if (m.status == ProgressStatus.ACHIEVED) {
        lines.add("목표 ${L.withUnit(m.targetValue, m.unit)}에 도달했어요. 정말 잘했어요!")
    } else if (e != null && (m.status == ProgressStatus.AHEAD || m.status == ProgressStatus.BEHIND || m.status == ProgressStatus.ON_TRACK)) {
        val gap = (m.currentValue - e) * dir
        val g = L.withUnit1(abs(gap), m.unit)
        when (m.status) {
            ProgressStatus.AHEAD -> lines.add("오늘 계획보다 $g 앞서 있어요.")
            ProgressStatus.BEHIND -> lines.add("오늘 계획보다 $g 뒤처져 있어요. 조금만 더 힘내요!")
            else -> lines.add("계획한 속도에 맞춰 잘 가고 있어요.")
        }
    }
    val projected = m.projectedDate
    if (projected != null && m.status != ProgressStatus.ACHIEVED) {
        lines.add("최근 기록 추세라면 ${L.md(projected)}쯤 목표에 닿아요${goalProjectedTail(p.endDate, projected)}.")
    }
    return lines
}

/** " (종료일보다 3일 늦어요)" 같은 꼬리말 */
fun goalProjectedTail(end: String?, projected: String): String {
    if (end == null) return ""
    val diff = KST.diffDays(end, projected)
    return when {
        diff > 0 -> " (종료일보다 ${diff}일 늦어요)"
        diff < 0 -> " (종료일보다 ${-diff}일 빨라요)"
        else -> " (종료일에 딱 맞아요)"
    }
}

@Composable
private fun GoalMetricInsight(p: GoalProgress, m: MetricProgress) {
    val lines = goalMetricInsightLines(p, m)
    if (lines.isEmpty()) return
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(NC.subtle, RoundedCornerShape(Radius.xxl))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (line in lines) Text(line, style = pt(13).copy(lineHeight = 19.sp), color = NC.ink)
    }
}

// ── 그래프 ──

/** 수치 그래프 (추가 그래프에서도 같은 방식으로 그려요) */
@Composable
fun GoalMetricChart(p: GoalProgress, m: MetricProgress, height: Int = 260, onInteract: (() -> Unit)? = null) {
    val L = GoalDetailLogic
    val from = p.startDate ?: m.logs.firstOrNull()?.date ?: p.today
    val candidates = listOfNotNull(p.endDate, m.logs.lastOrNull()?.date, p.today, m.planLine.lastOrNull()?.date)
    var to = candidates.maxOrNull() ?: p.today
    if (p.endDate == null) to = KST.addDays(to, 7)
    if (to <= from) to = KST.addDays(from, 7)

    val actual = m.logs.map { GoalChartPoint(date = it.date, value = it.value) }.toMutableList()
    val s = p.startDate
    if (s != null && m.logs.none { it.date <= s }) {
        actual.add(0, GoalChartPoint(date = s, value = m.startValue))
    }
    val series = listOf(
        GoalChartSeries(
            key = "plan",
            name = "계획",
            kind = GoalChartSeries.Kind.plan,
            points = m.planLine.map { GoalChartPoint(date = it.date, value = it.value, label = it.label) },
        ),
        GoalChartSeries(key = "actual", name = "기록", kind = GoalChartSeries.Kind.actual, points = actual),
    )
    val currentDate = m.lastLoggedDate ?: (if (s != null && s < p.today) s else p.today)
    val unit = m.unit

    GoalProgressChart(
        series = series,
        from = from,
        to = to,
        today = p.today,
        target = GoalChartMarker(value = m.targetValue, label = "목표 ${L.withUnit(m.targetValue, unit)}"),
        current = GoalChartMarker(date = currentDate, value = m.currentValue, label = "지금 ${L.withUnit(m.currentValue, unit)}"),
        format = { L.withUnit(it, unit) },
        height = height.dp,
        onInteract = onInteract,
    )
}

// ── 지난 기록 ──

@Composable
private fun GoalMetricRecentLogs(
    p: GoalProgress,
    m: MetricProgress,
    vm: GoalDetailViewModel,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
) {
    if (m.logs.isEmpty()) return
    val rows = m.logs.reversed()
    val shown = if (showAll) rows else rows.take(5)
    // 같은 날짜가 여러 개면 처음 것 (iOS uniquingKeysWith: { a, _ in a })
    val notes: Map<String, GoalReflection> = buildMap {
        for (r in p.reflections ?: emptyList()) if (!containsKey(r.date)) put(r.date, r)
    }
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("기록", style = pt(12, PTWeight.semibold), color = NC.ink3)
        Column {
            shown.forEachIndexed { i, l ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                GoalMetricLogRow(l = l, note = notes[l.date], m = m, vm = vm)
            }
        }
        if (rows.size > 5) {
            Text(
                if (showAll) "접기" else "기록 ${rows.size - 5}개 더 보기",
                style = pt(13, PTWeight.medium),
                color = NC.ink3,
                modifier = Modifier.clickable(role = Role.Button) {
                    onShowAllChange(!showAll)
                    vm.track("goal_detail.metric_logs_more_toggle", mapOf("expanded" to !showAll, "log_count" to rows.size))
                },
            )
        }
    }
}

@Composable
private fun GoalMetricLogRow(l: MetricLog, note: GoalReflection?, m: MetricProgress, vm: GoalDetailViewModel) {
    val L = GoalDetailLogic
    val dir = if (m.targetValue - m.startValue >= 0) 1.0 else -1.0
    val idx = m.logs.indexOfFirst { it.date == l.date }.coerceAtLeast(0)
    val prev = if (idx > 0) m.logs[idx - 1].value else m.startValue
    val delta = l.value - prev
    val good = delta * dir > 0
    val mood = GoalMoodOption.of(note?.mood)
    val memo = note?.note ?: l.memo ?: ""
    val noteText = (mood?.let { it.emoji + " " } ?: "") + memo

    Row(
        Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(L.md(l.date), style = pt(13), color = NC.ink3, modifier = Modifier.width(64.dp))
        Text(L.withUnit(l.value, m.unit), style = ptMono(14, PTWeight.bold), color = NC.ink)
        if (delta != 0.0) {
            val c = if (good) NC.success else NC.warning
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                SfImage(if (delta < 0) "arrow.down.right" else "arrow.up.right", size = 10.dp, tint = c)
                Text(L.fmtNum(abs(delta)), style = pt(12, PTWeight.semibold), color = c)
            }
        }
        Text(
            noteText,
            style = pt(13),
            color = NC.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        GoalRowActions(
            label = "${L.md(l.date)} 기록",
            onEdit = {
                vm.track("goal_detail.metric_log_edit_click", mapOf("days_ago" to GD.daysAgo(l.date)))
                vm.sheet = GoalDetailSheet.metricLog(date = l.date, value = l.value)
            },
            onDelete = { vm.ask(GoalDetailConfirm.deleteMetricLog(date = l.date, value = l.value, unit = m.unit)) },
        )
    }
}

/** 수치 목표 아래 한 줄: Task 실행 현황 */
@Composable
private fun GoalTaskProgressLine(t: TaskProgress) {
    if (t.total <= 0) return
    val ink = NC.ink
    Column(Modifier.padding(top = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).height(1.dp).background(NC.line))
        Text(
            buildAnnotatedString {
                append("관련 Task ")
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append("${t.completed}") }
                append(" / ${t.total}개 완료")
                val c = t.completionPercent
                if (t.dueByToday > 0 && c != null) {
                    append(" · 오늘까지 할 일 중 ")
                    withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append("${c.roundToInt()}%") }
                    append(" 해냈어요")
                }
            },
            style = pt(13),
            color = NC.ink3,
        )
    }
}

// MARK: - 수치 없는 목표: Task 누적

@Composable
private fun GoalTaskProgressView(p: GoalProgress, vm: GoalDetailViewModel) {
    val t = p.tasks
    val behind = maxOf(t.dueByToday - t.completed, 0)
    val ratio = if (t.dueByToday == 0) 0.0 else minOf(t.completed.toDouble() / t.dueByToday.toDouble(), 1.0)
    val ink = NC.ink
    val brand = NC.brand

    Column {
        if (t.dueByToday == 0) {
            Text(
                if (t.total == 0) "아직 이 목표에 Task가 없어요." else "오늘까지 해야 할 Task는 아직 없어요.",
                style = pt(14),
                color = NC.ink,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append("오늘까지 할 Task ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${t.dueByToday}개") }
                        append(" 중 ")
                        withStyle(SpanStyle(color = brand, fontWeight = FontWeight.Bold)) { append("${t.completed}개") }
                        append(" 완료")
                    },
                    style = pt(14),
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("${(ratio * 100).roundToInt()}", style = ptMono(28, PTWeight.bold), color = NC.ink)
                    Text("%", style = pt(15, PTWeight.semibold), color = NC.ink3, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            ProgressBar(value = ratio, height = 6.dp, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (behind == 0) "밀린 Task 없이 계획대로 가고 있어요." else "밀린 Task ${behind}개 — 하나씩 끝내면 그래프가 따라잡아요.",
                style = pt(12),
                color = NC.ink3,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val from = t.series.firstOrNull()?.date
        val to = t.series.lastOrNull()?.date
        if (from != null && to != null && t.total > 0) {
            Box(Modifier.padding(top = 20.dp)) { GoalTaskProgressChart(p = p, from = from, to = to) }
            Box(Modifier.padding(top = 8.dp)) {
                GoalChartLegend(
                    items = listOf("해야 할 Task (누적)" to GoalChartSeries.Kind.plan, "완료한 Task (누적)" to GoalChartSeries.Kind.actual),
                )
            }
        }

        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .background(NC.subtle, RoundedCornerShape(Radius.xxl))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "몸무게·점수·권수처럼 숫자로 잴 수 있는 목표라면, 목표 수치를 정하고 매일 기록해 보세요. 목표까지 남은 거리를 그래프로 보여드려요.",
                style = pt(13),
                color = NC.ink2,
                modifier = Modifier.weight(1f),
            )
            NButton("목표 수치 정하기", variant = NButtonVariant.secondary, size = NButtonSize.sm) {
                vm.track("goal_detail.edit_open", mapOf("goal_mode" to vm.goal?.analyticsMode, "entry_point" to "progress_cta"))
                vm.sheet = GoalDetailSheet.editGoal
            }
        }
    }
}

@Composable
private fun GoalTaskProgressChart(p: GoalProgress, from: String, to: String) {
    val t = p.tasks
    val series = listOf(
        GoalChartSeries(
            key = "plan",
            name = "해야 할 Task",
            kind = GoalChartSeries.Kind.plan,
            points = t.series.map { GoalChartPoint(date = it.date, value = it.planned.toDouble()) },
        ),
        GoalChartSeries(
            key = "actual",
            name = "완료한 Task",
            kind = GoalChartSeries.Kind.actual,
            points = t.series.mapNotNull { s -> s.completed?.let { GoalChartPoint(date = s.date, value = it.toDouble()) } },
        ),
    )
    val chartVm = LocalGoalDetailViewModel.current
    val todayPoint = t.series.firstOrNull { it.date == p.today }
    val current = todayPoint?.let {
        GoalChartMarker(date = p.today, value = (it.completed ?: 0).toDouble(), label = "완료 ${t.completed}개")
    }
    GoalProgressChart(
        series = series,
        from = from,
        to = to,
        today = p.today,
        current = current,
        format = { "${GoalDetailLogic.fmtNum(it)}개" },
        height = 200.dp,
        zeroBased = true,
        onInteract = { chartVm?.trackChartInteract("task_cumulative") },
    )
}

// MARK: - 지난 수치 기록 고치기 시트 (웹 CheckInPrefilled)

@Composable
fun GoalMetricLogEditSheet(
    goalId: Int,
    metric: MetricProgress,
    today: String,
    date: String,
    value: Double,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val initialDate = date
    var pickedDate by remember { mutableStateOf(date) }
    var text by remember { mutableStateOf(GoalDetailLogic.fmtNum(value)) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { vm.track("goal_detail.metric_log_edit_cancel", mapOf("close_method" to it)) }
        }
    }

    val num: Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun submit(close: SheetClose) {
        val v = num ?: return
        if (saving) return
        val d = pickedDate
        vm.track("goal_detail.metric_log_edit_save", mapOf("days_ago" to GD.daysAgo(initialDate), "date_changed" to (d != initialDate)))
        saving = true
        scope.launch {
            val r = Mutation.run(
                invalidate = listOf(DataKey.goals),
                successMessage = "기록했어요.",
                errorFallback = "기록하지 못했어요. 다시 시도해 주세요.",
            ) {
                // 응답이 비어도 성공인지 알 수 있게 Pair 로 감싸요
                Pair(GoalEditAPI.recordMetric(goalId = goalId, date = d, value = v), true)
            }
            saving = false
            vm.track("goal_detail.metric_result", GD.result() + mapOf("metric_action" to "record"))
            if (r != null) {
                outcome = "saved"
                r.first?.let { vm.progress = it }
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "${GoalDetailLogic.md(pickedDate)} 기록 고치기",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                saving = saving,
                valid = num != null,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close) },
            ) {
                GoalFormField("기록 날짜") {
                    GoalDateInput(
                        value = pickedDate,
                        onValueChange = { pickedDate = if (it.isEmpty()) initialDate else it },
                        maxDate = today,
                    )
                }
                GoalFormField("${metric.name ?: "수치"} 값") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        NTextField(
                            value = text,
                            onValueChange = { text = it },
                            keyboardType = KeyboardType.Decimal,
                            textStyle = pt(15, PTWeight.semibold),
                            modifier = Modifier.weight(1f),
                        )
                        val u = metric.unit
                        if (!u.isNullOrEmpty()) Text(u, style = pt(13), color = NC.ink3)
                    }
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1100, name = "수치 목표")
@Composable
private fun GoalProgressSectionPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
                GoalProgressSection(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Task 누적")
@Composable
private fun GoalProgressSectionTaskPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview(progress = GoalPreviewData.taskOnlyProgress) }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
                GoalProgressSection(goal = GoalPreviewData.goal)
            }
        }
    }
}
