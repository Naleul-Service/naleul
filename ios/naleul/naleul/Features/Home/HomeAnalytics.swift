// Features/Home/HomeAnalytics.swift
//
// 홈 화면 이벤트 (Tracking Plan 분류 "홈" · 웹 homeEvents.ts 와 같은 이름).
// 홈에서 머문 시간 · 동작 수 · 스크롤 깊이(home.page_exit), 한 번만 보낼 이벤트(pageview · 빈 카드)를 여기서 세요.
//
//   HomeTracker.shared.track(HomeEvent.taskCheckClick, ["task_id": t.taskId])

import SwiftUI
import UIKit

enum HomeEvent {
    static let pageview = "home.pageview"
    static let pullRefresh = "home.pull_refresh"
    /// 요약 아래 섹션 칩 (오늘 일정 · 할 일 · 루틴/습관 · 다가오는 일정 · 마감 로드맵)
    static let sectionNavClick = "home.section_nav_click"
    static let notificationBellClick = "home.notification_bell_click"
    static let datePrevClick = "home.date_prev_click"
    static let dateNextClick = "home.date_next_click"
    static let dateTodayClick = "home.date_today_click"
    static let dayRollover = "home.day_rollover"
    static let retroButtonClick = "home.retro_button_click"
    static let calendarAddActivityClick = "home.calendar_add_activity_click"
    /// 오늘 일정 "추가" 메뉴 · 빈 칸 메뉴에서 AI로 자동 Task 추가 · 계획 할 일 추가 (2026-10)
    static let calendarTaskAddClick = "home.calendar_task_add_click"
    static let calendarLinkClick = "home.calendar_link_click"
    static let calendarTaskBlockClick = "home.calendar_task_block_click"
    /// 홈 캘린더에서 묶음에 넣기 · 빼기 (웹 HE.calendarTaskGroupSelect)
    static let calendarTaskGroupSelect = "home.calendar_task_group_select"
    static let calendarFixedBlockClick = "home.calendar_fixed_block_click"
    static let calendarActivityBlockClick = "home.calendar_activity_block_click"
    static let calendarEmptySlotClick = "home.calendar_empty_slot_click"
    static let calendarDragDrop = "home.calendar_drag_drop"
    static let calendarDragCancel = "home.calendar_drag_cancel"
    static let taskCheckClick = "home.task_check_click"
    static let taskRowClick = "home.task_row_click"
    static let taskAddClick = "home.task_add_click"
    static let taskDoneToggleClick = "home.task_done_toggle_click"
    static let routineCheckClick = "home.routine_check_click"
    static let habitCheckClick = "home.habit_check_click"
    static let habitGroupLinkClick = "home.habit_group_link_click"
    static let habitAddClick = "home.habit_add_click"
    static let upcomingDayClick = "home.upcoming_day_click"
    static let deadlineItemClick = "home.deadline_item_click"
    static let checkResult = "home.check_result"
    static let disabledClick = "home.disabled_click"
    static let deadClick = "home.dead_click"
    static let emptyStateView = "home.empty_state_view"
    static let loadFail = "home.load_fail"
    static let retryClick = "home.retry_click"
    static let sectionLoadFail = "home.section_load_fail"
    static let pageExit = "home.page_exit"
    // 오늘 회고 시트
    static let retroPanelView = "home.retro_panel_view"
    static let retroPanelClose = "home.retro_panel_close"
    static let retroReflectionsLinkClick = "home.retro_reflections_link_click"
    static let retroGoalChipClick = "home.retro_goal_chip_click"
    static let retroMoreGoalsClick = "home.retro_more_goals_click"
    static let retroInputStart = "home.retro_input_start"
    static let retroSaveClick = "home.retro_save_click"
    static let retroSkipClick = "home.retro_skip_click"
    static let retroSaveResult = "home.retro_save_result"
    static let retroEditClick = "home.retro_edit_click"
    static let retroEditSaveClick = "home.retro_edit_save_click"
    static let retroEditCancelClick = "home.retro_edit_cancel_click"
    static let retroDeleteClick = "home.retro_delete_click"
    static let checkinMoodSelect = "home.checkin_mood_select"
    static let checkinValueInvalid = "home.checkin_value_invalid"
    static let checkinSaveClick = "home.checkin_save_click"
    static let retroFutureBlockedView = "home.retro_future_blocked_view"
    static let retroAllDoneView = "home.retro_all_done_view"
    static let retroDraftDiscard = "home.retro_draft_discard"
}

