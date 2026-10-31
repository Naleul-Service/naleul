// feature/home/HomeRetroSheet.kt
//
// iOS Features/Home/HomeRetroSheet.swift
// 오늘 회고 시트 — 웹 features/home/ui/TodayRetro.tsx RetroPanel.
// 목표를 하나씩 고를 필요 없이 입력칸 하나로 써요.
//  - 오늘 할 일이 있던 목표 중 아직 회고가 없는 것부터 자동으로 골라 둬요
//  - 키보드의 "다음"으로 저장하면 다음 목표로 넘어가요 (건너뛰기도 가능)
//  - 위의 목표 칩을 눌러 직접 고를 수도 있어요 (✓ = 오늘 회고 있음)
//  - 목표형: 목표 상세 "오늘 기록"과 같은 칸 (수치 + 어땠어요 + 한 줄 회고, HomeCheckInForm) — 어디서 쓰든 같은 기록
//  - 업무형: 지금처럼 회고 글
// 저장 · 수정 · 삭제 후 DataKey.home 을 다시 불러오면 HomeViewModel 의 goals 가 바뀌어 ✓ 가 따라와요.
// 위의 "지난 회고 모아보기"로 날짜별 회고 모음(ReflectionsScreen)을 열어요.
//
//   if (retroOpen) HomeRetroSheet(vm = vm, today = viewDate, onDismiss = { retroOpen = false })

package com.naleul.naleul.feature.home

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HomeRetroSheet(vm: HomeViewModel, today: String, entryPoint: String = "button", onDismiss: () -> Unit) {
    // home.retro_panel_view (닫힘 · 쓰던 글은 HomeRetroPanel 이 세요)
    LaunchedEffect(Unit) {
        val goals = vm.home?.goals ?: emptyList()
        val active = goals.filter { it.activeToday }
        HomeTracker.system(
            HomeEvent.retroPanelView,
            "view_date" to today, "date_relation" to HomeTracker.relation(today),
            "goal_count" to goals.size, "active_goal_count" to active.size,
            "retro_done_count" to active.count { it.hasRecord }, "entry_point" to entryPoint,
        )
    }
    SheetScaffold(
        onDismiss = onDismiss,
        title = "오늘 회고",
        description = "오늘 한 일을 보면서 목표마다 한 줄씩",
        fullHeight = true,
    ) { close ->
        when {
            vm.homeFailed -> Text("회고할 목표를 불러오지 못했어요.", style = pt(14), color = NC.ink3)
            vm.home == null -> Box(Modifier.fillMaxWidth().height(240.dp).background(NC.subtle, RoundedCornerShape(Radius.card)))
            vm.home?.goals.isNullOrEmpty() -> Text("진행 중인 목표가 없어요.", style = pt(14), color = NC.ink3)
            else -> HomeRetroPanel(
                goals = vm.home?.goals ?: emptyList(),
                today = today,
                onOpenReflections = {
                    HomeTracker.track(HomeEvent.retroReflectionsLinkClick, "view_date" to today)
                    close()
                    RF.pendingEntry = "home_retro"
                    AppRouter.openReflections()
                },
            )
        }
    }
}

