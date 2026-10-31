/**
 * BFF 프록시: 브라우저의 /api/v1/** 요청을 백엔드 /api/v1/** 로 그대로 전달해요.
 *
 *   브라우저 ──(쿠키)──▶ Next 서버 /api/v1/goal-creation/sessions
 *                        └─(Bearer 토큰)──▶ api.naleul.com/api/v1/goal-creation/sessions
 *
 * 그래서 기능마다 Route Handler 를 따로 만들 필요가 없어요.
 * 브라우저는 백엔드에 직접 요청하지 않으므로 CORS 도 필요 없어요.
 */
import type { NextRequest } from 'next/server'
import { backendFetch } from '@/lib/server/backend'
import { toRouteResponse } from '@/lib/server/routeResponse'

// Vercel 함수 최대 실행 시간(초). AI 인터뷰 응답이 수 초 걸려서 여유 있게 잡아요.
export const maxDuration = 60

// 인증 API 는 프록시하지 않아요. (로그인/재발급은 서버가 직접 처리)
const BLOCKED_PREFIXES = ['auth']

async function handler(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params

  if (BLOCKED_PREFIXES.includes(path[0])) {
    return Response.json({ success: false, status: 'NOT_FOUND', message: '찾을 수 없어요.' }, { status: 404 })
  }

  const backendPath = `/api/v1/${path.map(encodeURIComponent).join('/')}${req.nextUrl.search}`
  const hasBody = req.method !== 'GET' && req.method !== 'HEAD'
  const body = hasBody ? await req.text() : undefined

  const result = await backendFetch(backendPath, {
    method: req.method,
    body: body || undefined,
    headers: body ? { 'Content-Type': req.headers.get('content-type') ?? 'application/json' } : undefined,
  })

  return toRouteResponse(result)
}

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE }
