// app/AppRouter.kt
//
// iOS App/AppRouter.swift — 화면 이동을 한 곳에서 관리해요. 웹의 Link href / router.push 를 대신해요.
// 기능(Feature)끼리는 서로의 화면을 직접 띄우지 않고 AppRouter 를 통해서만 이동해요.
//
//   AppRouter.openHome()                                   // 웹 / (홈)
//   AppRouter.openGoal(12)                                 // 웹 /goal/12
//   AppRouter.openGoal(12, created = true)                 // 웹 /goal/12?created=1
//   AppRouter.openGoalAdd(mode = GoalAddMode.manual)       // 웹 /goal/add?mode=manual
//   AppRouter.openCalendar(view = CalendarViewMode.week, date = d)
//   AppRouter.openTaskAdd(mode = TaskAddMode.ai)           // 캘린더 위 Task 추가 시트
//   AppRouter.startGoalCreation()                          // 전체 화면 AI 목표 만들기
//   AppRouter.resumeGoalCreation(sessionId = 3)
//   AppRouter.openSettings(SettingsRoute.LifePattern)      // 웹 /settings/life-pattern
//   AppRouter.openNotifications()                          // 알림함
//   AppRouter.push(AppRoute.Settings(SettingsRoute.Membership))   // iOS NavigationLink(value: AppRoute.settings(.membership))
//   AppRouter.push(AppRoute.Screen("goal-notes-12") { GoalNotesScreen(12) })  // 기능 안에서만 쓰는 화면을 쌓을 때
//   AppRouter.pop()                                        // 뒤로 (iOS dismiss())
//
// 왜 Navigation Compose 라이브러리를 안 쓰나요?
//  iOS 는 탭마다 NavigationStack(path:) 가 있어서 "탭별로 화면이 쌓이는" 구조예요.
//  여기서도 탭마다 화면 목록(backstack)을 들고 맨 위 화면만 그리는 아주 단순한 방식이 iOS 코드와 1:1 로 맞아서 옮기기 쉬워요.

package com.naleul.naleul.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

enum class AppTab(val title: String, val icon: String) {
    home("홈", "house"),
    goals("목표", "target"),
    calendar("캘린더", "calendar"),
    /** 회고 모아보기 (예전엔 쌓이는 화면만 있었어요) */
    reflections("회고", "book.closed"),
    patterns("패턴", "chart.bar.xaxis"),
    /** 아래 탭 바에는 없어요 — 맨 위 헤더의 톱니바퀴로 열어요 */
    settings("설정", "gearshape");

    companion object {
        /** 아래 탭 바에 보이는 순서 */
        val bar: List<AppTab> = listOf(home, goals, calendar, reflections, patterns)
    }
}

/** 캘린더 보기 방식 (웹 ?view=day|week|month) */
enum class CalendarViewMode(val label: String) {
    day("일"), threeDay("3일"), week("주"), month("월");

    /** 앰플리튜드 view_mode 값 — 3일은 three_day */
    val rawValue: String get() = if (this == threeDay) "three_day" else name
}

