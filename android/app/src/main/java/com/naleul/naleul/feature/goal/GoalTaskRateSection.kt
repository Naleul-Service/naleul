// Features/Goal/GoalTaskRateSection.kt
//
// iOS GoalTaskRateSection.swift — 웹 features/goal/ui/TaskRateSection.tsx — Task 실천률.
// 실제로 만든 Task 중 날짜가 지난 것을 얼마나 해냈는지 주별 막대로 (iOS 는 Swift Charts, 여기선 상자를 직접 쌓아 그려요).
// 루틴은 위 히트맵에서 따로 보니까, 기본은 일회성 Task 만 (원하면 루틴 포함).

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private enum class GoalTaskRateKind { oneTime, all }

internal data class GoalTaskRateWeekBar(
    val week: String,
    val due: Int,
    val done: Int,
    /** 이번 주처럼 아직 진행 중인 주 */
    val current: Boolean,
) {
    val rate: Double get() = if (due > 0) done.toDouble() / due.toDouble() else 0.0
    val label: String get() = if (current) "이번 주" else "${KST.monthOf(week)}/${KST.dayOfMonth(week)}"
}

/** 주(월요일 시작)마다: 그 주에 하기로 했던 Task 중 오늘까지 날짜가 온 것 → 몇 개 실천했나 */
internal fun goalTaskRateWeekly(list: List<TimeBlockTask>, today: String): List<GoalTaskRateWeekBar> {
    val due = mutableMapOf<String, Int>()
    val done = mutableMapOf<String, Int>()
    for (t in list) {
        // 아직 안 온 날의 Task 는 실천률에 넣지 않아요
        val day = GoalDetailLogic.dayOf(t) ?: continue
        if (day > today) continue
        val w = KST.startOfWeek(day)
        due[w] = (due[w] ?: 0) + 1
        if (GoalDetailLogic.isDone(t)) done[w] = (done[w] ?: 0) + 1
    }
    val first = due.keys.sorted().firstOrNull() ?: return emptyList()
    val thisWeek = KST.startOfWeek(today)
    val bars = mutableListOf<GoalTaskRateWeekBar>()
    var w = first
    while (w <= thisWeek) {
        bars.add(GoalTaskRateWeekBar(week = w, due = due[w] ?: 0, done = done[w] ?: 0, current = w == thisWeek))
        w = KST.addDays(w, 7)
    }
    return bars
}

/** 한 화면에 보이는 주 수 — 넘으면 좌우로 밀어서 지난 주를 봐요 */
private const val GOAL_TASK_RATE_VISIBLE_WEEKS = 6

@Composable
fun GoalTaskRateSection(tasks: List<TimeBlockTask>, modifier: Modifier = Modifier) {
    if (tasks.isEmpty()) return
    var kind by remember { mutableStateOf(GoalTaskRateKind.oneTime) }
    val vm = LocalGoalDetailViewModel.current
    fun pickKind(k: GoalTaskRateKind) {
        if (k == kind) return
        kind = k
        vm?.track("goal_detail.task_rate_kind_select", mapOf("task_kind" to if (k == GoalTaskRateKind.all) "ALL" else "ONE_TIME"))
    }

    val today = KST.today()
    val hasRoutine = tasks.any { it.sourceType == TaskSourceType.ROUTINE }
    val list = if (kind == GoalTaskRateKind.all) tasks else tasks.filter { it.sourceType != TaskSourceType.ROUTINE }
    val bars = goalTaskRateWeekly(list, today)
    val due = bars.sumOf { it.due }
    val done = bars.sumOf { it.done }
    val rate: Int? = if (due > 0) (done.toDouble() / due.toDouble() * 100).roundToInt() else null
    val recent = bars.takeLast(12)

    SectionCard("Task 실천률", modifier = modifier, aside = { Text(if (rate != null) "$done / ${due}개" else "") }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("지금까지 하기로 한 Task 중", style = pt(13), color = NC.ink3)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(rate?.toString() ?: "-", style = ptMono(32, PTWeight.bold), color = NC.ink, modifier = Modifier.alignByBaseline())
                        Text("%", style = pt(16, PTWeight.semibold), color = NC.ink3, modifier = Modifier.alignByBaseline())
                        Text(
                            "실천했어요",
                            style = pt(14, PTWeight.medium),
                            color = NC.ink2,
                            modifier = Modifier.alignByBaseline().padding(start = 6.dp),
                        )
                    }
                }
                if (hasRoutine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NChip("할 일만", selected = kind == GoalTaskRateKind.oneTime, size = ChipSize.sm) { pickKind(GoalTaskRateKind.oneTime) }
                        NChip("루틴 포함", selected = kind == GoalTaskRateKind.all, size = ChipSize.sm) { pickKind(GoalTaskRateKind.all) }
                    }
                }
            }

            if (recent.isEmpty()) {
                Text(
                    "아직 날짜가 지난 ${if (kind == GoalTaskRateKind.all) "Task" else "할 일"}이 없어요. 날짜가 지나면 여기서 실천률을 보여드려요.",
                    style = pt(14),
                    color = NC.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                GoalTaskRateChart(recent)
                Text(
                    "주마다 그 주에 하기로 한 Task 중 몇 개를 실천했는지예요. 오늘 이후 날짜의 Task는 빼고 계산해요." +
                        (if (recent.size > GOAL_TASK_RATE_VISIBLE_WEEKS) " 좌우로 밀면 지난 주도 볼 수 있어요." else "") +
                        (if (bars.size > recent.size) " 최근 12주까지 보여줘요." else ""),
                    style = pt(12),
                    color = NC.ink3,
                )
            }
        }
    }
}

