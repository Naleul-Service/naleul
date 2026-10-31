// App/MainTabView.swift
//
// 로그인 후 화면의 공통 틀 (웹 AppShell + Sidebar).
// 아래 탭: 홈 · 목표 · 캘린더 · 회고 · 패턴 (설정은 위 헤더의 톱니 버튼). 위 공통 헤더 + 직접 그린 탭바 (Figma).
// 홈(오늘 요약 · 오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵)이 첫 탭이에요.
//
// 각 탭은 자기 NavigationStack 을 갖고, 탭 사이를 오가는 공통 경로(AppRoute)는 여기서 등록해요.

import SwiftUI
import UIKit

@MainActor
struct MainTabView: View {
    @Environment(AppRouter.self) private var router
    /// 키보드가 떠 있으면 아래 탭바를 숨겨요 (시스템 탭바처럼)
    @State private var keyboardVisible = false

    var body: some View {
        @Bindable var router = router

        VStack(spacing: 0) {
            // 위 공통 헤더 (워드마크 · 알림 · 설정) — 각 탭의 첫 화면에서만. 상세 화면은 자기 내비게이션 바를 써요
            if isAtTabRoot {
                AppMobileHeader(unread: NudgeCenter.shared.unreadCount, onBell: openInbox, onSettings: {
                    router.openSettings()
                })
            }

            // 시스템 탭바는 숨기고(.toolbar(.hidden, for: .tabBar)) 아래 AppTabBar 를 그려요
            TabView(selection: $router.tab) {
                NavigationStack(path: $router.homePath) {
                    HomeScreen()
                        .hidesSystemTabBar()
                        .appRouteDestinations()
                }
                .tag(AppTab.home)

                NavigationStack(path: $router.goalsPath) {
                    GoalListScreen()
                        .hidesSystemTabBar()
                        .appRouteDestinations()
                }
                .tag(AppTab.goals)

                NavigationStack(path: $router.calendarPath) {
                    CalendarScreen()
                        .hidesSystemTabBar()
                        .appRouteDestinations()
                }
                .tag(AppTab.calendar)

                NavigationStack(path: $router.reflectionsPath) {
                    ReflectionsScreen(isTabRoot: true)
                        .hidesSystemTabBar()
                        .appRouteDestinations()
                }
                .tag(AppTab.reflections)

                NavigationStack(path: $router.patternsPath) {
                    PatternScreen()
                        .hidesSystemTabBar()
                        .appRouteDestinations()
                }
                .tag(AppTab.patterns)
            }
            .tint(Color.textBrand)

            if !keyboardVisible {
                AppTabBar(selected: router.tab, onSelect: select)
            }
        }
        .background(Color.bgCanvas.ignoresSafeArea())
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            keyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardVisible = false
        }
        // AI 목표 만들기는 사이드바 없는 전체 화면 플로우 (웹 (flow) 레이아웃)
        .fullScreenCover(item: $router.goalCreation) { start in
            GoalCreationFlowView(start: start)
        }
        // 기한이 지나 자동 판정된 목표 — "목표는 만족했나요?" (웹 AppShell 의 GoalOutcomePromptModal)
        .goalOutcomePrompts()
    }

    /// 지금 탭이 첫 화면인지 (쌓인 화면이 없으면 위 공통 헤더를 보여줘요)
    private var isAtTabRoot: Bool { navDepth(router.tab) == 0 }

    /// 아래 탭을 눌렀을 때 (예전 시스템 탭바와 같은 이벤트 · 같은 탭 이름)
    private func select(_ new: AppTab) {
        let from = router.tab
        Analytics.track(AnalyticsEvent.tabBarClick, [
            "tab": new.rawValue, "from_tab": from.rawValue, "is_reselect": new == from,
        ])
        if new == from {
            Analytics.track(AnalyticsEvent.tabBarReselect, ["tab": new.rawValue, "nav_depth": navDepth(new)])
            // 시스템 탭바처럼 같은 탭을 다시 누르면 첫 화면으로
            popToRoot(new)
        }
        router.tab = new
    }

    private func popToRoot(_ tab: AppTab) {
        switch tab {
        case .home: router.homePath = NavigationPath()
        case .calendar: router.calendarPath = NavigationPath()
        case .goals: router.goalsPath = NavigationPath()
        case .reflections: router.reflectionsPath = NavigationPath()
        case .patterns: router.patternsPath = NavigationPath()
        }
    }

    /// 헤더 종 — 예전 홈 위 종과 같은 이벤트 · 같은 동작
    private func openInbox() {
        HomeTracker.shared.track(HomeEvent.notificationBellClick, ["unread_count": NudgeCenter.shared.unreadCount])
        InboxTracker.shared.pendingEntry = "home_bell"
        router.openNotifications()
    }

    /// 그 탭에 쌓인 화면 수 (0 = 탭 첫 화면)
    private func navDepth(_ tab: AppTab) -> Int {
        switch tab {
        case .home: return router.homePath.count
        case .calendar: return router.calendarPath.count
        case .goals: return router.goalsPath.count
        case .reflections: return router.reflectionsPath.count
        case .patterns: return router.patternsPath.count
        }
    }
}

