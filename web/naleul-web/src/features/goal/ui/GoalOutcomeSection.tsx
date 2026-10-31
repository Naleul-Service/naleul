'use client'

import { useEffect, useImperativeHandle, useRef, useState, type Ref } from 'react'
import { CalendarMinus, CalendarPlus, Flag } from 'lucide-react'
import { X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { track } from '@/lib/client/analytics'
import { cn } from '@/lib/cn'
import { errorStatusOf } from '@/features/home/analytics'
import {
  useApplyAdjustment,
  useCompleteGoal,
  useDismissAdjustment,
  useGoalProgress,
  useUpdateOutcome,
  type GoalAdjustment,
  type GoalCategory,
  type GoalOutcome,
  type OutcomeResult,
} from '../api'
import { inlineInput } from '../edit/inline'
import { JAVA_DAY_LABEL } from '../format'
import { Section } from './sections'

export const OUTCOME_OPTIONS: { value: OutcomeResult; emoji: string; label: string; hint: string }[] = [
  { value: 'ACHIEVED', emoji: '🎉', label: '달성했어요', hint: '목표한 만큼 해냈어요' },
  { value: 'PARTIAL', emoji: '🙂', label: '일부 달성', hint: '절반 이상은 해냈어요' },
  { value: 'NOT_ACHIEVED', emoji: '😣', label: '못 했어요', hint: '이번엔 어려웠어요' },
]
export const outcomeOf = (r: OutcomeResult) => OUTCOME_OPTIONS.find((o) => o.value === r)!

const ACHIEVEMENT_MAX = 500

function todayYmd() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function OutcomePicker({
  value,
  onChange,
  disabled,
}: {
  value: OutcomeResult | null
  onChange: (v: OutcomeResult) => void
  disabled?: boolean
}) {
  return (
    <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="결과">
      {OUTCOME_OPTIONS.map((o) => {
        const selected = value === o.value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            onClick={() => onChange(o.value)}
            className={cn(
              'rounded-2xl border px-2 py-3 text-center transition-colors disabled:opacity-60',
              selected ? 'border-brand bg-brand-soft' : 'border-border-subtle hover:border-line-strong'
            )}
          >
            <span className="block text-xl">{o.emoji}</span>
            <span className={cn('mt-1 block text-[13px] font-bold', selected && 'text-text-brand')}>{o.label}</span>
          </button>
        )
      })}
    </div>
  )
}

// ─── 진행 중: 목표 마무리 ───────────────────────────────────────

/** 진행 중인 달성형 목표를 결과와 함께 끝내요. 고른 결과는 다음 목표 추천의 근거가 돼요 */
/** 목표 마무리 창을 밖에서 열기 (⋯ 더보기 메뉴 — 명세 13.5) */
export interface GoalWrapUpHandle {
  open: (entryPoint: 'menu') => void
}

