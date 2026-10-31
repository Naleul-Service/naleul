// feature/calendarimport/CalendarImportModels.kt
//
// iOS Features/CalendarImport/CalendarImportModels.swift
// 웹 원본: features/external-calendar/types.ts · api.ts
//
// 휴대폰 기본 캘린더의 일정을 읽어서, 고른 것만 서버 POST /v1/external-calendar/import 로 보내 Task 로 만들어요.
// 왜 서버가 직접 읽지 않나: 휴대폰 캘린더는 기기 안에서만 읽을 수 있어요 (웹 구글 연동도 같은 API 를 써요).
//  - iOS: EventKit
//  - Android: CalendarContract (캘린더 앱이 같이 쓰는 "캘린더 저장소"). 구글 캘린더 · 삼성 캘린더 · Outlook 등
//    휴대폰에 동기화된 계정 일정이 모두 들어 있어요. READ_CALENDAR 권한이 필요해요.
//
// 날짜/시간은 앱 전체 규칙대로 "한국 시간 벽시계" 문자열로 보내요 (KST.*).
//  - 시간 일정: start/end = "yyyy-MM-ddTHH:mm:ss" (어느 나라에서 만든 일정이든 한국 시간으로)
//  - 하루 종일 일정: date 만 (Android 는 하루 종일 일정을 UTC 0시로 저장해서 UTC 날짜로 읽어요 — 하루가 밀리지 않게)

package com.naleul.naleul.feature.calendarimport

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

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import java.util.Date
import java.util.UUID

// MARK: - 서버 요청 · 응답

/** 서버로 보내는 외부 일정 1건 (웹 ExternalEvent · 백엔드 ExternalEventImportRequest.Event) */
@Serializable
data class CalendarImportEvent(
    /** 외부 캘린더의 일정 id — 반복 일정은 회차마다 달라요 ("외부id|시작시각"). 다시 가져올 때 중복을 막아요 (최대 255자) */
    val externalId: String,
    /** 최대 200자 */
    val title: String,
    /** 어느 캘린더에서 왔는지 (최대 100자) */
    val calendarName: String? = null,
    val allDay: Boolean,
    val start: String? = null,
    val end: String? = null,
    /** 그 일정의 날짜 (화면에서 날짜별로 묶을 때도 써요) */
    val date: String? = null,
) {
    val id: String get() = externalId

    companion object {}
}

/** 서버로 보내는 묶음 (source 는 CalendarImportAPI.source) */
@Serializable
private data class CalendarImportRequest(
    val source: String,
    val goalCategoryId: Int? = null,
    val events: List<CalendarImportEvent>,
)

/** 웹 ImportResult (백엔드 ExternalEventImportResponse) */
@Serializable
data class CalendarImportResult(
    /** 새로 만든 Task 수 */
    val created: Int = 0,
    /** 이미 가져왔거나 같은 이름 · 같은 시작 시각의 Task 가 이미 있어서 건너뜀 */
    val skippedDuplicate: Int = 0,
    /** 오늘 이전 일정이라 건너뜀 */
    val skippedPast: Int = 0,
    /** 날짜 · 시간이 비었거나 맞지 않아 건너뜀 */
    val skippedInvalid: Int = 0,
) {
    /** 300개씩 나눠 보낸 결과 합치기 */
    operator fun plus(b: CalendarImportResult): CalendarImportResult = CalendarImportResult(
        created = created + b.created,
        skippedDuplicate = skippedDuplicate + b.skippedDuplicate,
        skippedPast = skippedPast + b.skippedPast,
        skippedInvalid = skippedInvalid + b.skippedInvalid,
    )

    companion object {
        val zero = CalendarImportResult(created = 0, skippedDuplicate = 0, skippedPast = 0, skippedInvalid = 0)
    }
}

object CalendarImportAPI {
    /** 서버 한 번 요청 한도 (ExternalEventImportRequest @Size(max = 300)) */
    private const val chunkSize = 300

