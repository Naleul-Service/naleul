// Features/CalendarImport/CalendarImportViewModel.swift
//
// 웹 원본: features/external-calendar/ui/CalendarImportView.tsx (GoogleImport 의 상태 · 흐름)
// 예전 iOS: Features/Setting/ViewModel/ExternalCalendarImportViewModel.swift (ObservableObject → @Observable 로)
//
// 흐름: 가져올 곳 고르기(구글 캘린더 · 아이폰 캘린더) → 구글 계정 연결 / 캘린더 접근 허용 →
//       캘린더 · 기간 · 목표 고르기 → "일정 불러오기" → 일정 고르기 → "N개 Task로 가져오기" → 결과

import Foundation
import Observation

/// 날짜별로 묶은 일정 (화면용)
struct CalendarImportDay: Identifiable {
    var id: String { date }
    /// "YYYY-MM-DD" (없으면 "")
    let date: String
    let events: [CalendarImportEvent]
}

@MainActor
@Observable
final class CalendarImportViewModel {
    enum Access { case unknown, granted, denied }

    /// 가져올 곳 — server: 서버 ExternalCalendarSource · analytics: import_source (아이폰 캘린더는 웹과 같은 APPLE)
    enum Source: String {
        case google, device
        var server: String { self == .google ? "GOOGLE" : "APPLE" }
        var analytics: String { server }
    }

    /// nil = 아직 안 고름 (웹처럼 먼저 고르게)
    var source: Source?
    /// 구글 연결 중 (로그인 창 · 캘린더 목록)
    var googleConnecting = false
    /// 구글 access token (1시간). 만료되면 nil → 다시 연결
    private var googleToken: String?

    var access: Access = .unknown
    /// calendar_access 값 (unknown · granted · denied)
    var accessKey: String {
        switch access {
        case .unknown: return "unknown"
        case .granted: return "granted"
        case .denied: return "denied"
        }
    }
    /// range_days — 고른 기간의 날 수 (양 끝 포함)
    var rangeDays: Int { KST.diffDays(max(from, today), max(to, from)) + 1 }
    var calendars: [CalendarImportDeviceCalendar] = []
    var selectedCalendarIds: Set<String> = []
    /// 시작일 · 종료일 ("YYYY-MM-DD", 한국 날짜). 기본: 오늘 ~ 4주 뒤
    var from: String
    var to: String
    var goals: [GoalCategory] = []
    var selectedGoalId: Int?
    /// nil = 아직 안 불러옴
    var events: [CalendarImportEvent]?
    var pickedEventIds: Set<String> = []
    var isLoading = false
    var isImporting = false
    var result: CalendarImportResult?
    /// 화면 아래 빨간 문구 (웹 error)
    var errorText: String?

    let today: String
    var maxDate: String { KST.addDays(today, 365) }

    // let 은 @Observable 이 원래 추적하지 않아요 (@ObservationIgnored 를 붙일 필요 없음)
    private let reader: any CalendarImportReading
    private let importer: (String, Int?, [CalendarImportEvent]) async throws -> CalendarImportResult
    private let goalLoader: () async throws -> [GoalCategory]

    init(
        reader: any CalendarImportReading = CalendarImportEventKitReader(),
        importer: @escaping (String, Int?, [CalendarImportEvent]) async throws -> CalendarImportResult = { source, goalId, events in
            try await CalendarImportAPI.importEvents(source: source, goalCategoryId: goalId, events: events)
        },
        goalLoader: @escaping () async throws -> [GoalCategory] = { try await GoalAPI.goals() },
        today: String = KST.today()
    ) {
        self.reader = reader
        self.importer = importer
        self.goalLoader = goalLoader
        self.today = today
        self.from = today
        self.to = KST.addDays(today, 27)
    }

    // MARK: 진입

    func onAppear() async {
        if source == .device { applyAccess(reader.currentAccess()) }
        if goals.isEmpty {
            // 진행 중 · 시작 전 목표만 연결 대상으로 (임시 목표는 빼요 — 웹과 같아요)
            let all = (try? await goalLoader()) ?? []
            goals = all.filter { !$0.isTemporary && $0.goalCategoryStatus.isOngoing }
        }
    }

    /// 설정 앱에서 허용을 켜고 돌아왔을 때
    func refreshAccess() {
        if source == .device { applyAccess(reader.currentAccess()) }
    }

