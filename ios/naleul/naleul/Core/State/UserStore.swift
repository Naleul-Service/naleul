// Core/State/UserStore.swift
//
// 로그인한 사용자 정보. 앱 어디서든 UserStore.shared 또는 @Environment(UserStore.self) 로 읽어요.

import SwiftUI
import Observation

@MainActor
@Observable
final class UserStore {
    static let shared = UserStore()
    private init() {}

    var user: AppUser? = nil
    /// 앱 안으로 들어왔는지 (진짜 로그인 또는 둘러보기)
    var isLoggedIn: Bool { user != nil }
    /// 로그인 없이 둘러보는 중 — 저장하려면 로그인 안내가 떠요
    var isGuest: Bool { user?.userRole == .guest }

    func setUser(_ user: AppUser) {
        self.user = user
        // 다음에 앱을 켤 때 서버가 잠깐 안 돼도 이 정보로 바로 들어갈 수 있게
        if user.userRole != .guest {
            TokenManager.shared.cacheUser(name: user.userName, email: user.userEmail, role: user.userRole.rawValue)
        }
    }
    func clearUser() { user = nil }

    func updateUserName(_ name: String) {
        user?.userName = name
        if let user { setUser(user) }
    }
    func updateUserRole(_ role: UserRole) {
        user?.userRole = role
        if let user { setUser(user) }
    }

    /// 운영진이 보고 있는 플랜 ("FREE" | "PRO", 운영진이 아니면 nil) — 로그인 · 세션 복원 · 플랜 바꿔 보기 뒤에 불러요.
    /// user 를 다시 넣어서 isPaid 를 읽는 화면(광고 · 배지 · 업그레이드 배너)이 새로 그려지게 해요.
    func updateAdminViewPlan(_ plan: String?) {
        UserRole.saveAdminViewPlan(plan)
        // UserDefaults 는 SwiftUI 가 지켜보지 않아서, 관찰되는 값(user.adminViewPlan)도 같이 바꿔 화면을 다시 그려요
        user?.adminViewPlan = plan
    }
}

struct AppUser: Equatable {
    let userId: Int
    var userName: String
    var userEmail: String
    var userRole: UserRole
    /// 운영진이 보고 있는 플랜 ("FREE" | "PRO", 운영진이 아니면 nil) — 바뀌면 isPaid 를 쓰는 화면이 다시 그려지게 여기에도 둬요
    var adminViewPlan: String? = nil
}

/// 백엔드 UserRole
enum UserRole: String, Codable {
    case free = "FREE"
    case pro = "PRO"
    case admin = "ADMIN"
    /// 로그인 없이 둘러보기 (서버 역할이 아니라 앱에서만 써요)
    case guest = "GUEST"

    /// 웹 사이드바의 플랜 표기
    var planLabel: String {
        switch self {
        case .free: return "Free plan"
        case .pro: return "Pro plan"
        case .admin: return UserRole.adminViewsFree ? "Admin · Free" : "Admin · Pro"
        case .guest: return "로그인 전"
        }
    }

    /// Pro 혜택을 받는지. 운영진은 "플랜 바꿔 보기"로 고른 플랜을 따라요 (기본 Pro, 2026-10-24)
    var isPaid: Bool { self == .pro || (self == .admin && !UserRole.adminViewsFree) }

    // MARK: 운영진 플랜 바꿔 보기 (서버 users.admin_view_plan 과 같은 값 — 기기에는 화면 표시용으로만 둬요)
    private static let adminViewPlanKey = "admin_view_plan"
    /// 운영진이 Free 화면으로 보고 있는지
    static var adminViewsFree: Bool { UserDefaults.standard.string(forKey: adminViewPlanKey) == "FREE" }
    static func saveAdminViewPlan(_ plan: String?) {
        if let plan { UserDefaults.standard.set(plan, forKey: adminViewPlanKey) }
        else { UserDefaults.standard.removeObject(forKey: adminViewPlanKey) }
    }
}
