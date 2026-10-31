// feature/goal/GoalStructurePanel.kt
//
// iOS Features/Goal/GoalStructurePanel.swift
// 웹 features/goal/ui/StructurePanel.tsx · MilestonesSection.tsx · SubGoalsSection.tsx
//
// 세부 구조 (세부 목표 · 마일스톤) — 기본은 접어 둬요.
// 왜: 매일 보는 건 "할 것(루틴·Task)"이고, 세부 목표는 라벨, 마일스톤은 자동으로 맞춰지니 꼭 바꿀 때만 열면 돼요.
// 접혀 있어도 다음 마일스톤은 크게 보여줘요.
// (웹은 이 안을 편집 중이면 접히지 않게 했어요 — 앱은 편집을 시트로 해서 그럴 필요가 없어요)

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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// MARK: - 세부 구조 패널

@Composable
fun GoalStructurePanel(goal: GoalCategory, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val vm = LocalGoalDetailViewModel.current
    val today = KST.today()
    val milestones = GoalDetailLogic.sortedMilestones(goal)
    val next = milestones.firstOrNull { it.status != MilestoneStatus.ACHIEVED && it.dueDate >= today }
    val achieved = milestones.count { it.status == MilestoneStatus.ACHIEVED }
    val areas = GoalDetailLogic.areas(goal)
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "structure-chevron")
    val shape = RoundedCornerShape(Radius.card)
    val line = NC.line

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape),
    ) {
        if (next != null) {
            GoalNextMilestoneBlock(goal = goal, next = next, today = today)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    vm?.track("goal_detail.structure_toggle", mapOf(
                        "expanded" to !open, "plan_milestone_count" to milestones.size, "plan_subgoal_count" to areas.size,
                    ))
                    open = !open
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("세부 구조", style = pt(15, PTWeight.bold), color = NC.ink)
                Text(
                    "세부 목표 ${areas.size}개 · 마일스톤 ${if (milestones.isEmpty()) "없음" else "$achieved/${milestones.size} 달성"}",
                    style = pt(13),
                    color = NC.ink3,
                )
            }
            Text(if (open) "접기" else "펼쳐서 수정", style = pt(13), color = NC.ink3)
            SfImage("chevron.down", size = 14.dp, tint = NC.ink3, modifier = Modifier.rotate(chevron))
        }

        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 위쪽 1dp 선
                    .drawBehind { drawRect(line, topLeft = Offset.Zero, size = Size(size.width, 1.dp.toPx())) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalMilestonesSection(goal = goal)
                GoalSubGoalsSection(goal = goal)
            }
        }
    }
}

/** 다음 마일스톤 — 접혀 있어도 한눈에 보이게 크게 */
@Composable
private fun GoalNextMilestoneBlock(goal: GoalCategory, next: MilestoneInfo, today: String) {
    val dLeft = maxOf(KST.diffDays(today, next.dueDate), 0)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.brandSoft)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("다음 마일스톤", style = pt(13, PTWeight.semibold), color = NC.textBrand)
            Spacer(Modifier.weight(1f))
            Text(
                if (dLeft == 0) "D-DAY" else "D-$dLeft",
                style = ptMono(13, PTWeight.bold),
                color = Color.White,
                modifier = Modifier
                    .background(NC.brand, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Text(next.title, style = NType.h4, color = NC.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${KST.monthOf(next.dueDate)}월 ${KST.dayOfMonth(next.dueDate)}일까지", style = pt(14), color = NC.ink2)
            val t = next.targetValue
            if (t != null) {
                Text(
                    "목표 ${GoalDetailLogic.trim(t, 6)}${goal.metricUnit ?: ""}",
                    style = pt(14, PTWeight.bold),
                    color = NC.textBrand,
                )
            }
        }
    }
}

// MARK: - 마일스톤 (동그라미로 달성 체크, 글자를 누르면 수정)