// MARK: - 위 공통 헤더 (Figma MobileHeader · h48 · 좌우 20)

private struct AppMobileHeader: View {
    let unread: Int
    let onBell: () -> Void
    let onSettings: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Wordmark(height: 24)
            Spacer(minLength: 0)
            // HeaderActions: 알림(읽지 않은 알림이 있으면 빨간 점) · 설정
            Button(action: onBell) {
                (unread > 0 ? NIcon.bellDot : NIcon.bell).image(size: 20)
                    .foregroundStyle(Color.textSecondary)
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(IconButtonStyle())
            .accessibilityLabel(unread > 0 ? "알림함, 읽지 않은 알림 \(unread)개" : "알림함")

            Button(action: onSettings) {
                NIcon.settings.image(size: 20)
                    .foregroundStyle(Color.textSecondary)
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(IconButtonStyle())
            .accessibilityLabel("설정")
        }
        .padding(.horizontal, PageLayout.horizontalPadding)
        .frame(height: PageLayout.headerHeight)
        .background(Color.bgSurface.ignoresSafeArea(edges: .top))
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.borderSoft).frame(height: 1)
        }
    }
}

// MARK: - 아래 탭바 (Figma TabBar)
// bg/surface · 위 1px border/subtle · padding 8/16 · 아이콘 상자 32×32 r8 (선택: action/ghost/bg/hover) · 아이콘 20 · 라벨 Label/SM

private struct AppTabBar: View {
    let selected: AppTab
    let onSelect: (AppTab) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.barTabs, id: \.self) { tab in
                let on = tab == selected
                Button { onSelect(tab) } label: {
                    VStack(spacing: 2) {
                        tab.iconAsset.image(size: 20)
                            .frame(width: 32, height: 32)
                            .background(on ? Color.ghostHover : Color.clear,
                                        in: RoundedRectangle(cornerRadius: Radius.nav, style: .continuous))
                        Text(tab.title)
                            .font(.labelSM)
                            .tracking(-0.24)
                            .lineLimit(1)
                    }
                    .foregroundStyle(on ? Color.textBrand : Color.textTertiary)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.title)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(Color.bgSurface.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.borderSubtle).frame(height: 1)
        }
    }
}

// MARK: - 공통 경로 → 화면

private struct AppRouteDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content.navigationDestination(for: AppRoute.self) { route in
            destination(route)
                .hidesSystemTabBar()
        }
    }

    @ViewBuilder
    private func destination(_ route: AppRoute) -> some View {
        switch route {
        case let .goalDetail(goalId, created):
            GoalDetailScreen(goalId: goalId, justCreated: created)
        case let .goalAdd(mode):
            GoalAddScreen(initialMode: mode)
        case let .settings(r):
            SettingsRouteView(route: r)
        case let .reflections(goalId):
            ReflectionsScreen(initialGoalId: goalId)
        case .notifications:
            NudgeInboxScreen()
        case .settingsHome:
            SettingsScreen()
        }
    }
}

extension View {
    /// 탭의 NavigationStack 루트 화면에 붙여요
    func appRouteDestinations() -> some View { modifier(AppRouteDestinations()) }

    /// 시스템 탭바 숨기기 (아래 AppTabBar 를 대신 그려요) — 탭 첫 화면 · 쌓이는 화면 모두에 붙여요
    func hidesSystemTabBar() -> some View { toolbar(.hidden, for: .tabBar) }
}
