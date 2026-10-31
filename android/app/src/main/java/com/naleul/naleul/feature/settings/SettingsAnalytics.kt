// feature/settings/SettingsAnalytics.kt
//
// iOS Features/Settings/SettingsAnalytics.swift — 설정 이벤트 (Tracking Plan 분류 "설정" ·
// 웹 settings · membership · life_pattern · calendar_import · notification_settings · terms · withdraw 와 같은 이름).
// 멤버십은 웹의 토스 카드 결제 대신 구글 플레이 결제라 purchase_result · manage_click · restore_* 를 써요.
//
//   ST.pendingMembershipEntry = "task_add"; AppRouter.openSettings(SettingsRoute.Membership)

package com.naleul.naleul.feature.settings

import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.core.state.UserRole
import com.naleul.naleul.core.state.UserStore
import com.naleul.naleul.feature.goal.EventProps
import com.naleul.naleul.feature.goal.GD

object ST {
    /** 미리보기(LocalInspectionMode)에서는 화면이 preview = true 로 불러요 */
    fun track(event: String, props: EventProps = emptyMap(), preview: Boolean = false) {
        if (preview) return
        GD.track(event, props)
    }

    /** 요청 실패의 HTTP 상태 (0 = 네트워크) */
    fun status(e: Throwable): Int = (e as? ApiError)?.httpStatus ?: 0

    /** 멤버십을 열기 직전에 정해 둬요 (settings_row · settings_banner · task_add · notification_settings) → membership.pageview */
    var pendingMembershipEntry: String? = null

    /** 기본 생활 패턴을 열기 직전에 정해 둬요 (settings · calendar_fixed_detail) → life_pattern.pageview */
    var pendingLifePatternEntry: String? = null

    /** is_premium — Pro · 운영진 */
    val isPremium: Boolean get() = UserStore.user?.userRole?.isPaid == true

    /** membership_state 의 기본값 (구글 플레이 구독 여부를 모를 때) — free · web_premium */
    val roleState: String get() = if (UserStore.user?.userRole?.isPaid == true) "web_premium" else "free"

    val isGuest: Boolean get() = UserStore.user?.userRole == UserRole.guest
}
