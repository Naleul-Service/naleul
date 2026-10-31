/**
 * Apple 로그인 콜백 (Apple Developer → Services ID → Return URLs: {웹 주소}/api/auth/apple/callback)
 *
 * 카카오와 다른 점: Apple 은 GET 이 아니라 "POST 폼(form_post)"으로 이 주소를 호출해요.
 *  - id_token : Apple 이 서명한 JWT → 백엔드가 검증하고 우리 서비스 JWT 를 발급해요 (iOS 앱과 같은 API)
 *  - state    : 로그인 전에 보던 경로
 *  - user     : 처음 로그인할 때만 오는 이름/이메일 JSON
 *  - error    : 사용자가 취소하면 user_cancelled_authorize → apple_cancelled 로 알려요
 *
 * 응답은 303 리다이렉트예요. (POST 다음에 GET 으로 화면을 열게 하는 상태코드 — 302 면 브라우저가 POST 를 다시 보낼 수 있어요)
 */
import { NextResponse, type NextRequest } from 'next/server'
import { appleLogin } from '@/lib/server/authApi'
import { clearSessionCookies, setLoginResultCookie, setTokenCookies, setUserCookies } from '@/lib/server/session'
import { safeRedirectPath } from '@/lib/redirectPath'
import { LOGIN_ERROR_PARAM } from '@/components/auth/kakaoAuth'
import { LOGIN_PROVIDER_PARAM } from '@/lib/loginAnalytics'

interface AppleUserJson {
  name?: { firstName?: string; lastName?: string }
}

/** 한국식으로 성+이름 붙이기 ("황" + "유림" → "황유림"). 영문 이름이면 띄어 써요. */
function fullNameOf(userJson: string | null): string | undefined {
  if (!userJson) return undefined
  try {
    const { name } = JSON.parse(userJson) as AppleUserJson
    const first = name?.firstName?.trim() ?? ''
    const last = name?.lastName?.trim() ?? ''
    if (!first && !last) return undefined
    const isKorean = /[가-힣]/.test(first + last)
    return isKorean ? `${last}${first}` : [first, last].filter(Boolean).join(' ')
  } catch {
    return undefined
  }
}

export async function POST(request: NextRequest) {
  const { origin } = request.nextUrl
  const form = await request.formData().catch(() => null)
  const field = (key: string) => {
    const v = form?.get(key)
    return typeof v === 'string' ? v : null
  }

  const next = safeRedirectPath(field('state'))
  const fail = (error: string) => {
    const url = new URL(next, origin)
    url.searchParams.set(LOGIN_ERROR_PARAM, error)
    url.searchParams.set(LOGIN_PROVIDER_PARAM, 'apple') // login.fail 이벤트용
    return NextResponse.redirect(url, 303)
  }

  if (field('error')) return fail('apple_cancelled')
  const idToken = field('id_token')
  if (!idToken) return fail('no_code')

  const result = await appleLogin(idToken, fullNameOf(field('user')))
  if (!result) return fail('login_failed')

  const response = NextResponse.redirect(new URL(next, origin), 303)
  // 둘러보기 쿠키(userRole=GUEST)가 남지 않게 먼저 모두 지우고 새로 넣어요
  clearSessionCookies(response.cookies)
  setTokenCookies(response.cookies, { accessToken: result.accessToken, refreshToken: result.refreshToken })
  setUserCookies(response.cookies, { ...result, userRole: result.userRole ?? 'FREE' })
  // login.complete 이벤트용 1회용 쿠키 (첫 화면에서 읽고 지워요)
  setLoginResultCookie(response.cookies, {
    provider: 'apple',
    userId: String(result.userId),
    userRole: result.userRole ?? 'FREE',
    isNewUser: result.isNewUser,
  })
  return response
}
