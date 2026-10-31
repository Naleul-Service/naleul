// feature/notifications/NotificationsAnalytics.kt
//
// iOS Features/Notifications/NotificationsAnalytics.swift — 알림함 · 실천 도우미 이벤트 (Tracking Plan 분류 "알림").
//  - notification_inbox.*  알림함 화면
//  - nudge_settings.*      설정 > 알림 설정의 실천 도우미 카드
//  - nudge.*               기기 안에서 예약하는 실천 도우미 알림 (시스템)
// 알림을 눌러 앱이 열리는 것은 공통 분류(push.open)라서 NudgeCenter.trackPushOpen 에 그대로 있어요.

package com.naleul.naleul.feature.notifications

import com.naleul.naleul.core.analytics.AnalyticsService

object NotiEvent {
    const val pageview = "notification_inbox.pageview"
    const val exit = "notification_inbox.exit"
    const val permissionBannerView = "notification_inbox.permission_banner_view"
    const val openSettingsClick = "notification_inbox.open_settings_click"
    const val permissionReturn = "notification_inbox.permission_return"
    const val nextCardClick = "notification_inbox.next_card_click"
    // notification_inbox.filter_click — 2026-10 필터(전체 · 읽지 않음 · 실천 도우미) 제거로 폐기
    const val emptyView = "notification_inbox.empty_view"
    const val itemClick = "notification_inbox.item_click"
    const val itemMarkRead = "notification_inbox.item_mark_read"
    const val itemDelete = "notification_inbox.item_delete"
    const val itemMenuOpen = "notification_inbox.item_menu_open"
    const val markAllReadClick = "notification_inbox.mark_all_read_click"
    const val deleteAllClick = "notification_inbox.delete_all_click"
    const val settingsClick = "notification_inbox.settings_click"
    const val pullRefresh = "notification_inbox.pull_refresh"
    // 설정 > 알림 설정 · 실천 도우미
    const val nudgeToggle = "nudge_settings.toggle"
    const val nudgeLockedClick = "nudge_settings.locked_click"
    const val nudgeInboxClick = "nudge_settings.inbox_click"
    // 시스템
    const val scheduleResult = "nudge.schedule_result"
    const val delivered = "nudge.delivered"
    const val permissionChanged = "nudge.permission_changed"
}

object InboxTracker {
    /** 알림함을 열기 직전에 정해 둬요 (home_bell · settings) → pageview 의 entry_source */
    var pendingEntry: String? = null
    /** '설정 열기'로 나갔다가 돌아오면 권한 결과를 한 번 보내요 */
    var awaitingSettingsReturn = false

    private var enteredAt: Long? = null
    private var interactions = 0
    private var navigating = false
    private val emptySent = mutableSetOf<String>()
    private var bannerSent = false

    /** 사용자 동작 (interaction_count 에 셈) */
    fun track(event: String, vararg props: Pair<String, Any?>) {
        interactions += 1
        AnalyticsService.track(event, *props)
    }

    /** 화면이 보여 주는 것 · 시스템 (셈하지 않음) */
    fun system(event: String, vararg props: Pair<String, Any?>) {
        AnalyticsService.track(event, *props)
    }

    /** 새로 들어왔을 때만 pageview. 다른 탭에 갔다 오거나 앱이 다시 앞으로 온 거면 시간만 다시 재요 */
    fun enter(fresh: Boolean) {
        if (!fresh && enteredAt != null) return
        enteredAt = System.currentTimeMillis()
        interactions = 0
        navigating = false
        if (!fresh) return
        emptySent.clear()
        bannerSent = false
        system(
            NotiEvent.pageview,
            "entry_source" to (pendingEntry ?: "other"),
            "unread_count" to NudgeCenter.unreadCount,
            "total_count" to NudgeCenter.entries.size,
            "planned_count" to NudgeCenter.planned.size,
            "push_authorized" to NudgeCenter.isAuthorized,
        )
        pendingEntry = null
    }

    /** 알림 · 설정을 눌러 다른 화면으로 가기 직전 */
    fun markNavigating() { navigating = true }

    /** autoReadCount: 화면을 떠나며 자동으로 읽음 처리한 개수 (앱이 뒤로 갈 때는 null — 아직 떠난 게 아니라서) */
    fun exit(type: String? = null, autoReadCount: Int? = null) {
        val since = enteredAt ?: return
        system(
            NotiEvent.exit,
            "exit_type" to (type ?: if (navigating) "navigate" else "back"),
            "dwell_seconds" to ((System.currentTimeMillis() - since) / 1000).toInt(),
            "interaction_count" to interactions,
            "unread_count" to NudgeCenter.unreadCount,
            "auto_read_count" to autoReadCount,
        )
        enteredAt = null
    }

    fun emptyOnce() {
        if (!emptySent.add("all")) return
        system(
            NotiEvent.emptyView,
            "total_count" to NudgeCenter.entries.size, "push_authorized" to NudgeCenter.isAuthorized,
        )
    }

    fun bannerOnce() {
        if (bannerSent) return
        bannerSent = true
        system(NotiEvent.permissionBannerView, "planned_count" to NudgeCenter.planned.size, "enabled_group_count" to NudgeGroup.enabledCount)
    }
}

// MARK: - 이벤트 값

val NudgeGroup.Companion.enabledCount: Int get() = NudgeGroup.entries.count { it.isEnabled }

/** 실천 도우미면 종류(firstStep · dayWrapUp …), 서버 푸시면 server */
val NudgeEntry.analyticsKind: String get() = if (kind == NudgeKind.routineNudge) "routine_nudge" else kind.rawValue

/** 루틴 실천 알림이면 YESTERDAY_TIME · RATE_RESCHEDULE (이벤트의 routine_kind) */
val NudgeEntry.analyticsRoutineKind: String? get() = if (kind == NudgeKind.routineNudge) routineKind else null

/** 누르면 갈 곳 — 경로 앞부분만 (AI 문장 같은 자유 입력은 보내지 않아요). 경로가 없으면 오늘 캘린더 */
val NudgeEntry.analyticsDestination: String
    get() {
        val r = route
        if (r.isNullOrEmpty()) return "calendar_today"
        return r.substringBefore(":")
    }

val NudgeEntry.minutesSinceSent: Int get() = maxOf(0L, (System.currentTimeMillis() - date) / 60_000L).toInt()

/** 알림함 날짜 묶음 제목 → section 값 */
fun inboxSectionKey(title: String): String = when (title) {
    "오늘" -> "today"
    "어제" -> "yesterday"
    "이번 주" -> "this_week"
    else -> "older"
}
