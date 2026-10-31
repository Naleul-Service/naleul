'use client'

import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CheckboxMark } from '@/components/ui/Checkbox'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import type { GoalCategory } from '../../api'
import { useUpdateGoalMetric, type GoalUpdateInput } from '../../edit/mutations'
import { Field, inlineInput } from '../../edit/inline'

const numStr = (v: number | null | undefined) => (v == null ? '' : String(Number(v.toFixed(2))))
const toNum = (s: string) => {
  if (s.trim() === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}
const md = (iso: string) => `${Number(iso.slice(5, 7))}월 ${Number(iso.slice(8, 10))}일`

export type MetricEditEntry = 'progress_section' | 'progress_cta' | 'legacy_setup'

/**
 * 수치 목표만 고치기 (2026-10) — 목표 전체 수정(헤더)과 따로.
 * 수치 마감일은 목표 종료일과 독립: 비워 두면 목표 종료일을 따라요.
 */
export function MetricEditForm({
  goal,
  entry,
  onDone,
}: {
  goal: GoalCategory
  entry: MetricEditEntry
  onDone: () => void
}) {
  const gid = goal.goalCategoryId
  const update = useUpdateGoalMetric(gid)
  const hadMetric = goal.targetValue != null || !!goal.metricName
  const [f, setF] = useState({
    name: goal.metricName ?? '',
    unit: goal.metricUnit ?? '',
    start: numStr(goal.startValue),
    current: numStr(goal.currentValue ?? goal.startValue),
    target: numStr(goal.targetValue),
    // 따로 정한 마감일이 있으면 그 날짜, 없으면 "목표 종료일 따르기"
    ownDate: !!goal.metricTargetDate,
    date: goal.metricTargetDate ?? goal.goalCategoryEndDate ?? '',
  })
  const set = (k: keyof typeof f) => (v: string | boolean) => setF((p) => ({ ...p, [k]: v }))
  const num = (s: string) => s.replace(/[^0-9.-]/g, '')

  const start = toNum(f.start)
  const target = toNum(f.target)
  const current = toNum(f.current)
  const startDate = goal.goalCategoryStartDate
  const error = !f.name.trim()
    ? '무엇을 잴지 적어 주세요 (예: 몸무게)'
    : start == null || target == null
      ? '시작 값과 목표 값을 적어 주세요'
      : start === target
        ? '시작 값과 목표 값이 같아요'
        : f.ownDate && !f.date
          ? '수치 마감일을 골라 주세요'
          : f.ownDate && startDate && f.date < startDate
            ? '수치 마감일은 목표 시작일 이후여야 해요'
            : null

  /** 처음 값과 달라진 칸 (분석용 changed_fields) */
  const changedFields = () => {
    const out: string[] = []
    if (f.name.trim() !== (goal.metricName ?? '')) out.push('name')
    if (f.unit.trim() !== (goal.metricUnit ?? '')) out.push('unit')
    if (start !== (goal.startValue ?? null)) out.push('start_value')
    if (current !== (goal.currentValue ?? goal.startValue ?? null)) out.push('current_value')
    if (target !== (goal.targetValue ?? null)) out.push('target_value')
    if ((f.ownDate ? f.date : null) !== (goal.metricTargetDate ?? null)) out.push('target_date')
    return out
  }

  const cancel = (method: 'esc' | 'cancel_button') => {
    track('goal_detail.metric_edit_cancel', {
      goal_category_id: gid,
      close_method: method,
      entry_point: entry,
      is_dirty: changedFields().length > 0,
    })
    onDone()
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (error || update.isPending) {
      if (error)
        track('goal_detail.metric_edit_error', {
          goal_category_id: gid,
          validation_error: error,
        })
      return
    }
    const body: GoalUpdateInput = {
      metricName: f.name.trim(),
      metricUnit: f.unit.trim(),
      startValue: start,
      targetValue: target,
      currentValue: current ?? start,
    }
    if (f.ownDate) body.metricTargetDate = f.date
    else if (goal.metricTargetDate) body.clearMetricTargetDate = true
    track('goal_detail.metric_edit_save', {
      goal_category_id: gid,
      entry_point: entry,
      had_metric: hadMetric,
      metric_action: hadMetric ? 'update' : 'add',
      changed_fields: changedFields().join(','),
      own_target_date: f.ownDate,
    })
    update.mutate(body, {
      onSuccess: () => {
        track('goal_detail.metric_edit_result', {
          goal_category_id: gid,
          result: 'success',
        })
        onDone()
      },
      onError: () =>
        track('goal_detail.metric_edit_result', {
          goal_category_id: gid,
          result: 'fail',
        }),
    })
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
      className="border-border-subtle bg-brand-soft/50 mt-4 rounded-2xl border px-4 py-4"
    >
      <p className="text-[14px] font-semibold">{hadMetric ? '수치 수정' : '목표 수치 정하기'}</p>
      <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-[2fr_1fr]">
        <Field label="무엇을 잴까요?">
          <input
            autoFocus
            value={f.name}
            maxLength={30}
            onChange={(e) => set('name')(e.target.value)}
            placeholder="예) 몸무게 · 토익 점수"
            className={inlineInput}
          />
        </Field>
        <Field label="단위">
          <input
            value={f.unit}
            maxLength={10}
            onChange={(e) => set('unit')(e.target.value)}
            placeholder="kg · 점"
            className={inlineInput}
          />
        </Field>
      </div>
      <div className="mt-2 grid grid-cols-3 gap-2">
        <Field label="시작 값">
          <input
            inputMode="decimal"
            value={f.start}
            onChange={(e) => set('start')(num(e.target.value))}
            className={inlineInput}
          />
        </Field>
        <Field label="현재 값">
          <input
            inputMode="decimal"
            value={f.current}
            onChange={(e) => set('current')(num(e.target.value))}
            className={inlineInput}
          />
        </Field>
        <Field label="목표 값">
          <input
            inputMode="decimal"
            value={f.target}
            onChange={(e) => set('target')(num(e.target.value))}
            className={inlineInput}
          />
        </Field>
      </div>

      <div className="mt-3">
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">수치 마감일</span>
        <button
          type="button"
          role="checkbox"
          aria-checked={!f.ownDate}
          onClick={() => set('ownDate')(!f.ownDate)}
          className="flex w-fit items-center gap-2 text-[13px]"
        >
          <CheckboxMark checked={!f.ownDate} />
          목표 종료일 따르기
          {goal.goalCategoryEndDate && <span className="text-ink-3">({md(goal.goalCategoryEndDate)})</span>}
        </button>
        {f.ownDate && (
          <input
            type="date"
            value={f.date}
            min={startDate ?? undefined}
            onChange={(e) => set('date')(e.target.value)}
            aria-label="수치 마감일"
            className={cn(inlineInput, 'mt-2 w-[170px]')}
          />
        )}
        <p className="text-ink-3 mt-1.5 text-[12px]">
          수치 마감일은 목표 종료일과 따로 움직여요. 목표 기간을 바꿔도 수치 마감일은 그대로예요.
        </p>
      </div>

      {error && <p className="text-danger mt-2 text-[12px]">{error}</p>}
      <div className="mt-3 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={() => cancel('cancel_button')}>
          취소
        </Button>
        <Button type="submit" size="sm" loading={update.isPending} disabled={!!error}>
          저장
        </Button>
      </div>
    </form>
  )
}
