// feature/calendarimport/GoogleCalendarReader.kt
//
// 구글 캘린더 읽기 (앱에서 직접) — 웹 features/external-calendar/google.ts 와 같은 방식
//  - 화면이 Google Play 서비스 AuthorizationClient 로 "캘린더 보기" 권한만 받아 1시간짜리 access token 을 넘겨줘요.
//  - 그 토큰으로 Google Calendar API 를 바로 읽고 버려요 (서버에 구글 토큰 · 비밀키를 저장하지 않아요).
//  - 고른 일정만 서버 POST /v1/external-calendar/import (source = GOOGLE) 로 보내요.
// 필요한 설정: Google Cloud 의 Android OAuth 클라이언트 (패키지 이름 + 서명 SHA-1). 코드에 넣을 키는 없어요.

package com.naleul.naleul.feature.calendarimport

import com.naleul.naleul.core.util.KST
import com.naleul.naleul.designsystem.colorHex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 화면에 보여줄 실패 이유 (분석 fail_reason: access_denied · popup_closed · expired · failed — 웹과 같은 값) */
class GoogleCalendarException(val reason: String, message: String) : Exception(message)

/** 디스크 · 네트워크 작업이라 IO 스레드에서 불러요 (ViewModel 이 Dispatchers.IO 로 감싸요) */
class GoogleCalendarReader(private val accessToken: String) : CalendarImportReading {

    @Serializable
    private data class CalendarList(val items: List<Item> = emptyList()) {
        @Serializable
        data class Item(
            val id: String,
            val summary: String = "",
            val summaryOverride: String? = null,
            val backgroundColor: String? = null,
            val primary: Boolean = false,
        )
    }

    @Serializable
    private data class Events(val items: List<Item> = emptyList(), val nextPageToken: String? = null) {
        @Serializable
        data class Item(
            val id: String,
            val status: String? = null,
            val summary: String? = null,
            val start: Time? = null,
            val end: Time? = null,
            val attendees: List<Attendee>? = null,
        )

        @Serializable
        data class Time(val date: String? = null, val dateTime: String? = null)

        @Serializable
        data class Attendee(val self: Boolean = false, val responseStatus: String? = null)
    }

    /** 캘린더 이름 (일정 → calendarName) */
    private val names = mutableMapOf<String, String>()

    override fun currentAccess(): Boolean = true

    override fun calendars(): List<CalendarImportDeviceCalendar> {
        val r: CalendarList = get(API.toHttpUrl().newBuilder()
            .addPathSegments("users/me/calendarList")
            .addQueryParameter("minAccessRole", "reader")
            .addQueryParameter("maxResults", "250")
            .build())
        return r.items.map {
            val name = it.summaryOverride?.takeIf { s -> s.isNotBlank() } ?: it.summary
            names[it.id] = name
            CalendarImportDeviceCalendar(
                id = it.id,
                title = name,
                accountName = "Google",
                color = colorHex(it.backgroundColor ?: "#0F7A9C"),
                primary = it.primary,
            )
        }.sortedByDescending { it.primary }
    }

    override fun events(calendarIds: Set<String>, from: String, to: String): List<CalendarImportEvent> {
        val out = mutableListOf<CalendarImportEvent>()
        val timeMin = "${from}T00:00:00+09:00"
        val timeMax = "${KST.addDays(to, 1)}T00:00:00+09:00"
        for (calId in calendarIds) {
            var pageToken: String? = null
            do {
                val url = API.toHttpUrl().newBuilder()
                    .addPathSegment("calendars").addPathSegment(calId).addPathSegment("events")
                    .addQueryParameter("timeMin", timeMin)
                    .addQueryParameter("timeMax", timeMax)
                    .addQueryParameter("singleEvents", "true")   // 반복 일정을 회차별로 펼쳐서
                    .addQueryParameter("orderBy", "startTime")
                    .addQueryParameter("maxResults", "250")
                    .addQueryParameter("timeZone", "Asia/Seoul")
                    .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                    .build()
                val r: Events = get(url)
                for (e in r.items) toEvent(e, calId)?.let { out.add(it) }
                pageToken = r.nextPageToken
            } while (pageToken != null && out.size < 1000)
        }
        return out
    }

    /** 취소됐거나 내가 거절한 일정은 null (웹 toExternalEvent) */
    private fun toEvent(e: Events.Item, calId: String): CalendarImportEvent? {
        if (e.status == "cancelled") return null
        if (e.attendees?.any { it.self && it.responseStatus == "declined" } == true) return null
        val title = (e.summary ?: "").trim().ifEmpty { "(제목 없음)" }.take(200)
        // 반복 일정은 singleEvents=true 로 회차마다 id 가 달라요 (웹과 같은 externalId 라 웹 · 앱 어디서 가져와도 중복을 막아요)
        val externalId = "$calId:${e.id}".take(255)
        val calendarName = names[calId]?.take(100)
        val allDayDate = e.start?.date
        if (allDayDate != null) {
            return CalendarImportEvent(externalId = externalId, title = title, calendarName = calendarName,
                allDay = true, start = null, end = null, date = allDayDate)
        }
        val s = e.start?.dateTime?.let(::toKst) ?: return null
        val en = e.end?.dateTime?.let(::toKst) ?: return null
        return CalendarImportEvent(externalId = externalId, title = title, calendarName = calendarName,
            allDay = false, start = s, end = en, date = s.take(10))
    }

    private inline fun <reified T> get(url: HttpUrl): T {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
        http.newCall(req).execute().use { res ->
            if (res.code == 401) throw GoogleCalendarException("expired", "구글 연결이 만료됐어요. 다시 연결해 주세요.")
            if (!res.isSuccessful) {
                // 원인을 알 수 있게 구글 오류 코드 · 이유를 붙여요 (예: 403 insufficientPermissions · accessNotConfigured)
                val body = res.body?.string().orEmpty()
                val why = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?: Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                throw GoogleCalendarException("failed", "구글 캘린더를 불러오지 못했어요. (${res.code}${why?.let { " $it" } ?: ""})")
            }
            return json.decodeFromString(res.body?.string() ?: "{}")
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/calendar/v3"
        private val json = Json { ignoreUnknownKeys = true }
        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val WALL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /** "2026-10-09T10:00:00+09:00" / "...Z" → 한국 시간 벽시계 "2026-10-09T10:00:00" */
        fun toKst(iso: String): String? = runCatching {
            OffsetDateTime.parse(iso).atZoneSameInstant(SEOUL).format(WALL)
        }.getOrNull()

        /** 앱이 받는 권한 — 캘린더 보기만 */
        /** 캘린더 목록 보기 + 일정 보기만 (calendar.readonly 보다 좁은 범위 — 구글 OAuth 범위 심사) */
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly",
            "https://www.googleapis.com/auth/calendar.events.readonly",
        )
    }
}