@Composable
fun GoalMilestonesSection(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var pendingId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // 수치 목표가 있는 목표인지 (점검 시점 수치를 자동 계산할 수 있는지)
    val hasMetric = goal.startValue != null && goal.targetValue != null
    val milestones = GoalDetailLogic.sortedMilestones(goal)
    val today = KST.today()
    val achieved = milestones.count { it.status == MilestoneStatus.ACHIEVED }
    // 지금 향하고 있는 마일스톤: 아직 달성 안 했고 기한이 남은 것 중 가장 가까운 것
    val currentId = milestones.firstOrNull { it.status != MilestoneStatus.ACHIEVED && it.dueDate >= today }?.milestoneId
    val lineStrong = NC.lineStrong

    fun toggle(m: MilestoneInfo) {
        val done = m.status == MilestoneStatus.ACHIEVED
        val next = if (done) "PENDING" else "ACHIEVED"
        val id = m.milestoneId
        vm.track("goal_detail.milestone_check_toggle", mapOf(
            "check_action" to if (done) "uncomplete" else "complete", "is_overdue" to (m.dueDate < KST.today()),
        ))
        pendingId = id
        scope.launch {
            Mutation.run(
                invalidate = listOf(DataKey.goals, DataKey.timetable),
                successMessage = if (done) "달성을 취소했어요." else "마일스톤을 달성했어요. 🎉",
            ) {
                GoalEditAPI.updateMilestone(id, json("status" to next))
            }
            vm.track("goal_detail.structure_result", GD.result() + mapOf(
                "item_kind" to "milestone", "structure_action" to if (done) "uncheck" else "check",
            ))
            pendingId = null
        }
    }

    SectionCard(
        title = "마일스톤",
        modifier = modifier,
        aside = { Text(if (milestones.isEmpty()) "" else "$achieved / ${milestones.size} 달성", style = pt(13), color = NC.ink3) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "목표까지 가는 길의 중간 점검 지점이에요. 그날까지 잘 가고 있는지 확인하고, 동그라미를 눌러 달성을 체크해요." +
                    (if (hasMetric) " 수치는 처음 값 → 목표 값 사이에서 날짜에 맞춰 자동으로 정해져요." else ""),
                style = pt(13),
                color = NC.ink3,
            )

            if (milestones.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 세로 줄 (동그라미들을 잇는 선)
                        .drawBehind {
                            val top = 8.dp.toPx()
                            drawRect(
                                lineStrong,
                                topLeft = Offset(9.dp.toPx(), top),
                                size = Size(2.dp.toPx(), (size.height - top * 2).coerceAtLeast(0f)),
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    milestones.forEachIndexed { i, m ->
                        key(m.milestoneId) {
                            GoalMilestoneRow(
                                goal = goal,
                                m = m,
                                current = m.milestoneId == currentId,
                                isLast = i == milestones.size - 1,
                                pending = pendingId == m.milestoneId,
                                vm = vm,
                                onToggle = { toggle(m) },
                            )
                        }
                    }
                }
            }
            GoalAddButton("마일스톤 추가") {
                vm.track("goal_detail.structure_add_click", mapOf("item_kind" to "milestone"))
                vm.sheet = GoalDetailSheet.milestone(milestone = null, isLast = false)
            }
        }
    }
}

@Composable
private fun GoalMilestoneRow(
    goal: GoalCategory,
    m: MilestoneInfo,
    current: Boolean,
    isLast: Boolean,
    pending: Boolean,
    vm: GoalDetailViewModel,
    onToggle: () -> Unit,
) {
    val done = m.status == MilestoneStatus.ACHIEVED
    val unit = goal.metricUnit ?: ""
    val ringColor = if (done) NC.actionPrimary else if (current) NC.actionPrimary else NC.borderControl
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        // 동그라미를 누르면 달성 / 달성 취소
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (done) NC.actionPrimary else NC.bgSurface, CircleShape)
                .border(3.dp, ringColor, CircleShape)
                .clickable(enabled = !pending, role = Role.Checkbox) { onToggle() }
                .semantics { contentDescription = if (done) "${m.title} 달성 취소" else "${m.title} 달성" },
            contentAlignment = Alignment.Center,
        ) {
            if (done) SfImage("checkmark", size = 9.dp, tint = Color.White)
        }

        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button) {
                    vm.track("goal_detail.structure_edit_open", mapOf("item_kind" to "milestone", "entry_point" to "row"))
                    vm.sheet = GoalDetailSheet.milestone(milestone = m, isLast = isLast)
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "~${GoalFormat.formatDot(m.dueDate)}" + (if (m.status == MilestoneStatus.MISSED) " · 기한 지남" else ""),
                style = pt(12),
                color = if (m.status == MilestoneStatus.MISSED) NC.danger else NC.ink3,
            )
            FlowLayout(spacing = 6.dp) {
                Text(m.title, style = pt(15, PTWeight.bold), color = NC.ink)
                val t = m.targetValue
                if (t != null) {
                    NBadge("${GoalDetailLogic.trim(t, 6)}$unit", tone = if (done) BadgeTone.success else BadgeTone.brand)
                }
                if (m.targetValueManual == true) {
                    Text("직접 정함", style = pt(11, PTWeight.medium), color = NC.ink3)
                }
            }
            val d = m.description
            if (!d.isNullOrEmpty()) {
                Text(d, style = pt(13), color = NC.ink3)
            }
        }

        GoalRowActions(
            label = m.title,
            onEdit = {
                vm.track("goal_detail.structure_edit_open", mapOf("item_kind" to "milestone", "entry_point" to "icon"))
                vm.sheet = GoalDetailSheet.milestone(milestone = m, isLast = isLast)
            },
            onDelete = { vm.ask(GoalDetailConfirm.deleteMilestone(m)) },
        )
    }
}

// MARK: - 마일스톤 추가 · 수정 시트 (웹 MilestoneForm)

/**
 * 수치는 기본으로 "처음 값 → 목표 값" 직선에서 날짜에 맞춰 서버가 계산해요.
 * 꼭 필요할 때만 "이 시점만 직접 정하기"를 펼쳐서 값을 넣고, 언제든 자동으로 되돌릴 수 있어요.
 * isLast: 수치 목표의 마지막 시점은 항상 목표 종료일 · 목표 값 (자동)
 */
