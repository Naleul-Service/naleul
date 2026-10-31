// feature/goal/GoalDetailHeader.kt
//
// iOS Features/Goal/GoalDetailHeader.swift
// 웹 features/goal/ui/GoalHeader.tsx — 목표 상세 머리말(이름·상태·기간) · 목표 수정 폼 · 시작한 이유/AI 노트 카드.
// 웹은 연필을 누르면 그 자리가 수정 폼으로 바뀌었어요 → 앱은 "수정" 버튼으로 시트를 열어요.
// 삭제는 폼 안의 "목표 삭제" → 확인창.

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// MARK: - 머리말

@Composable
fun GoalDetailHeader(
    goal: GoalCategory,
    vm: GoalDetailViewModel? = LocalGoalDetailViewModel.current,
    modifier: Modifier = Modifier,
) {
    val period = GoalFormat.periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    val emoji = goal.emoji
    val titleText = if (!emoji.isNullOrEmpty()) "$emoji ${goal.goalCategoryName}" else goal.goalCategoryName
    // 업무형 + 종류 (회사 업무 등)
    val sub = goal.goalSubType
    val recordBadge = if (goal.goalType == GoalType.WORK && sub != null) "업무형 · ${GoalKind.subOption(sub).label}" else "업무형"

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(goalColor(goal.colorCode), CircleShape))
            Text(titleText, style = NType.h2, color = NC.textPrimary)
        }
        FlowLayout(spacing = 6.dp) {
            NBadge(
                goal.goalCategoryStatus.label,
                tone = if (goal.goalCategoryStatus == GoalStatus.COMPLETED) BadgeTone.success else BadgeTone.neutral,
            )
            if (goal.isTemporary) NBadge("임시 목표", tone = BadgeTone.warning)
            if (goal.isRecordGoal) NBadge(recordBadge, tone = BadgeTone.neutral)
            val kind = goal.goalKindName
            if (kind != null && goal.goalType != GoalType.WORK) NBadge(kind, tone = BadgeTone.brand)
            Row(
                Modifier
                    .height(24.dp)
                    .clickable(role = Role.Button) { vm?.sheet = GoalDetailSheet.editGoal }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SfImage("pencil", size = 12.dp, tint = NC.ink3)
                Text("수정", style = pt(13, PTWeight.semibold), color = NC.ink3)
            }
            // AI 없이 만든 목표형 목표 → 처음엔 시트로 한 번, 그 뒤로는 이 버튼으로 (2026-10)
            if (GoalLegacy.isLegacy(goal)) {
                Row(
                    Modifier
                        .height(24.dp)
                        .background(NC.aiTintSoft, RoundedCornerShape(Radius.lg))
                        .clickable(role = Role.Button) {
                            vm?.track("goal_detail.legacy_setup_button_click")
                            vm?.sheet = GoalDetailSheet.legacySetup("button")
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("sparkles", size = 12.dp, tint = NC.textBrand)
                    Text("목표 형태 정하기", style = pt(13, PTWeight.semibold), color = NC.textBrand)
                }
            }
        }
        val start = goal.goalCategoryStartDate
        if (start != null) {
            val end = goal.goalCategoryEndDate?.let { GoalFormat.formatDot(it) } ?: "종료일 미정"
            val weeks = period?.let { " · ${it.weeks}주" } ?: ""
            Text("${GoalFormat.formatDot(start)} – $end$weeks", style = pt(14), color = NC.ink3)
        }
    }
}

// MARK: - 시작한 이유 · AI 노트 카드 (웹 GoalNotes)

@Composable
fun GoalNotesCard(
    goal: GoalCategory,
    vm: GoalDetailViewModel? = LocalGoalDetailViewModel.current,
    modifier: Modifier = Modifier,
) {
    val motive = goal.motive
    val note = goal.aiNote
    if (motive == null && note == null) return
    Column(
        modifier
            .fillMaxWidth()
            .card()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (motive != null) {
            // 누르면 목표 수정을 열어요 (머리말 수정에서 함께 바뀌어요)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        vm?.track("goal_detail.edit_open", mapOf("goal_mode" to goal.analyticsMode, "entry_point" to "notes"))
                        vm?.sheet = GoalDetailSheet.editGoal
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(GoalDetailLogic.motiveLabel(goal), style = pt(13, PTWeight.medium), color = NC.ink3)
                Text(motive, style = pt(15).copy(lineHeight = 22.sp), color = NC.ink)
            }
        }
        if (note != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .aiBorder(RoundedCornerShape(Radius.xxl), glass = false)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AIBadge(AIBadgeSize.sm)
                Text(note, style = pt(14).copy(lineHeight = 20.sp), color = NC.ink)
            }
        }
    }
}

