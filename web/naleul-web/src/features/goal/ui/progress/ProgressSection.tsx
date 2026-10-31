'use client'

import { useState, type FormEvent } from 'react'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { TrendingDown, TrendingUp } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import {
  useDeleteMetricLog,
  useGoalProgress,
  useRecordMetric,
  type GoalCategory,
  type GoalProgress,
  type GoalReflection,
  type MetricProgress,
  type ProgressStatus,
  type TaskProgress,
} from '../../api'
import { InlineConfirm, RowActions, inlineInput, useEditing } from '../../edit/inline'
import { Section } from '../sections'
import { moodOf } from './DailyCheckInCard'
import { ProgressChart, dayNum, type ChartSeries } from './ProgressChart'
import { MetricEditForm, type MetricEditEntry } from './MetricEditForm'

/**
 * 목표 상세 "지금 어디쯤?" — 목표 지점까지 지금 어디에 있는지 보여줘요.
 *
 * - 수치 목표(체중 72 → 69kg 등): 시작 → 목표 막대 위 현재 위치 + 계획선 대비 기록 꺾은선 + 오늘 기록 입력
 * - 수치가 없는 목표: 오늘까지 해야 했던 Task 대비 완료한 Task 누적 그래프
 */

// ─── 숫자 · 날짜 표시 ─────────────────────────────────────────

export const fmtNum = (v: number) => String(Number(v.toFixed(2)))
const withUnit = (v: number, unit: string | null) => `${fmtNum(v)}${unit ?? ''}`
/** 계산으로 나온 값(계획값 · 차이)은 소수 1자리까지만 */
const withUnit1 = (v: number, unit: string | null) => `${String(Number(v.toFixed(1)))}${unit ?? ''}`
const md = (iso: string) => {
  const [, m, d] = iso.split('-').map(Number)
  return `${m}월 ${d}일`
}
const daysBetween = (a: string, b: string) => dayNum(b) - dayNum(a)
const addDaysIso = (iso: string, n: number) => new Date((dayNum(iso) + n) * 86_400_000).toISOString().slice(0, 10)

/** 수치 기록 요청 결과 (지난 기록 저장 = record · 삭제 = delete) */
const trackMetricResult = (goalId: number, action: 'record' | 'delete', err?: unknown) =>
  track('goal_detail.metric_result', {
    goal_category_id: goalId,
    metric_action: action,
    result: err === undefined ? 'success' : 'fail',
    error_status: err === undefined ? undefined : errorStatusOf(err),
  })

const STATUS: Record<ProgressStatus, { label: string; tone: string }> = {
  ACHIEVED: { label: '목표 달성 🎉', tone: 'bg-success-soft text-success' },
  AHEAD: { label: '계획보다 앞서요', tone: 'bg-success-soft text-success' },
  ON_TRACK: { label: '계획대로 가는 중', tone: 'bg-brand-soft text-text-brand' },
  BEHIND: { label: '계획보다 조금 늦어요', tone: 'bg-warning-soft text-warning' },
  NOT_STARTED: { label: '시작 전', tone: 'bg-subtle text-ink-3' },
  NO_PLAN: { label: '종료일 없음', tone: 'bg-subtle text-ink-3' },
}

export function StatusChip({ status }: { status: ProgressStatus }) {
  const s = STATUS[status]
  return <span className={cn('rounded-full px-2.5 py-1 text-[12px] font-semibold', s.tone)}>{s.label}</span>
}

// ─── 시작 → 목표 막대 위의 현재 위치 ───────────────────────────────

