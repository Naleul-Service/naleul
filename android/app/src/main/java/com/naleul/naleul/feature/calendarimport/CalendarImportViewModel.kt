// feature/calendarimport/CalendarImportViewModel.kt
//
// iOS Features/CalendarImport/CalendarImportViewModel.swift
// 웹 원본: features/external-calendar/ui/CalendarImportView.tsx (GoogleImport 의 상태 · 흐름)
//
// 흐름: 가져올 곳 고르기(휴대폰 캘린더 · 구글 캘린더) → 캘린더 접근 허용 / 구글 계정 연결 →
//       캘린더 · 기간 · 목표 고르기 → "일정 불러오기" → 일정 고르기 → "N개 Task로 가져오기" → 결과

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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 날짜별로 묶은 일정 (화면용) */
data class CalendarImportDay(
    /** "YYYY-MM-DD" (없으면 "") */
    val date: String,
    val events: List<CalendarImportEvent>,
) {
    val id: String get() = date

    companion object {}
}

class CalendarImportViewModel(
    /** 휴대폰 캘린더 (CalendarContract) */
    private val reader: CalendarImportReading = CalendarImportContentReader(AppContext.app),
    private val importer: suspend (String, Int?, List<CalendarImportEvent>) -> CalendarImportResult = { source, goalId, events ->
        CalendarImportAPI.importEvents(source = source, goalCategoryId = goalId, events = events)
    },
    /** 구글 access token → 캘린더 읽기 (미리보기에서는 바꿔 끼워요) */
    private val googleReaderFactory: (String) -> CalendarImportReading = { GoogleCalendarReader(it) },
    private val goalLoader: suspend () -> List<GoalCategory> = { GoalAPI.goals() },
    val today: String = KST.today(),
) : ScreenModel() {
    enum class Access { unknown, granted, denied }

    /**
     * 가져올 곳
     * @param server  서버 ExternalCalendarSource 값
     * @param analytics import_source 값 (웹은 GOOGLE · APPLE, 앱의 휴대폰 캘린더는 DEVICE)
     */
    enum class Source(val server: String, val analytics: String) {
        device("APPLE", "DEVICE"),
        google("GOOGLE", "GOOGLE"),
    }

    /** null = 아직 안 고름 (웹처럼 먼저 고르게) */
    var source by mutableStateOf<Source?>(null)
    /** 구글 연결 중 (권한 창 · 캘린더 목록) */
    var googleConnecting by mutableStateOf(false)
    private var googleReader: CalendarImportReading? = null
    /** 지금 고른 곳의 읽기 */
    private val activeReader: CalendarImportReading?
        get() = when (source) {
            Source.device -> reader
            Source.google -> googleReader
            null -> null
        }

    var access by mutableStateOf(Access.unknown)
    var calendars by mutableStateOf<List<CalendarImportDeviceCalendar>>(emptyList())
    var selectedCalendarIds by mutableStateOf<Set<String>>(emptySet())
    /** 시작일 · 종료일 ("YYYY-MM-DD", 한국 날짜). 기본: 오늘 ~ 4주 뒤 */
    @set:JvmName("assignFrom")
    var from by mutableStateOf(today)
    @set:JvmName("assignTo")
    var to by mutableStateOf(KST.addDays(today, 27))
    var goals by mutableStateOf<List<GoalCategory>>(emptyList())
    var selectedGoalId by mutableStateOf<Int?>(null)
    /** null = 아직 안 불러옴 */
    var events by mutableStateOf<List<CalendarImportEvent>?>(null)
    var pickedEventIds by mutableStateOf<Set<String>>(emptySet())
    var isLoading by mutableStateOf(false)
    var isImporting by mutableStateOf(false)
    var result by mutableStateOf<CalendarImportResult?>(null)
    /** 화면 아래 빨간 문구 (웹 error) */
    var errorText by mutableStateOf<String?>(null)

    val maxDate: String get() = KST.addDays(today, 365)

    // MARK: Amplitude (설정 분류 calendar_import.*)
    /** 미리보기에서는 보내지 않아요 (화면이 previewModel 일 때 true) */
    var quiet = false
    private fun track(event: String, props: EventProps = emptyMap()) = ST.track(event, props, quiet)
    /** calendar_access 값 (unknown · granted · denied) */
    val accessKey: String get() = access.name
    private val rangeDays: Int get() = runCatching { KST.diffDays(from, to) + 1 }.getOrDefault(0)
    private var pageviewSent = false

    /** 화면이 처음 그려지고 권한을 확인한 직후 한 번 */
    fun trackPageview() {
        if (pageviewSent) return
        pageviewSent = true
        track("calendar_import.pageview", mapOf("calendar_access" to accessKey))
    }
    fun trackAccessRequest() = track("calendar_import.access_request_click", mapOf("calendar_access" to accessKey))
    fun trackOpenSettings() = track("calendar_import.open_settings_click", mapOf("calendar_access" to accessKey))

    /** 연결할 목표 메뉴 */
    fun selectGoal(id: Int?) {
        if (id == selectedGoalId) return
        selectedGoalId = id
        trackOption("goal")
    }
    private fun trackOption(field: String) = track("calendar_import.option_change", mapOf(
        "edit_field" to field, "range_days" to rangeDays, "has_goal" to (selectedGoalId != null), "has_list" to (events != null),
    ))

    // MARK: 진입

    suspend fun onAppear() {
        if (source == Source.device) applyAccess(reader.currentAccess())
        if (goals.isEmpty()) {
            // 진행 중 · 시작 전 목표만 연결 대상으로 (임시 목표는 빼요 — 웹과 같아요)
            val all = try {
                goalLoader()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            goals = all.filter { !it.isTemporary && it.goalCategoryStatus.isOngoing }
        }
    }

    /** 휴대폰 설정에서 허용을 켜고 돌아왔을 때 (화면이 다시 보일 때) */
    fun refreshAccess() {
        if (source == Source.device) applyAccess(reader.currentAccess())
    }

    /** 가져올 곳 고르기 — 바꾸면 고른 캘린더 · 불러온 목록 · 결과를 비워요 */
    fun selectSource(s: Source) {
        track("calendar_import.source_select", mapOf("import_source" to s.analytics))
        if (s == source) return
        source = s
        calendars = emptyList()
        selectedCalendarIds = emptySet()
        events = null
        pickedEventIds = emptySet()
        result = null
        errorText = null
        when (s) {
            Source.device -> {
                access = Access.unknown
                applyAccess(reader.currentAccess())
            }
            Source.google -> {
                access = Access.unknown
                googleReader?.let { r -> scope.launch { loadGoogleCalendars(r, sendResult = false) } }
            }
        }
    }

    // MARK: 구글 캘린더 연결 (권한 창은 화면이 AuthorizationClient 로 띄워요 — Activity 가 필요해서)

    fun trackGoogleConnect() = track("calendar_import.google_connect_click", mapOf("import_source" to "GOOGLE"))

    /** 권한을 받았어요 → 캘린더 목록 */
    fun onGoogleToken(accessToken: String) {
        val r = googleReaderFactory(accessToken)
        scope.launch { loadGoogleCalendars(r, sendResult = true) }
    }

    /** 권한 창을 닫았거나 거부 · 실패 (reason: access_denied · popup_closed · failed) */
    fun onGoogleError(reason: String, message: String) {
        googleConnecting = false
        errorText = message
        track("calendar_import.google_connect_result", mapOf("result" to "fail", "fail_reason" to reason, "import_source" to "GOOGLE"))
    }

    fun onGoogleConnecting() {
        googleConnecting = true
        errorText = null
    }

    private suspend fun loadGoogleCalendars(r: CalendarImportReading, sendResult: Boolean) {
        googleConnecting = true
        try {
            val list = withContext(Dispatchers.IO) { r.calendars() }
            googleReader = r
            if (source != Source.google) return
            calendars = list
            // 처음엔 기본 캘린더만 (웹과 같아요). 기본이 없으면 첫 캘린더
            selectedCalendarIds = (list.filter { it.primary }.ifEmpty { list.take(1) }).map { it.id }.toSet()
            access = Access.granted
            if (sendResult) track("calendar_import.google_connect_result", mapOf(
                "result" to "success", "item_count" to list.size, "import_source" to "GOOGLE",
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            googleReader = null
            access = Access.unknown
            val reason = (e as? GoogleCalendarException)?.reason ?: "failed"
            errorText = e.message?.takeIf { e is GoogleCalendarException } ?: "구글 연결에 실패했어요."
            if (sendResult) track("calendar_import.google_connect_result", mapOf(
                "result" to "fail", "fail_reason" to reason, "import_source" to "GOOGLE",
            ))
        } finally {
            googleConnecting = false
        }
    }

    /**
     * 권한 창의 결과 (iOS requestAccess() 의 뒤쪽).
     * 권한 창은 화면이 rememberLauncherForActivityResult 로 띄워요 (Activity 가 있어야 띄울 수 있어서).
     */
    fun onAccessResult(granted: Boolean) {
        reader.didRequestAccess()
        applyAccess(if (granted) true else reader.currentAccess() ?: false)
        track("calendar_import.access_result", mapOf("calendar_access" to accessKey))
    }

    private fun applyAccess(value: Boolean?) {
        // [UX] 거부되면 '설정 앱 열기' 카드만 보여요 — 거부 상태로 바뀔 때마다 한 번
        if (value == false && access != Access.denied) track("calendar_import.denied_view", mapOf("calendar_access" to "denied"))
        when (value) {
            true -> {
                access = Access.granted
                if (calendars.isEmpty()) {
                    calendars = reader.calendars()
                    selectedCalendarIds = calendars.map { it.id }.toSet()
                }
            }
            false -> access = Access.denied
            null -> access = Access.unknown
        }
    }

    fun toggleCalendar(id: String) {
        val on = !selectedCalendarIds.contains(id)
        selectedCalendarIds = if (!on) selectedCalendarIds - id else selectedCalendarIds + id
        track("calendar_import.calendar_toggle", mapOf("checked" to on, "selected_count" to selectedCalendarIds.size))
    }

    /** 시작일을 종료일보다 뒤로 고르면 종료일도 같이 밀어요 */
    fun setFrom(d: String) {
        from = maxOf(d, today)
        if (to < from) to = from
        trackOption("start_date")
    }

    fun setTo(d: String) {
        to = minOf(maxOf(d, from), maxDate)
        trackOption("end_date")
    }

    // MARK: 일정 불러오기 · 고르기

    fun loadEvents() {
        if (isLoading) return
        val r = activeReader ?: return
        track("calendar_import.load_click", mapOf(
            "selected_count" to selectedCalendarIds.size, "range_days" to rangeDays, "has_goal" to (selectedGoalId != null),
            "import_source" to source?.analytics,
        ))
        errorText = null
        result = null
        isLoading = true
        // 지난 일정은 "못 한 일"로 잡혀 실행률이 틀어져서 오늘부터
        val start = maxOf(from, today)
        val end = maxOf(to, start)
        val ids = selectedCalendarIds
        scope.launch {
            try {
                // 캘린더 저장소 읽기는 디스크 작업이라 메인 스레드 밖에서
                val list = withContext(Dispatchers.IO) { r.events(calendarIds = ids, from = start, to = end) }
                    .sortedWith(compareBy({ it.date ?: "" }, { it.start ?: "" }))
                events = list
                // 하루 종일 일정(생일 · 휴가 등)은 할 일이 아닌 경우가 많아서 처음엔 빼 둬요
                pickedEventIds = list.filter { !it.allDay }.map { it.externalId }.toSet()
                track("calendar_import.load_result", mapOf(
                    "result" to "success", "event_count" to list.size, "all_day_count" to list.count { it.allDay },
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleCalendarException) {
                // 구글 연결 만료(1시간) → 다시 연결 화면으로
                if (e.reason == "expired") {
                    googleReader = null
                    access = Access.unknown
                }
                errorText = e.message
                track("calendar_import.load_result", mapOf("result" to "fail", "fail_reason" to e.reason))
            } catch (e: Exception) {
                // 예전엔 잡지 않아 앱이 꺼질 수 있었어요 → 실패 문구로
                errorText = "일정을 불러오지 못했어요."
                track("calendar_import.load_result", mapOf("result" to "fail", "fail_reason" to (e::class.simpleName ?: "error")))
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleEvent(id: String) {
        val on = !pickedEventIds.contains(id)
        pickedEventIds = if (!on) pickedEventIds - id else pickedEventIds + id
        track("calendar_import.event_toggle", mapOf(
            "checked" to on, "is_all_day" to (events?.firstOrNull { it.externalId == id }?.allDay == true),
            "selected_count" to pickedEventIds.size,
        ))
    }

    val allPicked: Boolean
        get() {
            val list = events
            if (list.isNullOrEmpty()) return false
            return list.all { pickedEventIds.contains(it.externalId) }
        }

    fun toggleAll() {
        val list = events ?: return
        val checking = !allPicked
        track("calendar_import.select_all_click", mapOf("checked" to checking, "event_count" to list.size))
        pickedEventIds = if (!checking) emptySet() else list.map { it.externalId }.toSet()
    }

    /** 날짜별로 묶은 일정 (불러온 순서 = 날짜 · 시각 순) */
    val eventsByDay: List<CalendarImportDay>
        get() {
            val map = LinkedHashMap<String, MutableList<CalendarImportEvent>>()
            for (e in events ?: emptyList()) {
                map.getOrPut(e.date ?: "") { mutableListOf() }.add(e)
            }
            return map.map { (k, v) -> CalendarImportDay(date = k, events = v) }
        }

    // MARK: 가져오기

    suspend fun importPicked() {
        val list = events ?: return
        if (isImporting) return
        val picked = list.filter { pickedEventIds.contains(it.externalId) }
        if (picked.isEmpty()) return
        val src = source ?: return
        track("calendar_import.import_click", mapOf(
            "selected_count" to picked.size, "all_day_count" to picked.count { it.allDay },
            "has_goal" to (selectedGoalId != null), "range_days" to rangeDays, "import_source" to src.analytics,
        ))
        isImporting = true
        errorText = null
        try {
            val r = importer(src.server, selectedGoalId, picked)
            result = r
            track("calendar_import.import_result", mapOf(
                "result" to "success", "created_count" to r.created,
                "skipped_count" to (r.skippedDuplicate + r.skippedPast + r.skippedInvalid),
            ))
            events = null
        } catch (e: CancellationException) {
            // 화면을 떠났어요
            isImporting = false
            throw e
        } catch (e: Exception) {
            errorText = errorMessage(e, fallback = "일정을 가져오지 못했어요.")
            track("calendar_import.import_result", mapOf("result" to "fail", "error_status" to ST.status(e)))
        }
        isImporting = false
        // 웹 onSettled: 캘린더 · 목표 다시 불러오기
        Invalidation.invalidate(DataKey.timetable, DataKey.goals)
    }

    companion object {}
}
