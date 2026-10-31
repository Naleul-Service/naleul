import 'server-only'
import { NextResponse } from 'next/server'
import type { ApiEnvelope } from '@/types/api'
import type { BackendResult } from './backend'

/**
 * BackendResult → 브라우저로 보낼 응답.
 * 백엔드의 HTTP 상태코드와 body(success/status/message/data)를 "그대로" 전달해요.
 */
export function toRouteResponse<T>(result: BackendResult<T>): Response {
  if (result.httpStatus === 204) return new Response(null, { status: 204 })

  const body: ApiEnvelope<T> = {
    success: result.ok,
    status: result.status,
    message: result.message,
    data: result.data,
  }
  return NextResponse.json(body, { status: result.httpStatus })
}