export function TrackBar({ m }: { m: MetricProgress }) {
  const span = m.targetValue - m.startValue
  const pos = (v: number) => Math.min(Math.max((v - m.startValue) / span, 0), 1) * 100
  const now = pos(m.currentValue)
  const expected = m.expectedValue != null ? pos(m.expectedValue) : null
  const percent = m.progressPercent ?? 0

  return (
    <div className="mt-1">
      <div className="mb-2 flex items-end justify-between gap-2">
        <p className="text-[13px]">
          <span className="text-ink-3">시작 </span>
          <b>{withUnit(m.startValue, m.unit)}</b>
        </p>
        <p className="text-[28px] leading-none font-bold tracking-tight">
          {Math.round(Math.min(Math.max(percent, 0), 999))}
          <span className="text-ink-3 ml-0.5 text-[15px] font-semibold">%</span>
        </p>
        <p className="text-[13px]">
          <span className="text-ink-3">목표 </span>
          <b className="text-success">{withUnit(m.targetValue, m.unit)}</b>
        </p>
      </div>
      <div className="bg-subtle relative h-3 rounded-full">
        <div className="bg-brand absolute inset-y-0 left-0 rounded-full transition-all" style={{ width: `${now}%` }} />
        {expected != null && (
          <span
            className="bg-ink-2 absolute -top-1 h-5 w-0.5 rounded-full"
            style={{ left: `calc(${expected}% - 1px)` }}
            title={`오늘 계획 ${withUnit1(m.expectedValue!, m.unit)}`}
          />
        )}
        <span
          className="bg-brand border-surface absolute top-1/2 size-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] shadow"
          style={{ left: `${now}%` }}
        />
      </div>
      <div className="text-ink-3 mt-2 flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[12px]">
        <span>
          지금 <b className="text-ink">{withUnit(m.currentValue, m.unit)}</b>
          {m.status !== 'ACHIEVED' && (
            <> · 목표까지 {withUnit(Math.abs(m.targetValue - m.currentValue), m.unit)} 남음</>
          )}
        </span>
        {m.expectedValue != null && (
          <span className="flex items-center gap-1">
            <i className="bg-ink-2 inline-block h-3 w-0.5 rounded-full" />
            오늘 계획 {withUnit1(m.expectedValue, m.unit)}
          </span>
        )}
      </div>
    </div>
  )
}

/** "오늘 계획보다 0.3kg 늦어요 · 이 속도면 10월 16일 도달" */
export function Insight({ m, endDate }: { m: MetricProgress; endDate: string | null }) {
  const lines: string[] = []
  const dir = Math.sign(m.targetValue - m.startValue)
  if (m.status === 'ACHIEVED') {
    lines.push(`목표 ${withUnit(m.targetValue, m.unit)}에 도달했어요. 정말 잘했어요!`)
  } else if (m.expectedValue != null && (m.status === 'AHEAD' || m.status === 'BEHIND' || m.status === 'ON_TRACK')) {
    const gap = (m.currentValue - m.expectedValue) * dir
    const g = withUnit1(Math.abs(gap), m.unit)
    lines.push(
      m.status === 'AHEAD'
        ? `오늘 계획보다 ${g} 앞서 있어요.`
        : m.status === 'BEHIND'
          ? `오늘 계획보다 ${g} 뒤처져 있어요. 조금만 더 힘내요!`
          : `계획한 속도에 맞춰 잘 가고 있어요.`
    )
  }
  if (m.projectedDate && m.status !== 'ACHIEVED') {
    let tail = ''
    if (endDate) {
      const diff = daysBetween(endDate, m.projectedDate)
      tail =
        diff > 0
          ? ` (종료일보다 ${diff}일 늦어요)`
          : diff < 0
            ? ` (종료일보다 ${-diff}일 빨라요)`
            : ' (종료일에 딱 맞아요)'
    }
    lines.push(`최근 기록 추세라면 ${md(m.projectedDate)}쯤 목표에 닿아요${tail}.`)
  }
  if (lines.length === 0) return null
  return (
    <div className="bg-subtle mt-4 rounded-2xl px-4 py-3 text-[13px] leading-relaxed">
      {lines.map((l) => (
        <p key={l}>{l}</p>
      ))}
    </div>
  )
}

// ─── 수치 마감일 + "수치 수정" (목표 전체 수정과 따로, 2026-10) ─────────────

const trackMetricEditOpen = (goalId: number, entry: MetricEditEntry, hadMetric: boolean) =>
  track('goal_detail.metric_edit_open', { goal_category_id: goalId, entry_point: entry, had_metric: hadMetric })

