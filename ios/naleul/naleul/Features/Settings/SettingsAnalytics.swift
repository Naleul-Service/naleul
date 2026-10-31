// Features/Settings/SettingsAnalytics.swift
//
// 설정 이벤트 (Tracking Plan 분류 "설정" · 웹 settings / membership / life_pattern / calendar_import /
// notification_settings / terms / withdraw 와 같은 이름).
// 멤버십은 애플 인앱 결제라 purchase_result · manage_click · restore_* 를 써요 (예전 paywall_shown · purchase_* 대신).
//
//   ST.membershipEntry = "settings_row"   // → membership.pageview 의 entry_source

import Foundation
import SwiftUI

@MainActor
enum ST {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// 요청 실패의 HTTP 상태 (0 = 네트워크)
    static func status(_ error: Error) -> Int { (error as? APIError)?.httpStatus ?? 0 }

    /// 멤버십을 열기 직전에 정해 둬요 (settings_row · settings_banner · task_add · notification_settings)
    static var membershipEntry: String?
    /// 기본 생활 패턴을 열기 직전에 정해 둬요 (calendar_fixed_detail) — 없으면 settings
    static var lifePatternEntry: String?

    /// 지금 사용자가 유료인지
    static var isPremium: Bool { (UserStore.shared.user?.userRole ?? .free).isPaid }

    /// 상품 id → plan (monthly · yearly)
    static func plan(_ productId: String?) -> String? {
        switch productId {
        case ProductIdentifier.premiumMonthly.rawValue?: return "monthly"
        case ProductIdentifier.premiumYearly.rawValue?: return "yearly"
        default: return nil
        }
    }

    /// 앱 구독 중인 상품들 → plan (연간 우선)
    static func plan(of ids: Set<String>) -> String? {
        if ids.contains(ProductIdentifier.premiumYearly.rawValue) { return "yearly" }
        if ids.contains(ProductIdentifier.premiumMonthly.rawValue) { return "monthly" }
        return nil
    }

    /// membership_state — free · apple_active · web_premium
    static func membershipState(role: UserRole, appleIds: Set<String>) -> String {
        if !appleIds.isEmpty { return "apple_active" }
        return role.isPaid ? "web_premium" : "free"
    }

    /// 지금 사용자의 membership_state (탈퇴 화면 등 · PurchaseManager 기준)
    static var currentMembershipState: String {
        membershipState(role: UserStore.shared.user?.userRole ?? .free, appleIds: PurchaseManager.shared.purchasedProductIDs)
    }
}