@Composable
private fun HomeRetroPanel(goals: List<HomeGoalToday>, today: String, onOpenReflections: () -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    var finished by remember { mutableStateOf(false) }
    /** 저장 직후 다시 불러오기 전에도 "썼다"로 보이게 */
    var written by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var text by remember { mutableStateOf("") }
    var showIdle by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    /** 이벤트: 열린 시각 · 닫힌 방법 · 글을 쓰기 시작한 목표 */
    val openedAt = remember { System.currentTimeMillis() }
    var closeMethod by remember { mutableStateOf<String?>(null) }
    var startedInput by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // MARK: 고르기

    fun hasRetro(g: HomeGoalToday): Boolean = g.hasRecord || written.contains(g.goalCategoryId)

    /** 아직 회고를 안 쓴 다음 목표 (오늘 할 일이 있던 목표 순서로 돌아가며, 지금 목표는 빼고) */
    fun nextGoal(after: Int?, w: Set<Int>): HomeGoalToday? {
        val order = goals.filter { it.activeToday }
        var start = -1
        if (after != null) {
            val i = order.indexOfFirst { it.goalCategoryId == after }
            if (i >= 0) start = i
        }
        val rest = order.drop(start + 1) + order.take(maxOf(start, 0))
        return rest.firstOrNull { g -> !g.hasRecord && !w.contains(g.goalCategoryId) && g.goalCategoryId != after }
    }

    val current: HomeGoalToday? = if (finished) null else {
        val p = picked
        (if (p != null) goals.firstOrNull { it.goalCategoryId == p } else null)
            ?: nextGoal(after = null, w = written)
            ?: goals.firstOrNull { it.activeToday }
            ?: goals.firstOrNull()
    }

    fun requestInputFocus() {
        runCatching { inputFocus.requestFocus() }
    }

    fun pick(id: Int) {
        goals.firstOrNull { it.goalCategoryId == id }?.let { g ->
            HomeTracker.track(
                HomeEvent.retroGoalChipClick,
                "goal_category_id" to id, "goal_mode" to g.goalMode?.name, "has_record" to hasRetro(g),
                "is_active_today" to g.activeToday, "content_length" to text.length,
            )
        }
        picked = id
        finished = false
    }

    fun goNext(from: Int, justWrote: Boolean) {
        val w = if (justWrote) written + from else written
        val n = nextGoal(after = from, w = w)
        if (n != null) {
            picked = n.goalCategoryId
        } else {
            picked = null
            finished = true
            focusManager.clearFocus()
        }
    }

    /** method — button · return_key */
    fun submit(method: String = "button") {
        val v = text.trim()
        val g = current ?: return
        if (v.isEmpty() || saving) return
        val id = g.goalCategoryId
        HomeTracker.track(
            HomeEvent.retroSaveClick,
            "goal_category_id" to id, "goal_mode" to g.goalMode?.name, "submit_method" to method,
            "content_length" to v.length, "is_auto_selected" to (picked == null),
            "view_date" to today, "date_relation" to HomeTracker.relation(today),
        )
        saving = true
        scope.launch {
            val ok = Mutation.run(invalidate = listOf(DataKey.home, DataKey.work), successMessage = "회고를 남겼어요.") {
                HomeAPI.createRetro(goalId = id, content = v, date = today)
            }
            HomeTracker.system(
                HomeEvent.retroSaveResult,
                "retro_action" to "create", "result" to if (ok == null) "fail" else "success", "goal_category_id" to id,
            )
            saving = false
            if (ok != null) {
                text = ""
                written = written + id
                goNext(from = id, justWrote = true)
            }
        }
    }

    // MARK: 화면

    val active = goals.filter { it.activeToday }
    val idle = goals.filter { !it.activeToday }
    val doneCount = active.count { hasRetro(it) }
    val cur = current

    // 시트가 닫힐 때 — home.retro_panel_close (+ 쓰던 글이 있으면 home.retro_draft_discard)
    val latestText by rememberUpdatedState(text)
    val latestWritten by rememberUpdatedState(written)
    val latestCur by rememberUpdatedState(cur)
    val latestClose by rememberUpdatedState(closeMethod)
    DisposableEffect(Unit) {
        onDispose {
            val draft = latestText.trim()
            if (draft.isNotEmpty()) {
                HomeTracker.system(
                    HomeEvent.retroDraftDiscard,
                    "discard_reason" to (latestClose ?: "sheet_close"), "content_length" to draft.length,
                    "form_type" to "retro", "goal_category_id" to latestCur?.goalCategoryId,
                )
            }
            HomeTracker.track(
                HomeEvent.retroPanelClose,
                "close_method" to (latestClose ?: "swipe_down"), "written_count" to latestWritten.size,
                "open_duration_seconds" to ((System.currentTimeMillis() - openedAt) / 1000).toInt(),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.isNotEmpty()) {
                    Text("$doneCount / ${active.size} 목표", style = ptMono(12), color = NC.ink3)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            closeMethod = "reflections_link"
                            onOpenReflections()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("books.vertical", size = 13.dp, tint = NC.textBrand)
                    Text("지난 회고 모아보기", style = pt(12, PTWeight.semibold), color = NC.textBrand)
                }
            }
            // 목표 고르기 — 자동으로 골라 두지만 눌러서 바꿀 수 있어요
            FlowLayout(spacing = 6.dp) {
                for (g in (if (active.isEmpty()) idle else active)) {
                    HomeRetroChip(g = g, selected = cur?.goalCategoryId == g.goalCategoryId, hasRetro = hasRetro(g)) {
                        pick(g.goalCategoryId)
                    }
                }
                if (active.isNotEmpty() && idle.isNotEmpty() && !showIdle) {
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                HomeTracker.track(HomeEvent.retroMoreGoalsClick, "item_count" to idle.size)
                                showIdle = true
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ 다른 목표 ${idle.size}", style = pt(12, PTWeight.semibold), color = NC.ink3)
                    }
                }
                if (active.isNotEmpty() && showIdle) {
                    for (g in idle) {
                        HomeRetroChip(g = g, selected = cur?.goalCategoryId == g.goalCategoryId, hasRetro = hasRetro(g)) {
                            pick(g.goalCategoryId)
                        }
                    }
                }
            }
        }

        if (cur != null) {
            HomeRetroEditor(
                g = cur,
                today = today,
                text = text,
                onTextChange = { v ->
                    text = v
                    // 목표마다 처음 글자를 칠 때 한 번 (home.retro_input_start)
                    if (v.isNotEmpty() && cur.goalCategoryId !in startedInput) {
                        startedInput = startedInput + cur.goalCategoryId
                        HomeTracker.track(
                            HomeEvent.retroInputStart,
                            "goal_category_id" to cur.goalCategoryId, "form_type" to "retro", "goal_mode" to cur.goalMode?.name,
                        )
                    }
                },
                saving = saving,
                inputFocus = inputFocus,
                onSubmit = { method -> submit(method) },
                onSkip = { formType ->
                    HomeTracker.track(
                        HomeEvent.retroSkipClick,
                        "goal_category_id" to cur.goalCategoryId, "form_type" to formType,
                        "content_length" to if (formType == "retro") text.length else null,
                    )
                    goNext(from = cur.goalCategoryId, justWrote = false)
                },
                onCheckInSaved = {
                    written = written + cur.goalCategoryId
                    goNext(from = cur.goalCategoryId, justWrote = true)
                },
            )
        } else {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("오늘 회고를 모두 남겼어요 🎉", style = pt(15, PTWeight.bold), color = NC.ink)
                LaunchedEffect(Unit) {
                    HomeTracker.system(HomeEvent.retroAllDoneView, "written_count" to written.size, "active_goal_count" to active.size)
                }
                Text(
                    "더 쓰고 싶은 목표가 있으면 위에서 골라 주세요.",
                    style = pt(13), color = NC.ink3, textAlign = TextAlign.Center,
                )
            }
        }
    }

    // 목표가 바뀌면(골랐거나 다음으로 넘어가면) 입력칸에 바로 커서를 둬요 (iOS inputFocused = true)
    LaunchedEffect(picked) {
        if (picked != null) requestInputFocus()
    }
}

