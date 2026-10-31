// Features/Notifications/NotificationsAnalytics.swift
//
// 알림함 · 실천 도우미 이벤트 (Tracking Plan 분류 "알림" · 모바일에만 있는 화면).
//  - notification_inbox.*  알림함 화면
//  - nudge_settings.*      설정 > 알림 설정의 실천 도우미 카드
//  - nudge.*               기기 안에서 예약하는 실천 도우미 알림 (시스템)
// 알림 배너를 눌러 앱이 열리는 것은 공통 분류(push.open)라서 AppDelegate 에 그대로 있어요.
//
//   InboxTracker.shared.track(NotiEvent.itemClick, ["notification_kind": "dayWrapUp"])

import Foundation
import UserNotifications

enum NotiEvent {
    static let pageview = "notification_inbox.pageview"
    static let exit = "notification_inbox.exit"
    static let permissionBannerView = "notification_inbox.permission_banner_view"
    static let openSettingsClick = "notification_inbox.open_settings_click"
    static let permissionReturn = "notification_inbox.permission_return"
    static let nextCardClick = "notification_inbox.next_card_click"
    // notification_inbox.filter_click — 2026-10 필터(전체 · 읽지 않음 · 실천 도우미) 제거로 폐기
    static let emptyView = "notification_inbox.empty_view"
    static let itemClick = "notification_inbox.item_click"
    static let itemMarkRead = "notification_inbox.item_mark_read"
    static let itemDelete = "notification_inbox.item_delete"
    static let markAllReadClick = "notification_inbox.mark_all_read_click"
    static let deleteAllClick = "notification_inbox.delete_all_click"
    static let settingsClick = "notification_inbox.settings_click"
    static let pullRefresh = "notification_inbox.pull_refresh"
    // 설정 > 알림 설정 · 실천 도우미
    static let nudgeToggle = "nudge_settings.toggle"
    static let nudgeLockedClick = "nudge_settings.locked_click"
    static let nudgeInboxClick = "nudge_settings.inbox_click"
    // 시스템
    static let scheduleResult = "nudge.schedule_result"
    static let delivered = "nudge.delivered"
    static let permissionChanged = "nudge.permission_changed"
}

@MainActor
final class InboxTracker {
    static let shared = InboxTracker()
    private init() {}

    /// 알림함을 열기 직전에 정해 둬요 (home_bell · settings) → pageview 의 entry_source
    var pendingEntry: String?
    /// '설정 열기'로 나갔다가 돌아오면 권한 결과를 한 번 보내요
    var awaitingSettingsReturn = false

    private var enteredAt: Date?
    private var interactions = 0
    private var navigating = false
    private var emptySent: Set<String> = []
    private var bannerSent = false

    /// 사용자 동작 (interaction_count 에 셈)
    func track(_ event: String, _ props: EventProps = [:]) {
        interactions += 1
        Analytics.track(event, props)
    }

    /// 화면이 보여 주는 것 · 시스템 (셈하지 않음)
    func system(_ event: String, _ props: EventProps = [:]) {
        Analytics.track(event, props)
    }

    // MARK: 들어옴 · 나감

    /// 새로 들어왔을 때만 pageview. 다른 탭에 갔다 돌아온 거면 시간만 다시 재요
    func enter(fresh: Bool, center: NudgeCenter) {
        // 이미 재는 중이면 그대로 (제어 센터만 내렸다 올려 active 가 또 와도 시간이 리셋되지 않게)
        if !fresh, enteredAt != nil { return }
        enteredAt = Date()
        interactions = 0
        navigating = false
        guard fresh else { return }
        emptySent = []
        bannerSent = false
        system(NotiEvent.pageview, [
            "entry_source": pendingEntry ?? "other",
            "unread_count": center.unreadCount,
            "total_count": center.entries.count,
            "planned_count": center.planned.count,
            "push_authorized": center.isAuthorized,
        ])
        pendingEntry = nil
    }

    /// 알림 · 설정을 눌러 다른 화면으로 가기 직전
    func markNavigating() { navigating = true }

    /// autoReadCount: 화면을 떠나며 자동으로 읽음 처리한 개수 (앱이 뒤로 갈 때는 nil — 아직 떠난 게 아니라서 읽음 처리 안 해요)
    func exit(_ type: String? = nil, unread: Int, autoReadCount: Int? = nil) {
        guard let since = enteredAt else { return }
        system(NotiEvent.exit, [
            "exit_type": type ?? (navigating ? "navigate" : "back"),
            "dwell_seconds": Int(Date().timeIntervalSince(since)),
            "interaction_count": interactions,
            "unread_count": unread,
            "auto_read_count": autoReadCount,
        ])
        enteredAt = nil
    }

    func emptyOnce(total: Int, authorized: Bool) {
        guard !emptySent.contains("all") else { return }
        emptySent.insert("all")
        system(NotiEvent.emptyView, ["total_count": total, "push_authorized": authorized])
    }

    func bannerOnce(planned: Int) {
        guard !bannerSent else { return }
        bannerSent = true
        system(NotiEvent.permissionBannerView, ["planned_count": planned, "enabled_group_count": NudgeGroup.enabledCount])
    }
}

// MARK: - 이벤트 값

extension NudgeGroup {
    static var enabledCount: Int { allCases.filter { $0.isEnabled }.count }
}

extension NudgeEntry {
    /// 실천 도우미면 종류(firstStep · dayWrapUp …), 서버 푸시면 server
    var analyticsKind: String { kind == .routineNudge ? "routine_nudge" : kind.rawValue }

    /// 루틴 실천 알림이면 YESTERDAY_TIME · RATE_RESCHEDULE (이벤트의 routine_kind)
    var analyticsRoutineKind: String? { kind == .routineNudge ? routineKind : nil }

    /// 누르면 갈 곳 — 경로 앞부분만 (AI 문장 같은 자유 입력은 보내지 않아요). 경로가 없으면 오늘 캘린더
    var analyticsDestination: String {
        guard let route, !route.isEmpty else { return "calendar_today" }
        return String(route.split(separator: ":", maxSplits: 1).first ?? "")
    }

    var minutesSinceSent: Int { max(0, Int(Date().timeIntervalSince(date) / 60)) }
}

/// 알림함 날짜 묶음 제목 → section 값
enum InboxSectionKey {
    static func of(_ title: String) -> String {
        switch title {
        case "오늘": return "today"
        case "어제": return "yesterday"
        case "이번 주": return "this_week"
        default: return "older"
        }
    }
}
