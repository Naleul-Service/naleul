'use client'

import { useEffect, useRef, useState, type ReactNode } from 'react'
import Link from 'next/link'
import { Bell, Menu, SettingsIcon } from '@/components/icons'
import { Logo } from '@/components/brand/Logo'
import { GuestBanner } from '@/components/auth/LoginRequiredDialog'
import { GuestSessionTracker } from '@/components/analytics/GuestSessionTracker'
import { COMMON_EVENTS as CE, markOperatorDevice, track } from '@/lib/client/analytics'
import { currentPath } from '@/lib/client/commonEvents'
import { cn } from '@/lib/cn'
import { iconButtonClass } from '@/components/ui/IconButton'
import { GoalOutcomePromptModal } from '@/features/goal/ui/GoalOutcomePromptModal'
import { MobileTabBar } from './MobileTabBar'
import { Sidebar } from './Sidebar'

const LG = 1024
const breakpoint = () => {
  const w = window.innerWidth
  return w >= 1536 ? '2xl' : w >= LG ? 'lg' : w >= 640 ? 'sm' : 'mobile'
}

import { isGuestUser, type SessionUserView } from './types'

/**
 * 로그인 후 화면의 공통 틀.
 *  - lg(1024px) 이상: 왼쪽 고정 사이드바
 *  - 그보다 좁으면: 위 MobileHeader(☰ · 워드마크 · 알림 · 설정) + 아래 MobileTabBar · ☰ 로 여는 서랍(drawer)
 */
export function AppShell({ user, children }: { user: SessionUserView; children: ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const openedAt = useRef(0)
  // 서랍 닫기 — 바깥(어두운 곳) backdrop / 머리의 X x_button / 서랍 안 링크·버튼 navigate
  const closeWith = (method: 'backdrop' | 'navigate' | 'x_button') => {
    track(CE.drawerClose, {
      close_method: method,
      open_duration_seconds: Math.round((Date.now() - openedAt.current) / 1000),
    })
    setDrawerOpen(false)
  }
  const close = () => closeWith('navigate')

  // 이미 로그인해 있던 운영자(ADMIN)도 운영 Amplitude 집계에서 빼요 — 푸는 건 로그인 때만(identifyUser)
  useEffect(() => {
    if (user.role === 'ADMIN') markOperatorDevice(true)
  }, [user.role])

  // 서랍이 열린 채로 창을 lg 이상으로 넓히면 서랍만 숨겨지고 열린 상태는 남아요 (다시 좁히면 다시 나타남)
  useEffect(() => {
    if (!drawerOpen) return
    let wide = window.innerWidth >= LG
    const onResize = () => {
      const nowWide = window.innerWidth >= LG
      if (nowWide && !wide) track(CE.drawerResizeHidden, { layout_breakpoint: breakpoint() })
      wide = nowWide
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [drawerOpen])

  return (
    <div className="lg:bg-bg-page min-h-dvh">
      <GuestSessionTracker isGuest={isGuestUser(user)} />
      {/* 디자인 시스템 Sidebar — 240 · bg/surface · 오른쪽 1px border/subtle */}
      <aside className="border-border-subtle bg-bg-surface fixed inset-y-0 left-0 z-30 hidden w-60 border-r lg:block">
        <Sidebar user={user} />
      </aside>

      {/* 디자인 시스템 MobileHeader — h48 · 위에 고정 · 왼쪽 ☰ + 워드마크 / 오른쪽 알림 · 설정 */}
      <header className="border-border-soft bg-bg-surface sticky top-0 z-20 flex h-12 items-center gap-2 border-b px-5 lg:hidden">
        <button
          type="button"
          onClick={() => {
            track(CE.mobileHeaderMenuClick, { current_path: currentPath(), layout_breakpoint: breakpoint() })
            openedAt.current = Date.now()
            setDrawerOpen(true)
          }}
          aria-label="메뉴 열기"
          className={cn(iconButtonClass('md'), '-ml-1.5')}
        >
          <Menu />
        </button>
        {/* 위쪽 바 로고는 링크가 아니에요 (사이드바 로고는 홈으로 감) → 눌러 보는지 확인 */}
        <span onClick={() => track(CE.mobileHeaderDeadClick, { dead_target: 'logo', current_path: currentPath() })}>
          <Logo />
        </span>
        <div className="-mr-1.5 ml-auto flex items-center gap-1">
          <Link
            href="/settings/notifications"
            aria-label="알림 설정"
            title="알림 설정"
            className={iconButtonClass('md')}
          >
            <Bell />
          </Link>
          <Link href="/settings" aria-label="설정" title="설정" className={iconButtonClass('md')}>
            <SettingsIcon />
          </Link>
        </div>
      </header>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="bg-bg-overlay absolute inset-0 animate-[fade-in_150ms_ease-out]"
            onClick={() => closeWith('backdrop')}
            aria-hidden
          />
          <aside className="bg-bg-surface shadow-pop absolute inset-y-0 left-0 w-60 max-w-[85vw]">
            <Sidebar user={user} onNavigate={close} onClose={() => closeWith('x_button')} />
          </aside>
        </div>
      )}

      {/* 모바일: 아래 탭바만큼 비워요 (--tabbar-h) */}
      <main className="pb-(--tabbar-h) lg:pb-0 lg:pl-60">
        {isGuestUser(user) && <GuestBanner />}
        {/* 아주 넓은 모니터: 내용 폭을 넓히되(1440 → 1760 → 2000) 양옆 여백은 화면에 맞춰 남겨요 */}
        <div className="mx-auto w-full max-w-[1440px] px-5 py-6 min-[1800px]:max-w-[1760px] min-[1800px]:px-[clamp(40px,3vw,72px)] min-[2200px]:max-w-[2000px] sm:px-6 lg:px-10 lg:py-8">
          {children}
        </div>
      </main>

      <MobileTabBar />
      {/* 기한이 지나 자동 판정된 목표 — "목표는 만족했나요?" (둘러보기 사용자는 목표가 없어 묻지 않아요) */}
      <GoalOutcomePromptModal enabled={!isGuestUser(user)} />
    </div>
  )
}