/** 캘린더 탭에 "이 날짜/보기로 가 줘" 요청 (CalendarScreen 이 받아서 처리하고 null 로 비워요) */
data class CalendarRequest(
    val view: CalendarViewMode? = null,
    val date: String? = null,
    /** 불러온 뒤 이 Task 상세 시트를 열어요 (루틴 실천 알림 "시간을 바꿔 볼까요?") */
    val taskId: Int? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** Task 추가 방식 (웹 TaskAddView initialMode) */
enum class TaskAddMode {
    /** AI로 정리하기 (Brain dump) */
    ai,
    /** 직접 추가하기 */
    manual,
    /** 이미 한 일 기록 */
    log;

    val rawValue: String get() = name
}

/** 캘린더 위에 여는 Task 추가 시트 요청 */
data class TaskAddRequest(
    val mode: TaskAddMode = TaskAddMode.ai,
    /** 빈 칸을 눌러 열었을 때 그 날짜 · 시각 (계획 할 일 추가 폼에 미리 채워요) */
    val date: String? = null,
    val startMinutes: Int? = null,
    /** true 면 방식 탭 없이 그 방식만 (캘린더 + 메뉴 · 스펙 11.3) */
    val standalone: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
)

/** 목표 추가 화면 모드 (웹 /goal/add?mode=) */
enum class GoalAddMode {
    /** 생활형 목표 직접 입력 폼 */
    manual,
    /** 업무형(기록형) 목표 폼 */
    record;

    val rawValue: String get() = name
}

/** AI 목표 만들기 시작 방법 */
data class GoalCreationStart(
    /** 임시 목표를 AI 목표로 구체화할 때 (웹 ?sourceGoalId=) */
    val sourceGoalId: Int? = null,
    /** 진행 중인 대화 세션 이어가기 */
    val resumeSessionId: Int? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** 캘린더 "AI로 일정 조정" 시트를 여는 요청 (iOS ScheduleAiSheet.swift 의 ScheduleAiRequest — 라우터가 써서 여기 둬요) */
data class ScheduleAiRequest(
    val prompt: String? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** 설정 탭 안의 화면 (웹 /settings/…) */
sealed class SettingsRoute {
    object LifePattern : SettingsRoute()
    object Notifications : SettingsRoute()
    object CalendarImport : SettingsRoute()
    object Membership : SettingsRoute()
    object Terms : SettingsRoute()
    /** 약관 상세 (웹 /settings/terms/[type]) — "service" | "privacy" | "marketing" … */
    data class TermsDetail(val type: String) : SettingsRoute()
    object Withdraw : SettingsRoute()
}

/** 탭 위에 쌓이는 화면 */
sealed class AppRoute {
    /** 목표 상세 (웹 /goal/12?created=1) */
    data class GoalDetail(val goalId: Int, val created: Boolean = false) : AppRoute()
    /** 목표 추가 (웹 /goal/add?mode=) */
    data class GoalAdd(val mode: GoalAddMode? = null) : AppRoute()
    data class Settings(val route: SettingsRoute) : AppRoute()
    /** 회고 모아보기 (웹 /reflections?goal=) — null 이면 모든 목표 */
    data class Reflections(val goalId: Int? = null) : AppRoute()
    /** 알림함 — 실천 도우미 알림 + 받은 푸시 */
    object Notifications : AppRoute()
    /**
     * 기능 안에서만 쓰는 화면 (iOS NavigationLink { 목적지 } / .navigationDestination(isPresented:))
     * key 는 같은 화면이 두 번 쌓이지 않게 구분하는 이름이에요.
     */
    class Screen(val key: String, val content: @Composable () -> Unit) : AppRoute()
}

object AppRouter {
    var tab: AppTab by mutableStateOf(AppTab.home)

    /** 탭마다 쌓인 화면 (iOS homePath, calendarPath …) */
    val homePath: SnapshotStateList<AppRoute> = mutableStateListOf()
    val calendarPath: SnapshotStateList<AppRoute> = mutableStateListOf()
    val goalsPath: SnapshotStateList<AppRoute> = mutableStateListOf()
    val patternsPath: SnapshotStateList<AppRoute> = mutableStateListOf()
    val reflectionsPath: SnapshotStateList<AppRoute> = mutableStateListOf()
    val settingsPath: SnapshotStateList<AppRoute> = mutableStateListOf()

    /** CalendarScreen 이 읽고 처리한 뒤 null 로 비워요 */
    var calendarRequest: CalendarRequest? by mutableStateOf(null)
    /** CalendarScreen 이 시트로 띄워요 (캘린더를 보면서 추가) */
    var taskAdd: TaskAddRequest? by mutableStateOf(null)
    /** MainTabScreen 이 전체 화면으로 띄워요 */
    var goalCreation: GoalCreationStart? by mutableStateOf(null)
    /** CalendarScreen 이 "AI로 일정 조정" 시트로 띄워요 */
    var scheduleAi: ScheduleAiRequest? by mutableStateOf(null)
    /** HomeScreen 이 "오늘 회고" 시트를 열고 false 로 돌려요 (하루 마무리 알림을 눌렀을 때) */
    var homeRetro: Boolean by mutableStateOf(false)

    fun path(tab: AppTab): SnapshotStateList<AppRoute> = when (tab) {
        AppTab.home -> homePath
        AppTab.calendar -> calendarPath
        AppTab.goals -> goalsPath
        AppTab.patterns -> patternsPath
        AppTab.reflections -> reflectionsPath
        AppTab.settings -> settingsPath
    }

    /** 지금 탭에 화면을 쌓아요 */
    fun push(route: AppRoute) {
        path(tab).add(route)
    }

    /** 지금 탭의 맨 위 화면을 닫아요 (뒤로). 닫을 게 없으면 false */
    fun pop(): Boolean {
        val p = path(tab)
        if (p.isEmpty()) return false
        p.removeAt(p.lastIndex)
        return true
    }

    /** 지금 탭을 처음 화면으로 */
    fun popToRoot() {
        path(tab).clear()
    }

    val canPop: Boolean get() = path(tab).isNotEmpty()

    // MARK: 홈

    fun openHome(retro: Boolean = false) {
        goalCreation = null
        tab = AppTab.home
        homePath.clear()
        if (retro) homeRetro = true
    }

    /** 알림함 (홈 위 종 모양) */
    fun openNotifications() {
        goalCreation = null
        tab = AppTab.home
        homePath.clear()
        homePath.add(AppRoute.Notifications)
    }

    // MARK: 나의 패턴

    fun openPatterns() {
        goalCreation = null
        tab = AppTab.patterns
        patternsPath.clear()
    }

    // MARK: 목표

    fun openGoalList() {
        tab = AppTab.goals
        goalsPath.clear()
    }

    fun openGoal(goalId: Int, created: Boolean = false) {
        goalCreation = null
        if (tab != AppTab.goals) {
            tab = AppTab.goals
            goalsPath.clear()
        }
        goalsPath.add(AppRoute.GoalDetail(goalId, created))
    }

    /** 목표 추가 화면에서 목표를 만든 뒤처럼 "지금 화면을 상세로 바꾸기" (웹 router.replace) */
    fun replaceWithGoal(goalId: Int, created: Boolean = false) {
        goalCreation = null
        tab = AppTab.goals
        goalsPath.clear()
        goalsPath.add(AppRoute.GoalDetail(goalId, created))
    }

    fun openGoalAdd(mode: GoalAddMode? = null) {
        tab = AppTab.goals
        goalsPath.add(AppRoute.GoalAdd(mode))
    }

    // MARK: 회고 모아보기

    /** 지금 탭에서 회고 모아보기를 열어요 (홈 회고 · 목표 상세 · 업무형 보드에서) */
    fun openReflections(goalId: Int? = null) {
        path(tab).add(AppRoute.Reflections(goalId))
    }

    // MARK: 캘린더 · Task 추가

    fun openCalendar(view: CalendarViewMode? = null, date: String? = null, taskId: Int? = null) {
        goalCreation = null
        tab = AppTab.calendar
        calendarPath.clear()
        if (view != null || date != null || taskId != null) calendarRequest = CalendarRequest(view = view, date = date, taskId = taskId)
    }

    /** 캘린더 "AI로 일정 조정" (루틴 폼에서 겹치는 루틴을 정리할 때 문장을 채워서) */
    fun openCalendarAi(prompt: String? = null) {
        goalCreation = null
        tab = AppTab.calendar
        calendarPath.clear()
        scheduleAi = ScheduleAiRequest(prompt = prompt)
    }

    fun openTaskAdd(mode: TaskAddMode = TaskAddMode.ai, date: String? = null, startMinutes: Int? = null, standalone: Boolean = false) {
        tab = AppTab.calendar
        calendarPath.clear()
        taskAdd = TaskAddRequest(mode = mode, date = date, startMinutes = startMinutes, standalone = standalone)
    }

    // MARK: AI 목표 만들기 (전체 화면)

    fun startGoalCreation(sourceGoalId: Int? = null) {
        goalCreation = GoalCreationStart(sourceGoalId = sourceGoalId)
    }

    fun resumeGoalCreation(sessionId: Int) {
        goalCreation = GoalCreationStart(resumeSessionId = sessionId)
    }

    fun closeGoalCreation() {
        goalCreation = null
    }

    // MARK: 설정

    fun openSettings(route: SettingsRoute? = null) {
        tab = AppTab.settings
        settingsPath.clear()
        if (route != null) settingsPath.add(AppRoute.Settings(route))
    }

    /** 로그아웃 시 처음 상태로 */
    fun reset() {
        tab = AppTab.home
        homePath.clear()
        calendarPath.clear()
        goalsPath.clear()
        patternsPath.clear()
        reflectionsPath.clear()
        settingsPath.clear()
        calendarRequest = null
        taskAdd = null
        goalCreation = null
        scheduleAi = null
        homeRetro = false
    }
}
