// feature/home/HomeScreen.kt
//
// iOS Features/Home/HomeScreen.swift
// 홈 탭 — 오늘 해야 할 것만 한곳에. 웹 원본: features/home/ui/HomeView.tsx
//  1. 오늘 요약 (얼마나 했는지 · 지금/다음 할 일 · 마감 · 오늘 회고 버튼)
//  2. 오늘 일정 (캘린더 일간과 같은 계획 | 실제 격자 — 블록을 누르면 캘린더와 같은 상세 시트)
//  3. 할 일 · 루틴과 생활 습관 (바로 체크)
//  4. 다가오는 일정 (6일) · 마감 로드맵
// 웹은 넓은 화면에서 칸을 나누지만 휴대폰은 한 줄로 카드를 쌓아요.
// 오늘 회고는 요약 카드의 "오늘 회고" → 시트 (HomeRetroSheet)

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 실패를 값으로 받아요 (취소는 그대로 던져요 — 가이드 §4) */
private suspend fun <T> homeAttempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

// MARK: - 데이터

class HomeViewModel : ScreenModel() {
    /** days[0] = 오늘, 나머지 = 앞으로 6일 */
    var days by mutableStateOf<List<TimetableDay>>(emptyList())
        private set
    var habitHome by mutableStateOf<HabitHome?>(null)
        private set
    /** /home/today — 아직 배포 전인 서버(404)면 null + homeFailed */
    var home by mutableStateOf<HomeToday?>(null)
        private set
    var homeFailed by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var isFetching by mutableStateOf(false)
        private set
    var togglingTaskId by mutableStateOf<Int?>(null)
        private set
    private var offline = false

    val today: TimetableDay? get() = days.firstOrNull()
    val upcoming: List<TimetableDay> get() = days.drop(1)

    /** 오늘의 루틴이 아닌 Task (시간순, 시간 미정은 뒤) */
    val tasks: List<TimeBlockTask>
        get() {
            val d = today ?: return emptyList()
            return HomeDay.tasksOfDay(d).filter { !HomeDay.isRoutine(it) }.sortedBy { HomeDay.startMinutes(d.date, it) }
        }

    /** 오늘의 루틴 Task */
    val routines: List<TimeBlockTask>
        get() {
            val d = today ?: return emptyList()
            return HomeDay.tasksOfDay(d).filter { HomeDay.isRoutine(it) }.sortedBy { HomeDay.startMinutes(d.date, it) }
        }

    /** 오늘 체크할 생활 습관 */
    val todayHabits: List<Habit>
        get() = (habitHome?.habits ?: emptyList()).filter { it.status == HabitStatus.ACTIVE && it.scheduled }

    /** 아직 회고를 안 쓴, 오늘 할 일이 있던 목표 수 */
    val retroLeft: Int
        get() = (home?.goals ?: emptyList()).count { it.activeToday && !it.hasRecord }

    /**
     * - today: 보고 있는 날 (날짜 ‹ › 로 고른 날)
     * - realToday: 진짜 오늘. 습관은 앞날 체크가 안 돼서 앞날이면 숨겨요
     */
    suspend fun load(today: String, realToday: String) {
        if (offline) return
        val day = today
        isFetching = true
        try {
            val isToday = day == realToday
            val isFuture = day > realToday
            if (isFuture) habitHome = null
            coroutineScope {
                val week = async { homeAttempt { TimetableAPI.timetable(day, KST.addDays(day, 6)) } }
                // 앞날은 습관 체크가 안 돼서 불러오지 않아요
                val habits = async {
                    homeAttempt { if (isFuture) null else HabitAPI.home(date = if (isToday) null else day) }
                }
                val homeToday = async { homeAttempt { HomeAPI.today(date = if (isToday) null else day) } }

                // 다시 불러오다 실패하면 이전 데이터를 그대로 보여줘요
                week.await()
                    .onSuccess { r ->
                        days = r.days
                        loadError = null
                        loadedOnce = true
                    }
                    .onFailure { e ->
                        loadError = errorMessage(e)
                        loadedOnce = true
                    }
                habits.await()
                    .onSuccess { h -> h?.let { habitHome = it } }
                    .onFailure { e ->
                        HomeTracker.system(
                            HomeEvent.sectionLoadFail,
                            "failed_section" to "habits", "error_status" to (e as? ApiError)?.httpStatus, "view_date" to day,
                        )
                    }
                homeToday.await()
                    .onSuccess { h ->
                        if (h != null) {
                            home = h
                            homeFailed = false
                        } else if (home == null) {
                            homeFailed = true
                        }
                    }
                    .onFailure { e ->
                        if (home == null) homeFailed = true
                        HomeTracker.system(
                            HomeEvent.sectionLoadFail,
                            "failed_section" to "deadlines", "error_status" to (e as? ApiError)?.httpStatus, "view_date" to day,
                        )
                    }
            }
            HomeTracker.lastLoadedAt = System.currentTimeMillis()
        } finally {
            isFetching = false
        }
    }

