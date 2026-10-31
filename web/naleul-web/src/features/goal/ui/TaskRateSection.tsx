'use client'

import { useMemo, useState } from 'react'
import { Chip } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { addDays, startOfWeek, todayKst } from '@/features/timetable/time'
import type { TimeBlockTask } from '@/features/timetable/types'
import { useGoalTasks, type GoalCategory } from '../api'
import { dayOf, isDone } from './GoalTaskList'
import { Section } from './sections'

type Kind = 'ONE_TIME' | 'ALL'

interface WeekBar {
  week: string
  due: number
  done: number
  /** 이번 주처럼 아직 진행 중인 주 */
  current: boolean
}

/** 주(월요일 시작)마다: 그 주에 하기로 했던 Task 중 오늘까지 날짜가 온 것 → 몇 개 실천했나 */
export function weekly(tasks: TimeBlockTask[], today: string): WeekBar[] {
  const byWeek = new Map<string, { due: number; done: number }>()
  for (const t of tasks) {
    const day = dayOf(t)
    if (!day || day > today) continue // 아직 안 온 날의 Task 는 실천률에 넣지 않아요
    const w = startOfWeek(day)
    const cur = byWeek.get(w) ?? { due: 0, done: 0 }
    cur.due++
    if (isDone(t)) cur.done++
    byWeek.set(w, cur)
  }
  if (!byWeek.size) return []
  const thisWeek = startOfWeek(today)
  const first = [...byWeek.keys()].sort()[0]
  const bars: WeekBar[] = []
  for (let w = first; w <= thisWeek; w = addDays(w, 7)) {
    const v = byWeek.get(w) ?? { due: 0, done: 0 }
    bars.push({ week: w, ...v, current: w === thisWeek })
  }
  return bars
}

/**
 * Task 실천률 — 실제로 만든 Task 중 날짜가 지난 것을 얼마나 해냈는지 주별 막대로.
 * 루틴은 위 히트맵에서 따로 보니까, 기본은 일회성 Task 만 (원하면 루틴 포함).
 */
export function TaskRateSection({ goal }: { goal: GoalCategory }) {
  const { data } = useGoalTasks(goal.goalCategoryId)
  const [kind, setKind] = useState<Kind>('ONE_TIME')
  const today = todayKst()

  const all = data?.tasks ?? []
  const hasRoutine = all.some((t) => t.sourceType === 'ROUTINE')
  const tasks = kind === 'ALL' ? all : all.filter((t) => t.sourceType !== 'ROUTINE')
  const bars = useMemo(() => weekly(tasks, today), [tasks, today])

  const selectKind = (k: Kind) => {
    track('goal_detail.task_rate_kind_select', { goal_category_id: goal.goalCategoryId, task_kind: k })
    setKind(k)
  }

  if (!data || !all.length) return null
  const due = bars.reduce((n, b) => n + b.due, 0)
  const done = bars.reduce((n, b) => n + b.done, 0)
  const rate = due ? Math.round((done / due) * 100) : null
  const recent = bars.slice(-12) // 최근 12주

  return (
    <Section title="Task 실천률" aside={rate !== null ? `${done} / ${due}개` : undefined}>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-text-secondary text-[13px]">지금까지 하기로 한 Task 중</p>
          <p className="mt-0.5 text-[32px] leading-none font-bold tracking-tight">
            {rate ?? '-'}
            <span className="text-ink-3 ml-0.5 text-[16px] font-semibold">%</span>
            <span className="text-ink-2 ml-2 text-[14px] font-medium">실천했어요</span>
          </p>
        </div>
        {hasRoutine && (
          <div className="flex gap-1.5">
            <Chip size="sm" selected={kind === 'ONE_TIME'} onClick={() => selectKind('ONE_TIME')}>
              할 일만
            </Chip>
            <Chip size="sm" selected={kind === 'ALL'} onClick={() => selectKind('ALL')}>
              루틴 포함
            </Chip>
          </div>
        )}
      </div>

      {recent.length === 0 ? (
        <p className="bg-bg-sunken text-ink-3 mt-4 rounded-2xl px-4 py-6 text-center text-sm">
          아직 날짜가 지난 {kind === 'ALL' ? 'Task' : '할 일'}이 없어요. 날짜가 지나면 여기서 실천률을 보여드려요.
        </p>
      ) : (
        <>
          {/* 주별 막대: 높이 = 그 주 실천률, 위 숫자 = 실천/해야 할 개수 */}
          <div className="mt-5 flex h-[150px] items-end gap-1.5" role="img" aria-label="주별 Task 실천률">
            {recent.map((b) => {
              const r = b.due ? b.done / b.due : 0
              return (
                <div key={b.week} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1">
                  <span className="text-text-secondary text-[10px] tabular-nums">{b.due ? `${b.done}/${b.due}` : ''}</span>
                  <div className="bg-progress-track relative w-full max-w-[28px] flex-1 overflow-hidden rounded-md">
                    {b.due > 0 && (
                      <div
                        className={cn(
                          'absolute inset-x-0 bottom-0 rounded-md transition-all',
                          r >= 0.8 ? 'bg-progress-fill' : r >= 0.5 ? 'bg-progress-fill/70' : 'bg-progress-fill/40'
                        )}
                        style={{ height: `${Math.max(r * 100, 4)}%` }}
                        title={`${b.week} 주 · ${Math.round(r * 100)}%`}
                      />
                    )}
                  </div>
                  <span className={cn('text-[10px] tabular-nums', b.current ? 'text-text-brand font-bold' : 'text-text-secondary')}>
                    {b.current ? '이번 주' : `${Number(b.week.slice(5, 7))}/${Number(b.week.slice(8, 10))}`}
                  </span>
                </div>
              )
            })}
          </div>
          <p className="text-ink-3 mt-3 text-[12px]">
            주마다 그 주에 하기로 한 Task 중 몇 개를 실천했는지예요 (오늘 이후 날짜의 Task는 빼고 계산해요).
            {bars.length > recent.length && ' 최근 12주만 보여줘요.'}
          </p>
        </>
      )}
    </Section>
  )
}
