// App/AppRouter.swift
//
// 화면 이동을 한 곳에서 관리해요. 웹의 Link href / router.push 를 대신해요.
// 기능(Feature)끼리는 서로의 화면을 직접 만들지 않고 router 를 통해서만 이동해요.
//
//   @Environment(AppRouter.self) private var router
//   router.openHome()                           // 웹 / (홈)
//   router.openGoal(12)                         // 웹 /goal/12
//   router.openGoal(12, created: true)          // 웹 /goal/12?created=1
//   router.openGoalAdd(mode: .manual)           // 웹 /goal/add?mode=manual
//   router.openCalendar(view: .week, date: d)   // 웹 /calendar?view=week&date=…
//   router.openTaskAdd(mode: .ai)               // 웹 /calendar?add=ai (캘린더 위 Task 추가 시트)
//   router.startGoalCreation()                  // 웹 /goal/new (전체 화면 AI 목표 만들기)
//   router.startGoalCreation(sourceGoalId: 7)   // 웹 /goal/new?sourceGoalId=7
//   router.resumeGoalCreation(sessionId: 3)     // 이어서 하기 (세션 상태에 맞는 단계로)
//   router.openSettings(.lifePattern)           // 웹 /settings/life-pattern
//   router.openNotifications()                  // 알림함 (iOS 전용)

import SwiftUI
import Observation

enum AppTab: String, Hashable, CaseIterable {
    case home, goals, calendar, reflections, patterns

    /// 아래 탭바에 보이는 탭 (순서대로). 설정은 탭이 아니라 지금 탭 위에 쌓는 화면이에요 (AppRoute.settingsHome)
    static let barTabs: [AppTab] = [.home, .goals, .calendar, .reflections, .patterns]

    var title: String {
        switch self {
        case .home: return "홈"
        case .calendar: return "캘린더"
        case .goals: return "목표"
        case .reflections: return "회고"
        case .patterns: return "패턴"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house"
        case .calendar: return "calendar"
        case .goals: return "target"
        case .reflections: return "book"
        case .patterns: return "chart.bar.xaxis"
        }
    }

    /// 아래 탭바 아이콘 (Figma 아이콘 에셋 · template)
    var iconAsset: NIcon {
        switch self {
        case .home: return .home
        case .calendar: return .calendar
        case .goals: return .target
        case .reflections: return .notebook
        case .patterns: return .chartBar
        }
    }
}

/// 캘린더 보기 방식 (웹 ?view=day|week|month)
enum CalendarViewMode: String, Hashable, CaseIterable {
    /// threeDay = 3일 보기 (모바일 전용 · 2026-10) — 고른 날부터 3일
    case day, threeDay = "three_day", week, month

    var label: String {
        switch self {
        case .day: return "일"
        case .threeDay: return "3일"
        case .week: return "주"
        case .month: return "월"
        }
    }
}

/// 캘린더 탭에 "이 날짜/보기로 가 줘" 요청 (CalendarScreen 이 받아서 처리하고 nil 로 비워요)
struct CalendarRequest: Equatable {
    var view: CalendarViewMode?
    var date: String?
    /// 불러온 뒤 이 Task 상세 시트를 열어요 (루틴 실천 알림 "시간을 바꿔 볼까요?")
    var taskId: Int? = nil
    let id = UUID()
}

/// Task 추가 방식 (웹 TaskAddView initialMode)
enum TaskAddMode: String, Hashable, CaseIterable {
    /// AI로 정리하기 (Brain dump)
    case ai
    /// 직접 추가하기
    case manual
    /// 이미 한 일 기록
    case log
}

/// 캘린더 위에 여는 Task 추가 시트 요청
struct TaskAddRequest: Identifiable, Equatable {
    let id = UUID()
    var mode: TaskAddMode = .ai
    /// true = 그 방식만 보여주는 시트 (방식 고르기 탭 없음 · 캘린더 + 메뉴에서 · DESIGN_SPEC 11.3)
    var single = false
    /// 빈 칸을 눌러 열었을 때 그 날짜 · 시각 (DESIGN_SPEC 11.4)
    var prefill: TaskAddPrefill? = nil
}

