'use client'

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Chip } from '@/components/ui/Chip'
import { Card } from '@/components/ui/Card'
import { track } from '@/lib/client/analytics'
import { goalAccent } from '@/lib/goalTone'
import { addDays, startOfWeek, todayKst, weekdayIndex } from '@/features/timetable/time'
import { useGoalProgress, useGoalTasks, useRoutineHeatmap, type GoalCategory } from '../../api'
import { JAVA_DAYS } from '../../format'
import { RoutineHeatmapSection } from '../RoutineHeatmapSection'
import { TaskRateSection, weekly } from '../TaskRateSection'
import { ProgressSection } from './ProgressSection'

type View = 'all' | 'position' | 'task_rate' | 'routine_rate'

const VIEW_LABEL: Record<View, string> = {
  all: '전체',
  position: '지금 어디쯤?',
  task_rate: 'Task 실천률',
  routine_rate: '루틴 실천',
}

/** 그래프에 보여줄 최근 주 수 */
const WEEKS = 12
const pct = (n: number) => Math.round(Math.max(0, Math.min(100, n)))

/**
 * 달성형 목표 "진행" (명세 13.7) — 칩(전체 · 지금 어디쯤? · Task 실천률 · 루틴 실천, 하나만 고르기)
 *  - 전체: 세 가지를 주(월요일 시작) 단위 하나의 선 그래프로 (0~100%)
 *  - 나머지: 기존 영역 그대로 하나만
 *  - "함께 보는 그래프"(positionExtra)는 "지금 어디쯤?" 을 골랐을 때만 그 아래에 보여요 (2026-10-24)
 */
export function ProgressOverview({ goal, positionExtra }: { goal: GoalCategory; positionExtra?: ReactNode }) {
  const [view, setView] = useState<View>('all')
  const views: View[] = goal.temporary
    ? ['all', 'task_rate', 'routine_rate']
    : ['all', 'position', 'task_rate', 'routine_rate']
  const pick = (v: View) => {
    if (v === view) return
    track('goal_detail.progress_view_change', {
      goal_category_id: goal.goalCategoryId,
      goal_mode: goal.goalMode ?? 'ACHIEVEMENT',
      view: v,
      from_view: view,
    })
    setView(v)
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="진행 보기">
        {views.map((v) => (
          <Chip key={v} size="sm" selected={view === v} onClick={() => pick(v)} role="radio" aria-checked={view === v}>
            {VIEW_LABEL[v]}
          </Chip>
        ))}
      </div>
      {view === 'all' ? (
        <CombinedChart goal={goal} />
      ) : view === 'position' ? (
        <>
          <ProgressSection goal={goal} />
          {positionExtra}
        </>
      ) : view === 'task_rate' ? (
        <TaskRateSection goal={goal} />
      ) : (
        <RoutineHeatmapSection goal={goal} />
      )}
    </div>
  )
}

interface Series {
  key: string
  label: string
  color: string
  /** 주별 % (값이 없는 주는 null) */
  values: (number | null)[]
  /** 지금 값 (요약 글) */
  now: number | null
}

