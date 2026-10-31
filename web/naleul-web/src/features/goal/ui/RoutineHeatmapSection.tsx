'use client'

import { useEffect, useMemo, useRef } from 'react'
import { Flame } from 'lucide-react'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { addDays, startOfWeek, todayKst, weekdayIndex } from '@/features/timetable/time'
import { useRoutineHeatmap, type GoalCategory, type JavaDayOfWeek, type RoutineHeatmap, type RoutineTimeStats } from '../api'
import { JAVA_DAYS, JAVA_DAY_LABEL } from '../format'
import { Section } from './sections'

/** 칸 크기·간격 (px) — 깃허브 잔디처럼 작게 */
const CELL = 13
const GAP = 3

type CellState = 'none' | 'done' | 'extra' | 'missed' | 'today' | 'future'

const CELL_CLASS: Record<CellState, string> = {
  none: 'bg-transparent',
  done: 'bg-brand',
  // 반복 요일이 아닌 날에 '다른 날 추가'로 한 날 — 같은 색, 점선 테두리로 구분
  extra: 'bg-brand/60 ring-brand ring-1 ring-inset',
  missed: 'bg-heat-empty ring-line ring-1 ring-inset', // 명세 8.1 — 안 한 날은 연하게
  today: 'bg-surface ring-brand ring-[1.5px] ring-inset',
  future: 'bg-subtle',
}

/** 이 루틴이 그날 해야 하는 날인지 (기간 안 + 반복 요일) */
const isScheduled = (r: RoutineHeatmap, ymd: string, dayIndex: number) =>
  ymd >= r.repeatStartDate && (!r.repeatEndDate || ymd <= r.repeatEndDate) && r.repeatDays.includes(JAVA_DAYS[dayIndex])

function stats(r: RoutineHeatmap, today: string) {
  const done = new Set(r.completedDates)
  let due = 0
  let completed = 0
  const end = r.repeatEndDate && r.repeatEndDate < today ? r.repeatEndDate : today
  for (let d = r.repeatStartDate; d <= end; d = addDays(d, 1)) {
    if (!r.repeatDays.includes(JAVA_DAYS[weekdayIndex(d)])) continue
    due++
    if (done.has(d)) completed++
  }
  // 반복 요일이 아닌 날에 한 것 ('다른 날 추가') — 실천률에는 넣지 않고 따로 보여줘요
  const extra = r.completedDates.filter((d) => !isScheduled(r, d, weekdayIndex(d))).length
  return { due, completed, extra, rate: due ? Math.round((completed / due) * 100) : null }
}

/** 히트맵 줄: 반복 요일 + '다른 날 추가'로 한 적이 있는 요일 */
function rowDays(r: RoutineHeatmap) {
  const extraDays = new Set(
    r.completedDates.filter((d) => !isScheduled(r, d, weekdayIndex(d))).map((d) => JAVA_DAYS[weekdayIndex(d)])
  )
  return JAVA_DAYS.map((d, i) => ({ d, i })).filter(({ d }) => r.repeatDays.includes(d) || extraDays.has(d))
}

/**
 * 루틴 실천 히트맵 — 루틴마다 "해야 하는 요일만" 칸으로 그려요 (월·수·금 루틴이면 월·수·금 3줄).
 * 실행한 날은 색이 채워지고, 지나갔는데 안 한 날은 회색, 아직 안 온 날은 옅게.
 * 모든 루틴이 같은 주(열)를 공유해서 한눈에 비교할 수 있어요. 각 루틴 아래에 따로 두면 비교가 어려워요.
 */
