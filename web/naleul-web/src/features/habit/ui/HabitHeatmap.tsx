'use client'

import { useMemo, useState } from 'react'
import { Flame } from 'lucide-react'
import { ChevronLeft, ChevronRight } from '@/components/icons'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { addDays, addMonths, weekdayIndex, WEEKDAY_LABEL } from '@/features/timetable/time'
import { useHabitHeatmap, type Habit, type HabitDayState } from '../api'

/** 칸 크기 · 간격 (px) */
const CELL = 20
const GAP = 3
/** 서버가 한 번에 주는 최대 기간(400일) 안에서 — 최근 12달 + 이번 달 */
const MAX_MONTHS_BACK = 12

const STATE_CLASS: Record<HabitDayState, string> = {
  DONE: 'bg-brand',
  MISSED: 'bg-heat-empty ring-line ring-1 ring-inset', // 명세 8.1 — 안 한 날은 연하게
  TODO: 'bg-surface ring-brand ring-[1.5px] ring-inset',
  OFF: 'bg-subtle',
  PAUSED: 'bg-hatch ring-hatch-line ring-1 ring-inset',
  BEFORE_START: 'bg-transparent',
  FUTURE: 'bg-transparent ring-line ring-1 ring-inset',
}

const STATE_LABEL: Record<HabitDayState, string> = {
  DONE: '했어요',
  MISSED: '놓쳤어요',
  TODO: '오늘 아직',
  OFF: '안 하는 날',
  PAUSED: '쉬는 중',
  BEFORE_START: '시작 전',
  FUTURE: '',
}

const firstOfMonth = (ymd: string) => `${ymd.slice(0, 8)}01`
const lastOfMonth = (ymd: string) => addDays(addMonths(firstOfMonth(ymd), 1), -1)

/** 그 달을 월요일부터 시작하는 주(열)로 — 각 주는 월~일 7칸, 그 달이 아닌 날은 null */
function monthWeeks(month: string) {
  const last = lastOfMonth(month)
  const weeks: (string | null)[][] = []
  let d = addDays(month, -weekdayIndex(month))
  while (d <= last) {
    const col: (string | null)[] = []
    for (let i = 0; i < 7; i++) {
      const x = addDays(d, i)
      col.push(x >= month && x <= last ? x : null)
    }
    weeks.push(col)
    d = addDays(d, 7)
  }
  return weeks
}

/**
 * 습관별 잔디 — 한 달씩 넘겨 봐요 (‹ ›). 가로는 요일(월~일), 세로는 그 달의 주 — 달력처럼 (2026-10-24).
 * 넓은 화면에서는 습관을 2~3열로 나란히 놓아 빈 공간을 줄여요.
 * 습관을 추가한 달부터 볼 수 있고, 그 달보다 앞은 비어 있어요.
 * (전체를 합친 잔디는 없앴어요: 습관마다 시작한 날이 달라서 합치면 "언제부터 했는지"가 안 보여요)
 */
export function HabitHeatmap({ habits, today }: { habits: Habit[]; today: string }) {
  const shown = useMemo(
    () =>
      habits
        .filter((h) => h.status !== 'DELETED')
        .sort((a, b) => Number(a.status === 'PAUSED') - Number(b.status === 'PAUSED')),
    [habits]
  )
  // 가장 먼저 시작한 습관의 그 달 1일부터 (최근 12달까지)
  const thisMonth = firstOfMonth(today)
  const limit = addMonths(thisMonth, -MAX_MONTHS_BACK)
  const earliest = shown.reduce((m, h) => (h.startDate < m ? h.startDate : m), today)
  const from = firstOfMonth(earliest) < limit ? limit : firstOfMonth(earliest)
  const { data, isPending } = useHabitHeatmap(from, shown.length > 0)
  const [month, setMonth] = useState(thisMonth)
  const changeMonth = (next: string, action: 'prev' | 'next' | 'today') => {
    const monthsAgo =
      (Number(thisMonth.slice(0, 4)) - Number(next.slice(0, 4))) * 12 +
      (Number(thisMonth.slice(5, 7)) - Number(next.slice(5, 7)))
    track('habit.heatmap_month_click', { date_action: action, months_ago: monthsAgo })
    setMonth(next)
  }

  const stateOf = useMemo(() => {
    const map = new Map<number, Map<string, HabitDayState>>()
    for (const row of data?.habits ?? []) {
      const m = new Map<string, HabitDayState>()
      row.cells.forEach((s, i) => m.set(addDays(data!.from, i), s))
      map.set(row.habitId, m)
    }
    return map
  }, [data])
  const weeks = useMemo(() => monthWeeks(month), [month])

  if (shown.length === 0) return null
  const canPrev = month > from
  const canNext = month < thisMonth
  const sameYear = month.slice(0, 4) === today.slice(0, 4)

  return (
    <Card className="p-5 sm:p-6">
      <div className="mb-4 flex items-center gap-2">
        <h2 className="text-h4">습관별 잔디</h2>
        <div className="ml-auto flex items-center gap-1">
          <button
            type="button"
            onClick={() => changeMonth(addMonths(month, -1), 'prev')}
            disabled={!canPrev}
            aria-label="지난달"
            className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1 disabled:opacity-30"
          >
            <ChevronLeft className="size-5" />
          </button>
          <span className="min-w-[72px] text-center text-[15px] font-bold tabular-nums">
            {sameYear ? '' : `${month.slice(0, 4)}년 `}
            {Number(month.slice(5, 7))}월
          </span>
          <button
            type="button"
            onClick={() => changeMonth(addMonths(month, 1), 'next')}
            disabled={!canNext}
            aria-label="다음 달"
            className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1 disabled:opacity-30"
          >
            <ChevronRight className="size-5" />
          </button>
          {month !== thisMonth && (
            <button
              type="button"
              onClick={() => changeMonth(thisMonth, 'today')}
              className="text-text-brand ml-1 text-[13px] font-semibold"
            >
              이번 달
            </button>
          )}
        </div>
      </div>

      {isPending ? (
        <div className="grid h-32 place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : (
        <ul className="grid gap-x-8 gap-y-6 sm:grid-cols-2 xl:grid-cols-3">
          {shown.map((h) => (
            <HabitMonth
              key={h.habitId}
              habit={h}
              today={today}
              month={month}
              weeks={weeks}
              states={stateOf.get(h.habitId)}
            />
          ))}
        </ul>
      )}
      <p className="text-ink-3 mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
        <Legend className="bg-brand">했어요</Legend>
        <Legend className="bg-heat-empty ring-line ring-1 ring-inset">놓쳤어요</Legend>
        <Legend className="bg-subtle">안 하는 날</Legend>
        <Legend className="bg-hatch ring-hatch-line ring-1 ring-inset">쉬는 중</Legend>
      </p>
    </Card>
  )
}

