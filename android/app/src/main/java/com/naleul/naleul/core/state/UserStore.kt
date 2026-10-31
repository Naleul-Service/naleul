// core/state/UserStore.kt
//
// iOS Core/State/UserStore.swift + LoginPrompt.swift.
// 로그인한 사용자 정보. 앱 어디서든 UserStore.user 로 읽어요.
//
// 왜 mutableStateOf 인가요?
//  iOS @Observable 처럼, Compose 화면이 이 값을 읽고 있다가 값이 바뀌면 화면이 저절로 다시 그려져요.

package com.naleul.naleul.core.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naleul.naleul.core.network.TokenManager

object UserStore {
    @set:JvmName("assignUser")
    var user: AppUser? by mutableStateOf(null)
        private set

    /** 앱 안으로 들어왔는지 (진짜 로그인 또는 둘러보기) */
    val isLoggedIn: Boolean get() = user != null

    /** 로그인 없이 둘러보는 중 — 저장하려면 로그인 안내가 떠요 */
    val isGuest: Boolean get() = user?.userRole == UserRole.guest

    fun setUser(user: AppUser) {
        this.user = user
        // 다음에 앱을 켤 때 서버가 잠깐 안 돼도 이 정보로 바로 들어갈 수 있게
        if (user.userRole != UserRole.guest) {
            TokenManager.cacheUser(user.userName, user.userEmail, user.userRole.raw)
        }
    }

    fun clearUser() {
        user = null
    }

    fun updateUserName(name: String) {
        user?.let { setUser(it.copy(userName = name)) }
    }

    fun updateUserRole(role: UserRole) {
        user?.let { setUser(it.copy(userRole = role)) }
    }

    /**
     * 운영진이 보고 있는 플랜 ("FREE" | "PRO", 운영진이 아니면 null) — 2026-10-24.
     * 서버 users.admin_view_plan 과 같은 값이고, 로그인 · 세션 복원 · 플랜 바꿔 보기 때 맞춰요.
     * Compose 상태라 바뀌면 isPaid 를 읽는 화면(광고 · 배지 · 업그레이드 배너)이 저절로 다시 그려져요.
     */
    var adminViewPlan: String? by mutableStateOf(null)
        private set

    fun updateAdminViewPlan(plan: String?) {
        adminViewPlan = plan
    }
}

data class AppUser(
    val userId: Int,
    val userName: String,
    val userEmail: String,
    val userRole: UserRole,
)

/** 백엔드 UserRole */
enum class UserRole(val raw: String) {
    free("FREE"),
    pro("PRO"),
    admin("ADMIN"),
    /** 로그인 없이 둘러보기 (서버 역할이 아니라 앱에서만 써요) */
    guest("GUEST");

    /** 웹 사이드바의 플랜 표기 */
    val planLabel: String
        get() = when (this) {
            free -> "Free plan"
            pro -> "Pro plan"
            admin -> if (UserStore.adminViewPlan == "FREE") "Admin · Free" else "Admin · Pro"
            guest -> "로그인 전"
        }

    /** Pro 혜택을 받는지. 운영진은 "플랜 바꿔 보기"로 고른 플랜을 따라요 (기본 Pro, 2026-10-24) */
    val isPaid: Boolean get() = this == pro || (this == admin && UserStore.adminViewPlan != "FREE")

    companion object {
        fun fromRaw(raw: String?): UserRole? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * 둘러보기(로그인 전) 중에 저장하려고 하면 "로그인하고 저장해요" 시트를 띄워요.
 * API 클라이언트가 LoginPrompt.request(reason, path) 를 부르고, RootScreen 이 isPresented 를 보고 시트를 열어요.
 * 왜 떴는지(reason) · 막힌 API(path) 는 login_modal.view / close 이벤트에 붙여요.
 */
object LoginPrompt {
    var isPresented: Boolean by mutableStateOf(false)

    /** save_blocked(앱이 저장 요청을 미리 막음) · guest_401(서버가 막음) · guest_banner · notification_settings · other */
    var reason: String? = null
        private set
    /** 막힌 API 경로 (숫자 id 는 :id) */
    var blockedPath: String? = null
        private set
    private var shownAt: Long? = null

    /** 둘러보기 중 저장하려 할 때 (로그인 사용자면 아무 일도 안 해요) */
    fun request(reason: String = "save_blocked", path: String? = null) {
        if (!UserStore.isGuest) return
        show(reason, path)
    }

    /** 버튼 등에서 직접 띄울 때 */
    fun show(reason: String, path: String? = null) {
        if (isPresented) return
        this.reason = reason
        this.blockedPath = path
        shownAt = System.currentTimeMillis()
        isPresented = true
    }

    /** 사용자가 닫을 때 — x_button · continue_browsing · swipe_down (로그인 성공으로 닫힐 땐 isPresented 만 꺼요) */
    fun close(method: String) {
        if (!isPresented) return
        isPresented = false
        com.naleul.naleul.core.analytics.AnalyticsService.track(
            com.naleul.naleul.core.analytics.AnalyticsEvent.loginModalClose,
            "close_method" to method,
            "modal_reason" to (reason ?: "other"),
            "open_duration_seconds" to shownAt?.let { ((System.currentTimeMillis() - it) / 1000).toInt() },
        )
    }
}
