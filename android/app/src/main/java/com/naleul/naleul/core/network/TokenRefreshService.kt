// core/network/TokenRefreshService.kt
//
// iOS TokenRefreshService.swift (actor) 를 옮긴 파일.
//
// 왜 Mutex 를 쓰나요?
//  여러 화면에서 동시에 API 를 부르다 액세스 토큰이 만료되면, 401 을 받은 요청들이 각자 재발급을 "동시에" 시도해요.
//  서버는 재발급할 때마다 refresh token 을 새 값으로 바꾸고(회전) 옛 값을 버려서,
//  두 번째 요청은 이미 버려진 refresh token 을 보내게 되고 → "Refresh token mismatch" → 강제 로그아웃이 돼요.
//  Mutex(한 번에 한 명만 들어가는 문)로 재발급을 한 번만 하고, 기다리던 요청들은 그 결과를 같이 써요.

package com.naleul.naleul.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 재발급 결과
 *  - REFRESHED: 새 토큰을 받음 → 요청을 다시 보내요
 *  - INVALID: refresh token 이 정말 무효 (401/403) → 다시 로그인해야 해요
 *  - UNAVAILABLE: 서버가 잠깐 응답을 못 함 (네트워크 · 5xx) → 로그아웃시키지 않아요
 */
enum class TokenRefreshResult { REFRESHED, INVALID, UNAVAILABLE }

@Serializable
private data class TokenReissueResponse(val accessToken: String, val refreshToken: String)

@Serializable
internal data class GuestTokenResponse(val accessToken: String, val userId: Int)

object TokenRefreshService {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<TokenRefreshResult>? = null

    suspend fun refreshToken(): TokenRefreshResult {
        // 이미 누가 재발급 중이면 그 결과를 기다려요
        val (deferred, owner) = mutex.withLock {
            inFlight?.let { it to false } ?: CompletableDeferred<TokenRefreshResult>().also { inFlight = it }.let { it to true }
        }
        if (!owner) return deferred.await()

        val result = try {
            if (TokenManager.isGuest) performGuestRefresh() else performRefresh()
        } catch (e: Exception) {
            TokenRefreshResult.UNAVAILABLE // 네트워크 오류 — 토큰은 그대로 두고 다음에 다시
        }
        mutex.withLock { inFlight = null }
        deferred.complete(result)
        return result
    }

    /** 둘러보기 토큰이 만료됐으면 새 둘러보기 토큰을 받아요 (refresh token 이 없어서) */
    private suspend fun performGuestRefresh(): TokenRefreshResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${API.baseUrl}/api/v1/auth/guest")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        API.http.newCall(req).execute().use { res ->
            if (res.code != 200) return@withContext TokenRefreshResult.UNAVAILABLE
            val text = res.body?.string() ?: return@withContext TokenRefreshResult.UNAVAILABLE
            val token = runCatching {
                NJson.decodeFromString(ApiCallResult.serializer(GuestTokenResponse.serializer()), text).data
            }.getOrNull() ?: return@withContext TokenRefreshResult.UNAVAILABLE
            TokenManager.saveGuest(token.accessToken, token.userId)
            TokenRefreshResult.REFRESHED
        }
    }

    private suspend fun performRefresh(): TokenRefreshResult = withContext(Dispatchers.IO) {
        val refreshToken = TokenManager.refreshToken ?: return@withContext TokenRefreshResult.INVALID
        val req = Request.Builder()
            .url("${API.baseUrl}/api/v1/auth/token-reissue")
            .get()
            .header("Content-Type", "application/json")
            .header("Authorization-Refresh", "Bearer $refreshToken")
            .build()
        API.http.newCall(req).execute().use { res ->
            // 리프레시 토큰 자체가 명확히 무효(401/403)일 때만 로그아웃 처리.
            // 그 외(500 등 서버 일시 오류)는 토큰을 지우지 않고 이번 시도만 실패로 둬요.
            if (res.code == 401 || res.code == 403) {
                TokenManager.clearAll()
                return@withContext TokenRefreshResult.INVALID
            }
            if (res.code != 200) return@withContext TokenRefreshResult.UNAVAILABLE
            val text = res.body?.string() ?: return@withContext TokenRefreshResult.UNAVAILABLE
            // 서버는 새 토큰을 헤더가 아니라 JSON 응답 바디(ApiCallResult.data)에 담아 내려줘요
            val result = runCatching {
                NJson.decodeFromString(ApiCallResult.serializer(TokenReissueResponse.serializer()), text)
            }.getOrNull()
            val tokens = result?.takeIf { it.success }?.data ?: return@withContext TokenRefreshResult.UNAVAILABLE
            // 중요 — 서버가 refresh token 도 회전시켜서, 둘 다 새 값으로 저장해야 다음 재발급이 성공해요
            TokenManager.save(tokens.accessToken, tokens.refreshToken)
            TokenRefreshResult.REFRESHED
        }
    }
}
