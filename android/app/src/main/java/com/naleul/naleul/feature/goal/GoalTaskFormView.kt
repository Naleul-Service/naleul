// Features/Goal/GoalTaskFormView.kt
//
// iOS GoalTaskFormView.swift — 웹 features/goal/ui/GoalTaskForm.tsx — 목표의 Task 추가 · 수정 폼.
// 이름과 날짜만 넣고 저장하면 그날 "시간 미정"으로 들어가고, 자동 배치가 빈 시간에 넣어 줘요.
// 시간을 넣으면 그 시간에 바로 고정(📌)돼요 — 다른 고정 일정과 겹치면 저장되지 않아요.
//
// 목표 상세(시트 안)와 Task 추가 화면(직접 추가)에서 같이 써요.
// 시트 안에서는 GoalSheetForm { } 으로 감싸면 테두리 없이 그려져요.

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * @param task null 이면 새로 추가
 * @param onCreated 새로 만든 Task (추가일 때만) — 예: "이날 빈 시간에 배치하기" 안내
 */
@Composable
fun GoalTaskFormView(
    goal: GoalCategory,
    task: TimeBlockTask?,
    onDone: () -> Unit,
    onCreated: ((TimeBlockTask) -> Unit)? = null,
    /** 앰플리튜드 surface (goal_detail / habit) — null 이면 이벤트를 보내지 않아요 (Task 추가 화면은 자기 이벤트를 써요) */
    analyticsSurface: String? = null,
    modifier: Modifier = Modifier,
    /** false 면 goal_task_list.edit_cancel 을 안 보내요 (Task 추가 › 직접 추가는 폼을 다시 만들며 비워서) */
    tracksCancel: Boolean = true,
    /** 처음 값과 달라졌는지 바뀔 때마다 (Task 추가 시트의 has_input) */
    onDirtyChange: ((Boolean) -> Unit)? = null,
    /** 새로 추가할 때 미리 채울 날짜 · 시작 시각(분) — 캘린더 빈 칸을 눌러 열었을 때 (1시간) */
    prefillDate: String? = null,
    prefillStartMinutes: Int? = null,
) {
    // iOS init 에서 State(initialValue:) 로 채우던 값들 — task 가 바뀌면 새로 채워요
    val areas = GoalDetailLogic.areas(goal)
    val milestones = GoalDetailLogic.sortedMilestones(goal)
    var emoji by remember(task?.taskId) { mutableStateOf(task?.emoji ?: "") }
    var name by remember(task?.taskId) { mutableStateOf(task?.taskName ?: "") }
    var date by remember(task?.taskId) {
        val planned = task?.plannedStartAt?.take(10)
        mutableStateOf(planned ?: task?.date ?: task?.scheduledDate ?: (if (task == null) prefillDate else null) ?: KST.today())
    }
    val pre = if (task == null) prefillStartMinutes else null
    var startTime by remember(task?.taskId) { mutableStateOf(if (pre != null) KST.formatMinutes(pre) else KST.hm(task?.plannedStartAt)) }
    var endTime by remember(task?.taskId) { mutableStateOf(if (pre != null) KST.formatMinutes(minOf(pre + 60, 24 * 60)) else KST.hm(task?.plannedEndAt)) }
    var sub by remember(task?.taskId) {
        val gid = task?.generalCategoryId
        mutableStateOf(if (gid != null && areas.any { it.generalCategoryId == gid }) gid else null)
    }
    var milestone by remember(task?.taskId) { mutableStateOf(task?.milestoneId) }
    var duration by remember(task?.taskId) {
        val d = task?.plannedDurationMinutes
        mutableStateOf(if (d != null && d > 0 && task?.plannedStartAt == null) d.toString() else "")
    }
    var dueDate by remember(task?.taskId) { mutableStateOf(task?.dueDate ?: "") }
    var more by remember(task?.taskId) {
        mutableStateOf(task?.milestoneId != null || task?.dueDate != null || (task != null && task.plannedStartAt == null))
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 앰플리튜드 — 시트가 어떻게 닫혔는지 · 입력이 바뀌었는지
    var outcome by remember { mutableStateOf<String?>(null) }
    val formKey = listOf(emoji, name, date, startTime, endTime, (sub ?: 0).toString(), (milestone ?: 0).toString(), duration, dueDate)
        .joinToString("|")
    val initialKey = remember(task?.taskId) { formKey }
    val latestKey by rememberUpdatedState(formKey)
    fun track(event: String, props: EventProps) {
        val s = analyticsSurface ?: return
        GD.track(event, mapOf("surface" to s, "goal_category_id" to goal.goalCategoryId), props)
    }
    LaunchedEffect(formKey) { onDirtyChange?.invoke(formKey != initialKey) }
    DisposableEffect(Unit) {
        onDispose {
            if (analyticsSurface != null && tracksCancel) {
                GD.closeMethod(outcome)?.let { m ->
                    track("goal_task_list.edit_cancel", mapOf("is_new" to (task == null), "close_method" to m, "is_dirty" to (latestKey != initialKey)))
                }
            }
        }
    }

    val trimmedName = name.trim()
    val hasTime = startTime.isNotEmpty() && endTime.isNotEmpty()
    val durationNum = GoalDetailLogic.toNum(duration)
    /** 시작~종료 분 (자정을 넘기면 다음 날까지) */
    val span: Int? = if (hasTime) ((KST.timeToMinutes(endTime) - KST.timeToMinutes(startTime)) % 1440 + 1440) % 1440 else null

    val error: String? = when {
        trimmedName.isEmpty() -> null
        date.isEmpty() -> "날짜를 입력해 주세요."
        startTime.isEmpty() != endTime.isEmpty() -> "시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요."
        hasTime && span == 0 -> "시작과 종료 시간이 같아요."
        !hasTime && durationNum != null && (durationNum.isNaN() || durationNum < 5 || durationNum > 1440) ->
            "소요 시간은 5~1440분(24시간)으로 입력해 주세요."
        else -> null
    }
    LaunchedEffect(error) {
        val e = error ?: return@LaunchedEffect
        track("goal_task_list.edit_error", mapOf("is_new" to (task == null), "validation_error" to e))
    }

    val subOptions: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "세부 목표 없음") +
        areas.map { it.generalCategoryId to it.generalCategoryName }
    val milestoneOptions: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "없음") +
        milestones.map { it.milestoneId to it.title }

    val hint: String = if (!hasTime || span == null) {
        "시간을 비워 두면 그날 빈 시간에 자동으로 배치돼요."
    } else {
        val overnight = if (KST.timeToMinutes(endTime) < KST.timeToMinutes(startTime)) " (다음 날까지)" else ""
        "${KST.formatDuration(span)} · 이 시간에 고정돼요$overnight."
    }

    fun submit(method: String) {
        if (trimmedName.isEmpty() || error != null || saving) return
        track("goal_task_list.save_click", mapOf(
            "is_new" to (task == null), "days_from_today" to GD.daysFromToday(date), "has_time" to hasTime,
            "has_duration" to (!hasTime && durationNum != null), "has_due_date" to dueDate.isNotEmpty(),
            "has_milestone" to (milestone != null), "has_sub_goal" to (sub != null), "submit_method" to method,
        ))
        val e = emoji.trim()
        val input = GoalTaskInput(
            taskName = trimmedName,
            emoji = if (e.isEmpty()) null else e,
            generalCategoryId = sub,
            milestoneId = milestone,
            date = date,
            startTime = if (hasTime) startTime else null,
            endTime = if (hasTime) endTime else null,
            durationMinutes = if (hasTime) null else durationNum?.toInt(),
            dueDate = if (dueDate.isEmpty()) null else dueDate,
        )
        val goalId = goal.goalCategoryId
        val taskId = task?.taskId
        saving = true
        scope.launch {
            if (taskId != null) {
                val r = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "Task를 수정했어요.") {
                    GoalAPI.updateGoalTask(goalId = goalId, taskId = taskId, input = input)
                    Unit
                }
                saving = false
                track("goal_task_list.result", GD.result() + mapOf("task_action" to "update"))
                if (r != null) {
                    outcome = "saved"
                    onDone()
                }
            } else {
                // 응답(TimeBlockTask?)이 null 이어도 "성공"과 "실패"를 구분하려고 listOf 로 한 번 감싸요
                // (Kotlin 은 R? 의 R 이 이미 nullable 이면 두 null 을 구분하지 못해요)
                val r = Mutation.run(invalidate = listOf(DataKey.goals, DataKey.timetable), successMessage = "Task를 추가했어요.") {
                    listOf(GoalAPI.createGoalTask(goalId = goalId, input = input))
                }
                saving = false
                track("goal_task_list.result", GD.result() + mapOf("task_action" to "create"))
                if (r != null) {
                    outcome = "saved"
                    r.firstOrNull()?.let { created -> onCreated?.invoke(created) }
                    onDone()
                }
            }
        }
    }

    GoalInlineForm(
        submitLabel = if (task == null) "추가" else "저장",
        saving = saving,
        valid = trimmedName.isNotEmpty() && error == null,
        error = error,
        onCancel = {
            outcome = "cancel_button"
            onDone()
        },
        onSubmit = { submit("button") },
        modifier = modifier,
        extra = {
            Row(
                Modifier.clickable(role = Role.Button) {
                    more = !more
                    track("goal_task_list.more_toggle", mapOf("is_new" to (task == null), "expanded" to more))
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SfImage(if (more) "chevron.up" else "chevron.down", size = 11.dp, tint = NC.ink3)
                Text(if (more) "간단히" else "더 설정하기", style = pt(13, PTWeight.semibold), color = NC.ink3)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFormField("이모지", modifier = Modifier.width(64.dp)) {
                NTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    placeholder = "📝",
                    maxLength = 16,
                    textStyle = pt(15).copy(textAlign = TextAlign.Center),
                )
            }
            GoalFormField("Task 이름", modifier = Modifier.weight(1f)) {
                NTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "예: 영단어 50개 외우기",
                    maxLength = 100,
                    imeAction = ImeAction.Done,
                    onSubmit = { submit("keyboard_done") },
                )
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFormField("날짜", modifier = Modifier.weight(1f)) { GoalDateInput(value = date, onValueChange = { date = it }) }
            if (areas.isNotEmpty()) {
                GoalFormField("세부 목표 (선택)", modifier = Modifier.weight(1f)) {
                    GoalSelectMenu(selection = sub, onSelectionChange = { sub = it }, options = subOptions)
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalFormField("시작 (선택)", modifier = Modifier.weight(1f)) {
                GoalTimeInput(value = startTime, onValueChange = { startTime = it })
            }
            GoalFormField("종료 (선택)", modifier = Modifier.weight(1f)) {
                GoalTimeInput(
                    value = endTime,
                    onValueChange = { endTime = it },
                    suggested = if (startTime.isEmpty()) "09:30" else KST.minutesToTime(KST.timeToMinutes(startTime) + 30),
                )
            }
        }
        if (more) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalFormField("소요 시간 (분)", modifier = Modifier.weight(1f)) {
                    if (hasTime) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .background(NC.subtle, RoundedCornerShape(Radius.xl))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(span?.toString() ?: "", style = pt(15), color = NC.ink3)
                        }
                    } else {
                        NTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            placeholder = "30",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                GoalFormField("마감일", modifier = Modifier.weight(1f)) {
                    GoalDateInput(value = dueDate, onValueChange = { dueDate = it }, optional = true)
                }
            }
            // 마일스톤이 없는 목표(예: 기본 생활 습관)에선 고를 게 없어서 숨겨요
            if (milestones.isNotEmpty()) {
                GoalFormField("마일스톤") {
                    GoalSelectMenu(selection = milestone, onSelectionChange = { milestone = it }, options = milestoneOptions)
                }
            }
        }
        Text(hint, style = pt(12), color = NC.ink3)
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun GoalTaskFormViewPreview() {
    NaleulTheme(dark = false) {
        Column(
            Modifier.fillMaxSize().canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalTaskFormView(goal = GoalPreviewData.goal, task = null, onDone = {})
            GoalTaskFormView(goal = GoalPreviewData.goal, task = GoalPreviewData.tasks.tasks[0], onDone = {})
        }
    }
}
