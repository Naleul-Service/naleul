'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { CalendarIcon, ChartBarIcon, HomeIcon, NotebookIcon, TargetIcon, type IconComponent } from '@/components/icons'
import { cn } from '@/lib/cn'
import { isActive } from './Sidebar'

interface Tab {
  href: string
  label: string
  icon: IconComponent
}

// 사이드바와 같은 경로 (설정은 위쪽 헤더 톱니바퀴에서)
const TABS: Tab[] = [
  { href: '/', label: '홈', icon: HomeIcon },
  { href: '/goal', label: '목표', icon: TargetIcon },
  { href: '/calendar', label: '캘린더', icon: CalendarIcon },
  { href: '/reflections', label: '회고', icon: NotebookIcon },
  { href: '/patterns', label: '패턴', icon: ChartBarIcon },
]

/**
 * 디자인 시스템 TabBar (모바일 · lg 미만) — 아래 고정, bg/surface · 위 1px border/subtle
 *  칸: 32×32 아이콘 상자(모서리 8, 선택 = ghost-hover 바탕) + Label/SM · 간격 2
 *  높이는 globals.css 의 --tabbar-h 로 다른 화면 요소(본문 여백 · 떠 있는 바 · 토스트)가 비켜요.
 * (탭바 전용 분석 이벤트는 아직 없어요 — 사이드바 이벤트를 흉내 내지 않아요)
 */
export function MobileTabBar() {
  const pathname = usePathname()
  return (
    <nav
      data-mobile-tabbar
      aria-label="주 메뉴"
      className="bg-bg-surface border-border-subtle fixed inset-x-0 bottom-0 z-30 flex border-t px-4 pt-2 pb-[calc(env(safe-area-inset-bottom)+8px)] lg:hidden"
    >
      {TABS.map(({ href, label, icon: Icon }) => {
        const active = isActive(pathname, href)
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'flex min-w-0 flex-1 flex-col items-center gap-0.5',
              active ? 'text-text-brand' : 'text-text-tertiary hover:text-text-primary'
            )}
          >
            <span
              className={cn(
                'rounded-nav grid size-8 place-items-center transition-colors',
                active && 'bg-action-ghost-hover'
              )}
            >
              <Icon className="size-5" />
            </span>
            <span className="text-label-sm truncate">{label}</span>
          </Link>
        )
      })}
    </nav>
  )
}
