// feature/goalcreation/DraftItemEditorSheet.kt
//
// iOS Features/GoalCreation/DraftItemEditorSheet.swift
// 초안 항목 고치기 (목표 · 세부 목표 · 마일스톤 · 루틴 / 할 일).
// 웹 원본: features/goal-creation/ui/draft/ItemEditModal.tsx (그 자리 편집기 → 휴대폰은 시트)
//
// 삭제는 같은 시트 아래에서 한 번 더 확인해요 (웹과 같아요).
// 시트로 띄워지는 화면이라 onDismiss 를 받아요 (iOS @Environment(\.dismiss)).

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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 지금 편집 중인 대상. index 가 null 이면 새로 추가 (iOS 연관값 enum → sealed class) */
sealed class DraftEditTarget {
    object goal : DraftEditTarget()
    data class subGoal(val i: Int?) : DraftEditTarget()
    data class milestone(val i: Int?) : DraftEditTarget()
    data class task(val i: Int?, val type: DraftTaskType) : DraftEditTarget()

    val id: String
        get() = when (this) {
            is goal -> "goal"
            is subGoal -> "subGoal-${i?.toString() ?: "new"}"
            is milestone -> "milestone-${i?.toString() ?: "new"}"
            is task -> "task-${type.rawValue}-${i?.toString() ?: "new"}"
        }

    val index: Int?
        get() = when (this) {
            is goal -> null
            is subGoal -> i
            is milestone -> i
            is task -> i
        }

    val isNew: Boolean
        get() = if (this is goal) false else index == null

    /** "루틴 추가", "목표 수정" … */
    val title: String
        get() {
            val name = when (this) {
                is goal -> "목표"
                is subGoal -> "세부 목표"
                is milestone -> "마일스톤"
                is task -> if (type == DraftTaskType.ROUTINE) "루틴" else "할 일"
            }
            return "$name ${if (isNew) "추가" else "수정"}"
        }

    companion object {}
}

sealed class DraftEditResult {
    /** 수치 목표일 때만 (시작값, 목표값) — iOS 튜플 (start:, target:) */
    data class MetricValues(val start: Double, val target: Double) { companion object }

    /** metricValues: 수치 목표일 때만 */
    data class goal(
        val title: String,
        val emoji: String?,
        val startDate: String,
        val endDate: String,
        val metricValues: MetricValues?,
    ) : DraftEditResult()

    data class subGoal(val index: Int?, val item: DraftPlanSubGoal) : DraftEditResult()
    data class milestone(val index: Int?, val item: DraftPlanMilestone) : DraftEditResult()
    data class task(val index: Int?, val item: DraftPlanTask) : DraftEditResult()

    companion object {}
}

/**
 * @param deleteDisabledReason 최소 개수 등으로 지울 수 없을 때 이유
 * @param onDismiss 시트 닫기 (부르는 쪽에서 editTarget = null)
 */
@Composable
fun DraftItemEditorSheet(
    target: DraftEditTarget,
    plan: DraftGoalPlan,
    onSave: (DraftEditResult) -> Unit,
    onDelete: () -> Unit,
    deleteDisabledReason: String?,
    onDismiss: () -> Unit,
) {
    key(target.id) {
        when (target) {
            is DraftEditTarget.goal -> DraftGoalForm(plan = plan, onSave = onSave, onDismiss = onDismiss)
            is DraftEditTarget.subGoal -> DraftSubGoalForm(target.i, plan, onSave, onDelete, deleteDisabledReason, onDismiss)
            is DraftEditTarget.milestone -> DraftMilestoneForm(target.i, plan, onSave, onDelete, deleteDisabledReason, onDismiss)
            is DraftEditTarget.task -> DraftTaskForm(target.i, target.type, plan, onSave, onDelete, deleteDisabledReason, onDismiss)
        }
    }
}

// MARK: - 공통 부품

