/**
 * GET /api/billing → 백엔드 GET /api/v1/web-billing (구독 상태)
 * 일반 BFF 프록시 대신 따로 둔 이유: 응답을 보고 userRole 쿠키(FREE/PRO)를 맞추려고 (billingRoute.ts)
 */
import { backendFetch } from '@/lib/server/backend'
import { syncRoleFromBilling } from '@/lib/server/billingRoute'
import { toRouteResponse } from '@/lib/server/routeResponse'

export async function GET() {
  const result = await backendFetch<{ premium?: boolean }>('/api/v1/web-billing')
  await syncRoleFromBilling(result)
  return toRouteResponse(result)
}
