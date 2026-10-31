'use client'

import { useEffect } from 'react'
import { EVENTS, identifyUser, track, type AnalyticsUserRole } from '@/lib/client/analytics'
import { LOGIN_ERROR_PARAM } from '@/components/auth/kakaoAuth'
import { secondsSince, takeLoginAttempt, takeLoginResultCookie } from '@/lib/loginAnalytics'

const ROLES: AnalyticsUserRole[] = ['GUEST', 'FREE', 'PRO', 'ADMIN']
const toRole = (v?: string) => (ROLES.includes(v as AnalyticsUserRole) ? (v as AnalyticsUserRole) : undefined)

/**
 * 카카오 · Apple 로그인 "결과"를 보내요. 화면에는 아무것도 그리지 않아요. (Providers 에 하나만)
 *
 * 1) login.complete
 *    콜백(서버)이 성공하면 1회용 쿠키(naleul_login_result)를 심고 원래 화면으로 리다이렉트해요.
 *    첫 화면에서 쿠키를 읽고 지운 뒤 setUserId → login.complete 순서로 보내요.
 *    (setUserId 를 먼저 해야 이 이벤트가 로그인한 사용자에게 붙어요)
 *
 * 2) login.oauth_back  [UX]
 *    버튼을 눌러 카카오/Apple 로 갔다가 결과 없이 브라우저 뒤로가기로 돌아온 경우.
 *    클릭 때 sessionStorage 에 남긴 시도 정보가 그대로 있고, 결과(쿠키 · ?loginError)가 없으면 이탈로 봐요.
 *    뒤로가기는 브라우저가 이전 화면을 메모리에서 그대로 꺼내기도 해서(bfcache) effect 가 다시 안 돌아요
 *    → pageshow 이벤트(persisted)도 같이 들어요.
 *
 * login.fail 은 로그인 모달(LoginRequiredDialog)이 ?loginError 를 읽을 때 보내요.
 */
export function LoginResultTracker() {
  useEffect(() => {
    const check = () => {
      const result = takeLoginResultCookie()
      if (result) {
        const attempt = takeLoginAttempt()
        identifyUser({ userId: result.userId, role: toRole(result.userRole), provider: result.provider })
        track(EVENTS.loginComplete, {
          login_provider: result.provider,
          login_surface: attempt?.surface,
          is_new_user: result.isNewUser,
          redirect_path: window.location.pathname + window.location.search,
          seconds_since_click: secondsSince(attempt),
        })
        return
      }
      // 실패로 돌아온 경우는 로그인 모달이 login.fail 로 처리해요 (시도 정보도 거기서 꺼내요)
      if (new URL(window.location.href).searchParams.has(LOGIN_ERROR_PARAM)) return
      const attempt = takeLoginAttempt()
      if (!attempt) return
      track(EVENTS.loginOauthBack, {
        login_provider: attempt.provider,
        login_surface: attempt.surface,
        seconds_since_click: secondsSince(attempt),
      })
    }

    check()
    const onPageShow = (e: PageTransitionEvent) => {
      if (e.persisted) check()
    }
    window.addEventListener('pageshow', onPageShow)
    return () => window.removeEventListener('pageshow', onPageShow)
  }, [])
  return null
}
