/**
 * POST /api/billing/{prepare|confirm|cancel|resume} → 백엔드 /api/v1/web-billing/{action}
 * 구독 시작 · 해지 결과를 userRole 쿠키에도 바로 반영해요 (billingRoute.ts)
 */
import type { NextRequest } from 'next/server'
import { backendFetch } from '@/lib/server/backend'
import { syncRoleFromBilling } from '@/lib/server/billingRoute'
import { toRouteResponse } from '@/lib/server/routeResponse'

const ACTIONS = new Set(['prepare', 'confirm', 'cancel', 'resume'])

// 첫 결제는 카드사 승인을 기다려서 넉넉히
export const maxDuration = 60

export async function POST(req: NextRequest, ctx: { params: Promise<{ action: string }> }) {
  const { action } = await ctx.params
  if (!ACTIONS.has(action)) {
    return Response.json({ success: false, status: 'NOT_FOUND', message: '찾을 수 없어요.' }, { status: 404 })
  }
  const body = (await req.text()) || '{}'
  const result = await backendFetch<{ premium?: boolean }>(`/api/v1/web-billing/${action}`, {
    method: 'POST',
    body,
    headers: { 'Content-Type': 'application/json' },
  })
  await syncRoleFromBilling(result)
  return toRouteResponse(result)
}
