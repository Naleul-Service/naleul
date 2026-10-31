// Features/CalendarImport/CalendarImportModels.swift
//
// 웹 원본: features/external-calendar/types.ts · api.ts
// 예전 iOS: Features/Setting/Model/ExternalCalendarModels.swift · API/ExternalCalendarAPI.swift ·
//          ViewModel/DeviceCalendarReader.swift (EventKit 읽기 로직을 그대로 가져왔어요)
//
// 아이폰 기본 캘린더(EventKit)의 일정을 읽어서, 고른 것만 서버 POST /v1/external-calendar/import 로 보내 Task 로 만들어요.
// 왜 서버가 직접 읽지 않나: 아이폰 캘린더는 기기 안에서만 읽을 수 있어요 (웹 구글 연동도 같은 API 를 써요).
//
// 날짜/시간은 앱 전체 규칙대로 "한국 시간 벽시계" 문자열로 보내요 (KST.*).
//  - 시간 일정: start/end = "yyyy-MM-ddTHH:mm:ss" (어느 나라에서 만든 일정이든 한국 시간으로)
//  - 하루 종일 일정: date 만 (기기 시간대의 그 날짜 — 시간대를 바꿔도 하루가 밀리지 않게)

import EventKit
import SwiftUI

// MARK: - 서버 요청 · 응답

/// 서버로 보내는 외부 일정 1건 (웹 ExternalEvent · 백엔드 ExternalEventImportRequest.Event)
struct CalendarImportEvent: Encodable, Hashable, Identifiable {
    var id: String { externalId }

    /// 외부 캘린더의 일정 id — 반복 일정은 회차마다 달라요 ("외부id|시작시각"). 다시 가져올 때 중복을 막아요 (최대 255자)
    let externalId: String
    /// 최대 200자
    let title: String
    /// 어느 캘린더에서 왔는지 (최대 100자)
    let calendarName: String?
    let allDay: Bool
    let start: String?
    let end: String?
    /// 그 일정의 날짜 (화면에서 날짜별로 묶을 때도 써요)
    let date: String?

    enum CodingKeys: String, CodingKey {
        case externalId, title, calendarName, allDay, start, end, date
    }
}

/// source — 아이폰 기본 캘린더(EventKit) "APPLE" · 구글 캘린더 "GOOGLE"
private struct CalendarImportRequest: Encodable {
    let source: String
    let goalCategoryId: Int?
    let events: [CalendarImportEvent]
}

/// 웹 ImportResult (백엔드 ExternalEventImportResponse)
struct CalendarImportResult: Decodable, Hashable {
    /// 새로 만든 Task 수
    var created: Int
    /// 이미 가져왔거나 같은 이름 · 같은 시작 시각의 Task 가 이미 있어서 건너뜀
    var skippedDuplicate: Int
    /// 오늘 이전 일정이라 건너뜀
    var skippedPast: Int
    /// 날짜 · 시간이 비었거나 맞지 않아 건너뜀
    var skippedInvalid: Int

    static let zero = CalendarImportResult(created: 0, skippedDuplicate: 0, skippedPast: 0, skippedInvalid: 0)

    /// 300개씩 나눠 보낸 결과 합치기
    static func + (a: CalendarImportResult, b: CalendarImportResult) -> CalendarImportResult {
        CalendarImportResult(
            created: a.created + b.created,
            skippedDuplicate: a.skippedDuplicate + b.skippedDuplicate,
            skippedPast: a.skippedPast + b.skippedPast,
            skippedInvalid: a.skippedInvalid + b.skippedInvalid
        )
    }
}

enum CalendarImportAPI {
    /// 서버 한 번 요청 한도 (ExternalEventImportRequest @Size(max = 300))
    private static let chunkSize = 300

    /// 고른 일정을 Task 로 만들어요. 300개가 넘으면 나눠서 보내고 결과를 합쳐요 (웹 useImportExternalEvents)
    /// 끝나면(성공·실패 모두) .timetable · .goals 를 다시 불러와요 — 화면(ViewModel)에서 해요
    static func importEvents(source: String, goalCategoryId: Int?, events: [CalendarImportEvent]) async throws -> CalendarImportResult {
        var total = CalendarImportResult.zero
        var index = 0
        while index < events.count {
            let chunk = Array(events[index..<min(index + chunkSize, events.count)])
            let body = CalendarImportRequest(source: source, goalCategoryId: goalCategoryId, events: chunk)
            let r: CalendarImportResult? = try await API.request("POST", "/v1/external-calendar/import", encodable: body)
            if let r { total = total + r }
            index += chunkSize
        }
        return total
    }
}

// MARK: - 아이폰 캘린더 (EventKit 에서 읽은 값)

struct CalendarImportDeviceCalendar: Identifiable, Hashable {
    let id: String
    let title: String
    /// "iCloud", "Gmail", "Outlook" … (아이폰 설정에 추가한 계정 이름)
    let accountName: String
    let color: Color
    /// 구글 캘린더의 기본 캘린더 (처음엔 이것만 켜요 — 웹과 같아요)
    var primary: Bool = false
}