export function GoalWrapUpSection({
  goal,
  headless = false,
  ref,
}: {
  goal: GoalCategory
  /** 카드 없이 창만 (⋯ 더보기 메뉴에서 열어요) */
  headless?: boolean
  ref?: Ref<GoalWrapUpHandle>
}) {
  const progress = useGoalProgress(goal.goalCategoryId)
  const complete = useCompleteGoal(goal.goalCategoryId)
  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<OutcomeResult | null>(null)
  const [achievement, setAchievement] = useState('')

  const today = todayYmd()
  const metricDone = progress.data?.metric?.status === 'ACHIEVED'
  const periodOver = !!goal.goalCategoryEndDate && goal.goalCategoryEndDate < today
  const gid = goal.goalCategoryId
  const wrapupPrompt = metricDone ? 'metric_done' : periodOver ? 'period_over' : 'normal'
  const lead = metricDone
    ? '목표값에 도달했어요! 결과를 남기고 마무리해 볼까요?'
    : periodOver
      ? '기간이 끝났어요. 어땠는지 남기고 마무리해요.'
      : '목표를 끝냈다면 결과를 남겨 주세요. 다음 목표를 짤 때 참고해요.'

  const openModal = (entryPoint?: 'menu') => {
    track('goal_detail.wrapup_open', {
      goal_category_id: gid,
      wrapup_prompt: wrapupPrompt,
      ...(entryPoint ? { entry_point: entryPoint } : {}),
    })
    setResult(metricDone ? 'ACHIEVED' : null)
    setAchievement('')
    setOpen(true)
  }

  useImperativeHandle(ref, () => ({ open: (entryPoint) => openModal(entryPoint) }))

  const close = (method: ModalCloseMethod | 'cancel_button') => {
    if (complete.isPending) return
    track('goal_detail.wrapup_cancel', {
      goal_category_id: gid,
      close_method: method,
      outcome_result: result ?? undefined,
    })
    setOpen(false)
  }

  const pick = (r: OutcomeResult) => {
    track('goal_detail.wrapup_outcome_select', { goal_category_id: gid, outcome_result: r })
    setResult(r)
  }

  const submit = () => {
    if (!result) return
    const note = achievement.trim()
    track('goal_detail.wrapup_submit', {
      goal_category_id: gid,
      outcome_result: result,
      achievement_length: note.length,
      was_preselected: metricDone && result === 'ACHIEVED',
      wrapup_prompt: wrapupPrompt,
    })
    complete
      .mutateAsync({ goalCategoryEndDate: today, achievement: note || null, result })
      .then(() => {
        track('goal_detail.wrapup_result', { goal_category_id: gid, result: 'success', outcome_result: result })
        setOpen(false)
      })
      .catch((e: unknown) =>
        track('goal_detail.wrapup_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
          outcome_result: result,
        })
      )
  }

  const modal = (
      <Modal
        open={open}
        onClose={close}
        title="이 목표, 어땠나요?"
        description="오늘 날짜로 마무리해요. 남은 기간의 루틴과 예정된 할 일은 캘린더에서 빠지고, 지난 기록과 완료한 일은 그대로 남아요."
        dismissible={!complete.isPending}
        footer={
          <>
            <Button variant="secondary" onClick={() => close('cancel_button')} disabled={complete.isPending}>
              취소
            </Button>
            <Button onClick={submit} disabled={!result} loading={complete.isPending}>
              마무리하기
            </Button>
          </>
        }
      >
        <OutcomePicker value={result} onChange={pick} disabled={complete.isPending} />
        <label className="mt-4 block">
          <span className="text-ink-2 text-[13px] font-semibold">한 줄 소감 (선택)</span>
          <textarea
            value={achievement}
            onChange={(e) => setAchievement(e.target.value.slice(0, ACHIEVEMENT_MAX))}
            rows={3}
            disabled={complete.isPending}
            placeholder="예) 주 3회 운동은 지켰는데 식단은 어려웠어요"
            className={cn(inlineInput, 'bg-surface mt-1.5 h-auto min-h-[80px] resize-y py-2 leading-relaxed')}
          />
        </label>
      </Modal>
  )
  if (headless) return modal
  return (
    <Section title="목표 마무리">
      <p className={cn('text-[14px]', metricDone || periodOver ? 'text-ink font-semibold' : 'text-ink-2')}>{lead}</p>
      <Button
        variant={metricDone || periodOver ? 'primary' : 'secondary'}
        fullWidth
        className="mt-3"
        onClick={() => openModal()}
      >
        <Flag className="size-4" />
        마무리하기
      </Button>
      {modal}
    </Section>
  )
}

// ─── 끝난 목표: 결과 ────────────────────────────────────────────

