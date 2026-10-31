/**
 * 로그인 이벤트(login.complete · login.fail)를 위한 정보 전달 도구.
 * 서버(카카오 · Apple 콜백 Route Handler)와 브라우저가 같이 써요. → window 는 함수 안에서만 써요.
 *
 * 왜 필요한가요?
 *  카카오 · Apple 로그인은 "우리 사이트 → 카카오/Apple → 콜백(서버) → 리다이렉트" 순서로 페이지가 통째로 바뀌어요.
 *  그래서 ① 버튼을 누른 화면(login_page / login_modal) ② 로그인 결과(신규 가입 여부 등)를
 *  리다이렉트 너머로 넘겨줄 방법이 따로 있어야 해요.
 *
 *  ① 시도 정보 → sessionStorage (같은 탭이면 다른 사이트에 다녀와도 남아 있어요)
 *     Apple 콜백은 다른 사이트에서 오는 POST 라서 SameSite=Lax 쿠키가 안 실려요. 그래서 쿠키 대신 sessionStorage 를 써요.
 *  ② 성공 결과 → 콜백이 1회용 쿠키(LOGIN_RESULT_COOKIE)에 담아요 → 첫 화면에서 읽고 바로 지워요
 *  ③ 실패 → 기존 ?loginError=... 에 ?loginProvider=kakao|apple 를 같이 붙여요
 */

export type LoginProvider = 'kakao' | 'apple'
export type LoginSurface = 'login_page' | 'login_modal'

/** 로그인 성공 결과를 담는 1회용 쿠키 (JS 에서 읽어야 해서 httpOnly 가 아니에요. 토큰은 절대 넣지 않아요) */
export const LOGIN_RESULT_COOKIE = 'naleul_login_result'
export const LOGIN_RESULT_MAX_AGE_SEC = 60

/**
 * 둘러보기 토큰을 "방금" 발급했다는 1회용 표시 (proxy.ts 가 심고, GuestSessionTracker 가 읽고 지워요)
 * → guest.session_start 의 is_new_guest = true
 */
export const GUEST_STARTED_COOKIE = 'naleul_guest_started'
export const GUEST_STARTED_MAX_AGE_SEC = 60

/** 로그인 실패로 돌아올 때 붙이는 쿼리 (?loginError 와 같이) */
export const LOGIN_PROVIDER_PARAM = 'loginProvider'

export interface LoginResult {
  provider: LoginProvider
  userId: string
  userRole?: string
  isNewUser?: boolean
}

export function encodeLoginResult(result: LoginResult) {
  return encodeURIComponent(JSON.stringify(result))
}

export function decodeLoginResult(raw: string | undefined | null): LoginResult | null {
  if (!raw) return null
  try {
    const v = JSON.parse(decodeURIComponent(raw)) as Partial<LoginResult>
    if ((v.provider !== 'kakao' && v.provider !== 'apple') || !v.userId) return null
    return { provider: v.provider, userId: String(v.userId), userRole: v.userRole, isNewUser: v.isNewUser }
  } catch {
    return null
  }
}

/** Tracking Plan 의 login_error_code (cancelled / no_code / login_failed) */
export function loginErrorCodeOf(code: string | null | undefined): 'cancelled' | 'no_code' | 'login_failed' | undefined {
  if (!code) return undefined
  if (code === 'cancelled' || code === 'kakao_cancelled' || code === 'apple_cancelled') return 'cancelled'
  if (code === 'no_code') return 'no_code'
  return 'login_failed'
}

export function loginProviderOf(value: string | null | undefined): LoginProvider | undefined {
  return value === 'kakao' || value === 'apple' ? value : undefined
}

/* ---------- 브라우저 전용: 로그인 시도 정보 (sessionStorage) ---------- */

const ATTEMPT_KEY = 'naleul.login_attempt'
const ATTEMPT_TTL_MS = 30 * 60 * 1000 // 30분 지난 시도는 무시

export interface LoginAttempt {
  provider: LoginProvider
  surface: LoginSurface
  at: number
}

export function saveLoginAttempt(provider: LoginProvider, surface: LoginSurface) {
  try {
    sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify({ provider, surface, at: Date.now() } satisfies LoginAttempt))
  } catch {
    // 시크릿 모드 등에서 저장이 막혀도 로그인은 그대로 진행돼요 (login_surface 만 빠져요)
  }
}

/** 버튼을 누른 뒤 지난 초 (seconds_since_click) */
export const secondsSince = (attempt: LoginAttempt | null) =>
  attempt ? Math.max(0, Math.round((Date.now() - attempt.at) / 1000)) : undefined

/** 시도 정보를 꺼내고 지워요 (한 번만 쓰도록) */
export function takeLoginAttempt(): LoginAttempt | null {
  try {
    const raw = sessionStorage.getItem(ATTEMPT_KEY)
    sessionStorage.removeItem(ATTEMPT_KEY)
    if (!raw) return null
    const v = JSON.parse(raw) as LoginAttempt
    return Date.now() - v.at < ATTEMPT_TTL_MS ? v : null
  } catch {
    return null
  }
}

/** 1회용 쿠키를 읽고 바로 지워요 (없으면 undefined) */
function takeCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined
  const prefix = `${name}=`
  const raw = document.cookie
    .split('; ')
    .find((c) => c.startsWith(prefix))
    ?.slice(prefix.length)
  if (raw !== undefined) document.cookie = `${name}=; Max-Age=0; Path=/`
  return raw
}

/** 1회용 결과 쿠키를 읽고 바로 지워요 */
export function takeLoginResultCookie(): LoginResult | null {
  const raw = takeCookie(LOGIN_RESULT_COOKIE)
  return raw === undefined ? null : decodeLoginResult(raw)
}

/** 둘러보기 토큰을 방금 받았는지 (읽으면 지워져요) */
export function takeGuestStartedCookie(): boolean {
  return takeCookie(GUEST_STARTED_COOKIE) !== undefined
}
