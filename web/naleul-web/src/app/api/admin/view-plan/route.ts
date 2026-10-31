/**
 * 운영진 플랜 바꿔 보기: PATCH /api/admin/view-plan → 백엔드 PATCH /api/v1/admin/view-plan
 *
 * 일반 BFF 프록시(/api/v1/**)를 쓰지 않고 따로 만든 이유:
 * 사이드바 배지 · 광고 · 설정의 Pro 표시는 httpOnly 쿠키(adminViewPlan)를 읽는데,
 * 브라우저 JS 로는 못 바꾸니까 서버가 성공 응답을 받은 자리에서 쿠키도 같이 바꿔요.
 * 운영진이 아니면 백엔드가 403 을 줘요.
 */
import { cookies } from 'next/headers'
import type { NextRequest } from 'next/server'
import { backendFetch } from '@/lib/server/backend'
import { toRouteResponse } from '@/lib/server/routeResponse'
import { setUserCookies, type AdminViewPlan } from '@/lib/server/session'

interface ViewPlanResult {
  plan: AdminViewPlan
  premium: boolean
}

export async function PATCH(req: NextRequest) {
  const body = await req.text()
  const result = await backendFetch<ViewPlanResult>('/api/v1/admin/view-plan', {
    method: 'PATCH',
    body,
  })
  if (result.ok && result.data?.plan) {
    // 백엔드가 id 목록으로만 운영진이던 계정을 ADMIN 으로 맞추므로 쿠키 역할도 같이 맞춰요
    setUserCookies(await cookies(), {
      userRole: 'ADMIN',
      adminViewPlan: result.data.plan,
    })
  }
  return toRouteResponse(result)
}