function OutcomeStats({ outcome }: { outcome: GoalOutcome }) {
  const stats = [
    outcome.metricPercent != null && `수치 ${outcome.metricPercent}%`,
    outcome.routineRate != null && `루틴 실천 ${outcome.routineRate}%`,
    outcome.taskRate != null && `할 일 완료 ${outcome.taskRate}%`,
  ].filter(Boolean) as string[]
  if (!stats.length && !outcome.metricUnrecorded) return null
  return (
    <>
      {stats.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {stats.map((s) => (
            <span key={s} className="bg-subtle text-ink-2 rounded-full px-3 py-1 text-[13px] font-medium tabular-nums">
              {s}
            </span>
          ))}
        </div>
      )}
      {outcome.metricUnrecorded && (
        <p className="text-ink-3 mt-2 text-[13px]">수치 기록이 없어서 실천률로 판정했어요.</p>
      )}
      {outcome.source === 'AUTO' && outcome.autoMessage && (
        <p className="text-ink-3 mt-2 text-[13px]">{outcome.autoMessage}</p>
      )}
    </>
  )
}

/**
 * 끝난 목표의 결과.
 * 기간 만료로 자동 판정(AUTO)된 목표는 "이 목표 어땠나요?"로 직접 골라 달라고 해요 — 직접 고른 결과가 더 정확해요.
 */
export function GoalOutcomeSection({ goal }: { goal: GoalCategory }) {
  const progress = useGoalProgress(goal.goalCategoryId)
  const update = useUpdateOutcome(goal.goalCategoryId)
  const [editing, setEditing] = useState(false)
  const outcome = progress.data?.outcome ?? null

  const ask = !outcome || outcome.source === 'AUTO' || editing
  const gid = goal.goalCategoryId
  const source = outcome?.source ?? 'none'
  const autoResult = outcome?.autoResult ?? undefined
  const autoReason = outcome?.autoReason ?? undefined

  // '이 목표 어땠나요?' 칸이 보일 때 한 번 (결과를 불러온 뒤, 다시 고르기는 제외)
  const askVisible = progress.isSuccess && ask && !editing
  const askSent = useRef(false)
  useEffect(() => {
    if (!askVisible || askSent.current) return
    askSent.current = true
    track('goal_detail.outcome_ask_view', {
      goal_category_id: gid,
      outcome_source: source,
      auto_result: autoResult,
      auto_reason: autoReason,
    })
  }, [askVisible, source, gid, autoResult, autoReason])

  const toggleEditing = (next: boolean) => {
    track('goal_detail.outcome_change_click', { goal_category_id: gid, outcome_action: next ? 'open' : 'cancel' })
    setEditing(next)
  }

  const choose = (r: OutcomeResult) => {
    track('goal_detail.outcome_select', {
      goal_category_id: gid,
      outcome_result: r,
      outcome_source: source,
      is_change: editing,
      auto_result: autoResult,
      is_match: autoResult ? r === autoResult : undefined,
    })
    update
      .mutateAsync(r)
      .then(() => {
        track('goal_detail.outcome_result', { goal_category_id: gid, result: 'success', outcome_result: r })
        setEditing(false)
      })
      .catch((e: unknown) =>
        track('goal_detail.outcome_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
          outcome_result: r,
        })
      )
  }

  return (
    <Section
      title="목표 결과"
      aside={
        outcome?.source === 'USER' &&
        !editing && (
          <button type="button" onClick={() => toggleEditing(true)} className="hover:text-ink font-semibold">
            바꾸기
          </button>
        )
      }
    >
      {outcome && !editing && (
        <p className="text-[17px] font-bold">
          {outcomeOf(outcome.result).emoji} {outcomeOf(outcome.result).label}
          {outcome.source === 'AUTO' && <span className="text-ink-3 ml-2 text-xs font-medium">기록으로 자동 판정</span>}
        </p>
      )}
      {outcome && <OutcomeStats outcome={outcome} />}
      {ask && (
        <div className={cn(outcome && !editing && 'border-line mt-4 border-t pt-4')}>
          <p className="text-ink-2 mb-2.5 text-[14px] font-semibold">
            {editing ? '결과를 다시 골라 주세요' : '이 목표 어땠나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요'}
          </p>
          <OutcomePicker
            value={editing ? (outcome?.result ?? null) : null}
            onChange={choose}
            disabled={update.isPending}
          />
          {editing && (
            <button
              type="button"
              onClick={() => toggleEditing(false)}
              className="text-ink-3 hover:text-ink mt-2 text-[13px] font-semibold"
            >
              취소
            </button>
          )}
        </div>
      )}
    </Section>
  )
}

