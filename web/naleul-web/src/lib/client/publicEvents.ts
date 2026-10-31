/**
 * 공개 페이지 이벤트 이름 — 노션 Tracking Plan 「공개 페이지」 분류 (로그인 없이 보는 서비스 소개 · 개인정보처리방침 · 이용약관).
 * 구글 OAuth 앱 인증(홈페이지 · 방침 공개 URL)을 위해 2026-10-28 추가했어요.
 *  - about.* : /about 서비스 소개
 *  - legal.* : /privacy · /terms (legal_type 으로 구분)
 *  - login.legal_link_click : 로그인 화면 아래 링크 (소개 · 이용약관 · 개인정보처리방침)
 */
export const PUBLIC_EVENTS = [
  'about.pageview',
  'about.start_click',
  'about.login_click',
  'about.link_click',
  'about.first_interaction',
  'about.page_exit',
  'legal.pageview',
  'legal.scroll_depth',
  'legal.start_click',
  'legal.login_click',
  'legal.link_click',
  'legal.first_interaction',
  'legal.page_exit',
  'login.legal_link_click',
] as const

export type PublicEventName = (typeof PUBLIC_EVENTS)[number]

/** legal_type */
export type LegalType = 'privacy' | 'terms' | 'refund'
/** link_type — 공개 페이지 안에서 누른 링크 종류 */
export type PublicLinkType =
  | 'about'
  | 'privacy'
  | 'terms'
  | 'refund'
  | 'google_policy'
  | 'google_permissions'
  | 'ad_settings'
  | 'contact'
