// feature/goalcreation/DraftPlanSections.kt
//
// iOS Features/GoalCreation/DraftPlanSections.swift
// 초안 검토 화면의 카드들: 목표 · 수치 · 루틴 · 일회성 Task · 마일스톤 · 세부 목표.
// 웹 원본: features/goal-creation/ui/draft/PlanSections.tsx
//
// 웹은 항목을 누르면 그 자리에서 편집기가 열려요. 휴대폰은 화면이 좁아서 → 아래에서 올라오는 시트로 열어요.
// 각 항목에는 Modifier.draftAnchor("tasks[1]") 처럼 서버 violations 의 path 와 같은 키를 붙여서,
// 문제 항목으로 스크롤할 수 있게 해요 (iOS .id(vkey) + ScrollViewReader 대신).

package com.naleul.naleul.feature.goalcreation

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

// MARK: - 스크롤 위치 기록 (iOS .id(vkey) + ScrollViewReader.scrollTo)

/**
 * 항목 키("tasks[1]") → 그 항목의 화면 위치 정보.
 * DraftReviewView 가 하나 만들어 LocalDraftScrollAnchors 로 내려주고, 문제 항목으로 스크롤할 때 써요.
 * (그리기와 상관없는 값이라 상태(mutableState)가 아닌 보통 Map 이에요)
 */
class DraftScrollAnchors {
    val items: MutableMap<String, LayoutCoordinates> = mutableMapOf()
    /** 스크롤되는 내용 전체(Column)의 위치 */
    var content: LayoutCoordinates? = null

    /** 그 항목의 위쪽이 내용 맨 위에서 몇 px 아래인지 + 높이 (못 찾으면 null) */
    fun offsetOf(key: String): Pair<Float, Int>? {
        val c = content ?: return null
        val item = items[key] ?: return null
        if (!c.isAttached || !item.isAttached) return null
        val y = c.localPositionOf(item, Offset.Zero).y
        return y to item.size.height
    }

    companion object {}
}

val LocalDraftScrollAnchors = staticCompositionLocalOf<DraftScrollAnchors?> { null }

/** 이 항목을 key 로 기록해요 (문제 항목으로 스크롤용) */
@Composable
fun Modifier.draftAnchor(key: String): Modifier {
    val anchors = LocalDraftScrollAnchors.current ?: return this
    return this.onGloballyPositioned { anchors.items[key] = it }
}

/** iOS .padding(.horizontal, -8) — 양옆으로 그만큼 넓게 그려요 (Compose 는 음수 padding 이 안 돼요) */
private fun Modifier.draftOutset(horizontal: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val p = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = constraints.maxWidth + extra,
        )
    )
    layout((p.width - extra).coerceAtLeast(0), p.height) { p.place(-extra / 2, 0) }
}

/** 점선 테두리 (iOS .stroke(style: StrokeStyle(dash: [4, 3]))) */
private fun Modifier.draftDashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

// MARK: - 공통

/** 검증 실패 표시 틀: 문제가 있으면 빨간 테두리 + 메시지 */
@Composable
fun DraftIssueFrame(
    vkey: String,
    issues: DraftIssueMap,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val messages = issues[vkey]
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        modifier
            .draftAnchor(vkey)
            .fillMaxWidth()
            .then(
                if (messages != null) Modifier
                    .background(NC.dangerSoft.copy(alpha = 0.4f), shape)
                    .border(2.dp, NC.danger, shape)
                else Modifier
            ),
    ) {
        content()
        if (messages != null) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (m in messages) Text("· $m", style = pt(13, PTWeight.medium), color = NC.danger)
            }
        }
    }
}

/** 제목 있는 카드 (웹 PanelCard) */
@Composable
fun DraftPanelCard(
    title: String,
    aside: String? = null,
    vkey: String,
    issues: DraftIssueMap,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val messages = issues[vkey]
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .draftAnchor(vkey)
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(if (messages != null) 2.dp else 1.dp, if (messages != null) NC.danger else NC.borderSubtle, shape)
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            if (aside != null) Text(aside, style = pt(13), color = NC.ink3)
        }
        if (messages != null) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (m in messages) Text("· $m", style = pt(13, PTWeight.medium), color = NC.danger)
            }
        }
        content()
    }
}

