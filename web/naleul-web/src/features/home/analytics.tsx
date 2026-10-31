'use client'

import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react'
import { isApiError } from '@/lib/client/api'
import { HOME_EVENTS, track, type EventProps } from '@/lib/client/analytics'
import { todayKst } from '@/features/timetable/time'

/**
 * 홈 이벤트 공통 도구. 이벤트 이름은 HOME_EVENTS (lib/client/homeEvents.ts), 규칙은 노션 Tracking Plan 「홈」.
 *  - view_date · date_relation 처럼 거의 모든 이벤트에 붙는 값을 화면 위에서 한 번 정해 내려줘요 (HomeAnalyticsProvider)
 *  - once(key) : "pageview 당 1회" 이벤트 (빈 상태 노출 · 스크롤 등) 중복 방지
 */
export { HOME_EVENTS as HE }

export type DateRelation = 'past' | 'today' | 'future'
export const dateRelationOf = (date: string, today = todayKst()): DateRelation =>
  date === today ? 'today' : date < today ? 'past' : 'future'

/** 화면 폭 구간 (노션 layout_breakpoint) — 홈은 lg 부터 한 화면, 2xl 부터 회고 패널이 옆으로 */
export function layoutBreakpoint() {
  if (typeof window === 'undefined') return undefined
  const w = window.innerWidth
  return w >= 1536 ? '2xl' : w >= 1024 ? 'lg' : w >= 640 ? 'sm' : 'mobile'
}

/** 실패한 요청의 HTTP 상태 (네트워크 끊김 0) */
export const errorStatusOf = (e: unknown) => (isApiError(e) ? e.httpStatus : undefined)

interface HomeAnalytics {
  viewDate: string
  dateRelation: DateRelation
  /** 같은 key 는 이번 pageview 동안 한 번만 실행 */
  once: (key: string, fn: () => void) => void
  /** view_date · date_relation 을 붙여서 보내기 */
  trackHome: (event: Parameters<typeof track>[0], props?: EventProps) => void
}

const Ctx = createContext<HomeAnalytics | null>(null)

export function HomeAnalyticsProvider({ viewDate, children }: { viewDate: string; children: ReactNode }) {
  const seen = useRef(new Set<string>())
  const dateRelation = dateRelationOf(viewDate)
  const value: HomeAnalytics = {
    viewDate,
    dateRelation,
    once: (key, fn) => {
      if (seen.current.has(key)) return
      seen.current.add(key)
      fn()
    },
    trackHome: (event, props) => track(event, { view_date: viewDate, date_relation: dateRelation, ...props }),
  }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

/** Provider 밖(다른 화면에서 재사용)에서도 안전하게 — 그때는 아무것도 보내지 않아요 */
export function useHomeAnalytics(): HomeAnalytics {
  return (
    useContext(Ctx) ?? {
      viewDate: todayKst(),
      dateRelation: 'today',
      once: () => undefined,
      trackHome: () => undefined,
    }
  )
}

/** 빈 상태 카드가 보일 때 (카드마다 pageview 당 1회) — 빈 상태 문구 옆에 그려 두면 돼요 */
export function EmptyStateView({ section }: { section: string }) {
  const { once, trackHome } = useHomeAnalytics()
  useEffect(() => {
    once(`empty:${section}`, () => trackHome(HOME_EVENTS.emptyStateView, { empty_section: section }))
  }, [section, once, trackHome])
  return null
}
