// feature/goal/GoalOutcomeSection.kt
//
// iOS Features/Goal/GoalOutcomeSection.swift
// 웹 features/goal/ui/GoalOutcomeSection.tsx
//  - GoalWrapUpSection : 진행 중인 목표를 결과와 함께 마무리 (모달 → 시트)
//  - GoalOutcomeSection: 끝난 목표의 결과 (자동 판정이면 직접 골라 달라고 해요)
//  - GoalAdjustmentBanner: 주간 점검 제안 (2주 연속 실천률이 낮으면 하루 줄이기, 높으면 하루 늘리기)

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 결과 선택지 (웹 OUTCOME_OPTIONS) */
data class GoalOutcomeOption(
    val value: OutcomeResult,
    val emoji: String,
    val label: String,
    val hint: String,
) {
    val id: OutcomeResult get() = value

    companion object {
        val all: List<GoalOutcomeOption> = listOf(
            GoalOutcomeOption(value = OutcomeResult.ACHIEVED, emoji = "🎉", label = "달성했어요", hint = "목표한 만큼 해냈어요"),
            GoalOutcomeOption(value = OutcomeResult.PARTIAL, emoji = "🙂", label = "일부 달성", hint = "절반 이상은 해냈어요"),
            GoalOutcomeOption(value = OutcomeResult.NOT_ACHIEVED, emoji = "😣", label = "못 했어요", hint = "이번엔 어려웠어요"),
        )

        fun of(r: OutcomeResult): GoalOutcomeOption = all.firstOrNull { it.value == r } ?: all[1]
    }
}

@Composable
internal fun GoalOutcomePicker(value: OutcomeResult?, disabled: Boolean = false, onChange: (OutcomeResult) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in GoalOutcomeOption.all) {
            val selected = value == o.value
            val shape = RoundedCornerShape(Radius.xxl)
            Column(
                Modifier
                    .weight(1f)
                    .alpha(if (disabled) 0.6f else 1f)
                    .clip(shape)
                    .background(if (selected) NC.brandSoft else Color.Transparent, shape)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.borderFocus else NC.borderSubtle, shape)
                    .clickable(enabled = !disabled, role = Role.RadioButton) { onChange(o.value) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(o.emoji, style = pt(22).copy(fontSize = 22.sp))
                Text(o.label, style = pt(13, PTWeight.bold), color = if (selected) NC.textBrand else NC.ink)
            }
        }
    }
}

// MARK: - 진행 중: 목표 마무리

/** 진행 중인 달성형 목표를 결과와 함께 끝내요. 고른 결과는 다음 목표 추천의 근거가 돼요 */
@Composable
fun GoalWrapUpSection(
    goal: GoalCategory,
    metricDone: Boolean,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    val periodOver = goal.goalCategoryEndDate?.let { it < KST.today() } ?: false
    val strong = metricDone || periodOver
    val lead = when {
        metricDone -> "목표값에 도달했어요! 결과를 남기고 마무리해 볼까요?"
        periodOver -> "기간이 끝났어요. 어땠는지 남기고 마무리해요."
        else -> "목표를 끝냈다면 결과를 남겨 주세요. 다음 목표를 짤 때 참고해요."
    }
    SectionCard(title = "목표 마무리", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                lead,
                style = pt(14, if (strong) PTWeight.semibold else PTWeight.regular),
                color = if (strong) NC.ink else NC.ink2,
            )
            NButton(
                "마무리하기",
                variant = if (strong) NButtonVariant.primary else NButtonVariant.secondary,
                icon = "flag",
                fullWidth = true,
            ) {
                vm.track("goal_detail.wrapup_open", mapOf(
                    "wrapup_prompt" to goalWrapUpPrompt(goal, metricDone), "entry_point" to "plan_card",
                ))
                vm.sheet = GoalDetailSheet.wrapUp
            }
        }
    }
}

