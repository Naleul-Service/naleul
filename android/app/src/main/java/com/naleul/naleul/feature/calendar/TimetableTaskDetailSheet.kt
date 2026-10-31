// feature/calendar/TimetableTaskDetailSheet.kt
//
// iOS Features/Calendar/TimetableTaskDetailSheet.swift 를 옮긴 파일.
// Task 블록을 누르면 뜨는 상세 시트.
// 웹 원본: features/timetable/ui/TaskDetail.tsx
//  - 상태 표시 (완료 · 놓침 · 루틴 · 미션 · 약속 · 직접 정한 시간 / 자동 배치)
//  - 이름 · 시간 · 세부 목표 · 마일스톤 · 마감일 — 누르면 그 자리에서 수정 (노션처럼)
//  - 배치 이유 (명세: 항상 설명)
//  - 완료 / 완료 취소 · ⋯ (이어진 N개 묶어 옮기기 · 고정 풀기 · 시간 미정으로 · 삭제)
//  - "계획과 다른 시간에 했어요" · "이 시간에 다른 일을 했어요"
// 웹의 키보드 안내(Enter 저장 · Esc 취소 · Del)는 휴대폰에 없어서 뺐어요.

package com.naleul.naleul.feature.calendar

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class TimetableDetailEdit { title, time, due, priority }

/** 상세 시트에 보여줄 시간 문장 */
object TimetableDetailText {
    /** "11월 18일 (수) · 09:00 – 10:30 (1시간 30분)" */
    fun rangeText(startAt: String, endAt: String?): String {
        val base = startAt.take(10)
        val s = KST.minutesFrom(base, startAt)
        val e = endAt?.let { KST.minutesFrom(base, it) }
        var range = KST.minutesToTime(s)
        if (e != null) {
            range = "${KST.minutesToTime(s)} – ${if (e >= 1440) "다음 날 ${KST.minutesToTime(e)}" else KST.minutesToTime(e)}"
        }
        var out = "${KST.formatMonthDay(base)} · $range"
        if (e != null && e > s) out += " (${KST.formatDuration(e - s)})"
        return out
    }

    fun timeLine(t: TimeBlockTask): String {
        val ps = t.plannedStartAt
        if (ps == null) {
            val day = t.date ?: t.scheduledDate
            if (day != null) return "${KST.formatMonthDay(day)} · 시간 미정"
            return "날짜 미정"
        }
        return rangeText(ps, t.plannedEndAt)
    }
}

