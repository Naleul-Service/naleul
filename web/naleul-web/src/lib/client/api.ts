/**
 * 브라우저(클라이언트 컴포넌트)에서 쓰는 API 함수.
 * 항상 같은 도메인의 /api/v1/** (BFF 프록시)로 요청해요.
 *
 * 사용 예:
 *   const turn = await api.post<TurnResponse>('/v1/goal-creation/sessions', { initialMessage })
 *   const active = await api.get<DetailResponse | null>('/v1/goal-creation/sessions/active')  // 204 → null
 *
 * 실패하면 ApiError 를 throw 해요. → TanStack Query 의 error 로 들어가요.
 */
import type { ApiEnvelope, Violation } from '@/types/api'
import { resetAnalytics, trackBeforeLeave, type AnalyticsEventName } from './analytics'

export class ApiError<D = unknown> extends Error {
  /** 숫자 HTTP 상태코드 (409, 422, 429, 502 ...) */
  readonly httpStatus: number
  /** 백엔드 상태 이름 ("CONFLICT" ...) */
  readonly status?: string
  /** 실패 응답에 담긴 data (예: 422 의 { violations }) */
  readonly data?: D

  constructor(httpStatus: number, message: string, status?: string, data?: D) {
    super(message)
    this.name = 'ApiError'
    this.httpStatus = httpStatus
    this.status = status
    this.data = data
  }

  /** 422 확정 검증 실패일 때 violations 꺼내기 */
  get violations(): Violation[] {
    const d = this.data as { violations?: Violation[] } | undefined
    return Array.isArray(d?.violations) ? d.violations : []
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError

const NETWORK_MESSAGE = '네트워크 연결을 확인해 주세요.'

/** 둘러보기 중 저장하려고 할 때 — LoginRequiredDialog 가 이 이벤트를 듣고 로그인 안내를 띄워요 */
export const LOGIN_REQUIRED_EVENT = 'naleul:login-required'
export const isGuestLoginRequired = (e: unknown) => isApiError(e) && e.status === 'GUEST_LOGIN_REQUIRED'

function goToLogin() {
  if (typeof window === 'undefined') return
  const here = window.location.pathname + window.location.search
  window.location.href = `/login?redirect=${encodeURIComponent(here)}&from=unauthorized`
}

/**
 * 막힌 API 를 Amplitude 에 남길 때 쓰는 모양 (login_modal.view 의 blocked_api_path).
 * 숫자 id 는 :id 로 바꾸고 쿼리는 빼요 → "POST /v1/goals/:id/tasks" 처럼 묶어서 볼 수 있어요.
 */
function apiPathForAnalytics(method: string, path: string) {
  const clean = path.split('?')[0].replace(/\/\d+(?=\/|$)/g, '/:id')
  return `${method} ${clean}`
}

async function request<T>(method: string, path: string, body?: unknown, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, {
      ...init,
      method,
      headers: body !== undefined ? { 'Content-Type': 'application/json', ...init?.headers } : init?.headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      cache: 'no-store',
    })
  } catch {
    throw new ApiError(0, NETWORK_MESSAGE)
  }

  if (res.status === 204) return null as T

  const json = (await res.json().catch(() => null)) as ApiEnvelope<T> | null

  if (!res.ok || !json?.success) {
    if (res.status === 401 && json?.status === 'GUEST_LOGIN_REQUIRED') {
      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent(LOGIN_REQUIRED_EVENT, {
            detail: { reason: 'save', source: 'guest_blocked_api', blockedApiPath: apiPathForAnalytics(method, path) },
          })
        )
      }
    } else if (res.status === 401) goToLogin()
    throw new ApiError(res.status, json?.message ?? '문제가 생겼어요. 다시 시도해 주세요.', json?.status, json?.data)
  }

  return json.data as T
}

export const api = {
  get: <T>(path: string, init?: RequestInit) => request<T>('GET', path, undefined, init),
  post: <T>(path: string, body?: unknown, init?: RequestInit) => request<T>('POST', path, body ?? {}, init),
  put: <T>(path: string, body?: unknown, init?: RequestInit) => request<T>('PUT', path, body ?? {}, init),
  patch: <T>(path: string, body?: unknown, init?: RequestInit) => request<T>('PATCH', path, body ?? {}, init),
  /** body 는 선택 — 회원 탈퇴(DELETE /v1/users)처럼 사유를 같이 보내야 할 때만 넘겨요 */
  delete: <T>(path: string, body?: unknown, init?: RequestInit) => request<T>('DELETE', path, body, init),
}

/**
 * 로그아웃: (이벤트 전송) → Amplitude 초기화 → 쿠키 삭제 → 로그인 화면으로
 * trackEvent: 어디서 로그아웃했는지 (settings.logout_confirm_click / sidebar.logout_click)
 *
 * 순서가 중요해요. 이벤트를 다 보낸 뒤에 reset 해야 그 이벤트가 "로그아웃한 사람"에게 붙어요.
 * reset 을 먼저 하면 로그아웃 이벤트가 익명 사용자에게 붙어요.
 */
export async function logout(trackEvent?: AnalyticsEventName) {
  if (trackEvent) await trackBeforeLeave(trackEvent)
  resetAnalytics()
  await fetch('/api/auth/logout', { method: 'POST' }).catch(() => undefined)
  window.location.href = '/login?from=logout'
}