function MetricHead({
  goalId,
  m,
  metricEnd,
  own,
  goalEnd,
  onEdit,
  editing,
}: {
  goalId: number
  m: MetricProgress
  metricEnd: string | null
  /** 수치 마감일을 따로 정했는지 */
  own: boolean
  goalEnd: string | null
  onEdit: () => void
  editing: boolean
}) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px]">
      <span className="text-ink-2 font-semibold">{m.name || '수치'}</span>
      <span className="text-ink-3">
        {metricEnd ? `${md(metricEnd)}까지` : '마감일 없음'}
        {own && goalEnd && metricEnd !== goalEnd ? ` · 목표 종료 ${md(goalEnd)}` : ''}
      </span>
      {!editing && (
        <Button
          size="sm"
          variant="ghost"
          className="ml-auto h-7"
          onClick={() => {
            trackMetricEditOpen(goalId, 'progress_section', true)
            onEdit()
          }}
        >
          수치 수정
        </Button>
      )}
    </div>
  )
}

// ─── 지난 기록 ────────────────────────────────────────────────

function RecentLogs({
  goalId,
  today,
  m,
  notes,
  onEdit,
}: {
  goalId: number
  today: string
  m: MetricProgress
  /** 날짜별 회고 — 수치 옆에 그날 무엇을 했는지 같이 보여줘요 */
  notes: Record<string, GoalReflection>
  onEdit: (date: string, value: number) => void
}) {
  const [showAll, setShowAll] = useState(false)
  const del = useDeleteMetricLog(goalId)
  const editing = useEditing('del:metriclog')
  if (m.logs.length === 0) return null

  const dir = Math.sign(m.targetValue - m.startValue)
  const rows = [...m.logs].reverse()
  const shown = showAll ? rows : rows.slice(0, 5)

  return (
    <div className="mt-5">
      <p className="text-ink-3 mb-2 text-[12px] font-semibold">기록</p>
      <ul className="divide-line divide-y">
        {shown.map((l) => {
          const idx = m.logs.findIndex((x) => x.date === l.date)
          const prev = idx > 0 ? m.logs[idx - 1].value : m.startValue
          const delta = l.value - prev
          const good = delta * dir > 0
          const key = `del:metriclog:${l.date}`
          const daysAgo = daysBetween(l.date, today)
          if (editing.current === key) {
            return (
              <li key={l.date} className="py-2">
                <InlineConfirm
                  message={`${md(l.date)} 기록(${withUnit(l.value, m.unit)})을 지울까요?`}
                  detail="그래프와 현재 값이 바로 바뀌어요."
                  loading={del.isPending}
                  onCancel={() => {
                    track('goal_detail.metric_log_delete_cancel', { goal_category_id: goalId })
                    editing.close()
                  }}
                  onConfirm={() => {
                    track('goal_detail.metric_log_delete_confirm', { goal_category_id: goalId, days_ago: daysAgo })
                    del.mutate(l.date, {
                      onSuccess: () => trackMetricResult(goalId, 'delete'),
                      onError: (e) => trackMetricResult(goalId, 'delete', e),
                      onSettled: editing.close,
                    })
                  }}
                />
              </li>
            )
          }
          return (
            <li key={l.date} className="group flex items-center gap-3 py-2 text-[14px]">
              <span className="text-ink-3 w-[70px] shrink-0 text-[13px]">{md(l.date)}</span>
              <b className="shrink-0">{withUnit(l.value, m.unit)}</b>
              {delta !== 0 && (
                <span
                  className={cn(
                    'flex shrink-0 items-center gap-0.5 text-[12px] font-semibold',
                    good ? 'text-success' : 'text-warning'
                  )}
                >
                  {delta < 0 ? <TrendingDown className="size-3.5" /> : <TrendingUp className="size-3.5" />}
                  {fmtNum(Math.abs(delta))}
                </span>
              )}
              <span className="text-ink-3 min-w-0 flex-1 truncate text-[13px]" title={notes[l.date]?.note ?? undefined}>
                {moodOf(notes[l.date]?.mood)?.emoji}
                {notes[l.date]?.mood ? ' ' : ''}
                {notes[l.date]?.note ?? l.memo}
              </span>
              <RowActions
                label={`${md(l.date)} 기록`}
                onEdit={() => {
                  track('goal_detail.metric_log_edit_click', { goal_category_id: goalId, days_ago: daysAgo })
                  onEdit(l.date, l.value)
                }}
                onDelete={() => {
                  track('goal_detail.metric_log_delete_click', { goal_category_id: goalId, days_ago: daysAgo })
                  editing.openOther(key)
                }}
              />
            </li>
          )
        })}
      </ul>
      {rows.length > 5 && (
        <button
          type="button"
          onClick={() => {
            track('goal_detail.metric_logs_more_toggle', {
              goal_category_id: goalId,
              expanded: !showAll,
              log_count: rows.length,
            })
            setShowAll(!showAll)
          }}
          className="text-ink-3 hover:text-ink mt-1 text-[13px] font-medium"
        >
          {showAll ? '접기' : `기록 ${rows.length - 5}개 더 보기`}
        </button>
      )}
    </div>
  )
}