/** 습관 하나 — 위 이름 · 기록, 아래 그 달 잔디 (가로 요일 × 세로 주, 달력 모양) */
function HabitMonth({
  habit: h,
  today,
  month,
  weeks,
  states,
}: {
  habit: Habit
  today: string
  month: string
  weeks: (string | null)[][]
  states: Map<string, HabitDayState> | undefined
}) {
  const stateAt = (d: string): HabitDayState =>
    d > today ? 'FUTURE' : d < h.startDate ? 'BEFORE_START' : (states?.get(d) ?? 'BEFORE_START')
  const days = weeks.flat().filter((d): d is string => d !== null)
  const cells = days.map(stateAt)
  const doneDays = cells.filter((s) => s === 'DONE').length
  const dueDays = cells.filter((s) => s === 'DONE' || s === 'MISSED' || s === 'TODO').length
  const notYet = h.startDate > lastOfMonth(month)

  return (
    <li className={cn('flex min-w-0 flex-col gap-2.5', h.status === 'PAUSED' && 'opacity-60')}>
      <div className="min-w-0">
        <p className="truncate text-[14px] font-bold">
          {h.emoji ? `${h.emoji} ` : ''}
          {h.name}
        </p>
        <p className="text-ink-3 mt-0.5 text-[12px] tabular-nums">
          {notYet ? '아직 시작 전' : dueDays > 0 ? `이 달 ${doneDays}/${dueDays}일` : '이 달 할 날 없음'}
          <span className="text-ink-4 ml-1.5 text-[11px]">
            · {Number(h.startDate.slice(5, 7))}월 {Number(h.startDate.slice(8, 10))}일부터
          </span>
        </p>
        {h.streak > 1 && (
          <p className="text-goal-orange-fg mt-0.5 inline-flex items-center gap-0.5 text-[12px] font-semibold">
            <Flame className="size-3.5" />
            {h.streak}일 연속
          </p>
        )}
        {h.status === 'PAUSED' && <p className="text-ink-3 text-[12px]">쉬는 중</p>}
      </div>

      <div
        className="flex flex-col self-start"
        style={{ gap: GAP }}
        role="grid"
        aria-label={`${h.name} ${Number(month.slice(5, 7))}월`}
      >
        {/* 요일 눈금 (가로, 월~일) */}
        <div className="flex" style={{ gap: GAP }} aria-hidden>
          {WEEKDAY_LABEL.map((w) => (
            <span key={w} className="text-ink-4 text-center text-[10px] leading-none" style={{ width: CELL }}>
              {w}
            </span>
          ))}
        </div>
        {weeks.map((col, wi) => (
          <div key={wi} className="flex" style={{ gap: GAP }} role="row">
            {col.map((d, di) =>
              d === null ? (
                <span key={di} style={{ width: CELL, height: CELL }} />
              ) : (
                <Cell key={d} date={d} state={stateAt(d)} today={today} />
              )
            )}
          </div>
        ))}
      </div>
    </li>
  )
}

function Cell({ date: d, state: s, today }: { date: string; state: HabitDayState; today: string }) {
  return (
    <span
      role="gridcell"
      title={STATE_LABEL[s] ? `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))} · ${STATE_LABEL[s]}` : undefined}
      className={cn(
        'rounded-[3px]',
        STATE_CLASS[s],
        s === 'BEFORE_START' && 'bg-subtle/40',
        d === today && 'ring-ink-2 ring-1 ring-offset-1'
      )}
      style={{ width: CELL, height: CELL }}
    />
  )
}

function Legend({ className, children }: { className: string; children: string }) {
  return (
    <span className="flex items-center gap-1">
      <i className={cn('inline-block size-[11px] rounded-[3px]', className)} />
      {children}
    </span>
  )
}
