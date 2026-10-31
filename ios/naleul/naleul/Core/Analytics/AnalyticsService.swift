// Core/Analytics/AnalyticsService.swift
//
// Amplitude 이벤트를 한 곳에서 관리해요. (웹 src/lib/client/analytics.ts 와 같은 역할 · 같은 Amplitude 프로젝트)
// 화면에서는 Amplitude SDK 를 직접 import 하지 않고 이 파일의 Analytics 만 써요.
//
// 규칙은 Notion "Tracking Plan" 을 따라요.
//  - 이벤트 이름은 'page.action' 그대로 (예: login_modal.kakao_click) — 웹과 같은 동작이면 웹과 같은 이름
//  - 이메일 · 닉네임 · 자유 입력 원문은 보내지 않아요
//  - 로그아웃 직후 reset() → 다음 사람의 둘러보기 이벤트가 이전 사용자에게 붙지 않아요
//
// Config.amplitudeApiKey 가 비어 있으면 아무것도 보내지 않아요 (웹 NEXT_PUBLIC_AMPLITUDE_API_KEY 와 같은 키를 넣어요).
//
// 운영 데이터 지키기 (2026-10, 웹과 같은 규칙)
//  - 운영 서버(Config.baseURL 이 https://api.naleul.com)에 붙은 앱만 운영 Amplitude 로 보내요.
//    테스트 서버(dev-api) · 로컬 서버에 붙은 빌드는 아무것도 보내지 않아요.
//  - 운영자(ADMIN)로 로그인한 기기는 운영 집계에서 빼요 (opt-out). 운영자가 아닌 사람이 로그인하면 풀려요.
//
//   Analytics.track(AnalyticsEvent.loginKakaoClick, ["login_surface": "screen"])
//   .trackScreen("CalendarScreen")   // 지금 보는 화면 이름만 기억해요 (app.background · toast.view 에 붙여요)

import Foundation
import SwiftUI
import AmplitudeSwift

/// Tracking Plan 의 event 컬럼 값 (모바일 · 로그인 / 공통)
enum AnalyticsEvent {
    // 로그인
    static let loginPageview = "login.pageview"
    static let loginKakaoClick = "login.kakao_click"
    static let loginAppleClick = "login.apple_click"
    static let loginComplete = "login.complete"
    static let loginFail = "login.fail"
    static let loginCancel = "login.cancel"
    static let loginGuestClick = "login.guest_click"
    static let guestSessionStart = "guest.session_start"
    static let guestStartFail = "guest.start_fail"
    static let loginModalView = "login_modal.view"
    static let loginModalKakaoClick = "login_modal.kakao_click"
    static let loginModalAppleClick = "login_modal.apple_click"
    static let loginModalClose = "login_modal.close"
    static let guestBannerLoginClick = "guest_banner.login_click"
    static let settingsLogoutClick = "settings.logout_click"
    static let settingsLogoutConfirmClick = "settings.logout_confirm_click"
    static let settingsLogoutCancelClick = "settings.logout_cancel_click"
    static let sessionRestoreResult = "session.restore_result"
    static let sessionExpired = "session.expired"

    // 공통
    static let appOpen = "app.open"
    static let appBackground = "app.background"
    static let tabBarClick = "tab_bar.click"
    static let tabBarReselect = "tab_bar.reselect"
    static let pushPermissionPromptView = "push_permission.prompt_view"
    static let pushPermissionResult = "push_permission.result"
    static let pushReceive = "push.receive"
    static let pushOpen = "push.open"
    static let pushOpenBlocked = "push.open_blocked"
    static let toastView = "toast.view"
    static let toastDismissClick = "toast.dismiss_click"
    static let apiError = "api.error"
    static let adView = "ad.view"
    static let adClick = "ad.click"
}

/// event property 값. Tracking Plan 의 varchar → String, int → Int, boolean → Bool (nil 이면 안 보내요)
typealias EventProps = [String: Any?]

@MainActor
enum Analytics {
    /// 운영 집계로 보내는 서버 — 이 주소에 붙은 앱만 이벤트를 보내요
    static let productionBaseURL = "https://api.naleul.com"
    private static let operatorKey = "naleul.analytics.operator"

    private static let amplitude: Amplitude? = {
        let key = Config.amplitudeApiKey
        guard !key.isEmpty else {
            #if DEBUG
            print("⚠️ Amplitude API key missing — analytics disabled")
            #endif
            return nil
        }
        guard Config.baseURL.hasPrefix(productionBaseURL) else {
            #if DEBUG
            print("ℹ️ Amplitude: 운영 서버가 아니라서(\(Config.baseURL)) 운영 집계로 보내지 않아요")
            #endif
            return nil
        }
        #if DEBUG
        let level = LogLevelEnum.DEBUG
        #else
        let level = LogLevelEnum.WARN
        #endif
        let instance = Amplitude(configuration: Configuration(
            apiKey: key,
            logLevel: level,
            // 수동 이벤트와 겹치지 않게 세션만 자동으로 (앱 열기 · 화면 조회는 직접 보내요)
            autocapture: [.sessions]
        ))
        // 운영자 기기는 앱을 다시 켜도 계속 빠져요
        instance.configuration.optOut = UserDefaults.standard.bool(forKey: operatorKey)
        return instance
    }()