// ─── 수치 목표 ────────────────────────────────────────────────

function MetricView({ goal, p, m }: { goal: GoalCategory; p: GoalProgress; m: MetricProgress }) {
  const goalId = goal.goalCategoryId
  const [editingMetric, setEditingMetric] = useState(false)
  const metricEnd = m.targetDate ?? p.endDate
  // 지난 기록 "수정" → 입력창에 불러오기 (CheckIn 을 새 key 로 다시 띄워 초기값을 넣어요)
  const [loaded, setLoaded] = useState<{ date: string; value: number; n: number } | null>(null)

  const from = p.startDate ?? m.logs[0]?.date ?? p.today
  const lastLog = m.logs[m.logs.length - 1]?.date
  const candidates = [p.endDate, lastLog, p.today, m.planLine[m.planLine.length - 1]?.date].filter(Boolean) as string[]
  let to = candidates.reduce((a, b) => (a > b ? a : b))
  if (!p.endDate) to = addDaysIso(to, 7)
  if (to <= from) to = addDaysIso(from, 7)

  const actual = [...m.logs.map((l) => ({ date: l.date, value: l.value }))]
  if (p.startDate && !m.logs.some((l) => l.date <= p.startDate!)) {
    actual.unshift({ date: p.startDate, value: m.startValue })
  }

  const series: ChartSeries[] = [
    { key: 'plan', name: '계획', kind: 'plan', points: m.planLine },
    { key: 'actual', name: '기록', kind: 'actual', points: actual },
  ]
  const currentDate = m.lastLoggedDate ?? (p.startDate && p.startDate < p.today ? p.startDate : p.today)

  return (
    <>
      <MetricHead
        goalId={goalId}
        m={m}
        metricEnd={metricEnd}
        own={!!goal.metricTargetDate}
        goalEnd={p.endDate}
        onEdit={() => setEditingMetric(true)}
        editing={editingMetric}
      />
      {editingMetric && <MetricEditForm goal={goal} entry="progress_section" onDone={() => setEditingMetric(false)} />}
      <TrackBar m={m} />
      <Insight m={m} endDate={metricEnd} />

      {/* 오늘 기록은 화면 위 "오늘 기록" 카드에서 해요. 여기서는 지난 기록을 고칠 때만 입력창을 열어요 */}
      {loaded && (
        <CheckInPrefilled
          key={loaded.n}
          goalId={goalId}
          m={m}
          today={p.today}
          date={loaded.date}
          value={loaded.value}
          onDone={() => setLoaded(null)}
        />
      )}

      <ProgressChart
        className="mt-5"
        series={series}
        from={from}
        to={to}
        today={p.today}
        target={{ value: m.targetValue, label: `목표 ${withUnit(m.targetValue, m.unit)}` }}
        current={{ date: currentDate, value: m.currentValue, label: `지금 ${withUnit(m.currentValue, m.unit)}` }}
        format={(v) => withUnit(v, m.unit)}
        onFirstInteract={() =>
          track('goal_detail.chart_interact', { goal_category_id: goalId, chart_kind: 'main_metric' })
        }
      />
      <Legend
        items={[
          { name: '계획', kind: 'plan' },
          { name: '내 기록', kind: 'actual' },
        ]}
      />

      <RecentLogs
        goalId={goalId}
        today={p.today}
        m={m}
        notes={Object.fromEntries((p.reflections ?? []).map((r) => [r.date, r]))}
        onEdit={(date, value) => setLoaded({ date, value, n: Date.now() })}
      />
      <TaskLine t={p.tasks} />
    </>
  )
}