    /// 가져올 곳 고르기 — 바꾸면 고른 캘린더 · 불러온 목록 · 결과를 비워요
    func selectSource(_ s: Source) {
        ST.track("calendar_import.source_select", ["import_source": s.analytics])
        guard s != source else { return }
        source = s
        calendars = []
        selectedCalendarIds = []
        events = nil
        pickedEventIds = []
        result = nil
        errorText = nil
        access = .unknown
        switch s {
        case .device: applyAccess(reader.currentAccess())
        case .google:
            if let token = googleToken { Task { await loadGoogleCalendars(token: token, sendResult: false) } }
        }
    }

    // MARK: 구글 캘린더 연결

    /// '구글 계정 연결하기' → 로그인 창 → 캘린더 목록
    func connectGoogle(auth: GoogleCalendarAuth) async {
        ST.track("calendar_import.google_connect_click", ["import_source": "GOOGLE"])
        errorText = nil
        googleConnecting = true
        do {
            let token = try await auth.requestAccessToken()
            await loadGoogleCalendars(token: token, sendResult: true)
        } catch {
            googleConnecting = false
            let e = error as? GoogleCalendarError
            errorText = e?.message ?? "구글 연결에 실패했어요."
            ST.track("calendar_import.google_connect_result", [
                "result": "fail", "fail_reason": e?.reason ?? "failed", "import_source": "GOOGLE",
            ])
        }
    }

    private func loadGoogleCalendars(token: String, sendResult: Bool) async {
        googleConnecting = true
        defer { googleConnecting = false }
        do {
            let list = try await GoogleCalendarAPI.calendars(token: token)
            googleToken = token
            guard source == .google else { return }
            calendars = list
            // 처음엔 기본 캘린더만 (웹과 같아요). 기본이 없으면 첫 캘린더
            let primary = list.filter(\.primary)
            selectedCalendarIds = Set((primary.isEmpty ? Array(list.prefix(1)) : primary).map(\.id))
            access = .granted
            if sendResult {
                ST.track("calendar_import.google_connect_result", [
                    "result": "success", "item_count": list.count, "import_source": "GOOGLE",
                ])
            }
        } catch {
            googleToken = nil
            access = .unknown
            let e = error as? GoogleCalendarError
            errorText = e?.message ?? "구글 연결에 실패했어요."
            if sendResult {
                ST.track("calendar_import.google_connect_result", [
                    "result": "fail", "fail_reason": e?.reason ?? "failed", "import_source": "GOOGLE",
                ])
            }
        }
    }

    func requestAccess() async {
        ST.track("calendar_import.access_request_click", ["calendar_access": accessKey])
        do {
            applyAccess(try await reader.requestAccess())
        } catch {
            applyAccess(false)
        }
        ST.track("calendar_import.access_result", ["calendar_access": accessKey])
    }

    private func applyAccess(_ value: Bool?) {
        switch value {
        case .some(true):
            access = .granted
            if calendars.isEmpty {
                calendars = reader.calendars()
                selectedCalendarIds = Set(calendars.map(\.id))
            }
        case .some(false):
            access = .denied
        case .none:
            access = .unknown
        }
    }

    func toggleCalendar(_ id: String) {
        let on = !selectedCalendarIds.contains(id)
        if on { selectedCalendarIds.insert(id) } else { selectedCalendarIds.remove(id) }
        ST.track("calendar_import.calendar_toggle", ["checked": on, "selected_count": selectedCalendarIds.count])
    }

    /// 시작일을 종료일보다 뒤로 고르면 종료일도 같이 밀어요
    func setFrom(_ d: String) {
        let old = from
        from = max(d, today)
        if to < from { to = from }
        if from != old { trackOption("start_date") }
    }

    func setTo(_ d: String) {
        let old = to
        to = min(max(d, from), maxDate)
        if to != old { trackOption("end_date") }
    }

    func setGoal(_ id: Int?) {
        guard id != selectedGoalId else { return }
        selectedGoalId = id
        trackOption("goal")
    }

    /// 기간 · 목표를 바꿈 — 이미 불러온 목록은 다시 불러오기 전까지 그대로 남아요
    private func trackOption(_ field: String) {
        ST.track("calendar_import.option_change", [
            "edit_field": field, "range_days": rangeDays, "has_goal": selectedGoalId != nil, "has_list": events != nil,
        ])
    }

    // MARK: 일정 불러오기 · 고르기

