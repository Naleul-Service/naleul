'use client'

import { useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/cn'
import Link from 'next/link'
import { CalendarDays, Check, Sparkles } from '@/components/icons'
import { Button, buttonClass } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { isHabitGoal, useGoalCategories, useGoalCategory } from '@/features/goal/api'
import { isOngoing } from '@/features/goal/format'
import { GoalTaskForm } from '@/features/goal/ui/GoalTaskForm'
import { useFillTimetable } from '@/features/timetable/api'
import { formatMonthDay, hm } from '@/features/timetable/time'
import type { TimeBlockTask } from '@/features/timetable/types'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TaskAddSurface } from '../analytics'

/** a → b 가 며칠인지 (YYYY-MM-DD) */
const daysBetween = (a: string, b: string) =>
  Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000)

/** 폼에 적어 둔 내용이 있는지 — 기본값이 채워지는 날짜·선택 상자는 빼고, 글자·시간·숫자 입력만 봐요 */
const formHasInput = (root: HTMLElement | null) =>
  !!root &&
  [
    ...root.querySelectorAll<HTMLInputElement>(
      'input:not([type]), input[type=text], input[type=number], input[type=time]'
    ),
  ].some((i) => i.value.trim() !== '')

const field =
  'border-border-subtle bg-surface focus:border-border-focus h-10 w-full min-w-0 rounded-input border px-3 text-[14px] outline-none'

/**
 * Task 직접 추가 — 목표를 고르고, 이름·날짜·시간(선택)·영역(선택)을 정해 Enter.
 * 시간을 비워 두면 그날 "시간 미정"으로 들어가고, 바로 "AI로 빈 시간에 배치하기"를 누를 수 있어요.
 */