function CombinedChart({ goal }: { goal: GoalCategory }) {
  const today = todayKst()
  const progress = useGoalProgress(goal.goalCategoryId, !goal.temporary)
  const tasks = useGoalTasks(goal.goalCategoryId)
  const heat = useRoutineHeatmap(goal.goalCategoryId)

  const weeks = useMemo(() => {
    const last = startOfWeek(today)
    return Array.from({ length: WEEKS }, (_, i) => addDays(last, (i - WEEKS + 1) * 7))
  }, [today])

  const series = useMemo<Series[]>(() => {
    const out: Series[] = []
    // ① 지금 어디쯤? — 수치 목표면 (기록-시작)/(목표-시작), 아니면 Task 누적 완료율
    const p = progress.data
    if (p && !goal.temporary) {
      const m = p.metric
      const values = weeks.map((w) => {
        const end = addDays(w, 6)
        if (m) {
          const logs = m.logs.filter((l) => l.date <= end).sort((a, b) => a.date.localeCompare(b.date))
          const v = logs.length ? logs[logs.length - 1].value : null
          if (v == null || m.targetValue === m.startValue) return null
          return pct(((v - m.startValue) / (m.targetValue - m.startValue)) * 100)
        }
        const pts = p.tasks.series.filter((s) => s.date <= end && s.completed != null)
        const lastPt = pts[pts.length - 1]
        return lastPt && p.tasks.total ? pct(((lastPt.completed ?? 0) / p.tasks.total) * 100) : null
      })
      const now = m ? (m.progressPercent != null ? pct(m.progressPercent) : null) : (p.tasks.completionPercent ?? null)
      out.push({ key: 'position', label: '지금 어디쯤?', color: goalAccent(goal.colorCode), values, now })
    }
    // ② Task 실천률 — 일회성 Task, 주별 (TaskRateSection 과 같은 계산)
    const list = (tasks.data?.tasks ?? []).filter((t) => t.sourceType !== 'ROUTINE')
    if (list.length) {
      const bars = weekly(list, today)
      const byWeek = new Map(bars.map((b) => [b.week, b]))
      const due = bars.reduce((n, b) => n + b.due, 0)
      const done = bars.reduce((n, b) => n + b.done, 0)
      out.push({
        key: 'task_rate',
        label: 'Task 실천률',
        color: 'var(--color-brand)',
        values: weeks.map((w) => {
          const b = byWeek.get(w)
          return b && b.due ? pct((b.done / b.due) * 100) : null
        }),
        now: due ? pct((done / due) * 100) : null,
      })
    }
    // ③ 루틴 실천 — 해야 하는 요일 중 한 날 (오늘까지)
    const routines = (heat.data ?? []).filter((r) => r.repeatDays.length)
    if (routines.length) {
      const due = new Map<string, number>()
      const done = new Map<string, number>()
      for (const r of routines) {
        const set = new Set(r.completedDates)
        const end = r.repeatEndDate && r.repeatEndDate < today ? r.repeatEndDate : today
        for (let d = r.repeatStartDate < weeks[0] ? weeks[0] : r.repeatStartDate; d <= end; d = addDays(d, 1)) {
          if (!r.repeatDays.includes(JAVA_DAYS[weekdayIndex(d)])) continue
          const w = startOfWeek(d)
          due.set(w, (due.get(w) ?? 0) + 1)
          if (set.has(d)) done.set(w, (done.get(w) ?? 0) + 1)
        }
      }
      const totalDue = [...due.values()].reduce((a, b) => a + b, 0)
      const totalDone = [...done.values()].reduce((a, b) => a + b, 0)
      out.push({
        key: 'routine_rate',
        label: '루틴 실천',
        color: 'var(--color-accent-mint)',
        values: weeks.map((w) => (due.get(w) ? pct(((done.get(w) ?? 0) / due.get(w)!) * 100) : null)),
        now: totalDue ? pct((totalDone / totalDue) * 100) : null,
      })
    }
    return out
  }, [progress.data, tasks.data, heat.data, weeks, today, goal.temporary, goal.colorCode])

  const loading = (!goal.temporary && progress.isPending) || tasks.isPending || heat.isPending
  // 그래프는 실제 너비대로 그려요 (viewBox 를 늘리면 큰 화면에서 글자 · 높이가 같이 커져요)
  const boxRef = useRef<HTMLDivElement>(null)
  const [boxW, setBoxW] = useState(640)
  useEffect(() => {
    const el = boxRef.current
    if (!el) return
    const ro = new ResizeObserver(([e]) => setBoxW(Math.max(240, Math.round(e.contentRect.width))))
    ro.observe(el)
    return () => ro.disconnect()
  }, [loading, series.length])
  const W = boxW
  const H = 200
  const PAD = { l: 36, r: 12, t: 10, b: 24 }
  const x = (i: number) => PAD.l + (i / (WEEKS - 1)) * (W - PAD.l - PAD.r)
  const y = (v: number) => PAD.t + (1 - v / 100) * (H - PAD.t - PAD.b)
  // 점에 커서를 올리면(키보드는 포커스) 그 주의 값을 보여줘요 (2026-10-24)
  const [hover, setHover] = useState<{ key: string; i: number } | null>(null)
  const hovered = hover ? series.find((s) => s.key === hover.key) : undefined
  const hoveredValue = hovered && hover ? hovered.values[hover.i] : null
  const weekLabel = (i: number) => {
    const s = weeks[i]
    const e = addDays(s, 6)
    return `${Number(s.slice(5, 7))}/${Number(s.slice(8, 10))}~${Number(e.slice(5, 7))}/${Number(e.slice(8, 10))}`
  }

  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-h4">진행 한눈에</h2>
      {loading ? (
        <div className="bg-bg-sunken mt-4 h-[150px] animate-pulse rounded-xl" />
      ) : !series.length ? (
        <p className="text-text-secondary bg-bg-sunken mt-4 rounded-xl px-4 py-6 text-center text-sm">
          수치를 기록하거나 Task · 루틴을 실천하면 여기서 함께 보여드려요.
        </p>
      ) : (
        <>
          <div ref={boxRef} className="relative mt-4 w-full">
            <svg
              width={W}
              height={H}
              viewBox={`0 0 ${W} ${H}`}
              className="block"
              role="img"
              aria-label="주별 진행 · 실천률"
            >
              {[0, 50, 100].map((t) => (
                <g key={t}>
                  <line x1={PAD.l} x2={W - PAD.r} y1={y(t)} y2={y(t)} className="stroke-border-soft" />
                  <text x={PAD.l - 6} y={y(t) + 3} textAnchor="end" fontSize="11" className="fill-text-tertiary">
                    {t}%
                  </text>
                </g>
              ))}
              {weeks.map((w, i) =>
                i % 3 === 0 || i === WEEKS - 1 ? (
                  <text key={w} x={x(i)} y={H - 5} textAnchor="middle" fontSize="11" className="fill-text-tertiary">
                    {Number(w.slice(5, 7))}/{Number(w.slice(8, 10))}
                  </text>
                ) : null
              )}
              {series.map((s) => {
                const pts = s.values.map((v, i) => (v == null ? null : ([x(i), y(v)] as const)))
                const d = pts
                  .map((pt, i) =>
                    pt ? `${i > 0 && pts[i - 1] ? 'L' : 'M'}${pt[0].toFixed(1)},${pt[1].toFixed(1)}` : ''
                  )
                  .join(' ')
                return (
                  <g key={s.key} style={{ stroke: s.color, fill: s.color }}>
                    <path d={d} fill="none" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
                    {pts.map((pt, i) =>
                      pt ? (
                        <g key={i}>
                          <circle
                            cx={pt[0]}
                            cy={pt[1]}
                            r={hover?.key === s.key && hover.i === i ? 5.5 : 3.5}
                            stroke="none"
                          />
                          {/* 커서를 올리기 쉬운 투명한 큰 원 */}
                          <circle
                            cx={pt[0]}
                            cy={pt[1]}
                            r="11"
                            fill="transparent"
                            stroke="none"
                            className="cursor-pointer outline-none"
                            tabIndex={0}
                            role="img"
                            aria-label={`${s.label} ${weekLabel(i)} ${s.values[i]}%`}
                            onMouseEnter={() => setHover({ key: s.key, i })}
                            onMouseLeave={() => setHover(null)}
                            onFocus={() => setHover({ key: s.key, i })}
                            onBlur={() => setHover(null)}
                          />
                        </g>
                      ) : null
                    )}
                  </g>
                )
              })}
            </svg>
            {hover && hovered && hoveredValue != null && (
              <div
                role="tooltip"
                className="bg-bg-inverse text-text-inverse text-caption-md shadow-pop pointer-events-none absolute z-10 rounded-lg px-2.5 py-1.5 whitespace-nowrap"
                style={{
                  left: Math.min(Math.max(x(hover.i), 60), W - 60),
                  top: y(hoveredValue) - 10,
                  transform: 'translate(-50%, -100%)',
                }}
              >
                <span className="opacity-70">{weekLabel(hover.i)}</span> · {hovered.label}{' '}
                <b className="font-semibold tabular-nums">{hoveredValue}%</b>
              </div>
            )}
          </div>
          {/* 범례 + 지금 값 */}
          <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5" aria-label="범례">
            {series.map((s) => (
              <li key={s.key} className="text-caption-md text-text-secondary flex items-center gap-1.5">
                <span className="h-0.5 w-3 rounded-full" style={{ backgroundColor: s.color }} aria-hidden />
                {s.label}
                {s.now != null && <b className="text-text-primary font-semibold tabular-nums">{s.now}%</b>}
              </li>
            ))}
          </ul>
          <p className="text-caption-md text-text-tertiary mt-2">최근 {WEEKS}주 · 주(월~일)마다</p>
        </>
      )}
    </Card>
  )
}