@Composable
fun GoalMilestoneFormSheet(
    goal: GoalCategory,
    milestone: MilestoneInfo?,
    isLast: Boolean,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    // 새 점검 시점 기본 날짜: 마지막 시점 + 4주 (없으면 오늘 + 4주), 목표 종료일을 넘지 않게
    val suggested = remember {
        val last = (goal.milestones ?: emptyList()).maxByOrNull { it.dueDate }
        var s = KST.addDays(last?.dueDate ?: KST.today(), 28)
        val end = goal.goalCategoryEndDate
        if (end != null && s > end) s = end
        s
    }
    val manual = milestone?.targetValueManual == true
    var title by remember { mutableStateOf(milestone?.title ?: "") }
    var dueDate by remember { mutableStateOf(milestone?.dueDate ?: suggested) }
    var description by remember { mutableStateOf(milestone?.description ?: "") }
    // 직접 정하기: 이미 직접 정한 값이면 펼친 채로
    var manualOpen by remember { mutableStateOf(manual) }
    var target by remember {
        mutableStateOf(if (manual) (milestone?.targetValue?.let { GoalDetailLogic.trim(it, 6) } ?: "") else "")
    }
    var revertToAuto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.structure_edit_cancel", mapOf("item_kind" to "milestone", "is_new" to (milestone == null), "close_method" to m))
            }
        }
    }
    /** 요청이 끝난 직후 — 성공이면 저장으로 닫혀요 */
    fun trackResult(): Boolean {
        vm.track("goal_detail.structure_result", GD.result() + mapOf(
            "item_kind" to "milestone", "structure_action" to if (milestone == null) "create" else "update",
        ))
        val ok = Mutation.lastErrorStatus == null
        if (ok) outcome = "saved"
        return ok
    }

    val unit = goal.metricUnit ?: ""
    val metric = goal.startValue != null && goal.targetValue != null
    val lockedLast = metric && isLast && goal.goalCategoryEndDate != null
    val targetNum: Double? = if (manualOpen) GoalDetailLogic.toNum(target) else null
    val trimmedTitle = title.trim()
    val error: String? = when {
        trimmedTitle.isEmpty() -> null
        dueDate.isEmpty() -> "날짜를 입력해 주세요."
        targetNum != null && targetNum.isNaN() -> "수치는 숫자로 입력해 주세요."
        else -> null
    }
    val autoNow = run {
        val v = milestone?.targetValue
        if (v == null || revertToAuto) "" else " (지금 ${GoalDetailLogic.trim(v, 6)}$unit)"
    }

    fun submit(close: SheetClose) {
        if (trimmedTitle.isEmpty() || error != null || saving) return
        val t = trimmedTitle
        val desc = description.trim()
        val goalId = goal.goalCategoryId
        val due = dueDate
        val tn = targetNum
        vm.track("goal_detail.structure_save_click", mapOf(
            "item_kind" to "milestone", "is_new" to (milestone == null), "is_target_manual" to manualOpen,
        ))
        saving = true
        if (milestone != null) {
            val o = mutableMapOf<String, JsonElement>(
                "title" to toJsonValue(t),
                "dueDate" to toJsonValue(due),
                "description" to toJsonValue(desc),
            )
            if (manualOpen && tn != null) o["targetValue"] = toJsonValue(tn)
            val autoTarget = revertToAuto || (milestone.targetValueManual == true && !manualOpen)
            if (autoTarget) o["autoTargetValue"] = toJsonValue(true)
            val body = JsonObject(o)
            val id = milestone.milestoneId
            scope.launch {
                val ok = Mutation.run(
                    invalidate = listOf(DataKey.goals, DataKey.timetable),
                    successMessage = if (autoTarget) "자동 계산으로 되돌렸어요." else "마일스톤을 수정했어요.",
                ) {
                    GoalEditAPI.updateMilestone(id, body)
                }
                saving = false
                if (trackResult()) close()
            }
        } else {
            scope.launch {
                val ok = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "마일스톤을 추가했어요.") {
                    GoalEditAPI.createMilestone(
                        goalId = goalId,
                        title = t,
                        description = desc,
                        dueDate = due,
                        targetValue = if (manualOpen) tn else null,
                    )
                }
                saving = false
                if (trackResult()) close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (milestone == null) "마일스톤 추가" else "마일스톤 수정",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                submitLabel = if (milestone == null) "추가" else "저장",
                saving = saving,
                valid = trimmedTitle.isNotEmpty() && error == null,
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close) },
            ) {
                GoalFormField("이름") {
                    NTextField(title, { title = it }, placeholder = "예: 1개월 차 점검", maxLength = 40)
                }
                GoalFormField(if (lockedLast) "날짜 (목표 종료일)" else "날짜") {
                    if (lockedLast) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .background(NC.subtle, RoundedCornerShape(Radius.xl))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(GoalFormat.formatDot(goal.goalCategoryEndDate), style = pt(15), color = NC.ink3)
                        }
                    } else {
                        GoalDateInput(value = dueDate, onValueChange = { dueDate = it })
                    }
                }

                // 수치: 기본은 자동, 필요할 때만 직접
                if (metric) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.subtle.copy(alpha = 0.6f), RoundedCornerShape(Radius.xl))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        val ink2 = NC.ink2
                        if (lockedLast) {
                            Text(
                                buildAnnotatedString {
                                    append("마지막 마일스톤은 항상 ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("목표 값 ${GoalDetailLogic.trim(goal.targetValue ?: 0.0, 6)}$unit")
                                    }
                                    append("이에요. 바꾸려면 목표 수정에서 목표 값을 고쳐 주세요.")
                                },
                                style = pt(13),
                                color = ink2,
                            )
                        } else if (manualOpen) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                                    GoalFormField("이 시점 목표 (${unit.ifEmpty { "값" }})", modifier = Modifier.width(150.dp)) {
                                        NTextField(
                                            target,
                                            { target = it },
                                            placeholder = milestone?.targetValue?.let { GoalDetailLogic.trim(it, 6) } ?: "예: 76",
                                            keyboardType = KeyboardType.Decimal,
                                        )
                                    }
                                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            "자동으로 되돌리기",
                                            style = pt(13, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                                            color = NC.ink2,
                                            modifier = Modifier.clickable(role = Role.Button) {
                                                manualOpen = false
                                                target = ""
                                                revertToAuto = milestone?.targetValueManual == true
                                            },
                                        )
                                    }
                                }
                                Text(
                                    "직접 정한 값은 자동 계산에서 빠져요. 목표의 처음 값·목표 값·종료일을 바꾸면 다시 자동으로 돌아가요. 처음 값과 목표 값 사이를 벗어난 값은 자동으로 바뀌어요.",
                                    style = pt(12),
                                    color = NC.ink3,
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    buildAnnotatedString {
                                        append("이 시점 수치는 날짜에 맞춰 ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("자동으로 계산") }
                                        append("돼요$autoNow.")
                                    },
                                    style = pt(13),
                                    color = ink2,
                                )
                                Text(
                                    "이 시점만 직접 정하기",
                                    style = pt(12, PTWeight.semibold).copy(textDecoration = TextDecoration.Underline),
                                    color = NC.ink3,
                                    modifier = Modifier.clickable(role = Role.Button) { manualOpen = true },
                                )
                            }
                        }
                    }
                }

                GoalFormField("설명 (선택)") {
                    NTextField(description, { description = it }, maxLength = 300)
                }
            }
        }
    }
}

