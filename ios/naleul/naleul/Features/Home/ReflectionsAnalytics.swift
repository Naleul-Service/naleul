// Features/Home/ReflectionsAnalytics.swift
//
// 회고 이벤트 (Tracking Plan 분류 "회고" · 웹 reflections / reflection_timeline 과 같은 이름).
// 같은 기간별 목록(ReflectionTimelineView)을 회고 모아보기와 목표 상세 "회고" 탭이 같이 써서 surface 로 구분해요
// (ReflectionsViewModel.surface = reflections / goal_detail).
//
//   RF.pendingEntry = "home_retro"; router.openReflections()   // → reflections.pageview 의 entry_source

import Foundation

@MainActor
enum RF {
    /// 미리보기에서는 보내지 않아요
    static var isPreview: Bool { ProcessInfo.processInfo.environment["XCODE_RUNNING_FOR_PREVIEWS"] == "1" }

    static func track(_ event: String, _ props: EventProps = [:]) {
        if isPreview { return }
        Analytics.track(event, props)
    }

    /// 요청 실패의 HTTP 상태 (0 = 네트워크)
    static func status(_ error: Error) -> Int { (error as? APIError)?.httpStatus ?? 0 }

    /// 회고 모아보기를 열기 직전에 정해 둬요 (home_retro · work_compose · nudge) → 첫 pageview 가 가져가요
    static var pendingEntry: String?
}

extension ReflectionPeriod {
    /// period 값 — 웹과 같이 DAY · WEEK · MONTH
    var analyticsValue: String { rawValue.uppercased() }
}
