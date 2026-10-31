// app/RootScreen.kt
//
// iOS App/RootView.swift — 앱 첫 화면: 저장된 토큰으로 로그인 상태를 복원하고, 로그인 화면 또는 메인 탭을 보여줘요.

package com.naleul.naleul.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.naleul.naleul.core.analytics.AnalyticsEvent
import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.network.ApiError
import com.naleul.naleul.core.network.AuthService
import com.naleul.naleul.core.network.DeviceTokenService
import com.naleul.naleul.core.network.TokenManager
import com.naleul.naleul.core.network.UserAPI
import com.naleul.naleul.core.state.AppUser
import com.naleul.naleul.core.state.DataKey
import com.naleul.naleul.core.state.ReloadOn
import com.naleul.naleul.core.state.LoginPrompt
import com.naleul.naleul.core.state.ToastHost
import com.naleul.naleul.core.state.UserRole
import com.naleul.naleul.core.state.UserStore
import com.naleul.naleul.designsystem.NC
import com.naleul.naleul.designsystem.NSheet
import com.naleul.naleul.designsystem.NaleulTheme
import com.naleul.naleul.designsystem.canvasBackground
import com.naleul.naleul.feature.auth.LoginView
import com.naleul.naleul.feature.notifications.NudgeCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RootScreen() {
    var isRestoring by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val loggedIn = UserStore.isLoggedIn

    Box(Modifier.fillMaxSize().canvasBackground()) {
        when {
            isRestoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.ink3)
            }
            loggedIn -> {
                MainTabScreen()
                // 루틴 · Task · 목표를 지우거나 바꾸면 실천 도우미 예약도 바로 다시 계산해요
                // (예전엔 앱을 다시 켤 때까지 지운 루틴 이름으로 예약된 알림이 그대로 울렸어요)
                ReloadOn(listOf(DataKey.timetable, DataKey.goals, DataKey.habits)) {
                    runCatching { NudgeCenter.refresh(force = true, trigger = "data_change") }
                }
            }
            else -> LoginView()
        }
        // 화면 아래 토스트 (시트가 열려 있으면 시트 안에서 그려요)
        ToastHost()
    }

    // 둘러보기 중 저장하려고 하면 → 로그인 시트 (로그인하면 저절로 닫혀요)
    if (LoginPrompt.isPresented) {
        // 아래로 쓸어내리기 · 바깥 탭으로 닫힘 (X · 계속 둘러보기는 LoginView 가 직접 닫아요)
        NSheet(onDismiss = { LoginPrompt.close("swipe_down") }, fullHeight = true) {
            LoginView(asSheet = true)
        }
    }

    LaunchedEffect(Unit) {
        restoreSession()
        isRestoring = false
        if (UserStore.isLoggedIn) NudgeCenter.refresh(force = true)
    }

    // 로그인 상태가 바뀔 때 (iOS .onChange(of: userStore.isLoggedIn))
    var previous by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(loggedIn) {
        val before = previous
        previous = loggedIn
        if (before == null || isRestoring) return@LaunchedEffect
        if (loggedIn) {
            NudgeCenter.refresh(force = true, trigger = "login")
        } else {
            AppRouter.reset()
            // 로그아웃한 기기에 앞 사람의 실천 도우미 알림이 울리지 않게
            NudgeCenter.reset()
        }
    }

    // 앱이 앞으로 올 때마다: 받은 알림을 알림함에 모으고, 앞으로 사흘치 실천 도우미 알림을 다시 예약해요
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && UserStore.isLoggedIn) {
                scope.launch { runCatching { NudgeCenter.refresh() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** 저장된 토큰으로 로그인 상태 복원 */
private suspend fun restoreSession() {
    val userId = TokenManager.userId
    if (userId == null || TokenManager.accessToken == null) {
        AnalyticsService.track(AnalyticsEvent.sessionRestoreResult, "result" to "no_token")
        return
    }
    // 둘러보기: 서버에 물어볼 것 없이 바로 (토큰이 만료됐으면 API 클라이언트가 새로 받아요)
    if (TokenManager.isGuest) {
        UserStore.setUser(AuthService.guestUser(userId))
        AnalyticsService.setUserProperties(role = UserRole.guest)
        AnalyticsService.track(AnalyticsEvent.sessionRestoreResult, "result" to "guest")
        return
    }
    try {
        val info = UserAPI.fetchUser(userId)
        UserStore.updateAdminViewPlan(info.adminViewPlan)
        UserStore.setUser(
            AppUser(
                userId = info.id,
                userName = info.nickname,
                userEmail = info.email ?: "",
                userRole = UserRole.fromRaw(info.userRole) ?: UserRole.free,
            )
        )
        AnalyticsService.identify(info.id, UserRole.fromRaw(info.userRole) ?: UserRole.free)
        AnalyticsService.track(AnalyticsEvent.sessionRestoreResult, "result" to "success")
        // 앱을 켤 때 FCM 토큰이 로그인 확인보다 먼저 와서 등록이 건너뛰어졌을 수 있어요 → 세션 복원 뒤 다시 보내요
        DeviceTokenService.resendCurrentTokenIfNeeded()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 다시 로그인해야 하는 경우(재발급 실패 → 토큰 삭제)가 아니면, 서버가 잠깐 안 되는 것뿐이에요.
        // 로그인 화면으로 보내지 않고 마지막으로 본 정보로 들어가요
        if (TokenManager.accessToken != null) {
            AnalyticsService.track(
                AnalyticsEvent.sessionRestoreResult,
                "result" to "cached",
                "fail_reason" to ((e as? ApiError.Http)?.let { "http_${it.status}" } ?: "network"),
            )
            val cached = TokenManager.cachedUser
            UserStore.setUser(
                AppUser(
                    userId = userId,
                    userName = cached?.name ?: "나를 사용자",
                    userEmail = cached?.email ?: "",
                    userRole = UserRole.fromRaw(cached?.role) ?: UserRole.free,
                )
            )
        } else {
            // 재발급까지 거절돼 토큰이 지워짐 → 로그인 화면 (session.expired 도 같이 나가요)
            AnalyticsService.track(AnalyticsEvent.sessionRestoreResult, "result" to "expired")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RootScreenPreview() {
    NaleulTheme(dark = false) {
        LoginView()
    }
}
