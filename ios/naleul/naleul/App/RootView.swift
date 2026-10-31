// App/RootView.swift
//
// 앱 첫 화면: 저장된 토큰으로 로그인 상태를 복원하고, 로그인 화면 또는 메인 탭을 보여줘요.

import SwiftUI
import UIKit

struct RootView: View {
    @Environment(UserStore.self) private var userStore
    @State private var loginPrompt = LoginPrompt.shared
    @AppStorage(ThemePref.storageKey) private var themeRaw = ThemePref.system.rawValue
    @State private var isRestoring = true
    @Environment(\.scenePhase) private var scenePhase

    private var theme: ThemePref { ThemePref(rawValue: themeRaw) ?? .system }

    var body: some View {
        Group {
            if isRestoring {
                ProgressView().tint(Color.ink3)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .canvasBackground()
            } else if userStore.isLoggedIn {
                MainTabView()
                    // 캘린더가 바뀌면(완료 · 추가 · 시간 변경) 홈 화면 위젯도 새로 채워요
                    .reloadOn([.timetable]) { await WidgetSync.refresh() }
                    // 루틴 · Task · 목표를 지우거나 바꾸면 실천 도우미 예약도 바로 다시 계산해요
                    // (예전엔 앱을 다시 켤 때까지 지운 루틴 이름으로 예약된 알림이 그대로 울렸어요)
                    .reloadOn([.timetable, .goals, .habits]) {
                        await NudgeCenter.shared.refresh(force: true, trigger: "data_change")
                    }
            } else {
                LoginView()
            }
        }
        .preferredColorScheme(theme.colorScheme)
        // 둘러보기 중 저장하려고 하면 → 로그인 시트 (로그인하면 저절로 닫혀요)
        .sheet(isPresented: $loginPrompt.isPresented, onDismiss: trackLoginSheetClose) {
            LoginView(asSheet: true)
        }
        .toastHost()
        .task { await restoreSession() }
        .onChange(of: userStore.isLoggedIn) { _, loggedIn in
            if loggedIn {
                Task { await NudgeCenter.shared.refresh(force: true, trigger: "login") }
            } else {
                AppRouter.shared.reset()
                // 로그아웃한 기기에 앞 사람의 실천 도우미 알림이 울리지 않게
                NudgeCenter.shared.reset()
                // 위젯에 앞 사람 일정이 남지 않게
                WidgetSync.clear()
            }
        }
        // 앱이 앞으로 올 때마다: 받은 알림을 알림함에 모으고, 앞으로 사흘치 실천 도우미 알림을 다시 예약해요
        // ("다시 시작" 알림도 이때 뒤로 밀려서, 정말 며칠 안 들어왔을 때만 울려요)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, userStore.isLoggedIn {
                Task { await NudgeCenter.shared.refresh() }
                Task { await WidgetSync.refresh(force: true) }
            }
            // app.open / app.background — 앱을 새로 켰을 땐 사용자 복원 전이라 저장된 토큰으로 판단해요
            if phase == .active { Analytics.appDidBecomeActive(isLoggedIn: TokenManager.shared.accessToken != nil) }
            if phase == .background { Analytics.appDidEnterBackground() }
        }
    }

    /// 로그인 시트가 닫혔을 때 — 로그인 성공으로 저절로 닫힌 건 빼요
    private func trackLoginSheetClose() {
        guard userStore.isGuest else { return }
        let opened = loginPrompt.shownAt.map { Int(Date().timeIntervalSince($0)) }
        Analytics.track(AnalyticsEvent.loginModalClose, [
            "close_method": loginPrompt.closeMethod ?? "swipe_down",
            "modal_reason": loginPrompt.reason ?? "other",
            "open_duration_seconds": opened,
        ])
    }

    private func restoreSession() async {
        // 앱을 새로 켠 순간 — scenePhase 가 처음부터 active 라 onChange 가 안 올 수 있어서 여기서도 불러요 (중복은 한 번으로)
        Analytics.appDidBecomeActive(isLoggedIn: TokenManager.shared.accessToken != nil)
        defer {
            isRestoring = false
            if userStore.isLoggedIn { Task { await NudgeCenter.shared.refresh(force: true) } }
        }
        guard let userId = TokenManager.shared.userId, TokenManager.shared.accessToken != nil else {
            Analytics.track(AnalyticsEvent.sessionRestoreResult, ["result": "no_token"])
            return
        }
        // 둘러보기: 서버에 물어볼 것 없이 바로 (토큰이 만료됐으면 API 클라이언트가 새로 받아요)
        if TokenManager.shared.isGuest {
            userStore.setUser(AuthService.guestUser(id: userId))
            Analytics.setUserProperties(role: .guest)
            Analytics.track(AnalyticsEvent.sessionRestoreResult, ["result": "guest"])
            return
        }
        do {
            let info = try await UserAPI.fetchUser(userId: userId)
            UserRole.saveAdminViewPlan(info.adminViewPlan)
            userStore.setUser(AppUser(
                userId: info.id,
                userName: info.nickname,
                userEmail: info.email ?? "",
                userRole: UserRole(rawValue: info.userRole) ?? .free,
                adminViewPlan: info.adminViewPlan
            ))
            Analytics.identify(userId: info.id, role: UserRole(rawValue: info.userRole) ?? .free)
            Analytics.track(AnalyticsEvent.sessionRestoreResult, ["result": "success"])
            // 앱을 켤 때 FCM 토큰이 로그인 확인보다 먼저 와서 등록이 건너뛰어졌을 수 있어요 → 세션 복원 뒤 다시 보내요
            (UIApplication.shared.delegate as? AppDelegate)?.resendCurrentTokenIfNeeded()
        } catch {
            #if DEBUG
            print("❌ 세션 복원 실패:", error)
            #endif
            // 다시 로그인해야 하는 경우(재발급 실패 → 토큰 삭제)가 아니면, 서버가 잠깐 안 되는 것뿐이에요.
            // 예전엔 이때 로그인 화면으로 보내서 "로그인이 풀렸다"고 느껴졌어요 → 마지막으로 본 정보로 들어가요
            if TokenManager.shared.accessToken != nil {
                Analytics.track(AnalyticsEvent.sessionRestoreResult, [
                    "result": "cached",
                    "fail_reason": { () -> String in if case .http(let st, _, _, _)? = error as? APIError { return "http_\(st)" }; return "network" }(),
                ])
                let cached = TokenManager.shared.cachedUser
                userStore.setUser(AppUser(userId: userId, userName: cached?.name ?? "나를 사용자",
                                          userEmail: cached?.email ?? "",
                                          userRole: UserRole(rawValue: cached?.role ?? "") ?? .free))
            } else {
                // 재발급까지 거절돼 토큰이 지워짐 → 로그인 화면 (session.expired 도 같이 나가요)
                Analytics.track(AnalyticsEvent.sessionRestoreResult, ["result": "expired"])
            }
        }
    }
}

#Preview {
    RootView()
        .environment(UserStore.shared)
        .environment(AppRouter.shared)
}
