/**
 * 나의 패턴(/patterns) 이벤트 이름
 * — 노션 Tracking Plan 「나의 패턴」 분류의 event 컬럼 그대로예요. track('pattern.pageview', ...) 처럼 써요.
 */
export const PATTERN_EVENTS = [
  'pattern.chart_interact',
  'pattern.dead_click',
  'pattern.empty_cta_click',
  'pattern.keep_filter_click',
  'pattern.load_fail',
  'pattern.locked_view',
  'pattern.pageview',
  'pattern.period_change',
  'pattern.recalc_click',
  'pattern.recalc_result',
  'pattern.report_view',
  'pattern.retry_click',
  'pattern.scroll_depth',
  'pattern.share_click',
  'pattern.share_result',
  'pattern.suggestion_apply_cancel',
  'pattern.suggestion_apply_click',
  'pattern.suggestion_apply_open',
  'pattern.suggestion_apply_result',
  'pattern.suggestion_dismiss_click',
  'pattern.suggestion_dismiss_result',
  'pattern.suggestion_view',
] as const

export type PatternEventName = (typeof PATTERN_EVENTS)[number]
