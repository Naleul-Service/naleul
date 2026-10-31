// core/network/AuthService.kt
//
// iOS Core/Network/AuthService.swift 를 옮긴 파일.
// 카카오 로그인 → 우리 서비스 JWT 받기 → 토큰 저장 → 사용자 정보 반영.
// (애플 로그인은 Android 에 없어서 뺐어요)

package com.naleul.naleul.core.network

import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.state.AppUser
import com.naleul.naleul.core.state.Invalidation
import com.naleul.naleul.core.state.LoginPrompt
import com.naleul.naleul.core.state.UserRole
import com.naleul.naleul.core.state.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/** 로그인 응답 (백엔드 LoginResponse) */
@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Int,
    val userName: String,
    val userEmail: String? = null,
    val userRole: String,
    /** 운영진이 보고 있는 플랜 "FREE" | "PRO" (운영진이 아니면 null) */
    val adminViewPlan: String? = null,
)

object AuthService {
    /** 화면과 상관없이 끝까지 해야 하는 일(로그아웃 통보 등)을 돌리는 범위 */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // MARK: - 카카오 로그인

    /**
     * 카카오 SDK 에서 받은 accessToken 으로 우리 서버에 로그인해요.
     * 서버 경로에 "ios" 가 들어 있지만, 카카오 토큰만 검증하는 API 라서 Android 도 같은 경로를 써요.
     */
    suspend fun kakaoLogin(accessToken: String): LoginResponse {
        val token = URLEncoder.encode(accessToken, "UTF-8")
        val req = Request.Builder()
            .url("${API.baseUrl}/api/v1/auth/kakao/ios?accessToken=$token")
            .get()
            .header("Content-Type", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        return login(req, method = "kakao")
    }

    // MARK: - 공통

    private suspend fun login(req: Request, method: String): LoginResponse {
        val (text, status) = withContext(Dispatchers.IO) {
            try {
                API.http.newCall(req).execute().use { (it.body?.string() ?: "") to it.code }
            } catch (e: java.io.IOException) {
                throw ApiError.Network
            }
        }
        if (status != 200) throw ApiError.unknown(status)

        val result = runCatching {
            NJson.decodeFromString(ApiCallResult.serializer(LoginResponse.serializer()), text)
        }.getOrElse { throw ApiError.Decoding(it.toString()) }
        val login = result.data
        if (!result.success || login == null) {
            throw ApiError.businessError(result.message ?: "로그인에 실패했어요. 잠시 후 다시 시도해 주세요.")
        }

        val wasGuest = TokenManager.isGuest
        TokenManager.save(login.accessToken, login.refreshToken, login.userId)
        // Amplitude 사용자 연결 (login.complete 이벤트는 버튼을 누른 화면(LoginView)이 보내요)
        AnalyticsService.identify(login.userId, UserRole.fromRaw(login.userRole) ?: UserRole.free, provider = method)

        withContext(Dispatchers.Main) {
            // 둘러보기에서 로그인 → 빈 화면 데이터 대신 내 데이터로 다시 불러와요
            if (wasGuest) {
                LoginPrompt.isPresented = false
                Invalidation.invalidateAll()
            }
            UserStore.updateAdminViewPlan(login.adminViewPlan)
            UserStore.setUser(
                AppUser(
                    userId = login.userId,
                    userName = login.userName,
                    userEmail = login.userEmail ?: "",
                    userRole = UserRole.fromRaw(login.userRole) ?: UserRole.free,
                )
            )
        }
        // 로그인 전에 받은 FCM 토큰은 서버 등록이 실패했을 수 있어서 다시 보내요
        DeviceTokenService.resendCurrentTokenIfNeeded()
        return login
    }

    // MARK: - 둘러보기 (로그인 없이)

    /** 읽기 전용 둘러보기 토큰을 받아 앱 안으로 들어가요. 저장하려고 하면 로그인 안내가 떠요 */
    suspend fun startGuest() {
        val req = Request.Builder()
            .url("${API.baseUrl}/api/v1/auth/guest")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val (text, status) = withContext(Dispatchers.IO) {
            try {
                API.http.newCall(req).execute().use { (it.body?.string() ?: "") to it.code }
            } catch (e: java.io.IOException) {
                throw ApiError.Network
            }
        }
        if (status != 200) throw ApiError.Network
        val g = runCatching {
            NJson.decodeFromString(ApiCallResult.serializer(GuestTokenResponse.serializer()), text).data
        }.getOrNull() ?: throw ApiError.Network
        TokenManager.saveGuest(g.accessToken, g.userId)
        withContext(Dispatchers.Main) { UserStore.setUser(guestUser(g.userId)) }
        AnalyticsService.setUserProperties(role = UserRole.guest)
    }

    fun guestUser(id: Int): AppUser = AppUser(userId = id, userName = "둘러보기", userEmail = "", userRole = UserRole.guest)

    // MARK: - 로그아웃

    /** 로그인 화면으로 가는 이유 — login.pageview 의 entry_source */
    enum class LogoutReason(val raw: String) { logout("logout"), sessionExpired("session_expired"), withdraw("withdraw") }

    /** 다음 login.pageview 가 한 번 꺼내 써요 */
    private var pendingLoginEntry: LogoutReason? = null
    fun takeLoginEntry(): LogoutReason? = pendingLoginEntry.also { pendingLoginEntry = null }

    /** 메인 스레드에서 불러요 (화면 상태를 바꿔서) */
    fun logout(reason: LogoutReason = LogoutReason.logout) {
        pendingLoginEntry = reason
        if (!TokenManager.isGuest) {
            DeviceTokenService.unregisterCurrentToken()
            // 이 기기의 로그인 세션만 서버에서 지워요 (다른 기기 로그인은 그대로)
            TokenManager.refreshToken?.let { revokeOnServer(it) }
        }
        TokenManager.clearAll()
        UserStore.clearUser()
        Invalidation.invalidateAll()
        // 다음 사람(둘러보기 포함)의 이벤트가 이 사용자에게 붙지 않게
        AnalyticsService.reset()
    }

    /** POST /api/v1/auth/logout — 실패해도 앱은 로그아웃해요 */
    private fun revokeOnServer(refreshToken: String) {
        backgroundScope.launch {
            runCatching {
                val req = Request.Builder()
                    .url("${API.baseUrl}/api/v1/auth/logout")
                    .post(ByteArray(0).toRequestBody(null))
                    .header("Authorization-Refresh", "Bearer $refreshToken")
                    .build()
                API.http.newCall(req).execute().close()
            }
        }
    }
}

// MARK: - 사용자 정보 (iOS UserAPI.swift)

@Serializable
data class UserResponse(
    val id: Int,
    val email: String? = null,
    val nickname: String,
    val userRole: String,
    /** 운영진이 보고 있는 플랜 "FREE" | "PRO" (운영진이 아니면 null) */
    val adminViewPlan: String? = null,
)

object UserAPI {
    /** GET /v1/users/{id} — 앱을 다시 켰을 때 로그인 상태 복원 */
    suspend fun fetchUser(userId: Int): UserResponse =
        API.get<UserResponse>("/v1/users/$userId") ?: throw ApiError.unknown(0)
}
