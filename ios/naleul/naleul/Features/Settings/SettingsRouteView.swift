// Features/Settings/SettingsRouteView.swift
//
// 웹 /settings/… 하위 주소 → iOS 화면. MainTabView 의 공통 경로(AppRoute.settings)에서 불러요.
//   /settings/life-pattern     → LifePatternScreen
//   /settings/notifications    → SettingsNotificationScreen
//   /settings/calendar-import  → CalendarImportScreen (TaskAdd 작업자가 제공)
//   /settings/membership       → MembershipScreen (애플 인앱 결제)
//   /settings/terms            → TermsListScreen
//   /settings/terms/[type]     → TermsDetailScreen
//   /settings/withdraw         → SettingsWithdrawScreen

import SwiftUI

struct SettingsRouteView: View {
    let route: SettingsRoute

    init(route: SettingsRoute) { self.route = route }

    var body: some View {
        switch route {
        case .lifePattern:
            LifePatternScreen()
        case .notifications:
            SettingsNotificationScreen()
        case .calendarImport:
            CalendarImportScreen()
        case .membership:
            MembershipScreen()
        case .terms:
            TermsListScreen()
        case .termsDetail(let type):
            TermsDetailScreen(type: type)
        case .withdraw:
            SettingsWithdrawScreen()
        case .widget:
            WidgetSettingsScreen()
        }
    }
}

#Preview {
    NavigationStack {
        SettingsRouteView(route: .termsDetail("privacy"))
    }
    .environment(UserStore.shared)
    .environment(AppRouter.shared)
    .environmentObject(PurchaseManager.shared)
}
