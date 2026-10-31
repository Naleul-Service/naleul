// feature/calendar/CalendarScreen.kt
//
// iOS Features/Calendar/CalendarScreen.swift 를 옮긴 파일.
// 캘린더 탭 루트 (일간 · 주간 · 월간).
// 웹 원본: features/timetable/ui/CalendarView.tsx + app/(main)/calendar/page.tsx
//
// 웹 URL(?view=week&date=…&add=ai) ↔ Android
//  - view · date  → 이 화면의 상태 (다른 화면에서는 AppRouter.openCalendar(view, date) → AppRouter.calendarRequest 로 와요)
//  - add=ai       → AppRouter.openTaskAdd(mode) → AppRouter.taskAdd 시트 (TaskAddSheet)
//  - AI로 일정 조정 → AppRouter.scheduleAi 시트 (ScheduleAiSheet)
//
// 화면 배치 (휴대폰 세로 · 모바일 UX)
//  - 위: 날짜 제목 [‹ ›] · [오늘] · 일/주/월 (한 줄). 일간이면 아래에 밀어서 넘기는 요일 띠
//  - 가운데: TimeTable 격자 (화면 폭을 꽉 채워요 — 이 화면의 주인공)
//  - 오른쪽 아래 떠 있는 버튼: ✨ AI 배치 (시간 미정 개수 배지) · ＋ 추가 메뉴 (AI로 정리 · 직접 추가 · 실제로 한 일)
//  - 아래: 범위 고르기 · AI 배치 확인 · 이월 제안 바

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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 캘린더 탭 첫 화면 */
@Composable
fun CalendarScreen() {
    val board = rememberScreenModel { TimetableBoardModel() }
    val month = rememberScreenModel { CalendarMonthModel() }
    CalendarScreenContent(board = board, month = month, initialMode = CalendarViewMode.day)
}

/** 미리보기용 (네트워크 없이 샘플 데이터) */
@Composable
fun CalendarScreen(previewMode: CalendarViewMode) {
    val today = KST.today()
    val start = if (previewMode == CalendarViewMode.week) KST.startOfWeek(today) else today
    val end = if (previewMode == CalendarViewMode.week) KST.addDays(start, 6) else today
    val board = remember { TimetableBoardModel.sample(start = start, end = end) }
    val month = remember { CalendarMonthModel.sample(today) }
    CalendarScreenContent(board = board, month = month, initialMode = previewMode)
}

