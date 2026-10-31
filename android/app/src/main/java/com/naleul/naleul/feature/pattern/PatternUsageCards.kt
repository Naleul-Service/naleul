// feature/pattern/PatternUsageCards.kt
//
// iOS Features/Pattern/PatternUsageCards.swift
// 웹 features/pattern/ui/TimeUsage.tsx · BlockKeeping.tsx · RoutineLists.tsx · TaskHabits.tsx
// 리포트 아래쪽 카드들 (시간 사용 · 블록 지키기 · 루틴 · Task 처리 습관)

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - 어디에 시간을 쓰고 있나요? (목표별 주 평균 실제 시간, 세로선 = 계획)

@Composable
fun PatternTimeUsageCard(usage: PatternTimeUsage?, modifier: Modifier = Modifier) {
    val goals = usage?.goals ?: emptyList()
    val maxHours = goals.maxOfOrNull { maxOf(it.actualHoursPerWeek, it.plannedHoursPerWeek) }?.let { maxOf(1.0, it) } ?: 1.0
    SectionCard("어디에 시간을 쓰고 있나요?", modifier = modifier, aside = {
        if (usage != null && goals.isNotEmpty()) Text("주 평균 ${PatternFormat.hours1(usage.weeklyTotalHours)}h · ┃ 계획")
    }) {
        if (goals.isEmpty()) {
            NotEnoughView(text = "목표에 연결된 Task를 실행하면 보여드려요")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (g in goals) {
                    key(g.goalCategoryId) {
                        Box(Modifier.patternChartTap("time_usage").patternDeadTap("time_usage_row")) {
                            PatternGoalUsageRow(goal = g, maxHours = maxHours)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternGoalUsageRow(goal: PatternGoalUsage, maxHours: Double) {
    val ratioText = when {
        goal.newTemporary -> "새 임시 목표"
        goal.planRatio != null -> "계획의 ${goal.planRatio}%"
        else -> "계획 없음"
    }
    val color = goal.colorCode?.let { goalColor(it) } ?: NC.brand
    val ink = NC.ink
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                (goal.emoji?.let { "$it " } ?: "") + goal.name,
                style = pt(15, PTWeight.bold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)) {
                        append("${PatternFormat.hours1(goal.actualHoursPerWeek)}h")
                    }
                    append(" / 주 · $ratioText")
                },
                style = pt(13),
                color = NC.ink3,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(12.dp).background(NC.subtle, CircleShape))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(w * (goal.actualHoursPerWeek / maxHours).toFloat().coerceIn(0f, 1f))
                    .height(12.dp)
                    .background(color, CircleShape),
            )
            if (goal.plannedHoursPerWeek > 0) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = w * (goal.plannedHoursPerWeek / maxHours).toFloat() - 1.dp)
                        .size(width = 2.dp, height = 20.dp)
                        .background(NC.ink, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

// MARK: - 블록을 어떻게 지키나요? (제시간 / 늦게 / 놓침 + 자주 옮기는 루틴)

/** 제시간 / 늦게 / 놓침 필터 */
enum class PatternKeepKind {
    onTime, late, missed;

    val id: String get() = name
    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            onTime -> "제시간 시작"
            late -> "늦게 시작"
            missed -> "놓침"
        }

    val color: Color
        @Composable get() = when (this) {
            onTime -> NC.success
            late -> NC.warning
            missed -> NC.danger
        }

    fun count(i: PatternBlockKeepItem): Int = when (this) {
        onTime -> i.onTime
        late -> i.late
        missed -> i.missed
    }

    fun percent(b: PatternBlockKeeping): Int = when (this) {
        onTime -> b.onTime ?: 0
        late -> b.late ?: 0
        missed -> b.missed ?: 0
    }

    companion object {}
}

/** initialKind: 처음부터 펼쳐 둘 칩 (미리보기용) */
@Composable
fun PatternBlockKeepingCard(block: PatternBlockKeeping?, initialKind: PatternKeepKind? = null, modifier: Modifier = Modifier) {
    // 누른 칩 — 그렇게 된 루틴 · Task 를 많은 순으로 보여줘요 (다시 누르면 접혀요)
    var kind by remember { mutableStateOf(initialKind) }
    val haptic = LocalHapticFeedback.current
    val hasRatio = (block?.total ?: 0) > 0 && block?.onTime != null
    val moves = block?.frequentMoves ?: emptyList()
    SectionCard("블록을 어떻게 지키나요?", modifier = modifier, aside = {
        val total = block?.total
        if (total != null && total > 0) Text("${PatternFormat.number(total)}개 기준")
    }) {
        Column {
            if (block != null && hasRatio) {
                PatternKeepingBar(onTime = block.onTime ?: 0, late = block.late ?: 0, missed = block.missed ?: 0)
                FlowLayout(spacing = 8.dp, lineSpacing = 8.dp, modifier = Modifier.padding(top = 12.dp)) {
                    for (k in PatternKeepKind.entries) {
                        PatternKeepChip(kind = k, value = k.percent(block), on = kind == k) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val expanded = kind != k
                            PT.track("pattern.keep_filter_click", mapOf(
                                "keep_kind" to k.analyticsKey, "expanded" to expanded,
                                "item_count" to block.items.orEmpty().count { k.count(it) > 0 },
                                "period" to PT.period.name,
                            ))
                            kind = if (expanded) k else null
                        }
                    }
                }
                PatternKeepList(block = block, kind = kind)
            } else {
                NotEnoughView(text = "시간을 정한 블록이 쌓이면 보여드려요")
            }

            Text("자주 옮기는 루틴", style = pt(15, PTWeight.bold), color = NC.ink, modifier = Modifier.padding(top = 24.dp))
            Text(
                "반복되는 루틴만 봐요 — 늘 같은 방향으로 옮긴다면 루틴 시간을 바꾸는 게 나아요.",
                style = pt(12),
                color = NC.ink3,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (moves.isEmpty()) {
                Text(
                    "캘린더에서 루틴 블록을 옮긴 기록이 쌓이면 보여드려요",
                    style = pt(13),
                    color = NC.ink3,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    moves.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = NC.borderSoft)
                        Box(Modifier.patternDeadTap("moved_routine")) { PatternMoveRow(move = m) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternKeepingBar(onTime: Int, late: Int, missed: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
        val w = maxWidth
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().width(w * (onTime / 100f)).background(NC.success))
            Box(Modifier.fillMaxHeight().width(w * (late / 100f)).background(NC.warning))
            Box(Modifier.fillMaxHeight().width(w * (missed / 100f)).background(NC.danger))
        }
    }
}

@Composable
private fun PatternKeepList(block: PatternBlockKeeping, kind: PatternKeepKind?) {
    val items = block.items ?: emptyList()
    if (kind != null) {
        val list = items.filter { kind.count(it) > 0 }
            .sortedWith { a, b ->
                if (kind.count(a) != kind.count(b)) kind.count(b).compareTo(kind.count(a)) else b.total.compareTo(a.total)
            }
        if (list.isEmpty()) {
            Text(
                if (kind == PatternKeepKind.missed) "놓친 블록이 없어요 👏" else "${kind.label}한 블록이 없어요",
                style = pt(13),
                color = NC.ink3,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .border(1.dp, NC.borderSubtle, RoundedCornerShape(Radius.xxl)),
            ) {
                list.forEachIndexed { i, item ->
                    key(item.id) {
                        if (i > 0) HorizontalDivider(color = NC.borderSoft)
                        PatternKeepItemRow(item = item, kind = kind)
                    }
                }
            }
        }
    } else if (items.isNotEmpty()) {
        Text(
            "칩을 누르면 어떤 루틴 · Task였는지 볼 수 있어요",
            style = pt(12),
            color = NC.ink4,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PatternKeepChip(kind: PatternKeepKind, value: Int, on: Boolean, action: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (on) NC.actionPrimary else Color.Transparent, CircleShape)
            .border(1.dp, if (on) NC.actionPrimary else NC.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClick = action)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(kind.color, RoundedCornerShape(2.dp)))
        Text(kind.label, style = pt(13), color = if (on) NC.actionPrimaryFg else NC.ink2)
        Text("$value%", style = pt(13, PTWeight.bold), color = if (on) NC.actionPrimaryFg else NC.ink)
    }
}

@Composable
private fun PatternKeepItemRow(item: PatternBlockKeepItem, kind: PatternKeepKind) {
    val kindColor = kind.color
    val ink4 = NC.ink4
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.routine) SfImage("repeat", size = 11.dp, tint = NC.textBrand, contentDescription = "루틴")
                Text(
                    (item.emoji?.let { "$it " } ?: "") + item.title,
                    style = pt(14, PTWeight.semibold),
                    color = NC.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val goal = item.goalName
            if (goal != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(7.dp).background(goalColor(item.goalColorCode), CircleShape))
                    Text(goal, style = pt(12), color = NC.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = kindColor)) { append("${kind.count(item)}번") }
                withStyle(SpanStyle(color = ink4)) { append(" / ${item.total}번") }
            },
            style = pt(13),
            maxLines = 1,
        )
    }
}

