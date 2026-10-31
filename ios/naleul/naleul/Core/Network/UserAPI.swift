// Core/Network/UserAPI.swift

import Foundation

enum UserAPI {
    /// GET /v1/users/{id} — 앱을 다시 켰을 때 로그인 상태 복원
    static func fetchUser(userId: Int) async throws -> UserResponse {
        guard let user: UserResponse = try await API.get("/v1/users/\(userId)") else {
            throw APIError.unknown(0)
        }
        return user
    }
}

struct UserResponse: Decodable {
    let id: Int
    let email: String?
    let nickname: String
    let userRole: String
    /// 운영진이 보고 있는 플랜 "FREE" | "PRO" (운영진이 아니면 nil)
    var adminViewPlan: String? = nil
}