@Composable
private fun CalendarScreenContent(
    board: TimetableBoardModel,
    month: CalendarMonthModel,
    initialMode: CalendarViewMode,
) {
    /** 앱을 열면 "오늘 하루"부터 보여줘요 (모바일에서는 주간 7칸이 너무 좁아요) */
    var mode by remember { mutableStateOf(initialMode) }
    var date by remember { mutableStateOf(KST.today()) }
    /** Task 추가 시트에서 방금 추가한 Task — 캘린더에 NEW 로 표시 */
    var newIds by remember { mutableStateOf(setOf<Int>()) }
    /** 빈 칸을 눌러 연 + 메뉴 (날짜, 분) — null = 닫힘 */
    var emptyMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val scope = rememberCoroutineScope()

    // 월간은 아래 패널에 보여줄 "선택한 날" 하루만 불러와요 (달 전체 요약은 MonthView 가 따로)
    // 3일: 고른 날부터 3일 (2026-10)
    val rangeStart = if (mode == CalendarViewMode.week) KST.startOfWeek(date) else date
    val rangeEnd = when (mode) {
        CalendarViewMode.week -> KST.addDays(rangeStart, 6)
        CalendarViewMode.threeDay -> KST.addDays(date, 2)
        else -> date
    }

    /** 아래에 확인 바(범위 고르기 · AI 배치 확인)가 떠 있으면 떠 있는 버튼은 숨겨요 */
    val bottomBarBusy = board.ask != null || board.proposing

    fun go(newMode: CalendarViewMode? = null, newDate: String? = null) {
        board.close()
        if (newMode != null) mode = newMode
        if (newDate != null) date = newDate
    }

    /** method: button(‹ ›) · swipe(머리글 · 요일 띠 밀기) */
    fun move(n: Int, method: String) {
        val step = when (mode) {
            CalendarViewMode.week -> 7
            CalendarViewMode.threeDay -> 3
            else -> 1
        }
        val next = if (mode == CalendarViewMode.month) KST.addMonths(date, n) else KST.addDays(date, n * step)
        if (!board.isOffline) {
            CAL.track(
                "calendar.date_nav_click",
                mapOf(
                    "view_mode" to mode.rawValue, "date_action" to if (n < 0) "prev" else "next",
                    "view_date" to next, "date_relation" to CAL.relation(next), "nav_method" to method,
                ),
            )
        }
        go(newDate = next)
    }

    /** 미리보기(샘플 보드)에서는 보내지 않아요 */
    fun calTrack(event: String, props: EventProps) {
        if (!board.isOffline) CAL.track(event, props)
    }

    /** Task 추가 — 월간이면 일간으로 바꿔서 추가한 블록이 보이게 */
    fun openTaskAdd(addMode: TaskAddMode, standalone: Boolean = false, prefillDate: String? = null, prefillStart: Int? = null) {
        if (mode == CalendarViewMode.month) mode = CalendarViewMode.day
        AppRouter.openTaskAdd(mode = addMode, date = prefillDate, startMinutes = prefillStart, standalone = standalone)
    }

    /** "실제로 한 일" — 오늘(또는 보고 있는 지난 날)의 최근 1시간으로 열어요 */
    fun openActivityNow() {
        val now = KST.now()
        val today = now.date
        val day = if (mode == CalendarViewMode.day && date < today) date else today
        val endMin = if (day == today) (now.minutes / 10) * 10 else 13 * 60
        calTrack("calendar.activity_add_click", mapOf("view_mode" to mode.rawValue, "date_relation" to CAL.relation(day), "entry_source" to "fab"))
        board.openActivity(TimetableActivityDraft(activity = null, date = day, start = maxOf(endMin - 60, 0), end = endMin, replaced = null))
    }

    /** Task 추가 시트에서 추가가 끝났을 때 (웹 CalendarView onAdded) */
    fun handleAdded(info: TaskAddedInfo) {
        newIds = newIds + info.taskIds
        val start = rangeStart
        val end = rangeEnd
        val inView: (String) -> Boolean = { d -> d >= start && d <= end }
        // AI로 정리한 Task 는 바로 놓지 않고 점선으로 먼저 보여 줘요 → 아래 바에서 "이대로 진행할까요?"
        val pending = info.pendingTaskIds
        if (!pending.isNullOrEmpty()) {
            // 시트가 캘린더를 가리고 있으면 점선 제안이 안 보여서 닫아요 (task_add.panel_close 의 close_method = next)
            AnalyticsService.sheetCloseHint = "next"
            AppRouter.taskAdd = null
            val fallback = info.date
            scope.launch {
                board.previewFill(
                    TimetableFillTarget.tasks(pending),
                    kind = TimetableProposalKind.added,
                    onPreview = { r ->
                        TA.track("task_add_result.proposal_ready", mapOf(
                            "surface" to "calendar", "result" to "success",
                            "placed_count" to r.proposals.size, "unscheduled_count" to r.unscheduled.size,
                        ))
                        val first = r.proposals.map { it.date }.sorted().firstOrNull() ?: fallback
                        if (first != null && !inView(first)) go(newDate = first)
                    },
                    onResolve = { applied, shown ->
                        // 예 = 그 자리에 저장 · 아니요 = Task 는 저장된 채 시간 미정
                        TA.track("task_add_result.proposal_resolve", mapOf(
                            "surface" to "calendar", "proposal_applied" to applied,
                            "dragged_count" to shown.count { it.start != it.proposedStart || it.end != it.proposedEnd },
                            "placed_count" to if (applied) shown.size else 0,
                            "unscheduled_count" to if (applied) board.proposalUnscheduled else pending.size,
                        ))
                    },
                    onError = {
                        TA.track("task_add_result.proposal_ready", mapOf(
                            "surface" to "calendar", "result" to "fail", "placed_count" to 0, "unscheduled_count" to pending.size,
                        ))
                    },
                )
            }
            return
        }
        // 추가한 날이 지금 보는 기간 밖이면 그 날(주)로 옮겨요
        val d = info.date
        if (d != null && !inView(d)) go(newDate = d)
    }

    // 다른 화면에서 온 "이 날짜/보기로" 요청 (웹 /calendar?view=&date=)
    val request = AppRouter.calendarRequest
    LaunchedEffect(request) {
        val req = request ?: return@LaunchedEffect
        board.close()
        req.view?.let { mode = it }
        val d = req.date
        if (d != null && KST.isYmd(d)) date = d
        req.taskId?.let { board.openTaskWhenLoaded(it) }
        AppRouter.calendarRequest = null
    }

    ReloadOn(listOf(DataKey.timetable, DataKey.activities), id = "${rangeStart}_${rangeEnd}_${mode.rawValue}") {
        board.setRange(rangeStart, rangeEnd)
        board.load(withActivities = mode != CalendarViewMode.month)
    }

    // 캘린더 탭이 처음 그려진 직후 (다른 탭에 갔다 오면 다시)
    LaunchedEffect(Unit) {
        calTrack("calendar.pageview", mapOf("view_mode" to mode.rawValue, "view_date" to date, "date_relation" to CAL.relation(date)))
    }

    // 화면을 떠날 때 고르지 않은 범위는 "이날만"으로 저장 (iOS .onDisappear)
    DisposableEffect(board) {
        onDispose { board.saveAskOnLeave() }
    }

    // 스펙 9.1: 캘린더는 페이지 · 머리글 · 격자 모두 bg/surface (흰색)
    Column(Modifier.fillMaxSize().background(NC.bgSurface).statusBarsPadding()) {
        CalendarHeader(
            modifier = Modifier.pageGutter(),
            mode = mode, date = date, rangeStart = rangeStart, rangeEnd = rangeEnd,
            fetching = board.isFetching && board.loadedOnce,
            onMode = { m ->
                if (m != mode) {
                    calTrack("calendar.view_change", mapOf("view_mode" to m.rawValue, "from_view" to mode.rawValue, "view_date" to date))
                }
                go(newMode = m)
            },
            onToday = {
                val today = KST.today()
                calTrack(
                    "calendar.date_nav_click",
                    mapOf(
                        "view_mode" to mode.rawValue, "date_action" to "today", "view_date" to today,
                        "date_relation" to "today", "nav_method" to "button",
                    ),
                )
                go(newDate = today)
            },
            onMove = { n, method -> move(n, method) },
            onSelectDate = { d ->
                calTrack("calendar.week_strip_select", mapOf("view_date" to d, "date_relation" to CAL.relation(d)))
                go(newDate = d)
            },
        )
        // 스펙 10.3: 주간은 머리글 아래(날짜 줄 + 격자)를 좌우 10 · 그 외는 페이지 여백 20
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = if (mode == CalendarViewMode.week || mode == CalendarViewMode.threeDay) 10.dp else PageGutter)) {
            if (mode == CalendarViewMode.month) {
                CalendarMonthView(
                    model = month, board = board, date = date,
                    onSelectDate = { d -> go(newDate = d) },
                    onOpenDay = { d -> go(newMode = CalendarViewMode.day, newDate = d) },
                )
            } else {
                CalendarBoardSection(
                    board = board, mode = mode, date = date, newIds = newIds,
                    onEmptyTap = { d, m -> emptyMenu = d to m },
                    onDayClick = { d ->
                        calTrack("calendar.day_header_click", mapOf("view_date" to d, "date_relation" to CAL.relation(d)))
                        go(newMode = CalendarViewMode.day, newDate = d)
                    },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !bottomBarBusy,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 0.dp, bottom = 16.dp),
            ) {
                CalendarFloatingActions(
                    board = board, mode = mode,
                    onAdd = { m ->
                        calTrack("calendar.task_add_click", mapOf("view_mode" to mode.rawValue, "view_date" to date, "add_mode" to m.rawValue, "entry_source" to "fab"))
                        openTaskAdd(m, standalone = true)
                    },
                    onActivity = { openActivityNow() },
                    onScheduleAi = {
                        calTrack("calendar.schedule_ai_click", mapOf("view_mode" to mode.rawValue, "view_date" to date))
                        AppRouter.scheduleAi = ScheduleAiRequest()
                    },
                )
            }
        }
        // 범위 고르기 · AI 배치 확인 · 이월 제안 바 (iOS .safeAreaInset(edge: .bottom))
        TimetableBottomBars(board = board)
    }

    // 빈 칸 + 메뉴 (스펙 11.4) — 계획 할 일 · 실제 한 일은 그 날짜 · 시각으로 미리 채워요
    emptyMenu?.let { (d, m) ->
        val now = KST.now()
        val past = d < now.date || (d == now.date && m < now.minutes)
        CalendarEmptySlotMenu(
            date = d, minutes = m, canLogActual = past,
            onPick = { picked ->
                when (picked) {
                    null -> {
                        calTrack("calendar.activity_add_click", mapOf("view_mode" to mode.rawValue, "date_relation" to CAL.relation(d), "entry_source" to "empty_tap"))
                        board.openActivity(TimetableActivityDraft(activity = null, date = d, start = m, end = m + 60, replaced = null))
                    }
                    else -> {
                        calTrack("calendar.task_add_click", mapOf("view_mode" to mode.rawValue, "view_date" to d, "add_mode" to picked.rawValue, "entry_source" to "empty_tap"))
                        if (picked == TaskAddMode.manual) openTaskAdd(picked, standalone = true, prefillDate = d, prefillStart = m)
                        else openTaskAdd(picked, standalone = true)
                    }
                }
            },
            onDismiss = { emptyMenu = null },
        )
    }

    // 블록을 누르면 뜨는 시트
    TimetableSheets(board)

    // Task 추가 시트 (캘린더를 보면서 추가)
    AppRouter.taskAdd?.let { req ->
        key(req.id) {
            TaskAddSheet(
                initialMode = req.mode,
                standalone = req.standalone,
                prefillDate = req.date,
                prefillStartMinutes = req.startMinutes,
                onAdded = { info -> handleAdded(info) },
                onDismiss = { if (AppRouter.taskAdd?.id == req.id) AppRouter.taskAdd = null },
            )
        }
    }

    // AI로 일정 조정 — 보고 있는 주/날 기준 (월간이면 그 날이 든 주)
    AppRouter.scheduleAi?.let { req ->
        val s = if (mode == CalendarViewMode.day || mode == CalendarViewMode.threeDay) date else KST.startOfWeek(date)
        key(req.id) {
            ScheduleAiSheet(
                start = s,
                end = when (mode) {
                    CalendarViewMode.day -> date
                    CalendarViewMode.threeDay -> KST.addDays(date, 2)
                    else -> KST.addDays(s, 6)
                },
                prompt = req.prompt,
                onDismiss = { if (AppRouter.scheduleAi?.id == req.id) AppRouter.scheduleAi = null },
            )
        }
    }
}

