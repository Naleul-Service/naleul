/**
 * 로그인 쿠키 관련 상수/헬퍼.
 * proxy.ts 에서도 import 하므로 'server-only' 와 next/headers 를 쓰지 않아요.
 *
 * 쿠키를 쓰는 곳은 두 종류예요.
 *  1) Route Handler / Server Action 의 `await cookies()`
 *  2) NextResponse 의 `response.cookies`
 * 둘 다 set/delete 메서드를 가지고 있어서 아래 CookieWriter 하나로 받아요.
 */
import { secondsUntilExp } from '@/lib/jwt'
import {
  GUEST_STARTED_COOKIE,
  GUEST_STARTED_MAX_AGE_SEC,
  LOGIN_RESULT_COOKIE,
  LOGIN_RESULT_MAX_AGE_SEC,
  encodeLoginResult,
  type LoginResult,
} from '@/lib/loginAnalytics'

export const COOKIE = {
  access: 'accessToken',
  refresh: 'refreshToken',
  userId: 'userId',
  userName: 'userName',
  userRole: 'userRole',
  /** 운영진(ADMIN)이 보고 있는 플랜 FREE | PRO — 없으면 PRO (2026-10-24) */
  adminViewPlan: 'adminViewPlan',
} as const

/** GUEST = 로그인 없이 둘러보기 (읽기 전용 토큰) */
export type UserRole = 'FREE' | 'PRO' | 'ADMIN' | 'GUEST'

/** 운영진이 보고 있는 플랜 */
export type AdminViewPlan = 'FREE' | 'PRO'

export const GUEST_NAME = '둘러보기'

interface CookieOptions {
  httpOnly?: boolean
  secure?: boolean
  sameSite?: 'lax' | 'strict' | 'none'
  path?: string
  maxAge?: number
}

export interface CookieWriter {
  set(name: string, value: string, options?: CookieOptions): unknown
  delete(name: string): unknown
}

const ACCESS_FALLBACK_SEC = 60 * 60 // 백엔드 access 만료 1시간
const REFRESH_FALLBACK_SEC = 60 * 60 * 24 * 60 // refresh 만료를 못 읽을 때 기본 60일 (쓸 때마다 연장)

function baseOptions(maxAge: number): CookieOptions {
  return {
    httpOnly: true, // JS 에서 읽을 수 없게 → XSS 로 토큰 탈취 방지
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge,
  }
}

export interface SessionTokens {
  accessToken: string
  refreshToken?: string // 재발급 시 새로 안 주면 undefined → 기존 것 유지
}

export interface SessionUser {
  userId?: number | string
  userName?: string
  userRole?: UserRole
  /** 운영진일 때만 (로그인 응답 · 플랜 바꿔 보기) */
  adminViewPlan?: AdminViewPlan | null
}

/** 토큰 쿠키 저장. 쿠키 수명을 토큰 실제 만료 시각에 맞춰요. */
export function setTokenCookies(writer: CookieWriter, tokens: SessionTokens) {
  writer.set(COOKIE.access, tokens.accessToken, baseOptions(secondsUntilExp(tokens.accessToken, ACCESS_FALLBACK_SEC)))
  if (tokens.refreshToken) {
    writer.set(
      COOKIE.refresh,
      tokens.refreshToken,
      baseOptions(secondsUntilExp(tokens.refreshToken, REFRESH_FALLBACK_SEC))
    )
  }
}

/**
 * 사이드바 표시용 사용자 정보. 서버 컴포넌트에서만 읽으므로 httpOnly 로 둬요.
 * refresh token 은 쓸 때마다 연장되는데 이 쿠키는 로그인 때만 저장돼서, 수명을 길게(400일, 브라우저 최대) 잡아요.
 * 로그아웃하면 같이 지워지고, 토큰이 없으면 proxy 가 로그인 화면으로 보내므로 남아 있어도 문제없어요.
 */
const USER_COOKIE_SEC = 60 * 60 * 24 * 400

export function setUserCookies(writer: CookieWriter, user: SessionUser) {
  const opts = baseOptions(USER_COOKIE_SEC)
  if (user.userId !== undefined && user.userId !== null) writer.set(COOKIE.userId, String(user.userId), opts)
  if (user.userName) writer.set(COOKIE.userName, user.userName, opts)
  if (user.userRole) writer.set(COOKIE.userRole, user.userRole, opts)
  if (user.adminViewPlan) writer.set(COOKIE.adminViewPlan, user.adminViewPlan, opts)
  // 운영진이 아닌 계정으로 로그인하면 예전 운영진 플랜 쿠키를 지워요
  else if (user.userRole && user.userRole !== 'ADMIN') writer.delete(COOKIE.adminViewPlan)
}

/** 둘러보기 세션 저장: 읽기 전용 토큰 + 표시용 이름/역할 */
export function setGuestCookies(writer: CookieWriter, accessToken: string) {
  writer.set(COOKIE.access, accessToken, baseOptions(secondsUntilExp(accessToken, 60 * 60 * 24 * 30)))
  writer.delete(COOKIE.refresh)
  const opts = baseOptions(USER_COOKIE_SEC)
  writer.set(COOKIE.userName, GUEST_NAME, opts)
  writer.set(COOKIE.userRole, 'GUEST', opts)
}

/** 둘러보기 토큰을 방금 발급했다는 1회용 표시 (guest.session_start 의 is_new_guest). 브라우저 JS 가 읽어야 해서 httpOnly 가 아니에요 */
export function setGuestStartedCookie(writer: CookieWriter) {
  writer.set(GUEST_STARTED_COOKIE, '1', {
    httpOnly: false,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: GUEST_STARTED_MAX_AGE_SEC,
  })
}

export function clearSessionCookies(writer: CookieWriter) {
  Object.values(COOKIE).forEach((name) => writer.delete(name))
}

/**
 * 로그인 성공 결과를 1회용 쿠키로 남겨요 → 첫 화면(AnalyticsBootstrap)이 읽어서 login.complete 를 보내고 지워요.
 * 브라우저 JS 가 읽어야 하므로 httpOnly 가 아니에요. (토큰은 넣지 않아요)
 */
export function setLoginResultCookie(writer: CookieWriter, result: LoginResult) {
  writer.set(LOGIN_RESULT_COOKIE, encodeLoginResult(result), {
    httpOnly: false,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: LOGIN_RESULT_MAX_AGE_SEC,
  })
}
