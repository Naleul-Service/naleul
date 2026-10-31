// Core/State/LoginPrompt.swift
//
// 둘러보기(로그인 전) 중에 저장하려고 하면 "로그인하고 저장해요" 시트를 띄워요.
// API 클라이언트가 LoginPrompt.request(reason:path:) 를 부르고, RootView 가 isPresented 를 보고 시트를 열어요.
// 왜 떴는지(reason) · 막힌 API(path) 는 login_modal.view / close 이벤트에 붙여요.

import SwiftUI
import Observation

@MainActor
@Observable
final class LoginPrompt {
    static let shared = LoginPrompt()
    private init() {}

    var isPresented = false {
        didSet { if isPresented && !oldValue { shownAt = Date() } }
    }

    /// save_blocked(앱이 저장 요청을 미리 막음) · guest_401(서버가 막음) · guest_banner · notification_settings · other
    private(set) var reason: String?
    /// 막힌 API 경로 (숫자 id 는 :id 로 바꿔요)
    private(set) var blockedPath: String?
    private(set) var shownAt: Date?
    /// 시트를 닫은 방법 — x_button · continue_browsing (없으면 쓸어내려 닫은 것)
    var closeMethod: String?

    /// 둘러보기 중 저장하려 할 때 (로그인 사용자면 아무 일도 안 해요)
    static func request(reason: String = "save_blocked", path: String? = nil) {
        guard UserStore.shared.isGuest else { return }
        shared.show(reason: reason, path: path)
    }

    /// 버튼 등에서 직접 띄울 때
    func show(reason: String, path: String? = nil) {
        guard !isPresented else { return }
        self.reason = reason
        self.blockedPath = path
        self.closeMethod = nil
        isPresented = true
    }
}