export function ManualTaskAdd({
  inPanel = false,
  onCreated,
  surface = 'page',
  prefill,
}: {
  /** 첫 Task 폼에 미리 채울 날짜 · 시각 */
  prefill?: { date: string; startTime?: string | null; endTime?: string | null }
  /** 분석용 — task_add_manual.* 의 surface */
  surface?: TaskAddSurface
  /** 캘린더 옆 패널 안 — 캘린더가 이미 보이므로 "캘린더에서 보기"는 숨겨요 */
  inPanel?: boolean
  onCreated?: (t: TimeBlockTask) => void
} = {}) {
  // 기본 생활 습관으로도 분류할 수 있게 (2026-10)
  const goals = useGoalCategories({ includeHabit: true })
  const ongoing = (goals.data ?? []).filter((g) => isOngoing(g.goalCategoryStatus))
  const [goalId, setGoalId] = useState<number | null>(null)
  const selectedId = goalId ?? ongoing[0]?.goalCategoryId ?? null
  const goal = useGoalCategory(selectedId ?? 0, !!selectedId)
  const [formKey, setFormKey] = useState(0)
  const [added, setAdded] = useState<TimeBlockTask[]>([])
  // AI 배치를 요청한 Task (시간은 캘린더에서 확인)
  const [placed, setPlaced] = useState<Set<number>>(new Set())
  const fill = useFillTimetable()
  const formRef = useRef<HTMLDivElement>(null)

  // '연결할 목표 없음' 카드가 보일 때 한 번 (목록을 못 불러와도 같은 카드)
  const noGoal = !goals.isPending && !ongoing.length
  const goalListState = goals.isError ? 'error' : 'empty'
  const noGoalSent = useRef(false)
  useEffect(() => {
    if (!noGoal || noGoalSent.current) return
    noGoalSent.current = true
    track('task_add_manual.no_goal_view', { surface, goal_list_state: goalListState })
  }, [noGoal, goalListState, surface])

  if (goals.isPending) {
    return (
      <Card className="grid min-h-[200px] place-items-center">
        <Spinner className="text-ink-3 size-5" />
      </Card>
    )
  }
  if (!ongoing.length) {
    return (
      <Card className="p-8 text-center">
        <p className="font-bold">Task를 연결할 목표가 없어요</p>
        <p className="text-ink-3 mt-1 text-sm">
          목표를 먼저 만들거나, AI로 정리하기를 쓰면 임시 목표가 자동으로 만들어져요.
        </p>
        <Link
          href="/goal/add"
          className={buttonClass('primary') + ' mt-4'}
          onClick={() => track('task_add_manual.goal_create_click', { surface })}
        >
          목표 만들기
        </Link>
      </Card>
    )
  }

  const unplaced = added.filter((t) => !t.plannedStartAt && !placed.has(t.taskId))
  const unplacedDates = [...new Set(unplaced.map((t) => t.date ?? t.scheduledDate).filter(Boolean))] as string[]

  return (
    <Card className="p-5 sm:p-6">
      {!inPanel && <h2 className="text-h4">직접 추가하기</h2>}
      <p className={cn('text-ink-3 text-sm', !inPanel && 'mt-1')}>
        이름과 날짜만 넣고 Enter. 시간을 비워 두면 그날 빈 시간에 AI가 배치할 수 있어요.
      </p>

      <label className="mt-4 block max-w-sm">
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">목표</span>
        <select
          value={selectedId ?? ''}
          onChange={(e) => {
            const id = Number(e.target.value)
            track('task_add_manual.goal_select', {
              surface,
              goal_category_id: id,
              is_temporary: !!ongoing.find((g) => g.goalCategoryId === id)?.temporary,
              is_habit_goal: isHabitGoal(ongoing.find((g) => g.goalCategoryId === id)),
              has_input: formHasInput(formRef.current),
            })
            setGoalId(id)
            setFormKey((k) => k + 1)
          }}
          className={field}
        >
          {ongoing.map((g) => (
            <option key={g.goalCategoryId} value={g.goalCategoryId}>
              {g.emoji ? `${g.emoji} ` : ''}
              {g.goalCategoryName}
              {g.temporary ? ' (임시)' : ''}
            </option>
          ))}
        </select>
      </label>

      <div className="mt-3" ref={formRef}>
        {goal.data ? (
          <GoalTaskForm
            key={`${selectedId}-${formKey}`}
            goal={goal.data}
            surface="task_add_manual"
            // 빈 곳을 눌러 연 시각은 첫 Task 에만 (저장하고 다음 Task 는 비워서)
            prefill={formKey === 0 ? prefill : undefined}
            // 저장하면 폼을 비우고 다음 Task 를 바로 적을 수 있게
            onDone={() => setFormKey((k) => k + 1)}
            onCreated={(t) => {
              setAdded((a) => [t, ...a])
              onCreated?.(t)
            }}
          />
        ) : (
          <div className="grid min-h-[160px] place-items-center">
            <Spinner className="text-ink-3 size-5" />
          </div>
        )}
      </div>

      {added.length > 0 && (
        <div className="border-line mt-5 border-t pt-4">
          <p className="text-ink-3 mb-2 text-xs font-semibold">방금 추가한 Task {added.length}</p>
          <ul className="space-y-1.5">
            {added.map((t) => (
              <li key={t.taskId} className="flex items-center gap-2 text-sm">
                <Check className="text-success size-4 shrink-0" />
                <span className="min-w-0 flex-1 truncate font-medium">
                  {t.emoji ? `${t.emoji} ` : ''}
                  {t.taskName}
                </span>
                <span className="text-ink-3 shrink-0 text-xs">
                  {formatMonthDay((t.plannedStartAt ?? t.date ?? '').slice(0, 10) || t.scheduledDate || '')} ·{' '}
                  {t.plannedStartAt
                    ? `${hm(t.plannedStartAt)}–${hm(t.plannedEndAt)}`
                    : placed.has(t.taskId)
                      ? 'AI가 빈 시간에 배치했어요'
                      : '시간 미정'}
                </span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex flex-wrap gap-2">
            {unplacedDates.length > 0 && (
              <Button
                variant="ai"
                size="sm"
                loading={fill.isPending}
                onClick={() => {
                  const sorted = [...unplacedDates].sort()
                  track('task_add_manual.ai_fill_click', {
                    surface,
                    unscheduled_count: unplaced.length,
                    day_count: daysBetween(sorted[0], sorted[sorted.length - 1]) + 1,
                  })
                  fill.mutate(
                    { startDate: sorted[0], endDate: sorted[sorted.length - 1] },
                    {
                      onSuccess: () => {
                        setPlaced(new Set([...placed, ...unplaced.map((t) => t.taskId)]))
                        track('task_add_manual.ai_fill_result', { surface, result: 'success' })
                      },
                      onError: (e) =>
                        track('task_add_manual.ai_fill_result', {
                          surface,
                          result: 'fail',
                          error_status: errorStatusOf(e),
                        }),
                    }
                  )
                }}
              >
                <Sparkles className="size-3.5" />
                시간 미정 {unplaced.length}개 AI로 빈 시간에 배치하기
              </Button>
            )}
            {!inPanel && (
              <Link
                href={`/calendar?view=week&date=${(added[0].plannedStartAt ?? added[0].date ?? '').slice(0, 10)}`}
                className={buttonClass('secondary', 'sm')}
                onClick={() =>
                  track('task_add_manual.calendar_click', {
                    item_count: added.length,
                    has_date: !!(added[0].plannedStartAt ?? added[0].date ?? '').slice(0, 10),
                  })
                }
              >
                <CalendarDays className="size-3.5" />
                캘린더에서 보기
              </Link>
            )}
          </div>
        </div>
      )}
    </Card>
  )
}