    /**
     * 고른 일정을 Task 로 만들어요. 300개가 넘으면 나눠서 보내고 결과를 합쳐요 (웹 useImportExternalEvents)
     * 끝나면(성공·실패 모두) timetable · goals 를 다시 불러와요 — 화면(ViewModel)에서 해요
     * @param source 서버 ExternalCalendarSource — 구글 캘린더 "GOOGLE" · 휴대폰 캘린더 "APPLE"
     *               (백엔드 enum 이 GOOGLE · APPLE 뿐이라 휴대폰 캘린더는 iOS 와 같은 값. ANDROID 를 추가하면 여기만)
     */
    suspend fun importEvents(source: String, goalCategoryId: Int?, events: List<CalendarImportEvent>): CalendarImportResult {
        var total = CalendarImportResult.zero
        for (chunk in events.chunked(chunkSize)) {
            val body = CalendarImportRequest(source = source, goalCategoryId = goalCategoryId, events = chunk)
            val r: CalendarImportResult? = API.requestEncodable<CalendarImportResult, CalendarImportRequest>(
                "POST", "/v1/external-calendar/import", body,
            )
            if (r != null) total = total + r
        }
        return total
    }
}

// MARK: - 휴대폰 캘린더 (CalendarContract 에서 읽은 값)

data class CalendarImportDeviceCalendar(
    val id: String,
    val title: String,
    /** 계정 이름 ("me@gmail.com", "Samsung account" …) */
    val accountName: String,
    val color: Color,
    /** 구글 캘린더의 기본 캘린더 (처음엔 이것만 켜요 — 웹과 같아요) */
    val primary: Boolean = false,
) {
    companion object {}
}

/** 휴대폰 기본 캘린더 읽기. 미리보기에서는 CalendarImportMockReader 로 바꿔 끼워요 */
interface CalendarImportReading {
    /** true: 허용됨 · false: 거절됨 · null: 아직 묻지 않음 */
    fun currentAccess(): Boolean?

    /**
     * 권한 창을 띄웠다는 표시 (Android 는 "아직 안 물어봄"과 "거절함"을 시스템이 구분해 주지 않아서 직접 기억해요).
     * iOS requestAccess() 의 권한 창은 화면(CalendarImportScreen)의 권한 요청 런처가 띄워요.
     */
    fun didRequestAccess() {}

    fun calendars(): List<CalendarImportDeviceCalendar>

    /** from ~ to (둘 다 포함, "YYYY-MM-DD" 한국 날짜) 사이 일정 */
    fun events(calendarIds: Set<String>, from: String, to: String): List<CalendarImportEvent>
}

/**
 * CalendarContract 로 읽어요.
 * 휴대폰 캘린더 앱에 보이는 모든 계정(구글 · 삼성 · Outlook)의 일정이 들어 있어서,
 * 노션 캘린더에 연결한 구글 계정의 일정도 여기서 같이 가져올 수 있어요.
 */
class CalendarImportContentReader(context: Context) : CalendarImportReading {
    private val context: Context = context.applicationContext
    private val prefs by lazy { this.context.getSharedPreferences("naleul_prefs", Context.MODE_PRIVATE) }

