'use client'

import { useEffect, useState, type ReactNode } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ChevronLeft, ChevronUp, type IconComponent } from '@/components/icons'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import type { GoalCategory } from '../api'

export interface TabDef<T extends string> {
  value: T
  label: string
  icon: IconComponent
}

/**
 * 고른 탭은 주소(?tab=)에 남겨서 새로고침·공유해도 그대로예요. 첫 탭(기본)은 주소에서 빼요.
 * 모르는 값(예전 ?tab=journal 등)은 첫 탭으로.
 */
export function useGoalTab<T extends string>(tabs: TabDef<T>[]) {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const raw = params.get('tab')
  const tab = tabs.find((t) => t.value === raw)?.value ?? tabs[0].value
  const setTab = (t: T, goal: GoalCategory) => {
    // 같은 탭을 또 눌러도 보내요
    track('goal_detail.tab_click', {
      goal_category_id: goal.goalCategoryId,
      goal_mode: goal.goalMode ?? 'ACHIEVEMENT',
      tab: t,
      from_tab: tab,
    })
    const next = new URLSearchParams(params)
    if (t === tabs[0].value) next.delete('tab')
    else next.set('tab', t)
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }
  return [tab, setTab] as const
}

/**
 * 요소의 아래 끝이 화면 위쪽 offset(px) 위로 지나갔는지 (명세 13.4).
 * IntersectionObserver 는 요소가 나중에 다시 그려지면 놓쳐서, 스크롤 · 크기 변경마다 직접 재요 (rAF 로 한 프레임에 한 번).
 */
function useScrolledPast(id: string, offset: number) {
  const [past, setPast] = useState(false)
  useEffect(() => {
    let raf = 0
    const measure = () => {
      raf = 0
      const el = document.getElementById(id)
      setPast(!!el && el.getBoundingClientRect().bottom < offset)
    }
    const onScroll = () => {
      if (!raf) raf = window.requestAnimationFrame(measure)
    }
    measure()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (raf) window.cancelAnimationFrame(raf)
    }
  }, [id, offset])
  return past
}

/**
 * 목표 상세 탭 — 밑줄 탭 (명세 12.4 · 12.5). 고정(sticky)하지 않고 내용과 같이 스크롤돼요.
 * 모바일: 탭이 화면 위로 지나가면 위 가운데에 "탭이름 ▲" 알약 → 누르면 탭이 보이는 곳까지 올라가요.
 */
export function GoalTabBar<T extends string>({
  tabs,
  tab,
  onChange,
}: {
  tabs: TabDef<T>[]
  tab: T
  onChange: (t: T) => void
}) {
  const past = useScrolledPast('goal-tabs', 92) // 앱 머리 48 + 목표 내비 44
  const current = tabs.find((t) => t.value === tab)
  return (
    <>
      <div
        id="goal-tabs"
        className="border-border-soft mt-6 flex scroll-mt-28 scrollbar-none gap-1 overflow-x-auto border-b"
        role="tablist"
        aria-label="보기"
      >
        {tabs.map(({ value, label, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="tab"
            id={`goal-tab-${value}`}
            aria-selected={tab === value}
            aria-controls={`goal-panel-${value}`}
            onClick={() => onChange(value)}
            className={cn(
              'text-label-lg -mb-px flex h-11 shrink-0 items-center gap-1.5 border-b-2 px-3 transition-colors',
              tab === value
                ? 'border-action-primary-bg text-text-primary'
                : 'text-text-secondary hover:text-text-primary border-transparent'
            )}
          >
            <Icon className="size-4" />
            {label}
          </button>
        ))}
      </div>
      {/* 모바일: 탭으로 돌아가기 알약 */}
      <button
        type="button"
        aria-hidden={!past}
        tabIndex={past ? 0 : -1}
        onClick={() => document.getElementById('goal-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
        className={cn(
          'bg-bg-surface border-border-subtle shadow-card text-label-md text-text-primary fixed top-[100px] left-1/2 z-20 flex h-8 -translate-x-1/2 items-center gap-1 rounded-full border px-3.5 transition-opacity lg:hidden',
          past ? 'opacity-100' : 'pointer-events-none opacity-0'
        )}
      >
        {current?.label}
        <ChevronUp className="size-4" />
      </button>
    </>
  )
}

/**
 * 모바일 목표 상세 내비 (명세 12.4 · 13.4) — 앱 머리 바로 아래 늘 붙어 있는 단색 bg/surface 줄:
 * ← 목표 · 가운데 목표 이름(요약 카드가 위로 지나가면 서서히) · ⋯ 더보기. 이름이 보일 때 아래 1px border/soft.
 */
export function GoalScrollTitle({
  title,
  onBack,
  more,
}: {
  title: string
  onBack?: () => void
  /** ⋯ 더보기 (예: 목표 마무리) — 없으면 버튼을 숨겨요 */
  more?: ReactNode
}) {
  const past = useScrolledPast('goal-summary', 92)
  return (
    <div
      className={cn(
        'bg-bg-surface sticky top-12 z-20 -mx-5 -mt-6 mb-4 flex h-11 items-center border-b px-3 transition-colors lg:hidden',
        past ? 'border-border-soft' : 'border-transparent'
      )}
    >
      <Link
        href="/goal"
        onClick={onBack}
        aria-label="목표 목록"
        className="text-text-secondary hover:bg-bg-subtle grid size-8 place-items-center rounded-[6px]"
      >
        <ChevronLeft className="size-5" />
      </Link>
      <p
        aria-hidden={!past}
        className={cn(
          'text-label-lg text-text-primary absolute inset-x-12 truncate text-center transition-opacity duration-200',
          past ? 'opacity-100' : 'pointer-events-none opacity-0'
        )}
      >
        {title}
      </p>
      <div className="ml-auto">{more}</div>
    </div>
  )
}
