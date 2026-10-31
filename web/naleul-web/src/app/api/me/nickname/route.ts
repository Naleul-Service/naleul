/**
 * 닉네임 수정: PATCH /api/me/nickname → 백엔드 PATCH /api/v1/users/nickname
 *
 * 일반 BFF 프록시(/api/v1/**)를 쓰지 않고 따로 만든 이유:
 * 사이드바에 보이는 이름은 httpOnly 쿠키(userName)에서 읽어요.
 * httpOnly 라 브라우저 JS 로는 못 바꾸니까, 서버가 성공 응답을 받은 자리에서 쿠키도 같이 바꿔줘야 해요.
 */
import { cookies } from 'next/headers'
import type { NextRequest } from 'next/server'
import { backendFetch } from '@/lib/server/backend'
import { toRouteResponse } from '@/lib/server/routeResponse'
import { setUserCookies } from '@/lib/server/session'

interface NicknameUpdated {
  userId: number
  nickname: string
}

export async function PATCH(req: NextRequest) {
  const body = await req.text()
  const result = await backendFetch<NicknameUpdated>('/api/v1/users/nickname', { method: 'PATCH', body })

  if (result.ok && result.data?.nickname) {
    const store = await cookies()
    setUserCookies(store, { userName: result.data.nickname })
  }
  return toRouteResponse(result)
}
