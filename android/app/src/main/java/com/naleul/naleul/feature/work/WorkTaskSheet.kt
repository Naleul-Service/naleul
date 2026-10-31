// feature/work/WorkTaskSheet.kt
//
// iOS Features/Work/WorkTaskSheet.swift
// 웹 features/work/ui/WorkTaskModal.tsx
// Task 자세히 보기 · 고치기 — 이름 · 칸 · 주제 · 하는 날 · 마감 · 메모.
// 바뀐 값만 보내요 (지운 값은 clear 플래그로). 반복 루틴 Task 는 완료 · 완료 취소만.
//
// 시트로 띄우는 화면이라 onDismiss 를 받아요: WorkTaskSheet(goalId = id, task = t, topics = …, onDismiss = { … })

package com.naleul.naleul.feature.work

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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WorkTaskSheet(
    goalId: Int,
    task: WorkTask,
    topics: List<WorkTopic>,
    /** 어디서 열었는지 (roadmap · board) — 앰플리튜드 surface */
    surface: String = "board",
    isPreview: Boolean = false,
    onDismiss: () -> Unit,
) {
    val initToday = remember { KST.today() }
    var name by remember { mutableStateOf(task.taskName) }
    var topicId by remember { mutableStateOf(task.topicId) }
    var status by remember { mutableStateOf(task.status) }
    var hasDate by remember { mutableStateOf(task.date != null) }
    var date by remember { mutableStateOf(task.date ?: initToday) }
    var hasDue by remember { mutableStateOf(task.dueDate != null) }
    var dueDate by remember { mutableStateOf(task.dueDate ?: (task.date ?: initToday)) }
    var memo by remember { mutableStateOf(task.memo ?: "") }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val trimmedName = name.trim()
    val realTopics = topics.filter { it.topicId != null }
    val statusOptions: List<Pair<WorkColumn, String>> = WorkColumn.entries.map { it to it.label }
    val newDate: String? = if (hasDate) date else null
    /** 어떻게 닫혔는지 (저장 · 지우기 · 완료면 닫기 이벤트를 보내지 않아요) */
    var outcome by remember { mutableStateOf<String?>(null) }

    fun track(event: String, props: EventProps = emptyMap()) {
        if (isPreview) return
        WK.track(event, mapOf("task_id" to task.taskId) + props)
    }

    /** 바뀐 칸 이름들 (changed_fields · is_dirty) */
    fun changedFields(): List<String> = buildList {
        val n = name.trim()
        if (n.isNotEmpty() && n != task.taskName) add("name")
        if (topicId != task.topicId) add("topic")
        if ((if (hasDate) date else null) != task.date) add("date")
        if ((if (hasDue) dueDate else null) != task.dueDate) add("due_date")
        if (memo.trim() != (task.memo ?: "")) add("memo")
        if (status != task.status) add("status")
    }
    val dirty = !task.routine && changedFields().isNotEmpty()
    val latestDirty by rememberUpdatedState(dirty)

    DisposableEffect(Unit) {
        onDispose {
            val m = GD.closeMethod(outcome) ?: return@onDispose
            track("work_task_modal.close", mapOf("is_routine" to task.routine, "close_method" to m, "is_dirty" to latestDirty))
        }
    }

    // MARK: 저장

    suspend fun save(close: () -> Unit) {
        if (saving) return
        val patch = WorkTaskPatch()
        val n = name.trim()
        if (n.isNotEmpty() && n != task.taskName) patch.taskName = n
        if (topicId != task.topicId) {
            val t = topicId
            if (t != null) patch.topicId = t else patch.clearTopic = true
        }
        val d: String? = if (hasDate) date else null
        if (d != task.date) {
            if (d != null) patch.date = d else patch.clearDate = true
        }
        val due: String? = if (hasDue) dueDate else null
        if (due != task.dueDate) {
            if (due != null) patch.dueDate = due else patch.clearDueDate = true
        }
        val m = memo.trim()
        if (m != (task.memo ?: "")) patch.memo = m
        if (status != task.status) patch.status = status

        track("work_task_modal.save_click", mapOf(
            "changed_fields" to changedFields().joinToString(","),
            "time_released" to (task.startTime != null && d != task.date), "submit_method" to "button",
        ))
        patch.status?.let { to ->
            track("work_task.status_change", WK.taskProps(task, surface) + mapOf(
                "goal_category_id" to goalId, "from_status" to task.status.name, "to_status" to to.name, "move_method" to "modal",
            ))
        }
        outcome = "saved"
        if (patch == WorkTaskPatch() || isPreview) {
            close()
            return
        }
        saving = true
        try {
            val p = patch.copy()
            val gid = goalId
            val tid = task.taskId
            val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "Task를 수정했어요.") {
                WorkAPI.updateTask(goalId = gid, taskId = tid, patch = p)
                Unit // 응답 본문이 비어도 성공은 성공 (iOS 의 이중 옵셔널과 같게)
            }
            track("work_task_modal.result", GD.result() + mapOf("task_action" to "update"))
            if (r != null) close() else outcome = null
        } finally {
            saving = false
        }
    }

    suspend fun remove(close: () -> Unit) {
        if (isPreview) {
            close()
            return
        }
        saving = true
        try {
            val tid = task.taskId
            val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "Task를 지웠어요.") {
                WorkAPI.deleteTask(taskId = tid)
            }
            track("work_task_modal.result", GD.result() + mapOf("task_action" to "delete"))
            if (Mutation.lastErrorStatus == null) {
                outcome = "deleted"
                close()
            }
        } finally {
            saving = false
        }
    }

    suspend fun toggleRoutine(close: () -> Unit) {
        val to = if (task.isDone) WorkColumn.NOT_STARTED else WorkColumn.DONE
        track("work_task.status_change", WK.taskProps(task, surface) + mapOf(
            "goal_category_id" to goalId, "from_status" to task.status.name, "to_status" to to.name, "move_method" to "modal",
        ))
        outcome = "saved"
        if (isPreview) {
            close()
            return
        }
        saving = true
        try {
            val tid = task.taskId
            val current = if (task.isDone) TaskStatus.COMPLETED else TaskStatus.TODO
            val msg = if (task.isDone) "완료를 취소했어요." else "완료했어요."
            val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = msg) {
                GoalAPI.toggleTask(taskId = tid, currentStatus = current)
            }
            track("work_task.move_result", GD.result() + mapOf("surface" to surface, "to_status" to to.name, "move_method" to "modal"))
            if (Mutation.lastErrorStatus != null) outcome = null
            if (r != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                close()
            }
        } finally {
            saving = false
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (task.routine) "반복 루틴 Task" else "Task",
        description = if (task.routine) "루틴 Task는 목표의 반복 루틴에서 바꿔 주세요. 여기서는 완료만 할 수 있어요." else null,
        dismissible = !saving,
        // iOS detents: 루틴은 .medium, 아니면 .large
        fullHeight = !task.routine,
        footer = { close ->
            if (task.routine) {
                NButton("닫기", variant = NButtonVariant.secondary) {
                    outcome = "cancel_button"
                    close()
                }
                NButton(
                    if (task.isDone) "완료 취소" else "완료",
                    variant = NButtonVariant.brand,
                    icon = if (task.isDone) null else "checkmark",
                    loading = saving,
                ) {
                    scope.launch { toggleRoutine(close) }
                }
            } else {
                Row(
                    Modifier
                        .height(40.dp)
                        .alpha(if (saving) 0.4f else 1f)
                        .clickable(enabled = !saving, role = Role.Button) {
                            track("work_task_modal.delete_click", mapOf("task_status" to task.status.name))
                            confirmDelete = true
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("trash", size = 12.dp, tint = NC.danger)
                    Text("지우기", style = pt(13, PTWeight.semibold), color = NC.danger)
                }
                // 스펙 13.2: 시트 아래 버튼은 남은 폭을 나눠 가져요 (빈칸 Spacer 없이)
                NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                    outcome = "cancel_button"
                    close()
                }
                NButton("저장", loading = saving, enabled = trimmedName.isNotEmpty()) {
                    scope.launch { save(close) }
                }
            }
        },
    ) { close ->
        if (task.routine) {
            // MARK: 루틴 (읽기 전용)
            val today = KST.today()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    task.taskName,
                    style = NType.h4.copy(textDecoration = if (task.isDone) TextDecoration.LineThrough else null),
                    color = if (task.isDone) NC.ink3 else NC.ink,
                )
                WorkTaskMetaRow(task = task, today = today, showTopic = true)
                val d = task.date
                if (d != null) {
                    val time: String = task.startTime?.let { " · $it" } ?: ""
                    Text(WorkFormat.dayLabel(d, today = today) + time, style = pt(14), color = NC.ink2)
                }
            }
        } else {
            // MARK: 고치기
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("이름", required = true)
                    NTextField(value = name, onValueChange = { name = it }, placeholder = "Task 이름", maxLength = 100)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("칸")
                    SegmentPicker(
                        selection = status,
                        onSelectionChange = { s ->
                            if (s != status) {
                                track("work_task_modal.column_select", mapOf("from_status" to status.name, "to_status" to s.name))
                                status = s
                            }
                        },
                        options = statusOptions,
                        fullWidth = true,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("세부 목표")
                    WorkTopicMenu(selection = topicId, onSelectionChange = { topicId = it }, topics = realTopics, showDue = true)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel("하는 날", hint = if (hasDate) null else "할 일 목록", modifier = Modifier.weight(1f))
                        NSwitch(isOn = hasDate, onIsOnChange = { hasDate = it }, label = "하는 날")
                    }
                    if (hasDate) {
                        TimetableDateField(ymd = date, onYmdChange = { date = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkQuickDateChip(title = "오늘 하기") {
                            track("work_task_modal.quick_date_click", mapOf("quick_date" to "today"))
                            hasDate = true
                            date = KST.today()
                        }
                        if (hasDate) {
                            WorkQuickDateChip(title = "할 일 목록으로") {
                                track("work_task_modal.quick_date_click", mapOf("quick_date" to "backlog"))
                                hasDate = false
                            }
                        }
                    }
                    if (task.startTime != null && newDate != task.date) {
                        FieldMessage(text = "날짜를 바꾸면 정해 둔 시간(${task.startTime ?: ""})은 풀리고 그날 자동으로 다시 배치돼요.")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel("마감", hint = if (hasDue) null else "선택", modifier = Modifier.weight(1f))
                        NSwitch(isOn = hasDue, onIsOnChange = { hasDue = it }, label = "마감")
                    }
                    if (hasDue) {
                        TimetableDateField(ymd = dueDate, onYmdChange = { dueDate = it })
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("메모", hint = "공유할 때 같이 보여요")
                    WorkTextEditor(
                        text = memo,
                        onTextChange = { memo = it },
                        placeholder = "예: 디자인 시안 v2 기준으로",
                        minHeight = 88.dp,
                        maxLength = 500,
                    )
                }
            }
        }
        ConfirmDialog(
            isPresented = confirmDelete,
            onDismiss = { confirmDelete = false },
            title = "이 Task를 지울까요?",
            message = "되돌릴 수 없어요.",
            confirmLabel = "지우기",
            tone = ConfirmTone.danger,
            onCancel = { track("work_task_modal.delete_cancel") },
        ) {
            track("work_task_modal.delete_confirm", mapOf("task_status" to task.status.name))
            scope.launch { remove(close) }
        }
    }
}

/** "오늘 하기" · "할 일 목록으로" */
@Composable
private fun WorkQuickDateChip(title: String, action: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .clip(CircleShape)
            .border(1.dp, NC.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClick = action)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = pt(12), color = NC.ink3)
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkTaskSheetPreview() {
    NaleulTheme(dark = false) {
        val b = WorkSampleData.board
        WorkTaskSheet(goalId = 2, task = b.board.inProgress[0], topics = b.topics, isPreview = true, onDismiss = {})
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkTaskSheetBacklogPreview() {
    NaleulTheme(dark = false) {
        val b = WorkSampleData.board
        WorkTaskSheet(goalId = 2, task = b.board.notStarted[1], topics = b.topics, isPreview = true, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkTaskSheetRoutinePreview() {
    NaleulTheme(dark = false) {
        val t = WorkSampleData.task(
            77, "주간 회의", topic = null, null, WorkColumn.NOT_STARTED,
            date = KST.today(), startTime = "10:00", routine = true,
        )
        WorkTaskSheet(goalId = 2, task = t, topics = emptyList(), isPreview = true, onDismiss = {})
    }
}
