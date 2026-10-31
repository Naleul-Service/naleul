/**
 * 홈(/) 이벤트 이름 — 노션 Tracking Plan 「홈」 분류의 event 컬럼 그대로예요.
 * 화면에서는 HOME_EVENTS.xxx 로 써요. (analytics.ts 의 EVENTS 와 같은 역할)
 */
export const HOME_EVENTS = {
  pageview: 'home.pageview',
  pageExit: 'home.page_exit',
  firstInteraction: 'home.first_interaction',
  loadFail: 'home.load_fail',
  sectionLoadFail: 'home.section_load_fail',
  retryClick: 'home.retry_click',
  dayRollover: 'home.day_rollover',
  scrollDepth: 'home.scroll_depth',
  sectionScroll: 'home.section_scroll',
  deadClick: 'home.dead_click',
  disabledClick: 'home.disabled_click',
  emptyStateView: 'home.empty_state_view',

  datePrevClick: 'home.date_prev_click',
  dateNextClick: 'home.date_next_click',
  dateTodayClick: 'home.date_today_click',
  retroButtonClick: 'home.retro_button_click',

  calendarTaskBlockClick: 'home.calendar_task_block_click',
  calendarFixedBlockClick: 'home.calendar_fixed_block_click',
  calendarActivityBlockClick: 'home.calendar_activity_block_click',
  calendarEmptySlotClick: 'home.calendar_empty_slot_click',
  calendarTaskGroupSelect: 'home.calendar_task_group_select',
  calendarDragDrop: 'home.calendar_drag_drop',
  calendarDragCancel: 'home.calendar_drag_cancel',
  calendarAddActivityClick: 'home.calendar_add_activity_click',
  // 추가 메뉴 (캘린더 일간과 같은 메뉴, 2026-10) — 머리글 "추가" · 빈 칸 왼쪽 클릭/우클릭 · Task 우클릭
  calendarTaskAddClick: 'home.calendar_task_add_click',
  calendarContextMenuOpen: 'home.calendar_context_menu_open',
  calendarContextMenuSelect: 'home.calendar_context_menu_select',
  calendarLinkClick: 'home.calendar_link_click',

  taskAddClick: 'home.task_add_click',
  taskCheckClick: 'home.task_check_click',
  taskDoneToggleClick: 'home.task_done_toggle_click',
  routineCheckClick: 'home.routine_check_click',
  habitCheckClick: 'home.habit_check_click',
  habitAddClick: 'home.habit_add_click',
  habitGroupLinkClick: 'home.habit_group_link_click',
  checkResult: 'home.check_result',

  upcomingDayClick: 'home.upcoming_day_click',
  deadlineItemClick: 'home.deadline_item_click',

  retroPanelView: 'home.retro_panel_view',
  retroPanelClose: 'home.retro_panel_close',
  retroReflectionsLinkClick: 'home.retro_reflections_link_click',
  retroGoalChipClick: 'home.retro_goal_chip_click',
  retroMoreGoalsClick: 'home.retro_more_goals_click',
  retroGoalLinkClick: 'home.retro_goal_link_click',
  retroInputStart: 'home.retro_input_start',
  retroSaveClick: 'home.retro_save_click',
  retroSkipClick: 'home.retro_skip_click',
  retroSaveResult: 'home.retro_save_result',
  retroAllDoneView: 'home.retro_all_done_view',
  retroFutureBlockedView: 'home.retro_future_blocked_view',
  retroDraftDiscard: 'home.retro_draft_discard',
  retroEditClick: 'home.retro_edit_click',
  retroEditCancelClick: 'home.retro_edit_cancel_click',
  retroEditSaveClick: 'home.retro_edit_save_click',
  retroDeleteClick: 'home.retro_delete_click',
  checkinMoodSelect: 'home.checkin_mood_select',
  checkinValueInvalid: 'home.checkin_value_invalid',
  checkinSaveClick: 'home.checkin_save_click',
} as const

export type HomeEventName = (typeof HOME_EVENTS)[keyof typeof HOME_EVENTS]
