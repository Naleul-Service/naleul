'use client'

import { cn } from '@/lib/cn'
import { EVENTS, trackBeforeLeave, type EventProps } from '@/lib/client/analytics'
import { saveLoginAttempt, type LoginSurface } from '@/lib/loginAnalytics'
import { appleAuthUrl, isAppleLoginEnabled } from './appleAuth'

function AppleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 814 1000" fill="currentColor" aria-hidden>
      <path d="M788.1 340.9c-5.8 4.5-108.2 62.2-108.2 190.5 0 148.4 130.3 200.9 134.2 202.2-.6 3.2-20.7 71.9-68.7 141.9-42.8 61.6-87.5 123.1-155.5 123.1s-85.5-39.5-164-39.5c-76.5 0-103.7 40.8-165.9 40.8s-105.6-57-155.5-127C46.7 790.7 0 663 0 541.8c0-194.4 126.4-297.5 250.8-297.5 66.1 0 121.2 43.4 162.7 43.4 39.5 0 101.1-46 176.3-46 28.5 0 130.9 2.6 198.3 99.2zm-234-181.5c31.1-36.9 53.1-88.1 53.1-139.3 0-7.1-.6-14.3-1.9-20.1-50.6 1.9-110.8 33.7-147.1 75.8-28.5 32.4-55.1 83.6-55.1 135.5 0 7.8 1.3 15.6 1.9 18.1 3.2.6 8.4 1.3 13.6 1.3 45.4 0 102.5-30.4 135.5-71.3z" />
    </svg>
  )
}

/**
 * Apple 로그인 버튼 (Apple 디자인 가이드: 검정 배경 + 흰 로고).
 * NEXT_PUBLIC_APPLE_CLIENT_ID / NEXT_PUBLIC_APPLE_REDIRECT_URI 가 없으면 아무것도 그리지 않아요.
 */
export function AppleLoginButton({
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
  if (!isAppleLoginEnabled()) return null
  return (
    <a
      href={appleAuthUrl(next)}
      onClick={() => {
        saveLoginAttempt('apple', surface)
        void trackBeforeLeave(surface === 'login_page' ? EVENTS.loginAppleClick : EVENTS.loginModalAppleClick, {
          redirect_path: next,
          ...trackProps,
        })
      }}
      className={cn(
        'flex h-14 w-full items-center justify-center gap-2.5 rounded-2xl bg-black text-[15px] font-semibold text-white transition hover:bg-black/85 dark:bg-white dark:text-black dark:hover:bg-white/85',
        className
      )}
    >
      <AppleIcon />
      Apple로 시작하기
    </a>
  )
}