// MARK: - 세부 목표 (영역)

/** 루틴·할 일을 묶어 보는 라벨. 없어도 돼요. 안 고른 루틴·할 일은 "기타 할 일" 그릇에 들어가고, 그 그릇은 여기 보여주지 않아요. */
@Composable
fun GoalSubGoalsSection(
    goal: GoalCategory,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    val subs = GoalDetailLogic.areas(goal)
    SectionCard(
        title = "세부 목표",
        modifier = modifier,
        aside = { Text("${subs.size}개", style = pt(13), color = NC.ink3) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "목표를 이루려고 나눈 갈래예요 (예: 필기 대비 · 실기 대비). 루틴·Task에 붙는 이름표 역할이라, 없어도 괜찮아요.",
                style = pt(13),
                color = NC.ink3,
            )
            // 세로로 쌓아요 — 좁은 칸에서 가로로 나누면 글자가 끊겨 읽기 어려워요
            for (sg in subs) {
                key(sg.generalCategoryId) {
                    GoalSubGoalCard(goal = goal, sub = sg, vm = vm)
                }
            }
            GoalAddButton("세부 목표 추가") {
                vm.track("goal_detail.structure_add_click", mapOf("item_kind" to "sub_goal"))
                vm.sheet = GoalDetailSheet.subGoal(sub = null)
            }
        }
    }
}

@Composable
private fun GoalSubGoalCard(goal: GoalCategory, sub: SubGoalInfo, vm: GoalDetailViewModel) {
    val ink3 = NC.ink3
    val ink4 = NC.ink4
    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.bgSunken, RoundedCornerShape(Radius.xxl))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(goalColor(sub.colorCode ?: goal.colorCode), CircleShape),
            )
            Spacer(Modifier.weight(1f))
            GoalRowActions(
                label = sub.generalCategoryName,
                onEdit = {
                    vm.track("goal_detail.structure_edit_open", mapOf("item_kind" to "sub_goal", "entry_point" to "icon"))
                    vm.sheet = GoalDetailSheet.subGoal(sub = sub)
                },
                onDelete = { vm.ask(GoalDetailConfirm.deleteSubGoal(sub)) },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    vm.track("goal_detail.structure_edit_open", mapOf("item_kind" to "sub_goal", "entry_point" to "row"))
                    vm.sheet = GoalDetailSheet.subGoal(sub = sub)
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                (sub.emoji?.let { "$it " } ?: "") + sub.generalCategoryName,
                style = pt(15, PTWeight.bold),
                color = NC.ink,
            )
            // AI 가 정한 실천 원칙 (예: 식단 — 하루 1,600kcal, 단백질 90g …)
            val d = sub.description
            if (!d.isNullOrEmpty()) {
                Text(d, style = pt(13).copy(lineHeight = 19.sp), color = NC.ink2)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ink3)) { append("루틴 ${sub.routines.size}개") }
                    val s = sub.generalCategoryStartDate
                    if (s != null) {
                        val from = GoalFormat.formatDot(s).drop(5)
                        val to = GoalFormat.formatDot(sub.generalCategoryEndDate).drop(5)
                        withStyle(SpanStyle(color = ink4)) { append(" · $from–$to") }
                    }
                },
                style = pt(13),
            )
        }
    }
}

