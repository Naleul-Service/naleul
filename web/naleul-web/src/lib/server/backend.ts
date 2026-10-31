/**
 * ⚠️ 서버 전용 (Route Handler / Server Component / Server Action)
 *
 * 백엔드(api.naleul.com) 호출의 유일한 통로예요.
 *  1. 쿠키에서 accessToken 을 꺼내 Authorization 헤더에 넣어요.
 *  2. 토큰이 만료 직전이면 "먼저" 재발급해요. (선제 갱신)
 *  3. 그래도 401 이 오면 한 번 재발급 후 재시도해요.
 *  4. 응답을 상태코드·message·data 그대로 돌려줘요. (422 violations 가 사라지지 않게)
 *
 * 403 은 토큰 문제로 취급하지 않아요. 403 = "권한/플랜 한도" 같은 비즈니스 에러예요.
 */
import 'server-only'
import { cookies } from 'next/headers'
import { isJwtExpired } from '@/lib/jwt'
import { backendUrl } from './backendPaths'
import { guestLogin, reissueAccessToken } from './authApi'
import {
  COOKIE,
  clearSessionCookies,
  setGuestCookies,
  setTokenCookies,
  type CookieWriter,
  type SessionTokens,
} from './session'

export interface BackendResult<T = unknown> {
  ok: boolean
  /** 숫자 HTTP 상태코드 (200, 201, 204, 409, 422 ...) */
  httpStatus: number
  /** 백엔드 ApiResponse.status ("OK", "CONFLICT" ...) */
  status?: string
  message?: string
  data?: T
}

type CookieStore = Awaited<ReturnType<typeof cookies>>

const DEFAULT_MESSAGES: Record<number, string> = {
  400: '요청 내용을 다시 확인해 주세요.',
  401: '로그인이 필요해요.',
  403: '이 작업을 할 수 있는 권한이 없어요.',
  404: '찾을 수 없어요.',
  409: '지금은 이 작업을 할 수 없어요.',
  429: '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.',
  500: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
  502: '응답을 받지 못했어요. 다시 시도해 주세요.',
  503: '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.',
  504: '응답이 너무 늦어요. 다시 시도해 주세요.',
}

export const defaultMessage = (status: number) => DEFAULT_MESSAGES[status] ?? '문제가 생겼어요. 다시 시도해 주세요.'

/**
 * Server Component 렌더링 중에는 쿠키를 쓸 수 없어요(Next.js 제약 → 에러 발생).
 * Route Handler / Server Action 에서만 실제로 저장되고, 나머지는 조용히 무시해요.
 * (페이지 이동 시에는 proxy.ts 가 미리 갱신해 두므로 문제 없음)
 */
function safeWriter(store: CookieStore): CookieWriter {
  return {
    set: (name, value, options) => {
      try {
        store.set(name, value, options)
      } catch {
        /* Server Component 에서는 무시 */
      }
    },
    delete: (name) => {
      try {
        store.delete(name)
      } catch {
        /* Server Component 에서는 무시 */
      }
    },
  }
}

/** @returns 새 토큰 / null(무효 → 쿠키 삭제) / 'unavailable'(잠깐 실패 → 쿠키 유지) */
async function refreshSession(store: CookieStore, refreshToken: string): Promise<SessionTokens | null | 'unavailable'> {
  const result = await reissueAccessToken(refreshToken)
  const writer = safeWriter(store)
  if (result === 'unavailable') return result
  if (result) setTokenCookies(writer, result)
  else clearSessionCookies(writer)
  return result
}

