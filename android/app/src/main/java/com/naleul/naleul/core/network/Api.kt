// core/network/Api.kt
//
// iOS Core/Network/APIClient.swift 를 옮긴 네트워크 레이어.
//
// 사용 예 (웹 · iOS 와 같은 경로를 써요 — 앞의 "/api" 는 여기서 붙여요):
//   val goals: List<GoalCategory> = API.get<List<GoalCategory>>("/v1/goal-categories") ?: emptyList()
//   val turn: TurnResponse? = API.post<TurnResponse>("/v1/goal-creation/sessions", json("initialMessage" to text))
//   API.send("PATCH", "/v1/tasks/$id/complete")      // 응답 data 를 안 쓰는 요청
//
// 동작 규칙 (iOS 와 동일)
//  - 백엔드 공통 응답 { success, status, message, data } 에서 data 만 꺼내 돌려줘요.
//  - 204(No Content) 는 null.
//  - 실패(HTTP 4xx/5xx 또는 success=false)는 ApiError.Http(...) 로 던져요. message 는 백엔드 문구 그대로.
//  - 401 은 토큰 재발급을 한 번 시도하고, 그래도 실패하면 로그아웃시켜요.
//
// 왜 suspend 함수인가요?
//  iOS 의 async/await 와 같아요. 네트워크를 기다리는 동안 화면(메인 스레드)이 멈추지 않게,
//  실제 통신은 Dispatchers.IO(입출력 전용 스레드)에서 하고 결과만 돌려줘요.

package com.naleul.naleul.core.network

import android.util.Log
import com.naleul.naleul.BuildConfig
import com.naleul.naleul.core.state.LoginPrompt
import com.naleul.naleul.core.analytics.AnalyticsEvent
import com.naleul.naleul.core.analytics.AnalyticsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// MARK: - 에러

/** iOS APIError */
sealed class ApiError(message: String) : Exception(message) {
    /** 백엔드가 실패로 응답 (httpStatus: 409, 422, 429 …) */
    class Http(val status: Int, override val message: String, val statusName: String?, val data: JsonElement?) : ApiError(message)
    /** 인터넷 연결 문제 */
    object Network : ApiError("네트워크 연결을 확인해 주세요.")
    /** 응답을 Kotlin 모델로 바꾸지 못함 (모델 정의가 백엔드와 다를 때) */
    class Decoding(val detail: String) : ApiError("데이터를 읽지 못했어요. 앱을 최신 버전으로 업데이트해 주세요.")
    object InvalidUrl : ApiError("잘못된 주소예요.")
    object Unauthorized : ApiError("다시 로그인해 주세요.")

    /** 화면에 보여줄 문구 (iOS errorDescription) */
    val errorDescription: String get() = message ?: "문제가 생겼어요. 다시 시도해 주세요."

    val isGuestLoginRequired: Boolean get() = this is Http && statusName == GUEST_STATUS

    /** 숫자 HTTP 상태코드 (네트워크 오류는 0) */
    val httpStatus: Int
        get() = when (this) {
            is Http -> status
            is Unauthorized -> 401
            else -> 0
        }

    /** 실패 응답에 담긴 data (예: 422 의 { violations }) */
    val failureData: JsonElement? get() = (this as? Http)?.data

    /** 422 확정 검증 실패일 때 violations 꺼내기 */
    val violations: List<Violation>
        get() {
            val arr = failureData?.get("violations")?.arrayValue ?: return emptyList()
            return arr.mapNotNull { v ->
                val ruleId = v["ruleId"]?.stringValue ?: return@mapNotNull null
                val path = v["path"]?.stringValue ?: return@mapNotNull null
                val message = v["message"]?.stringValue ?: return@mapNotNull null
                Violation(ruleId, path, message)
            }
        }

    companion object {
        /** 둘러보기 중에 저장하려고 함 → 로그인 안내 시트가 떠요 */
        const val GUEST_STATUS = "GUEST_LOGIN_REQUIRED"
        const val GUEST_MESSAGE = "로그인하면 저장할 수 있어요."
        val guestLoginRequired: ApiError get() = Http(401, GUEST_MESSAGE, GUEST_STATUS, null)

        fun unknown(code: Int): ApiError = Http(code, "오류가 발생했습니다 ($code)", null, null)
        fun businessError(message: String): ApiError = Http(400, message, null, null)
    }
}

