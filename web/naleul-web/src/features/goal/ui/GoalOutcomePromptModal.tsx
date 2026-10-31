'use client'

import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import {
  useAnswerOutcomePrompt,
  useDismissOutcomePrompt,
  useOutcomePrompts,
  type OutcomePrompt,
  type OutcomeResult,
} from '../api'
import { OutcomePicker, outcomeOf } from './GoalOutcomeSection'

/** 이벤트에 공통으로 붙는 값 (노션 Tracking Plan 「목표 결과 확인 모달」) */
const baseProps = (p: OutcomePrompt) => ({
  goal_category_id: p.goalCategoryId,
  auto_result: p.autoResult,
  auto_reason: p.autoReason ?? undefined,
  is_ai_generated: p.aiGenerated,
})

/**
 * 결과 확인 모달 — "OO 목표 기한이 지났어요. 목표는 만족했나요?"
 *
 * 왜 필요한가: 기한이 지나면 서버가 수치(80% 이상) · 실천률로 자동 판정해요. 사용자가 직접 고른 결과와
 * 얼마나 맞는지(판정 일치율) 알아야 대시보드의 목표 달성률을 믿을 수 있는데, 목표 상세에 들어가야만 고를 수 있으면
 * 답이 거의 모이지 않아요. 그래서 앱을 열 때 한 번 물어요.
 *  - 고르면 자동 판정을 덮어써요 (PATCH /goal-categories/{id}/outcome)
 *  - "나중에"는 자동 판정을 그대로 두고 다시 묻지 않아요 (목표 상세에서는 언제든 바꿀 수 있어요)
 *  - 여러 개면 오래된 것부터 하나씩
 */
export function GoalOutcomePromptModal({ enabled }: { enabled: boolean }) {
  const prompts = useOutcomePrompts(enabled)
  const answer = useAnswerOutcomePrompt()
  const dismiss = useDismissOutcomePrompt()
  const list = prompts.data ?? []
  const current = list[0]
  const busy = answer.isPending || dismiss.isPending

  // 목표마다 한 번 (같은 화면에서 다시 그려져도 중복 없이)
  const seen = useRef(new Set<number>())
  const remaining = list.length
  useEffect(() => {
    if (!current || seen.current.has(current.goalCategoryId)) return
    seen.current.add(current.goalCategoryId)
    track('goal_outcome_modal.modal_view', {
      ...baseProps(current),
      metric_percent: current.metricPercent ?? undefined,
      routine_rate: current.routineRate ?? undefined,
      task_rate: current.taskRate ?? undefined,
      remaining_count: remaining,
    })
  }, [current, remaining])

  if (!current) return null

  const choose = (r: OutcomeResult) => {
    const props = { ...baseProps(current), outcome_result: r, is_match: r === current.autoResult }
    track('goal_outcome_modal.outcome_select', props)
    answer
      .mutateAsync({ goalId: current.goalCategoryId, result: r })
      .then(() => track('goal_outcome_modal.answer_result', { ...props, result: 'success' }))
      .catch((e: unknown) =>
        track('goal_outcome_modal.answer_result', { ...props, result: 'fail', error_status: errorStatusOf(e) })
      )
  }

  const later = (method: ModalCloseMethod | 'later_button') => {
    if (busy) return
    const props = baseProps(current)
    track('goal_outcome_modal.later_click', { ...props, close_method: method })
    dismiss
      .mutateAsync(current.goalCategoryId)
      .then(() => track('goal_outcome_modal.later_result', { ...props, result: 'success' }))
      .catch((e: unknown) =>
        track('goal_outcome_modal.later_result', { ...props, result: 'fail', error_status: errorStatusOf(e) })
      )
  }

  const auto = outcomeOf(current.autoResult)
  return (
    <Modal
      open
      onClose={later}
      dismissible={!busy}
      title={
        <>
          {current.emoji ? `${current.emoji} ` : ''}
          {current.goalName} 목표 기한이 지났어요
        </>
      }
      description="목표는 만족했나요? 직접 골라 주면 다음 목표가 더 잘 맞춰져요."
      footer={
        <Button variant="secondary" fullWidth disabled={busy} onClick={() => later('later_button')}>
          나중에 (자동 판정 그대로 두기)
        </Button>
      }
    >
      <div className="bg-subtle rounded-2xl px-4 py-3">
        <p className="text-ink-2 text-[13px] font-semibold">
          자동 판정 · {auto.emoji} {auto.label}
        </p>
        {current.autoMessage && <p className="text-ink mt-1 text-[14px] leading-relaxed">{current.autoMessage}</p>}
      </div>
      <p className="text-ink-2 mt-4 mb-2.5 text-[14px] font-semibold">내가 느끼기엔</p>
      <OutcomePicker value={null} onChange={choose} disabled={busy} />
      {remaining > 1 && <p className="text-ink-3 mt-3 text-[12px]">물어볼 목표가 {remaining - 1}개 더 있어요.</p>}
    </Modal>
  )
}
