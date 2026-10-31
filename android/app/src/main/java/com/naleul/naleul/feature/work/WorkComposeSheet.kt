// feature/work/WorkComposeSheet.kt
//
// iOS Features/Work/WorkComposeSheet.swift
// 웹 WorkBoardView.tsx ComposePanel · TaskCompose · RetroCompose
// 적기 — 할 일 · 실제로 한 일 · 회고를 한곳에서 적어요.
//  - 할 일: 브레인덤프처럼 여러 줄을 붙여 넣으면 한 번에 추가. 추가해도 시트는 그대로 (계속 적을 수 있게)
//  - 한 일: RecordQuickLogView (업무 일지에 남아요)
//  - 회고: 적기 + 최근 회고
// 보드에서도, 목표 상세의 ✏️ 버튼에서도 열 수 있게 주제 · 회고는 시트가 직접 불러와요.
//
// 시트로 띄우는 화면이라 onDismiss 를 받아요: if (show) WorkComposeSheet(goal = g, onDismiss = { show = false })
// iOS 는 @Environment(GoalDetailViewModel.self) 를 "있으면" 읽었어요(목표 상세에서 열었을 때).
// Android 는 detail 인자로 넘겨요 — 없으면(null) "모두 보기"가 회고 모아보기 화면으로 가요.

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class WorkComposeTab {
    task, log, retro;

    val rawValue: String get() = name

    val label: String
        get() = when (this) {
            task -> "할 일"
            log -> "한 일"
            retro -> "회고"
        }

    val icon: String
        get() = when (this) {
            task -> "checklist"
            log -> "pencil.line"
            retro -> "text.bubble"
        }

    companion object {
        fun fromRaw(raw: String?): WorkComposeTab? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 언제 할지 — 나중에(할 일 목록) · 오늘 · 날짜 */
private enum class WorkComposeWhen { later, today, date }

/** board 를 주면 바로 보여주고, 시트가 열려 있는 동안 새로 불러와요 */
@Composable
fun WorkComposeSheet(
    goal: GoalCategory,
    initialTab: WorkComposeTab = WorkComposeTab.task,
    topicId: Int? = null,
    board: WorkBoard? = null,
    isPreview: Boolean = false,
    /** 목표 상세에서 열었으면 그 화면 모델 (iOS @Environment(GoalDetailViewModel.self) 옵셔널) */
    detail: GoalDetailViewModel? = null,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var boardState by remember { mutableStateOf(board) }

    // 할 일
    var topicIdState by remember { mutableStateOf(topicId) }
    var text by remember { mutableStateOf("") }
    var whenValue by remember { mutableStateOf(WorkComposeWhen.later) }
    var date by remember { mutableStateOf(KST.today()) }
    var hasDue by remember { mutableStateOf(false) }
    var dueDate by remember { mutableStateOf(KST.today()) }
    var saving by remember { mutableStateOf(false) }

    // 회고
    var retroText by remember { mutableStateOf("") }
    var savingRetro by remember { mutableStateOf(false) }
    var deletingRetro by remember { mutableStateOf<WorkRetro?>(null) }
    var confirmRetroDelete by remember { mutableStateOf(false) }
    // 앰플리튜드 — 닫힐 때 몇 개를 적었는지 · 적던 글이 남았는지
    var addedCount by remember { mutableIntStateOf(0) }
    var logDraft by remember { mutableStateOf("") }
    var closedByNext by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val tabOptions: List<Pair<WorkComposeTab, String>> = WorkComposeTab.entries.map { it to it.label }
    val tabIcons: Map<WorkComposeTab, String> = WorkComposeTab.entries.associateWith { it.icon }
    val whenOptions: List<Pair<WorkComposeWhen, String>> = listOf(
        WorkComposeWhen.later to "나중에",
        WorkComposeWhen.today to "오늘",
        WorkComposeWhen.date to "날짜",
    )

    val lines: List<String> = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun track(event: String, props: Map<String, Any?> = emptyMap()) {
        if (isPreview) return
        WK.track(event, goalId = goal.goalCategoryId, props = props)
    }

    /** 그 탭에 적던 글이 있는지 */
    fun draftOf(t: WorkComposeTab): Boolean = when (t) {
        WorkComposeTab.task -> text.split("\n").any { it.isNotBlank() }
        WorkComposeTab.log -> logDraft.isNotBlank()
        WorkComposeTab.retro -> retroText.isNotBlank()
    }

    fun whenAnalytics(): String = when (whenValue) {
        WorkComposeWhen.later -> "BACKLOG"
        WorkComposeWhen.today -> "TODAY"
        WorkComposeWhen.date -> "DATE"
    }

    // 닫힐 때 — '모두 보기'로 넘어가면 next, 아니면 X · 아래로 쓸어 닫기 (적기 시트는 저장해도 닫히지 않아요)
    val latestTab by rememberUpdatedState(tab)
    val latestDirty by rememberUpdatedState(draftOf(WorkComposeTab.task) || draftOf(WorkComposeTab.log) || draftOf(WorkComposeTab.retro))
    val latestAdded by rememberUpdatedState(addedCount)
    val latestNext by rememberUpdatedState(closedByNext)
    DisposableEffect(Unit) {
        onDispose {
            val hint = AnalyticsService.sheetCloseHint
            AnalyticsService.sheetCloseHint = null
            val method = if (latestNext) "next" else (hint ?: "swipe_down")
            track("work_compose.close", mapOf(
                "compose_tab" to latestTab.rawValue, "close_method" to method,
                "is_dirty" to latestDirty, "added_count" to latestAdded,
            ))
        }
    }

    // 시트가 열려 있는 동안 보드(주제 · 회고)를 새로 불러와요
    ReloadOn(listOf(DataKey.work), id = goal.goalCategoryId) {
        if (!isPreview) {
            try {
                WorkAPI.board(goalId = goal.goalCategoryId)?.let { boardState = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // iOS try? — 실패해도 조용히 (이전 값 그대로)
            }
        }
    }

    /** 추가하면 글만 비우고 시트는 그대로 (계속 적을 수 있게) */
    fun finishAdd(count: Int) {
        addedCount += count
        text = ""
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    suspend fun submitTask() {
        val ls = lines
        if (ls.isEmpty() || saving) return
        val day: String? = when (whenValue) {
            WorkComposeWhen.later -> null
            WorkComposeWhen.today -> KST.today()
            WorkComposeWhen.date -> date
        }
        val due: String? = if (hasDue) dueDate else null
        track("work_compose.task_submit", mapOf(
            "line_count" to ls.size, "has_topic" to (topicIdState != null), "when_option" to whenAnalytics(),
            "has_due_date" to (due != null), "is_truncated" to ls.any { it.length > 100 },
            "due_before_date" to (due != null && day != null && due < day), "submit_method" to "button",
        ))
        if (isPreview) {
            text = ""
            return
        }
        saving = true
        try {
            val gid = goal.goalCategoryId
            val tid = topicIdState
            if (ls.size == 1) {
                val name = ls[0].take(100)
                val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "Task를 추가했어요.") {
                    WorkAPI.createTask(goalId = gid, taskName = name, topicId = tid, date = day, dueDate = due)
                    Unit // 응답 본문이 비어도 성공은 성공 (iOS 의 이중 옵셔널과 같게)
                }
                val ok = Mutation.lastErrorStatus == null
                track("work_compose.task_result", GD.result() + mapOf("line_count" to 1, "created_count" to if (ok) 1 else 0))
                if (r != null) finishAdd(1)
            } else {
                val full = ls.joinToString("\n")
                val r = Mutation.run(
                    invalidate = WorkAPI.changed,
                    success = { list: List<WorkTask> -> "Task ${list.size}개를 추가했어요." },
                ) {
                    WorkAPI.createTasks(goalId = gid, text = full, topicId = tid, date = day, dueDate = due)
                }
                val created = r?.size ?: 0
                track("work_compose.task_result", GD.result() + mapOf("line_count" to ls.size, "created_count" to created))
                if (r != null) finishAdd(created)
            }
        } finally {
            saving = false
        }
    }

    suspend fun submitRetro() {
        val content = retroText.trim()
        if (content.isEmpty() || savingRetro) return
        track("work_compose.retro_submit", mapOf("content_length" to content.length, "submit_method" to "button"))
        if (isPreview) {
            retroText = ""
            return
        }
        savingRetro = true
        try {
            val gid = goal.goalCategoryId
            val r = Mutation.run(invalidate = WorkAPI.changed, successMessage = "회고를 남겼어요.") {
                WorkAPI.createRetro(goalId = gid, content = content)
            }
            track("work_compose.retro_result", GD.result() + mapOf("retro_action" to "create"))
            if (Mutation.lastErrorStatus == null) {
                retroText = ""
                addedCount += 1
            }
        } finally {
            savingRetro = false
        }
    }

    suspend fun deleteRetro(r: WorkRetro) {
        if (isPreview) return
        val id = r.retrospectiveId
        Mutation.run(invalidate = WorkAPI.changed, successMessage = "회고를 지웠어요.") {
            WorkAPI.deleteRetro(retrospectiveId = id)
        }
        track("work_compose.retro_result", GD.result() + mapOf("retro_action" to "delete"))
    }

    SheetScaffold(onDismiss = onDismiss, title = "적기", fullHeight = true) { close ->
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SegmentPicker(
                selection = tab,
                onSelectionChange = {
                    if (it != tab) {
                        track("work_compose.tab_click", mapOf(
                            "compose_tab" to it.rawValue, "from_tab" to tab.rawValue, "is_dirty" to draftOf(tab),
                        ))
                        tab = it
                    }
                },
                options = tabOptions,
                icons = tabIcons,
                fullWidth = true,
            )
            when (tab) {
                WorkComposeTab.task -> {
                    // MARK: 할 일
                    val count = lines.size
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldLabel("세부 목표")
                            WorkTopicMenu(
                                selection = topicIdState,
                                onSelectionChange = {
                                    topicIdState = it
                                    track("work_compose.topic_select", mapOf("topic_id" to it, "has_topic" to (it != null)))
                                },
                                topics = boardState?.realTopics ?: emptyList(),
                                showDue = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldLabel("해야 할 일", hint = if (count > 1) "${count}개" else null)
                            WorkTextEditor(
                                text = text,
                                onTextChange = { text = it },
                                placeholder = "해야 할 일을 한 줄에 하나씩 적어 주세요\n여러 줄을 붙여 넣으면 한 번에 추가돼요",
                                minHeight = 128.dp,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldLabel("언제")
                            SegmentPicker(
                                selection = whenValue,
                                onSelectionChange = {
                                    if (it != whenValue) {
                                        whenValue = it
                                        track("work_compose.when_select", mapOf("when_option" to whenAnalytics()))
                                    }
                                },
                                options = whenOptions,
                                fullWidth = true,
                            )
                            if (whenValue == WorkComposeWhen.date) {
                                TimetableDateField(ymd = date, onYmdChange = { date = it })
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

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NButton(
                                if (count > 1) "${count}개 추가" else "할 일 추가",
                                variant = NButtonVariant.brand,
                                size = NButtonSize.lg,
                                loading = saving,
                                fullWidth = true,
                                enabled = count > 0,
                            ) {
                                scope.launch { submitTask() }
                            }
                            Text(
                                "'나중에'는 캘린더에 안 올리고 로드맵 · 보드에만 담아요",
                                style = pt(11),
                                color = NC.ink4,
                            )
                        }
                    }
                }
                WorkComposeTab.log -> {
                    RecordQuickLogView(
                        goal = goal, sheetMode = true, onDone = null,
                        analytics = !isPreview, onDraftChange = { logDraft = it }, onLogged = { addedCount += 1 },
                    )
                }
                WorkComposeTab.retro -> {
                    // MARK: 회고
                    val value = retroText.trim()
                    val retros = boardState?.retrospectives ?: emptyList()
                    val today = boardState?.today ?: KST.today()
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WorkTextEditor(
                            text = retroText,
                            onTextChange = { retroText = it },
                            placeholder = "오늘 일하며 배운 점 · 막힌 점 · 다음에 바꿀 점",
                            minHeight = 120.dp,
                        )
                        NButton(
                            "회고 남기기",
                            variant = NButtonVariant.brand,
                            size = NButtonSize.lg,
                            loading = savingRetro,
                            fullWidth = true,
                            enabled = value.isNotEmpty(),
                        ) {
                            scope.launch { submitRetro() }
                        }

                        if (retros.isNotEmpty()) {
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("최근 회고", style = pt(12, PTWeight.semibold), color = NC.ink3)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .defaultMinSize(minHeight = 28.dp)
                                        .clickable(role = Role.Button) {
                                            track("work_compose.retro_all_click", mapOf("item_count" to retros.size))
                                            closedByNext = true
                                            close()
                                            // 목표 상세에서 열었으면 "회고" 탭으로, 아니면 회고 모아보기(이 목표)로
                                            if (detail != null) {
                                                detail.reflectionsTabRequested = true
                                            } else {
                                                RF.pendingEntry = "work_compose"
                                                AppRouter.openReflections(goalId = goal.goalCategoryId)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("모두 보기", style = pt(12, PTWeight.semibold), color = NC.textBrand)
                                }
                            }
                            val lineColor = NC.line
                            Column(Modifier.fillMaxWidth()) {
                                retros.forEachIndexed { index, r ->
                                    val isLast = index == retros.lastIndex
                                    key(r.id) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .drawBehind {
                                                    if (!isLast) {
                                                        val y = size.height - 0.5.dp.toPx()
                                                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                                    }
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                WorkFormat.dayLabel(r.reviewDate, today = today),
                                                style = pt(12),
                                                color = NC.ink3,
                                                modifier = Modifier.width(56.dp),
                                            )
                                            Text(
                                                r.content,
                                                style = pt(13).copy(lineHeight = 19.sp),
                                                color = NC.ink,
                                                modifier = Modifier.weight(1f),
                                            )
                                            NIconButton(
                                                "trash", size = 13.dp, color = NC.ink4, label = "회고 지우기",
                                                modifier = Modifier.workNegativePadding(top = 6.dp),
                                            ) {
                                                track("work_compose.retro_delete_click", mapOf("days_ago" to GD.daysAgo(r.reviewDate)))
                                                deletingRetro = r
                                                confirmRetroDelete = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ConfirmDialog(
            isPresented = confirmRetroDelete,
            onDismiss = { confirmRetroDelete = false },
            title = "회고를 지울까요?",
            message = "되돌릴 수 없어요.",
            confirmLabel = "지우기",
            tone = ConfirmTone.danger,
            onCancel = { track("work_compose.retro_delete_cancel") },
        ) {
            deletingRetro?.let { r ->
                track("work_compose.retro_delete_confirm", mapOf("days_ago" to GD.daysAgo(r.reviewDate)))
                scope.launch { deleteRetro(r) }
            }
        }
    }
}

// MARK: - 공용: 주제 고르기 · 여러 줄 입력칸 (Task 시트에서도 써요)

/** 주제 없음 + 주제들 (마감이 있으면 "(~10/16(금))") */
@Composable
fun WorkTopicMenu(
    selection: Int?,
    onSelectionChange: (Int?) -> Unit,
    topics: List<WorkTopic>,
    showDue: Boolean = false,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    fun title(t: WorkTopic): String {
        val d = t.dueDate
        if (!showDue || d == null) return t.title
        return "${t.title} (~${WorkFormat.shortDate(d)})"
    }

    val current: String = selection?.let { id -> topics.firstOrNull { it.topicId == id } }?.let { title(it) } ?: "세부 목표 없음"

    val items = listOf(
        MenuItem("세부 목표 없음", icon = if (selection == null) "checkmark" else null) { onSelectionChange(null) },
    ) + topics.map { t ->
        MenuItem(title(t), icon = if (selection != null && selection == t.topicId) "checkmark" else null) {
            onSelectionChange(t.topicId)
        }
    }

    val shape = RoundedCornerShape(Radius.xl)
    NMenu(items = items, enabled = !disabled, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(if (disabled) NC.subtle else NC.surface, shape)
                .border(1.dp, NC.borderSubtle, shape)
                .padding(horizontal = 14.dp)
                .semantics { contentDescription = "세부 목표" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                current,
                style = pt(15, PTWeight.semibold),
                color = if (selection == null) NC.ink3 else NC.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SfImage("chevron.up.chevron.down", size = 12.dp, tint = NC.ink3)
        }
    }
}

/** placeholder 가 있는 여러 줄 입력칸 (iOS TextEditor + .inputStyle) */
@Composable
fun WorkTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp = 96.dp,
    maxLength: Int? = null,
    modifier: Modifier = Modifier,
) {
    NTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = placeholder,
        minHeight = minHeight,
        singleLine = false,
        maxLength = maxLength,
        modifier = modifier,
    )
}

// MARK: - 미리보기
// (목표 · 한 일 입력칸은 다른 기능(goal · record)의 미리보기 데이터를 써요)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkComposeSheetTaskPreview() {
    NaleulTheme(dark = false) {
        WorkComposeSheet(
            goal = GoalPreviewData.recordGoal, initialTab = WorkComposeTab.task, topicId = 3,
            board = WorkSampleData.board, isPreview = true, onDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkComposeSheetRetroPreview() {
    NaleulTheme(dark = false) {
        WorkComposeSheet(
            goal = GoalPreviewData.recordGoal, initialTab = WorkComposeTab.retro,
            board = WorkSampleData.board, isPreview = true, onDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WorkComposeSheetLogPreview() {
    NaleulTheme(dark = false) {
        WorkComposeSheet(
            goal = GoalPreviewData.recordGoal, initialTab = WorkComposeTab.log,
            board = WorkSampleData.board, isPreview = true, onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkTopicMenuPreview() {
    NaleulTheme(dark = false) {
        var sel by remember { mutableStateOf<Int?>(3) }
        var memo by remember { mutableStateOf("") }
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkTopicMenu(selection = sel, onSelectionChange = { sel = it }, topics = WorkSampleData.board.realTopics, showDue = true)
            WorkTextEditor(text = memo, onTextChange = { memo = it }, placeholder = "예: 디자인 시안 v2 기준으로", minHeight = 88.dp, maxLength = 500)
        }
    }
}