// MARK: - 위쪽 (날짜 제목 · 이동 · 보기)

@Composable
private fun CalendarHeader(
    mode: CalendarViewMode,
    date: String,
    rangeStart: String,
    rangeEnd: String,
    fetching: Boolean,
    onMode: (CalendarViewMode) -> Unit,
    onToday: () -> Unit,
    /** (몇 칸, button · swipe) */
    onMove: (Int, String) -> Unit,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = when (mode) {
        CalendarViewMode.month -> "달"
        CalendarViewMode.week -> "주"
        CalendarViewMode.threeDay -> "3일"
        CalendarViewMode.day -> "날"
    }
    val today = KST.today()
    val showsToday = if (mode == CalendarViewMode.month) {
        KST.yearOf(date) == KST.yearOf(today) && KST.monthOf(date) == KST.monthOf(today)
    } else {
        rangeStart <= today && today <= rangeEnd
    }
    /** 일간 "10월 9일 (목)" · 주간 "10월 5일 – 11일" · 월간 "2026년 10월" */
    val title = when (mode) {
        CalendarViewMode.day -> KST.formatMonthDay(date)
        CalendarViewMode.week, CalendarViewMode.threeDay -> {
            val s = rangeStart
            val e = rangeEnd
            val head = "${KST.monthOf(s)}월 ${KST.dayOfMonth(s)}일"
            if (KST.monthOf(s) == KST.monthOf(e)) "$head – ${KST.dayOfMonth(e)}일" else "$head – ${KST.monthOf(e)}월 ${KST.dayOfMonth(e)}일"
        }
        CalendarViewMode.month -> "${KST.yearOf(date)}년 ${KST.monthOf(date)}월"
    }
    val line = NC.line
    val move by rememberUpdatedState(onMove)

    Column(
        modifier
            .fillMaxWidth()
            .background(NC.surface)
            .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 0.5.dp.toPx()) }
            // 머리글을 좌우로 밀면 이전 · 다음 (일간은 요일 띠가 주 단위로 따로 처리해요)
            .pointerInput(mode) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        if (mode != CalendarViewMode.day && abs(total.x) > abs(total.y) * 1.5f && abs(total.x) > 50.dp.toPx()) {
                            move(if (total.x < 0) 1 else -1, "swipe")
                        }
                    },
                    onDrag = { change, amount ->
                        total += amount
                        change.consume()
                    },
                )
            }
            .padding(top = 6.dp, bottom = 8.dp),
        // 스펙 9.2: 제목 줄과 요일 띠 사이 12
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(start = 0.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            // 스펙 9.3: 제목 H3 18 · 화살표 IconButton SM (아이콘 16)
            NIconButton("chevron.left", size = 16.dp, color = NC.textSecondary, label = "이전 $unit") { onMove(-1, "button") }
            Text(title, style = NType.h3, color = NC.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NIconButton("chevron.right", size = 16.dp, color = NC.textSecondary, label = "다음 $unit") { onMove(1, "button") }
            if (fetching) {
                CircularProgressIndicator(color = NC.ink4, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.weight(1f).widthIn(min = 6.dp))
            if (!showsToday) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .height(30.dp)
                        .clip(CircleShape)
                        .background(NC.brandSoft)
                        .clickable(role = Role.Button, onClick = onToday)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("오늘", style = pt(13, PTWeight.semibold), color = NC.textBrand)
                }
            }
            CalendarModeSwitch(mode = mode, onMode = onMode)
        }
        if (mode == CalendarViewMode.day) {
            CalendarWeekStrip(
                date = date, onSelect = onSelectDate, onMoveWeek = { n -> onMove(n * 7, "swipe") },
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/** 일 · 주 · 월 작은 전환 (한 줄 머리글에 들어가게 짧게) */
@Composable
private fun CalendarModeSwitch(mode: CalendarViewMode, onMode: (CalendarViewMode) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val modes = listOf(CalendarViewMode.day, CalendarViewMode.threeDay, CalendarViewMode.week, CalendarViewMode.month)
    Row(
        Modifier
            .background(NC.bgSubtle, RoundedCornerShape(Radius.segment))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (m in modes) {
            val on = m == mode
            val label = when (m) {
                CalendarViewMode.day -> "일"
                CalendarViewMode.threeDay -> "3일"
                CalendarViewMode.week -> "주"
                CalendarViewMode.month -> "월"
            }
            val shape = RoundedCornerShape(Radius.segmentItem)
            Box(
                Modifier
                    .size(36.dp, 30.dp)
                    .then(if (on) Modifier.segShadow(shape).background(NC.bgSurface, shape) else Modifier)
                    .clip(shape)
                    .clickable(role = Role.Tab) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMode(m)
                    }
                    .semantics {
                        contentDescription = "${label}간 보기"
                        selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NType.labelMD, color = if (on) NC.textPrimary else NC.textSecondary)
            }
        }
    }
}

/** 일간 위 요일 띠 — 날짜를 누르면 그날로, 좌우로 밀면 이전 · 다음 주 */
@Composable
private fun CalendarWeekStrip(
    date: String,
    onSelect: (String) -> Unit,
    onMoveWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = KST.startOfWeek(date)
    val today = KST.today()
    val haptic = LocalHapticFeedback.current
    val moveWeek by rememberUpdatedState(onMoveWeek)
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        if (abs(total.x) > abs(total.y) && abs(total.x) > 40.dp.toPx()) {
                            moveWeek(if (total.x < 0) 1 else -1)
                        }
                    },
                    onDrag = { change, amount ->
                        total += amount
                        change.consume()
                    },
                )
            },
    ) {
        for (i in 0 until 7) {
            val d = KST.addDays(start, i)
            val selected = d == date
            val isToday = d == today
            val weekdayColor = if (i == 6) NC.calendarHoliday else (if (i == 5) NC.calendarSaturday else NC.textTertiary)
            val numberColor = if (selected) {
                if (isToday) NC.calendarTodayFg else NC.actionPrimaryFg
            } else {
                if (isToday) NC.textPrimary else (if (d < today) NC.textTertiary else NC.textPrimary)
            }
            val brand = NC.calendarToday
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(d)
                        }
                    }
                    .semantics {
                        contentDescription = KST.formatMonthDay(d) + (if (isToday) ", 오늘" else "")
                        this.selected = selected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(KST.weekdayLabel[i], style = pt(11, PTWeight.medium), color = weekdayColor)
                Box(
                    Modifier
                        .size(36.dp)
                        .then(
                            when {
                                selected -> Modifier.background(if (isToday) NC.calendarToday else NC.actionPrimary, CircleShape)
                                isToday -> Modifier.border(1.5.dp, brand, CircleShape)
                                else -> Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${KST.dayOfMonth(d)}",
                        style = pt(16, if (selected || isToday) PTWeight.bold else PTWeight.medium),
                        color = numberColor,
                    )
                }
            }
        }
    }
}

