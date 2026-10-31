// Core/Network/DeviceTokenService.swift
//
// FCM(푸시) 토큰을 서버에 등록 · 삭제해요.
//
// 왜 공통 API 클라이언트를 쓰나요?
//  예전에는 URLSession 으로 직접 보내서, 앱을 켰을 때 액세스 토큰이 만료돼 있으면
//  401 을 받고 그대로 끝났어요 (콘솔의 "디바이스 토큰 등록 실패 - status: 401").
//  API 클라이언트는 401 이면 토큰을 재발급해서 한 번 더 보내 주기 때문에 이 문제가 없어요.

import Foundation

final class DeviceTokenService {
    static let shared = DeviceTokenService()
    private init() {}

    /// FCM 토큰을 서버에 등록(또는 소유자 갱신).
    /// 로그인 전이면 보내지 않아요 → 로그인 직후 · 세션 복원 직후 AppDelegate.resendCurrentTokenIfNeeded() 가 다시 보내요.
    func registerToken(_ fcmToken: String) async {
        // 로그인 전이거나 둘러보기 중이면 보내지 않아요 (둘러보기는 같은 계정을 모두 같이 써서 푸시를 받을 수 없어요)
        guard TokenManager.shared.accessToken != nil, !TokenManager.shared.isGuest else { return }
        do {
            try await API.send("POST", "/v1/device-token", body: body(fcmToken))
            #if DEBUG
            print("✅ 디바이스 토큰 등록 성공")
            #endif
        } catch {
            #if DEBUG
            print("❌ 디바이스 토큰 등록 실패:", error.localizedDescription)
            #endif
        }
    }

    /// 로그아웃 시 — 로그아웃한 기기에 계속 푸시가 가는 걸 막아요.
    /// accessToken 은 로그아웃으로 지우기 "전에" 꺼내 둔 값을 받아요 (지운 뒤에 읽으면 nil 이라 요청을 못 보내요).
    /// (로그아웃 중이라 재발급 · 자동 로그아웃이 다시 일어나지 않게 직접 보내요)
    func unregisterToken(_ fcmToken: String, accessToken: String) async {
        guard let url = URL(string: Config.baseURL + "/api/v1/device-token") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "DELETE"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try? JSONEncoder().encode(body(fcmToken))
        _ = try? await URLSession.shared.data(for: req)
    }

    /// platform 은 백엔드 DeviceToken.Platform enum 과 같은 "IOS"
    private func body(_ fcmToken: String) -> JSONValue {
        ["fcmToken": .string(fcmToken), "platform": "IOS"]
    }
}