/// Task 추가에 미리 채울 날짜 · 시작 시각 (그날 0시 기준 분 — 24시 넘으면 다음 날)
struct TaskAddPrefill: Equatable {
    var date: String
    var startMinutes: Int
}

/// 목표 추가 화면 모드 (웹 /goal/add?mode=)
enum GoalAddMode: String, Hashable {
    /// 생활형 목표 직접 입력 폼
    case manual
    /// 업무형(기록형) 목표 폼
    case record
}

/// AI 목표 만들기 시작 방법
struct GoalCreationStart: Identifiable, Equatable {
    let id = UUID()
    /// 임시 목표를 AI 목표로 구체화할 때 (웹 ?sourceGoalId=)
    var sourceGoalId: Int? = nil
    /// 진행 중인 대화 세션 이어가기
    var resumeSessionId: Int? = nil
}

/// 설정 탭 안의 화면 (웹 /settings/…)
enum SettingsRoute: Hashable {
    case lifePattern
    case notifications
    case calendarImport
    case membership
    case terms
    /// 약관 상세 (웹 /settings/terms/[type]) — "service" | "privacy" | "marketing" …
    case termsDetail(String)
    case withdraw
    /// 홈 화면 위젯 스타일
    case widget
}

/// 탭 사이를 오가는 공통 경로 (각 탭의 NavigationStack 에 등록돼 있어요)
enum AppRoute: Hashable {
    /// 목표 상세 (웹 /goal/12?created=1)
    case goalDetail(goalId: Int, created: Bool)
    /// 목표 추가 (웹 /goal/add?mode=)
    case goalAdd(GoalAddMode?)
    case settings(SettingsRoute)
    /// 설정 첫 화면 (위 헤더 톱니 버튼 · 지금 탭 위에 쌓여요)
    case settingsHome
    /// 회고 모아보기 (웹 /reflections?goal=) — nil 이면 모든 목표
    case reflections(goalId: Int?)
    /// 알림함 — 실천 도우미 알림 + 받은 푸시
    case notifications
}

@MainActor
@Observable
final class AppRouter {
    static let shared = AppRouter()
    private init() {}

    var tab: AppTab = .home
    var homePath = NavigationPath()
    var calendarPath = NavigationPath()
    var goalsPath = NavigationPath()
    var patternsPath = NavigationPath()
    var reflectionsPath = NavigationPath()

    /// CalendarScreen 이 읽고 처리한 뒤 nil 로 비워요
    var calendarRequest: CalendarRequest?
    /// CalendarScreen 이 시트로 띄워요 (캘린더를 보면서 추가)
    var taskAdd: TaskAddRequest?
    /// MainTabView 가 전체 화면으로 띄워요
    var goalCreation: GoalCreationStart?
    /// CalendarScreen 이 "AI로 일정 조정" 시트로 띄워요
    var scheduleAi: ScheduleAiRequest?
    /// HomeScreen 이 "오늘 회고" 시트를 열고 false 로 돌려요 (하루 마무리 알림을 눌렀을 때)
    var homeRetro = false

    // MARK: 홈

    func openHome(retro: Bool = false) {
        goalCreation = nil
        tab = .home
        homePath = NavigationPath()
        if retro { homeRetro = true }
    }

    /// 알림함 (홈 위 종 모양)
    func openNotifications() {
        goalCreation = nil
        tab = .home
        homePath = NavigationPath()
        homePath.append(AppRoute.notifications)
    }

    // MARK: 나의 패턴

    func openPatterns() {
        goalCreation = nil
        tab = .patterns
        patternsPath = NavigationPath()
    }

    // MARK: 목표

    func openGoalList() {
        tab = .goals
        goalsPath = NavigationPath()
    }

