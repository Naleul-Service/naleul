/**
 * 회고 모아보기(/reflections)와 회고 타임라인(목표 상세 회고 탭에서도 씀) 이벤트 이름
 * — 노션 Tracking Plan 「회고」 분류의 event 컬럼 그대로예요. track('reflections.pageview', ...) 처럼 써요.
 */
export const REFLECTION_EVENTS = [
  'reflection_detail.close',
  'reflection_detail.delete_cancel',
  'reflection_detail.delete_click',
  'reflection_detail.delete_confirm',
  'reflection_detail.delete_fail',
  'reflection_detail.edit_cancel',
  'reflection_detail.edit_click',
  'reflection_detail.edit_fail',
  'reflection_detail.edit_save',
  'reflection_detail.goal_link_click',
  'reflection_detail.view',
  'reflection_timeline.empty_view',
  'reflection_timeline.entry_click',
  'reflection_timeline.goal_link_click',
  'reflection_timeline.load_fail',
  'reflection_timeline.nav_click',
  'reflection_timeline.period_change',
  'reflection_timeline.retry_click',
  'reflection_timeline.scroll_depth',
  'reflection_timeline.view',
  'reflections.goal_filter_select',
  'reflections.goal_param_unmatched',
  'reflections.goals_load_fail',
  'reflections.pageview',
] as const

export type ReflectionEventName = (typeof REFLECTION_EVENTS)[number]