/**
 * 주별 막대: 높이 = 그 주 실천률, 위 숫자 = 실천률(%) · 실천/해야 할 개수
 * 80% 점선 = 목표를 해냈다고 보는 기준 (목표 결과 계산과 같은 기준)
 * y 축은 0~125 (위 숫자가 들어갈 자리를 남겨요). 6주를 넘으면 좌우로 밀어요 (처음엔 맨 오른쪽 = 이번 주).
 */
@Composable
private fun GoalTaskRateChart(recent: List<GoalTaskRateWeekBar>) {
    val chartHeight = 210.dp
    val labelHeight = 22.dp
    val plotHeight = chartHeight - labelHeight
    fun h(v: Double): Dp = plotHeight * (v / 125.0).toFloat()

    val scrolls = recent.size > GOAL_TASK_RATE_VISIBLE_WEEKS
    val scroll = rememberScrollState()
    LaunchedEffect(recent.size) {
        // 레이아웃이 끝나 스크롤 길이가 정해지면 맨 끝(이번 주)으로
        if (scrolls) {
            val max = snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
            if (max > 0) scroll.scrollTo(max)
        }
    }

    val successLine = NC.success.copy(alpha = 0.7f)
    val subtle = NC.progressTrack
    val brand = NC.progressFill
    val ruleY = h(80.0)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { contentDescription = "주별 Task 실천률" },
    ) {
        val colWidth = if (scrolls) maxWidth / GOAL_TASK_RATE_VISIBLE_WEEKS else maxWidth / maxOf(recent.size, 1)
        Row(Modifier.fillMaxSize().then(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier)) {
            for (b in recent) {
                Column(Modifier.width(colWidth)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(plotHeight)
                            .drawBehind {
                                // 80% 기준 점선 (칸마다 이어 그려서 한 줄로 보여요)
                                val y = size.height - ruleY.toPx()
                                drawLine(
                                    color = successLine,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                                )
                            },
                    ) {
                        // 바탕 칸
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.62f)
                                .height(h(100.0))
                                .background(subtle, RoundedCornerShape(8.dp)),
                        )
                        if (b.due > 0) {
                            val valueHeight = h(maxOf(b.rate * 100, 4.0))
                            val barColor = when {
                                b.rate >= 0.8 -> brand
                                b.rate >= 0.5 -> brand.copy(alpha = 0.6f)
                                else -> brand.copy(alpha = 0.35f)
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(0.62f)
                                    .height(valueHeight)
                                    .background(barColor, RoundedCornerShape(8.dp)),
                            )
                            Column(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = valueHeight + 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${(b.rate * 100).roundToInt()}%",
                                    style = ptMono(12, PTWeight.bold),
                                    color = if (b.rate >= 0.8) NC.textBrand else NC.ink2,
                                )
                                Text("${b.done}/${b.due}", style = ptMono(10), color = NC.ink4)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(labelHeight), contentAlignment = Alignment.Center) {
                        Text(
                            b.label,
                            style = ptMono(11, if (b.current) PTWeight.bold else PTWeight.regular),
                            color = if (b.current) NC.textBrand else NC.ink3,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // 기준선 이름 (왼쪽 고정)
        Text(
            "80%",
            style = pt(10, PTWeight.semibold),
            color = NC.success,
            modifier = Modifier.align(Alignment.TopStart).offset(y = plotHeight - ruleY - 15.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun GoalTaskRateSectionPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            GoalTaskRateSection(tasks = GoalPreviewData.tasks.tasks)
        }
    }
}
