/**
 * 공통 이벤트 이름 — 노션 Tracking Plan 「공통」 분류 (사이드바 · 모바일 위쪽 바 · 서랍 · 광고 · 토스트 · 404 · 오류 화면).
 * 어느 화면에서나 나는 이벤트라 화면 이름 대신 부품 이름으로 시작해요.
 */
export const COMMON_EVENTS = {
  sidebarNavClick: 'sidebar.nav_click',
  sidebarLogoClick: 'sidebar.logo_click',
  sidebarTaskAddClick: 'sidebar.task_add_click',
  sidebarGoalAddClick: 'sidebar.goal_add_click',
  sidebarGoalItemClick: 'sidebar.goal_item_click',
  sidebarHabitItemClick: 'sidebar.habit_item_click',
  sidebarGoalEmptyClick: 'sidebar.goal_empty_click',
  sidebarGoalListLoadFail: 'sidebar.goal_list_load_fail',
  sidebarDeadClick: 'sidebar.dead_click',

  mobileHeaderMenuClick: 'mobile_header.menu_click',
  mobileHeaderDeadClick: 'mobile_header.dead_click',
  drawerClose: 'drawer.close',
  drawerResizeHidden: 'drawer.resize_hidden',

  adView: 'ad.view',
  adUpgradeClick: 'ad.upgrade_click',

  toastView: 'toast.view',
  toastDismissClick: 'toast.dismiss_click',

  notFoundView: 'not_found.view',
  notFoundHomeClick: 'not_found.home_click',
  errorPageView: 'error_page.view',
  errorPageRetryClick: 'error_page.retry_click',
} as const

export type CommonEventName = (typeof COMMON_EVENTS)[keyof typeof COMMON_EVENTS]

/** 지금 보고 있는 경로 (current_path). 개인 id 는 그대로 둬요 (노션 규칙) */
export const currentPath = () => (typeof window === 'undefined' ? undefined : window.location.pathname)

/** 어디서 들어왔는지 (referrer_type) */
export function referrerType(): 'internal' | 'external' | 'direct' {
  if (typeof document === 'undefined' || !document.referrer) return 'direct'
  try {
    return new URL(document.referrer).origin === window.location.origin ? 'internal' : 'external'
  } catch {
    return 'direct'
  }
}