@MainActor
final class HomeTracker {
    static let shared = HomeTracker()
    private init() {}

    private var enteredAt: Date?
    private var interactions = 0
    private var maxScroll = 0
    private var pageviewSent = false
    private var emptySent: Set<String> = []
    /// 마지막으로 다 불러온 시각 (pull_refresh 의 minutes_since_update)
    var lastLoadedAt: Date?

    /// 오늘 기준 past · today · future
    static func relation(_ date: String) -> String {
        let t = KST.today()
        return date == t ? "today" : (date < t ? "past" : "future")
    }

    /// 사용자 동작 이벤트 (page_exit 의 interaction_count 에 세요)
    func track(_ event: String, _ props: EventProps = [:]) {
        interactions += 1
        Analytics.track(event, props)
    }

    /// 화면이 보여 준 것 · 결과 이벤트 (동작 수에 안 세요)
    func system(_ event: String, _ props: EventProps = [:]) {
        Analytics.track(event, props)
    }

    // MARK: 들어옴 · 떠남

    func enter() {
        guard enteredAt == nil else { return }
        enteredAt = Date()
        interactions = 0
        maxScroll = 0
        pageviewSent = false
        emptySent = []
    }

    /// 홈을 다 불러온 뒤 한 번 (들어올 때마다)
    func pageviewOnce(_ props: EventProps) {
        guard enteredAt != nil, !pageviewSent else { return }
        pageviewSent = true
        system(HomeEvent.pageview, props)
    }

    /// exit_type — tab_switch · navigate · background
    func exit(_ type: String, viewDate: String) {
        guard let since = enteredAt else { return }
        enteredAt = nil
        system(HomeEvent.pageExit, [
            "dwell_seconds": Int(Date().timeIntervalSince(since)),
            "interaction_count": interactions,
            "exit_type": type,
            "scroll_percent": maxScroll,
            "view_date": viewDate,
        ])
    }

    /// 스크롤 내용의 위치 (coordinateSpace "homeScroll" 기준)
    func scrolled(_ frame: CGRect) {
        guard frame.height > 0 else { return }
        let viewport = UIScreen.main.bounds.height
        let p = Int(min(100, max(0, (-frame.minY + viewport) / frame.height * 100)))
        if p > maxScroll { maxScroll = p }
    }

    /// 빈 카드는 들어올 때마다 카드별로 한 번
    func emptyOnce(_ section: String, viewDate: String) {
        guard !emptySent.contains(section) else { return }
        emptySent.insert(section)
        system(HomeEvent.emptyStateView, [
            "empty_section": section, "view_date": viewDate, "date_relation": Self.relation(viewDate),
        ])
    }

    // MARK: 속성 도우미

    static func checkAction(_ wasDone: Bool) -> String { wasDone ? "uncomplete" : "complete" }

    /// overdue · due_soon(2일 안) · none
    static func dueState(_ t: TimeBlockTask, today: String) -> String {
        guard let due = t.dueDate, !t.isCompleted else { return "none" }
        if due < today { return "overdue" }
        return due <= KST.addDays(today, 2) ? "due_soon" : "none"
    }

    static func taskProps(_ t: TimeBlockTask, viewDate: String) -> EventProps {
        [
            "view_date": viewDate,
            "date_relation": relation(viewDate),
            "task_id": t.taskId,
            "source_type": t.sourceType.rawValue,
            "is_scheduled": t.plannedStartAt != nil,
            "goal_category_id": t.goalCategoryId,
        ]
    }
}
