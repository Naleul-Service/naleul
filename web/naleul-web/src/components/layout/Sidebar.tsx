'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Library, LogIn } from 'lucide-react'
import { BarChart3, CalendarDays, Home, LogOut, Plus, Settings, Target, X, type IconComponent } from '@/components/icons'
import { cn } from '@/lib/cn'
import { logout } from '@/lib/client/api'
import { COMMON_EVENTS as CE, EVENTS, track } from '@/lib/client/analytics'
import { currentPath } from '@/lib/client/commonEvents'
import { Logo } from '@/components/brand/Logo'
import { buttonClass } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Chip'
import { iconButtonClass } from '@/components/ui/IconButton'
import { GoalNavList } from '@/features/goal/ui/GoalNavList'
import { AdSlot } from '@/components/ads/AdSlot'
import { openLoginDialog } from '@/components/auth/loginHref'
import { isGuestUser, planRole, type SessionUserView } from './types'

interface NavItem {
  href: string
  label: string
  icon: IconComponent
  /** Amplitude nav_item */
  key: string
}

/** 넓은 화면 왼쪽 고정(sidebar) / 좁은 화면 ☰ 서랍(drawer) — 서랍일 때만 onNavigate 가 있어요 */
export type NavSurface = 'sidebar' | 'drawer'

const NAV: NavItem[] = [
  { href: '/', label: '홈', icon: Home, key: 'home' },
  { href: '/goal', label: '목표', icon: Target, key: 'goal' },
  { href: '/calendar', label: '캘린더', icon: CalendarDays, key: 'calendar' },
  { href: '/reflections', label: '회고 모아보기', icon: Library, key: 'reflections' },
  { href: '/patterns', label: '나의 패턴', icon: BarChart3, key: 'patterns' },
]