// MARK: - 세부 목표 추가 · 수정 시트 (웹 SubGoalForm)

@Composable
fun GoalSubGoalFormSheet(
    goal: GoalCategory,
    sub: SubGoalInfo?,
    colors: List<UserColor>,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    val period = remember { GoalDetailLogic.defaultPeriod(goal) }
    var name by remember { mutableStateOf(sub?.generalCategoryName ?: "") }
    var start by remember { mutableStateOf(sub?.generalCategoryStartDate ?: period.start) }
    var end by remember { mutableStateOf(sub?.generalCategoryEndDate ?: period.end) }
    var colorId by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.structure_edit_cancel", mapOf("item_kind" to "sub_goal", "is_new" to (sub == null), "close_method" to m))
            }
        }
    }

    val effectiveColor = colorId ?: GoalDetailLogic.colorIdOf(colors, sub?.colorCode ?: goal.colorCode)
    val trimmedName = name.trim()
    val error: String? = when {
        trimmedName.isEmpty() -> null
        start.isEmpty() || end.isEmpty() -> "기간을 입력해 주세요."
        start > end -> "종료일이 시작일보다 빨라요."
        else -> null
    }

    fun submit(close: SheetClose) {
        val color = effectiveColor ?: return
        if (trimmedName.isEmpty() || error != null || saving) return
        val n = trimmedName
        val s = start
        val e = end
        val goalId = goal.goalCategoryId
        val picked = colorId
        vm.track("goal_detail.structure_save_click", mapOf("item_kind" to "sub_goal", "is_new" to (sub == null)))
        saving = true
        scope.launch {
            if (sub != null) {
                Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "세부 목표를 수정했어요.") {
                    GoalEditAPI.updateSubGoal(id = sub.generalCategoryId, name = n, start = s, end = e, colorId = picked)
                }
            } else {
                Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "세부 목표를 추가했어요.") {
                    GoalEditAPI.createSubGoal(goalId = goalId, name = n, start = s, end = e, colorId = color)
                }
            }
            saving = false
            val ok = Mutation.lastErrorStatus == null
            vm.track("goal_detail.structure_result", GD.result() + mapOf(
                "item_kind" to "sub_goal", "structure_action" to if (sub == null) "create" else "update",
            ))
            if (ok) {
                outcome = "saved"
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (sub == null) "세부 목표 추가" else "세부 목표 수정",
        dismissible = !saving,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                submitLabel = if (sub == null) "추가" else "저장",
                saving = saving,
                valid = trimmedName.isNotEmpty() && error == null && effectiveColor != null,
                error = error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close) },
            ) {
                GoalFormField("세부 목표 이름") {
                    NTextField(name, { name = it }, placeholder = "예: 식단 관리, 단어 외우기", maxLength = 30)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    GoalFormField("시작일", modifier = Modifier.weight(1f)) {
                        GoalDateInput(value = start, onValueChange = { start = it })
                    }
                    GoalFormField("종료일", modifier = Modifier.weight(1f)) {
                        GoalDateInput(value = end, onValueChange = { end = it })
                    }
                }
                GoalFormField("색상") {
                    ColorPickerRow(colors = colors, selectedId = effectiveColor, onSelectedIdChange = { colorId = it })
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1200, name = "세부 구조")
@Composable
private fun GoalStructurePanelPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(
                Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalStructurePanel(goal = GoalPreviewData.goal)
                // 펼친 모습도 같이 (미리보기에선 누를 수 없어서)
                GoalMilestonesSection(goal = GoalPreviewData.goal)
                GoalSubGoalsSection(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700, name = "마일스톤 수정")
@Composable
private fun GoalMilestoneFormSheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        val goal = GoalPreviewData.goal
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            GoalMilestoneFormSheet(goal = goal, milestone = goal.milestones?.getOrNull(1), isLast = false, onDismiss = {})
        }
    }
}