/** 점선 "+ 추가" 버튼 */
@Composable
fun DraftAddButton(label: String, disabled: Boolean = false, action: () -> Unit) {
    val stroke = NC.lineStrong
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (disabled) 0.4f else 1f)
            .draftDashedBorder(stroke, Radius.xl)
            .clip(RoundedCornerShape(Radius.xl))
            .clickable(enabled = !disabled, role = Role.Button, onClick = action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        SfImage("plus", size = 14.dp, tint = NC.ink2)
        Text(label, style = pt(14, PTWeight.semibold), color = NC.ink2)
    }
}

@Composable
private fun DraftEmojiBox(emoji: String?, tone: Color) {
    Box(
        Modifier.size(40.dp).background(tone, RoundedCornerShape(Radius.xl)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (emoji.isNullOrEmpty()) "•" else emoji, style = TextStyle(fontSize = 18.sp))
    }
}

@Composable
private fun DraftPencil(size: Int = 13, modifier: Modifier = Modifier) {
    SfImage("pencil", size = size.dp, tint = NC.ink4, modifier = modifier)
}

// MARK: - 목표 카드

/**
 * @param colorId 고른 목표 색 (iOS @Binding) — null 이면 자동
 * @param styleChips 성향 · 강도 칩 (목표 카드 위)
 */
@Composable
fun DraftGoalHeroCard(
    plan: DraftGoalPlan,
    issues: DraftIssueMap,
    colors: List<UserColor>,
    colorId: Int?,
    onColorIdChange: (Int?) -> Unit,
    onEdit: (DraftEditTarget) -> Unit,
    styleChips: @Composable () -> Unit,
) {
    val goal = plan.goal
    val periodLine = run {
        val days = DraftPlanUtils.daysBetween(goal.startDate, goal.endDate)
        val weeks = maxOf(ceil(days.toDouble() / 7).toInt(), 1)
        var line = "${DraftPlanUtils.formatYmdDot(goal.startDate)} – ${DraftPlanUtils.formatYmdDot(goal.endDate)} · ${weeks}주"
        val m = goal.metric
        if (m != null) {
            val perWeek = (m.targetValue - m.startValue) / weeks.toDouble()
            if (perWeek.isFinite()) {
                line += " · 주 평균 ${if (perWeek > 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", perWeek)}${m.unit ?: ""}"
            }
        }
        line
    }
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(20.dp),
    ) {
        FlowLayout(spacing = 8.dp) {
            NBadge("✦ AI가 설계한 목표", tone = BadgeTone.brand)
            styleChips()
        }

        DraftIssueFrame("goal", issues = issues, modifier = Modifier.padding(top = 12.dp).draftOutset(8.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xxl))
                    .clickable(role = Role.Button) { onEdit(DraftEditTarget.goal) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        (goal.emoji?.let { "$it " } ?: "") + goal.title,
                        style = pt(26, PTWeight.bold),
                        color = NC.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    DraftPencil(size = 15)
                }
                Text(periodLine, style = pt(14), color = NC.ink3)
            }
        }

        val note = goal.aiNote
        if (!note.isNullOrEmpty()) {
            Text(
                "💡 $note",
                style = pt(14).copy(lineHeight = 20.sp),
                color = NC.ink,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // 목표 색상
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("목표 색상", style = pt(13, PTWeight.medium), color = NC.ink3)
            FlowLayout(spacing = 8.dp) {
                val auto = colorId == null
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(if (auto) NC.actionPrimary else Color.Transparent, CircleShape)
                        .border(1.dp, if (auto) NC.actionPrimary else NC.borderSubtle, CircleShape)
                        .clickable(role = Role.RadioButton) { onColorIdChange(null) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("자동", style = pt(12, PTWeight.semibold), color = if (auto) NC.actionPrimaryFg else NC.ink2)
                }
                for (c in colors) {
                    val on = colorId == c.userColorId
                    Box(
                        Modifier
                            .size(38.dp)
                            .border(2.dp, if (on) NC.actionPrimary else Color.Transparent, CircleShape)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { onColorIdChange(c.userColorId) }
                            .semantics { contentDescription = "색상 ${c.hex}" }
                            .padding(3.dp)
                            .background(c.color, CircleShape)
                    )
                }
            }
        }
    }
}

// MARK: - 마일스톤 타임라인

