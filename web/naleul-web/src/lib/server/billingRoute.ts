import 'server-only'
import { cookies } from 'next/headers'
import type { BackendResult } from './backend'
import { COOKIE, setUserCookies, type UserRole } from './session'

/**
 * 웹 구독 상태 응답을 받은 자리에서 userRole 쿠키를 맞춰요.
 * 사이드바 · 설정의 FREE/PRO 표시와 Pro 전용 화면 잠금은 httpOnly 쿠키(userRole)를 읽는데,
 * 브라우저 JS 로는 못 바꾸니까 구독 시작 · 만료가 바로 반영되도록 서버에서 바꿔요. ADMIN 은 건드리지 않아요.
 */
export async function syncRoleFromBilling(result: BackendResult<{ premium?: boolean }>) {
  if (!result.ok || typeof result.data?.premium !== 'boolean') return
  const store = await cookies()
  const current = store.get(COOKIE.userRole)?.value as UserRole | undefined
  // ADMIN · 둘러보기(GUEST)는 구독 상태로 바꾸지 않아요 (GUEST 가 FREE 로 바뀌면 로그인 안내가 사라져요)
  if (current === 'ADMIN' || current === 'GUEST') return
  const next: UserRole = result.data.premium ? 'PRO' : 'FREE'
  if (current !== next) setUserCookies(store, { userRole: next })
}
