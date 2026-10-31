// App/naleulApp.swift
//
// 앱 시작점. 카카오 SDK · Firebase(푸시 전용) · Amplitude(분석) 초기화와 FCM 토큰 등록을 해요.
// 분석은 Firebase 가 아니라 Amplitude 로만 보내요 (Core/Analytics/AnalyticsService.swift).

import SwiftUI
import UIKit
import KakaoSDKCommon
import KakaoSDKAuth
import FirebaseCore
import FirebaseMessaging
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        MainActor.assumeIsolated { Analytics.start() }
        // 광고 (AdMob) — Free · 둘러보기 사용자 홈 배너
        Ads.start()

        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        // 아직 한 번도 안 물었을 때만 시스템 창이 떠요 → 그때만 prompt_view / result 를 보내요
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let firstAsk = settings.authorizationStatus == .notDetermined
            if firstAsk {
                Task { @MainActor in
                    Analytics.track(AnalyticsEvent.pushPermissionPromptView, ["prompt_timing": "app_launch"])
                }
            }
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                if firstAsk {
                    Task { @MainActor in
                        Analytics.track(AnalyticsEvent.pushPermissionResult, [
                            "result": granted ? "granted" : "denied", "prompt_timing": "app_launch",
                        ])
                    }
                }
                guard granted else { return }
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        if AuthApi.isKakaoTalkLoginUrl(url) {
            MainActor.assumeIsolated { Analytics.pendingOpenSource = "kakao_return" }
            return AuthController.handleOpenUrl(url: url)
        }
        return false
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task { await DeviceTokenService.shared.registerToken(token) }
    }

    /// 앱을 보고 있을 때도 알림 배너를 보여줘요 + 알림함에 넣어요
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let entry = NudgeCenter.entry(from: notification)
        let kind = Self.notificationKind(notification)
        Task { @MainActor in
            NudgeCenter.shared.record(entry)
            Analytics.track(AnalyticsEvent.pushReceive, ["notification_kind": kind, "routine_kind": entry.analyticsRoutineKind])
        }
        completionHandler([.banner, .sound, .badge, .list])
    }

    /// 알림을 탭하면 — 실천 도우미 알림은 알림에 적힌 곳으로, 서버 푸시는 오늘 캘린더로 (일정 알림이 대부분이라서)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let entry = NudgeCenter.entry(from: response.notification)
        let kind = Self.notificationKind(response.notification)
        // 이 콜백은 메인 스레드에서 와요. app.open 보다 먼저 정해 두려고 바로 기록해요
        // (실천 도우미(로컬)면 nudge, 서버 푸시면 push — 이어서 오는 app.open 의 open_source)
        let launch = MainActor.assumeIsolated { () -> String in
            Analytics.pendingOpenSource = entry.kind == .server ? "push" : "nudge"
            return Analytics.launchTypeNow
        }
        Task { @MainActor in
            // 앱을 새로 켠 경우 아직 사용자 복원 전이라, 저장된 토큰으로 판단해요
            let hasSession = TokenManager.shared.accessToken != nil
            let isGuest = TokenManager.shared.isGuest
            let props: EventProps = [
                "notification_kind": kind,
                "routine_kind": entry.analyticsRoutineKind,
                "destination": Self.destination(entry),
                "launch_type": launch,
                "is_logged_in": hasSession,
                "minutes_since_sent": max(0, Int(Date().timeIntervalSince(entry.date) / 60)),
            ]
            Analytics.track(AnalyticsEvent.pushOpen, props)
            // 로그인 전(로그아웃 · 둘러보기)엔 가려던 화면을 볼 수 없어요
            if !hasSession || isGuest {
                Analytics.track(AnalyticsEvent.pushOpenBlocked, [
                    "notification_kind": kind, "destination": Self.destination(entry),
                ])
            }
            NudgeCenter.shared.handleTap(entry)
        }
        completionHandler()
    }

    /// 실천 도우미면 종류(firstStep · dayWrapUp …), 서버 푸시면 서버가 준 type (없으면 server)
    nonisolated static func notificationKind(_ n: UNNotification) -> String {
        let info = n.request.content.userInfo
        if (info["type"] as? String) == "ROUTINE_NUDGE" { return "routine_nudge" }
        if let kind = info["kind"] as? String, NudgeKind(rawValue: kind) != nil, kind != NudgeKind.server.rawValue { return kind }
        return (info["type"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "server"
    }

    /// 누르면 갈 곳 — 경로 앞부분만 (AI 문장 같은 자유 입력은 보내지 않아요). 경로가 없으면 오늘 캘린더
    nonisolated static func destination(_ e: NudgeEntry) -> String {
        guard let route = e.route, !route.isEmpty else { return "calendar_today" }
        return String(route.split(separator: ":", maxSplits: 1).first ?? "")
    }

    /// 로그인 직후: 로그인 전에 받은 FCM 토큰은 서버 등록이 실패했을 수 있어서 다시 보내요
    func resendCurrentTokenIfNeeded() {
        Messaging.messaging().token { token, _ in
            guard let token else { return }
            Task { await DeviceTokenService.shared.registerToken(token) }
        }
    }

    /// 로그아웃: 로그아웃한 기기에 계속 푸시가 가지 않도록 서버에서 지워요.
    /// FCM 토큰 조회는 비동기라, 그사이 토큰이 지워지지 않도록 액세스 토큰을 먼저 꺼내 둬요.
    func unregisterCurrentToken() {
        guard let accessToken = TokenManager.shared.accessToken else { return }
        Messaging.messaging().token { token, _ in
            guard let token else { return }
            Task { await DeviceTokenService.shared.unregisterToken(token, accessToken: accessToken) }
        }
    }
}

@main
struct naleulApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var userStore = UserStore.shared
    @State private var router = AppRouter.shared
    @StateObject private var purchaseManager = PurchaseManager.shared

    init() {
        KakaoSDK.initSDK(appKey: Config.kakaoNativeAppKey)
        NTabBarAppearance.apply()   // 아래 탭바 색 · 글꼴 (디자인 시스템)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .onOpenURL { url in
                    if AuthApi.isKakaoTalkLoginUrl(url) { _ = AuthController.handleOpenUrl(url: url) }
                }
                .environment(userStore)
                .environment(router)
                .environmentObject(purchaseManager)
        }
    }
}
