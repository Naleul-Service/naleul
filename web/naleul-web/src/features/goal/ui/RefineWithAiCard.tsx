'use client'

import { useEffect, useRef } from 'react'
import Link from 'next/link'
import { ArrowRight, NotebookPen, Sparkles } from '@/components/icons'
import { Button, buttonClass } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useKeepAsRecord } from '@/features/record/api'
import { useGoalTasks, type GoalCategory } from '../api'
import { AIBadge } from '@/components/ui/AIBadge'

export const refineHref = (goal: GoalCategory) => `/goal/new?sourceGoalId=${goal.goalCategoryId}`

/**
 * 임시 목표 → AI 목표로 구체화하기 유도.
 * 임시 목표는 Brain dump 가 대충 만든 그릇이라 기간·마일스톤·루틴이 없어요.
 * 구체화를 확정하면 지금 있는 Task 와 기록은 새 목표로 그대로 옮겨져요 (백엔드 BE-8).
 */
export function RefineWithAiCard({ goal, className }: { goal: GoalCategory; className?: string }) {
  // 회사 업무처럼 "이룰 것"이 없는 일은 구체화할 필요 없이 기록형으로 두는 게 맞아요
  const keep = useKeepAsRecord(goal.goalCategoryId)
  const gid = goal.goalCategoryId
  const tasks = useGoalTasks(gid)

  // 카드가 보일 때 한 번 — Task 목록을 받은 뒤 (못 받으면 task_count 없이)
  const viewSent = useRef(false)
  const taskCount = tasks.data ? tasks.data.tasks.filter((t) => t.routineId == null).length : undefined
  const tasksSettled = !tasks.isPending
  useEffect(() => {
    if (viewSent.current || !tasksSettled) return
    viewSent.current = true
    track('goal_detail.refine_card_view', { goal_category_id: gid, task_count: taskCount })
  }, [tasksSettled, taskCount, gid])

  const keepAsRecord = () => {
    track('goal_detail.keep_record_click', { goal_category_id: gid })
    keep
      .mutateAsync()
      .then(() => track('goal_detail.keep_record_result', { goal_category_id: gid, result: 'success' }))
      .catch((e: unknown) =>
        track('goal_detail.keep_record_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
        })
      )
  }

  const name = `${goal.emoji ? `${goal.emoji} ` : ''}${goal.goalCategoryName}`
  return (
    <section
      className={cn(
        'ai-border flex flex-col gap-4 rounded-card p-5 sm:flex-row sm:items-center sm:p-6',
        className
      )}
    >
      <AIBadge size="lg" />
      <div className="min-w-0 flex-1">
        <p className="text-[17px] leading-snug font-bold">AI로 &lsquo;{name}&rsquo; 목표를 구체화해 볼까요?</p>
        <p className="text-ink-3 mt-1 text-sm leading-relaxed">
          아직 임시 목표예요. 몇 가지 질문에 답하면 기간·마일스톤·루틴까지 설계해 드려요. 지금 있는 Task와 기록은 그대로
          옮겨져요. 회사 업무·사이드 프로젝트처럼 기한 없이 쌓아 갈 일이라면 업무형으로 두면 돼요.
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        <Button variant="secondary" size="lg" onClick={keepAsRecord} loading={keep.isPending}>
          <NotebookPen className="size-4" />
          업무형으로 두기
        </Button>
        <Link
          href={refineHref(goal)}
          onClick={() => track('goal_detail.refine_click', { goal_category_id: gid })}
          className={buttonClass('ai', 'lg')}
        >
          <Sparkles className="size-4" />
          AI로 구체화하기
          <ArrowRight className="size-4" />
        </Link>
      </div>
    </section>
  )
}