// MARK: - 떠 있는 버튼 (AI 배치 · 추가)

@Composable
private fun CalendarFloatingActions(
    board: TimetableBoardModel,
    mode: CalendarViewMode,
    onAdd: (TaskAddMode) -> Unit,
    onActivity: () -> Unit,
    onScheduleAi: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 스펙 11.1: AI 버튼 → AI 메뉴 (AI로 일정 조정 · AI로 TimeTable 자동 배치). 월간은 격자가 없어 자동 배치만 흐리게
        TimetableAIFab(
            days = board.rangeDays, today = KST.today(), loading = board.fillPreviewing,
            onScheduleAi = onScheduleAi,
            autoPlaceEnabled = mode != CalendarViewMode.month,
        ) { s, e ->
            val targets = TimetableRules.unplacedOf(board.rangeDays, today = KST.today())
            if (!board.isOffline) {
                CAL.track(
                    "calendar.auto_place_click",
                    mapOf(
                        "view_mode" to mode.rawValue, "unscheduled_count" to targets.sumOf { it.count },
                        "day_count" to targets.size,
                    ),
                )
            }
            scope.launch { board.previewFill(TimetableFillTarget.range(start = s, end = e)) }
        }
        // 스펙 11.2: + 메뉴 — 구분선 없이 3개
        // 못 한 일 자동 이월 켜기/끄기 (2026-10 · 설정 "일정"에도 있어요)
        LaunchedEffect(Unit) { if (!AutoCarrySetting.loaded) AutoCarrySetting.load() }
        val carryItem = MenuItem(
            title = if (AutoCarrySetting.enabled) "못 한 일 자동 이월: 켜짐" else "못 한 일 자동 이월: 꺼짐",
            icon = if (AutoCarrySetting.enabled) "checkmark.circle" else "circle",
            enabled = !AutoCarrySetting.saving,
        ) { scope.launch { AutoCarrySetting.set(!AutoCarrySetting.enabled, surface = "calendar") } }
        NMenu(items = calendarAddMenuItems(onAdd = onAdd, onActivity = onActivity) + carryItem, modifier = Modifier.semantics { contentDescription = "추가" }) {
            // 스펙 13.6: 공용 떠 있는 버튼 모양
            NFabFace(icon = "plus")
        }
    }
}