/** "이 목표, 어땠나요?" 시트 */
@Composable
fun GoalWrapUpSheet(
    goal: GoalCategory,
    metricDone: Boolean,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    var result by remember { mutableStateOf(if (metricDone) OutcomeResult.ACHIEVED else null) }
    var achievement by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 어떻게 닫혔는지 (cancel_button · saved, 없으면 X · 쓸어 닫기)
    var outcome by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.wrapup_cancel", mapOf("close_method" to m, "outcome_result" to result?.rawValue))
            }
        }
    }

    fun submit(close: SheetClose) {
        val r = result ?: return
        if (saving) return
        val trimmed = achievement.trim()
        val a: String? = trimmed.ifEmpty { null }
        val id = goal.goalCategoryId
        vm.track("goal_detail.wrapup_submit", mapOf(
            "outcome_result" to r.rawValue, "achievement_length" to trimmed.length,
            "was_preselected" to (metricDone && r == OutcomeResult.ACHIEVED), "wrapup_prompt" to goalWrapUpPrompt(goal, metricDone),
        ))
        saving = true
        scope.launch {
            val ok = Mutation.run(
                successMessage = "목표를 마무리했어요. 수고했어요!",
                errorFallback = "마무리하지 못했어요. 다시 시도해 주세요.",
            ) {
                GoalEditAPI.completeGoal(goalId = id, endDate = KST.today(), achievement = a, result = r)
            }
            saving = false
            vm.track("goal_detail.wrapup_result", GD.result() + mapOf("outcome_result" to r.rawValue))
            if (ok != null) {
                outcome = "saved"
                // 웹: 성공했을 때만 진행 · 목록 · Task · 캘린더를 다시 불러와요
                // 서버가 남은 루틴을 끝내고 앞날의 일정을 지워서, 캘린더 · 홈도 다시 불러와요
                Invalidation.invalidate(DataKey.goals, DataKey.timetable, DataKey.home)
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "이 목표, 어땠나요?",
        description = "오늘 날짜로 마무리해요. 남은 기간의 루틴과 예정된 할 일은 캘린더에서 빠지고, 지난 기록과 완료한 일은 그대로 남아요.",
        dismissible = !saving,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                outcome = "cancel_button"
                close()
            }
            NButton("마무리하기", loading = saving, enabled = result != null) { submit(close) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GoalOutcomePicker(value = result, disabled = saving) {
                vm.track("goal_detail.wrapup_outcome_select", mapOf("outcome_result" to it.rawValue))
                result = it
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("한 줄 소감 (선택)", style = pt(13, PTWeight.semibold), color = NC.ink2)
                NTextField(
                    value = achievement,
                    onValueChange = { achievement = it },
                    placeholder = "예) 주 3회 운동은 지켰는데 식단은 어려웠어요",
                    minHeight = 80.dp,
                    singleLine = false,
                    enabled = !saving,
                    maxLength = 500,
                )
            }
        }
    }
}

// MARK: - 끝난 목표: 결과