    func openGoal(_ goalId: Int, created: Bool = false) {
        goalCreation = nil
        if tab != .goals {
            tab = .goals
            goalsPath = NavigationPath()
        }
        goalsPath.append(AppRoute.goalDetail(goalId: goalId, created: created))
    }

    /// 목표 추가 화면에서 목표를 만든 뒤처럼 "지금 화면을 상세로 바꾸기" (웹 router.replace)
    func replaceWithGoal(_ goalId: Int, created: Bool = false) {
        goalCreation = nil
        tab = .goals
        var path = NavigationPath()
        path.append(AppRoute.goalDetail(goalId: goalId, created: created))
        goalsPath = path
    }

    func openGoalAdd(mode: GoalAddMode? = nil) {
        tab = .goals
        goalsPath.append(AppRoute.goalAdd(mode))
    }

    // MARK: 회고 모아보기

    /// 지금 탭에서 회고 모아보기를 열어요 (홈 회고 · 목표 상세 · 업무형 보드에서)
    func openReflections(goalId: Int? = nil) {
        let route = AppRoute.reflections(goalId: goalId)
        switch tab {
        case .home: homePath.append(route)
        case .calendar: calendarPath.append(route)
        case .goals: goalsPath.append(route)
        case .patterns: patternsPath.append(route)
        case .reflections: reflectionsPath.append(route)
        }
    }

    // MARK: 캘린더 · Task 추가

    func openCalendar(view: CalendarViewMode? = nil, date: String? = nil, taskId: Int? = nil) {
        goalCreation = nil
        tab = .calendar
        calendarPath = NavigationPath()
        if view != nil || date != nil || taskId != nil { calendarRequest = CalendarRequest(view: view, date: date, taskId: taskId) }
    }

    /// 캘린더 "AI로 일정 조정" (루틴 폼에서 겹치는 루틴을 정리할 때 문장을 채워서)
    func openCalendarAi(_ prompt: String? = nil) {
        goalCreation = nil
        tab = .calendar
        calendarPath = NavigationPath()
        scheduleAi = ScheduleAiRequest(prompt: prompt)
    }

    func openTaskAdd(mode: TaskAddMode = .ai, single: Bool = false, prefill: TaskAddPrefill? = nil) {
        tab = .calendar
        calendarPath = NavigationPath()
        taskAdd = TaskAddRequest(mode: mode, single: single, prefill: prefill)
    }

    // MARK: AI 목표 만들기 (전체 화면)

    func startGoalCreation(sourceGoalId: Int? = nil) {
        goalCreation = GoalCreationStart(sourceGoalId: sourceGoalId)
    }

    func resumeGoalCreation(sessionId: Int) {
        goalCreation = GoalCreationStart(resumeSessionId: sessionId)
    }

    func closeGoalCreation() {
        goalCreation = nil
    }

    // MARK: 설정

    /// 설정 — 지금 탭 위에 설정 첫 화면(+ 하위 화면)을 쌓아요. 설정은 아래 탭이 아니에요
    func openSettings(_ route: SettingsRoute? = nil) {
        goalCreation = nil
        push(AppRoute.settingsHome)
        if let route { push(AppRoute.settings(route)) }
    }

    /// 지금 탭의 NavigationStack 에 화면 하나 쌓기
    private func push(_ route: AppRoute) {
        switch tab {
        case .home: homePath.append(route)
        case .calendar: calendarPath.append(route)
        case .goals: goalsPath.append(route)
        case .patterns: patternsPath.append(route)
        case .reflections: reflectionsPath.append(route)
        }
    }

    /// 로그아웃 시 처음 상태로
    func reset() {
        tab = .home
        homePath = NavigationPath()
        calendarPath = NavigationPath()
        goalsPath = NavigationPath()
        patternsPath = NavigationPath()
        reflectionsPath = NavigationPath()
        calendarRequest = nil
        taskAdd = nil
        goalCreation = nil
        scheduleAi = nil
        homeRetro = false
    }
}