/** + 메뉴 항목 (떠 있는 + 버튼 · 빈 칸 탭 메뉴 · 홈 오늘 일정 "추가"가 같이 써요) */
internal fun calendarAddMenuItems(onAdd: (TaskAddMode) -> Unit, onActivity: (() -> Unit)?): List<MenuItem> = buildList {
    add(MenuItem(title = taskAddEntryTitle(TaskAddMode.ai), icon = "sparkles") { onAdd(TaskAddMode.ai) })
    add(MenuItem(title = taskAddEntryTitle(TaskAddMode.manual), icon = "square.and.pencil") { onAdd(TaskAddMode.manual) })
    if (onActivity != null) add(MenuItem(title = "실제 한 일 추가", icon = "checkmark.circle") { onActivity() })
}

/** 빈 칸을 눌렀을 때 뜨는 + 메뉴 (휴대폰은 아래 액션 시트) — 그 날짜 · 시각을 넘겨요. 홈 오늘 일정도 같이 써요 (2026-10) */
@Composable
internal fun CalendarEmptySlotMenu(
    date: String,
    minutes: Int,
    canLogActual: Boolean,
    onPick: (TaskAddMode?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 스펙 13.2: 공용 시트 틀 (제목 = 누른 날짜 · 시각)
    SheetScaffold(onDismiss = onDismiss, title = "${KST.formatMonthDay(date)} ${KST.formatMinutes(minutes)}") { close ->
        Column(Modifier.fillMaxWidth()) {
            val items = calendarAddMenuItems(
                onAdd = { m -> onPick(m) },
                onActivity = if (canLogActual) ({ onPick(null) }) else null,
            )
            for (item in items) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(Radius.lg))
                        .clickable(role = Role.Button) { item.action(); close() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SfImage(item.icon ?: "plus", size = 18.dp, tint = NC.textPrimary)
                    Text(item.title, style = NType.bodyLGMedium, color = NC.textPrimary)
                }
            }
        }
    }
}