    // MARK: 체크

    /** Task 완료 ↔ 완료 취소 (끝나면 캘린더 · 목표 · 홈이 같이 다시 불러와져요) */
    suspend fun toggleTask(t: TimeBlockTask) {
        if (togglingTaskId != null || t.sourceType == TaskSourceType.MISSION) return
        togglingTaskId = t.taskId
        val current = t.taskStatus
        setStatus(t.taskId, if (current == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED)
        val ok = Mutation.run(invalidate = listOf(DataKey.timetable, DataKey.goals, DataKey.home, DataKey.record)) {
            TimetableAPI.toggleComplete(taskId = t.taskId, currentStatus = current)
        }
        if (ok == null) setStatus(t.taskId, current)
        HomeTracker.system(
            HomeEvent.checkResult,
            "item_type" to if (HomeDay.isRoutine(t)) "routine" else "task",
            "check_action" to HomeTracker.checkAction(current == TaskStatus.COMPLETED),
            "result" to if (ok == null) "fail" else "success",
        )
        togglingTaskId = null
    }

    /** 생활 습관 체크 (바로 바뀌고, 실패하면 되돌려요) */
    suspend fun toggleHabit(h: Habit, day: String) {
        val next = !h.checked
        setHabit(h.habitId, checked = next)
        val ok = Mutation.run(invalidate = listOf(DataKey.habits, DataKey.home)) {
            HabitAPI.setChecked(habitId = h.habitId, day = day, checked = next)
        }
        if (ok == null) setHabit(h.habitId, checked = !next)
        HomeTracker.system(
            HomeEvent.checkResult,
            "item_type" to "habit", "check_action" to if (next) "complete" else "uncomplete",
            "result" to if (ok == null) "fail" else "success",
        )
    }

    /** 화면의 Task 상태만 먼저 바꿔요 (Kotlin 객체는 참조라서 copy 로 새로 만들어요) */
    private fun setStatus(taskId: Int, status: TaskStatus) {
        days = days.map { d ->
            d.copy(
                tasks = d.tasks.map { if (it.taskId == taskId) it.copy(taskStatus = status) else it },
                unscheduledTasks = d.unscheduledTasks.map { if (it.taskId == taskId) it.copy(taskStatus = status) else it },
            )
        }
    }

    private fun setHabit(habitId: Int, checked: Boolean) {
        val hh = habitHome ?: return
        val target = hh.habits.firstOrNull { it.habitId == habitId } ?: return
        if (target.checked == checked) return
        habitHome = hh.copy(
            habits = hh.habits.map { if (it.habitId == habitId) it.copy(checked = checked) else it },
            doneCount = maxOf(0, hh.doneCount + (if (checked) 1 else -1)),
        )
    }

    companion object {
        /** 미리보기용 (네트워크 없이 샘플 데이터) */
        fun preview(homeFailed: Boolean = false, empty: Boolean = false): HomeViewModel {
            val m = HomeViewModel()
            m.offline = true
            val today = KST.today()
            m.days = if (empty) emptyList() else TimetableSampleData.days(today, KST.addDays(today, 6))
            m.habitHome = if (empty) null else HomeSampleData.habitHome(today)
            m.home = if (homeFailed) null else HomeSampleData.homeToday(today)
            m.homeFailed = homeFailed
            m.loadedOnce = true
            return m
        }
    }
}

// MARK: - 화면

/**
 * 홈 탭 첫 화면. previewVM 은 미리보기 전용 — 주면 서버를 부르지 않고 샘플을 보여줘요 (iOS init(previewVM:)).
 */
@Composable
fun HomeScreen(previewVM: HomeViewModel? = null) {
    val isPreview = previewVM != null
    val vm = rememberScreenModel { previewVM ?: HomeViewModel() }
    val board = rememberScreenModel {
        val d = KST.today()
        if (isPreview) TimetableBoardModel.sample(d, d) else TimetableBoardModel().also { it.analyticsSurface = "home" }
    }
    var today by remember { mutableStateOf(KST.today()) }
    /** 날짜 ‹ › 로 고른 날 (null = 오늘을 따라가요 — 자정이 지나면 저절로 다음 날) */
    var picked by remember { mutableStateOf<String?>(null) }
    val viewDate = picked ?: today
    val isToday = viewDate == today
    var nowMinutes by remember { mutableIntStateOf(KST.now().minutes) }
    var retroOpen by remember { mutableStateOf(false) }
    /** 회고 시트를 연 경로 — button · push(하루 마무리 알림) */
    var retroEntry by remember { mutableStateOf("button") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    suspend fun reload() {
        if (isPreview) return
        val d = viewDate
        val real = today
        board.setRange(d, d)
        coroutineScope {
            launch { vm.load(today = d, realToday = real) }
            launch { board.load(withActivities = true) }
        }
        trackHomePageview(vm, viewDate = d, isToday = d == real, nowMinutes = nowMinutes)
    }

    if (!isPreview) {
        TrackScreen("HomeScreen")
        // Android 13+ 알림 권한 (iOS 는 앱 시작 때 물어봐요)
        NotificationPermissionRequest()
    }

    ReloadOn(
        listOf(
            DataKey.timetable, DataKey.home, DataKey.habits, DataKey.goals,
            DataKey.record, DataKey.work, DataKey.activities,
        ),
        id = viewDate,
    ) { reload() }

    // 1분마다 "지금" 갱신 (지금 할 일 · 다음 일정까지 남은 시간). 날짜가 바뀌면 다시 불러와요
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000L)
            val n = KST.now()
            nowMinutes = n.minutes
            if (n.date != today) {
                HomeTracker.system(HomeEvent.dayRollover, "from_date" to today, "to_date" to n.date)
                today = n.date
            }
        }
    }

    // 하루 마무리 알림을 누르고 들어오면 → 오늘로 돌아와서 회고 시트
    LaunchedEffect(AppRouter.homeRetro) {
        if (AppRouter.homeRetro) {
            AppRouter.homeRetro = false
            picked = null
            retroEntry = "push"
            retroOpen = true
        }
    }

    // 화면을 떠날 때 "어디까지 바꿀까요?"가 떠 있으면 이날만으로 저장해요 (iOS onDisappear)
    DisposableEffect(board) {
        onDispose { board.saveAskOnLeave() }
    }

    // 홈 들어옴 · 떠남 (home.page_exit) — 다른 탭 · 화면으로 가면 이 화면이 사라지고, 앱이 뒤로 가면 ON_STOP
    val latestViewDate by rememberUpdatedState(viewDate)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isPreview) {
        if (isPreview) return@DisposableEffect onDispose {}
        HomeTracker.enter()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) HomeTracker.exit("background", latestViewDate)
            if (event == Lifecycle.Event.ON_START) HomeTracker.enter()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            HomeTracker.exit(if (AppRouter.tab == AppTab.home) "navigate" else "tab_switch", latestViewDate)
        }
    }

    /** 날짜 이동 — 오늘로 오면 다시 "오늘 따라가기" */
    fun go(d: String, event: String) {
        HomeTracker.track(
            event,
            "from_date" to viewDate, "to_date" to d,
            "date_relation" to if (event == HomeEvent.dateTodayClick) null else HomeTracker.relation(d),
        )
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        picked = if (d == today) null else d
    }

    val userName = UserStore.user?.userName?.trim().orEmpty().ifEmpty { "나를 사용자" }

    Column(
        Modifier
            .fillMaxSize()
            .canvasBackground()
            .statusBarsPadding(),
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                HomeTracker.track(
                    HomeEvent.pullRefresh,
                    "view_date" to viewDate,
                    "minutes_since_update" to HomeTracker.lastLoadedAt?.let { ((System.currentTimeMillis() - it) / 60_000).toInt() },
                )
                scope.launch {
                    refreshing = true
                    try { reload() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            val scrollState = rememberScrollState()
            var viewportHeight by remember { mutableIntStateOf(0) }
            // 섹션 칩 바 (나의 패턴과 같은 칩) — 카드 위치 · 지금 보는 카드
            val anchors = remember { HomeSectionAnchors() }
            val density = LocalDensity.current
            val navHeightPx = with(density) { HomeGeo.navHeight.dp.toPx() }
            val lineExtraPx = with(density) { 40.dp.toPx() }
            val jumpGapPx = with(density) { 12.dp.toPx() }
            var currentSection by remember { mutableStateOf(HomeSection.calendar) }
            /** 칩을 눌러 스크롤하는 동안에는 스크롤 위치로 칩을 바꾸지 않아요 (깜빡임 방지) */
            var jumping by remember { mutableStateOf(false) }
            LaunchedEffect(scrollState) {
                snapshotFlow { homeCurrentSection(anchors, scrollState.value, navHeightPx, lineExtraPx) }
                    .collect { if (!jumping) currentSection = it }
            }
            fun jump(sec: HomeSection) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HomeTracker.track(
                    HomeEvent.sectionNavClick,
                    "section" to sec.rawValue, "from_section" to currentSection.rawValue, "view_date" to viewDate,
                )
                val top = anchors.tops[sec] ?: return
                jumping = true
                currentSection = sec
                scope.launch {
                    try {
                        // 카드가 칩 바 바로 아래에 오도록
                        scrollState.animateScrollTo(maxOf(0, (top - navHeightPx - jumpGapPx).toInt()))
                    } finally {
                        jumping = false
                    }
                }
            }
            val showsSections = !vm.days.isEmpty()
            Box(Modifier.fillMaxSize().onGloballyPositioned { anchors.viewportTop = it.positionInRoot().y }) {
            // 가장 깊이 내린 스크롤 % (home.page_exit)
            LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value to scrollState.maxValue }
                    .collect { (v, m) -> HomeTracker.scrolled(v, m, viewportHeight) }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeight = it.height }
                    // 격자 블록을 끄는 동안에는 홈 스크롤을 잠가요
                    .verticalScroll(scrollState, enabled = board.drag == null)
                    .padding(start = PageGutter, end = PageGutter, top = 8.dp, bottom = 24.dp),
            ) {
                if (vm.days.isEmpty() && !vm.loadedOnce) {
                    LoadingView(minHeight = 420.dp)
                } else if (vm.days.isEmpty() && vm.loadError != null) {
                    LaunchedEffect(Unit) { HomeTracker.system(HomeEvent.loadFail, "view_date" to viewDate) }
                    Column(Modifier.fillMaxWidth().card(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isToday) "오늘 일정을 불러오지 못했어요" else "이날 일정을 불러오지 못했어요",
                            style = pt(16, PTWeight.bold),
                            color = NC.ink,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        ErrorView(message = vm.loadError ?: "", retry = {
                            HomeTracker.track(HomeEvent.retryClick, "view_date" to viewDate, "failed_section" to "all")
                            reload()
                        })
                    }
                } else {
                    HomeSectionsColumn(
                        anchors = anchors,
                        scroll = { scrollState.value },
                        currentSection = currentSection,
                        onJump = { jump(it) },
                        vm = vm,
                        board = board,
                        viewDate = viewDate,
                        today = today,
                        isToday = isToday,
                        nowMinutes = nowMinutes,
                        userName = userName,
                        onRetro = {
                            HomeTracker.track(
                                HomeEvent.retroButtonClick,
                                "view_date" to viewDate, "date_relation" to HomeTracker.relation(viewDate),
                                "retro_left_count" to vm.retroLeft,
                            )
                            retroEntry = "button"
                            retroOpen = true
                        },
                        onGo = { d, event -> go(d, event) },
                        onToggleTask = { t ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { vm.toggleTask(t) }
                        },
                        onToggleHabit = { h ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { vm.toggleHabit(h, day = viewDate) }
                        },
                    )
                }
            }
            // 칩 바 자리를 지나 내려가면 화면 맨 위에 붙여요 (iOS pinnedViews: .sectionHeaders)
            if (showsSections && anchors.navTop >= 0 && scrollState.value > anchors.navTop) {
                HomeSectionNav(current = currentSection, onTap = { jump(it) }, modifier = Modifier.align(Alignment.TopStart))
            }
            }
        }
        // iOS safeAreaInset(edge: .bottom) — 범위 고르기 · 이월 제안 같은 아래 바
        TimetableBottomBars(board = board, showFill = false)
    }

    // 블록을 누르면 뜨는 상세 시트 (iOS .timetableSheets(board))
    TimetableSheets(board = board)

    if (retroOpen) {
        HomeRetroSheet(vm = vm, today = viewDate, entryPoint = retroEntry, onDismiss = { retroOpen = false })
    }
}

