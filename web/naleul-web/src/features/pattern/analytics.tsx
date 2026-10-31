'use client'

import { createContext, useContext, useRef, type ReactNode } from 'react'
import { track } from '@/lib/client/analytics'
import { layoutBreakpoint } from '@/features/home/analytics'
import type { PatternPeriod, PatternReport, PatternSuggestionBar } from './types'

/**
 * 나의 패턴(/patterns) 이벤트 공통 도구 — 노션 Tracking Plan 「나의 패턴」.
 *  - period 를 화면 위에서 한 번 정해 카드들에 내려줘요 (PatternAnalyticsProvider)
 *  - once(key) : "방문당 한 번" 이벤트 중복 방지 (그래프 만지기 · 스크롤 깊이)
 */

/** 이 화면용 chart_kind (노션에 아직 값이 없어서 정한 값) */
export type PatternChartKind = 'heatmap' | 'weekday' | 'weekly' | 'time_usage'
/** 이 화면용 dead_target (노션에 아직 값이 없어서 정한 값) */
export type PatternDeadTarget =
  | 'summary_tile'
  | 'style_sentence'
  | 'time_usage_row'
  | 'moved_routine'
  | 'kept_routine'
  | 'missed_block'
  | 'golden_time'

interface PatternAnalytics {
  period: PatternPeriod
  once: (key: string, fn: () => void) => void
}

const Ctx = createContext<PatternAnalytics | null>(null)

export function PatternAnalyticsProvider({ period, children }: { period: PatternPeriod; children: ReactNode }) {
  const seen = useRef(new Set<string>())
  const value: PatternAnalytics = {
    period,
    once: (key, fn) => {
      if (seen.current.has(key)) return
      seen.current.add(key)
      fn()
    },
  }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

/** Provider 밖에서도 안전하게 — 그때는 아무것도 보내지 않아요 */
export function usePatternAnalytics(): PatternAnalytics {
  return useContext(Ctx) ?? { period: 'RECENT_4W', once: () => undefined }
}

/** 그래프에 처음 마우스를 올리거나 누를 때 (카드마다 방문당 한 번) — 그래프 감싸는 요소에 펼쳐 넣어요 */
export function useChartInteract(kind: PatternChartKind) {
  const { period, once } = usePatternAnalytics()
  const fire = () =>
    once(`chart:${kind}`, () =>
      track('pattern.chart_interact', { chart_kind: kind, period, layout_breakpoint: layoutBreakpoint() })
    )
  return { onPointerEnter: fire, onPointerDown: fire }
}

/** 눌렀지만 아무 일도 없는 곳 — onClick 에 넣어요 */
export function useDeadClick(target: PatternDeadTarget) {
  const { period } = usePatternAnalytics()
  return () => track('pattern.dead_click', { dead_target: target, period })
}

/** 마지막으로 계산한 뒤 지난 분 (generatedAt 은 시간대 없는 한국 시간일 수 있어요) */
export function minutesSince(iso: string) {
  const withZone = /[zZ]|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}+09:00`
  const t = Date.parse(withZone)
  if (Number.isNaN(t)) return undefined
  return Math.max(0, Math.floor((Date.now() - t) / 60000))
}

/** 제안 항목 종류, 쉼표로 (ROUTINE_TIME,ROUTINE_DAY) */
export const suggestionTypesOf = (s?: PatternSuggestionBar | null) =>
  s ? [...new Set(s.items.map((i) => i.type))].join(',') : undefined

const ALL_SECTIONS = [
  'summary',
  'heatmap',
  'weekday',
  'weekly',
  'time_usage',
  'block_keeping',
  'routines',
  'task_habits',
] as const

/** '기록이 쌓이면 보여드려요'로 비어 보인 카드 — 각 카드의 빈 화면 조건과 똑같이 */
export function emptySectionsOf(r: PatternReport) {
  if (r.evaluatedCount === 0) return ALL_SECTIONS.join(',') // 빈 화면: 카드가 하나도 안 보여요
  const empty: string[] = []
  if (r.summary?.executionRate == null) empty.push('summary')
  if (!r.heatmap?.rows.some((row) => row.cells.some((c) => c.state === 'VALUE'))) empty.push('heatmap')
  if (!r.weekday?.days.some((d) => d.rate != null)) empty.push('weekday')
  if ((r.weekly?.points ?? []).filter((p) => p.rate != null).length < 2) empty.push('weekly')
  if (!r.timeUsage?.goals.length) empty.push('time_usage')
  if (!(r.blockKeeping && r.blockKeeping.total > 0 && r.blockKeeping.onTime != null)) empty.push('block_keeping')
  if (!r.routines || (!r.routines.kept.length && !r.routines.missed.length)) empty.push('routines')
  if (!r.taskHabits || (r.taskHabits.postponedRate == null && r.taskHabits.brainDumpPerWeek == null))
    empty.push('task_habits')
  return empty.join(',')
}
