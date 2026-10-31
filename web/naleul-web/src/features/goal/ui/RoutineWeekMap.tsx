'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CalendarRange } from 'lucide-react'
import { ChevronDown } from '@/components/icons'
import { api } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { cn } from '@/lib/cn'
import { goalAccent, goalBg, goalFg } from '@/lib/goalTone'
import { minutesToTime, timeToMinutes, todayKst } from '@/features/timetable/time'
import { goalKeys, type JavaDayOfWeek } from '../api'
import { JAVA_DAYS, JAVA_DAY_LABEL } from '../format'
import { AIBadge } from '@/components/ui/AIBadge'

/** GET /routines/all — 내 모든 목표의 루틴 (목표 색 포함) */
export interface AllRoutine {
  routineId: number
  routineName: string
  goalCategoryId: number
  goalCategoryName: string
  goalCategoryColorCode: string | null
  repeatStartDate: string | null
  repeatEndDate: string | null
  repeatStartTime: string | null
  repeatEndTime: string | null
  routineStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'STOPPED' | 'COMPLETED' | 'DELETED'
  repeatDays: { dayOfWeek: JavaDayOfWeek; startTime: string | null; endTime: string | null }[]
}

export function useAllRoutines() {
  return useQuery({
    // 목표 · 루틴을 바꾸면 ['goals'] 를 다시 불러오니 그 아래에 둬요
    queryKey: [...goalKeys.all, 'routines-all'] as const,
    queryFn: async () => (await api.get<AllRoutine[]>('/v1/routines/all')) ?? [],
    staleTime: 30_000,
  })
}

/** 지금 반복 중인(또는 곧 시작할) 루틴만 */
const isLive = (r: AllRoutine, today: string) =>
  (r.routineStatus === 'NOT_STARTED' || r.routineStatus === 'IN_PROGRESS') &&
  (!r.repeatEndDate || r.repeatEndDate >= today)

interface Slot {
  key: string
  routineId: number | null
  name: string
  goalName: string
  color: string | null
  day: JavaDayOfWeek
  /** 그날 0시 기준 분. 자정을 넘기면 end > 1440 */
  start: number
  end: number
}

const range = (start: string, end: string) => {
  const s = timeToMinutes(start)
  let e = timeToMinutes(end)
  if (e <= s) e += 1440
  return { start: s, end: e }
}

/** 루틴 하나 → 요일별 시간 칸 (요일별 시간이 있으면 그 시간) */
function slotsOf(r: AllRoutine): Slot[] {
  const out: Slot[] = []
  for (const d of r.repeatDays) {
    const st = d.startTime ?? r.repeatStartTime
    const et = d.endTime ?? r.repeatEndTime
    if (!st || !et) continue
    out.push({
      key: `${r.routineId}-${d.dayOfWeek}`,
      routineId: r.routineId,
      name: r.routineName,
      goalName: r.goalCategoryName,
      color: r.goalCategoryColorCode,
      day: d.dayOfWeek,
      ...range(st, et),
    })
  }
  return out
}

/** 만들고 있는 루틴 (폼 값) */
export interface RoutineDraft {
  routineId?: number
  name: string
  days: JavaDayOfWeek[]
  /** 요일별 실제 시간 (기본 시간 또는 요일별 시간) — 시간이 없으면 빈 문자열 */
  timeOf: (d: JavaDayOfWeek) => { start: string; end: string }
}

/** 만들고 있는 루틴과 같은 요일 · 시간에 겹치는 기존 루틴 */
export function routineConflicts(all: AllRoutine[], draft: RoutineDraft, today = todayKst()) {
  const existing = all.filter((r) => r.routineId !== draft.routineId && isLive(r, today)).flatMap(slotsOf)
  const hits = new Map<number, { name: string; goalName: string; days: JavaDayOfWeek[]; time: string }>()
  for (const d of draft.days) {
    const t = draft.timeOf(d)
    if (!t.start || !t.end) continue
    const mine = range(t.start, t.end)
    for (const s of existing) {
      if (s.day !== d || !(s.start < mine.end && mine.start < s.end)) continue
      const h = hits.get(s.routineId!) ?? {
        name: s.name,
        goalName: s.goalName,
        days: [],
        time: `${minutesToTime(s.start % 1440)}–${minutesToTime(s.end % 1440)}`,
      }
      if (!h.days.includes(d)) h.days.push(d)
      hits.set(s.routineId!, h)
    }
  }
  return [...hits.values()]
}

const HOUR_PX = 18

/**
 * 루틴 폼 안의 "내 루틴 한눈에 보기" — 루틴을 만들 때 캘린더에 다녀오지 않아도 다른 루틴이 언제 있는지 보여요.
 *  - 위: 같은 요일 · 시간에 겹치는 루틴이 있으면 바로 경고 (+ 캘린더에서 AI로 한 번에 정리하기)
 *  - 펼치면: 월~일 주간표에 모든 목표의 루틴(목표 색)과 지금 만드는 루틴(점선)을 같이 그려요.
 *    빈 칸을 누르면 그 요일 · 시각으로 정해져요.
 */