@Composable
fun DraftMilestoneTimeline(plan: DraftGoalPlan, issues: DraftIssueMap, onEdit: (DraftEditTarget) -> Unit) {
    val milestones = plan.milestones
    val goal = plan.goal
    val unit = goal.metric?.unit ?: ""
    Column(Modifier.fillMaxWidth().draftAnchor("milestones")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("마일스톤", style = NType.h4, color = NC.ink)
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            Text(
                "${milestones.size}단계${if (goal.metric != null) " · 수치는 자동 계산" else ""}",
                style = pt(13),
                color = NC.ink3,
            )
        }
        val messages = issues["milestones"]
        if (messages != null) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (m in messages) Text("· $m", style = pt(13, PTWeight.medium), color = NC.danger)
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            milestones.forEachIndexed { i, _ ->
                DraftMilestoneCell(plan, i, last = i == milestones.size - 1, unit = unit, issues = issues, onEdit = onEdit)
            }
        }

        DraftAddButton(label = "마일스톤 추가", disabled = milestones.size >= DraftPlanUtils.Limits.milestones.max) {
            onEdit(DraftEditTarget.milestone(null))
        }
    }
}

@Composable
private fun DraftMilestoneCell(
    plan: DraftGoalPlan,
    i: Int,
    last: Boolean,
    unit: String,
    issues: DraftIssueMap,
    onEdit: (DraftEditTarget) -> Unit,
) {
    val ms = plan.milestones[i]
    val days = DraftPlanUtils.daysBetween(plan.goal.startDate, ms.dueDate)
    // 한 달 안쪽은 주 단위로 (2주 목표에 "1개월"이라고 나오지 않게)
    val whenText = if (days < 28) "${maxOf((days.toDouble() / 7).roundToInt(), 1)}주" else "${(days.toDouble() / 30).roundToInt()}개월"
    val dotFill = if (i == 0) NC.brandSoft else if (last) NC.success else NC.surface
    val dotStroke = if (i == 0) NC.brand else if (last) NC.success else NC.lineStrong
    Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(dotFill, CircleShape).border(3.dp, dotStroke, CircleShape))
            if (!last) Box(Modifier.weight(1f).height(2.dp).background(NC.lineStrong))
        }
        DraftIssueFrame("milestones[$i]", issues = issues, modifier = Modifier.padding(end = 12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xxl))
                    .clickable(role = Role.Button) { onEdit(DraftEditTarget.milestone(i)) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$whenText · ~${DraftPlanUtils.formatMd(ms.dueDate, withWeekday = false)}",
                        style = pt(12),
                        color = NC.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    DraftPencil(size = 11)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ms.title, style = pt(15, PTWeight.bold), color = NC.ink, modifier = Modifier.weight(1f, fill = false))
                    val v = ms.targetValue
                    if (v != null) NBadge("${DraftPlanUtils.num(v)}$unit", tone = if (last) BadgeTone.success else BadgeTone.brand)
                }
                val d = ms.description
                if (!d.isNullOrEmpty()) {
                    Text(d, style = pt(13), color = NC.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// MARK: - 수치 카드 (파란 카드)

/** 계획 곡선 (시작값 → 마일스톤 수치) — 웹 SVG 를 Canvas 로 */
@Composable
private fun DraftPlanCurve(plan: DraftGoalPlan) {
    val metric = plan.goal.metric ?: return
    val pts = buildList {
        add(plan.goal.startDate to metric.startValue)
        for (m in plan.milestones) m.targetValue?.let { add(m.dueDate to it) }
    }
    if (pts.size < 2) return
    val measurer = rememberTextMeasurer()
    val labelStyle = pt(11, PTWeight.bold).copy(color = Color.White)
    val total = maxOf(DraftPlanUtils.daysBetween(plan.goal.startDate, plan.goal.endDate), 1).toDouble()
    val values = pts.map { it.second }
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: 0.0
    val span = if (hi - lo == 0.0) 1.0 else hi - lo
    // 날짜 → 시작일부터 며칠째 (Canvas 안에서는 계산만)
    val dayOffsets = pts.map { DraftPlanUtils.daysBetween(plan.goal.startDate, it.first).toDouble() }

    Canvas(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(110.dp)
            .semantics { contentDescription = "계획 곡선" }
    ) {
        val w = size.width
        val h = size.height
        val pad = 14.dp.toPx()
        val top = 12.dp.toPx()
        fun x(i: Int): Float {
            val r = dayOffsets[i].coerceIn(0.0, total) / total
            return pad + (r * (w - pad * 2)).toFloat()
        }
        fun y(v: Double): Float = pad + top + ((hi - v) / span * (h - pad * 2 - top)).toFloat()

        val path = Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.second)) else path.lineTo(x(i), y(p.second))
        }
        drawPath(
            path,
            color = Color.White.copy(alpha = 0.85f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        )
        pts.forEachIndexed { i, p ->
            val cx = x(i)
            val cy = y(p.second)
            val r = (if (i == pts.size - 1) 5 else 4).dp.toPx()
            drawCircle(Color.White, radius = r, center = Offset(cx, cy))
            if (i > 0) {
                val layout = measurer.measure(DraftPlanUtils.num(p.second), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(cx - layout.size.width / 2f, cy - 14.dp.toPx() - layout.size.height / 2f),
                )
            }
        }
    }
}