@Composable
private fun HomeSectionsColumn(
    anchors: HomeSectionAnchors,
    scroll: () -> Int,
    currentSection: HomeSection,
    onJump: (HomeSection) -> Unit,
    vm: HomeViewModel,
    board: TimetableBoardModel,
    viewDate: String,
    today: String,
    isToday: Boolean,
    nowMinutes: Int,
    userName: String,
    onRetro: () -> Unit,
    onGo: (String, String) -> Unit,
    onToggleTask: (TimeBlockTask) -> Unit,
    onToggleHabit: (Habit) -> Unit,
) {
    val tasks = vm.tasks
    val routines = vm.routines
    val habits = vm.todayHabits
    val deadlines = vm.home?.deadlines ?: emptyList()
    val dayLabel = if (isToday) "오늘" else "이날"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // iOS 는 아래 여백을 -6 줄였어요 (Compose 는 음수 여백이 없어서 그대로 둬요)
        // 알림 종은 맨 위 공통 헤더(MainTabScreen)로 옮겼어요 — 이벤트도 거기서 그대로 보내요
        if (UserStore.isGuest) GuestBanner()

        HomeSummaryCard(
            today = viewDate, nowMinutes = nowMinutes, userName = userName,
            day = vm.today, tasks = tasks, routines = routines,
            habitDone = habits.count { it.checked }, habitTotal = habits.size,
            dueToday = deadlines.count { it.dDay == 0 },
            overdue = deadlines.count { it.overdue },
            retroLeft = vm.retroLeft,
            showRetro = vm.home != null && !vm.homeFailed,
            onRetro = onRetro,
            nav = HomeDayNav(
                isToday = isToday, isPast = viewDate < today,
                onPrev = { onGo(KST.addDays(viewDate, -1), HomeEvent.datePrevClick) },
                onNext = { onGo(KST.addDays(viewDate, 1), HomeEvent.dateNextClick) },
                onToday = { onGo(today, HomeEvent.dateTodayClick) },
            ),
        )

        // 섹션 칩 바 (스크롤해서 지나가면 화면 위에 같은 칩 바가 붙어요) — 화면 가장자리까지 꽉 차게
        HomeSectionNav(
            current = currentSection,
            onTap = onJump,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val gutter = PageGutter.roundToPx()
                    val p = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + gutter * 2, minWidth = constraints.maxWidth + gutter * 2))
                    layout(constraints.maxWidth, p.height) { p.place(-gutter, 0) }
                }
                .homeAnchor(anchors, scroll, null),
        )

        Box(Modifier.homeAnchor(anchors, scroll, HomeSection.calendar)) {
        HomeDayCalendarCard(
            board = board, today = viewDate,
            onOpenCalendar = {
                HomeTracker.track(HomeEvent.calendarLinkClick, "view_date" to viewDate)
                AppRouter.openCalendar(view = CalendarViewMode.day, date = viewDate)
            },
            dayLabel = dayLabel,
        )
        }

        Box(Modifier.homeAnchor(anchors, scroll, HomeSection.tasks)) {
        HomeTasksCard(
            today = viewDate, tasks = tasks, togglingId = vm.togglingTaskId,
            onToggle = { t ->
                HomeTracker.track(
                    HomeEvent.taskCheckClick,
                    "view_date" to viewDate, "date_relation" to HomeTracker.relation(viewDate),
                    "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                    "check_action" to HomeTracker.checkAction(t.isCompleted),
                    "list_section" to if (t.isCompleted) "done" else "todo",
                    "is_scheduled" to (t.plannedStartAt != null), "due_state" to HomeTracker.dueState(t, today),
                    "is_missed" to t.missed, "goal_category_id" to t.goalCategoryId,
                )
                onToggleTask(t)
            },
            onSelect = { t ->
                HomeTracker.track(
                    HomeEvent.taskRowClick,
                    "view_date" to viewDate, "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                    "list_section" to if (t.isCompleted) "done" else "todo",
                )
                board.selectTask(t, trackGrid = false)
            },
            onAdd = {
                HomeTracker.track(HomeEvent.taskAddClick, "entry_point" to "tasks_card", "view_date" to viewDate, "task_count" to tasks.size)
                AppRouter.openTaskAdd(mode = TaskAddMode.manual)
            },
            dayLabel = dayLabel,
        )
        }

        Box(Modifier.homeAnchor(anchors, scroll, HomeSection.routines)) {
        HomeRoutinesCard(
            today = viewDate, routines = routines, habits = habits, habitHome = vm.habitHome,
            togglingId = vm.togglingTaskId,
            onToggleTask = { t ->
                HomeTracker.track(
                    HomeEvent.routineCheckClick,
                    "view_date" to viewDate, "date_relation" to HomeTracker.relation(viewDate),
                    "task_id" to t.taskId, "check_action" to HomeTracker.checkAction(t.isCompleted),
                    "goal_category_id" to t.goalCategoryId,
                )
                onToggleTask(t)
            },
            onSelectTask = { t ->
                HomeTracker.track(
                    HomeEvent.taskRowClick,
                    "view_date" to viewDate, "task_id" to t.taskId, "source_type" to t.sourceType.rawValue,
                    "list_section" to "routine",
                )
                board.selectTask(t, trackGrid = false)
            },
            onToggleHabit = { h ->
                HomeTracker.track(
                    HomeEvent.habitCheckClick,
                    "view_date" to viewDate, "date_relation" to HomeTracker.relation(viewDate),
                    "habit_id" to h.habitId, "check_action" to HomeTracker.checkAction(h.checked), "streak" to h.streak,
                )
                onToggleHabit(h)
            },
            onOpenHabits = {
                val id = vm.habitHome?.goalCategoryId
                if (id != null) AppRouter.openGoal(id) else AppRouter.openGoalList()
            },
            dayLabel = dayLabel,
        )
        }

        Box(Modifier.homeAnchor(anchors, scroll, HomeSection.upcoming)) {
        HomeUpcomingCard(
            days = vm.upcoming, today = viewDate,
            onOpenDay = { d -> AppRouter.openCalendar(view = CalendarViewMode.day, date = d) },
        )
        }

        Box(Modifier.homeAnchor(anchors, scroll, HomeSection.roadmap)) {
        HomeRoadmapCard(
            deadlines = vm.home?.deadlines, failed = vm.homeFailed, today = viewDate,
            onOpenGoal = { id -> AppRouter.openGoal(id) },
            onOpenDay = { d -> AppRouter.openCalendar(view = CalendarViewMode.day, date = d) },
        )
        }

        // Free · 둘러보기만 (Pro 는 광고 없음)
        AdBannerSlot(role = UserStore.user?.userRole)
    }
}

