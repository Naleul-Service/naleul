// feature/work/WorkTopicSheet.kt
//
// iOS Features/Work/WorkTopicSheet.swift
// 웹 WorkBoardView.tsx AddTopic · TopicEditor
// 주제 추가 · 고치기 — 이름(30자) · 마감. 고칠 때는 "끝냈어요 / 다시 열기"와 "주제 지우기"도.
//
// 시트로 띄우는 화면이라 onDismiss 를 받아요: WorkTopicSheet(goalId = id, topic = null, onDismiss = { … })

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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WorkTopicSheet(
    goalId: Int,
    /** null 이면 새 주제 */
    topic: WorkTopic?,
    /** 지금 주제 수 (앰플리튜드 add_topic_submit 의 topic_count) */
    topicCount: Int = 0,
    isPreview: Boolean = false,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(topic?.name ?: "") }
    var noDue by remember { mutableStateOf(if (topic != null) topic.dueDate == null else false) }
    var dueDate by remember { mutableStateOf(topic?.dueDate ?: KST.addDays(KST.today(), 7)) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** 어떻게 닫혔는지 (저장 · 끝냈어요 · 지우기면 취소 이벤트를 보내지 않아요) */
    var outcome by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val trimmed = name.trim()
    val isEdit = topic != null
    val newDue: String? = if (noDue) null else dueDate
    /** 처음 연 상태에서 바뀌었는지 */
    val isDirty = if (topic != null) trimmed != topic.name || newDue != topic.dueDate else trimmed.isNotEmpty()
    val latestDirty by rememberUpdatedState(isDirty)

    fun track(event: String, props: EventProps = emptyMap()) {
        if (isPreview) return
        WK.track(event, goalId, props)
    }
    fun trackResult(action: String) {
        track("work_roadmap.topic_result", GD.result() + mapOf("topic_id" to topic?.topicId, "topic_action" to action))
    }

    DisposableEffect(Unit) {
        onDispose {
            val m = GD.closeMethod(outcome) ?: return@onDispose
            if (topic != null) {
                track("work_roadmap.topic_edit_cancel", mapOf("topic_id" to topic.topicId, "close_method" to m, "is_dirty" to latestDirty))
            } else {
                track("work_roadmap.add_topic_cancel", mapOf("close_method" to m, "is_dirty" to latestDirty))
            }
        }
    }

    // MARK: 저장

    suspend fun save(close: () -> Unit, method: String) {
        val n = name.trim()
        if (n.isEmpty() || saving) return
        val due: String? = if (noDue) null else dueDate
        if (topic != null) {
            val changed = buildList {
                if (n != topic.name) add("name")
                if (due != topic.dueDate) add("due_date")
            }
            track("work_roadmap.topic_edit_save", mapOf(
                "topic_id" to topic.topicId, "has_due_date" to (due != null), "changed_fields" to changed.joinToString(","),
                "submit_method" to method,
            ))
        } else {
            track("work_roadmap.add_topic_submit", mapOf("has_due_date" to (due != null), "topic_count" to topicCount, "submit_method" to method))
        }
        outcome = "saved"
        if (isPreview) {
            close()
            return
        }
        saving = true
        try {
            val gid = goalId
            val id = topic?.topicId
            if (topic != null && id != null) {
                val patch = WorkTopicPatch()
                if (n != topic.name) patch.name = n
                if (due != topic.dueDate) {
                    if (due != null) patch.dueDate = due else patch.clearDueDate = true
                }
                if (patch == WorkTopicPatch()) {
                    close()
                    return
                }
                val p = patch.copy()
                val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "세부 목표를 고쳤어요.") {
                    WorkAPI.updateTopic(goalId = gid, topicId = id, patch = p)
                }
                trackResult("update")
                if (Mutation.lastErrorStatus == null) close() else outcome = null
            } else {
                val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "'$n' 세부 목표를 만들었어요.") {
                    WorkAPI.createTopic(goalId = gid, name = n, dueDate = due)
                }
                trackResult("create")
                if (Mutation.lastErrorStatus == null) close() else outcome = null
            }
        } finally {
            saving = false
        }
    }

    suspend fun toggleCompleted(t: WorkTopic, close: () -> Unit) {
        val id = t.topicId ?: return
        if (saving) return
        val completed = !t.completed
        track("work_roadmap.topic_complete_toggle", mapOf(
            "topic_id" to id, "checked" to completed, "topic_task_count" to t.remaining, "d_day" to t.dDay, "entry_point" to "sheet",
        ))
        if (isPreview) {
            close()
            return
        }
        saving = true
        try {
            val gid = goalId
            val r = Mutation.run(
                invalidate = WorkAPI.changed,
                successMessage = if (completed) "세부 목표를 끝냈어요. 수고했어요!" else "세부 목표를 다시 열었어요.",
            ) {
                WorkAPI.updateTopic(goalId = gid, topicId = id, patch = WorkTopicPatch(completed = completed))
            }
            trackResult(if (completed) "complete" else "reopen")
            if (Mutation.lastErrorStatus == null) {
                outcome = "saved"
                close()
            }
        } finally {
            saving = false
        }
    }

    suspend fun remove(close: () -> Unit) {
        val id = topic?.topicId ?: return
        if (isPreview) {
            close()
            return
        }
        saving = true
        try {
            val gid = goalId
            val r = Mutation.run(
                invalidate = WorkAPI.changed,
                successMessage = "세부 목표를 지웠어요. 들어 있던 Task는 '세부 목표 없음'으로 옮겼어요.",
            ) {
                WorkAPI.deleteTopic(goalId = gid, topicId = id)
            }
            trackResult("delete")
            if (Mutation.lastErrorStatus == null) {
                outcome = "deleted"
                close()
            }
        } finally {
            saving = false
        }
    }

    SheetScaffold(
        onDismiss = onDismiss,
        title = if (isEdit) "세부 목표 고치기" else "세부 목표 추가",
        description = if (isEdit) null else "포트폴리오 · 이력서 제출처럼 큰 단계를 세부 목표로 만들고, 마감을 정해 보세요.",
        dismissible = !saving,
        footer = { close ->
            NButton("취소", variant = NButtonVariant.secondary, enabled = !saving) {
                outcome = "cancel_button"
                close()
            }
            NButton(if (isEdit) "저장" else "만들기", loading = saving, enabled = trimmed.isNotEmpty()) {
                scope.launch { save(close, "button") }
            }
        },
    ) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("이름", hint = "${name.length}/30", required = true)
                NTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "예: 이력서 제출",
                    maxLength = 30,
                    onSubmit = { scope.launch { save(close, "keyboard_done") } },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("마감 없이", modifier = Modifier.weight(1f))
                    NSwitch(isOn = noDue, onIsOnChange = { noDue = it }, label = "마감 없이")
                }
                if (noDue) {
                    FieldMessage(text = "마감 없는 세부 목표는 로드맵 맨 뒤에 놓여요.")
                } else {
                    TimetableDateField(ymd = dueDate, onYmdChange = { dueDate = it })
                }
            }

            if (topic != null) {
                // 고칠 때만: 끝냈어요 / 다시 열기 · 주제 지우기
                // Task 를 모두 끝내서 "끝"인 주제는 되돌릴 게 없어요
                val t = topic
                val autoDone = t.isDone && !t.completed
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NC.line))
                    if (autoDone) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SfImage("checkmark.circle.fill", size = 13.dp, tint = NC.success)
                            Text("Task를 모두 끝내서 끝난 세부 목표예요", style = pt(13, PTWeight.medium), color = NC.success)
                        }
                    } else {
                        NButton(
                            if (t.completed) "다시 열기" else "끝냈어요",
                            variant = if (t.completed) NButtonVariant.secondary else NButtonVariant.brand,
                            icon = if (t.completed) "arrow.uturn.backward" else "checkmark",
                            loading = saving,
                            fullWidth = true,
                        ) {
                            scope.launch { toggleCompleted(t, close) }
                        }
                    }
                    Row(
                        Modifier
                            .height(36.dp)
                            .alpha(if (saving) 0.4f else 1f)
                            .clickable(enabled = !saving, role = Role.Button) {
                                track("work_roadmap.topic_delete_click", mapOf(
                                    "topic_id" to t.topicId, "topic_task_count" to t.remaining, "has_due_date" to (t.dueDate != null),
                                ))
                                confirmDelete = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SfImage("trash", size = 12.dp, tint = NC.danger)
                        Text("세부 목표 지우기", style = pt(13, PTWeight.semibold), color = NC.danger)
                    }
                }
            }
        }
        ConfirmDialog(
            isPresented = confirmDelete,
            onDismiss = { confirmDelete = false },
            title = "이 세부 목표를 지울까요?",
            message = "들어 있던 Task는 '세부 목표 없음'으로 옮겨져요",
            confirmLabel = "지우기",
            tone = ConfirmTone.danger,
            onCancel = { track("work_roadmap.topic_delete_cancel", mapOf("topic_id" to topic?.topicId)) },
        ) {
            track("work_roadmap.topic_delete_confirm", mapOf("topic_id" to topic?.topicId, "topic_task_count" to topic?.remaining))
            scope.launch { remove(close) }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true)
@Composable
private fun WorkTopicSheetAddPreview() {
    NaleulTheme(dark = false) {
        WorkTopicSheet(goalId = 2, topic = null, isPreview = true, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkTopicSheetEditPreview() {
    NaleulTheme(dark = false) {
        WorkTopicSheet(goalId = 2, topic = WorkSampleData.timeline[2], isPreview = true, onDismiss = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkTopicSheetDonePreview() {
    NaleulTheme(dark = false) {
        WorkTopicSheet(goalId = 2, topic = WorkSampleData.timeline[0], isPreview = true, onDismiss = {})
    }
}