@Composable
fun DraftMetricCard(plan: DraftGoalPlan, onEditGoal: () -> Unit) {
    val goal = plan.goal
    val routines = plan.tasks.filter { it.isRoutine }
    val oneTimes = plan.tasks.size - routines.size
    val weeklyCount = routines.sumOf { it.routineDays?.size ?: 0 }
    val remain = DraftPlanUtils.dDay(goal.endDate)
    // 기간별로 나눈 루틴(이론 → 기출)이 있으면 단계 수를 보여줘요 (V52: 마일스톤 대신)
    val phases = routines.map { "${it.startDate ?: ""}~${it.endDate ?: ""}" }.toSet().size
    val milestoneCount = plan.milestones.size
    val stats = buildList {
        add("루틴" to "${routines.size}개")
        add("주간 실천" to "주 ${weeklyCount}회")
        add("일회성 Task" to "${oneTimes}개")
        if (phases > 1) add("단계" to "${phases}단계")
        else if (milestoneCount > 0) add("마일스톤" to "${milestoneCount}단계")
    }
    val white70 = Color.White.copy(alpha = 0.7f)
    val white80 = Color.White.copy(alpha = 0.8f)

    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.brand, RoundedCornerShape(Radius.card))
            .padding(24.dp),
    ) {
        val m = goal.metric
        if (m != null) {
            Row(Modifier.fillMaxWidth()) {
                Text("현재", style = pt(13), color = white70)
                Spacer(Modifier.weight(1f))
                Text("목표", style = pt(13), color = white70)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append(DraftPlanUtils.num(m.startValue))
                        withStyle(SpanStyle(fontSize = 20.sp)) { append(m.unit ?: "") }
                    },
                    style = pt(32, PTWeight.bold),
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                SfImage("arrow.right", size = 22.dp, tint = white70)
                Spacer(Modifier.weight(1f))
                Text(
                    buildAnnotatedString {
                        append(DraftPlanUtils.num(m.targetValue))
                        withStyle(SpanStyle(fontSize = 20.sp)) { append(m.unit ?: "") }
                    },
                    style = pt(32, PTWeight.bold),
                    color = Color.White,
                )
            }
        } else {
            Text("기간", style = pt(13), color = white70)
            Text(
                "${DraftPlanUtils.formatMd(goal.startDate, withWeekday = false)} → ${DraftPlanUtils.formatMd(goal.endDate, withWeekday = false)}",
                style = pt(24, PTWeight.bold),
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // 진행 막대 (아직 시작 전이라 3%만)
        Box(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.25f), CircleShape)
        ) {
            Box(Modifier.fillMaxWidth(0.03f).fillMaxHeight().background(Color.White, CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("시작 ${DraftPlanUtils.formatMd(goal.startDate, withWeekday = false)}", style = pt(13), color = white80)
            Spacer(Modifier.weight(1f))
            Text(if (remain >= 0) "D-$remain" else "종료", style = pt(13), color = white80)
        }

        if (m != null) {
            Text("계획 ${m.name} 곡선", style = pt(13), color = white80, modifier = Modifier.padding(top = 20.dp))
            DraftPlanCurve(plan = plan)
        }

        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .clickable(role = Role.Button, onClick = onEditGoal)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SfImage("pencil", size = 13.dp, tint = Color.White)
            Text(
                if (m != null) "시작값·목표값·종료일 고치기" else "종료일 고치기",
                style = pt(13, PTWeight.semibold),
                color = Color.White,
            )
        }

        // 2열 통계 (iOS LazyVGrid — 스크롤 안이라 행을 직접 만들어요)
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in stats.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((label, value) in row) {
                        Column(
                            Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(Radius.xxl))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(label, style = pt(12), color = Color.White.copy(alpha = 0.75f))
                            Text(value, style = pt(20, PTWeight.bold), color = Color.White)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// MARK: - 세부 목표

@Composable
fun DraftSubGoalsCard(plan: DraftGoalPlan, issues: DraftIssueMap, onEdit: (DraftEditTarget) -> Unit) {
    val tones = listOf(NC.warningSoft, NC.brandSoft, NC.successSoft, NC.dangerSoft)
    DraftPanelCard("세부 목표", aside = "${plan.subGoals.size}개", vkey = "subGoals", issues = issues) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.subGoals.forEachIndexed { i, sg ->
                DraftIssueFrame("subGoals[$i]", issues = issues) {
                    val shape = RoundedCornerShape(Radius.xxl)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, NC.borderSubtle, shape)
                            .clickable(role = Role.Button) { onEdit(DraftEditTarget.subGoal(i)) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DraftEmojiBox(emoji = sg.emoji, tone = tones[i % tones.size])
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(sg.title, style = pt(15, PTWeight.bold), color = NC.ink)
                            val d = sg.description
                            if (!d.isNullOrEmpty()) Text(d, style = pt(13).copy(lineHeight = 18.sp), color = NC.ink3)
                        }
                        DraftPencil(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        DraftAddButton(label = "세부 목표 추가", disabled = plan.subGoals.size >= DraftPlanUtils.Limits.subGoals.max) {
            onEdit(DraftEditTarget.subGoal(null))
        }
    }
}

// MARK: - 생성될 Task (일회성)

@Composable
fun DraftOneTimeTasksCard(plan: DraftGoalPlan, issues: DraftIssueMap, onEdit: (DraftEditTarget) -> Unit) {
    /** 원래 배열 위치(index)를 유지해야 서버 violations 의 tasks[i] 와 맞아요 */
    val list = plan.tasks.withIndex()
        .filter { !it.value.isRoutine }
        .sortedBy { it.value.scheduledDate ?: it.value.dueDate ?: "" }

    fun subGoalName(t: DraftPlanTask): String? = plan.subGoals.firstOrNull { it.tempId == t.subGoalTempId }?.title

    fun oneTimeLine(t: DraftPlanTask): String {
        var s = t.scheduledDate?.let { DraftPlanUtils.formatMd(it) } ?: "날짜 자동"
        t.dueDate?.let { s += " · 마감 ${DraftPlanUtils.formatMd(it, withWeekday = false)}" }
        return s + " · ${t.durationMinutes}분"
    }

    DraftPanelCard("생성될 Task", aside = "일회성 · ${list.size}개", vkey = "tasks", issues = issues) {
        if (list.isEmpty()) {
            Text(
                "따로 챙길 일회성 Task는 없어요. 위의 루틴만 꾸준히 하면 돼요.",
                style = pt(13).copy(lineHeight = 18.sp),
                color = NC.ink3,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column {
            list.forEachIndexed { n, entry ->
                val task = entry.value
                val index = entry.index
                if (n > 0) HorizontalDivider(color = NC.borderSoft)
                DraftIssueFrame("tasks[$index]", issues = issues, modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onEdit(DraftEditTarget.task(index, DraftTaskType.ONE_TIME)) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${task.emoji ?: ""} ${task.title}",
                                style = pt(15, PTWeight.semibold),
                                color = NC.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(oneTimeLine(task), style = pt(13), color = NC.ink3)
                        }
                        val name = subGoalName(task)
                        if (name != null) NBadge(name, modifier = Modifier.widthIn(max = 96.dp))
                        DraftPencil()
                    }
                }
            }
        }
        DraftAddButton(label = "Task 추가", disabled = list.size >= DraftPlanUtils.Limits.oneTimes.max) {
            onEdit(DraftEditTarget.task(null, DraftTaskType.ONE_TIME))
        }
    }
}

// MARK: - 필요 루틴

@Composable
fun DraftRoutinesCard(plan: DraftGoalPlan, issues: DraftIssueMap, onEdit: (DraftEditTarget) -> Unit) {
    val list = plan.tasks.withIndex().filter { it.value.isRoutine }
    DraftPanelCard("필요 루틴", aside = "반복 · ${list.size}개", vkey = "routines", issues = issues) {
        Column {
            list.forEachIndexed { n, entry ->
                if (n > 0) HorizontalDivider(color = NC.borderSoft)
                DraftRoutineRow(plan, entry.value, entry.index, issues, onEdit, Modifier.padding(vertical = 4.dp))
            }
        }
        DraftAddButton(label = "루틴 추가", disabled = list.size >= DraftPlanUtils.Limits.routines.max) {
            onEdit(DraftEditTarget.task(null, DraftTaskType.ROUTINE))
        }
    }
}

/** 목표 기간 전체가 아니면 "10.21부터", "~10.20", "10.07~10.20" */
private fun draftRoutinePeriodOf(plan: DraftGoalPlan, t: DraftPlanTask): String? {
    val from = t.startDate?.takeIf { it > plan.goal.startDate }
    val to = t.endDate?.takeIf { it < plan.goal.endDate }
    val f = { v: String -> DraftPlanUtils.formatMd(v, withWeekday = false) }
    if (from != null && to != null) return "${f(from)}~${f(to)}"
    if (from != null) return "${f(from)}부터"
    if (to != null) return "~${f(to)}"
    return null
}

@Composable
private fun DraftRoutineRow(
    plan: DraftGoalPlan,
    task: DraftPlanTask,
    index: Int,
    issues: DraftIssueMap,
    onEdit: (DraftEditTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = draftRoutinePeriodOf(plan, task)
    val brand = NC.brand
    val line = buildAnnotatedString {
        append("${task.preferredStartTime ?: "시간 자동"} · ${task.durationMinutes}분")
        if (period != null) {
            withStyle(SpanStyle(color = brand, fontWeight = FontWeight.SemiBold)) { append(" · $period") }
        }
    }
    DraftIssueFrame("tasks[$index]", issues = issues, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onEdit(DraftEditTarget.task(index, DraftTaskType.ROUTINE)) }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(task.emoji ?: "", style = TextStyle(fontSize = 18.sp), modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title, style = pt(15, PTWeight.semibold), color = NC.ink, modifier = Modifier.weight(1f))
                    DraftPencil(modifier = Modifier.padding(top = 3.dp))
                }
                // 예전 목표에서 하던 루틴 — 확정하면 새로 만들지 않고 이어서 써요 (빼면 오늘까지만 하고 끝나요, 2026-10)
                if (task.tempId.startsWith("existing-routine-")) {
                    Text(
                        "지금 하는 루틴",
                        style = pt(11, PTWeight.semibold),
                        color = NC.ink2,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .background(NC.subtle, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(line, style = pt(13), color = NC.ink3, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in GoalCreationWeekday.entries) {
                        val on = task.routineDays?.contains(d) ?: false
                        Box(
                            Modifier.size(28.dp).background(if (on) NC.brand else NC.subtle, RoundedCornerShape(Radius.lg)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.label, style = pt(12, PTWeight.semibold), color = if (on) Color.White else NC.ink4)
                        }
                    }
                }
                val desc = task.description
                if (!desc.isNullOrEmpty()) {
                    Text(
                        desc,
                        style = pt(13).copy(lineHeight = 18.sp),
                        color = NC.ink2,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .background(NC.subtle, RoundedCornerShape(Radius.lg))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                val r = task.reason
                if (!r.isNullOrEmpty()) {
                    Text("💡 $r", style = pt(12).copy(lineHeight = 17.sp), color = NC.ink3, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 2600)
@Composable
private fun DraftPlanSectionsPreview() {
    NaleulTheme(dark = false) {
        val plan = remember { GoalCreationSamples.plan }
        val issues: DraftIssueMap = mapOf("tasks[0]" to listOf("루틴은 1~1440분으로 설정해 주세요."))
        Column(
            Modifier.background(NC.canvas).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DraftGoalHeroCard(
                plan = plan, issues = emptyMap(),
                colors = listOf(UserColor(1, "#3D5AFE", true), UserColor(2, "#12B76A", false)),
                colorId = null, onColorIdChange = {}, onEdit = {},
            ) { Text("차근차근 계획형", style = pt(12, PTWeight.semibold)) }
            DraftMetricCard(plan = plan, onEditGoal = {})
            DraftRoutinesCard(plan = plan, issues = issues, onEdit = {})
            DraftOneTimeTasksCard(plan = plan, issues = emptyMap(), onEdit = {})
            DraftMilestoneTimeline(plan = plan, issues = emptyMap(), onEdit = {})
            DraftSubGoalsCard(plan = plan, issues = emptyMap(), onEdit = {})
        }
    }
}
