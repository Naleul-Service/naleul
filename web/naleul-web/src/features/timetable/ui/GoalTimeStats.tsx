'use client'

import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { goalAccent } from '@/lib/goalTone'
import { useGoalTime } from '../api'
import type { GoalTimeEntry } from '../types'

/** 80 → "1시간 20분", 45 → "45분", 120 → "2시간" */
export function formatDuration(minutes: number) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (!h) return `${m}분`
  return m ? `${h}시간 ${m}분` : `${h}시간`
}

const goalLabel = (g: GoalTimeEntry) =>
  g.goalCategoryId == null
    ? '목표 없음'
    : `${g.goalEmoji ? `${g.goalEmoji} ` : ''}${g.goalCategoryName ?? '이름 없는 목표'}`

type Trigger = 'hover' | 'tap' | 'focus'

/**
 * 목표별 실천 시간 (2026-10) — 기간 동안 완료한 Task 시간을 목표별로 나눈 얇은 막대.
 * 크게 보여주지 않고 참고용으로만: 막대 + 합계만 두고,
 *  - 넓은 화면(마우스): 칸에 올리면 어떤 목표인지 말풍선
 *  - 터치: 칸을 누르면 말풍선 (다시 누르거나 바깥을 누르면 닫혀요)
 * 일간(오늘 할 일 아래) · 주간(격자 위 한 줄) · 월간(달력 아래)에 써요.
 */
