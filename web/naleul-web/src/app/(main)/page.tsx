import type { Metadata } from 'next'
import { cookies } from 'next/headers'
import { COOKIE } from '@/lib/server/session'
import { HomeView } from '@/features/home/ui/HomeView'

export const metadata: Metadata = { title: '홈' }

export default async function HomePage() {
  const store = await cookies()
  // 레이아웃과 같은 이름 (백엔드 "내 정보" API 가 생기면 그걸로)
  const name = store.get(COOKIE.userName)?.value || '나를 사용자'
  return <HomeView userName={name} />
}
