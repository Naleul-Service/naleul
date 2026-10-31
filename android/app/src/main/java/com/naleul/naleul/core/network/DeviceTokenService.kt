// core/network/DeviceTokenService.kt
//
// iOS Core/Network/DeviceTokenService.swift + AppDelegate 의 FCM 토큰 부분을 옮긴 파일.
// FCM(푸시) 토큰을 서버에 등록 · 삭제해요. platform 은 백엔드 DeviceToken.Platform 의 "ANDROID".
//
// 왜 공통 API 클라이언트(API.send)를 쓰나요?
//  앱을 켰을 때 액세스 토큰이 만료돼 있으면 401 을 받는데, API 클라이언트는 토큰을 재발급해서 한 번 더 보내 줘요.

package com.naleul.naleul.core.network

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.naleul.naleul.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object DeviceTokenService {
    private const val TAG = "DeviceToken"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * FCM 토큰을 서버에 등록(또는 소유자 갱신).
     * 로그인 전이면 보내지 않아요 → 로그인 직후 · 세션 복원 직후 resendCurrentTokenIfNeeded() 가 다시 보내요.
     */
    suspend fun registerToken(fcmToken: String) {
        // 로그인 전이거나 둘러보기 중이면 보내지 않아요 (둘러보기는 같은 계정을 모두 같이 써서 푸시를 받을 수 없어요)
        if (TokenManager.accessToken == null || TokenManager.isGuest) return
        try {
            API.send("POST", "/v1/device-token", body(fcmToken))
            if (BuildConfig.DEBUG) Log.d(TAG, "✅ 디바이스 토큰 등록 성공")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "❌ 디바이스 토큰 등록 실패: ${e.message}")
        }
    }

    /** 새 토큰이 나왔을 때 (FirebaseMessagingService.onNewToken) */
    fun onNewToken(token: String) {
        scope.launch { registerToken(token) }
    }

    /** 로그인 직후 · 세션 복원 직후: 지금 FCM 토큰을 다시 보내요 */
    fun resendCurrentTokenIfNeeded() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token != null) scope.launch { registerToken(token) }
            }
        }
    }

    /**
     * 로그아웃: 로그아웃한 기기에 계속 푸시가 가지 않도록 서버에서 지워요.
     * FCM 토큰 조회는 비동기라, 그사이 토큰이 지워지지 않도록 액세스 토큰을 먼저 꺼내 둬요.
     * (로그아웃 중이라 재발급 · 자동 로그아웃이 다시 일어나지 않게 직접 보내요)
     */
    fun unregisterCurrentToken() {
        val accessToken = TokenManager.accessToken ?: return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token == null) return@addOnSuccessListener
                scope.launch {
                    runCatching {
                        val text = NJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), body(token))
                        val req = Request.Builder()
                            .url("${API.baseUrl}/api/v1/device-token")
                            .delete(text.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .header("Authorization", "Bearer $accessToken")
                            .build()
                        API.http.newCall(req).execute().close()
                    }
                }
            }
        }
    }

    private fun body(fcmToken: String) = json("fcmToken" to fcmToken, "platform" to "ANDROID")
}
