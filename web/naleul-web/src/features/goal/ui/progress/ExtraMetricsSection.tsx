'use client'

import { useState, type FormEvent } from 'react'
import { LineChart } from 'lucide-react'
import { Pencil, Trash2, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import {
  useCreateExtraMetric,
  useDeleteExtraMetric,
  useDeleteExtraMetricLog,
  useGoalProgress,
  useRecordExtraMetric,
  useUpdateExtraMetric,
  type ExtraMetric,
  type GoalCategory,
  type GoalProgress,
} from '../../api'
import {
  AddButton,
  Field,
  InlineConfirm,
  InlineForm,
  inlineInput,
  toNum,
  type InlineCloseMethod,
  type InlineSubmitMethod,
} from '../../edit/inline'
import { Section } from '../sections'
import { ProgressChart, type ChartSeries } from './ProgressChart'
import { Insight, Legend, StatusChip, TrackBar, fmtNum } from './ProgressSection'

const MAX = 5
const withUnit = (v: number, unit: string | null) => `${fmtNum(v)}${unit ?? ''}`
const md = (iso: string) => `${Number(iso.slice(5, 7))}월 ${Number(iso.slice(8, 10))}일`
/** 오늘부터 며칠 전인지 (YYYY-MM-DD) */
const daysBetween = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000)

type ExtraMetricAction = 'create' | 'update' | 'delete' | 'record' | 'log_delete'
const trackExtraResult = (goalId: number, action: ExtraMetricAction, err?: unknown) =>
  track('goal_detail.extra_metric_result', {
    goal_category_id: goalId,
    metric_action: action,
    result: err === undefined ? 'success' : 'fail',
    error_status: err === undefined ? undefined : errorStatusOf(err),
  })
/** mutate 두 번째 인자 — 결과 이벤트 + 성공했을 때 할 일 */
const resultCallbacks = (goalId: number, action: ExtraMetricAction, onSuccess?: () => void) => ({
  onSuccess: () => {
    trackExtraResult(goalId, action)
    onSuccess?.()
  },
  onError: (e: unknown) => trackExtraResult(goalId, action, e),
})

// ─── 그래프 만들기 · 고치기 ─────────────────────────────────────

function MetricForm({
  initial,
  saving,
  onSubmit,
  onCancel,
  submitLabel,
}: {
  initial?: { name: string; unit: string; startValue: number; targetValue: number }
  saving: boolean
  onSubmit: (
    v: { name: string; unit: string; startValue: number; targetValue: number },
    method: InlineSubmitMethod
  ) => void
  onCancel: (method: InlineCloseMethod) => void
  submitLabel: string
}) {
  const [f, setF] = useState({
    name: initial?.name ?? '',
    unit: initial?.unit ?? '',
    start: initial ? fmtNum(initial.startValue) : '',
    target: initial ? fmtNum(initial.targetValue) : '',
  })
  const set = <K extends keyof typeof f>(k: K, v: string) => setF((x) => ({ ...x, [k]: v }))
  const start = toNum(f.start)
  const target = toNum(f.target)
  const error =
    start === null || target === null
      ? null
      : Number.isNaN(start) || Number.isNaN(target)
        ? '숫자로 입력해 주세요.'
        : start === target
          ? '시작값과 목표값을 다르게 정해 주세요.'
          : null
  const valid = !!f.name.trim() && start !== null && target !== null && !error

  return (
    <InlineForm
      onSubmit={(method) =>
        onSubmit({ name: f.name.trim(), unit: f.unit.trim(), startValue: start!, targetValue: target! }, method)
      }
      onCancel={onCancel}
      saving={saving}
      valid={valid}
      error={error}
      submitLabel={submitLabel}
    >
      <div className="grid grid-cols-[minmax(0,1fr)_88px] gap-2">
        <Field label="무엇을 잴까요?">
          <input
            value={f.name}
            onChange={(e) => set('name', e.target.value)}
            maxLength={30}
            placeholder="예: 기출 회독"
            className={inlineInput}
            data-autofocus
          />
        </Field>
        <Field label="단위">
          <input
            value={f.unit}
            onChange={(e) => set('unit', e.target.value)}
            maxLength={10}
            placeholder="회"
            className={inlineInput}
          />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Field label="지금">
          <input
            inputMode="decimal"
            value={f.start}
            onChange={(e) => set('start', e.target.value)}
            placeholder="0"
            className={inlineInput}
          />
        </Field>
        <Field label="목표">
          <input
            inputMode="decimal"
            value={f.target}
            onChange={(e) => set('target', e.target.value)}
            placeholder="5"
            className={inlineInput}
          />
        </Field>
      </div>
      <p className="text-ink-3 text-[12px]">목표 기간 동안 시작값에서 목표값까지 곧게 가는 계획선이 그려져요.</p>
    </InlineForm>
  )
}