/** 확정 API 422 응답의 data 구조 */
data class Violation(val ruleId: String, val path: String, val message: String)

/** 어떤 에러든 화면에 보여줄 문구로 (iOS errorMessage(error, fallback:)) */
fun errorMessage(error: Throwable, fallback: String = "문제가 생겼어요. 다시 시도해 주세요."): String =
    (error as? ApiError)?.errorDescription ?: fallback

// MARK: - 공통 응답 껍데기

/** 백엔드 공통 응답 형식 { success, message, data, status } (iOS ApiCallResult) */
@Serializable
data class ApiCallResult<T>(
    val success: Boolean = false,
    val message: String? = null,
    val data: T? = null,
    val status: String? = null,
    val error: String? = null,
)

@Serializable
private data class Envelope(
    val success: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    val data: JsonElement? = null,
)

// MARK: - API

object API {
    private const val TAG = "API"

    /** BuildConfig.BASE_URL 은 local.properties 의 naleul.baseUrl.debug / release 에서 와요 */
    val baseUrl: String get() = BuildConfig.BASE_URL.trimEnd('/')

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** iOS URLSession.shared 처럼 앱 전체에서 하나만 써요 (연결을 재사용해서 빨라요) */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 응답 data 를 T 로 받아요. data 가 없거나 204 면 null */
    suspend inline fun <reified T> get(path: String): T? = decode(raw("GET", path, null), serializer<T>())

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T? =
        decode(raw("POST", path, body ?: JsonObject(emptyMap())), serializer<T>())

    suspend inline fun <reified T> put(path: String, body: JsonElement? = null): T? =
        decode(raw("PUT", path, body ?: JsonObject(emptyMap())), serializer<T>())

    suspend inline fun <reified T> patch(path: String, body: JsonElement? = null): T? =
        decode(raw("PATCH", path, body ?: JsonObject(emptyMap())), serializer<T>())

    /** body 는 선택 — 회원 탈퇴(DELETE /v1/users)처럼 사유를 같이 보내야 할 때만 넘겨요 */
    suspend inline fun <reified T> delete(path: String, body: JsonElement? = null): T? =
        decode(raw("DELETE", path, body), serializer<T>())

    /** 메서드를 직접 고를 때 (iOS API.request(method, path, body:)) */
    suspend inline fun <reified T> request(method: String, path: String, body: JsonElement?): T? =
        decode(raw(method, path, body), serializer<T>())

    /** @Serializable 객체를 body 로 보낼 때 (iOS API.request(_:_:encodable:)) */
    suspend inline fun <reified T, reified B> requestEncodable(method: String, path: String, body: B): T? =
        decode(raw(method, path, NJson.encodeToJsonElement(serializer<B>(), body)), serializer<T>())

    /** 응답 data 를 쓰지 않는 요청 (완료 처리, 삭제 등) */
    suspend fun send(method: String, path: String, body: JsonElement? = null) {
        val defaultBody: JsonElement? = if (method == "GET" || method == "DELETE") null else JsonObject(emptyMap())
        raw(method, path, body ?: defaultBody)
    }