// MARK: - 목표 수정 시트 (웹 GoalEditForm)

/**
 * 이름·이모지·기간·색·시작한 이유·수치 목표.
 * 수치는 "시작값 · 목표값 · 종료일" 세 가지만 정해요. 마일스톤 수치는 이 값으로 자동 계산되고,
 * "현재" 값은 "지금 어디쯤?"에서 남긴 기록으로만 바뀌어요.
 */
@Composable
fun GoalEditFormSheet(
    goal: GoalCategory,
    colors: List<UserColor>,
    vm: GoalDetailViewModel = LocalGoalDetailViewModel.current!!,
    onDismiss: () -> Unit = { vm.sheet = null },
) {
    fun numStr(v: Double?): String = v?.let { GoalDetailLogic.trim(it, 6) } ?: ""

    var emoji by remember { mutableStateOf(goal.emoji ?: "") }
    var name by remember { mutableStateOf(goal.goalCategoryName) }
    var start by remember { mutableStateOf(goal.goalCategoryStartDate ?: "") }
    var end by remember { mutableStateOf(goal.goalCategoryEndDate ?: "") }
    var colorId by remember { mutableStateOf<Int?>(null) }
    var motive by remember { mutableStateOf(goal.motive ?: "") }
    var metricName by remember { mutableStateOf(goal.metricName ?: "") }
    var metricUnit by remember { mutableStateOf(goal.metricUnit ?: "") }
    var startValue by remember { mutableStateOf(numStr(goal.startValue)) }
    var targetValue by remember { mutableStateOf(numStr(goal.targetValue)) }
    var kind by remember {
        val t = goal.goalType
        val s = goal.goalSubType
        mutableStateOf(if (t != null && s != null) GoalKindValue(goalType = t, goalSubType = s, goalKindLabel = goal.goalKindLabel ?: "") else null)
    }
    var showMetric by remember { mutableStateOf(goal.targetValue != null || !(goal.metricName ?: "").isEmpty()) }
    // 수치를 지우려고 하는지 ("수치 없이 진행하기")
    var clearMetric by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 업무형은 기한 · 수치 · 카테고리가 없어요 (마감은 주제마다, V53) → 기간 · 수치 · 카테고리 칸을 숨겨요
    val isWork = goal.isRecordGoal
    // 기본 생활 습관 그릇(V52)은 이름 · 이모지 · 색만 바꿀 수 있어요 (지울 수도 없어요)
    val isHabit = goal.isHabitGoal
    val hadMetric = goal.targetValue != null || !(goal.metricName ?: "").isEmpty()
    val effectiveColorId = colorId ?: GoalDetailLogic.colorIdOf(colors, goal.colorCode)
    val nums = listOf(GoalDetailLogic.toNum(startValue), GoalDetailLogic.toNum(targetValue))
    val trimmedName = name.trim()

    val error: String? = run {
        if (trimmedName.isEmpty()) return@run "목표 이름을 입력해 주세요."
        if (start.isNotEmpty() && end.isNotEmpty() && start > end) return@run "종료일이 시작일보다 빨라요."
        if (nums.any { it?.isNaN() == true }) return@run "수치는 숫자로 입력해 주세요."
        val k = kind
        if (!isWork && !isHabit && k != null && !GoalKind.isComplete(k)) return@run "기타 카테고리 이름을 1~10자로 적어 주세요."
        null
    }

    // ── 앰플리튜드 ──
    // 시트가 어떻게 닫혔는지 (cancel_button · saved · deleted, 없으면 X · 쓸어 닫기)
    var outcome by remember { mutableStateOf<String?>(null) }
    /** 화면에 보이는 오류 문구 (이름이 비면 문구 없이 막혀요) */
    val shownError = if (trimmedName.isEmpty()) null else error
    /** 수치 칸 상태 (add · clear · update · none) */
    val metricAction = when {
        clearMetric -> "clear"
        showMetric && !hadMetric -> "add"
        showMetric -> "update"
        else -> "none"
    }
    /** 바뀐 칸들 (name, emoji, period, kind, color, motive, metric) */
    val changedFields = buildList {
        if (trimmedName != goal.goalCategoryName) add("name")
        if (emoji.trim() != (goal.emoji ?: "")) add("emoji")
        if (!isWork && !isHabit && (start != (goal.goalCategoryStartDate ?: "") || end != (goal.goalCategoryEndDate ?: ""))) add("period")
        val k = kind
        if (!isWork && !isHabit && k != null && GoalKind.isComplete(k) &&
            (k.goalType != goal.goalType || k.goalSubType != goal.goalSubType || (k.goalKindLabel ?: "") != (goal.goalKindLabel ?: ""))
        ) add("kind")
        if (colorId != null) add("color")
        if (motive.trim() != (goal.motive ?: "")) add("motive")
        if (!isWork && !isHabit) {
            if (clearMetric) add("metric")
            else if (showMetric && (metricName != (goal.metricName ?: "") || metricUnit != (goal.metricUnit ?: "") ||
                    nums[0] != goal.startValue || nums[1] != goal.targetValue)) add("metric")
        }
    }
    val isDirtyNow by rememberUpdatedState(changedFields.isNotEmpty())
    // iOS .onChange 처럼 처음 열 때부터 있던 오류는 보내지 않아요
    val initialError = remember { shownError }
    var errorSeenChange by remember { mutableStateOf(false) }
    LaunchedEffect(shownError) {
        if (shownError != initialError) errorSeenChange = true
        if (errorSeenChange) shownError?.let { vm.track("goal_detail.edit_error", mapOf("validation_error" to it)) }
    }
    DisposableEffect(Unit) {
        onDispose {
            GD.closeMethod(outcome)?.let { m ->
                vm.track("goal_detail.edit_cancel", mapOf("close_method" to m, "is_dirty" to isDirtyNow))
            }
        }
    }

    fun submit(close: SheetClose, method: String) {
        if (error != null || saving) return
        vm.track("goal_detail.edit_save", mapOf(
            "goal_mode" to goal.analyticsMode, "changed_fields" to changedFields.joinToString(","),
            "metric_action" to "none", "submit_method" to method,
        ))
        val o = mutableMapOf<String, JsonElement>(
            "goalCategoryName" to toJsonValue(trimmedName),
            "emoji" to toJsonValue(emoji.trim()),
            "motive" to toJsonValue(motive.trim()),
        )
        if (start.isNotEmpty() && !isHabit) o["goalCategoryStartDate"] = toJsonValue(start)
        // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
        if (end.isNotEmpty() && !isWork && !isHabit) o["goalCategoryEndDate"] = toJsonValue(end)
        colorId?.let { o["colorId"] = toJsonValue(it) }
        // 카테고리는 2단계까지 고른 경우에만 (고르다 만 건 기존 값 유지) · 업무형 · 습관은 카테고리 없음
        if (!isWork && !isHabit && GoalKind.isComplete(kind)) {
            o.putAll(GoalKind.body(kind))
        }
        // 수치 목표는 "수치 수정" 시트에서 따로 보내요 (2026-10)
        val body = JsonObject(o)
        val goalId = goal.goalCategoryId
        saving = true
        scope.launch {
            val ok = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "목표를 수정했어요.") {
                GoalEditAPI.updateGoal(goalId, body)
            }
            saving = false
            vm.track("goal_detail.edit_result", GD.result())
            if (ok != null) {
                outcome = "saved"
                close()
            }
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = "목표 수정",
        dismissible = !saving && !deleting,
        fullHeight = true,
    ) { close ->
        GoalSheetForm {
            GoalInlineForm(
                saving = saving,
                valid = error == null,
                error = if (trimmedName.isEmpty()) null else error,
                onCancel = {
                    outcome = "cancel_button"
                    close()
                },
                onSubmit = { submit(close, "button") },
                extra = {
                    if (!isHabit) {
                        Row(
                            Modifier
                                .clickable(enabled = !saving && !deleting, role = Role.Button) {
                                    vm.track("goal_detail.delete_click", mapOf(
                                        "goal_mode" to goal.analyticsMode, "goal_status" to goal.goalCategoryStatus.name,
                                    ))
                                    confirmDelete = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SfImage("trash", size = 12.dp, tint = NC.danger)
                            Text("목표 삭제", style = pt(13, PTWeight.semibold), color = NC.danger)
                        }
                    }
                },
            ) {
                // ── 이모지 · 이름 ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    GoalFormField("이모지", modifier = Modifier.width(72.dp)) {
                        NTextField(
                            value = emoji,
                            onValueChange = { emoji = it },
                            placeholder = "🎯",
                            maxLength = 16,
                            textStyle = pt(15).copy(textAlign = TextAlign.Center),
                        )
                    }
                    GoalFormField("목표 이름", modifier = Modifier.weight(1f)) {
                        NTextField(
                            value = name,
                            onValueChange = { name = it },
                            maxLength = 50,
                            onSubmit = { if (error == null) submit(close, "keyboard_done") },
                        )
                    }
                }
                if (!isWork && !isHabit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        GoalFormField("시작일", modifier = Modifier.weight(1f)) {
                            GoalDateInput(value = start, onValueChange = { start = it }, optional = true)
                        }
                        GoalFormField("종료일", modifier = Modifier.weight(1f)) {
                            GoalDateInput(value = end, onValueChange = { end = it }, optional = true)
                        }
                    }
                    // 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 카테고리를 고르지 않아요
                    GoalFormField("카테고리") {
                        GoalKindPicker(value = kind, onValueChange = { kind = it }, compact = true)
                    }
                }
                GoalFormField("색상") {
                    ColorPickerRow(colors = colors, selectedId = effectiveColorId, onSelectedIdChange = { colorId = it })
                }
                if (!isHabit) {
                    GoalFormField(if (isWork) "${GoalDetailLogic.motiveLabel(goal)} (선택)" else "이 목표를 시작한 이유") {
                        NTextField(
                            value = motive,
                            onValueChange = { motive = it },
                            singleLine = false,
                            minHeight = 64.dp,
                            maxLength = 500,
                        )
                    }
                }
                if (!isWork && !isHabit) {
                    // ── 수치 목표 — 2026-10 부터 "수치 수정" 시트에서 따로 고쳐요 (수치 마감일도 거기서) ──
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.subtle.copy(alpha = 0.6f), RoundedCornerShape(Radius.xl))
                            .clickable(enabled = !saving && !deleting, role = Role.Button) {
                                outcome = "metric_edit"
                                vm.sheet = GoalDetailSheet.editMetric("goal_edit")
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("수치 목표", style = pt(12, PTWeight.medium), color = NC.ink3)
                            Text(
                                if (hadMetric) GoalDetailLogic.metricSentence(
                                    name = goal.metricName ?: "",
                                    unit = goal.metricUnit ?: "",
                                    start = goal.startValue,
                                    target = goal.targetValue,
                                    startDate = goal.goalCategoryStartDate,
                                    endDate = goal.metricTargetDate ?: goal.goalCategoryEndDate,
                                ) ?: (goal.metricName ?: "수치") else "+ 수치 목표 추가 (예: 체중 80 → 72kg)",
                                style = pt(13, PTWeight.semibold),
                                color = if (hadMetric) NC.ink2 else NC.textBrand,
                            )
                        }
                        SfImage("chevron.right", size = 12.dp, tint = NC.ink3)
                    }
                }
            }
        }

        ConfirmDialog(
            isPresented = confirmDelete,
            onDismiss = { confirmDelete = false },
            title = "'${goal.goalCategoryName}' 목표를 삭제할까요?",
            message = "지금 이후로 예정된 Task와 루틴은 모두 지워지고, 지난 Task·완료한 기록은 남아요. 되돌릴 수 없어요.",
            confirmLabel = "삭제",
            tone = ConfirmTone.danger,
            onCancel = { vm.track("goal_detail.delete_cancel") },
        ) {
            vm.track("goal_detail.delete_confirm", mapOf(
                "goal_mode" to goal.analyticsMode, "goal_status" to goal.goalCategoryStatus.name,
                "routine_count" to goal.analyticsRoutineCount,
            ))
            scope.launch {
                deleting = true
                val goalId = goal.goalCategoryId
                val ok = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "목표를 삭제했어요.") {
                    GoalEditAPI.deleteGoal(goalId = goalId)
                }
                deleting = false
                vm.track("goal_detail.delete_result", GD.result())
                if (ok != null) {
                    outcome = "deleted"
                    vm.sheet = null
                    AppRouter.openGoalList()
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 700, name = "머리말")
@Composable
private fun GoalDetailHeaderPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Column(
                Modifier
                    .canvasBackground()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalDetailHeader(goal = GoalPreviewData.goal)
                GoalDetailHeader(goal = GoalPreviewData.recordGoal)
                GoalNotesCard(goal = GoalPreviewData.goal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900, name = "목표 수정")
@Composable
private fun GoalEditFormSheetPreview() {
    NaleulTheme(dark = false) {
        val vm = remember { GoalDetailViewModel.preview() }
        CompositionLocalProvider(LocalGoalDetailViewModel provides vm) {
            Box(Modifier.fillMaxSize().background(NC.canvas)) {
                GoalEditFormSheet(goal = GoalPreviewData.goal, colors = GoalPreviewData.colors, onDismiss = {})
            }
        }
    }
}