    /// 이 기기를 운영자 기기로 표시하거나 풀어요 (운영자면 이벤트를 안 보내요)
    static func markOperatorDevice(_ isOperator: Bool) {
        UserDefaults.standard.set(isOperator, forKey: operatorKey)
        amplitude?.configuration.optOut = isOperator
    }

    /// SDK 를 미리 만들어 둬요 (앱 시작 때 한 번)
    static func start() { _ = amplitude }

    static func track(_ event: String, _ props: EventProps = [:]) {
        var clean: [String: Any] = [:]
        for (k, v) in props { if let v { clean[k] = v } }
        amplitude?.track(eventType: event, eventProperties: clean.isEmpty ? nil : clean)
    }

    /// 로그인 사용자 연결 + User Property. 둘러보기 중 이벤트는 같은 deviceId 라서 이 순간 이 사용자에게 이어 붙어요.
    static func identify(userId: Int, role: UserRole, provider: String? = nil) {
        if role != .guest { markOperatorDevice(role == .admin) }
        guard let amplitude else { return }
        let id = String(userId)
        if amplitude.getUserId() != id { amplitude.setUserId(userId: id) }
        setUserProperties(role: role, provider: provider)
    }

    /// user_role(GUEST/FREE/PRO/ADMIN) · login_provider
    static func setUserProperties(role: UserRole? = nil, provider: String? = nil) {
        guard let amplitude else { return }
        let identify = Identify()
        var changed = false
        if let role { identify.set(property: "user_role", value: role.rawValue); changed = true }
        if let provider { identify.set(property: "login_provider", value: provider); changed = true }
        if changed { amplitude.identify(identify: identify) }
    }

    /// 로그아웃 직후: userId 를 지우고 deviceId 를 새로 만들어요
    static func reset() { amplitude?.reset() }

    /// 앱이 뒤로 갈 때 남은 이벤트를 바로 보내요
    static func flush() { amplitude?.flush() }

    // MARK: - 화면 · 앱 상태 (app.open / app.background · toast.view 의 current_path)

    /// .trackScreen 이 넣어 주는 지금 화면 이름
    static private(set) var currentScreen: String?
    static func setScreen(_ name: String) { currentScreen = name }

    private static var foregroundAt: Date?
    private static var didOpenOnce = false
    /// 앱이 앞으로 오기 직전에 정해지는 여는 경로 (push · nudge · kakao_return)
    static var pendingOpenSource: String?

    static func appDidBecomeActive(isLoggedIn: Bool) {
        guard foregroundAt == nil else { return }   // 이미 앞에 있는데 active 가 또 와도 한 번만
        let launch = didOpenOnce ? "warm" : "cold"
        didOpenOnce = true
        foregroundAt = Date()
        track(AnalyticsEvent.appOpen, [
            "launch_type": launch,
            "open_source": pendingOpenSource ?? "icon",
            "is_logged_in": isLoggedIn,
        ])
        pendingOpenSource = nil
    }

    static func appDidEnterBackground() {
        guard let since = foregroundAt else { return }
        track(AnalyticsEvent.appBackground, [
            "last_screen": currentScreen,
            "foreground_seconds": Int(Date().timeIntervalSince(since)),
        ])
        foregroundAt = nil
        flush()
    }

    /// 알림을 눌러 앱이 열릴 때 — 아직 app.open 전이면 cold
    static var launchTypeNow: String { didOpenOnce ? "warm" : "cold" }

    // MARK: - 로그인 흐름 상태

    /// 소셜 버튼을 누른 시각 → login.complete / fail / cancel 의 seconds_since_click
    private static var loginClickAt: Date?
    static func markLoginClick() { loginClickAt = Date() }
    static func secondsSinceLoginClick() -> Int? { loginClickAt.map { Int(Date().timeIntervalSince($0)) } }

    /// 이번 앱 실행에서 로그인 시트가 뜬 횟수 → login_modal.view 의 prompt_count_session
    private static var loginPromptCount = 0

    /// 시트 머리의 X 로 닫았을 때 잠깐 남겨요 (쓸어 닫기와 구분 · 시트 닫힘 이벤트가 한 번 꺼내 써요)
    static var sheetCloseHint: String?
    static func nextLoginPromptCount() -> Int { loginPromptCount += 1; return loginPromptCount }
}

// MARK: - 화면 이름 기억

private struct ScreenTrackingModifier: ViewModifier {
    let screenName: String
    func body(content: Content) -> some View {
        content.onAppear { Analytics.setScreen(screenName) }
    }
}

extension View {
    /// .trackScreen("CalendarScreen") — 화면 조회 이벤트는 각 화면의 *.pageview 로 따로 보내요
    func trackScreen(_ screenName: String) -> some View {
        modifier(ScreenTrackingModifier(screenName: screenName))
    }
}

// MARK: - 흐린 버튼 탭

extension View {
    /// 흐린(disabled) 버튼을 눌렀을 때 — disabled 버튼은 탭을 받지 않아서, 막혀 있는 동안만 위에 투명 덮개를 깔아 잡아요.
    /// (*.disabled_click 같은 [UX] 이벤트용 · 막힌 동안엔 원래 동작이 없으니 덮개가 가로채도 괜찮아요)
    func disabledTap(_ disabled: Bool, perform: @escaping () -> Void) -> some View {
        overlay {
            if disabled {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture(perform: perform)
                    .accessibilityHidden(true)
            }
        }
    }
}