export function RoutineWeekMap({
  draft,
  onPick,
  analytics,
}: {
  draft: RoutineDraft
  /** 빈 칸을 눌렀을 때 — 그 요일을 넣고 시작 시각을 그 시각으로 */
  onPick: (day: JavaDayOfWeek, minutes: number) => void
  /** 분석용 — 목표 id · 새 루틴인지 · 목표 루틴 수 */
  analytics?: { goalId: number; isNew: boolean; routineCount: number }
}) {
  const routines = useAllRoutines()
  const [open, setOpen] = useState(false)
  const today = todayKst()
  const all = routines.data ?? []
  const existing = all.filter((r) => r.routineId !== draft.routineId && isLive(r, today)).flatMap(slotsOf)
  const conflicts = routineConflicts(all, draft, today)
  const conflictCount = conflicts.length
  const goalId = analytics?.goalId
  const isNew = analytics?.isNew

  // 겹침 안내가 처음 뜰 때 한 번만 (입력창 하나당)
  const conflictSeen = useRef(false)
  useEffect(() => {
    if (goalId == null || conflictCount === 0 || conflictSeen.current) return
    conflictSeen.current = true
    track('goal_detail.routine_conflict_view', {
      goal_category_id: goalId,
      conflict_count: conflictCount,
      is_new: isNew,
    })
  }, [goalId, conflictCount, isNew])

  const mine: Slot[] = draft.days.flatMap((d) => {
    const t = draft.timeOf(d)
    if (!t.start || !t.end) return []
    return [
      {
        key: `new-${d}`,
        routineId: null,
        name: draft.name || '새 루틴',
        goalName: '',
        color: null,
        day: d,
        ...range(t.start, t.end),
      },
    ]
  })

  // 보이는 시간: 기본 6시~24시, 루틴이 그 밖에 있으면 넓혀요
  const lo = Math.min(6 * 60, ...existing.concat(mine).map((s) => s.start))
  const hi = Math.max(24 * 60, ...existing.concat(mine).map((s) => Math.min(s.end, 1440 + 6 * 60)))
  const from = Math.floor(lo / 60) * 60
  const to = Math.ceil(hi / 60) * 60
  const height = ((to - from) / 60) * HOUR_PX
  const y = (m: number) => ((m - from) / 60) * HOUR_PX
  const liveCount = new Set(existing.map((s) => s.routineId)).size
  // 겹치는 칸은 나란히 (기존 = 왼쪽 절반, 새 루틴 = 오른쪽 절반)
  const overlaps = (a: Slot, b: Slot) => a.day === b.day && a.start < b.end && b.start < a.end
  const half = (s: Slot) =>
    s.routineId === null ? existing.some((e) => overlaps(e, s)) : mine.some((m) => overlaps(m, s))
  const sideStyle = (s: Slot) =>
    !half(s) ? { left: 2, right: 2 } : s.routineId === null ? { left: '50%', right: 2 } : { left: 2, right: '50%' }

  const conflictCommand = conflicts.length
    ? `${conflicts.map((c) => `'${c.name}'`).join(', ')} 루틴과 '${draft.name || '새 루틴'}' 시간이 겹쳐. 겹치지 않게 정리해줘`
    : ''

  return (
    <div className="space-y-2">
      {conflicts.length > 0 && (
        <div className="bg-warning-soft border-border-subtle rounded-xl border px-3 py-2.5 text-[13px]">
          <p className="flex items-start gap-1.5 font-semibold">
            <AlertTriangle className="text-warning mt-0.5 size-4 shrink-0" />
            같은 시간에 이미 루틴이 있어요
          </p>
          <ul className="text-ink-2 mt-1 space-y-0.5 pl-6">
            {conflicts.map((c) => (
              <li key={`${c.name}-${c.time}`}>
                <b>{c.name}</b>
                <span className="text-ink-3"> ({c.goalName})</span> · {c.days.map((d) => JAVA_DAY_LABEL[d]).join('·')}{' '}
                {c.time}
              </li>
            ))}
          </ul>
          <p className="text-ink-3 mt-1.5 pl-6 text-[12px]">
            그대로 둬도 괜찮아요 (같은 시간에 둘 다 보여요). 많이 겹치면{' '}
            <Link
              href={`/calendar?view=week&ai=${encodeURIComponent(conflictCommand)}`}
              onClick={() =>
                analytics &&
                track('goal_detail.routine_conflict_ai_click', {
                  goal_category_id: analytics.goalId,
                  conflict_count: conflicts.length,
                  is_new: analytics.isNew,
                })
              }
              className="text-text-brand inline-flex items-center gap-1 font-semibold hover:underline"
            >
              <AIBadge size="sm" className="size-4 rounded-[4px] [&_svg]:size-2.5" />
              캘린더에서 AI로 한 번에 정리
            </Link>
            할 수 있어요.
          </p>
        </div>
      )}

      <button
        type="button"
        onClick={() => {
          if (analytics)
            track('goal_detail.routine_weekmap_toggle', {
              goal_category_id: analytics.goalId,
              expanded: !open,
              routine_count: analytics.routineCount,
            })
          setOpen(!open)
        }}
        aria-expanded={open}
        className="text-ink-2 hover:text-ink flex items-center gap-1.5 text-[13px] font-semibold"
      >
        <CalendarRange className="size-4" />내 루틴 한눈에 보기
        {liveCount > 0 && <span className="text-ink-3 font-medium">({liveCount}개)</span>}
        <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="border-line bg-surface rounded-xl border p-2">
          {routines.isPending ? (
            <div className="bg-subtle h-40 animate-pulse rounded-lg" />
          ) : (
            <>
              <div className="grid grid-cols-[32px_repeat(7,minmax(0,1fr))] gap-x-0.5">
                <span />
                {JAVA_DAYS.map((d) => (
                  <span
                    key={d}
                    className={cn(
                      'pb-1 text-center text-[11px] font-bold',
                      draft.days.includes(d) ? 'text-text-brand' : 'text-ink-3'
                    )}
                  >
                    {JAVA_DAY_LABEL[d]}
                  </span>
                ))}
                {/* 시간 눈금 */}
                <div className="relative" style={{ height }}>
                  {Array.from({ length: (to - from) / 60 + 1 }, (_, i) => from + i * 60)
                    .filter((m) => (m - from) % 180 === 0)
                    .map((m) => (
                      <span
                        key={m}
                        className="text-ink-4 absolute right-1 -translate-y-1/2 text-[10px] tabular-nums"
                        style={{ top: y(m) }}
                      >
                        {String(Math.floor(m / 60) % 24).padStart(2, '0')}
                      </span>
                    ))}
                </div>
                {JAVA_DAYS.map((d) => (
                  <button
                    key={d}
                    type="button"
                    aria-label={`${JAVA_DAY_LABEL[d]}요일 시간 고르기`}
                    onClick={(e) => {
                      const rect = e.currentTarget.getBoundingClientRect()
                      const m = from + ((e.clientY - rect.top) / HOUR_PX) * 60
                      onPick(d, Math.max(0, Math.min(1410, Math.floor(m / 30) * 30)))
                    }}
                    className="bg-subtle/50 hover:bg-brand-soft/40 relative cursor-crosshair overflow-hidden rounded-md"
                    style={{ height }}
                  >
                    {Array.from({ length: (to - from) / 60 }, (_, i) => (
                      <span
                        key={i}
                        className="border-border-soft pointer-events-none absolute inset-x-0 border-t"
                        style={{ top: i * HOUR_PX }}
                      />
                    ))}
                    {existing
                      .filter((s) => s.day === d)
                      .map((s) => (
                        <span
                          key={s.key}
                          title={`${s.name} (${s.goalName}) ${minutesToTime(s.start % 1440)}–${minutesToTime(s.end % 1440)}`}
                          className="pointer-events-none absolute overflow-hidden rounded-[4px] pr-0.5 pl-1 text-left text-[9px] leading-tight font-semibold"
                          style={{
                            ...sideStyle(s),
                            top: y(s.start),
                            height: Math.max(y(Math.min(s.end, to)) - y(s.start), 6),
                            // 디자인 시스템 Event — 연한 목표 바탕 + 왼쪽 색 선 + 목표 글자색
                            backgroundColor: goalBg(s.color),
                            color: goalFg(s.color),
                            boxShadow: `inset 2px 0 0 ${goalAccent(s.color)}`,
                          }}
                        >
                          {s.end - s.start >= 40 ? s.name : ''}
                        </span>
                      ))}
                    {mine
                      .filter((s) => s.day === d)
                      .map((s) => (
                        <span
                          key={s.key}
                          className="border-brand bg-brand-soft/70 text-text-brand pointer-events-none absolute overflow-hidden rounded-[4px] border-2 border-dashed px-0.5 text-left text-[9px] leading-tight font-bold"
                          style={{
                            ...sideStyle(s),
                            top: y(s.start),
                            height: Math.max(y(Math.min(s.end, to)) - y(s.start), 6),
                          }}
                        >
                          {s.end - s.start >= 40 ? s.name : ''}
                        </span>
                      ))}
                  </button>
                ))}
              </div>
              <p className="text-ink-3 mt-2 text-[11px]">
                색 칸 = 다른 루틴 · 점선 = 지금 만드는 루틴 · 빈 곳을 누르면 그 요일 · 시각으로 정해져요
              </p>
            </>
          )}
        </div>
      )}
    </div>
  )
}