@Composable
private fun HomeRetroChip(g: HomeGoalToday, selected: Boolean, hasRetro: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) NC.actionPrimary else NC.surface, CircleShape)
            .border(1.dp, if (selected) NC.actionPrimary else NC.borderSubtle, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = g.goalName + (if (hasRetro) ", 회고 있음" else "") }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasRetro) {
            SfImage("checkmark", size = 12.dp, tint = if (selected) NC.actionPrimaryFg else NC.success)
        } else {
            Box(Modifier.size(8.dp).background(goalColor(g.colorCode), CircleShape))
        }
        Text(
            g.title,
            style = pt(12, PTWeight.semibold),
            color = if (selected) NC.actionPrimaryFg else NC.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeRetroEditor(
    g: HomeGoalToday,
    today: String,
    text: String,
    onTextChange: (String) -> Unit,
    saving: Boolean,
    inputFocus: FocusRequester,
    /** method — button · return_key */
    onSubmit: (String) -> Unit,
    /** formType — retro · checkin */
    onSkip: (String) -> Unit,
    onCheckInSaved: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(goalColor(g.colorCode), CircleShape))
            Text(g.goalName, style = pt(13, PTWeight.bold), color = NC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.planned + g.done > 0) {
                Text(
                    "· 오늘 ${g.done}${if (g.planned > 0) "/${g.planned}" else ""}개 끝냄",
                    style = ptMono(12),
                    color = NC.ink3,
                    maxLines = 1,
                )
            }
        }

        if (g.doneTasks.isNotEmpty() || g.openTasks.isNotEmpty()) {
            FlowLayout(spacing = 4.dp) {
                for (name in g.doneTasks) {
                    Row(
                        Modifier
                            .background(NC.successSoft, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        SfImage("checkmark", size = 9.dp, tint = NC.success)
                        Text(name, style = pt(11, PTWeight.semibold), color = NC.success, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                for (name in g.openTasks) {
                    Text(
                        name,
                        style = pt(11, PTWeight.medium),
                        color = NC.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(NC.subtle, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .semantics { contentDescription = "오늘 못 한 일: $name" },
                    )
                }
            }
        }

        if (g.retros.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in g.retros) key(r.retrospectiveId) { HomeRetroItem(retro = r, goalId = g.goalCategoryId) }
            }
        }

        val c = g.checkIn
        if (today > KST.today()) {
            // 앞날은 아직 기록할 수 없어요 (목표형 기록은 서버도 미래 날짜를 막아요)
            Text(
                "앞날은 아직 기록할 수 없어요. 그날이 되면 남겨 주세요.",
                style = pt(13),
                color = NC.ink3,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NC.subtle, RoundedCornerShape(Radius.xl))
                    .padding(12.dp),
            )
            LaunchedEffect(today) { HomeTracker.system(HomeEvent.retroFutureBlockedView, "view_date" to today) }
        } else if (c != null) {
            key("${g.goalCategoryId}-$today") {
                HomeCheckInForm(goal = g, checkIn = c, date = today, onSaved = onCheckInSaved, onSkip = { onSkip("checkin") })
            }
        } else {
            // 업무형: 회고 글
            NTextField(
                value = text,
                // 여러 줄 입력칸은 엔터가 줄바꿈으로 들어올 수 있어서, 그걸 "저장하고 다음"으로 바꿔요
                onValueChange = { v ->
                    if (v.endsWith("\n")) {
                        onTextChange(v.dropLast(1))
                        onSubmit("return_key")
                    } else {
                        onTextChange(v)
                    }
                },
                placeholder = g.prompt,
                minHeight = 96.dp,
                singleLine = false,
                imeAction = ImeAction.Next,
                onSubmit = { onSubmit("return_key") },
                textStyle = pt(14),
                modifier = Modifier
                    .focusRequester(inputFocus)
                    .semantics { contentDescription = "${g.goalName} 오늘 회고" },
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NButton("건너뛰기", variant = NButtonVariant.ghost, size = NButtonSize.sm) { onSkip("retro") }
                Spacer(Modifier.weight(1f))
                val fg = nButtonContentColor(NButtonVariant.primary, !saving && text.trim().isNotEmpty())
                Row(
                    Modifier.nButton(
                        variant = NButtonVariant.primary,
                        size = NButtonSize.sm,
                        enabled = !saving && text.trim().isNotEmpty(),
                    ) { onSubmit("button") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (saving) CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text("저장하고 다음", style = pt(13, PTWeight.semibold), color = fg)
                    SfImage("arrow.right", size = 12.dp, tint = fg)
                }
            }
            Text("키보드의 다음 버튼으로 저장하고 다음 목표로 넘어가요.", style = pt(11), color = NC.ink4)
        }
    }
}

// MARK: - 이미 쓴 회고 (고치기 · 지우기)

@Composable
private fun HomeRetroItem(retro: HomeRetro, goalId: Int? = null) {
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        val v = value.trim()
        if (v.isEmpty() || busy) return
        busy = true
        val id = retro.retrospectiveId
        scope.launch {
            val ok = Mutation.run(invalidate = listOf(DataKey.home, DataKey.work), successMessage = "회고를 고쳤어요.") {
                WorkAPI.updateRetro(retrospectiveId = id, content = v)
            }
            HomeTracker.system(
                HomeEvent.retroSaveResult,
                "retro_action" to "update", "result" to if (ok == null) "fail" else "success", "goal_category_id" to goalId,
            )
            busy = false
            if (ok != null) editing = false
        }
    }

    fun remove() {
        if (busy) return
        busy = true
        val id = retro.retrospectiveId
        scope.launch {
            val ok = Mutation.run(invalidate = listOf(DataKey.home, DataKey.work), successMessage = "회고를 지웠어요.") {
                WorkAPI.deleteRetro(retrospectiveId = id)
            }
            HomeTracker.system(
                HomeEvent.retroSaveResult,
                "retro_action" to "delete", "result" to if (ok == null) "fail" else "success", "goal_category_id" to goalId,
            )
            busy = false
        }
    }

    if (editing) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NTextField(
                value = value,
                onValueChange = { value = it },
                minHeight = 72.dp,
                singleLine = false,
                modifier = Modifier.semantics { contentDescription = "회고 고치기" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NButton("취소", variant = NButtonVariant.ghost, size = NButtonSize.sm) {
                    HomeTracker.track(HomeEvent.retroEditCancelClick, "close_method" to "cancel_button")
                    editing = false
                }
                NButton("저장", size = NButtonSize.sm, loading = busy, enabled = value.trim().isNotEmpty()) {
                    HomeTracker.track(HomeEvent.retroEditSaveClick, "content_length" to value.trim().length)
                    save()
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .background(NC.subtle.copy(alpha = 0.7f), RoundedCornerShape(Radius.xl))
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(retro.content, style = pt(13), color = NC.ink, modifier = Modifier.weight(1f).padding(top = 2.dp))
            Row(Modifier.offset(y = (-6).dp)) {
                NIconButton("pencil", size = 13.dp, color = NC.ink4, label = "회고 고치기") {
                    HomeTracker.track(HomeEvent.retroEditClick, "goal_category_id" to goalId)
                    value = retro.content
                    editing = true
                }
                NIconButton("trash", size = 13.dp, color = NC.ink4, label = "회고 지우기", enabled = !busy) {
                    HomeTracker.track(HomeEvent.retroDeleteClick, "goal_category_id" to goalId)
                    remove()
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeRetroSheetPreview() {
    NaleulTheme(dark = false) {
        // 시트(ModalBottomSheet)는 미리보기에서 잘 안 보여서 안쪽 내용만 그려요
        val home = HomeSampleData.homeToday(KST.today())
        Column(Modifier.canvasBackground().padding(24.dp)) {
            HomeRetroPanel(goals = home.goals, today = KST.today(), onOpenReflections = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeRetroSheetFailedPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(24.dp)) {
            Text("회고할 목표를 불러오지 못했어요.", style = pt(14), color = NC.ink3)
        }
    }
}
