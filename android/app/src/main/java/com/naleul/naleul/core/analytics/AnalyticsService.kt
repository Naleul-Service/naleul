// core/analytics/AnalyticsService.kt
//
// Amplitude 이벤트를 한 곳에서 관리해요. (iOS Core/Analytics/AnalyticsService.swift · 웹 src/lib/client/analytics.ts 와 같은 역할 · 같은 Amplitude 프로젝트)
// 화면에서는 Amplitude SDK 를 직접 쓰지 않고 이 서비스만 써요.
//
// 규칙은 Notion "Tracking Plan" 을 따라요.
//  - 이벤트 이름은 'page.action' 그대로 (예: login_modal.kakao_click) — 웹 · iOS 와 같은 동작이면 같은 이름
//  - 이메일 · 닉네임 · 자유 입력 원문은 보내지 않아요
//  - 로그아웃 직후 reset() → 다음 사람의 둘러보기 이벤트가 이전 사용자에게 붙지 않아요
//
// local.properties 의 naleul.amplitudeApiKey 가 비어 있으면 아무것도 보내지 않아요 (웹 NEXT_PUBLIC_AMPLITUDE_API_KEY 와 같은 키).
//
//   AnalyticsService.track(AnalyticsEvent.loginKakaoClick, "login_surface" to "screen")
//   TrackScreen("CalendarScreen")   // 지금 보는 화면 이름만 기억해요 (app.background · toast.view 에 붙여요)

package com.naleul.naleul.core.analytics

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.core.events.Identify
import com.naleul.naleul.BuildConfig
import com.naleul.naleul.core.state.UserRole
import com.naleul.naleul.core.util.AppContext

/** Tracking Plan 의 event 컬럼 값 (모바일 · 로그인 / 공통) */
object AnalyticsEvent {
    // 로그인
    const val loginPageview = "login.pageview"
    const val loginKakaoClick = "login.kakao_click"
    const val loginComplete = "login.complete"
    const val loginFail = "login.fail"
    const val loginCancel = "login.cancel"
    const val loginGuestClick = "login.guest_click"
    const val guestSessionStart = "guest.session_start"
    const val guestStartFail = "guest.start_fail"
    const val loginModalView = "login_modal.view"
    const val loginModalKakaoClick = "login_modal.kakao_click"
    const val loginModalClose = "login_modal.close"
    const val guestBannerLoginClick = "guest_banner.login_click"
    const val settingsLogoutClick = "settings.logout_click"
    const val settingsLogoutConfirmClick = "settings.logout_confirm_click"
    const val settingsLogoutCancelClick = "settings.logout_cancel_click"
    const val sessionRestoreResult = "session.restore_result"
    const val sessionExpired = "session.expired"

    // 공통
    const val appOpen = "app.open"
    const val appBackground = "app.background"
    const val tabBarClick = "tab_bar.click"
    const val tabBarReselect = "tab_bar.reselect"
    const val pushPermissionPromptView = "push_permission.prompt_view"
    const val pushPermissionResult = "push_permission.result"
    const val pushReceive = "push.receive"
    const val pushOpen = "push.open"
    const val pushOpenBlocked = "push.open_blocked"
    const val toastView = "toast.view"
    const val toastDismissClick = "toast.dismiss_click"
    const val apiError = "api.error"
    const val adView = "ad.view"
    const val adClick = "ad.click"

    // TODO(분류 설계 때 교체): 예전 이벤트 — 멤버십은 설정 분류 membership.* 로 바꿨어요 (feature/settings/SettingsAnalytics.kt)
    const val taskAdded = "task_added"
    const val planLimitReached = "plan_limit_reached"
}

object AnalyticsService {
    /**
     * 운영 데이터 지키기 (2026-10, 웹 · iOS 와 같은 규칙)
     *  - 운영 서버(BASE_URL 이 https://api.naleul.com)에 붙은 빌드만 운영 Amplitude 로 보내요. 테스트 서버(dev-api) 빌드는 안 보내요
     *  - 운영자(ADMIN)로 로그인한 기기는 운영 집계에서 빼요 (opt-out). 운영자가 아닌 사람이 로그인하면 풀려요
     */
    private const val PRODUCTION_BASE_URL = "https://api.naleul.com"
    private const val OPERATOR_KEY = "naleul.analytics.operator"
    private val prefs by lazy { AppContext.app.getSharedPreferences("naleul_prefs", android.content.Context.MODE_PRIVATE) }

    private val amplitude: Amplitude? by lazy {
        val key = BuildConfig.AMPLITUDE_API_KEY
        if (key.isBlank()) {
            if (BuildConfig.DEBUG) Log.w("Analytics", "Amplitude API key missing — analytics disabled")
            return@lazy null
        }
        if (!BuildConfig.BASE_URL.startsWith(PRODUCTION_BASE_URL)) {
            if (BuildConfig.DEBUG) Log.i("Analytics", "운영 서버가 아니라서(${BuildConfig.BASE_URL}) 운영 집계로 보내지 않아요")
            return@lazy null
        }
        runCatching {
            Amplitude(
                Configuration(
                    apiKey = key,
                    context = AppContext.app,
                    // 수동 이벤트와 겹치지 않게 세션만 자동으로 (앱 열기 · 화면 조회는 직접 보내요)
                    autocapture = setOf(AutocaptureOption.SESSIONS),
                    // 운영자 기기는 앱을 다시 켜도 계속 빠져요
                    optOut = prefs.getBoolean(OPERATOR_KEY, false),
                )
            )
        }.getOrNull()
    }

