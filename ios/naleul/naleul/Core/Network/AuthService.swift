// Core/Network/AuthService.swift
//
// 카카오 · 애플 로그인 → 우리 서비스 JWT 받기 → 토큰 저장 → 사용자 정보 반영.
// (예전 코드에서 워치로 토큰을 보내던 부분만 빠졌어요)

import Foundation
import UIKit

final class AuthService {
    static let shared = AuthService()
    private init() {}

    private let baseURL = Config.baseURL

    // MARK: - 카카오 로그인

    func kakaoLogin(accessToken: String) async throws -> LoginResponse {
        guard let url = URL(string: "\(baseURL)/api/v1/auth/kakao/ios?accessToken=\(accessToken)") else {
            throw APIError.invalidURL
        }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.cachePolicy = .reloadIgnoringLocalCacheData
        return try await login(req, method: "kakao")
    }

    // MARK: - 애플 로그인

    func appleLogin(identityToken: String, fullName: String?) async throws -> LoginResponse {
        guard let url = URL(string: "\(baseURL)/api/v1/auth/apple/login") else {
            throw APIError.invalidURL
        }
        var body: [String: String] = ["identityToken": identityToken]
        if let fullName { body["fullName"] = fullName }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(body)
        return try await login(req, method: "apple")
    }

    // MARK: - 공통

    private func login(_ req: URLRequest, method: String) async throws -> LoginResponse {
        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else { throw APIError.unknown(status) }

        let result = try JSONDecoder().decode(ApiCallResult<LoginResponse>.self, from: data)
        guard result.success, let login = result.data else {
            throw APIError.businessError(message: result.message ?? "로그인에 실패했어요. 잠시 후 다시 시도해 주세요.")
        }

        let wasGuest = TokenManager.shared.isGuest
        TokenManager.shared.save(accessToken: login.accessToken, refreshToken: login.refreshToken, userId: login.userId)
        await MainActor.run {
            // Amplitude 사용자 연결 (login.complete 이벤트는 버튼을 누른 화면(LoginView)이 보내요)
            Analytics.identify(userId: login.userId, role: UserRole(rawValue: login.userRole) ?? .free, provider: method)

            // 둘러보기에서 로그인 → 빈 화면 데이터 대신 내 데이터로 다시 불러와요
            if wasGuest {
                LoginPrompt.shared.isPresented = false
                Invalidation.shared.invalidateAll()
            }
            UserRole.saveAdminViewPlan(login.adminViewPlan)
            UserStore.shared.setUser(AppUser(
                userId: login.userId,
                userName: login.userName,
                userEmail: login.userEmail ?? "",
                userRole: UserRole(rawValue: login.userRole) ?? .free,
                adminViewPlan: login.adminViewPlan
            ))
            // 로그인 전에 받은 FCM 토큰은 서버 등록이 실패했을 수 있어서 다시 보내요
            (UIApplication.shared.delegate as? AppDelegate)?.resendCurrentTokenIfNeeded()
        }
        return login
    }

    // MARK: - 둘러보기 (로그인 없이)

    /// 읽기 전용 둘러보기 토큰을 받아 앱 안으로 들어가요. 빈 화면 + "추가해 보세요" 안내가 보이고, 저장하면 로그인 안내가 떠요
    func startGuest() async throws {
        guard let url = URL(string: "\(baseURL)/api/v1/auth/guest") else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        let (data, response) = try await URLSession.shared.data(for: req)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw APIError.network }
        let result = try JSONDecoder().decode(ApiCallResult<GuestLoginResponse>.self, from: data)
        guard let g = result.data else { throw APIError.network }
        TokenManager.shared.saveGuest(accessToken: g.accessToken, userId: g.userId)
        await MainActor.run {
            UserStore.shared.setUser(Self.guestUser(id: g.userId))
            Analytics.setUserProperties(role: .guest)
        }
    }

    static func guestUser(id: Int) -> AppUser {
        AppUser(userId: id, userName: "둘러보기", userEmail: "", userRole: .guest)
    }

    // MARK: - 로그아웃

    /// 로그인 화면으로 가는 이유 — login.pageview 의 entry_source
    enum LogoutReason: String {
        case logout
        case sessionExpired = "session_expired"
        case withdraw
    }

    /// 다음 login.pageview 가 한 번 꺼내 써요
    @MainActor private(set) var pendingLoginEntry: LogoutReason?
    @MainActor func takeLoginEntry() -> LogoutReason? {
        defer { pendingLoginEntry = nil }
        return pendingLoginEntry
    }

    @MainActor
    func logout(reason: LogoutReason = .logout) {
        pendingLoginEntry = reason
        if !TokenManager.shared.isGuest {
            (UIApplication.shared.delegate as? AppDelegate)?.unregisterCurrentToken()
            // 이 기기의 로그인 세션만 서버에서 지워요 (다른 기기 로그인은 그대로)
            if let refresh = TokenManager.shared.refreshToken { revokeOnServer(refresh) }
        }
        TokenManager.shared.clearAll()
        UserStore.shared.clearUser()
        Invalidation.shared.invalidateAll()
        // 다음 사람(둘러보기 포함)의 이벤트가 이 사용자에게 붙지 않게
        Analytics.reset()
    }
}

extension AuthService {
    /// POST /api/v1/auth/logout — 실패해도 앱은 로그아웃해요
    fileprivate func revokeOnServer(_ refreshToken: String) {
        guard let url = URL(string: "\(baseURL)/api/v1/auth/logout") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("Bearer \(refreshToken)", forHTTPHeaderField: "Authorization-Refresh")
        Task.detached { _ = try? await URLSession.shared.data(for: req) }
    }
}

private struct GuestLoginResponse: Decodable {
    let accessToken: String
    let userId: Int
}

// MARK: - 로그인 응답 (백엔드 LoginResponse)

struct LoginResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let userId: Int
    let userName: String
    let userEmail: String?
    let userRole: String
    /// 운영진이 보고 있는 플랜 "FREE" | "PRO" (운영진이 아니면 nil)
    var adminViewPlan: String? = nil
}