@Composable
fun TimetableTaskDetailSheet(
    board: TimetableBoardModel,
    task: TimeBlockTask,
    onDismiss: () -> Unit,
) {
    val t = task
    val perm = TimetableRules.permissions(t)
    var editing by remember { mutableStateOf<TimetableDetailEdit?>(null) }
    var draftEmoji by remember { mutableStateOf("") }
    var draftName by remember { mutableStateOf("") }
    /** 세부 목표 · 마일스톤을 고를 때 쓰는 목표 정보 */
    var goal by remember { mutableStateOf<GoalCategory?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    /** 시트가 닫힌 이유 — 작업이 끝나 닫히거나 다른 시트로 넘어가면 "next" (task_detail.close 안 보냄) */
    var outcome by remember { mutableStateOf<String?>(null) }

    // MARK: 앰플리튜드 (그 자리 고치기)
    fun fieldName(e: TimetableDetailEdit): String = when (e) {
        TimetableDetailEdit.title -> "title"
        TimetableDetailEdit.time -> "time"
        TimetableDetailEdit.due -> "due"
        TimetableDetailEdit.priority -> "priority"
    }

    /** 항목 고치기 열기 — 다른 항목을 고치던 중이면 그건 취소(other_open) */
    fun openEdit(e: TimetableDetailEdit) {
        val cur = editing
        if (cur != null && cur != e) {
            board.sheetTrack("task_detail.field_cancel", mapOf("edit_field" to fieldName(cur), "close_method" to "other_open"))
        }
        board.sheetTrack(
            "task_detail.field_edit_open",
            mapOf("task_id" to t.taskId, "edit_field" to fieldName(e), "source_type" to t.sourceType.rawValue),
        )
        editing = e
    }

    fun cancelEdit(e: TimetableDetailEdit, method: String) {
        board.sheetTrack("task_detail.field_cancel", mapOf("edit_field" to fieldName(e), "close_method" to method))
        editing = null
    }

    fun trackSave(field: String) {
        board.sheetTrack(
            "task_detail.field_save",
            mapOf("task_id" to t.taskId, "edit_field" to field, "source_type" to t.sourceType.rawValue),
        )
    }

    // [UX] 배치 이유를 실제로 보는지 (자동 배치 · 고정 안 한 Task) + 닫힐 때 task_detail.close
    DisposableEffect(t.taskId) {
        if (t.placementReason != null && !t.locked) {
            board.sheetTrack(
                "task_detail.reason_view",
                mapOf("task_id" to t.taskId, "reason_codes" to (t.placementReasonCodes ?: emptyList()).joinToString(",")),
            )
        }
        onDispose {
            editing?.let {
                board.sheetTrack("task_detail.field_cancel", mapOf("edit_field" to fieldName(it), "close_method" to "sheet_close"))
            }
            GD.closeMethod(outcome)?.let { m ->
                board.sheetTrack("task_detail.close", mapOf("task_id" to t.taskId, "close_method" to m))
            }
        }
    }

    // 세부 목표 · 마일스톤을 고칠 수 있을 때만 목표 정보를 불러와요
    LaunchedEffect(t.taskId) {
        val gid = t.goalCategoryId
        if (!perm.editContent || gid == null || board.isOffline || goal != null) return@LaunchedEffect
        goal = try {
            GoalAPI.goal(id = gid)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            null
        }
    }

    // MARK: 저장 (목표 상세 Task 수정 API)
    fun saveContent(input: GoalTaskInput, field: String) {
        val gid = t.goalCategoryId ?: return
        if (saving) return
        saving = true
        scope.launch {
            val ok = board.updateTaskContent(goalId = gid, taskId = t.taskId, input = input)
            saving = false
            board.trackFieldResult(field, ok = ok)
            if (ok) editing = null
        }
    }

    /** 중요도 바꾸기 — 루틴 · 미션은 그날 Task 만 (웹 TaskDetail savePriority) */
    fun pickPriority(p: TaskPriority) {
        val prev = t.taskPriority ?: TaskPriority.fallback
        if (p == prev) {
            cancelEdit(TimetableDetailEdit.priority, "same_select")
            return
        }
        if (saving) return
        board.sheetTrack(
            "task_detail.field_save",
            mapOf(
                "task_id" to t.taskId, "edit_field" to "priority", "source_type" to t.sourceType.rawValue,
                "priority" to p.rawValue, "prev_priority" to prev.rawValue,
            ),
        )
        saving = true
        scope.launch {
            val ok = board.changePriority(taskId = t.taskId, priority = p)
            saving = false
            board.trackFieldResult("priority", ok = ok)
            if (ok) editing = null
        }
    }

    fun saveTitle() {
        if (saving) return
        val name = draftName.trim()
        val e = draftEmoji.trim()
        val emoji: String? = e.ifEmpty { null }
        if (name.isEmpty()) {
            // [UX] 비우고 저장하면 안내 없이 원래 이름으로
            board.sheetTrack("task_detail.title_empty_cancel", mapOf("task_id" to t.taskId))
            editing = null
            return
        }
        if (name == t.taskName && emoji == t.emoji) {
            editing = null
            return
        }
        val input = TimetableRules.currentInput(t)
        input.taskName = name
        input.emoji = emoji
        trackSave("title")
        saveContent(input, field = "title")
    }

    fun pickWorkTopic(goalId: Int, topicId: Int?) {
        if (topicId == t.workTopicId || saving) return
        trackSave("sub")
        saving = true
        scope.launch {
            val ok = board.updateWorkTopic(goalId = goalId, taskId = t.taskId, topicId = topicId)
            saving = false
            board.trackFieldResult("sub", ok = ok)
        }
    }

    fun pickSub(id: Int) {
        if (id == t.generalCategoryId) return
        val input = TimetableRules.currentInput(t)
        input.generalCategoryId = id
        trackSave("sub")
        saveContent(input, field = "sub")
    }

    fun pickMilestone(id: Int?) {
        if (id == t.milestoneId) return
        val input = TimetableRules.currentInput(t)
        input.milestoneId = id
        trackSave("milestone")
        saveContent(input, field = "milestone")
    }

    fun pickDue(date: String?) {
        val input = TimetableRules.currentInput(t)
        input.dueDate = date
        trackSave("due")
        saveContent(input, field = "due")
    }

    SheetScaffold(onDismiss = onDismiss, title = null) {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "Task 상세" }) {
            TimetableTaskBadges(task = t)

            // MARK: 제목
            Box(Modifier.padding(top = 10.dp)) {
                if (editing == TimetableDetailEdit.title) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            NTextField(
                                value = draftEmoji, onValueChange = { draftEmoji = it }, placeholder = "🙂", maxLength = 16,
                                modifier = Modifier.width(56.dp).semantics { contentDescription = "이모지" },
                            )
                            NTextField(
                                value = draftName, onValueChange = { draftName = it }, maxLength = 100,
                                textStyle = pt(16, PTWeight.bold), onSubmit = { saveTitle() },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Task 이름" },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm) { cancelEdit(TimetableDetailEdit.title, "cancel_button") }
                            NButton("저장", size = NButtonSize.sm, loading = saving) { saveTitle() }
                        }
                    }
                } else if (perm.editContent) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                draftEmoji = t.emoji ?: ""
                                draftName = t.taskName
                                openEdit(TimetableDetailEdit.title)
                            }
                            .semantics { contentDescription = "Task 이름 수정" },
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TimetableDetailTitleText(t, Modifier.weight(1f))
                        SfImage("pencil", size = 13.dp, tint = NC.ink4, modifier = Modifier.padding(top = 5.dp))
                    }
                } else {
                    TimetableDetailTitleText(t, Modifier.fillMaxWidth())
                }
            }

            // MARK: 속성 (시간 · 세부 목표 · 마일스톤 · 마감일 …)
            val actual = t.taskStatus == TaskStatus.COMPLETED && t.actualStartAt != null && t.actualEndAt != null
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val aS = t.actualStartAt
                if (actual && aS != null) {
                    TimetablePropRow(icon = { SfImage("checkmark", size = 13.dp, tint = NC.success) }) {
                        val segs = t.actualSegments
                        val text = if (segs != null && segs.size > 1) {
                            segs.joinToString(" · ") { TimetableDetailText.rangeText(it.start, it.end) }
                        } else {
                            TimetableDetailText.rangeText(aS, t.actualEndAt)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("실제", style = pt(14), color = NC.ink3)
                            Text(text, style = pt(14), color = NC.ink2)
                        }
                    }
                }
                // 시간
                if (editing == TimetableDetailEdit.time) {
                    TimetableInlineTimeEditor(
                        board = board, task = t, onDone = { editing = null },
                        onCancel = { cancelEdit(TimetableDetailEdit.time, "cancel_button") },
                    )
                } else {
                    TimetablePropRow(
                        editable = perm.editTime,
                        icon = { SfImage("clock", size = 13.dp, tint = NC.ink3) },
                        onClick = if (perm.editTime) ({ openEdit(TimetableDetailEdit.time) }) else null,
                        label = "시간 수정",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (actual) Text("계획", style = pt(14), color = NC.ink3)
                            Text(TimetableDetailText.timeLine(t), style = pt(14), color = if (actual) NC.ink3 else NC.ink2)
                        }
                    }
                }
                // 중요도 — A 는 TimeBlock 에서 배지 + 테두리로 강조돼요
                val priority = t.taskPriority ?: TaskPriority.fallback
                if (editing == TimetableDetailEdit.priority) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(NC.brandSoft.copy(alpha = 0.4f), RoundedCornerShape(Radius.xl))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PriorityChips(value = priority, enabled = !saving) { pickPriority(it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (t.sourceType == TaskSourceType.MANUAL) "A는 캘린더에서 눈에 띄게 보여요"
                                else "이 날 Task만 바뀌어요 · A는 눈에 띄게 보여요",
                                style = pt(11), color = NC.ink3, modifier = Modifier.weight(1f),
                            )
                            NButton("닫기", variant = NButtonVariant.ghost, size = NButtonSize.sm) {
                                cancelEdit(TimetableDetailEdit.priority, "cancel_button")
                            }
                        }
                    }
                } else {
                    TimetablePropRow(
                        editable = perm.editPriority,
                        icon = { PriorityMark(priority = priority, color = goalColor(t.goalColorCode)) },
                        onClick = if (perm.editPriority) ({ openEdit(TimetableDetailEdit.priority) }) else null,
                        label = "중요도 수정",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("중요도 ${priority.rawValue}", style = pt(14), color = NC.ink2)
                            Text("· ${priority.label}", style = pt(14), color = NC.ink3)
                        }
                    }
                }
                // 목표 › 세부 목표 — 업무형 목표는 로드맵 세부 목표(WorkTopic)
                val gname = t.goalCategoryName
                val isWork = goal?.isWorkGoal ?: (t.workTopicId != null)
                val gid = t.goalCategoryId
                if (gname != null && isWork) {
                    var topics by remember(gid) { mutableStateOf<List<WorkTopic>?>(null) }
                    if (perm.editContent && gid != null) {
                        LaunchedEffect(gid) {
                            topics = try {
                                WorkAPI.board(goalId = gid)?.topics ?: emptyList()
                            } catch (c: CancellationException) {
                                throw c
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val goalColorValue = goalColor(t.goalColorCode)
                    val row: @Composable () -> Unit = {
                        TimetablePropRow(
                            editable = perm.editContent,
                            icon = { Box(Modifier.padding(top = 2.dp).size(11.dp).background(goalColorValue, CircleShape)) },
                        ) {
                            val ink3 = NC.ink3
                            Text(
                                buildAnnotatedString {
                                    append(TimetableRules.title(t.goalEmoji, gname))
                                    withStyle(SpanStyle(color = ink3)) { append(" › ${t.workTopicName ?: "세부 목표 없음"}") }
                                },
                                style = pt(14), color = NC.ink2,
                            )
                        }
                    }
                    if (perm.editContent && gid != null) {
                        val list = topics
                        val items = if (list == null) {
                            listOf(MenuItem(title = "불러오는 중…", enabled = false) { })
                        } else {
                            listOf(MenuItem(title = "세부 목표 없음", icon = if (t.workTopicId == null) "checkmark" else null) { pickWorkTopic(gid, null) }) +
                                list.filter { it.topicId != null && (!it.completed || it.topicId == t.workTopicId) }.map { x ->
                                    MenuItem(
                                        title = listOfNotNull(x.emoji, x.name).joinToString(" "),
                                        icon = if (x.topicId == t.workTopicId) "checkmark" else null,
                                    ) { pickWorkTopic(gid, x.topicId) }
                                }
                        }
                        NMenu(items = items, enabled = !saving, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "세부 목표 수정" }) {
                            row()
                        }
                    } else {
                        row()
                    }
                } else if (gname != null) {
                    val goalColorValue = goalColor(t.goalColorCode)
                    val row: @Composable () -> Unit = {
                        TimetablePropRow(
                            editable = perm.editContent,
                            icon = { Box(Modifier.padding(top = 2.dp).size(11.dp).background(goalColorValue, CircleShape)) },
                        ) {
                            TimetableDetailGoalText(t, gname)
                        }
                    }
                    if (perm.editContent) {
                        val g = goal
                        val items = if (g != null) {
                            g.generalCategories.filter { it.generalCategoryStatus != GoalStatus.DELETED }.map { s ->
                                MenuItem(
                                    title = s.generalCategoryName,
                                    icon = if (s.generalCategoryId == t.generalCategoryId) "checkmark" else null,
                                ) { pickSub(s.generalCategoryId) }
                            }
                        } else {
                            listOf(MenuItem(title = "불러오는 중…", enabled = false) { })
                        }
                        NMenu(items = items, enabled = !saving, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "세부 목표 수정" }) {
                            row()
                        }
                    } else {
                        row()
                    }
                }
                // 마일스톤
                if (t.milestoneTitle != null || perm.editContent) {
                    val row: @Composable () -> Unit = {
                        TimetablePropRow(editable = perm.editContent, icon = { SfImage("flag", size = 13.dp, tint = NC.ink3) }) {
                            val m = t.milestoneTitle
                            if (m != null) {
                                Text(m, style = pt(14), color = NC.ink2)
                            } else {
                                Text("마일스톤 없음", style = pt(14), color = NC.ink4)
                            }
                        }
                    }
                    if (perm.editContent) {
                        val g = goal
                        val items = mutableListOf(MenuItem(title = "없음") { pickMilestone(null) })
                        if (g != null) {
                            for (m in (g.milestones ?: emptyList()).sortedBy { it.dueDate }) {
                                items.add(
                                    MenuItem(title = m.title, icon = if (m.milestoneId == t.milestoneId) "checkmark" else null) {
                                        pickMilestone(m.milestoneId)
                                    }
                                )
                            }
                        } else {
                            items.add(MenuItem(title = "불러오는 중…", enabled = false) { })
                        }
                        NMenu(items = items, enabled = !saving, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "마일스톤 수정" }) {
                            row()
                        }
                    } else {
                        row()
                    }
                }
                // 마감일
                if (t.dueDate != null || perm.editContent) {
                    if (editing == TimetableDetailEdit.due) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(18.dp)) { SfImage("calendar.badge.clock", size = 13.dp, tint = NC.ink3) }
                            TimetableDateField(
                                ymd = t.dueDate ?: KST.today(),
                                onYmdChange = { v -> if (v != t.dueDate) pickDue(v) },
                                modifier = Modifier.weight(1f),
                            )
                            if (t.dueDate != null) {
                                NButton("지우기", variant = NButtonVariant.ghost, size = NButtonSize.sm, icon = "xmark", enabled = !saving) {
                                    pickDue(null)
                                }
                            }
                            NButton("닫기", variant = NButtonVariant.ghost, size = NButtonSize.sm) { cancelEdit(TimetableDetailEdit.due, "close_button") }
                        }
                    } else {
                        TimetablePropRow(
                            editable = perm.editContent,
                            icon = { SfImage("calendar.badge.clock", size = 13.dp, tint = NC.ink3) },
                            onClick = if (perm.editContent) ({ openEdit(TimetableDetailEdit.due) }) else null,
                            label = "마감일 수정",
                        ) {
                            val d = t.dueDate
                            if (d != null) {
                                Text("${KST.formatMonthDay(d)}까지", style = pt(14), color = NC.ink2)
                            } else {
                                Text("마감일 없음", style = pt(14), color = NC.ink4)
                            }
                        }
                    }
                }
                if (t.carryOverCount > 0) {
                    TimetablePropRow(icon = { SfImage("arrow.uturn.backward", size = 13.dp, tint = NC.ink3) }) {
                        Text("${t.carryOverCount}번 미뤄진 Task예요", style = pt(14), color = NC.ink2)
                    }
                }
                if (t.sourceType == TaskSourceType.ROUTINE) {
                    TimetablePropRow(icon = { SfImage("square.stack.3d.up", size = 13.dp, tint = NC.ink3) }) {
                        Text("이름은 목표 상세의 루틴에서 바꿀 수 있어요.", style = pt(13), color = NC.ink3)
                    }
                }
            }

            TimetableReasonSection(task = t, modifier = Modifier.padding(top = 16.dp))

            // MARK: 아래 버튼
            Box(Modifier.padding(top = 20.dp)) {
                TimetableDetailActions(board = board, t = t, perm = perm, onNext = { outcome = "next" })
            }
        }
    }
}