export const isActive = (pathname: string, href: string) =>
  href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`)

const PLAN_LABEL: Record<string, string> = { FREE: 'Free plan', PRO: 'Pro plan', ADMIN: 'Admin', GUEST: '로그인 전' }

function NavLink({
  item,
  pathname,
  onNavigate,
  surface,
}: {
  item: NavItem
  pathname: string
  onNavigate?: () => void
  surface: NavSurface
}) {
  const active = isActive(pathname, item.href)
  const Icon = item.icon
  return (
    <Link
      // 회고 모아보기는 들어온 길(reflections.pageview entry_source)을 주소에 붙여요
      href={item.key === 'reflections' ? `${item.href}?from=sidebar` : item.href}
      onClick={() => {
        track(CE.sidebarNavClick, {
          nav_item: item.key,
          nav_surface: surface,
          current_path: currentPath(),
          is_current: active,
        })
        onNavigate?.()
      }}
      aria-current={active ? 'page' : undefined}
      // 디자인 시스템 NavItem — h40 · px8 · gap8 · 모서리 8 · 아이콘 20 + Body/SM·Medium
      className={cn(
        'rounded-nav text-body-sm flex h-10 items-center gap-2 px-2 font-medium transition-colors',
        active
          ? 'bg-action-ghost-hover text-text-brand'
          : 'text-text-tertiary hover:bg-bg-subtle hover:text-text-primary'
      )}
    >
      <Icon className="size-5" strokeWidth={1.5} />
      {item.label}
    </Link>
  )
}

export function Sidebar({
  user,
  onNavigate,
  onClose,
}: {
  user: SessionUserView
  onNavigate?: () => void
  /** 모바일 서랍: 머리 오른쪽 X */
  onClose?: () => void
}) {
  const pathname = usePathname()
  const surface: NavSurface = onNavigate ? 'drawer' : 'sidebar'
  // 서랍은 구분선을 더 연하게 (border/soft)
  const line = onClose ? 'border-border-soft' : 'border-border-subtle'
  const ev = { nav_surface: surface, current_path: currentPath() }

  return (
    <div className="flex h-full flex-col">
      {/* 머리 56 — 워드마크 */}
      <div className={cn(line, 'flex h-14 shrink-0 items-center border-b px-4')}>
        <Link
          href="/"
          onClick={() => {
            track(CE.sidebarLogoClick, { ...ev, current_path: currentPath(), is_current: pathname === '/' })
            onNavigate?.()
          }}
        >
          <Logo />
        </Link>
        {onClose && (
          <button type="button" onClick={onClose} aria-label="메뉴 닫기" className={cn(iconButtonClass('md'), 'ml-auto -mr-1.5')}>
            <X className="size-[18px]" />
          </button>
        )}
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto p-4">

      {/* 캘린더를 보면서 추가하도록 캘린더 오른쪽 패널로 열어요 (/tasks/new 전체 화면도 그대로 있어요) */}
      <Link
        href="/calendar?add=ai"
        onClick={() => {
          track(CE.sidebarTaskAddClick, { ...ev, current_path: currentPath() })
          onNavigate?.()
        }}
        className={buttonClass('primary', 'md', true)}
      >
        <Plus className="size-[18px]" />
        Task 추가
      </Link>

      <nav className="mt-4 flex flex-col gap-1" aria-label="주 메뉴">
        {NAV.map((item) => (
          <NavLink key={item.href} item={item} pathname={pathname} onNavigate={onNavigate} surface={surface} />
        ))}
      </nav>

      <section className="mt-6" aria-labelledby="my-goals-heading">
        <div className="flex h-7 items-center justify-between px-2">
          <h2
            id="my-goals-heading"
            onClick={() => track(CE.sidebarDeadClick, { dead_target: 'goals_heading', nav_surface: surface })}
            className="text-label-xs text-text-secondary"
          >
            내 목표
          </h2>
          <Link
            href="/goal/add"
            onClick={() => {
              track(CE.sidebarGoalAddClick, { ...ev, current_path: currentPath() })
              onNavigate?.()
            }}
            aria-label="목표 추가"
            className={iconButtonClass('sm')}
          >
            <Plus className="size-3.5" />
          </Link>
        </div>
        <GoalNavList onNavigate={onNavigate} surface={surface} />
      </section>

      <div className="mt-4">
        <NavLink
          item={{ href: '/settings', label: '설정', icon: Settings, key: 'settings' }}
          pathname={pathname}
          onNavigate={onNavigate}
          surface={surface}
        />
      </div>

      {/* 아래 프로필을 바닥으로 밀어요 (광고가 안 보일 때도) */}
      <div className="mt-auto" />
      <AdSlot placement="sidebar" role={planRole(user)} className="mt-3" />
      </div>

      {isGuestUser(user) ? (
        <div className={cn(line, 'border-t p-4')}>
          <p className="text-body-sm text-text-secondary px-1">둘러보는 중이에요. 로그인하면 만든 목표와 Task가 저장돼요.</p>
          <button
            type="button"
            onClick={() => {
              onNavigate?.() // 모바일 서랍은 닫고 모달을 띄워요
              track(EVENTS.sidebarGuestLoginClick)
              openLoginDialog({ reason: 'login', source: 'sidebar' })
            }}
            className={cn(buttonClass('primary', 'md', true), 'mt-3')}
          >
            <LogIn className="size-4" />
            로그인하고 시작하기
          </button>
        </div>
      ) : (
        // 디자인 시스템 UserCard — 56 · 아바타 28 + 이름 + 플랜 Tag + 버튼
        <div className={cn(line, 'flex h-14 shrink-0 items-center gap-2 border-t px-4')}>
          <span
            onClick={() => track(CE.sidebarDeadClick, { dead_target: 'avatar', nav_surface: surface })}
            className="from-ai-gradient-start to-ai-gradient-end text-label-sm grid size-7 shrink-0 place-items-center rounded-full bg-linear-to-br text-white"
          >
            {user.name.slice(0, 1)}
          </span>
          <div className="flex min-w-0 flex-1 items-center gap-1.5">
            <p
              onClick={() => track(CE.sidebarDeadClick, { dead_target: 'profile_name', nav_surface: surface })}
              className="text-body-md text-text-primary min-w-0 truncate font-medium"
            >
              {user.name}
            </p>
            <p onClick={() => track(CE.sidebarDeadClick, { dead_target: 'plan_label', nav_surface: surface })}>
              <Badge tone={planRole(user) === 'PRO' ? 'mint' : 'neutral'} className="h-5 px-2">
                {user.role === 'ADMIN'
                  ? `Admin · ${planRole(user) === 'PRO' ? 'Pro' : 'Free'}`
                  : (PLAN_LABEL[user.role ?? 'FREE'] ?? 'Free plan')}
              </Badge>
            </p>
          </div>
          <button
            type="button"
            // logout 을 그대로 넘기면 클릭 이벤트 객체가 trackEvent 자리에 들어가요 → 화살표 함수로 감싸요
            onClick={() => void logout(EVENTS.sidebarLogoutClick)}
            aria-label="로그아웃"
            title="로그아웃"
            className={iconButtonClass('md')}
          >
            <LogOut className="size-[18px]" />
          </button>
        </div>
      )}
    </div>
  )
}