// ─── 그래프 하나 ──────────────────────────────────────────────

function ExtraMetricCard({
  goalId,
  p,
  metric,
  readOnly,
}: {
  goalId: number
  p: GoalProgress
  metric: ExtraMetric
  readOnly: boolean
}) {
  const m = metric.progress
  const record = useRecordExtraMetric(goalId)
  const update = useUpdateExtraMetric(goalId)
  const remove = useDeleteExtraMetric(goalId)
  const removeLog = useDeleteExtraMetricLog(goalId)
  const [value, setValue] = useState('')
  const [mode, setMode] = useState<'view' | 'edit' | 'delete'>('view')
  const [showLogs, setShowLogs] = useState(false)
  const ids = { goal_category_id: goalId, metric_id: metric.metricId }

  if (mode === 'edit') {
    return (
      <MetricForm
        initial={{ name: m.name ?? '', unit: m.unit ?? '', startValue: m.startValue, targetValue: m.targetValue }}
        saving={update.isPending}
        submitLabel="저장"
        onCancel={(method) => {
          track('goal_detail.extra_metric_cancel', { goal_category_id: goalId, is_new: false, close_method: method })
          setMode('view')
        }}
        onSubmit={(v, method) => {
          track('goal_detail.extra_metric_save', { goal_category_id: goalId, is_new: false, submit_method: method })
          update.mutate(
            { metricId: metric.metricId, ...v },
            resultCallbacks(goalId, 'update', () => setMode('view'))
          )
        }}
      />
    )
  }
  if (mode === 'delete') {
    return (
      <InlineConfirm
        message={`'${m.name}' 그래프를 지울까요?`}
        detail="이 그래프에 남긴 기록도 함께 사라져요."
        loading={remove.isPending}
        onCancel={() => {
          track('goal_detail.extra_metric_delete_cancel', ids)
          setMode('view')
        }}
        onConfirm={() => {
          track('goal_detail.extra_metric_delete_confirm', { ...ids, log_count: m.logs.length })
          remove.mutate(metric.metricId, resultCallbacks(goalId, 'delete'))
        }}
      />
    )
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const v = Number(value)
    if (value.trim() === '' || !Number.isFinite(v) || record.isPending) return
    track('goal_detail.extra_metric_record_submit', { ...ids, is_overwrite: !!m.loggedToday })
    record.mutate(
      { metricId: metric.metricId, value: v },
      resultCallbacks(goalId, 'record', () => setValue(''))
    )
  }

  const from = p.startDate ?? m.logs[0]?.date ?? p.today
  const to = p.endDate ?? m.planLine[m.planLine.length - 1]?.date ?? p.today
  const actual = m.logs.map((l) => ({ date: l.date, value: l.value }))
  if (p.startDate && !m.logs.some((l) => l.date <= p.startDate!))
    actual.unshift({ date: p.startDate, value: m.startValue })
  const series: ChartSeries[] = [
    { key: 'plan', name: '계획', kind: 'plan', points: m.planLine },
    { key: 'actual', name: '기록', kind: 'actual', points: actual },
  ]
  const logs = [...m.logs].reverse()

  return (
    <div className="group/metric">
      <div className="mb-2 flex items-center gap-2">
        <h3 className="min-w-0 flex-1 truncate text-[15px] font-bold">{m.name}</h3>
        <StatusChip status={m.status} />
        {!readOnly && (
          <span className="flex shrink-0 items-center transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-focus-within/metric:opacity-100 [@media(hover:hover)]:group-hover/metric:opacity-100">
            <button
              type="button"
              onClick={() => {
                track('goal_detail.extra_metric_edit_click', ids)
                setMode('edit')
              }}
              aria-label={`${m.name} 그래프 수정`}
              className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1.5"
            >
              <Pencil className="size-3.5" />
            </button>
            <button
              type="button"
              onClick={() => {
                track('goal_detail.extra_metric_delete_click', { ...ids, log_count: m.logs.length })
                setMode('delete')
              }}
              aria-label={`${m.name} 그래프 삭제`}
              className="text-ink-3 hover:bg-danger-soft hover:text-danger rounded-lg p-1.5"
            >
              <Trash2 className="size-3.5" />
            </button>
          </span>
        )}
      </div>

      <TrackBar m={m} />
      {/* "오늘은 여기까지" — 계획선 위 오늘 값 */}
      {m.expectedValue != null && m.status !== 'ACHIEVED' && (
        <p className="bg-brand-soft text-text-brand mt-3 rounded-xl px-3 py-2 text-[13px] font-semibold">
          계획대로라면 오늘까지 {withUnit(Math.round(m.expectedValue * 10) / 10, m.unit)}
          {m.currentValue !== m.expectedValue && (
            <span className="text-ink-2 font-medium"> · 지금 {withUnit(m.currentValue, m.unit)}</span>
          )}
        </p>
      )}
      {/* 오늘 기대값이 없을 때(종료일 없음 · 달성)만 설명 문장을 보여줘요 — 위 문장과 겹치지 않게 */}
      {(m.expectedValue == null || m.status === 'ACHIEVED') && <Insight m={m} endDate={p.endDate} />}

      {!readOnly && (
        <form onSubmit={submit} className="mt-3 flex items-center gap-2">
          <div className="relative min-w-0 flex-1">
            <input
              inputMode="decimal"
              value={value}
              onChange={(e) => setValue(e.target.value.replace(/[^0-9.-]/g, ''))}
              placeholder={m.loggedToday ? '오늘 기록을 고쳐요' : `오늘 ${m.name ?? '값'}`}
              aria-label={`오늘 ${m.name ?? '값'} 기록`}
              className={cn(inlineInput, 'pr-12')}
            />
            {m.unit && (
              <span className="text-ink-3 pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-[13px]">
                {m.unit}
              </span>
            )}
          </div>
          <Button type="submit" size="sm" loading={record.isPending} disabled={value.trim() === ''}>
            기록
          </Button>
        </form>
      )}

      {(m.planLine.length > 1 || m.logs.length > 0) && (
        <>
          <ProgressChart
            className="mt-4"
            series={series}
            from={from}
            to={to > from ? to : from}
            today={p.today}
            height={180}
            target={{ value: m.targetValue, label: `목표 ${withUnit(m.targetValue, m.unit)}` }}
            current={{
              date: m.lastLoggedDate ?? (p.startDate && p.startDate < p.today ? p.startDate : p.today),
              value: m.currentValue,
              label: `지금 ${withUnit(m.currentValue, m.unit)}`,
            }}
            format={(v) => withUnit(v, m.unit)}
            onFirstInteract={() =>
              track('goal_detail.chart_interact', { goal_category_id: goalId, chart_kind: 'extra_metric' })
            }
          />
          <Legend
            items={[
              { name: '계획', kind: 'plan' },
              { name: '내 기록', kind: 'actual' },
            ]}
          />
        </>
      )}

      {logs.length > 0 && (
        <div className="mt-3">
          <button
            type="button"
            onClick={() => {
              track('goal_detail.extra_metric_logs_toggle', { ...ids, expanded: !showLogs })
              setShowLogs(!showLogs)
            }}
            aria-expanded={showLogs}
            className="text-ink-3 hover:text-ink text-[12px] font-semibold"
          >
            {showLogs ? '기록 접기' : `기록 ${logs.length}개 보기`}
          </button>
          {showLogs && (
            <ul className="divide-line mt-1 divide-y">
              {logs.map((l) => (
                <li key={l.date} className="group flex items-center gap-3 py-1.5 text-[13px]">
                  <span className="text-ink-3 w-[70px] shrink-0">{md(l.date)}</span>
                  <b className="flex-1">{withUnit(l.value, m.unit)}</b>
                  {!readOnly && (
                    <button
                      type="button"
                      onClick={() => {
                        track('goal_detail.extra_metric_log_delete_click', {
                          ...ids,
                          days_ago: daysBetween(l.date, p.today),
                        })
                        removeLog.mutate(
                          { metricId: metric.metricId, date: l.date },
                          resultCallbacks(goalId, 'log_delete')
                        )
                      }}
                      aria-label={`${md(l.date)} 기록 지우기`}
                      className="text-ink-4 hover:text-danger p-0.5 [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100"
                    >
                      <X className="size-3.5" />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

// ─── 섹션 ─────────────────────────────────────────────────────

/**
 * 목표형 추가 그래프 (V52) — 메인 수치("지금 어디쯤?") 말고 같이 보고 싶은 수치를 더 그려요.
 * 예) SQLD: 메인 "모의고사 점수 40 → 70점" + 추가 "기출 회독 0 → 5회"
 * 그래프마다 오늘 기대값("계획대로라면 오늘까지 2회")을 보여줘서 오늘 어디까지 해야 하는지 바로 알 수 있어요.
 */
export function ExtraMetricsSection({ goal, readOnly = false }: { goal: GoalCategory; readOnly?: boolean }) {
  const { data: p } = useGoalProgress(goal.goalCategoryId)
  const create = useCreateExtraMetric(goal.goalCategoryId)
  const [adding, setAdding] = useState(false)
  if (!p) return null
  const metrics = p.extraMetrics ?? []
  if (readOnly && metrics.length === 0) return null

  return (
    <Section title="함께 보는 그래프" aside={metrics.length ? `${metrics.length} / ${MAX}` : undefined}>
      {metrics.length === 0 && !adding && (
        <p className="text-ink-3 -mt-1 mb-3 flex items-start gap-2 text-[13px] leading-relaxed">
          <LineChart className="mt-0.5 size-4 shrink-0" />
          메인 수치 말고도 매일 보고 싶은 숫자가 있다면 그래프를 더 만들어 보세요. 예) 기출 회독 0 → 5회, 공부 시간 0 →
          60시간
        </p>
      )}
      <div className="divide-line space-y-5 divide-y [&>*:not(:first-child)]:pt-5">
        {metrics.map((metric) => (
          <ExtraMetricCard
            key={metric.metricId}
            goalId={goal.goalCategoryId}
            p={p}
            metric={metric}
            readOnly={readOnly}
          />
        ))}
      </div>
      {!readOnly && (
        <div className={cn(metrics.length > 0 && 'mt-5')}>
          {adding ? (
            <MetricForm
              saving={create.isPending}
              submitLabel="그래프 추가"
              onCancel={(method) => {
                track('goal_detail.extra_metric_cancel', {
                  goal_category_id: goal.goalCategoryId,
                  is_new: true,
                  close_method: method,
                })
                setAdding(false)
              }}
              onSubmit={(v, method) => {
                track('goal_detail.extra_metric_save', {
                  goal_category_id: goal.goalCategoryId,
                  is_new: true,
                  submit_method: method,
                })
                create.mutate(
                  v,
                  resultCallbacks(goal.goalCategoryId, 'create', () => setAdding(false))
                )
              }}
            />
          ) : (
            metrics.length < MAX && (
              <AddButton
                onClick={() => {
                  track('goal_detail.extra_metric_add_click', {
                    goal_category_id: goal.goalCategoryId,
                    metric_count: metrics.length,
                  })
                  setAdding(true)
                }}
              >
                그래프 추가
              </AddButton>
            )
          )}
        </div>
      )}
    </Section>
  )
}
