/**
 * 목표 만들기처럼 사이드바 없이 화면 전체를 쓰는 플로우용 레이아웃.
 * (로그인 확인은 proxy.ts 가 해요)
 */
import { cookies } from 'next/headers'
import { GuestSessionTracker } from '@/components/analytics/GuestSessionTracker'
import { COOKIE } from '@/lib/server/session'

export const dynamic = 'force-dynamic'

export default async function FlowLayout({ children }: { children: React.ReactNode }) {
  // 목표 만들기 링크로 바로 들어온 둘러보기 사용자도 guest.session_start 를 보내요 (AppShell 을 안 거치므로)
  const isGuest = (await cookies()).get(COOKIE.userRole)?.value === 'GUEST'
  return (
    <>
      <GuestSessionTracker isGuest={isGuest} />
      {children}
    </>
  )
}