    func loadEvents() {
        guard let source, !isLoading else { return }
        ST.track("calendar_import.load_click", [
            "selected_count": selectedCalendarIds.count, "range_days": rangeDays, "has_goal": selectedGoalId != nil,
            "import_source": source.analytics,
        ])
        errorText = nil
        result = nil
        // 지난 일정은 "못 한 일"로 잡혀 실행률이 틀어져서 오늘부터
        let start = max(from, today)
        let end = max(to, start)
        if source == .google {
            Task { await loadGoogleEvents(from: start, to: end) }
            return
        }
        isLoading = true
        defer { isLoading = false }
        showLoaded(reader.events(calendarIds: selectedCalendarIds, from: start, to: end))
    }

    private func loadGoogleEvents(from start: String, to end: String) async {
        guard let token = googleToken else {
            access = .unknown
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let chosen = calendars.filter { selectedCalendarIds.contains($0.id) }
            showLoaded(try await GoogleCalendarAPI.events(token: token, calendars: chosen, from: start, to: end))
        } catch {
            let e = error as? GoogleCalendarError
            // 구글 연결 만료(1시간) → 다시 연결 화면으로
            if e?.reason == "expired" {
                googleToken = nil
                access = .unknown
            }
            errorText = e?.message ?? "일정을 불러오지 못했어요."
            ST.track("calendar_import.load_result", ["result": "fail", "fail_reason": e?.reason ?? "failed"])
        }
    }

    private func showLoaded(_ raw: [CalendarImportEvent]) {
        let list = raw.sorted { ($0.date ?? "", $0.start ?? "") < ($1.date ?? "", $1.start ?? "") }
        events = list
        // 하루 종일 일정(생일 · 휴가 등)은 할 일이 아닌 경우가 많아서 처음엔 빼 둬요
        pickedEventIds = Set(list.filter { !$0.allDay }.map(\.externalId))
        ST.track("calendar_import.load_result", [
            "result": "success", "event_count": list.count, "all_day_count": list.filter(\.allDay).count,
        ])
    }

    func toggleEvent(_ id: String) {
        let on = !pickedEventIds.contains(id)
        if on { pickedEventIds.insert(id) } else { pickedEventIds.remove(id) }
        ST.track("calendar_import.event_toggle", [
            "checked": on, "is_all_day": events?.first { $0.externalId == id }?.allDay ?? false,
            "selected_count": pickedEventIds.count,
        ])
    }

    var allPicked: Bool {
        guard let events, !events.isEmpty else { return false }
        return events.allSatisfy { pickedEventIds.contains($0.externalId) }
    }

    func toggleAll() {
        guard let events else { return }
        let checked = !allPicked
        ST.track("calendar_import.select_all_click", ["checked": checked, "event_count": events.count])
        pickedEventIds = checked ? Set(events.map(\.externalId)) : []
    }

    /// 날짜별로 묶은 일정 (불러온 순서 = 날짜 · 시각 순)
    var eventsByDay: [CalendarImportDay] {
        var order: [String] = []
        var map: [String: [CalendarImportEvent]] = [:]
        for e in events ?? [] {
            let k = e.date ?? ""
            if map[k] == nil { order.append(k) }
            map[k, default: []].append(e)
        }
        return order.map { CalendarImportDay(date: $0, events: map[$0] ?? []) }
    }

    // MARK: 가져오기

    func importPicked() async {
        guard let events, let source, !isImporting else { return }
        let picked = events.filter { pickedEventIds.contains($0.externalId) }
        guard !picked.isEmpty else { return }
        ST.track("calendar_import.import_click", [
            "selected_count": picked.count, "all_day_count": picked.filter(\.allDay).count,
            "has_goal": selectedGoalId != nil, "range_days": rangeDays, "import_source": source.analytics,
        ])
        isImporting = true
        errorText = nil
        defer { isImporting = false }
        do {
            let r = try await importer(source.server, selectedGoalId, picked)
            result = r
            self.events = nil
            ST.track("calendar_import.import_result", [
                "result": "success", "created_count": r.created,
                "skipped_count": r.skippedDuplicate + r.skippedPast + r.skippedInvalid,
            ])
        } catch is CancellationError {
            // 화면을 떠났어요
        } catch {
            ST.track("calendar_import.import_result", ["result": "fail", "error_status": ST.status(error)])
            errorText = errorMessage(error, fallback: "일정을 가져오지 못했어요.")
        }
        // 웹 onSettled: 캘린더 · 목표 다시 불러오기
        Invalidation.shared.invalidate(.timetable, .goals)
    }
}