    override fun currentAccess(): Boolean? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        return if (prefs.getBoolean(REQUESTED_KEY, false)) false else null
    }

    override fun didRequestAccess() {
        prefs.edit().putBoolean(REQUESTED_KEY, true).apply()
    }

    override fun calendars(): List<CalendarImportDeviceCalendar> {
        val out = mutableListOf<CalendarImportDeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        CalendarImportDeviceCalendar(
                            id = c.getLong(0).toString(),
                            title = c.getString(1) ?: "",
                            accountName = c.getString(2) ?: "",
                            color = if (c.isNull(3)) Color(0xFF8B9099) else Color(c.getInt(3)),
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // 권한이 없으면 빈 목록
        }
        return out.sortedWith(compareBy({ it.accountName }, { it.title }))
    }

    override fun events(calendarIds: Set<String>, from: String, to: String): List<CalendarImportEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        // 한국 시간 from 0시 ~ to 다음 날 0시
        val startMillis = KST.date(fromYmd = from).time
        val endMillis = KST.date(fromYmd = KST.addDays(to, 1)).time
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMillis)
            ContentUris.appendId(it, endMillis)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,          // 0
            CalendarContract.Instances.TITLE,             // 1
            CalendarContract.Instances.BEGIN,             // 2
            CalendarContract.Instances.END,               // 3
            CalendarContract.Instances.ALL_DAY,           // 4
            CalendarContract.Instances.CALENDAR_ID,       // 5
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME, // 6
            CalendarContract.Instances.STATUS,            // 7
            CalendarContract.Instances.SELF_ATTENDEE_STATUS, // 8
            CalendarContract.Instances.UID_2445,          // 9
        )
        val ids = calendarIds.toList()
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }})"

        data class Row(val begin: Long, val event: CalendarImportEvent)
        val rows = mutableListOf<Row>()
        try {
            context.contentResolver.query(uri, projection, selection, ids.toTypedArray(), "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    // 취소된 일정은 빼요
                    if (!c.isNull(7) && c.getInt(7) == CalendarContract.Events.STATUS_CANCELED) continue
                    // 내가 거절한 초대 일정은 빼요
                    if (!c.isNull(8) && c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                    val begin = c.getLong(2)
                    val endRaw = if (c.isNull(3)) begin else c.getLong(3)
                    val uid = if (c.isNull(9)) null else c.getString(9)
                    val base = uid?.takeIf { it.isNotEmpty() }
                        ?: (if (c.isNull(0)) null else "android:${c.getLong(0)}")
                        ?: UUID.randomUUID().toString()
                    // 반복 일정은 회차마다 같은 id 라서 시작 시각을 붙여 회차를 구분해요
                    val key = "$base|${KST.dateTime(fromDate = Date(begin))}"
                    val rawTitle = (c.getString(1) ?: "").trim()
                    val title = rawTitle.ifEmpty { "(제목 없음)" }.take(200)
                    val calendarName = c.getString(6)?.take(100)
                    val allDay = !c.isNull(4) && c.getInt(4) == 1
                    val event = if (allDay) {
                        CalendarImportEvent(
                            externalId = key.take(255), title = title, calendarName = calendarName,
                            allDay = true, start = null, end = null,
                            // 하루 종일 일정은 UTC 0시로 저장돼 있어요
                            date = KST.ymdFromUtcMillis(begin),
                        )
                    } else {
                        val s = KST.dateTime(fromDate = Date(begin))
                        CalendarImportEvent(
                            externalId = key.take(255), title = title, calendarName = calendarName,
                            allDay = false, start = s, end = KST.dateTime(fromDate = Date(endRaw)),
                            date = s.take(10),
                        )
                    }
                    rows.add(Row(begin, event))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return rows.sortedBy { it.begin }.map { it.event }
    }

    companion object {
        private const val REQUESTED_KEY = "calendar-import-permission-requested"
    }
}

// MARK: - 미리보기용

class CalendarImportMockReader(var access: Boolean? = true) : CalendarImportReading {
    override fun currentAccess(): Boolean? = access

    override fun didRequestAccess() {
        access = true
    }

    override fun calendars(): List<CalendarImportDeviceCalendar> = listOf(
        CalendarImportDeviceCalendar(id = "c1", title = "개인", accountName = "me@gmail.com", color = colorHex("#3D5AFE")),
        CalendarImportDeviceCalendar(id = "c2", title = "회사", accountName = "work@outlook.com", color = colorHex("#12B76A")),
    )

    override fun events(calendarIds: Set<String>, from: String, to: String): List<CalendarImportEvent> {
        val d1 = KST.addDays(from, 1)
        val d2 = KST.addDays(from, 2)
        val d4 = KST.addDays(from, 4)
        return listOf(
            CalendarImportEvent(externalId = "e1", title = "주간 회의", calendarName = "회사", allDay = false,
                start = "${d1}T10:00:00", end = "${d1}T11:00:00", date = d1),
            CalendarImportEvent(externalId = "e2", title = "치과 예약", calendarName = "개인", allDay = false,
                start = "${d2}T14:00:00", end = "${d2}T14:30:00", date = d2),
            CalendarImportEvent(externalId = "e3", title = "엄마 생신", calendarName = "개인", allDay = true,
                start = null, end = null, date = d4),
        )
    }

    companion object {}
}
