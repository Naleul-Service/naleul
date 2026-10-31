// feature/work/WorkKanbanSection.kt
//
// iOS Features/Work/WorkKanbanSection.swift
// 웹 WorkBoardView.tsx TopicBar · Kanban · BoardCard
// 보드 — 시작 전 · 진행 중 · 완료. 휴대폰은 칸 세 개를 나란히 둘 자리가 없어서
// 세그먼트로 칸을 고르고, 끌어서 옮기기 대신 카드 오른쪽 … 메뉴(옮기기 · 지우기)를 써요.

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
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 주제 필터: 전체 · 주제 없음 · 주제 id */
sealed class WorkTopicFilter {
    object all : WorkTopicFilter()
    object noTopic : WorkTopicFilter()
    data class topic(val id: Int) : WorkTopicFilter()

    fun matches(t: WorkTask): Boolean = when (this) {
        is all -> true
        is noTopic -> t.topicId == null
        is topic -> t.topicId == id
    }

    companion object {
        fun of(topic: WorkTopic): WorkTopicFilter {
            val id = topic.topicId
            return if (id != null) WorkTopicFilter.topic(id) else WorkTopicFilter.noTopic
        }
    }
}

@Composable
fun WorkKanbanSection(
    board: WorkBoard,
    model: WorkBoardModel,
    onOpenTask: (WorkTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf<WorkTopicFilter>(WorkTopicFilter.all) }
    var column by remember { mutableStateOf(WorkColumn.NOT_STARTED) }
    var deleting by remember { mutableStateOf<WorkTask?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** 빈 칸 안내를 이미 보낸 칸·필터 조합 */
    val emptySeen = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()

    fun items(c: WorkColumn): List<WorkTask> = board.board.tasks(c).filter { filter.matches(it) }

    fun track(event: String, props: EventProps = emptyMap()) {
        if (model.isPreview) return
        WK.track(event, board.goalCategoryId, props)
    }

    fun pickFilter(f: WorkTopicFilter) {
        filter = f
        track("work_board.topic_filter_change", mapOf(
            "topic_filter" to f.analyticsName, "topic_id" to f.analyticsTopicId, "entry_point" to "topic_bar",
            "result_count" to WorkColumn.entries.sumOf { c -> board.board.tasks(c).count { f.matches(it) } },
        ))
    }

    fun count(c: WorkColumn): Int =
        if (c == WorkColumn.DONE && filter == WorkTopicFilter.all) board.board.doneTotal else items(c).size

    val columnOptions: List<Pair<WorkColumn, String>> = WorkColumn.entries.map { c -> c to "${c.label} ${count(c)}" }

    val emptyText = when (column) {
        WorkColumn.IN_PROGRESS -> "하고 있는 일을 여기로 옮겨 오세요"
        WorkColumn.DONE -> "끝낸 일이 여기 쌓여요"
        WorkColumn.NOT_STARTED -> "할 일이 없어요"
    }

    SectionCard("보드", modifier = modifier, aside = { Text("눌러서 자세히 고쳐요") }) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 주제 칩
            val total = board.topics.sumOf { it.remaining }
            FlowLayout(spacing = 6.dp) {
                NChip("전체 $total", selected = filter == WorkTopicFilter.all, size = ChipSize.sm) {
                    if (filter != WorkTopicFilter.all) pickFilter(WorkTopicFilter.all)
                }
                for (t in board.topics) {
                    val f = WorkTopicFilter.of(t)
                    NChip("${t.title} ${t.remaining}", selected = filter == f, size = ChipSize.sm) {
                        pickFilter(if (filter == f) WorkTopicFilter.all else f)
                    }
                }
            }
            SegmentPicker(
                selection = column,
                onSelectionChange = { c ->
                    if (c != column) {
                        column = c
                        track("work_board.column_select", mapOf(
                            "to_status" to c.name, "topic_filter" to filter.analyticsName, "item_count" to items(c).size,
                        ))
                    }
                },
                options = columnOptions,
                fullWidth = true,
            )

            // 카드
            val list = items(column)
            if (list.isEmpty()) {
                val emptyKey = "${column.name}:${filter.analyticsName}:${filter.analyticsTopicId ?: 0}"
                LaunchedEffect(emptyKey) {
                    if (emptySeen.add(emptyKey)) {
                        track("work_board.empty_column_view", mapOf(
                            "to_status" to column.name, "topic_filter" to filter.analyticsName,
                            "unfiltered_count" to board.board.tasks(column).size,
                        ))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(NC.subtle.copy(alpha = 0.6f), RoundedCornerShape(Radius.xxl))
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emptyText, style = pt(12), color = NC.ink4)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in list) {
                        key(t.taskId) {
                            WorkKanbanCard(
                                task = t,
                                today = board.today,
                                showTopic = filter == WorkTopicFilter.all,
                                onOpen = { onOpenTask(t) },
                                onToggle = { scope.launch { model.toggle(t, surface = "board") } },
                                onMove = { to -> scope.launch { model.move(t, to = to, surface = "board", method = "menu") } },
                                onDelete = {
                                    track("work_board.card_delete_click", mapOf(
                                        "task_id" to t.taskId, "task_status" to t.status.name, "is_routine" to t.routine,
                                    ))
                                    deleting = t
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
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
        onCancel = { deleting?.let { t -> track("work_board.card_delete_cancel", mapOf("task_id" to t.taskId)) } },
    ) {
        deleting?.let { t ->
            track("work_board.card_delete_confirm", mapOf("task_id" to t.taskId, "task_status" to t.status.name))
            scope.launch { model.delete(t) }
        }
    }
}

// MARK: - 카드

@Composable
private fun WorkKanbanCard(
    task: WorkTask,
    today: String,
    showTopic: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMove: (WorkColumn) -> Unit,
    onDelete: () -> Unit,
) {
    val done = task.isDone
    val shape = RoundedCornerShape(Radius.xl)
    Row(
        Modifier
            .fillMaxWidth()
            .cardShadow(shape)
            .background(NC.surface, shape)
            .border(1.dp, NC.borderSubtle, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WorkCheckCircle(done = done) { onToggle() }
        Column(
            Modifier.weight(1f).clickable(role = Role.Button) { onOpen() },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                task.taskName,
                style = pt(14, PTWeight.semibold).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) NC.ink3 else NC.ink,
            )
            WorkTaskMetaRow(task = task, today = today, showTopic = showTopic)
            if (task.date == null && !done) {
                Text("할 일 목록", style = pt(11), color = NC.ink4)
            }
            val memo = task.memo
            if (!memo.isNullOrEmpty()) {
                Text(
                    memo,
                    style = pt(12),
                    color = NC.ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // … 메뉴: 옮기기(다른 칸들) · 지우기
        // (iOS 는 "옮기기" 섹션 제목이 있지만 NMenu 는 섹션이 없어서 칸 이름만 나열해요)
        val items = WorkColumn.entries.filter { it != task.status }.map { c ->
            MenuItem("옮기기 · ${c.label}", icon = c.icon) { onMove(c) }
        } + MenuItem("지우기", icon = "trash", destructive = true) { onDelete() }
        NMenu(items = items) {
            Box(Modifier.size(width = 32.dp, height = 28.dp), contentAlignment = Alignment.Center) {
                SfImage("ellipsis", size = 14.dp, tint = NC.ink3, contentDescription = "${task.taskName} 옮기기")
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkKanbanSectionPreview() {
    NaleulTheme(dark = false) {
        val board = WorkSampleData.board
        val model = remember { WorkBoardModel.preview(board) }
        Column(Modifier.canvasBackground().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WorkKanbanSection(board = board, model = model, onOpenTask = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkKanbanSectionEmptyPreview() {
    NaleulTheme(dark = false) {
        val board = WorkSampleData.emptyBoard
        val model = remember { WorkBoardModel.preview(board) }
        Column(Modifier.canvasBackground().padding(16.dp)) {
            WorkKanbanSection(board = board, model = model, onOpenTask = {})
        }
    }
}
