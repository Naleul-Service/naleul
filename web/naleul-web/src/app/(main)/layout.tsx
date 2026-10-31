import { cookies } from 'next/headers'
import { AppShell } from '@/components/layout/AppShell'
import { COOKIE, type UserRole } from '@/lib/server/session'

// 로그인 사용자별 화면이라 정적 생성하지 않아요.
export const dynamic = 'force-dynamic'

export default async function MainLayout({ children }: { children: React.ReactNode }) {
  const store = await cookies()
  // TODO: 백엔드 "내 정보" API 가 확인되면 쿠키 대신 그 응답을 사용
  const user = {
    name: store.get(COOKIE.userName)?.value || '나를 사용자',
    role: store.get(COOKIE.userRole)?.value as UserRole | undefined,
    adminViewPlan: store.get(COOKIE.adminViewPlan)?.value === 'FREE' ? ('FREE' as const) : ('PRO' as const),
  }

  return <AppShell user={user}>{children}</AppShell>
}