    /** 이 기기를 운영자 기기로 표시하거나 풀어요 (운영자면 이벤트를 안 보내요) */
    fun markOperatorDevice(isOperator: Boolean) {
        prefs.edit().putBoolean(OPERATOR_KEY, isOperator).apply()
        runCatching { amplitude?.configuration?.optOut = isOperator }
    }

    /** SDK 를 미리 만들어 둬요 (NaleulApplication.onCreate 에서 한 번) */
    fun start() { amplitude }

    /** null 인 속성은 빼요 (Amplitude 에 "null" 로 쌓이지 않게) */
    fun track(event: String, vararg props: Pair<String, Any?>) {
        val clean = props.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        runCatching { amplitude?.track(event, clean.ifEmpty { null }) }
    }

    /** 로그인 사용자 연결 + User Property. 둘러보기 중 이벤트는 같은 deviceId 라서 이 순간 이 사용자에게 이어 붙어요 */
    fun identify(userId: Int, role: UserRole, provider: String? = null) {
        if (role != UserRole.guest) markOperatorDevice(role == UserRole.admin)
        val a = amplitude ?: return
        val id = userId.toString()
        if (a.getUserId() != id) a.setUserId(id)
        setUserProperties(role, provider)
    }

    /** user_role(GUEST/FREE/PRO/ADMIN) · login_provider */
    fun setUserProperties(role: UserRole? = null, provider: String? = null) {
        val a = amplitude ?: return
        if (role == null && provider == null) return
        val identify = Identify()
        role?.let { identify.set("user_role", it.raw) }
        provider?.let { identify.set("login_provider", it) }
        runCatching { a.identify(identify) }
    }

    /** 로그아웃 직후: userId 를 지우고 deviceId 를 새로 만들어요 */
    fun reset() { runCatching { amplitude?.reset() } }

    /** 시트 머리의 X 로 닫았을 때 잠깐 남겨요 (쓸어 닫기와 구분 · 시트 닫힘 이벤트가 한 번 꺼내 써요) */
    var sheetCloseHint: String? = null

    fun flush() { runCatching { amplitude?.flush() } }

    // MARK: - 화면 · 앱 상태 (app.open / app.background · toast.view 의 current_path)

    /** TrackScreen 이 넣어 주는 지금 화면 이름 */
    var currentScreen: String? = null
        private set
    fun setScreen(name: String) { currentScreen = name }

    private var foregroundAt: Long? = null
    private var didOpenOnce = false
    /** 앱이 앞으로 오기 직전에 정해지는 여는 경로 (push · nudge) */
    var pendingOpenSource: String? = null

    /** MainActivity.onStart */
    fun appDidBecomeActive(isLoggedIn: Boolean) {
        if (foregroundAt != null) return
        val launch = if (didOpenOnce) "warm" else "cold"
        didOpenOnce = true
        foregroundAt = System.currentTimeMillis()
        track(
            AnalyticsEvent.appOpen,
            "launch_type" to launch,
            "open_source" to (pendingOpenSource ?: "icon"),
            "is_logged_in" to isLoggedIn,
        )
        pendingOpenSource = null
    }

    /** MainActivity.onStop (화면 회전은 빼요) */
    fun appDidEnterBackground() {
        val since = foregroundAt ?: return
        track(
            AnalyticsEvent.appBackground,
            "last_screen" to currentScreen,
            "foreground_seconds" to ((System.currentTimeMillis() - since) / 1000).toInt(),
        )
        foregroundAt = null
        pendingOpenSource = null
        flush()
    }

    /** 알림을 눌러 앱이 열릴 때 — 아직 app.open 전이면 cold */
    val launchTypeNow: String get() = if (didOpenOnce) "warm" else "cold"

    // MARK: - 로그인 흐름 상태

    private var loginClickAt: Long? = null
    fun markLoginClick() { loginClickAt = System.currentTimeMillis() }
    fun secondsSinceLoginClick(): Int? = loginClickAt?.let { ((System.currentTimeMillis() - it) / 1000).toInt() }

    private var loginPromptCount = 0
    fun nextLoginPromptCount(): Int { loginPromptCount += 1; return loginPromptCount }

    /** 경로의 숫자 id 는 :id 로 (같은 API 끼리 묶여 보이게) */
    fun analyticsPath(path: String): String =
        path.substringBefore('?').split('/').joinToString("/") { s -> if (s.isNotEmpty() && s.all { it.isDigit() }) ":id" else s }

    // MARK: - 예전 이벤트 (Task 추가 · 멤버십 화면이 써요)

    /** mode: "ai" · "manual" · "log" */
    fun logTaskAdded(mode: String, count: Int) = track(AnalyticsEvent.taskAdded, "mode" to mode, "count" to count)
    /** context: "goal_session" · "task_per_day" … */
    fun logPlanLimitReached(context: String) = track(AnalyticsEvent.planLimitReached, "context" to context)
}

/** 지금 보는 화면 이름을 기억해요 (iOS .trackScreen). 화면 조회 이벤트는 각 화면의 *.pageview 로 따로 보내요 */
@Composable
fun TrackScreen(screenName: String) {
    LaunchedEffect(screenName) { AnalyticsService.setScreen(screenName) }
}
