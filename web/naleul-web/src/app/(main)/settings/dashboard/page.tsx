import type { Metadata } from 'next'
import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { DashboardView } from '@/features/admin-dashboard/ui/DashboardView'
import { COOKIE } from '@/lib/server/session'

export const metadata: Metadata = { title: '운영 대시보드' }

/**
 * /settings/dashboard — 운영 대시보드 (운영진 전용, 2026-10-24).
 * 운영진이 아니면 설정으로 돌려보내요. 쿠키를 바꿔도 백엔드(AdminGuard)가 403 으로 한 번 더 막아요.
 */
export default async function AdminDashboardPage() {
  if ((await cookies()).get(COOKIE.userRole)?.value !== 'ADMIN') redirect('/settings')
  return <DashboardView />
}