export function RoutineHeatmapSection({ goal }: { goal: GoalCategory }) {
  const { data, isPending } = useRoutineHeatmap(goal.goalCategoryId)
  const today = todayKst()
  const routines = useMemo(
    () =>
      (data ?? [])
        .filter((r) => r.repeatDays.length > 0)
        .sort((a, b) => a.repeatStartDate.localeCompare(b.repeatStartDate)),
    [data]
  )
  const scroller = useRef<HTMLDivElement>(null)

  // 모든 루틴이 함께 쓰는 주(열) 목록: 가장 이른 시작 주 ~ 가장 늦은 종료 주
  const weeks = useMemo(() => {
    if (!routines.length) return []
    const first = startOfWeek(
      routines.reduce((m, r) => (r.repeatStartDate < m ? r.repeatStartDate : m), routines[0].repeatStartDate)
    )
    const lastEnd = routines.reduce((m, r) => {
      const e = r.repeatEndDate ?? addDays(today, 28)
      return e > m ? e : m
    }, today)
    const list: string[] = []
    for (let w = first; w <= lastEnd && list.length < 106; w = addDays(w, 7)) list.push(w)
    return list
  }, [routines, today])

  // 처음 열면 오늘이 보이게 가로 스크롤
  useEffect(() => {
    const el = scroller.current
    if (!el || !weeks.length) return
    const idx = weeks.findIndex((w) => w > today) - 1
    const col = idx < 0 ? weeks.length - 1 : idx
    el.scrollLeft = Math.max(col * (CELL + GAP) - el.clientWidth + 120, 0)
  }, [weeks, today])

  if (isPending) {
    return (
      <Section title="루틴 실천">
        <div className="grid h-24 place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      </Section>
    )
  }
  if (!routines.length) return null

  const totals = routines.map((r) => stats(r, today))
  const allDue = totals.reduce((n, s) => n + s.due, 0)
  const allDone = totals.reduce((n, s) => n + s.completed, 0)

  return (
    <Section
      title="루틴 실천"
      aside={allDue ? `전체 ${allDone} / ${allDue}회 · ${Math.round((allDone / allDue) * 100)}%` : undefined}
    >
      <p className="text-ink-3 -mt-2 mb-4 text-[13px]">
        해야 하는 요일만 칸으로 보여줘요. 실천한 날은 색이 채워지고, 놓친 날은 회색이에요. 다른 날에 추가로 한 날은 테두리로 표시해요.
      </p>

      <div ref={scroller} className="-mx-1 overflow-x-auto px-1 pb-2">
        <div className="inline-flex flex-col gap-5" style={{ minWidth: '100%' }}>
          {/* 달 표시 */}
          <div className="flex pl-7" style={{ gap: GAP }} aria-hidden>
            {weeks.map((w, i) => {
              const showMonth = i === 0 || w.slice(5, 7) !== weeks[i - 1].slice(5, 7)
              return (
                <span key={w} className="text-ink-3 relative text-[11px]" style={{ width: CELL, height: 14 }}>
                  {showMonth && <span className="absolute left-0 whitespace-nowrap">{Number(w.slice(5, 7))}월</span>}
                </span>
              )
            })}
          </div>

          {routines.map((r, ri) => {
            const s = totals[ri]
            const done = new Set(r.completedDates)
            const rows = rowDays(r)
            return (
              <div key={r.routineId}>
                <div className="mb-1.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5 pr-2">
                  <span className="text-[14px] font-semibold">{r.routineName}</span>
                  <span className="text-ink-3 text-[12px] tabular-nums">
                    {s.completed} / {s.due}회{s.rate !== null ? ` · ${s.rate}%` : ''}
                    {s.extra > 0 ? ` · 다른 날 +${s.extra}회` : ''}
                  </span>
                  {r.currentStreak > 1 && (
                    <span className="inline-flex items-center gap-0.5 text-[12px] font-semibold text-goal-orange-fg">
                      <Flame className="size-3.5" />
                      {r.currentStreak}회 연속
                    </span>
                  )}
                </div>
                <div className="flex flex-col" style={{ gap: GAP }}>
                  {rows.map(({ d, i }) => (
                    <div key={d} className="flex items-center" style={{ gap: GAP }}>
                      <span className="text-ink-3 w-6 shrink-0 text-[11px]">{JAVA_DAY_LABEL[d]}</span>
                      {weeks.map((w) => {
                        const ymd = addDays(w, i)
                        const state: CellState = !isScheduled(r, ymd, i)
                          ? done.has(ymd)
                            ? 'extra'
                            : 'none'
                          : done.has(ymd)
                            ? 'done'
                            : ymd > today
                              ? 'future'
                              : ymd === today
                                ? 'today'
                                : 'missed'
                        return (
                          <span
                            key={w}
                            title={state === 'none' ? undefined : `${ymd} · ${STATE_LABEL[state]}`}
                            className={cn('shrink-0 rounded-[3px]', CELL_CLASS[state])}
                            style={{ width: CELL, height: CELL }}
                          />
                        )
                      })}
                    </div>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      <RoutineTimeStatsList routines={routines} />

      {/* 범례 */}
      <div className="text-ink-3 mt-3 flex flex-wrap items-center gap-3 text-[12px]">
        {(['done', 'extra', 'missed', 'today', 'future'] as const).map((k) => (
          <span key={k} className="inline-flex items-center gap-1.5">
            <span className={cn('inline-block rounded-[3px]', CELL_CLASS[k])} style={{ width: 11, height: 11 }} />
            {STATE_LABEL[k]}
          </span>
        ))}
      </div>
    </Section>
  )
}

const STATE_LABEL: Record<Exclude<CellState, 'none'>, string> = {
  done: '실천',
  extra: '다른 날 실천',
  missed: '놓침',
  today: '오늘',
  future: '예정',
}

/**
 * 루틴마다 "주로 몇 시~몇 시에 실천했는지" + 0~23시 막대 (2026-10).
 * 완료 기록의 실제 시각(없으면 계획 시각)으로 서버가 계산해 줘요.
 */
function RoutineTimeStatsList({ routines }: { routines: RoutineHeatmap[] }) {
  return (
    <div className="border-line mt-5 border-t pt-4">
      <h3 className="mb-3 text-[14px] font-semibold">주로 실천한 시간</h3>
      <ul className="flex flex-col gap-3.5">
        {routines.map((r) => (
          <li key={r.routineId} className="flex flex-col gap-1.5 sm:flex-row sm:items-center sm:gap-4">
            <div className="min-w-0 sm:w-56 sm:shrink-0">
              <p className="truncate text-[13px] font-semibold">{r.routineName}</p>
              <p className="text-ink-3 text-[12px] tabular-nums">{timeStatsSentence(r.timeStats)}</p>
            </div>
            {r.timeStats && r.timeStats.practiceCount > 0 && <HourBars stats={r.timeStats} />}
          </li>
        ))}
      </ul>
    </div>
  )
}

export function timeStatsSentence(t: RoutineTimeStats | null | undefined) {
  if (!t || t.practiceCount === 0) return '아직 시간 기록이 없어요'
  const days = byDayLine(t)
  if (days) return `${days}에 가장 많이 실천했어요`
  return `주로 ${t.bestStart}~${t.bestEnd}에 실천했어요 · ${t.practiceCount}회 중 ${t.inWindowCount}회`
}

/** "화 19:00~19:30 · 목 17:10~17:40" (요일별 최빈 시간대가 없으면 null) */
export function byDayLine(t: RoutineTimeStats | null | undefined) {
  const list = t?.byDay ?? []
  if (!list.length) return null
  return list
    .map((d) => `${JAVA_DAY_LABEL[d.day as JavaDayOfWeek] ?? d.day} ${d.start}~${d.end}`)
    .join(' · ')
}

function HourBars({ stats }: { stats: RoutineTimeStats }) {
  const max = Math.max(1, ...stats.hourly)
  const startH = Number(stats.bestStart.slice(0, 2))
  const endRaw =
    stats.bestEnd === '00:00' ? 24 : Number(stats.bestEnd.slice(0, 2)) + (stats.bestEnd.slice(3) === '00' ? 0 : 1)
  const inBest = (h: number) => (startH <= endRaw ? h >= startH && h < endRaw : h >= startH || h < endRaw)
  return (
    <div className="min-w-0 flex-1" aria-hidden>
      <div className="flex h-8 items-end gap-[2px]">
        {stats.hourly.map((v, h) => (
          <span
            key={h}
            title={`${h}시 · ${v}회`}
            className={cn('flex-1 rounded-[2px]', v === 0 ? 'bg-subtle' : inBest(h) ? 'bg-brand' : 'bg-brand/35')}
            style={{ height: v === 0 ? 2 : `${Math.max(12, (v / max) * 100)}%` }}
          />
        ))}
      </div>
      <div className="text-ink-3 mt-0.5 flex justify-between text-[10px] tabular-nums">
        <span>0시</span>
        <span>6시</span>
        <span>12시</span>
        <span>18시</span>
        <span>24시</span>
      </div>
    </div>
  )
}
