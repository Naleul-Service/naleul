//
//  TokenRefreshService.swift
//  Core/Network
//
//  UIKit, Firebase, KakaoSDK에 의존하지 않는 순수 토큰 갱신 로직.
//  APIClient가 401을 만났을 때 이 서비스만 참조하도록 해서
//  워치 타겟이 iOS 전용 SDK(Kakao/Firebase)에 물리지 않도록 분리했습니다.

import Foundation

// ✅ /api/v1/auth/token-reissue 성공 응답의 data 부분과 매칭되는 최소 모델.
//    서버(LoginResponse.java)는 accessToken 외에 userName, userEmail 등도 내려주지만
//    여기서는 토큰 갱신에 필요한 두 필드만 있으면 되므로 나머지는 선언하지 않았습니다
//    (JSONDecoder는 구조체에 없는 JSON 필드는 알아서 무시합니다).
private struct TokenReissueResponse: Decodable {
    let accessToken: String
    let refreshToken: String
}

private struct GuestTokenResponse: Decodable {
    let accessToken: String
    let userId: Int
}

/// 재발급 결과
///  - refreshed: 새 토큰을 받음 → 요청을 다시 보내요
///  - invalid: refresh token 이 정말 무효 (401/403) → 다시 로그인해야 해요
///  - unavailable: 서버가 잠깐 응답을 못 함 (네트워크 · 5xx) → 로그아웃시키지 않아요
enum TokenRefreshResult {
    case refreshed, invalid, unavailable
}

// ✅ class → actor
//    여러 화면에서 동시에 API를 호출하다 액세스 토큰이 만료된 시점에 겹치면,
//    401을 받은 요청들이 각자 refreshToken()을 "동시에" 호출하게 됩니다.
//    actor + inFlightTask로 동시 호출을 한 번의 네트워크 요청으로 합쳐서
//    서버가 리프레시 토큰을 1회용으로 회전시킬 때 생기는 경쟁 상태를 없앴습니다.
actor TokenRefreshService {
    static let shared = TokenRefreshService()
    private init() {}

    private let baseURL = Config.baseURL

    private var inFlightTask: Task<TokenRefreshResult, Never>?

    func refreshToken() async -> TokenRefreshResult {
        if let existing = inFlightTask {
            return await existing.value
        }

        let task = Task<TokenRefreshResult, Never> {
            do {
                if TokenManager.shared.isGuest { return try await performGuestRefresh() }
                return try await performRefresh()
            } catch {
                return .unavailable   // 네트워크 오류 — 토큰은 그대로 두고 다음에 다시
            }
        }
        inFlightTask = task

        defer { inFlightTask = nil }
        return await task.value
    }

    /// 둘러보기 토큰이 만료됐으면 새 둘러보기 토큰을 받아요 (refresh token 이 없어서)
    private func performGuestRefresh() async throws -> TokenRefreshResult {
        guard let url = URL(string: "\(baseURL)/api/v1/auth/guest") else { return .invalid }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        let (data, response) = try await URLSession.shared.data(for: req)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let result = try? JSONDecoder().decode(ApiCallResult<GuestTokenResponse>.self, from: data),
              let token = result.data else { return .unavailable }
        TokenManager.shared.saveGuest(accessToken: token.accessToken, userId: token.userId)
        return .refreshed
    }

    private func performRefresh() async throws -> TokenRefreshResult {
        guard let refreshToken = TokenManager.shared.refreshToken else {
            return .invalid
        }

        guard let url = URL(string: "\(baseURL)/api/v1/auth/token-reissue") else {
            return .invalid
        }

        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(refreshToken)", forHTTPHeaderField: "Authorization-Refresh")

        let (data, response) = try await URLSession.shared.data(for: req)
        guard let httpResponse = response as? HTTPURLResponse else { return .unavailable }

        print("✅ 토큰 재발급 응답 코드:", httpResponse.statusCode)

        // 리프레시 토큰 자체가 명확히 무효(401/403)일 때만 로그아웃 처리.
        // 그 외(500 등 서버 일시 오류나 리프레시 토큰 불일치)는 토큰을 지우지 않고
        // 이번 시도만 실패로 두어, 다음 API 호출 때 다시 시도할 수 있게 합니다.
        if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {
            TokenManager.shared.clearAll()
            return .invalid
        }

        guard httpResponse.statusCode == 200 else {
            return .unavailable
        }

        // ✅ 변경 — 기존엔 응답 "헤더"에서 새 토큰을 읽으려 했지만,
        //    서버(AuthController.reissueToken)는 새 토큰을 헤더가 아니라
        //    JSON 응답 바디(ApiCallResult.data)에 담아 내려줍니다.
        //    헤더엔 애초에 값이 없으니 서버가 재발급에 성공해도 항상 실패로 처리되고 있었습니다.
        guard
            let result = try? JSONDecoder().decode(ApiCallResult<TokenReissueResponse>.self, from: data),
            result.success,
            let newTokens = result.data
        else {
            return .unavailable
        }

        // ✅ 중요 — accessToken만 저장하면 안 됩니다.
        //    서버가 재발급할 때마다 refreshToken도 새 값으로 회전(rotate)시키고
        //    DB의 옛 refreshToken은 즉시 폐기합니다. 여기서 refreshToken을 같이 갱신하지 않으면,
        //    다음 재발급 시도 때 클라이언트는 여전히 "이미 폐기된" refreshToken을 보내게 되어
        //    서버에서 "Refresh token mismatch"로 실패하고, 결국 강제 로그아웃으로 이어집니다.
        TokenManager.shared.save(
            accessToken: newTokens.accessToken,
            refreshToken: newTokens.refreshToken
        )

        return .refreshed
    }
}
