// Features/Calendar/CalendarAnalytics.swift
//
// 캘린더 이벤트 (Tracking Plan 분류 "캘린더" · 웹 calendar / timetable / task_detail / activity_form / actual_time /
// fixed_detail / scope_chooser / fill_proposal / carry_proposal / calendar_month / calendar_day_list / calendar_ai 와 같은 이름).
// 블록 상세 · 실제로 한 일 · 아래 확인 바는 홈 · Task 추가 결과 격자에서도 같은 부품이라 surface 로 구분해요
// (TimetableBoardModel.analyticsSurface = calendar / home / task_add_result).
// 격자 클릭 · 끌기(timetable.*)는 홈에서는 보내지 않아요 — 홈은 home.calendar_* 를 따로 보내요.
//
//   CAL.track("calendar.view_change", ["view_mode": "week", "from_view": "day", "view_date": d])

import Foundation

@MainActor
enum CAL {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// 요청 실패의 HTTP 상태 (0 = 네트워크)
    static func status(_ error: Error) -> Int { (error as? APIError)?.httpStatus ?? 0 }

    /// 요청 결과 공통 속성 — Mutation.run 을 쓴 곳 (실패면 마지막 HTTP 상태)
    static func result(_ ok: Bool) -> EventProps { GD.result(ok) }

    /// today · past · future
    static func relation(_ date: String) -> String { HomeTracker.relation(date) }

    /// 오늘부터 며칠 전 (오늘 0)
    static func daysAgo(_ ymd: String) -> Int { KST.diffDays(String(ymd.prefix(10)), KST.today()) }

    /// view_mode 값 (day · week · month)
    static func viewMode(_ m: CalendarViewMode) -> String { m.rawValue }
}