@Composable
private fun PatternMoveRow(move: PatternFrequentMove) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (move.emoji?.let { "$it " } ?: "") + move.title,
                style = pt(15, PTWeight.semibold),
                color = NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(move.description, style = pt(13), color = NC.ink3)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(move.fromLabel, style = pt(14).copy(textDecoration = TextDecoration.LineThrough), color = NC.ink4, maxLines = 1)
            Text("→", style = pt(14), color = NC.ink2)
            Text(move.toLabel, style = pt(14, PTWeight.bold), color = NC.ink, maxLines = 1)
            Text(
                "${move.count}회",
                style = pt(12, PTWeight.semibold),
                color = NC.ink2,
                maxLines = 1,
                modifier = Modifier
                    .background(NC.subtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

// MARK: - 잘 지키는 루틴 / 자주 놓치는 블록

@Composable
fun PatternRoutineLists(routines: PatternRoutines?, modifier: Modifier = Modifier) {
    val kept = routines?.kept ?: emptyList()
    val missed = routines?.missed ?: emptyList()
    Column(modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("💪 잘 지키는 루틴", style = pt(16, PTWeight.bold), color = NC.ink)
        if (kept.isEmpty()) {
            Text(
                "4번 이상 한 루틴 중 75% 넘게 지킨 루틴이 여기에 보여요",
                style = pt(13), color = NC.ink3, modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (r in kept) {
                    key(r.routineId) {
                        Row(Modifier.patternDeadTap("kept_routine"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                (r.emoji?.let { "$it  " } ?: "") + r.title,
                                style = pt(15, PTWeight.semibold), color = NC.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${r.rate}%", style = pt(15, PTWeight.bold), color = NC.success)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = NC.borderSoft, modifier = Modifier.padding(vertical = 20.dp))

        Text("🫠 자주 놓치는 블록", style = pt(16, PTWeight.bold), color = NC.ink)
        if (missed.isEmpty()) {
            Text("3번 이상 한 루틴 중 자주 놓친 게 없어요 👏", style = pt(13), color = NC.ink3, modifier = Modifier.padding(top = 12.dp))
        } else {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (r in missed) {
                    Row(Modifier.patternDeadTap("missed_block"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                (r.emoji?.let { "$it  " } ?: "") + r.label,
                                style = pt(15, PTWeight.semibold), color = NC.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(r.description, style = pt(13), color = NC.ink3)
                        }
                        Text("${r.rate}%", style = pt(15, PTWeight.bold), color = NC.danger)
                    }
                }
            }
        }
    }
}

// MARK: - Task 처리 습관 (루틴이 아닌 Task)

@Composable
fun PatternTaskHabitsCard(habits: PatternTaskHabits?, modifier: Modifier = Modifier) {
    val dash = "–"
    val h = habits
    val top = h?.postponedTopGoal
    val postponedSub: String? = when {
        top != null -> "대부분 $top"
        h?.postponedRate != null -> "미룬 Task가 거의 없어요"
        else -> null
    }
    SectionCard("Task 처리 습관", modifier = modifier, aside = {
        if (h != null) Text("Task ${h.taskCount}개")
    }) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            PatternHabitCell(
                label = "미룬 Task",
                value = h?.postponedRate?.let { "$it%" } ?: dash,
                sub = postponedSub,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(NC.line))
            PatternHabitCell(
                label = "brain dump",
                value = h?.brainDumpPerWeek?.let { "주 ${PatternFormat.hours1(it)}회" } ?: dash,
                sub = h?.brainDumpAvgItems?.let { "한 번에 평균 ${PatternFormat.hours1(it)}개" } ?: "Task 추가를 2번 이상 하면 보여요",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PatternHabitCell(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(14.dp)) {
        Text(label, style = pt(13), color = NC.ink3)
        Text(
            value,
            style = pt(22, PTWeight.extrabold).copy(letterSpacing = (-0.4).sp),
            color = NC.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (sub != null) {
            Text(sub, style = pt(12), color = NC.ink3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun PatternUsageCardsPreview() {
    val r = PatternSampleData.report
    NaleulTheme(dark = false) {
        Column(
            Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PatternTimeUsageCard(usage = r.timeUsage)
            PatternBlockKeepingCard(block = r.blockKeeping)
            PatternBlockKeepingCard(block = r.blockKeeping, initialKind = PatternKeepKind.missed)
            PatternRoutineLists(routines = r.routines)
            PatternTaskHabitsCard(habits = r.taskHabits)
            PatternTimeUsageCard(usage = null)
            PatternBlockKeepingCard(block = null)
            PatternRoutineLists(routines = null)
            PatternTaskHabitsCard(habits = null)
        }
    }
}
