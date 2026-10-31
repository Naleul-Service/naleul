// feature/home/HomeAnalytics.kt
//
// iOS Features/Home/HomeAnalytics.swift — 홈 화면 이벤트 (Tracking Plan 분류 "홈" · 웹과 같은 이름).
// 홈에서 머문 시간 · 동작 수 · 스크롤 깊이(home.page_exit), 한 번만 보낼 이벤트(pageview · 빈 카드)를 여기서 세요.

package com.naleul.naleul.feature.home

import com.naleul.naleul.core.analytics.AnalyticsService
import com.naleul.naleul.core.util.KST
import com.naleul.naleul.model.TimeBlockTask

object HomeEvent {
    const val pageview = "home.pageview"
    const val pullRefresh = "home.pull_refresh"
    /** 요약 아래 섹션 칩 (오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵) */
    const val sectionNavClick = "home.section_nav_click"
    const val notificationBellClick = "home.notification_bell_click"
    const val datePrevClick = "home.date_prev_click"
    const val dateNextClick = "home.date_next_click"
    const val dateTodayClick = "home.date_today_click"
    const val dayRollover = "home.day_rollover"
    const val retroButtonClick = "home.retro_button_click"
    const val calendarAddActivityClick = "home.calendar_add_activity_click"
    /** 오늘 일정 "추가" 메뉴 · 빈 칸 메뉴에서 AI로 자동 Task 추가 · 계획 할 일 추가 (2026-10) */
    const val calendarTaskAddClick = "home.calendar_task_add_click"
    const val calendarLinkClick = "home.calendar_link_click"
    const val calendarTaskBlockClick = "home.calendar_task_block_click"
    const val calendarTaskGroupSelect = "home.calendar_task_group_select"
    const val calendarFixedBlockClick = "home.calendar_fixed_block_click"
    const val calendarActivityBlockClick = "home.calendar_activity_block_click"
    const val calendarEmptySlotClick = "home.calendar_empty_slot_click"
    const val calendarDragDrop = "home.calendar_drag_drop"
    const val calendarDragCancel = "home.calendar_drag_cancel"
    const val taskCheckClick = "home.task_check_click"
    const val taskRowClick = "home.task_row_click"
    const val taskAddClick = "home.task_add_click"
    const val taskDoneToggleClick = "home.task_done_toggle_click"
    const val routineCheckClick = "home.routine_check_click"
    const val habitCheckClick = "home.habit_check_click"
    const val habitGroupLinkClick = "home.habit_group_link_click"
    const val habitAddClick = "home.habit_add_click"
    const val upcomingDayClick = "home.upcoming_day_click"
    const val deadlineItemClick = "home.deadline_item_click"
    const val checkResult = "home.check_result"
    const val disabledClick = "home.disabled_click"
    const val deadClick = "home.dead_click"
    const val emptyStateView = "home.empty_state_view"
    const val loadFail = "home.load_fail"
    const val retryClick = "home.retry_click"
    const val sectionLoadFail = "home.section_load_fail"
    const val pageExit = "home.page_exit"
    const val retroPanelView = "home.retro_panel_view"
    const val retroPanelClose = "home.retro_panel_close"
    const val retroReflectionsLinkClick = "home.retro_reflections_link_click"
    const val retroGoalChipClick = "home.retro_goal_chip_click"
    const val retroMoreGoalsClick = "home.retro_more_goals_click"
    const val retroInputStart = "home.retro_input_start"
    const val retroSaveClick = "home.retro_save_click"
    const val retroSkipClick = "home.retro_skip_click"
    const val retroSaveResult = "home.retro_save_result"
    const val retroEditClick = "home.retro_edit_click"
    const val retroEditSaveClick = "home.retro_edit_save_click"
    const val retroEditCancelClick = "home.retro_edit_cancel_click"
    const val retroDeleteClick = "home.retro_delete_click"
    const val checkinMoodSelect = "home.checkin_mood_select"
    const val checkinValueInvalid = "home.checkin_value_invalid"
    const val checkinSaveClick = "home.checkin_save_click"
    const val retroFutureBlockedView = "home.retro_future_blocked_view"
    const val retroAllDoneView = "home.retro_all_done_view"
    const val retroDraftDiscard = "home.retro_draft_discard"
}

object HomeTracker {
    private var enteredAt: Long? = null
    private var interactions = 0
    private var maxScroll = 0
    private var pageviewSent = false
    private val emptySent = mutableSetOf<String>()
    /** 마지막으로 다 불러온 시각 (pull_refresh 의 minutes_since_update) */
    var lastLoadedAt: Long? = null

    /** 오늘 기준 past · today · future */
    fun relation(date: String): String {
        val t = KST.today()
        return if (date == t) "today" else if (date < t) "past" else "future"
    }

    /** 사용자 동작 이벤트 (page_exit 의 interaction_count 에 세요) */
    fun track(event: String, vararg props: Pair<String, Any?>) {
        interactions += 1
        AnalyticsService.track(event, *props)
    }

    /** 화면이 보여 준 것 · 결과 이벤트 (동작 수에 안 세요) */
    fun system(event: String, vararg props: Pair<String, Any?>) = AnalyticsService.track(event, *props)

    fun enter() {
        if (enteredAt != null) return
        enteredAt = System.currentTimeMillis()
        interactions = 0
        maxScroll = 0
        pageviewSent = false
        emptySent.clear()
    }

    fun pageviewOnce(vararg props: Pair<String, Any?>) {
        if (enteredAt == null || pageviewSent) return
        pageviewSent = true
        system(HomeEvent.pageview, *props)
    }

    /** exitType — tab_switch · navigate · background */
    fun exit(exitType: String, viewDate: String) {
        val since = enteredAt ?: return
        enteredAt = null
        system(
            HomeEvent.pageExit,
            "dwell_seconds" to ((System.currentTimeMillis() - since) / 1000).toInt(),
            "interaction_count" to interactions,
            "exit_type" to exitType,
            "scroll_percent" to maxScroll,
            "view_date" to viewDate,
        )
    }

    /** 스크롤 위치 → 가장 깊이 내린 % */
    fun scrolled(value: Int, max: Int, viewport: Int) {
        val total = max + viewport
        if (total <= 0) return
        val p = ((value + viewport) * 100 / total).coerceIn(0, 100)
        if (p > maxScroll) maxScroll = p
    }

    fun emptyOnce(section: String, viewDate: String) {
        if (!emptySent.add(section)) return
        system(HomeEvent.emptyStateView, "empty_section" to section, "view_date" to viewDate, "date_relation" to relation(viewDate))
    }

    fun checkAction(wasDone: Boolean) = if (wasDone) "uncomplete" else "complete"

    /** overdue · due_soon(2일 안) · none */
    fun dueState(t: TimeBlockTask, today: String): String {
        val due = t.dueDate ?: return "none"
        if (t.isCompleted) return "none"
        if (due < today) return "overdue"
        return if (due <= KST.addDays(today, 2)) "due_soon" else "none"
    }
}