@Composable
private fun TimetableDetailTitleText(t: TimeBlockTask, modifier: Modifier = Modifier) {
    val done = t.taskStatus == TaskStatus.COMPLETED
    Text(
        TimetableRules.title(t.emoji, t.taskName),
        style = NType.h4.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
        color = if (done) NC.ink3 else NC.ink,
        modifier = modifier,
    )
}

@Composable
private fun TimetableDetailGoalText(t: TimeBlockTask, name: String) {
    val ink3 = NC.ink3
    val text = buildAnnotatedString {
        append(TimetableRules.title(t.goalEmoji, name))
        if (t.goalTemporary) withStyle(SpanStyle(color = ink3)) { append(" · 임시 목표") }
        val g = t.generalCategoryName
        if (g != null) withStyle(SpanStyle(color = ink3)) { append(" › $g") }
    }
    Text(text, style = pt(14), color = NC.ink2)
}

@Composable
private fun TimetableDetailActions(
    board: TimetableBoardModel,
    t: TimeBlockTask,
    perm: TimetableTaskPermissions,
    onNext: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val done = t.taskStatus == TaskStatus.COMPLETED
    val chain = board.chainSize(t)
    val now = KST.now()
    val nowAt = KST.toDateTime(now.date, now.minutes)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (board.confirmDeleteId == t.taskId) {
            TimetableInlineConfirm(
                message = "'${t.taskName}'을 삭제할까요?",
                detail = if (t.sourceType == TaskSourceType.ROUTINE) "이 날짜의 루틴 Task 하나만 지워져요." else "되돌릴 수 없어요.",
                loading = board.deleting,
                onConfirm = {
                    board.sheetTrack(
                        "task_detail.delete_confirm",
                        mapOf("task_id" to t.taskId, "source_type" to t.sourceType.rawValue, "submit_method" to "button"),
                    )
                    onNext()
                    scope.launch { board.confirmDelete() }
                },
                onCancel = {
                    board.sheetTrack("task_detail.delete_cancel", mapOf("task_id" to t.taskId, "close_method" to "cancel_button"))
                    board.cancelDelete()
                },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NButton(
                    if (done) "완료 취소" else "완료",
                    variant = if (done) NButtonVariant.secondary else NButtonVariant.brand,
                    icon = if (done) null else "checkmark",
                    loading = board.togglingId == t.taskId || board.busyTask,
                    fullWidth = true,
                    modifier = Modifier.weight(1f),
                ) {
                    board.sheetTrack(
                        "task_detail.complete_click",
                        mapOf(
                            "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                            "check_action" to if (done) "uncomplete" else "complete",
                            "is_missed" to t.missed, "is_scheduled" to (t.plannedStartAt != null),
                        ),
                    )
                    onNext()
                    scope.launch { board.toggleComplete(t, closeOnSuccess = true) }
                }
                // 계획과 다른 시간에 · 나눠서 했을 때 — 완료만큼 자주 써서 같은 크기 버튼으로 (2026-10, 웹과 같게)
                if (t.plannedStartAt != null && t.sourceType != TaskSourceType.MISSION) {
                    NButton(
                        if (done) "실제 시간 고치기" else "다른 시간에 했어요",
                        variant = NButtonVariant.secondary,
                        icon = "clock",
                        fullWidth = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        board.sheetTrack(
                            "task_detail.actual_time_click",
                            mapOf("task_id" to t.taskId, "task_status" to t.taskStatus.rawValue),
                        )
                        onNext()
                        board.completeWithTime(t)
                    }
                }
                if (perm.unlock || perm.unschedule || perm.delete || chain > 1) {
                    val items = mutableListOf<MenuItem>()
                    if (chain > 1) {
                        items.add(MenuItem(title = "이어진 ${chain}개 묶어 옮기기", icon = "link") {
                            board.sheetTrack("task_detail.chain_select_click", mapOf("task_id" to t.taskId, "group_size" to chain))
                            onNext()
                            board.selectChain(t)
                        })
                    }
                    if (perm.unlock) {
                        items.add(MenuItem(title = "AI 자동 배치에 맡기기", icon = "pin.slash") {
                            board.sheetTrack("task_detail.unlock_click", mapOf("task_id" to t.taskId))
                            onNext()
                            scope.launch { board.unlock(t) }
                        })
                    }
                    if (perm.unschedule) {
                        items.add(MenuItem(title = "시간 미정으로", icon = "clock") {
                            board.sheetTrack("task_detail.unschedule_click", mapOf("task_id" to t.taskId))
                            onNext()
                            scope.launch { board.unschedule(t) }
                        })
                    }
                    if (perm.delete) {
                        items.add(MenuItem(title = "삭제", icon = "trash", destructive = true) {
                            board.sheetTrack(
                                "task_detail.delete_click",
                                mapOf("task_id" to t.taskId, "source_type" to t.sourceType.rawValue, "entry_point" to "menu"),
                            )
                            board.askDelete(t)
                        })
                    }
                    val shape = RoundedCornerShape(Radius.xl)
                    NMenu(items = items, modifier = Modifier.semantics { contentDescription = "다른 작업" }) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(NC.surface, shape)
                                .border(1.dp, NC.borderSubtle, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            SfImage("ellipsis", size = 15.dp, tint = NC.ink)
                        }
                    }
                }
            }
            // 다른 날로 (2026-10) — 내일 · 모레 · 날짜 지정. 미션 · 완료한 Task · 시간 없는 루틴은 옮길 수 없어요
            if (!done && t.sourceType != TaskSourceType.MISSION &&
                !(t.sourceType == TaskSourceType.ROUTINE && t.plannedStartAt == null)
            ) {
                TimetableMoveDayRow(board = board, t = t, onNext = onNext)
            }
            // 계획 시각이 이미 시작됐는데 안 했으면: 그 시간에 실제로 한 일을 남길 수 있게
            val ps = t.plannedStartAt
            if (!done && ps != null && ps <= nowAt) {
                val shape = RoundedCornerShape(Radius.lg)
                Text(
                    "이 시간에 다른 일을 했어요",
                    style = pt(13, PTWeight.semibold),
                    color = NC.success,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(NC.successSoft.copy(alpha = 0.5f), shape)
                        .clickable(role = Role.Button) {
                            board.sheetTrack(
                                "task_detail.record_instead_click",
                                mapOf("task_id" to t.taskId, "source_type" to t.sourceType.rawValue),
                            )
                            onNext()
                            board.recordInstead(t)
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** "다른 날로" — 누르면 내일 · 모레 · 날짜 지정 칩이 펼쳐져요 */
@Composable
private fun TimetableMoveDayRow(board: TimetableBoardModel, t: TimeBlockTask, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember(t.taskId) { mutableStateOf(false) }
    var picking by remember(t.taskId) { mutableStateOf(false) }
    val today = KST.today()
    val current = TimetableRules.blockDay(t) ?: t.date
    val base = mapOf("task_id" to t.taskId, "source_type" to t.sourceType.rawValue, "is_scheduled" to (t.plannedStartAt != null))

    fun move(date: String, option: String) {
        board.sheetTrack("task_detail.move_date_select", base + mapOf(
            "move_option" to option, "day_offset" to KST.diffDays(today, date),
        ))
        onNext()
        scope.launch { board.moveToDate(t, date, option) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, enabled = !board.busyTask) {
                    if (!open) board.sheetTrack("task_detail.move_date_click", base)
                    open = !open
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SfImage("calendar", size = 13.dp, tint = NC.ink3)
            Text(
                " 다른 날로 옮기기",
                style = pt(13, PTWeight.semibold),
                color = NC.ink2,
            )
            SfImage(if (open) "chevron.up" else "chevron.down", size = 10.dp, tint = NC.ink3, modifier = Modifier.padding(start = 4.dp))
        }
        if (open) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                val tomorrow = KST.addDays(today, 1)
                val dayAfter = KST.addDays(today, 2)
                NChip("내일 (${KST.weekdayLabel[KST.weekdayIndex(tomorrow)]})", enabled = !board.busyTask && current != tomorrow) { move(tomorrow, "tomorrow") }
                NChip("모레 (${KST.weekdayLabel[KST.weekdayIndex(dayAfter)]})", enabled = !board.busyTask && current != dayAfter) { move(dayAfter, "day_after") }
                NChip("날짜 지정", icon = "calendar", enabled = !board.busyTask) {
                    picking = true
                }
            }
            Text(
                if (t.plannedStartAt != null) "같은 시각으로 옮겨요. 그 시각에 자리가 없으면 그날 시간 미정에 둬요." else "그날 시간 미정에 둬요.",
                style = pt(12),
                color = NC.ink4,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) {
        NDatePickerDialog(
            initial = KST.addDays(today, 1),
            minDate = today,
            onDismiss = { picking = false },
            onPick = { d ->
                picking = false
                if (d != current) move(d, "custom") else Toast.show("지금 있는 날이에요")
            },
        )
    }
}

// MARK: - 상태 표시

@Composable
private fun TimetableTaskBadges(task: TimeBlockTask) {
    val t = task
    FlowLayout(spacing = 6.dp) {
        if (t.taskStatus == TaskStatus.COMPLETED) NBadge("완료", tone = BadgeTone.success)
        if (t.missed) NBadge("놓침", tone = BadgeTone.danger)
        if (t.sourceType == TaskSourceType.ROUTINE) NBadge("루틴", icon = "repeat")
        if (t.sourceType == TaskSourceType.MISSION) NBadge("미션")
        if (t.taskKind == TaskKind.APPOINTMENT) NBadge("약속", tone = BadgeTone.warning)
        if (t.locked) {
            NBadge("직접 정한 시간")
        } else if (t.placedBy == PlacedBy.ENGINE) {
            NBadge("자동 배치", tone = BadgeTone.mint, icon = "sparkles")
        }
    }
}

// MARK: - 한 줄 속성 (누르면 그 자리에서 수정)

@Composable
private fun TimetablePropRow(
    editable: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics { if (label != null) contentDescription = label }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(18.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) { icon() }
        Box(Modifier.weight(1f)) { content() }
        if (editable) SfImage("pencil", size = 11.dp, tint = NC.ink4, modifier = Modifier.padding(top = 3.dp))
    }
}

// MARK: - 시간 바로 고치기 (날짜 · 시작 · 종료 + 루틴이면 적용 범위)

@Composable
private fun TimetableInlineTimeEditor(
    board: TimetableBoardModel,
    task: TimeBlockTask,
    onDone: () -> Unit,
    /** '취소' (task_detail.field_cancel) */
    onCancel: (() -> Unit)? = null,
) {
    val item = task
    val originalDay = remember(task.taskId) { TimetableRules.blockDay(task) ?: task.date ?: task.scheduledDate ?: KST.today() }
    val initRange = remember(task.taskId) {
        val day = originalDay
        val s0 = task.plannedStartAt?.let { KST.minutesFrom(day, it) } ?: (9 * 60)
        val e0 = task.plannedEndAt?.let { KST.minutesFrom(day, it) } ?: (s0 + (task.plannedDurationMinutes ?: 60))
        Pair(timetableWrapMinutes(s0), timetableWrapMinutes(e0))
    }
    var date by remember(task.taskId) { mutableStateOf(originalDay) }
    var start by remember(task.taskId) { mutableStateOf(initRange.first) }
    var end by remember(task.taskId) { mutableStateOf(initRange.second) }
    var scope by remember { mutableStateOf(ChangeScope.DAY) }
    var saving by remember { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()

    val s = start
    // 종료가 시작보다 이르면 다음 날로 넘어가는 것으로 봐요 (예: 23:00 ~ 01:00)
    val e = if (end <= start) end + 1440 else end
    val valid = e - s > 0 && e - s < 1440
    val routine = item.sourceType == TaskSourceType.ROUTINE
    val options: List<TimetableScopeOption>? = if (routine) {
        TimetableScope.options(
            kind = TimetableScopeKind.routine, isToday = date == KST.today(), sameDay = date == originalDay,
            crossesMidnight = e >= 1440,
        )
    } else {
        null
    }
    val effective = TimetableScope.effective(scope, options)
    val info = if (valid) "${KST.formatDuration(e - s)}${if (e >= 1440) " · 다음 날까지" else ""}" else "시간을 확인해 주세요"
    val shape = RoundedCornerShape(Radius.xl)

    Column(
        Modifier
            .fillMaxWidth()
            .background(NC.brandSoft.copy(alpha = 0.4f), shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimetableDateField(ymd = date, onYmdChange = { date = it }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TimetableClockPicker(minutes = start, onMinutesChange = { start = it }, label = "시작", modifier = Modifier.weight(1f))
            Text("–", style = pt(12), color = NC.ink3)
            TimetableClockPicker(minutes = end, onMinutesChange = { end = it }, label = "종료", modifier = Modifier.weight(1f))
        }
        if (options != null) {
            TimetableScopeChips(options = options, value = effective, onChange = { scope = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(info, style = pt(11), color = NC.ink3, modifier = Modifier.weight(1f))
            NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm, enabled = !saving) { (onCancel ?: onDone)() }
            NButton("저장", size = NButtonSize.sm, loading = saving, enabled = valid) {
                if (!saving) {
                    saving = true
                    val d = date
                    board.sheetTrack(
                        "task_detail.field_save",
                        mapOf(
                            "task_id" to item.taskId, "edit_field" to "time", "source_type" to item.sourceType.rawValue,
                            "change_scope" to if (routine) effective.rawValue else null,
                        ),
                    )
                    coroutine.launch {
                        val ok = board.saveTaskTime(item, date = d, start = s, end = e, scope = effective)
                        saving = false
                        board.trackFieldResult("time", ok = ok)
                        if (ok) onDone()
                    }
                }
            }
        }
    }
}

// MARK: - 배치 이유

@Composable
private fun TimetableReasonSection(task: TimeBlockTask, modifier: Modifier = Modifier) {
    val t = task
    val done = t.taskStatus == TaskStatus.COMPLETED
    val shape = RoundedCornerShape(Radius.xl)
    Column(modifier) {
        val reason = t.placementReason
        if (reason != null && !t.locked) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .aiBorder(shape, glass = false)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AIBadge(AIBadgeSize.sm)
                    Text("이 시간에 놓은 이유", style = pt(12, PTWeight.bold), color = NC.textBrand)
                }
                TimetablePlacementReason(text = reason, codes = t.placementReasonCodes ?: emptyList())
            }
        }
        if (t.locked && !done) {
            Text(
                "직접 정한 시간이라 자동 배치가 옮기지 않아요.",
                style = pt(12),
                color = NC.ink3,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NC.subtle, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** 배치 이유 꼬리표 · 문장 나누기 (iOS TimetablePlacementReason 의 static) */
object TimetablePlacementReasonText {
    /** 배치 이유 코드 → 짧은 근거 꼬리표 (웹 REASON_TAG) */
    val tagLabel: Map<String, String> = mapOf(
        "OVERDUE" to "⏰ 마감 지남",
        "DEADLINE_TODAY" to "⏰ 오늘 마감",
        "DEADLINE_SOON" to "⏰ 마감 임박",
        "CARRIED_OVER" to "↩️ 어제 못 한 일",
        "MILESTONE_SOON" to "🚩 마일스톤 임박",
        "HIGH_EFFICIENCY" to "📈 잘 지키는 시간",
        "BEST_REMAINING" to "📈 남은 시간 중 최선",
        "PREFERRED_TIME" to "⭐ 고른 선호 시간",
        "LOW_LOAD_SLOT" to "🗓️ 마감 여유",
        "GOAL_TIME_FIT" to "🎯 이 목표와 잘 맞는 시간",
        "MOVE_HABIT" to "↔️ 자주 옮겨 온 시간",
        "DURATION_ADJUSTED" to "⏱️ 실제 걸린 시간 반영",
    )

    /** 문장 끝("요." · "요)." 등) 기준으로 나눠요 (웹과 같은 정규식) */
    fun sentences(text: String): List<String> {
        val re = Regex("[^.]+?(?:요\\)?\\.|$)")
        val list = re.findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        return list.ifEmpty { listOf(text) }
    }
}

/**
 * 배치 이유: 첫 문장 = 왜 이 시간에, 뒤 문장들 = 내 기록 근거 ("최근 4주 … 12개 중 10개를 해냈어요").
 * 근거가 눈에 들어오도록 문장을 나눠 보여주고, 이유 코드는 꼬리표로.
 */
@Composable
private fun TimetablePlacementReason(text: String, codes: List<String>) {
    val list = TimetablePlacementReasonText.sentences(text)
    val head = list.firstOrNull() ?: text
    val evidence = list.drop(1)
    val tags = codes.mapNotNull { TimetablePlacementReasonText.tagLabel[it] }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(head, style = pt(13).copy(lineHeight = 19.sp), color = NC.ink2)
        if (evidence.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (e in evidence) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Text("•", style = pt(12), color = NC.textBrand)
                        Text(e, style = pt(12), color = NC.ink2)
                    }
                }
            }
        }
        if (tags.isNotEmpty()) {
            FlowLayout(spacing = 4.dp) {
                for (tag in tags) {
                    Text(
                        tag,
                        style = pt(11, PTWeight.semibold),
                        color = NC.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(NC.surface, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimetableTaskDetailSheetPreview() {
    NaleulTheme(dark = false) {
        val d = KST.today()
        val board = remember { TimetableBoardModel.sample(start = d, end = d) }
        TimetableTaskDetailSheet(board = board, task = TimetableSampleData.task, onDismiss = { })
    }
}