export function GoalTimeStats({
  start,
  end,
  viewMode,
  periodLabel,
  variant = 'card',
  className,
}: {
  start: string
  end: string
  viewMode: 'day' | 'week' | 'month'
  /** "오늘" · "이번 주" · "10월" … 합계 앞에 붙여요 */
  periodLabel: string
  /** card: 제목 · 합계 위, 막대 아래 / inline: 한 줄 (주간 격자 위) */
  variant?: 'card' | 'inline'
  className?: string
}) {
  const { data, isPending, isError } = useGoalTime(start, end)
  // placeholderData 로 이전 기간이 잠깐 보일 수 있어서 기간을 확인해요
  const current = data && data.startDate === start && data.endDate === end ? data : undefined
  const goals = useMemo(() => current?.goals ?? [], [current])
  const total = current?.totalMinutes ?? 0
  // 말풍선 — 기간이 바뀌면 저절로 닫혀요 (다른 기간에서 연 것은 없는 것으로)
  const range = `${start}~${end}`
  const [opened, setActive] = useState<{ index: number; trigger: Trigger; range: string } | null>(null)
  const active = opened && opened.range === range ? opened : null
  const rootRef = useRef<HTMLDivElement>(null)
  const rangeDays = Math.round((Date.parse(end) - Date.parse(start)) / 86_400_000) + 1

  // 기간이 바뀌면 이벤트 중복 막기 초기화
  const seen = useRef(new Set<string>())
  useEffect(() => {
    seen.current = new Set()
  }, [start, end])

  // calendar.goal_time_view — 기간마다 한 번 (데이터가 온 뒤)
  useEffect(() => {
    if (!current) return
    const key = `view:${viewMode}:${start}:${end}`
    if (seen.current.has(key)) return
    seen.current.add(key)
    track('calendar.goal_time_view', {
      view_mode: viewMode,
      range_days: rangeDays,
      goal_count: current.goals.filter((g) => g.goalCategoryId != null).length,
      total_minutes: current.totalMinutes,
      has_data: current.totalMinutes > 0,
    })
  }, [current, viewMode, start, end, rangeDays])

  // 터치로 연 말풍선은 바깥을 누르면 닫혀요
  useEffect(() => {
    if (active?.trigger !== 'tap') return
    const onDown = (e: globalThis.PointerEvent) => {
      if (!(e.target instanceof Node && rootRef.current?.contains(e.target))) setActive(null)
    }
    document.addEventListener('pointerdown', onDown)
    return () => document.removeEventListener('pointerdown', onDown)
  }, [active])

  const open = (index: number, trigger: Trigger) => {
    setActive({ index, trigger, range })
    const g = goals[index]
    const key = `seg:${g.goalCategoryId ?? 'none'}`
    if (seen.current.has(key)) return
    seen.current.add(key)
    track('calendar.goal_time_segment_view', {
      view_mode: viewMode,
      range_days: rangeDays,
      goal_category_id: g.goalCategoryId ?? undefined,
      has_goal: g.goalCategoryId != null,
      minutes: g.minutes,
      share_percent: total ? Math.round((g.minutes / total) * 100) : 0,
      rank: index + 1,
      trigger,
    })
  }

  // 말풍선 가로 위치 = 그 칸의 가운데 (%)
  const centers = useMemo(
    () =>
      goals.map((g, i) => {
        if (!total) return 0
        const before = goals.slice(0, i).reduce((n, x) => n + x.minutes, 0)
        return ((before + g.minutes / 2) / total) * 100
      }),
    [goals, total]
  )

  const title = '목표별 실천 시간'
  const summary = isPending ? '…' : isError ? '불러오지 못했어요' : `${periodLabel} ${formatDuration(total)}`
  const activeGoal = active ? goals[active.index] : null

  const bar = (
    <div ref={rootRef} className={cn('relative', variant === 'inline' && 'min-w-[120px] flex-1')}>
      <div
        aria-label={`${title} — ${summary}`}
        className="bg-progress-track flex h-2 w-full gap-[2px] overflow-hidden rounded-full"
      >
        {total > 0 &&
          goals.map((g, i) => {
            const pct = Math.round((g.minutes / total) * 100)
            return (
              <button
                key={g.goalCategoryId ?? 'none'}
                type="button"
                aria-label={`${goalLabel(g)} ${formatDuration(g.minutes)} (${pct}%)`}
                aria-pressed={active?.index === i}
                onPointerEnter={(e: PointerEvent) => e.pointerType === 'mouse' && open(i, 'hover')}
                onPointerLeave={(e: PointerEvent) =>
                  e.pointerType === 'mouse' && setActive((a) => (a?.trigger === 'hover' ? null : a))
                }
                onClick={(e) => {
                  // 마우스는 올리기만 해도 보여요 — 누르기는 터치 · 펜만
                  if ((e.nativeEvent as globalThis.PointerEvent).pointerType === 'mouse') return
                  if (active?.index === i) setActive(null)
                  else open(i, 'tap')
                }}
                onFocus={(e) => e.currentTarget.matches(':focus-visible') && open(i, 'focus')}
                onBlur={() => setActive((a) => (a?.trigger === 'focus' ? null : a))}
                className={cn(
                  'h-full min-w-1 origin-left animate-[bar-grow_520ms_cubic-bezier(0.22,1,0.36,1)_both] cursor-default transition-opacity duration-150 outline-none',
                  activeGoal && active?.index !== i && 'opacity-40'
                )}
                style={{
                  flexGrow: g.minutes,
                  flexBasis: 0,
                  backgroundColor:
                    g.goalCategoryId == null ? 'var(--color-border-subtle)' : goalAccent(g.goalColorCode),
                  animationDelay: `${i * 40}ms`,
                }}
              />
            )
          })}
      </div>
      {activeGoal && active && (
        <div
          role="tooltip"
          className="bg-bg-inverse text-text-inverse shadow-pop pointer-events-none absolute bottom-full z-30 mb-2 w-max max-w-[220px] animate-[tip-in_160ms_cubic-bezier(0.22,1,0.36,1)] rounded-lg px-2.5 py-1.5 text-[12px] leading-snug"
          style={{
            left: `clamp(0px, ${centers[active.index]}%, 100%)`,
            // 가장자리 칸은 말풍선이 밖으로 나가지 않게 한쪽으로 붙여요 (tip-in 애니메이션도 같은 값을 써요)
            ['--tip-x' as string]: centers[active.index] < 20 ? '-10%' : centers[active.index] > 80 ? '-90%' : '-50%',
            transform: 'translateX(var(--tip-x))',
          }}
        >
          <span className="flex items-center gap-1.5 font-semibold">
            <span
              className="size-2 shrink-0 rounded-full"
              style={{
                backgroundColor:
                  activeGoal.goalCategoryId == null
                    ? 'var(--color-border-subtle)'
                    : goalAccent(activeGoal.goalColorCode),
              }}
              aria-hidden
            />
            <span className="truncate">{goalLabel(activeGoal)}</span>
          </span>
          <span className="block tabular-nums opacity-80">
            {formatDuration(activeGoal.minutes)} · {total ? Math.round((activeGoal.minutes / total) * 100) : 0}% · Task{' '}
            {activeGoal.taskCount}개
          </span>
        </div>
      )}
    </div>
  )

  if (variant === 'inline') {
    return (
      <section aria-label={title} className={cn('flex flex-wrap items-center gap-x-3 gap-y-1.5', className)}>
        <h3 className="text-ink-3 shrink-0 text-[12px] font-semibold">{title}</h3>
        {bar}
        <span className="text-ink-3 shrink-0 text-[12px] tabular-nums">
          {!isPending && !isError && total === 0 ? '완료한 Task가 없어요' : summary}
        </span>
      </section>
    )
  }

  return (
    <section aria-label={title} className={cn('border-line bg-surface rounded-2xl border px-4 py-3', className)}>
      <header className="mb-2.5 flex items-baseline gap-2">
        <h3 className="text-ink-2 text-[13px] font-semibold">{title}</h3>
        <span className="text-ink-3 ml-auto text-[12px] tabular-nums">{summary}</span>
      </header>
      {bar}
      {!isPending && !isError && total === 0 && (
        <p className="text-ink-4 mt-2 text-[12px]">완료한 Task가 생기면 목표별로 나눠 보여드려요</p>
      )}
    </section>
  )
}