    /** data(JsonElement) → T. 실패하면 ApiError.Decoding */
    fun <T> decode(data: JsonElement?, serializer: KSerializer<T>): T? {
        if (data == null || data is JsonNull) return null
        return try {
            NJson.decodeFromJsonElement(serializer, data)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "❌ 디코딩 실패 → ${serializer.descriptor.serialName}: $e")
            // 디코딩 실패 = 앱이 서버보다 옛 버전일 가능성 (api.error · fail_reason=decoding)
            AnalyticsService.track(
                AnalyticsEvent.apiError,
                "error_status" to 0, "fail_reason" to "decoding", "requested_path" to serializer.descriptor.serialName,
            )
            throw ApiError.Decoding(e.toString())
        }
    }

    // MARK: 내부 구현

    private fun makeRequest(method: String, path: String, body: JsonElement?, token: String?): Request {
        val url = "$baseUrl/api$path"
        val builder = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw ApiError.InvalidUrl
        }
        val requestBody = body?.let { NJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_MEDIA) }
        builder.method(method, requestBody)
        builder.header("Cache-Control", "no-cache")
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    private suspend fun perform(req: Request): Pair<String, Int> = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { res -> (res.body?.string() ?: "") to res.code }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Network
        }
    }

    /** 공통 요청. 성공하면 응답의 data(JsonElement) 를 돌려줘요. 실패하면 api.error 를 남기고 그대로 다시 던져요 */
    suspend fun raw(method: String, path: String, body: JsonElement?): JsonElement? =
        try {
            rawRequest(method, path, body)
        } catch (e: ApiError) {
            // 둘러보기 막힘 · 세션 만료(401)는 따로 세요
            if (!e.isGuestLoginRequired && e.httpStatus != 401) reportError(e, path)
            throw e
        }

    private fun reportError(e: ApiError, path: String) {
        val status = e.httpStatus
        val reason = when {
            e is ApiError.Network -> "network"
            e is ApiError.Decoding -> "decoding"
            status == 403 -> "plan_limit"
            status >= 500 -> "server"
            else -> "client"
        }
        AnalyticsService.track(
            AnalyticsEvent.apiError,
            "error_status" to status, "fail_reason" to reason, "requested_path" to AnalyticsService.analyticsPath(path),
        )
    }

    private suspend fun rawRequest(method: String, path: String, body: JsonElement?): JsonElement? {
        // 둘러보기(로그인 전)는 읽기만 — 저장 · 수정 · 삭제 · AI 는 보내기 전에 막고 로그인 안내를 띄워요
        // (서버도 같은 규칙으로 401 GUEST_LOGIN_REQUIRED 를 줘요)
        if (method != "GET" && TokenManager.isGuest) {
            withContext(Dispatchers.Main) { LoginPrompt.request("save_blocked", AnalyticsService.analyticsPath(path)) }
            throw ApiError.guestLoginRequired
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "➡️ $method $path")
        var (text, status) = perform(makeRequest(method, path, body, TokenManager.accessToken))

        // 401(인증 만료)만 토큰 갱신 대상. 403 은 플랜 제한 같은 정상적인 비즈니스 에러라 제외
        if (status == 401) {
            if (parseEnvelope(text)?.status == ApiError.GUEST_STATUS) {
                withContext(Dispatchers.Main) { LoginPrompt.request("guest_401", AnalyticsService.analyticsPath(path)) }
                throw ApiError.guestLoginRequired
            }
            when (TokenRefreshService.refreshToken()) {
                TokenRefreshResult.REFRESHED -> {
                    val retry = perform(makeRequest(method, path, body, TokenManager.accessToken))
                    text = retry.first
                    status = retry.second
                }
                // 서버가 잠깐 안 될 뿐이에요 — 로그아웃시키지 않고 이번 요청만 실패로
                TokenRefreshResult.UNAVAILABLE -> throw ApiError.Network
                TokenRefreshResult.INVALID -> Unit
            }
            if (status == 401) {
                withContext(Dispatchers.Main) {
                    // 재발급이 거절됨 → 안내 없이 로그인 화면으로 가요 (session.expired)
                    AnalyticsService.track(AnalyticsEvent.sessionExpired, "requested_path" to AnalyticsService.analyticsPath(path))
                    AuthService.logout(AuthService.LogoutReason.sessionExpired)
                }
                throw ApiError.Unauthorized
            }
        }

        if (status == 204 || text.isBlank()) {
            if (status in 200..299) return null
            throw ApiError.Http(status, "문제가 생겼어요. 다시 시도해 주세요.", null, null)
        }

        // 먼저 공통 껍데기(success/message/status)만 읽어요
        val envelope = parseEnvelope(text)
        val ok = status in 200..299 && (envelope?.success ?: false)
        if (!ok) {
            if (BuildConfig.DEBUG) Log.e(TAG, "❌ $status $method $path: $text")
            throw ApiError.Http(
                status = status,
                message = envelope?.message ?: "문제가 생겼어요. 다시 시도해 주세요.",
                statusName = envelope?.status,
                data = envelope?.data,
            )
        }
        return envelope?.data
    }

    private fun parseEnvelope(text: String): Envelope? =
        runCatching { NJson.decodeFromString(Envelope.serializer(), text) }.getOrNull()
}
