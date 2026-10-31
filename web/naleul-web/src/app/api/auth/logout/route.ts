import { cookies } from 'next/headers'
import { revokeRefreshToken } from '@/lib/server/authApi'
import { COOKIE, clearSessionCookies } from '@/lib/server/session'

/**
 * 로그아웃: 백엔드에서 이 기기의 refresh token 을 지우고(다른 기기 로그인은 유지),
 * 브라우저 쿠키도 삭제해요. (Route Handler 응답이라 실제로 브라우저에서 지워져요)
 */
export async function POST() {
  const store = await cookies()
  const refresh = store.get(COOKIE.refresh)?.value
  if (refresh) await revokeRefreshToken(refresh)
  clearSessionCookies(store)
  return new Response(null, { status: 204 })
}
