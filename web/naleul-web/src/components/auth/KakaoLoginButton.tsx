'use client'

import { cn } from '@/lib/cn'
import { EVENTS, trackBeforeLeave, type EventProps } from '@/lib/client/analytics'
import { saveLoginAttempt, type LoginSurface } from '@/lib/loginAnalytics'
import { kakaoAuthUrl } from './kakaoAuth'

function KakaoIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden>
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M10 2C5.582 2 2 4.925 2 8.5c0 2.254 1.42 4.236 3.57 5.387L4.67 17.1a.25.25 0 0 0 .375.27L9.3 14.95c.231.017.464.05.7.05 4.418 0 8-2.925 8-6.5S14.418 2 10 2z"
        fill="#191600"
      />
    </svg>
  )
}

/**
 * 카카오 로그인 버튼. 로그인이 끝나면 next 경로로 돌아와요.
 * surface: 어느 화면의 버튼인지 → login.kakao_click / login_modal.kakao_click
 * trackProps: 그 이벤트에 같이 보낼 속성 (모달이면 modal_reason · trigger_source 등)
 */
export function KakaoLoginButton({
  next,
  surface,
  trackProps,
  className,
}: {
  next: string
  surface: LoginSurface
  trackProps?: EventProps
  className?: string
}) {
  return (
    <a
      href={kakaoAuthUrl(next)}
      onClick={() => {
        // 카카오 페이지로 이동하기 직전 → beacon 으로 보내요 (기다리지 않아요)
        saveLoginAttempt('kakao', surface)
        void trackBeforeLeave(surface === 'login_page' ? EVENTS.loginKakaoClick : EVENTS.loginModalKakaoClick, {
          redirect_path: next,
          ...trackProps,
        })
      }}
      className={cn(
        'flex h-14 w-full items-center justify-center gap-2.5 rounded-2xl bg-[#FEE500] text-[15px] font-semibold text-[#191600] transition hover:brightness-95',
        className
      )}
    >
      <KakaoIcon />
      카카오로 시작하기
    </a>
  )
}