// MARK: - 오늘 일정 (캘린더 일간 격자)

/** 홈 전체가 스크롤되니 격자는 고정 높이 안에서 따로 스크롤해요 */
private val homeGridHeight = 440.dp

/**
 * 웹 HomeDayCalendar — 캘린더 화면의 일간 보기(계획 | 실제)를 그대로 넣었어요.
 * 블록을 눌러 상세 · 완료, 길게 눌러 끌어 시간 바꾸기까지 똑같이 돼요.
 * 추가도 캘린더 일간과 같아요 (2026-10): 머리글 "추가" 메뉴 · 빈 칸을 누르면 AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가.
 * Task 추가 시트는 캘린더 탭으로 옮기지 않고 홈 위에 띄워요.
 */
@Composable
fun HomeDayCalendarCard(
    board: TimetableBoardModel,
    today: String,
    onOpenCalendar: () -> Unit,
    /** 홈에서 다른 날을 보고 있으면 "이날" */
    dayLabel: String = "오늘",
) {
    val scope = rememberCoroutineScope()
    /** 빈 칸을 눌러 띄운 + 메뉴 (그 날짜 · 시각) */
    var emptyMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    /** 홈에서 연 Task 추가 시트 */
    var taskAdd by remember { mutableStateOf<TaskAddRequest?>(null) }
    /** 방금 추가한 Task — NEW 표시 */
    var newIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun homeTrack(event: String, vararg props: Pair<String, Any?>) {
        if (board.analyticsSurface == "home") HomeTracker.track(event, *props)
    }

    fun openTaskAdd(mode: TaskAddMode, source: String, date: String? = null, minutes: Int? = null) {
        val d = date ?: today
        homeTrack(
            HomeEvent.calendarTaskAddClick,
            "view_date" to d, "date_relation" to HomeTracker.relation(d), "add_mode" to mode.rawValue, "entry_source" to source,
        )
        taskAdd = TaskAddRequest(
            mode = mode, standalone = true,
            date = if (mode == TaskAddMode.manual) date else null,
            startMinutes = if (mode == TaskAddMode.manual) minutes else null,
        )
    }

    /** Task 추가 시트에서 추가가 끝났을 때 — 캘린더와 같이 NEW 표시, AI로 정리한 Task 는 점선 미리보기부터 */
    fun handleAdded(info: TaskAddedInfo) {
        newIds = newIds + info.taskIds
        val pending = info.pendingTaskIds
        if (pending.isNullOrEmpty()) return
        // 시트가 오늘 일정을 가리면 점선 제안이 안 보여서 닫아요 (task_add.panel_close 의 close_method = next)
        AnalyticsService.sheetCloseHint = "next"
        taskAdd = null
        scope.launch {
            board.previewFill(
                TimetableFillTarget.tasks(pending),
                kind = TimetableProposalKind.added,
                onPreview = { r ->
                    TA.track("task_add_result.proposal_ready", mapOf(
                        "surface" to "home", "result" to "success",
                        "placed_count" to r.proposals.size, "unscheduled_count" to r.unscheduled.size,
                    ))
                },
                onResolve = { applied, shown ->
                    TA.track("task_add_result.proposal_resolve", mapOf(
                        "surface" to "home", "proposal_applied" to applied,
                        "placed_count" to if (applied) shown.size else 0,
                    ))
                },
            )
        }
    }

    /** "한 일" — 오늘 최근 1시간으로 열어요 */
    fun openActivityNow() {
        homeTrack(
            HomeEvent.calendarAddActivityClick,
            "view_date" to today, "date_relation" to HomeTracker.relation(today), "entry_source" to "header_button",
        )
        val endMin = (KST.now().minutes / 10) * 10
        board.openActivity(
            TimetableActivityDraft(activity = null, date = today, start = maxOf(endMin - 60, 0), end = endMin, replaced = null)
        )
    }

    Column(Modifier.fillMaxWidth().card()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("$dayLabel 일정", style = NType.h4, color = NC.ink)
            if (board.isFetching && board.loadedOnce) {
                CircularProgressIndicator(color = NC.ink4, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.weight(1f))
            // 캘린더 + 버튼과 같은 메뉴 — AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가 (2026-10)
            NMenu(
                items = calendarAddMenuItems(
                    onAdd = { m -> openTaskAdd(m, source = "header_button") },
                    onActivity = { openActivityNow() },
                ),
                modifier = Modifier.semantics { contentDescription = "추가" },
            ) {
                Row(
                    Modifier.height(32.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SfImage("plus", size = 13.dp, tint = NC.ink3)
                    Text("추가", style = pt(13, PTWeight.semibold), color = NC.ink3)
                }
            }
            HomeCardTextButton(icon = "calendar", title = "캘린더", label = "캘린더") { onOpenCalendar() }
        }

        Box(Modifier.fillMaxWidth().height(homeGridHeight)) {
            val days = board.days
            if (days.isEmpty() && (board.isFetching || !board.loadedOnce)) {
                LoadingView(minHeight = homeGridHeight)
            } else if (days.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text("TimeTable을 불러오지 못했어요", style = pt(15, PTWeight.bold), color = NC.ink)
                    val e = board.loadError
                    if (e != null) Text(e, style = pt(13), color = NC.ink3, textAlign = TextAlign.Center)
                    NButton(
                        "다시 시도", variant = NButtonVariant.secondary, size = NButtonSize.sm,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (board.analyticsSurface == "home") {
                            HomeTracker.track(HomeEvent.retryClick, "view_date" to today, "failed_section" to "timetable")
                        }
                        scope.launch { board.load(withActivities = true) }
                    }
                }
                LaunchedEffect(Unit) {
                    if (board.analyticsSurface == "home") {
                        HomeTracker.system(HomeEvent.sectionLoadFail, "failed_section" to "timetable", "view_date" to today)
                    }
                }
            } else {
                val hours = TimetableLayout.visibleHours(days, activities = board.activities)
                TimetableGridView(
                    board = board,
                    days = days,
                    activities = board.activities,
                    startHour = hours.startHour,
                    endHour = hours.endHour,
                    planVsActual = true,
                    highlightIds = newIds,
                    emptyClickEnabled = true,
                    // 빈 칸을 누르면 캘린더 일간과 같은 + 메뉴 (앞으로의 시간도 눌러요)
                    onEmptyTap = { d, m ->
                        homeTrack(
                            HomeEvent.calendarEmptySlotClick,
                            "view_date" to d, "date_relation" to HomeTracker.relation(d), "slot_minutes" to m,
                        )
                        emptyMenu = d to m
                    },
                    footer = {},
                )
            }
        }
    }

    emptyMenu?.let { (d, m) ->
        val now = KST.now()
        val past = d < now.date || (d == now.date && m < now.minutes)
        CalendarEmptySlotMenu(
            date = d, minutes = m, canLogActual = past,
            onPick = { picked ->
                if (picked == null) {
                    homeTrack(
                        HomeEvent.calendarAddActivityClick,
                        "view_date" to d, "date_relation" to HomeTracker.relation(d), "entry_source" to "empty_tap",
                    )
                    board.openActivity(TimetableActivityDraft(activity = null, date = d, start = m, end = m + 60, replaced = null))
                } else {
                    openTaskAdd(picked, source = "empty_tap", date = d, minutes = m)
                }
            },
            onDismiss = { emptyMenu = null },
        )
    }

    taskAdd?.let { req ->
        key(req.id) {
            TaskAddSheet(
                initialMode = req.mode,
                standalone = req.standalone,
                prefillDate = req.date,
                prefillStartMinutes = req.startMinutes,
                onAdded = { info -> handleAdded(info) },
                onDismiss = { if (taskAdd?.id == req.id) taskAdd = null },
            )
        }
    }
}