async function send(path: string, init: RequestInit, accessToken?: string): Promise<Response | BackendResult<never>> {
  const headers = new Headers(init.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  try {
    return await fetch(backendUrl(path), {
      ...init,
      headers,
      cache: 'no-store',
      signal: init.signal ?? AbortSignal.timeout(55_000),
    })
  } catch (error) {
    const isTimeout = error instanceof DOMException && error.name === 'TimeoutError'
    const httpStatus = isTimeout ? 504 : 503
    console.error('[backend] fetch failed', path, error)
    return { ok: false, httpStatus, message: defaultMessage(httpStatus) }
  }
}

export async function parseBackendResponse<T>(res: Response): Promise<BackendResult<T>> {
  if (res.status === 204) return { ok: true, httpStatus: 204 }

  const text = await res.text()
  let json: unknown
  try {
    json = text ? JSON.parse(text) : undefined
  } catch {
    json = undefined
  }

  // 정상적인 ApiResponse 형식
  if (json && typeof json === 'object' && 'success' in json) {
    const body = json as { success: boolean; status?: string; message?: string; data?: T }
    const ok = res.ok && body.success !== false
    return {
      ok,
      httpStatus: res.status,
      status: body.status,
      message: body.message ?? (ok ? undefined : defaultMessage(res.status)),
      data: body.data,
    }
  }

  // Spring Security 기본 403 처럼 body 가 비었거나 JSON 이 아닌 경우
  return {
    ok: res.ok,
    httpStatus: res.status,
    message: res.ok ? undefined : defaultMessage(res.status),
    data: json as T | undefined,
  }
}

/** 둘러보기(로그인 전)에서 저장하려고 할 때 — 클라이언트가 이 status 를 보고 로그인 안내를 띄워요 */
export const GUEST_LOGIN_REQUIRED = 'GUEST_LOGIN_REQUIRED'
const guestBlocked = (): BackendResult<never> => ({
  ok: false,
  httpStatus: 401,
  status: GUEST_LOGIN_REQUIRED,
  message: '로그인하면 저장할 수 있어요.',
})

export async function backendFetch<T = unknown>(path: string, init: RequestInit = {}): Promise<BackendResult<T>> {
  const store = await cookies()
  const refreshToken = store.get(COOKIE.refresh)?.value
  let accessToken = store.get(COOKIE.access)?.value
  const guest = store.get(COOKIE.userRole)?.value === 'GUEST'
  const method = (init.method ?? 'GET').toUpperCase()

  if (guest) {
    // 둘러보기는 읽기만 — 백엔드에 보내기 전에 막아요 (백엔드 필터도 같은 규칙)
    if (method !== 'GET' && method !== 'HEAD') return guestBlocked()
    if (!accessToken || isJwtExpired(accessToken)) {
      accessToken = (await guestLogin()) ?? undefined
      if (accessToken) setGuestCookies(safeWriter(store), accessToken)
    }
    if (!accessToken) return { ok: false, httpStatus: 503, message: defaultMessage(503) }
    const res = await send(path, init, accessToken)
    return res instanceof Response ? parseBackendResponse<T>(res) : res
  }

  // ① 선제 갱신: 만료(또는 60초 안에 만료)된 토큰은 보내지 않고 먼저 재발급
  if ((!accessToken || isJwtExpired(accessToken)) && refreshToken) {
    const r = await refreshSession(store, refreshToken)
    if (r === 'unavailable') return { ok: false, httpStatus: 503, message: defaultMessage(503) }
    accessToken = r?.accessToken
  }
  if (!accessToken) {
    clearSessionCookies(safeWriter(store))
    return { ok: false, httpStatus: 401, message: defaultMessage(401) }
  }

  let res = await send(path, init, accessToken)

  // ② 사후 갱신: 그래도 401 이면 한 번만 재발급 후 재시도
  if (res instanceof Response && res.status === 401 && refreshToken) {
    const tokens = await refreshSession(store, refreshToken)
    if (tokens === 'unavailable') return { ok: false, httpStatus: 503, message: defaultMessage(503) }
    if (tokens) res = await send(path, init, tokens.accessToken)
  }

  if (!(res instanceof Response)) return res
  if (res.status === 401) clearSessionCookies(safeWriter(store))
  return parseBackendResponse<T>(res)
}
