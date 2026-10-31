import { LOGIN_REQUIRED_EVENT } from '@/lib/client/api'

/** 지금 보던 화면 경로 — 로그인 후 여기로 돌아와요 */
export function currentPath() {
  if (typeof window === 'undefined') return '/'
  return window.location.pathname + window.location.search
}

/**
 * 로그인 모달 열기.
 *  - 'save'  : 둘러보기 중 저장하려다 막혔을 때 (api 가 401 GUEST_LOGIN_REQUIRED 를 받으면 자동으로 열려요)
 *  - 'login' : 배너 · 사이드바의 "로그인" 버튼처럼 직접 로그인하려고 할 때
 */
export type LoginDialogReason = 'save' | 'login'

/** 로그인 모달을 연 곳 (Amplitude trigger_source) */
export type LoginTriggerSource = 'guest_blocked_api' | 'guest_banner' | 'sidebar' | 'login_error_return'

export interface LoginDialogDetail {
  reason?: LoginDialogReason
  /** 카카오 콜백이 실패를 알려준 경우 (loginError 코드) */
  error?: string
  source?: LoginTriggerSource
  /** 둘러보기 중 401 GUEST_LOGIN_REQUIRED 로 막힌 API (예: POST /v1/goals) */
  blockedApiPath?: string
}

export function openLoginDialog(detail: LoginDialogDetail = {}) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent<LoginDialogDetail>(LOGIN_REQUIRED_EVENT, { detail }))
}
