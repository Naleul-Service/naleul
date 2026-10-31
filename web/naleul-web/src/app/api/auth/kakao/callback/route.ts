/**
 * 카카오 로그인 콜백 (카카오 콘솔 Redirect URI: {웹 주소}/api/auth/kakao/callback)
 *
 * 1. 카카오가 ?code=...&state=... 로 이 주소를 호출해요.
 * 2. code 를 백엔드에 넘겨 우리 서비스 JWT 를 받아요.
 * 3. 쿠키를 "리다이렉트 응답 자체"에 담아서 보내요. → 브라우저에 확실히 저장돼요.
 * 4. state 에 담아둔 원래 가려던 페이지로 이동해요.
 *    실패해도 원래 페이지로 돌려보내고 ?loginError=... 를 붙여요 → 그 화면에서 로그인 모달이 에러와 함께 다시 떠요.
 */
import { NextResponse, type NextRequest } from 'next/server'
import { kakaoLogin } from '@/lib/server/authApi'
import { clearSessionCookies, setLoginResultCookie, setTokenCookies, setUserCookies } from '@/lib/server/session'
import { safeRedirectPath } from '@/lib/redirectPath'
import { LOGIN_ERROR_PARAM } from '@/components/auth/kakaoAuth'
import { LOGIN_PROVIDER_PARAM } from '@/lib/loginAnalytics'

export async function GET(request: NextRequest) {
  const { searchParams, origin } = request.nextUrl
  const code = searchParams.get('code')
  const kakaoError = searchParams.get('error')
  const next = safeRedirectPath(searchParams.get('state'))

  // 로그인 페이지 대신 원래 보던 화면으로 돌려보내요 (LoginRequiredDialog 가 loginError 를 보고 모달을 열어요)
  const toLogin = (error: string) => {
    const url = new URL(next, origin)
    url.searchParams.set(LOGIN_ERROR_PARAM, error)
    url.searchParams.set(LOGIN_PROVIDER_PARAM, 'kakao') // login.fail 이벤트용
    return NextResponse.redirect(url)
  }

  if (kakaoError) return toLogin('kakao_cancelled')
  if (!code) return toLogin('no_code')

  const result = await kakaoLogin(code)
  if (!result) return toLogin('login_failed')

  const response = NextResponse.redirect(new URL(next, origin))
  // 둘러보기 쿠키(userRole=GUEST, 이름 "둘러보기")가 남지 않게 먼저 모두 지우고 새로 넣어요
  clearSessionCookies(response.cookies)
  setTokenCookies(response.cookies, { accessToken: result.accessToken, refreshToken: result.refreshToken })
  setUserCookies(response.cookies, { ...result, userRole: result.userRole ?? 'FREE' })
  // login.complete 이벤트용 1회용 쿠키 (첫 화면에서 읽고 지워요)
  setLoginResultCookie(response.cookies, {
    provider: 'kakao',
    userId: String(result.userId),
    userRole: result.userRole ?? 'FREE',
    isNewUser: result.isNewUser,
  })
  return response
}