// MARK: - 격자 부분 (일간 · 주간)

@Composable
private fun CalendarBoardSection(
    board: TimetableBoardModel,
    mode: CalendarViewMode,
    date: String,
    newIds: Set<Int>,
    onDayClick: (String) -> Unit,
    onEmptyTap: ((String, Int) -> Unit)? = null,
) {
    val days = board.days
    val today = KST.today()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(NC.surface)) {
        val error = board.loadError
        if (days.isEmpty() && board.isFetching) {
            LoadingView(minHeight = 420.dp)
        } else if (days.isEmpty() && error != null) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("TimeTable을 불러오지 못했어요", style = pt(16, PTWeight.bold), color = NC.ink)
                Text(error, style = pt(14), color = NC.ink3, textAlign = TextAlign.Center)
                NButton("다시 시도", variant = NButtonVariant.secondary, modifier = Modifier.padding(top = 8.dp)) {
                    CAL.track("calendar.retry_click", mapOf("view_mode" to mode.rawValue, "error_status" to board.loadErrorStatus))
                    scope.launch { board.load(withActivities = true) }
                }
            }
            LaunchedEffect(Unit) {
                if (!board.isOffline) {
                    CAL.track(
                        "calendar.load_fail",
                        mapOf("view_mode" to mode.rawValue, "view_date" to date, "error_status" to board.loadErrorStatus),
                    )
                }
            }
        } else if (days.isEmpty() && !board.loadedOnce) {
            LoadingView(minHeight = 420.dp)
        } else {
            val hours = TimetableLayout.visibleHours(days, activities = board.activities)
            val isDay = mode == CalendarViewMode.day
            TimetableGridView(
                board = board, days = days, activities = board.activities,
                startHour = hours.startHour, endHour = hours.endHour,
                planVsActual = isDay, highlightIds = newIds,
                onDayClick = if (isDay) null else onDayClick,
                emptyClickEnabled = true,
                onEmptyTap = onEmptyTap,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 떠 있는 버튼에 마지막 내용이 가리지 않게
                        .padding(start = 0.dp, end = 0.dp, top = 14.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 일간: TimeTable(계획 | 실제) 아래에 그날 할 일 목록
                    if (isDay) {
                        CalendarDayTaskList(board = board, day = days.firstOrNull { it.date == date }, today = today)
                    }
                    // 이 기간(하루 · 3일 · 주) 목표별 실천 시간 — 참고용 작은 막대 (2026-10)
                    val first = days.firstOrNull()?.date
                    val last = days.lastOrNull()?.date
                    if (first != null && last != null) {
                        val label = when (mode) {
                            CalendarViewMode.day -> if (first == today) "오늘" else "이날"
                            CalendarViewMode.threeDay -> "3일"
                            else -> if (first <= today && today <= last) "이번 주" else "이 주"
                        }
                        GoalTimeStats(start = first, end = last, viewMode = mode.rawValue, periodLabel = label)
                    }
                    TimetableLegend()
                    Text(
                        "블록을 눌러 자세히 보고, 길게 눌러 끌면 시간을 바꿔요. 지난 빈 시간을 누르면 실제로 한 일을 남길 수 있어요.",
                        style = pt(12),
                        color = NC.ink4,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CalendarScreenDayPreview() {
    NaleulTheme(dark = false) { CalendarScreen(previewMode = CalendarViewMode.day) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CalendarScreenWeekPreview() {
    NaleulTheme(dark = false) { CalendarScreen(previewMode = CalendarViewMode.week) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CalendarScreenMonthPreview() {
    NaleulTheme(dark = false) { CalendarScreen(previewMode = CalendarViewMode.month) }
}