/** 지난 기록을 고칠 때 쓰는 입력창 (날짜·값을 미리 채워서) */
function CheckInPrefilled({
  goalId,
  m,
  today,
  date: initialDate,
  value: initialValue,
  onDone,
}: {
  goalId: number
  m: MetricProgress
  today: string
  date: string
  value: number
  onDone: () => void
}) {
  const record = useRecordMetric(goalId)
  const [date, setDate] = useState(initialDate)
  const [value, setValue] = useState(fmtNum(initialValue))
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const v = Number(value)
    if (value.trim() === '' || !Number.isFinite(v) || record.isPending) return
    track('goal_detail.metric_log_edit_save', {
      goal_category_id: goalId,
      days_ago: daysBetween(initialDate, today),
      date_changed: date !== initialDate,
    })
    record.mutate(
      { date, value: v },
      {
        onSuccess: () => {
          trackMetricResult(goalId, 'record')
          onDone()
        },
        onError: (err) => trackMetricResult(goalId, 'record', err),
      }
    )
  }
  const cancel = (method: 'esc' | 'cancel_button') => {
    track('goal_detail.metric_log_edit_cancel', { goal_category_id: goalId, close_method: method })
    onDone()
  }
  return (
    <form
      onSubmit={submit}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.preventDefault()
          cancel('esc')
        }
      }}
      className="border-border-subtle bg-brand-soft/50 mt-4 rounded-2xl border px-4 py-3"
    >
      <p className="text-[14px] font-semibold">{md(date)} 기록 고치기</p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input
          type="date"
          value={date}
          max={today}
          onChange={(e) => setDate(e.target.value || initialDate)}
          aria-label="기록 날짜"
          className={cn(inlineInput, 'w-[150px] shrink-0')}
        />
        <div className="relative min-w-[120px] flex-1">
          <input
            autoFocus
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value.replace(/[^0-9.-]/g, ''))}
            aria-label={`${m.name || '수치'} 값`}
            className={cn(inlineInput, 'pr-12 text-[15px] font-semibold')}
          />
          {m.unit && (
            <span className="text-ink-3 pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-[13px]">
              {m.unit}
            </span>
          )}
        </div>
        <Button type="submit" size="sm" loading={record.isPending} disabled={value.trim() === ''}>
          저장
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={() => cancel('cancel_button')}>
          취소
        </Button>
      </div>
    </form>
  )
}

export function Legend({ items }: { items: { name: string; kind: 'plan' | 'actual' }[] }) {
  return (
    <div className="text-ink-3 mt-2 flex flex-wrap items-center gap-4 text-[12px]">
      {items.map((i) => (
        <span key={i.name} className="flex items-center gap-1.5">
          <svg width="18" height="4" aria-hidden>
            <line
              x1="0"
              x2="18"
              y1="2"
              y2="2"
              className={i.kind === 'plan' ? 'stroke-ink-4' : 'stroke-brand'}
              strokeWidth="2.5"
              strokeDasharray={i.kind === 'plan' ? '4 3' : undefined}
            />
          </svg>
          {i.name}
        </span>
      ))}
      <span className="flex items-center gap-1.5">
        <i className="bg-brand inline-block size-2.5 rounded-full" />
        지금 위치
      </span>
    </div>
  )
}

/** 수치 목표 아래 한 줄: Task 실행 현황 */
function TaskLine({ t }: { t: TaskProgress }) {
  if (t.total === 0) return null
  return (
    <p className="text-ink-3 border-line mt-5 border-t pt-4 text-[13px]">
      관련 Task <b className="text-ink">{t.completed}</b> / {t.total}개 완료
      {t.dueByToday > 0 && t.completionPercent != null && (
        <>
          {' '}
          · 오늘까지 할 일 중 <b className="text-ink">{Math.round(t.completionPercent)}%</b> 해냈어요
        </>
      )}
    </p>
  )
}

// ─── 수치 없는 목표: Task 누적 ───────────────────────────────────