/** 이모지 + 제목 한 줄 */
@Composable
private fun DraftEmojiTitleRow(
    emoji: String,
    onEmojiChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    placeholder: String = "",
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalCreationField("이모지", modifier = Modifier.width(64.dp)) {
            NTextField(
                emoji,
                { onEmojiChange(GoalCreationForm.prefix(it, 4)) },
                placeholder = "🙂",
                textStyle = pt(15).copy(textAlign = TextAlign.Center),
            )
        }
        GoalCreationField("제목", modifier = Modifier.weight(1f)) {
            NTextField(title, { onTitleChange(GoalCreationForm.prefix(it, DraftPlanUtils.Limits.title)) }, placeholder = placeholder)
        }
    }
}

/** 시트 아래 버튼: [삭제] … [취소] [저장] · 삭제 확인 줄 */
@Composable
private fun RowScope.DraftEditorFooter(
    /** 이벤트의 item_kind */
    itemKind: String,
    canDelete: Boolean,
    deleteBlock: String?,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onErrorChange: (String?) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    close: () -> Unit,
) {
    if (confirmDelete) {
        val shape = RoundedCornerShape(Radius.xl)
        Row(
            Modifier
                .weight(1f)
                .background(NC.dangerSoft.copy(alpha = 0.6f), shape)
                .border(1.dp, NC.borderSubtle, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("이 항목을 초안에서 뺄까요?", style = pt(14, PTWeight.semibold), color = NC.ink, modifier = Modifier.weight(1f))
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm) { onConfirmDeleteChange(false) }
            NButton("삭제", variant = NButtonVariant.danger, size = NButtonSize.sm) {
                GCTracker.track(GCEvent.itemDeleteConfirm, "item_kind" to itemKind)
                onDelete()
            }
        }
    } else {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDelete) {
                Row(
                    Modifier.nButton(NButtonVariant.ghost, NButtonSize.sm) {
                        GCTracker.track(GCEvent.itemDeleteClick, "item_kind" to itemKind)
                        if (deleteBlock != null) {
                            // [UX] 최소 개수라 지울 수 없어요
                            GCTracker.track(GCEvent.itemDeleteBlocked, "item_kind" to itemKind, "validation_error" to deleteBlock)
                            onErrorChange(deleteBlock)
                        } else {
                            onErrorChange(null)
                            onConfirmDeleteChange(true)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SfImage("trash", size = 13.dp, tint = NC.danger)
                    Text("삭제", style = pt(13, PTWeight.semibold), color = NC.danger)
                }
            }
            Spacer(Modifier.weight(1f))
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm) {
                GCTracker.sheetClose = "cancel_button"
                close()
            }
            NButton("저장", size = NButtonSize.sm) { onSave() }
        }
    }
}