@Composable
fun GoalOutcomeSection(
    goalId: Int,
    outcome: GoalOutcome?,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun choose(r: OutcomeResult) {
        if (saving) return
        vm.track("goal_detail.outcome_select", mapOf(
            "outcome_result" to r.rawValue, "outcome_source" to (outcome?.source ?: "none"), "is_change" to editing,
            "auto_result" to outcome?.autoResult?.rawValue,
            "is_match" to outcome?.autoResult?.let { it == r },
        ))
        saving = true
        scope.launch {
            val res = Mutation.run(successMessage = "결과를 남겼어요.", errorFallback = "저장하지 못했어요. 다시 시도해 주세요.") {
                // 응답이 비어도 성공인지 알 수 있게 Pair 로 감싸요
                Pair(GoalEditAPI.updateOutcome(goalId = goalId, result = r), true)
            }
            saving = false
            vm.track("goal_detail.outcome_result", GD.result() + mapOf("outcome_result" to r.rawValue))
            if (res != null) {
                editing = false
                res.first?.let { vm.progress = it }
            }
        }
    }

    val ask = outcome == null || outcome.source == "AUTO" || editing
    SectionCard(
        title = "목표 결과",
        modifier = modifier,
        aside = {
            if (outcome?.source == "USER" && !editing) {
                Text(
                    "바꾸기",
                    style = pt(13, PTWeight.semibold),
                    color = NC.ink3,
                    modifier = Modifier.clickable(role = Role.Button) {
                        vm.track("goal_detail.outcome_change_click", mapOf("outcome_action" to "open"))
                        editing = true
                    },
                )
            }
        },
    ) {
        Column {
            if (outcome != null && !editing) {
                val o = GoalOutcomeOption.of(outcome.result)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${o.emoji} ${o.label}", style = NType.h4, color = NC.ink)
                    if (outcome.source == "AUTO") {
                        Text("기록으로 자동 판정", style = pt(12, PTWeight.medium), color = NC.ink3, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
            if (outcome != null) {
                GoalOutcomeStats(outcome = outcome)
            }
            if (ask) {
                Column(
                    Modifier.padding(top = if (outcome != null) 16.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!editing) {
                        LaunchedEffect(Unit) {
                            if (vm.once("outcome_ask")) {
                                vm.track("goal_detail.outcome_ask_view", mapOf(
                                    "outcome_source" to (outcome?.source ?: "none"),
                                    "auto_result" to outcome?.autoResult?.rawValue,
                                    "auto_reason" to outcome?.autoReason,
                                ))
                            }
                        }
                    }
                    if (outcome != null && !editing) {
                        Box(Modifier.padding(bottom = 6.dp).fillMaxWidth().height(1.dp).background(NC.line))
                    }
                    Text(
                        if (editing) "결과를 다시 골라 주세요" else "이 목표 어땠나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요",
                        style = pt(14, PTWeight.semibold),
                        color = NC.ink2,
                    )
                    GoalOutcomePicker(value = if (editing) outcome?.result else null, disabled = saving) { choose(it) }
                    if (editing) {
                        Text(
                            "취소",
                            style = pt(13, PTWeight.semibold),
                            color = NC.ink3,
                            modifier = Modifier.clickable(role = Role.Button) {
                                vm.track("goal_detail.outcome_change_click", mapOf("outcome_action" to "cancel"))
                                editing = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalOutcomeStats(outcome: GoalOutcome) {
    val list = buildList {
        outcome.metricPercent?.let { add("수치 ${GoalDetailLogic.trim(it, 1)}%") }
        outcome.routineRate?.let { add("루틴 실천 ${GoalDetailLogic.trim(it, 1)}%") }
        outcome.taskRate?.let { add("할 일 완료 ${GoalDetailLogic.trim(it, 1)}%") }
    }
    val autoNote = outcome.autoMessage?.takeIf { outcome.source == "AUTO" }
    if (list.isEmpty() && outcome.metricUnrecorded != true && autoNote == null) return
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isNotEmpty()) {
            FlowLayout(spacing = 8.dp) {
                for (s in list) {
                    Text(
                        s,
                        style = ptMono(13, PTWeight.medium),
                        color = NC.ink2,
                        modifier = Modifier
                            .background(NC.subtle, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (outcome.metricUnrecorded == true) {
            Text("수치 기록이 없어서 실천률로 판정했어요.", style = pt(13), color = NC.ink3)
        }
        autoNote?.let { Text(it, style = pt(13), color = NC.ink3) }
    }
}

// MARK: - 진행 중: 주간 점검 제안

@Composable
fun GoalAdjustmentBanner(
    goalId: Int,
    adjustment: GoalAdjustment,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    modifier: Modifier = Modifier,
) {
    var applying by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val down = adjustment.direction == "DOWN"
    val busy = applying || dismissing
    val day = adjustment.day.label

    LaunchedEffect(adjustment.key) {
        if (vm.once("adjust:${adjustment.key}")) {
            vm.track("goal_detail.adjust_banner_view", mapOf(
                "adjust_direction" to adjustment.direction,
                "previous_rate" to adjustment.previousWeekRate, "last_rate" to adjustment.lastWeekRate,
            ))
        }
    }

    fun apply() {
        if (busy) return
        val key = adjustment.key
        applying = true
        scope.launch {
            // 실패해도(이미 루틴이 바뀐 STALE 등) 새 진행 정보로 제안을 다시 받아요 → 항상 DataKey.goals
            val r = Mutation.run(
                invalidate = listOf(DataKey.goals),
                successMessage = "내일부터 바뀐 요일로 진행해요.",
                errorFallback = "바꾸지 못했어요. 다시 시도해 주세요.",
            ) {
                Pair(GoalEditAPI.applyAdjustment(goalId = goalId, key = key), true)
            }
            applying = false
            vm.track("goal_detail.adjust_result", GD.result() + mapOf("adjust_action" to "apply"))
            if (r != null) {
                r.first?.let { vm.progress = it }
                // 루틴 요일이 바뀌면 캘린더도 달라져요
                Invalidation.invalidate(DataKey.timetable)
            }
        }
    }

    fun dismiss(method: String) {
        if (busy) return
        vm.track("goal_detail.adjust_dismiss_click", mapOf("adjust_direction" to adjustment.direction, "close_method" to method))
        val key = adjustment.key
        dismissing = true
        scope.launch {
            val p = Mutation.run(successMessage = null, errorFallback = "처리하지 못했어요. 다시 시도해 주세요.") {
                GoalEditAPI.dismissAdjustment(goalId = goalId, key = key)
            }
            dismissing = false
            vm.track("goal_detail.adjust_result", GD.result() + mapOf("adjust_action" to "dismiss"))
            if (p != null) vm.progress = p
        }
    }

    val shape = RoundedCornerShape(Radius.xxl)
    val accent = if (down) NC.warningInk else NC.brand
    Column(
        modifier
            .fillMaxWidth()
            .background(if (down) NC.warningSoft else NC.brandSoft, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            SfImage(if (down) "calendar.badge.minus" else "calendar.badge.plus", size = 14.dp, tint = accent, modifier = Modifier.padding(top = 2.dp))
            Text("주간 점검 · ${adjustment.routineName}", style = pt(14, PTWeight.bold), color = accent, modifier = Modifier.weight(1f))
            NIconButton(
                "xmark",
                size = 14.dp,
                color = NC.ink3,
                label = "이번엔 넘기기",
                enabled = !busy,
                modifier = Modifier.offset(x = 8.dp, y = (-6).dp),
            ) { dismiss("x_button") }
        }
        Text(adjustment.message, style = pt(14), color = NC.ink2)
        Text(
            "지난 2주 실천률 ${GoalDetailLogic.trim(adjustment.previousWeekRate, 1)}% → ${GoalDetailLogic.trim(adjustment.lastWeekRate, 1)}% · 주 ${adjustment.fromDays}회 → ${adjustment.toDays}회",
            style = ptMono(13),
            color = NC.ink3,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NButton(
                if (down) "${day}요일 빼기" else "${day}요일 더하기",
                size = NButtonSize.sm,
                loading = applying,
                enabled = !busy,
            ) {
                if (!busy) vm.track("goal_detail.adjust_apply_click", mapOf("adjust_direction" to adjustment.direction))
                apply()
            }
            NButton("지금 그대로 할게요", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !busy) { dismiss("keep_button") }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1100, name = "결과 · 점검")
@Composable
private fun GoalOutcomeSectionPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        val p = GoalPreviewData.progress
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(
                Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val adj = p.adjustment
                if (adj != null) GoalAdjustmentBanner(goalId = 1, adjustment = adj)
                GoalOutcomeSection(goalId = 1, outcome = GoalPreviewData.taskOnlyProgress.outcome)
                GoalOutcomeSection(goalId = 1, outcome = null)
                GoalWrapUpSection(goal = GoalPreviewData.goal, metricDone = false)
            }
        }
    }
}
