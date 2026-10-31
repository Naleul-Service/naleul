// feature/auth/LoginView.kt
//
// iOS Features/Auth/LoginView.swift — 웹 (auth)/login/page.tsx 와 같은 로그인 화면.
// Android 는 애플 로그인이 없어서 카카오 로그인 + "둘러보기"만 있어요.
//
//   LoginView()                 // 로그인 전 첫 화면 (RootScreen)
//   LoginView(asSheet = true)   // 둘러보기 중 저장하려다 뜬 로그인 시트의 "내용" (시트 자체는 앱 골격이 그려요)
//
// 카카오 로그인 흐름
//  1) 카카오톡 앱이 있으면 카카오톡으로 로그인 (한 번 눌러 끝나요), 없으면 카카오 계정(웹) 로그인
//  2) 카카오가 준 accessToken 을 우리 서버에 보내 우리 서비스 토큰(JWT)을 받아요 (AuthService.kakaoLogin)
//  3) 로그인이 되면 UserStore.user 가 채워지고 → 앱이 메인 화면으로 바꿔 그려요

package com.naleul.naleul.feature.auth

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun LoginView(asSheet: Boolean = false) {
    TrackScreen("LoginView")
    // 전체 화면이면 login.pageview, 시트면 login_modal.view
    LaunchedEffect(asSheet) { LoginTracking.appear(asSheet) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 시트 닫기 = 앱 골격이 보고 있는 LoginPrompt 를 끄면 돼요 (iOS dismiss())
    val dismiss: (String) -> Unit = { method -> LoginPrompt.close(method) }

    // MARK: 둘러보기
    fun handleGuest() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                AuthService.startGuest()
                AnalyticsService.track(AnalyticsEvent.guestSessionStart, "is_new_guest" to true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalyticsService.track(
                    AnalyticsEvent.guestStartFail,
                    "error_status" to (e as? ApiError)?.httpStatus, "fail_reason" to LoginTracking.errorCode(e),
                )
                android.util.Log.e("Login", "둘러보기 시작 실패 (${API.baseUrl})", e)
                errorMessage = "지금은 둘러보기를 시작할 수 없어요. 잠시 후 다시 시도해 주세요." +
                    if (com.naleul.naleul.BuildConfig.DEBUG) "\n(서버: ${API.baseUrl})" else ""
            } finally {
                isLoading = false
            }
        }
    }

    // MARK: 카카오 로그인
    fun handleKakaoLogin() {
        scope.launch {
            isLoading = true
            errorMessage = null
            // 1) 카카오 로그인 (카카오 서버) → 2) 우리 서버 로그인. 어디서 실패했는지 나눠서 알려줘요
            val wasGuest = UserStore.isGuest
            val token = try {
                loginWithKakao(context) ?: run {
                    // null: 사용자가 취소 → 아무 말 없이
                    LoginTracking.cancel(asSheet)
                    isLoading = false
                    return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginTracking.fail(asSheet, "kakao_sdk")
                // 카카오 단계 실패: 키 해시 미등록 · 앱 키 불일치 · 카카오 콘솔 설정 문제가 대부분이에요
                android.util.Log.e("Login", "카카오 로그인 실패", e)
                errorMessage = "카카오 로그인에 실패했어요." +
                    if (com.naleul.naleul.BuildConfig.DEBUG) "\n(${e.message ?: e::class.java.simpleName})" else ""
                isLoading = false
                return@launch
            }
            try {
                AuthService.kakaoLogin(token.accessToken)
                LoginTracking.complete(asSheet, wasGuest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginTracking.fail(asSheet, LoginTracking.errorCode(e))
                // 우리 서버 단계 실패: 서버 주소(BASE_URL)에 폰이 닿지 못하거나 서버가 거절한 경우예요
                android.util.Log.e("Login", "서버 로그인 실패 (${API.baseUrl})", e)
                errorMessage = errorMessage(e, "로그인에 실패했어요. 잠시 후 다시 시도해 주세요.") +
                    if (com.naleul.naleul.BuildConfig.DEBUG) "\n(서버: ${API.baseUrl})" else ""
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // 스펙 13.2: 시트로 뜰 땐 bg/surface + 공용 시트 머리 · 내용 좌우 24 / 위 20
            .then(if (asSheet) Modifier.background(NC.bgSurface) else Modifier.canvasBackground().statusBarsPadding().navigationBarsPadding()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxSize()) {
        if (asSheet) NSheetHeader(title = null, onClose = { dismiss("x_button") })
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 24.dp, end = 24.dp, top = if (asSheet) 20.dp else 0.dp, bottom = if (asSheet) 24.dp else 16.dp),
        ) {
            Spacer(Modifier.weight(1f))

            BrandLogo(size = BrandLogoSize.lg)

            val brand = NC.brand
            Text(
                buildAnnotatedString {
                    append("목표를 세우고,\n")
                    withStyle(SpanStyle(color = brand)) { append("나를") }
                    append(" 알아가요")
                },
                style = pt(32, PTWeight.bold).copy(lineHeight = 42.sp),
                color = NC.ink,
                modifier = Modifier.padding(top = 40.dp),
            )

            Text(
                if (asSheet) "지금은 둘러보는 중이에요.\n로그인하면 만든 목표와 Task가 저장되고, 웹과 앱 어디서든 이어서 쓸 수 있어요."
                else "AI와 대화하며 목표를 설계하고\n매일의 실천을 기록해요.",
                style = pt(15).copy(lineHeight = 23.sp),
                color = NC.ink3,
                modifier = Modifier.padding(top = 12.dp),
            )

            val err = errorMessage
            if (err != null) {
                Text(
                    err,
                    style = pt(14, PTWeight.medium),
                    color = NC.danger,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .fillMaxWidth()
                        .background(NC.dangerSoft, RoundedCornerShape(Radius.xl))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().alpha(if (isLoading) 0.5f else 1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 카카오로 시작하기
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(NC.kakao, RoundedCornerShape(Radius.xxl))
                            .clickable(enabled = !isLoading, role = Role.Button) {
                                LoginTracking.click(asSheet)
                                handleKakaoLogin()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        val kakaoInk = colorHex("#191600")
                        SfImage("message.fill", size = 18.dp, tint = kakaoInk)
                        Text("카카오로 시작하기", style = pt(15, PTWeight.semibold), color = kakaoInk)
                    }

                    // 둘러보기 (시트면 "계속 둘러보기" = 닫기)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = !isLoading, role = Role.Button) {
                                if (asSheet) {
                                    dismiss("continue_browsing")
                                } else {
                                    AnalyticsService.track(AnalyticsEvent.loginGuestClick)
                                    handleGuest()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (asSheet) "계속 둘러보기" else "로그인 없이 둘러보기",
                            style = pt(15, PTWeight.semibold),
                            color = NC.ink2,
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                }
            }

            Text(
                "© 2026 나를(Naleul)",
                style = pt(12),
                color = NC.ink4,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        }
    }
}

/**
 * 카카오 로그인 → 카카오 accessToken.
 *  - 카카오톡 앱이 있으면 카카오톡으로, 실패하면 카카오 계정(웹) 로그인으로 한 번 더
 *  - 사용자가 취소하면 null (에러 문구를 띄우지 않아요)
 * 카카오 SDK 는 "끝나면 불러 줄게"(콜백) 방식이라 suspendCancellableCoroutine 으로 감싸서 기다려요.
 */
private suspend fun loginWithKakao(context: Context): OAuthToken? {
    val client = UserApiClient.instance
    if (client.isKakaoTalkLoginAvailable(context)) {
        try {
            return kakaoTalkLogin(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 카카오톡 로그인 화면에서 사용자가 취소한 경우 — 계정 로그인으로 넘어가지 않아요
            if (isKakaoCancel(e)) return null
            // 카카오톡에 연결된 카카오계정이 없는 경우 등 → 카카오 계정 로그인으로 다시 시도
        }
    }
    return try {
        kakaoAccountLogin(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (isKakaoCancel(e)) null else throw (e as? Exception ?: Exception(e))
    }
}

private fun isKakaoCancel(e: Throwable): Boolean = e is ClientError && e.reason == ClientErrorCause.Cancelled

private suspend fun kakaoTalkLogin(context: Context): OAuthToken = suspendCancellableCoroutine { cont ->
    UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
        if (!cont.isActive) return@loginWithKakaoTalk
        when {
            error != null -> cont.resumeWithException(error)
            token != null -> cont.resume(token)
            else -> cont.resumeWithException(ApiError.unknown(0))
        }
    }
}

private suspend fun kakaoAccountLogin(context: Context): OAuthToken = suspendCancellableCoroutine { cont ->
    UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
        if (!cont.isActive) return@loginWithKakaoAccount
        when {
            error != null -> cont.resumeWithException(error)
            token != null -> cont.resume(token)
            else -> cont.resumeWithException(ApiError.unknown(0))
        }
    }
}

// MARK: - 이벤트 (Tracking Plan · 로그인 — Android 는 카카오만)

private object LoginTracking {
    fun surface(asSheet: Boolean) = if (asSheet) "sheet" else "screen"

    fun appear(asSheet: Boolean) {
        if (asSheet) {
            AnalyticsService.track(
                AnalyticsEvent.loginModalView,
                "modal_reason" to (LoginPrompt.reason ?: "other"),
                "blocked_api_path" to LoginPrompt.blockedPath,
                "prompt_count_session" to AnalyticsService.nextLoginPromptCount(),
            )
        } else {
            AnalyticsService.track(
                AnalyticsEvent.loginPageview,
                "entry_source" to (AuthService.takeLoginEntry()?.raw ?: "app_launch"),
            )
        }
    }

    fun click(asSheet: Boolean) {
        AnalyticsService.markLoginClick()
        AnalyticsService.track(
            if (asSheet) AnalyticsEvent.loginModalKakaoClick else AnalyticsEvent.loginKakaoClick,
            "login_surface" to surface(asSheet),
            "modal_reason" to if (asSheet) (LoginPrompt.reason ?: "other") else null,
        )
    }

    fun complete(asSheet: Boolean, wasGuest: Boolean) = AnalyticsService.track(
        AnalyticsEvent.loginComplete,
        "login_provider" to "kakao", "login_surface" to surface(asSheet),
        "was_guest" to wasGuest, "seconds_since_click" to AnalyticsService.secondsSinceLoginClick(),
    )

    fun fail(asSheet: Boolean, code: String) = AnalyticsService.track(
        AnalyticsEvent.loginFail,
        "login_provider" to "kakao", "login_surface" to surface(asSheet),
        "login_error_code" to code, "seconds_since_click" to AnalyticsService.secondsSinceLoginClick(),
    )

    fun cancel(asSheet: Boolean) = AnalyticsService.track(
        AnalyticsEvent.loginCancel,
        "login_provider" to "kakao", "login_surface" to surface(asSheet),
        "seconds_since_click" to AnalyticsService.secondsSinceLoginClick(),
    )

    /** login_error_code · fail_reason — network · server · unknown */
    fun errorCode(e: Throwable): String = when (e) {
        is ApiError.Network -> "network"
        is ApiError -> "server"
        is java.io.IOException -> "network"
        else -> "unknown"
    }
}

// MARK: - 로고 (Figma brand/logo 워드마크 — designsystem Wordmark)

/** 로고 크기 (iOS BrandLogo.Size) */
enum class BrandLogoSize { md, lg }

@Composable
fun BrandLogo(size: BrandLogoSize = BrandLogoSize.md, modifier: Modifier = Modifier) {
    // Figma 워드마크 (brand/logo/base · 다크 모드 #7FCBE6)
    Wordmark(height = if (size == BrandLogoSize.lg) 36.dp else 24.dp, modifier = modifier)
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginViewSheetPreview() {
    NaleulTheme(dark = false) { LoginView(asSheet = true) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginViewPreview() {
    NaleulTheme(dark = false) { LoginView() }
}
