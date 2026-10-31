/**
 * Next.js 16 의 proxy (예전 이름: middleware)
 * 페이지 이동마다 실행돼요.
 *
 *  - 로그인 안 했으면 → "둘러보기" 읽기 전용 토큰을 받아서 그대로 보여줘요 (빈 화면 + "추가해 보세요" 안내)
 *    저장하려고 하면 그때 로그인 안내가 떠요 (백엔드가 401 GUEST_LOGIN_REQUIRED)
 *  - accessToken 이 만료(직전)이면 → refreshToken 으로 미리 재발급
 *    · 새 토큰을 "브라우저 응답"과 "지금 처리 중인 요청" 양쪽에 넣어요.
 *      응답에만 넣으면 이번 화면의 서버 컴포넌트는 여전히 옛 쿠키를 읽어서 첫 화면이 실패해요.
 */
import { NextResponse, type NextRequest } from 'next/server'
import { isJwtExpired } from '@/lib/jwt'
import { guestLogin, reissueAccessToken } from '@/lib/server/authApi'
import {
  COOKIE,
  GUEST_NAME,
  clearSessionCookies,
  setGuestCookies,
  setGuestStartedCookie,
  setTokenCookies,
} from '@/lib/server/session'

const PUBLIC_PATHS = ['/login']
/**
 * 로그인 · 둘러보기 토큰 없이 그대로 보여주는 공개 페이지 (구글 OAuth 앱 인증의 홈페이지 · 방침 · 약관 주소).
 * 로그인한 사람이 와도 홈으로 보내지 않아요.
 */
const OPEN_PATHS = ['/about', '/privacy', '/terms', '/refund']

const matches = (paths: string[], pathname: string) => paths.some((p) => pathname === p || pathname.startsWith(`${p}/`))
const isPublic = (pathname: string) => matches(PUBLIC_PATHS, pathname)
const isOpen = (pathname: string) => matches(OPEN_PATHS, pathname)

/**
 * from: 로그인 화면에 온 이유 (login.pageview 의 entry_source)
 *  - guest_unavailable : 둘러보기 토큰을 못 받음
 *  - session_expired   : refresh token 이 만료/무효
 */
function redirectToLogin(req: NextRequest, from: 'guest_unavailable' | 'session_expired') {
  const url = new URL('/login', req.url)
  const target = req.nextUrl.pathname + req.nextUrl.search
  if (target !== '/') url.searchParams.set('redirect', target)
  url.searchParams.set('from', from)
  return NextResponse.redirect(url)
}

/** 둘러보기 토큰을 받아 지금 요청 · 응답 양쪽에 넣어요. 실패하면 로그인 화면으로 */
async function startGuest(req: NextRequest) {
  const token = await guestLogin()
  if (!token) return redirectToLogin(req, 'guest_unavailable')
  req.cookies.set(COOKIE.access, token)
  req.cookies.delete(COOKIE.refresh)
  req.cookies.set(COOKIE.userName, GUEST_NAME)
  req.cookies.set(COOKIE.userRole, 'GUEST')
  const res = NextResponse.next({ request: { headers: req.headers } })
  setGuestCookies(res.cookies, token)
  setGuestStartedCookie(res.cookies) // guest.session_start 의 is_new_guest = true
  return res
}

export async function proxy(req: NextRequest) {
  const { pathname } = req.nextUrl
  if (isOpen(pathname)) return NextResponse.next()

  const access = req.cookies.get(COOKIE.access)?.value
  const refresh = req.cookies.get(COOKIE.refresh)?.value
  const guest = req.cookies.get(COOKIE.userRole)?.value === 'GUEST'
  const accessValid = !!access && !isJwtExpired(access)

  if (isPublic(pathname)) {
    // 이미 로그인된 사용자가 /login 에 오면 홈으로 (둘러보는 중이면 로그인 화면을 보여줘요)
    if (accessValid && !guest) return NextResponse.redirect(new URL('/', req.url))
    return NextResponse.next()
  }

  if (accessValid) return NextResponse.next()
  if (!refresh) return startGuest(req)

  const tokens = await reissueAccessToken(refresh)
  // 서버가 잠깐 응답을 못 하면 로그아웃시키지 않고 그대로 보여줘요 (API 호출 때 다시 재발급 시도)
  if (tokens === 'unavailable') return NextResponse.next()
  if (!tokens) {
    const res = redirectToLogin(req, 'session_expired')
    clearSessionCookies(res.cookies)
    return res
  }

  // 지금 요청에도 새 쿠키 반영 (req.cookies.set 은 요청의 cookie 헤더를 바꿔요)
  req.cookies.set(COOKIE.access, tokens.accessToken)
  if (tokens.refreshToken) req.cookies.set(COOKIE.refresh, tokens.refreshToken)

  const res = NextResponse.next({ request: { headers: req.headers } })
  setTokenCookies(res.cookies, tokens)
  return res
}

export const config = {
  // api, redirect(카카오 콜백), Next 내부 파일, 정적 파일은 제외
  matcher: ['/((?!api|redirect|_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|ico|webp|txt|xml)$).*)'],
}
