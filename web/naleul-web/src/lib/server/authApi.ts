/**
 * 백엔드 인증 API 호출 (로그인 / 토큰 재발급).
 * proxy.ts 에서도 쓰므로 'server-only' 를 넣지 않았어요. (브라우저에서 import 하지 마세요)
 */
import type { ApiEnvelope } from '@/types/api'
import { BACKEND_PATHS, backendUrl } from './backendPaths'
import type { SessionTokens, UserRole } from './session'

const stripBearer = (v: string | null) => v?.replace(/^Bearer\s+/i, '').trim() || undefined

/**
 * 같은 refreshToken 으로 동시에 여러 번 재발급하지 않도록 묶어요.
 * (화면 하나에서 API 3개가 동시에 401을 받으면 재발급도 3번 나가는 문제 방지)
 * 서버 인스턴스 하나 안에서만 효과가 있어요.
 */
const inflight = new Map<string, Promise<ReissueResult>>()

/**
 * - SessionTokens: 성공
 * - null: refresh token 이 정말 무효 (401/400) → 다시 로그인해야 함
 * - 'unavailable': 서버가 잠깐 응답을 못 함 (5xx, 네트워크) → 로그아웃시키지 말고 쿠키 유지
 *   (예전엔 이 경우도 로그아웃시켜서, 배포 중이나 서버가 잠깐 느릴 때 로그인이 풀렸어요)
 */
export type ReissueResult = SessionTokens | null | 'unavailable'

export function reissueAccessToken(refreshToken: string): Promise<ReissueResult> {
  const existing = inflight.get(refreshToken)
  if (existing) return existing

  const task = (async (): Promise<ReissueResult> => {
    try {
      const res = await fetch(backendUrl(BACKEND_PATHS.tokenReissue), {
        method: 'GET',
        headers: { 'Authorization-Refresh': `Bearer ${refreshToken}` },
        cache: 'no-store',
        signal: AbortSignal.timeout(10_000),
      })
      if (!res.ok) return res.status === 400 || res.status === 401 || res.status === 403 ? null : 'unavailable'

      // 새 access 는 응답 헤더 Authorization 에 담겨 와요.
      // 혹시 body(data.accessToken)로 오는 경우도 대비해요.
      let accessToken = stripBearer(res.headers.get('authorization'))
      let newRefresh = stripBearer(res.headers.get('authorization-refresh'))
      if (!accessToken) {
        const body = (await res.json().catch(() => null)) as ApiEnvelope<{
          accessToken?: string
          refreshToken?: string
        }> | null
        accessToken = body?.data?.accessToken
        newRefresh = newRefresh ?? body?.data?.refreshToken
      }
      if (!accessToken) return null

      return { accessToken, refreshToken: newRefresh }
    } catch (error) {
      console.error('[auth] token reissue failed', error)
      return 'unavailable'
    } finally {
      // 잠깐 뒤에 지워서, 거의 동시에 들어온 요청도 같은 결과를 쓰게 해요.
      setTimeout(() => inflight.delete(refreshToken), 5_000)
    }
  })()

  inflight.set(refreshToken, task)
  return task
}

/**
 * 로그인 없이 둘러보기 — 읽기 전용 access token 만 받아요 (refresh 없음, 30일).
 * 저장하려고 하면 백엔드가 401 GUEST_LOGIN_REQUIRED 를 줘요.
 */
export async function guestLogin(): Promise<string | null> {
  try {
    const res = await fetch(backendUrl(BACKEND_PATHS.guest), {
      method: 'POST',
      cache: 'no-store',
      signal: AbortSignal.timeout(8_000),
    })
    const body = (await res.json().catch(() => null)) as ApiEnvelope<{ accessToken?: string }> | null
    return res.ok && body?.data?.accessToken ? body.data.accessToken : null
  } catch (error) {
    console.error('[auth] guest login failed', error)
    return null
  }
}

/** 백엔드에 이 기기의 refresh token 무효화를 알려요 (실패해도 쿠키는 지워요) */
export async function revokeRefreshToken(refreshToken: string): Promise<void> {
  try {
    await fetch(backendUrl(BACKEND_PATHS.logout), {
      method: 'POST',
      headers: { 'Authorization-Refresh': `Bearer ${refreshToken}` },
      cache: 'no-store',
      signal: AbortSignal.timeout(5_000),
    })
  } catch (error) {
    console.error('[auth] logout failed', error)
  }
}

export interface KakaoLoginData {
  accessToken: string
  refreshToken: string
  userId: number
  userName?: string
  userEmail?: string
  userRole?: UserRole
  /** 운영진이 보고 있는 플랜 (운영진이 아니면 null) */
  adminViewPlan?: 'FREE' | 'PRO' | null
  /** 이번 로그인이 신규 가입인지 (백엔드 LoginResponse.isNewUser) */
  isNewUser?: boolean
}

/**
 * Apple 로그인 — iOS 앱과 같은 백엔드 API 를 써요.
 * identityToken 의 sub(Apple 사용자 ID)로 회원을 찾기 때문에, 앱에서 Apple 로 가입한 계정에 그대로 로그인돼요.
 */
export async function appleLogin(identityToken: string, fullName?: string): Promise<KakaoLoginData | null> {
  try {
    const res = await fetch(backendUrl(BACKEND_PATHS.appleLogin), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identityToken, fullName }),
      cache: 'no-store',
      signal: AbortSignal.timeout(15_000),
    })
    const body = (await res.json().catch(() => null)) as ApiEnvelope<KakaoLoginData> | null
    if (!res.ok || !body?.success || !body.data?.accessToken) {
      console.error('[auth] apple login failed', res.status, body?.message)
      return null
    }
    return body.data
  } catch (error) {
    console.error('[auth] apple login error', error)
    return null
  }
}

export async function kakaoLogin(code: string): Promise<KakaoLoginData | null> {
  const url = new URL(backendUrl(BACKEND_PATHS.kakaoLogin))
  url.searchParams.set('code', code)
  url.searchParams.set('redirectUri', process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI ?? '')

  try {
    const res = await fetch(url, { method: 'GET', cache: 'no-store', signal: AbortSignal.timeout(15_000) })
    const body = (await res.json().catch(() => null)) as ApiEnvelope<KakaoLoginData> | null
    if (!res.ok || !body?.success || !body.data?.accessToken) {
      console.error('[auth] kakao login failed', res.status, body?.message)
      return null
    }
    return body.data
  } catch (error) {
    console.error('[auth] kakao login error', error)
    return null
  }
}