function TaskView({ goal, p }: { goal: GoalCategory; p: GoalProgress }) {
  const goalId = goal.goalCategoryId
  const t = p.tasks
  const [metricForm, setMetricForm] = useState(false)
  const behind = Math.max(t.dueByToday - t.completed, 0)
  const ratio = t.dueByToday === 0 ? 0 : Math.min(t.completed / t.dueByToday, 1)

  const series: ChartSeries[] = [
    {
      key: 'plan',
      name: '해야 할 Task',
      kind: 'plan',
      points: t.series.map((s) => ({ date: s.date, value: s.planned })),
    },
    {
      key: 'actual',
      name: '완료한 Task',
      kind: 'actual',
      points: t.series.filter((s) => s.completed != null).map((s) => ({ date: s.date, value: s.completed! })),
    },
  ]
  const from = t.series[0]?.date
  const to = t.series[t.series.length - 1]?.date

  return (
    <>
      {t.dueByToday === 0 ? (
        <p className="text-[14px]">
          {t.total === 0 ? '아직 이 목표에 Task가 없어요.' : '오늘까지 해야 할 Task는 아직 없어요.'}
        </p>
      ) : (
        <>
          <div className="flex items-end justify-between gap-2">
            <p className="text-[14px]">
              오늘까지 할 Task <b>{t.dueByToday}개</b> 중 <b className="text-text-brand">{t.completed}개</b> 완료
            </p>
            <p className="text-[28px] leading-none font-bold tracking-tight">
              {Math.round(ratio * 100)}
              <span className="text-ink-3 ml-0.5 text-[15px] font-semibold">%</span>
            </p>
          </div>
          <div className="bg-subtle mt-2 h-3 overflow-hidden rounded-full">
            <div className="bg-brand h-full rounded-full transition-all" style={{ width: `${ratio * 100}%` }} />
          </div>
          <p className="text-ink-3 mt-2 text-[12px]">
            {behind === 0
              ? '밀린 Task 없이 계획대로 가고 있어요.'
              : `밀린 Task ${behind}개 — 하나씩 끝내면 그래프가 따라잡아요.`}
          </p>
        </>
      )}

      {from && to && t.total > 0 && (
        <>
          <ProgressChart
            className="mt-5"
            series={series}
            from={from}
            to={to}
            today={p.today}
            zeroBased
            current={
              t.series.some((s) => s.date === p.today)
                ? {
                    date: p.today,
                    value: t.series.find((s) => s.date === p.today)!.completed ?? 0,
                    label: `완료 ${t.completed}개`,
                  }
                : undefined
            }
            format={(v) => `${fmtNum(v)}개`}
            height={200}
            onFirstInteract={() =>
              track('goal_detail.chart_interact', { goal_category_id: goalId, chart_kind: 'task_cumulative' })
            }
          />
          <Legend
            items={[
              { name: '해야 할 Task (누적)', kind: 'plan' },
              { name: '완료한 Task (누적)', kind: 'actual' },
            ]}
          />
        </>
      )}

      <div className="bg-subtle mt-5 flex flex-wrap items-center gap-3 rounded-2xl px-4 py-3">
        <p className="text-ink-2 min-w-0 flex-1 text-[13px]">
          몸무게·점수·권수처럼 숫자로 잴 수 있는 목표라면, 목표 수치를 정하고 매일 기록해 보세요. 목표까지 남은 거리를
          그래프로 보여드려요.
        </p>
        {!metricForm && (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              // 목표 전체 수정 대신 수치만 정하는 입력창 (2026-10)
              trackMetricEditOpen(goalId, 'progress_cta', false)
              setMetricForm(true)
            }}
          >
            목표 수치 정하기
          </Button>
        )}
      </div>
      {metricForm && <MetricEditForm goal={goal} entry="progress_cta" onDone={() => setMetricForm(false)} />}
    </>
  )
}

// ─── 섹션 ─────────────────────────────────────────────────────

export function ProgressSection({ goal }: { goal: GoalCategory }) {
  const { data: p, isPending, isError } = useGoalProgress(goal.goalCategoryId)

  if (isPending) {
    return (
      <Section title="지금 어디쯤?">
        <div className="grid h-40 place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      </Section>
    )
  }
  if (isError || !p) return null

  const m = p.metric
  return (
    <Section title="지금 어디쯤?" aside={m ? <StatusChip status={m.status} /> : undefined}>
      {m ? <MetricView goal={goal} p={p} m={m} /> : <TaskView goal={goal} p={p} />}
    </Section>
  )
}