@Composable
private fun HomeCardTextButton(icon: String, title: String, label: String, action: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = action)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SfImage(icon, size = 13.dp, tint = NC.ink3)
        Text(title, style = pt(13, PTWeight.semibold), color = NC.ink3)
    }
}

// MARK: - 맨 위 (알림함)

/** 오른쪽 위 종 — 읽지 않은 알림 수를 빨간 점으로 */
@Composable
fun HomeTopBar(unread: Int, modifier: Modifier = Modifier, onBell: () -> Unit) {
    Row(modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 40.dp, height = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onBell)
                .semantics { contentDescription = if (unread > 0) "알림함, 읽지 않은 알림 ${unread}개" else "알림함" },
            contentAlignment = Alignment.Center,
        ) {
            SfImage(if (unread > 0) "bell.badge" else "bell", size = 20.dp, tint = NC.ink2)
            if (unread > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-1).dp)
                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                        .background(NC.actionDanger, CircleShape)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (unread > 99) "99+" else "$unread", style = pt(10, PTWeight.bold), color = Color.White)
                }
            }
        }
    }
}

// MARK: - 미리보기

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun HomeScreenPreview() {
    NaleulTheme(dark = false) { HomeScreen(previewVM = HomeViewModel.preview()) }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun HomeScreenNoDeadlinePreview() {
    // 홈 — 마감 API 없음
    NaleulTheme(dark = false) { HomeScreen(previewVM = HomeViewModel.preview(homeFailed = true)) }
}

@Preview(showBackground = true, heightDp = 560)
@Composable
private fun HomeDayCalendarCardPreview() {
    NaleulTheme(dark = false) {
        val board = remember { TimetableBoardModel.sample(KST.today(), KST.today()) }
        Box(Modifier.canvasBackground().padding(16.dp)) {
            HomeDayCalendarCard(board = board, today = KST.today(), onOpenCalendar = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeTopBarPreview() {
    NaleulTheme(dark = false) {
        Column(Modifier.canvasBackground().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTopBar(unread = 3) {}
            HomeTopBar(unread = 0) {}
        }
    }
}

/** 홈을 다 불러온 직후 (들어올 때마다 한 번) — home.pageview */
private fun trackHomePageview(vm: HomeViewModel, viewDate: String, isToday: Boolean, nowMinutes: Int) {
    if (vm.days.isEmpty()) return
    val tasks = vm.tasks
    val routines = vm.routines
    val habits = vm.todayHabits
    val deadlines = vm.home?.deadlines ?: emptyList()
    val done = tasks.count { it.isCompleted } + routines.count { it.isCompleted } + habits.count { it.checked }
    val total = tasks.size + routines.size + habits.size
    // 요약 카드 '지금 할 일 / 다음 일정' 상태 — current · next · none · not_today
    val focusState = run {
        val d = vm.today
        if (!isToday || d == null) return@run "not_today"
        val open = HomeDay.tasksOfDay(d).filter { !it.isCompleted && it.plannedStartAt?.take(10) == d.date }
        when {
            open.any { HomeDay.startMinutes(d.date, it) <= nowMinutes && nowMinutes < HomeDay.endMinutes(d.date, it) } -> "current"
            open.any { HomeDay.startMinutes(d.date, it) > nowMinutes } -> "next"
            else -> "none"
        }
    }
    HomeTracker.pageviewOnce(
        "view_date" to viewDate,
        "date_relation" to HomeTracker.relation(viewDate),
        "total_count" to total,
        "done_count" to done,
        "progress_percent" to if (total > 0) Math.round(done * 100.0 / total).toInt() else 0,
        "task_count" to tasks.size,
        "routine_count" to routines.size,
        "habit_count" to habits.size,
        "retro_left_count" to vm.retroLeft,
        "due_today_count" to deadlines.count { it.dDay == 0 },
        "overdue_count" to deadlines.count { it.overdue },
        "focus_state" to focusState,
        "unread_count" to NudgeCenter.unreadCount,
    )
}