/** 폼 공통 틀: 제목 · 내용 · 에러 · 아래 버튼 */
@Composable
private fun DraftEditorScaffold(
    target: DraftEditTarget,
    canDelete: Boolean,
    deleteBlock: String?,
    error: String?,
    onErrorChange: (String?) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    // [UX] 시트 안 검사 오류 (최소 개수 삭제 막힘은 푸터에서 따로 보내요)
    LaunchedEffect(error) {
        val e = error ?: return@LaunchedEffect
        if (e == deleteBlock) return@LaunchedEffect
        GCTracker.track(GCEvent.itemEditError, "item_kind" to target.analyticsKind, "validation_error" to e)
    }
    SheetScaffold(
        onDismiss = onDismiss,
        title = target.title,
        fullHeight = true,
        footer = { close ->
            DraftEditorFooter(
                itemKind = target.analyticsKind,
                canDelete = canDelete,
                deleteBlock = deleteBlock,
                confirmDelete = confirmDelete,
                onConfirmDeleteChange = { confirmDelete = it },
                onErrorChange = onErrorChange,
                onDelete = onDelete,
                onSave = onSave,
                close = close,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
            if (error != null) Text(error, style = pt(14, PTWeight.medium), color = NC.danger)
        }
    }
}

// MARK: - 목표
// 목표 이름 + "그래프를 정하는 값" (시작일 · 종료일 · 시작값 · 목표값).
// 마일스톤 날짜·수치는 이 값에서 자동으로 계산돼서, 마일스톤을 하나하나 맞출 필요가 없어요.

@Composable
private fun DraftGoalForm(plan: DraftGoalPlan, onSave: (DraftEditResult) -> Unit, onDismiss: () -> Unit) {
    val g = plan.goal
    var title by remember { mutableStateOf(g.title) }
    var emoji by remember { mutableStateOf(g.emoji ?: "") }
    var startDate by remember { mutableStateOf(g.startDate) }
    var endDate by remember { mutableStateOf(g.endDate) }
    var startValue by remember { mutableStateOf(g.metric?.let { DraftPlanUtils.num(it.startValue) } ?: "") }
    var targetValue by remember { mutableStateOf(g.metric?.let { DraftPlanUtils.num(it.targetValue) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val metric = g.metric
    val today = KST.today()

    fun submit() {
        val t = GoalCreationForm.trim(title)
        if (t.isEmpty()) { error = "목표 이름을 입력해 주세요."; return }
        if (startDate.isEmpty() || startDate < today) { error = "시작일은 오늘 이후여야 해요."; return }
        if (endDate.isEmpty() || endDate <= startDate) { error = "종료일은 시작일보다 뒤여야 해요."; return }
        var values: DraftEditResult.MetricValues? = null
        if (metric != null) {
            val sv = GoalCreationForm.toNumber(startValue)
            val tv = GoalCreationForm.toNumber(targetValue)
            if (sv == null || tv == null || sv.isNaN() || tv.isNaN()) {
                error = "시작값과 목표값을 숫자로 입력해 주세요."
                return
            }
            if (sv == tv) { error = "목표값이 시작값과 같아요."; return }
            values = DraftEditResult.MetricValues(start = sv, target = tv)
        }
        val e = GoalCreationForm.trim(emoji)
        error = null
        onSave(DraftEditResult.goal(title = t, emoji = e.ifEmpty { null }, startDate = startDate, endDate = endDate, metricValues = values))
    }

    DraftEditorScaffold(
        target = DraftEditTarget.goal, canDelete = false, deleteBlock = null,
        error = error, onErrorChange = { error = it },
        onDelete = {}, onSave = { submit() }, onDismiss = onDismiss,
    ) {
        DraftEmojiTitleRow(emoji = emoji, onEmojiChange = { emoji = it }, title = title, onTitleChange = { title = it })
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalCreationField("시작일", modifier = Modifier.weight(1f)) {
                GoalCreationDateField(ymd = startDate, onYmdChange = { startDate = it }, min = today)
            }
            GoalCreationField("종료일", modifier = Modifier.weight(1f)) {
                GoalCreationDateField(ymd = endDate, onYmdChange = { endDate = it }, min = if (startDate.isEmpty()) today else startDate)
            }
        }
        if (metric != null) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalCreationField("지금 ${metric.name} (${metric.unit ?: ""})", modifier = Modifier.weight(1f)) {
                    NTextField(startValue, { startValue = it }, keyboardType = KeyboardType.Decimal)
                }
                GoalCreationField("목표 ${metric.name} (${metric.unit ?: ""})", modifier = Modifier.weight(1f)) {
                    NTextField(targetValue, { targetValue = it }, keyboardType = KeyboardType.Decimal)
                }
            }
        }
        Text(
            "마일스톤 날짜${if (metric != null) "와 단계별 수치" else ""}는 이 값에 맞춰 자동으로 다시 계산돼요.",
            style = pt(12),
            color = NC.ink3,
        )
    }
}

// MARK: - 세부 목표

@Composable
private fun DraftSubGoalForm(
    index: Int?,
    plan: DraftGoalPlan,
    onSave: (DraftEditResult) -> Unit,
    onDelete: () -> Unit,
    deleteBlock: String?,
    onDismiss: () -> Unit,
) {
    val current = index?.let { plan.subGoals.getOrNull(it) }
    var title by remember { mutableStateOf(current?.title ?: "") }
    var emoji by remember { mutableStateOf(current?.emoji ?: "") }
    var description by remember { mutableStateOf(current?.description ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val t = GoalCreationForm.trim(title)
        if (t.isEmpty()) { error = "제목을 입력해 주세요."; return }
        val e = GoalCreationForm.trim(emoji)
        val d = GoalCreationForm.trim(description)
        error = null
        onSave(
            DraftEditResult.subGoal(
                index,
                DraftPlanSubGoal(
                    tempId = current?.tempId ?: DraftPlanUtils.newTempId("sg"),
                    title = t,
                    emoji = e.ifEmpty { null },
                    description = d.ifEmpty { null },
                    metric = current?.metric,
                ),
            )
        )
    }

    DraftEditorScaffold(
        target = DraftEditTarget.subGoal(index), canDelete = index != null, deleteBlock = deleteBlock,
        error = error, onErrorChange = { error = it },
        onDelete = onDelete, onSave = { submit() }, onDismiss = onDismiss,
    ) {
        DraftEmojiTitleRow(emoji = emoji, onEmojiChange = { emoji = it }, title = title, onTitleChange = { title = it }, placeholder = "예) 식단 관리")
        GoalCreationField("설명 (선택)") {
            NTextField(
                description, { description = GoalCreationForm.prefix(it, 200) },
                placeholder = "예) 하루 섭취량 1,800kcal 이하 유지",
                minHeight = 88.dp, singleLine = false,
            )
        }
    }
}

// MARK: - 마일스톤

@Composable
private fun DraftMilestoneForm(
    index: Int?,
    plan: DraftGoalPlan,
    onSave: (DraftEditResult) -> Unit,
    onDelete: () -> Unit,
    deleteBlock: String?,
    onDismiss: () -> Unit,
) {
    val current = index?.let { plan.milestones.getOrNull(it) }
    var title by remember { mutableStateOf(current?.title ?: "") }
    var description by remember { mutableStateOf(current?.description ?: "") }
    var dueDate by remember { mutableStateOf(current?.dueDate ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    /** 마지막 단계는 항상 목표 종료일 (자동) — 날짜를 바꾸려면 목표의 종료일을 고쳐요 */
    val isLast = index != null && index == plan.milestones.size - 1
    val metric = plan.goal.metric

    fun submit() {
        val t = GoalCreationForm.trim(title)
        if (t.isEmpty()) { error = "제목을 입력해 주세요."; return }
        if (dueDate.isEmpty()) { error = "날짜를 정해 주세요."; return }
        if (dueDate < plan.goal.startDate || dueDate > plan.goal.endDate) { error = "목표 기간 안의 날짜를 골라 주세요."; return }
        val d = GoalCreationForm.trim(description)
        error = null
        onSave(
            DraftEditResult.milestone(
                index,
                DraftPlanMilestone(
                    tempId = current?.tempId ?: DraftPlanUtils.newTempId("ms"),
                    title = t,
                    description = d.ifEmpty { null },
                    dueDate = dueDate,
                    // 수치는 저장할 때 시작값→목표값 직선에서 자동 계산돼요 (normalizePlan)
                    targetValue = current?.targetValue,
                ),
            )
        )
    }

    DraftEditorScaffold(
        target = DraftEditTarget.milestone(index), canDelete = index != null, deleteBlock = deleteBlock,
        error = error, onErrorChange = { error = it },
        onDelete = onDelete, onSave = { submit() }, onDismiss = onDismiss,
    ) {
        GoalCreationField("제목") {
            NTextField(title, { title = GoalCreationForm.prefix(it, DraftPlanUtils.Limits.title) }, placeholder = "예) 적응기: 습관 세팅")
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalCreationField(if (isLast) "날짜 (목표 종료일)" else "날짜", modifier = Modifier.weight(1f)) {
                GoalCreationDateField(
                    ymd = dueDate, onYmdChange = { dueDate = it },
                    min = plan.goal.startDate, max = plan.goal.endDate, disabled = isLast,
                )
            }
            if (metric != null) {
                Text(
                    "이 시점 ${metric.name}은(는) 날짜에 맞춰\n자동으로 계산돼요",
                    style = pt(12).copy(lineHeight = 17.sp),
                    color = NC.ink3,
                    modifier = Modifier.weight(1f).padding(top = 24.dp),
                )
            }
        }
        GoalCreationField("설명 (선택)") {
            NTextField(description, { description = GoalCreationForm.prefix(it, 200) }, minHeight = 72.dp, singleLine = false)
        }
    }
}

// MARK: - Task (루틴 / 일회성)

@Composable
private fun DraftTaskForm(
    index: Int?,
    type: DraftTaskType,
    plan: DraftGoalPlan,
    onSave: (DraftEditResult) -> Unit,
    onDelete: () -> Unit,
    deleteBlock: String?,
    onDismiss: () -> Unit,
) {
    val current = index?.let { plan.tasks.getOrNull(it) }
    val routine = type == DraftTaskType.ROUTINE
    var title by remember { mutableStateOf(current?.title ?: "") }
    var emoji by remember { mutableStateOf(current?.emoji ?: "") }
    var minutes by remember { mutableStateOf((current?.durationMinutes ?: (if (routine) 20 else 30)).toString()) }
    var subGoalId by remember { mutableStateOf(current?.subGoalTempId ?: plan.subGoals.firstOrNull()?.tempId ?: "") }
    // 루틴
    var days by remember { mutableStateOf(current?.routineDays ?: emptyList()) }
    var startTime by remember { mutableStateOf(current?.preferredStartTime ?: "") }
    var howTo by remember { mutableStateOf(current?.description ?: "") }
    // 일회성
    var milestoneId by remember { mutableStateOf(current?.milestoneTempId ?: "") }
    var scheduledDate by remember { mutableStateOf(current?.scheduledDate ?: "") }
    var dueDate by remember { mutableStateOf(current?.dueDate ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val limit = if (routine) DraftPlanUtils.Limits.routineMinutes else DraftPlanUtils.Limits.oneTimeMinutes
    val milestone = plan.milestones.firstOrNull { it.tempId == milestoneId }
    val maxDue = milestone?.dueDate ?: plan.goal.endDate

    fun submit() {
        val t = GoalCreationForm.trim(title)
        if (t.isEmpty()) { error = "제목을 입력해 주세요."; return }
        val m = GoalCreationForm.trim(minutes).toIntOrNull()
        if (m == null || m < limit.min || m > limit.max) {
            error = "${if (routine) "루틴" else "할 일"}은 ${limit.min}~${limit.max}분으로 설정해 주세요."
            return
        }

        // 제목에 쓴 시간("유산소 25분")은 걸리는 시간을 바꾸면 같이 바꿔요 (제목과 시간이 어긋나지 않게)
        var synced = title
        val old = current?.durationMinutes
        if (old != null && old != m && title.contains("${old}분")) {
            synced = title.replaceFirst("${old}분", "${m}분")
        }
        val e = GoalCreationForm.trim(emoji)
        val base = DraftPlanTask(
            tempId = current?.tempId ?: DraftPlanUtils.newTempId("t"),
            type = type,
            subGoalTempId = subGoalId.ifEmpty { null },
            milestoneTempId = null,
            title = GoalCreationForm.trim(synced),
            emoji = e.ifEmpty { null },
            durationMinutes = m,
        )

        if (routine) {
            if (days.isEmpty()) { error = "요일을 하나 이상 골라 주세요."; return }
            val how = GoalCreationForm.trim(howTo)
            val item = base.copy(
                milestoneTempId = current?.milestoneTempId,
                routineDays = GoalCreationWeekday.entries.filter { days.contains(it) },
                preferredStartTime = startTime.ifEmpty { null },
                startDate = current?.startDate ?: plan.goal.startDate,
                endDate = current?.endDate,
                description = how.ifEmpty { null },
                reason = current?.reason,
            )
            error = null
            onSave(DraftEditResult.task(index, item))
            return
        }

        if (dueDate.isEmpty()) { error = "마감일을 정해 주세요."; return }
        if (scheduledDate.isNotEmpty() && scheduledDate > dueDate) { error = "실행일은 마감일보다 늦을 수 없어요."; return }
        if (dueDate > maxDue) {
            error = if (milestone != null) {
                "마감일은 연결된 마일스톤(${DraftPlanUtils.formatMd(milestone.dueDate, withWeekday = false)})보다 늦을 수 없어요."
            } else {
                "마감일은 목표 종료일보다 늦을 수 없어요."
            }
            return
        }
        val item = base.copy(
            milestoneTempId = milestoneId.ifEmpty { null },
            scheduledDate = scheduledDate.ifEmpty { null },
            dueDate = dueDate,
        )
        error = null
        onSave(DraftEditResult.task(index, item))
    }

    DraftEditorScaffold(
        target = DraftEditTarget.task(index, type), canDelete = index != null, deleteBlock = deleteBlock,
        error = error, onErrorChange = { error = it },
        onDelete = onDelete, onSave = { submit() }, onDismiss = onDismiss,
    ) {
        DraftEmojiTitleRow(
            emoji = emoji, onEmojiChange = { emoji = it },
            title = title, onTitleChange = { title = it },
            placeholder = if (routine) "예) 하체 근력 50분" else "예) 5km 기록 측정",
        )
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalCreationField("걸리는 시간 (분)", hint = "${limit.min}~${limit.max}분", modifier = Modifier.weight(1f)) {
                NTextField(minutes, { minutes = it }, keyboardType = KeyboardType.Number)
            }
            GoalCreationField("세부 목표", modifier = Modifier.weight(1f)) {
                NMenuPicker(
                    selection = subGoalId,
                    options = plan.subGoals.map { it.tempId to "${it.emoji ?: ""} ${it.title}" },
                    onSelect = { subGoalId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (routine) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("반복 요일", style = pt(13, PTWeight.semibold), color = NC.ink2)
                GoalCreationDayChips(days = days, onDaysChange = { days = it })
            }
            GoalCreationField("시작 시각 (선택)") {
                GoalCreationTimeField(hm = startTime, onHmChange = { startTime = it }, placeholder = "시간 자동")
            }
            GoalCreationField(
                "하는 방법 (선택)",
                hint = "${GoalCreationForm.count(howTo)}/${DraftPlanUtils.Limits.routineDescription}",
            ) {
                NTextField(
                    howTo, { howTo = GoalCreationForm.prefix(it, DraftPlanUtils.Limits.routineDescription) },
                    placeholder = "예) 스쿼트 4x10 → 런지 3x12(각) → 레그프레스 3x12, 세트 사이 60~90초 휴식",
                    minHeight = 88.dp, singleLine = false,
                )
            }
        } else {
            GoalCreationField("마일스톤 (선택)") {
                NMenuPicker(
                    selection = milestoneId,
                    options = listOf("" to "연결 안 함") + plan.milestones.map {
                        it.tempId to "${it.title} (~${DraftPlanUtils.formatMd(it.dueDate, withWeekday = false)})"
                    },
                    onSelect = { milestoneId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalCreationField("실행일 (선택)", modifier = Modifier.weight(1f)) {
                    GoalCreationDateField(
                        ymd = scheduledDate, onYmdChange = { scheduledDate = it },
                        min = plan.goal.startDate, max = if (dueDate.isEmpty()) maxDue else dueDate, clearable = true,
                    )
                }
                GoalCreationField("마감일", modifier = Modifier.weight(1f)) {
                    GoalCreationDateField(
                        ymd = dueDate, onYmdChange = { dueDate = it },
                        min = if (scheduledDate.isEmpty()) plan.goal.startDate else scheduledDate, max = maxDue,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DraftItemEditorSheetRoutinePreview() {
    NaleulTheme(dark = false) {
        Box(Modifier.fillMaxSize().background(NC.canvas)) {
            DraftItemEditorSheet(
                target = DraftEditTarget.task(0, DraftTaskType.ROUTINE), plan = GoalCreationSamples.plan,
                onSave = {}, onDelete = {}, deleteDisabledReason = null, onDismiss = {},
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DraftItemEditorSheetGoalPreview() {
    NaleulTheme(dark = false) {
        Box(Modifier.fillMaxSize().background(NC.canvas)) {
            DraftItemEditorSheet(
                target = DraftEditTarget.goal, plan = GoalCreationSamples.plan,
                onSave = {}, onDelete = {}, deleteDisabledReason = null, onDismiss = {},
            )
        }
    }
}