/// 아이폰 기본 캘린더 읽기. 미리보기에서는 CalendarImportMockReader 로 바꿔 끼워요
protocol CalendarImportReading {
    /// true: 허용됨 · false: 거절됨 · nil: 아직 묻지 않음
    func currentAccess() -> Bool?
    func requestAccess() async throws -> Bool
    func calendars() -> [CalendarImportDeviceCalendar]
    /// from ~ to (둘 다 포함, "YYYY-MM-DD" 한국 날짜) 사이 일정
    func events(calendarIds: Set<String>, from: String, to: String) -> [CalendarImportEvent]
}

/// 하루 종일 일정의 날짜 — 기기 시간대 기준 "yyyy-MM-dd"
private let calendarImportLocalDateFormatter: DateFormatter = {
    let f = DateFormatter()
    f.calendar = Calendar(identifier: .gregorian)
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = .current
    f.dateFormat = "yyyy-MM-dd"
    return f
}()

/// EventKit 으로 읽어요.
/// 아이폰 캘린더 앱에 보이는 모든 계정(iCloud · 구글 · Outlook)의 일정이 들어 있어서,
/// 노션 캘린더에 연결한 계정의 일정도 여기서 같이 가져올 수 있어요.
final class CalendarImportEventKitReader: CalendarImportReading {
    private let store = EKEventStore()

    func currentAccess() -> Bool? {
        switch EKEventStore.authorizationStatus(for: .event) {
        case .fullAccess: return true
        case .notDetermined: return nil
        default: return false   // 거절 · 제한 · 쓰기 전용(일정 읽기 불가)
        }
    }

    func requestAccess() async throws -> Bool {
        try await store.requestFullAccessToEvents()
    }

    func calendars() -> [CalendarImportDeviceCalendar] {
        store.calendars(for: .event)
            .map {
                CalendarImportDeviceCalendar(
                    id: $0.calendarIdentifier,
                    title: $0.title,
                    accountName: $0.source?.title ?? "",
                    color: Color(cgColor: $0.cgColor)
                )
            }
            .sorted { ($0.accountName, $0.title) < ($1.accountName, $1.title) }
    }

    func events(calendarIds: Set<String>, from: String, to: String) -> [CalendarImportEvent] {
        let chosen = store.calendars(for: .event).filter { calendarIds.contains($0.calendarIdentifier) }
        guard !chosen.isEmpty else { return [] }
        // 한국 시간 from 0시 ~ to 다음 날 0시
        let start = KST.date(from: from)
        let end = KST.date(from: KST.addDays(to, 1))
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: chosen)

        return store.events(matching: predicate)   // 반복 일정은 회차별로 펼쳐져서 와요
            .sorted { ($0.startDate ?? .distantPast) < ($1.startDate ?? .distantPast) }
            .compactMap { event -> CalendarImportEvent? in
                if event.status == .canceled { return nil }
                // 내가 거절한 초대 일정은 빼요
                if let me = event.attendees?.first(where: { $0.isCurrentUser }), me.participantStatus == .declined {
                    return nil
                }
                let startDate: Date = event.startDate
                let endDate: Date = event.endDate ?? startDate
                let base: String = event.calendarItemExternalIdentifier ?? event.eventIdentifier ?? UUID().uuidString
                // 반복 일정은 회차마다 같은 id 라서 시작 시각을 붙여 회차를 구분해요
                let key = "\(base)|\(KST.dateTime(from: startDate))"
                let rawTitle = (event.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let title = String((rawTitle.isEmpty ? "(제목 없음)" : rawTitle).prefix(200))
                let calTitle: String? = event.calendar?.title
                let calendarName: String? = calTitle.map { String($0.prefix(100)) }
                if event.isAllDay {
                    return CalendarImportEvent(externalId: String(key.prefix(255)), title: title, calendarName: calendarName,
                                               allDay: true, start: nil, end: nil,
                                               date: calendarImportLocalDateFormatter.string(from: startDate))
                }
                let s = KST.dateTime(from: startDate)
                return CalendarImportEvent(externalId: String(key.prefix(255)), title: title, calendarName: calendarName,
                                           allDay: false, start: s, end: KST.dateTime(from: endDate),
                                           date: String(s.prefix(10)))
            }
    }
}

// MARK: - 미리보기용

struct CalendarImportMockReader: CalendarImportReading {
    var access: Bool? = true

    func currentAccess() -> Bool? { access }
    func requestAccess() async throws -> Bool { true }

    func calendars() -> [CalendarImportDeviceCalendar] {
        [
            CalendarImportDeviceCalendar(id: "c1", title: "개인", accountName: "iCloud", color: Color(hex: "#3D5AFE")),
            CalendarImportDeviceCalendar(id: "c2", title: "회사", accountName: "Gmail", color: Color(hex: "#12B76A")),
        ]
    }

    func events(calendarIds: Set<String>, from: String, to: String) -> [CalendarImportEvent] {
        let d1 = KST.addDays(from, 1)
        let d2 = KST.addDays(from, 2)
        let d4 = KST.addDays(from, 4)
        return [
            CalendarImportEvent(externalId: "e1", title: "주간 회의", calendarName: "회사", allDay: false,
                                start: "\(d1)T10:00:00", end: "\(d1)T11:00:00", date: d1),
            CalendarImportEvent(externalId: "e2", title: "치과 예약", calendarName: "개인", allDay: false,
                                start: "\(d2)T14:00:00", end: "\(d2)T14:30:00", date: d2),
            CalendarImportEvent(externalId: "e3", title: "엄마 생신", calendarName: "개인", allDay: true,
                                start: nil, end: nil, date: d4),
        ]
    }
}