// ─── 진행 중: 주간 점검 제안 ────────────────────────────────────

/** 2주 연속 실천률이 낮으면 하루 줄이기, 높으면 하루 늘리기를 제안해요 */
export function GoalAdjustmentBanner({ goal }: { goal: GoalCategory }) {
  const progress = useGoalProgress(goal.goalCategoryId)
  const apply = useApplyAdjustment(goal.goalCategoryId)
  const dismiss = useDismissAdjustment(goal.goalCategoryId)
  const adj: GoalAdjustment | null | undefined = progress.data?.adjustment
  const gid = goal.goalCategoryId

  // 제안이 보일 때 제안마다 한 번
  const sentKey = useRef<string | null>(null)
  const adjKey = adj?.key ?? null
  const adjDirection = adj?.direction
  const previousRate = adj?.previousWeekRate
  const lastRate = adj?.lastWeekRate
  useEffect(() => {
    if (!adjKey || sentKey.current === adjKey) return
    sentKey.current = adjKey
    track('goal_detail.adjust_banner_view', {
      goal_category_id: gid,
      adjust_direction: adjDirection,
      previous_rate: previousRate,
      last_rate: lastRate,
    })
  }, [adjKey, adjDirection, previousRate, lastRate, gid])

  if (!adj) return null

  const trackResult = (action: 'apply' | 'dismiss', p: Promise<unknown>) =>
    p
      .then(() =>
        track('goal_detail.adjust_result', { goal_category_id: gid, adjust_action: action, result: 'success' })
      )
      .catch((e: unknown) =>
        track('goal_detail.adjust_result', {
          goal_category_id: gid,
          adjust_action: action,
          result: 'fail',
          error_status: errorStatusOf(e),
        })
      )
  const doApply = () => {
    track('goal_detail.adjust_apply_click', { goal_category_id: gid, adjust_direction: adj.direction })
    trackResult('apply', apply.mutateAsync(adj.key))
  }
  const doDismiss = (method: 'x_button' | 'cancel_button') => {
    track('goal_detail.adjust_dismiss_click', {
      goal_category_id: gid,
      adjust_direction: adj.direction,
      close_method: method,
    })
    trackResult('dismiss', dismiss.mutateAsync(adj.key))
  }

  const down = adj.direction === 'DOWN'
  const Icon = down ? CalendarMinus : CalendarPlus
  const busy = apply.isPending || dismiss.isPending
  const day = JAVA_DAY_LABEL[adj.day]

  return (
    <div
      className={cn(
        'relative rounded-2xl border px-5 py-4',
        down ? 'border-border-subtle bg-warning-soft' : 'border-border-subtle bg-brand-soft'
      )}
    >
      <button
        type="button"
        aria-label="이번엔 넘기기"
        onClick={() => doDismiss('x_button')}
        disabled={busy}
        className="text-ink-3 hover:text-ink absolute top-3 right-3 rounded-lg p-1"
      >
        <X className="size-4" />
      </button>
      <p className={cn('flex items-center gap-1.5 pr-6 text-sm font-bold', down ? 'text-warning-ink' : 'text-text-brand')}>
        <Icon className="size-4" />
        주간 점검 · {adj.routineName}
      </p>
      <p className="text-ink-2 mt-1 text-[14px]">{adj.message}</p>
      <p className="text-ink-3 mt-1 text-[13px] tabular-nums">
        지난 2주 실천률 {adj.previousWeekRate}% → {adj.lastWeekRate}% · 주 {adj.fromDays}회 → {adj.toDays}회
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button size="sm" onClick={doApply} loading={apply.isPending} disabled={busy}>
          {down ? `${day}요일 빼기` : `${day}요일 더하기`}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => doDismiss('cancel_button')} disabled={busy}>
          지금 그대로 할게요
        </Button>
      </div>
    </div>
  )
}
