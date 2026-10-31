'use client'

import { Fragment, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { Check, ChevronDown, Repeat } from '@/components/icons'
import { Card } from '@/components/ui/Card'
import { Badge, Chip } from '@/components/ui/Chip'
import { Spinner } from '@/components/ui/Spinner'
import { errorStatusOf } from '@/features/home/analytics'
import { cn } from '@/lib/cn'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { shownTime } from '@/features/timetable/layout'
import { goalAccent } from '@/lib/goalTone'
import { WEEKDAY_LABEL, addDays, hm, startOfWeek, todayKst, weekdayIndex } from '@/features/timetable/time'
import type { TimeBlockTask } from '@/features/timetable/types'
import { useGoalTasks, useToggleGoalTask, type GoalCategory } from '../api'
import { useDeleteGoalTask } from '../edit/mutations'
import { AddButton, InlineConfirm, RowActions, useEditing } from '../edit/inline'
import { GoalTaskForm, daysFromToday, trackTaskList, type GoalTaskSurface } from './GoalTaskForm'
import { AIBadge } from '@/components/ui/AIBadge'

type StatusFilter = 'ALL' | 'TODO' | 'DONE'
type KindFilter = 'ALL' | 'ONE_TIME' | 'ROUTINE'
/** 언제 할 Task 를 볼지 — 기본은 이번 주 (전체는 몇 달치가 쌓여 지금 할 일이 묻혀요) */
type PeriodFilter = 'TODAY' | 'WEEK' | 'ALL'

const PAGE = 20

export const isDone = (t: TimeBlockTask) => t.taskStatus === 'COMPLETED'

const matchKind = (t: TimeBlockTask, k: KindFilter) =>
  k === 'ALL' || (k === 'ROUTINE' ? t.sourceType === 'ROUTINE' : t.sourceType !== 'ROUTINE')

const matchStatus = (t: TimeBlockTask, s: StatusFilter) => s === 'ALL' || (s === 'DONE' ? isDone(t) : !isDone(t))

/** 이 Task 가 화면에 놓이는 날짜 (완료 후 실제 → 계획 → 시간 미정 날짜 → 권장일 → 마감일) */
export const dayOf = (t: TimeBlockTask) =>
  shownTime(t)?.startAt.slice(0, 10) ?? t.date ?? t.scheduledDate ?? t.dueDate ?? null

/** 열려 있던 입력창(key)이 다른 입력창이 열려서 닫히면 goal_task_list.edit_cancel (close_method: other_open) */
function useOtherOpenCancel(key: string, current: string | null, surface: GoalTaskSurface, isNew: boolean) {
  const prev = useRef(current)
  useEffect(() => {
    const was = prev.current
    prev.current = current
    if (was === key && current !== null && current !== key) {
      trackTaskList(surface, 'goal_task_list.edit_cancel', { is_new: isNew, close_method: 'other_open' })
    }
  }, [key, current, surface, isNew])
}

/** "오늘" · "내일" · "어제" · "10.08 (목)" (해가 다르면 "2027.01.03 (일)") */
function dayLabel(ymd: string, today: string) {
  if (ymd === today) return '오늘'
  if (ymd === addDays(today, 1)) return '내일'
  if (ymd === addDays(today, -1)) return '어제'
  const md = `${ymd.slice(5, 7)}.${ymd.slice(8, 10)}`
  const date = ymd.slice(0, 4) === today.slice(0, 4) ? md : `${ymd.slice(0, 4)}.${md}`
  return `${date} (${WEEKDAY_LABEL[weekdayIndex(ymd)]})`
}

/** 한 줄 — 수정(일회성 Task 만)·삭제(미션 제외)는 그 줄이 입력창/확인창으로 바뀌어요 */
function TaskItem({
  surface,
  ...props
}: {
  goal: GoalCategory
  task: TimeBlockTask
  today: string
  onToggle: (t: TimeBlockTask) => void
  toggling: boolean
  surface: GoalTaskSurface
}) {
  const t = props.task
  const key = `task:${t.taskId}`
  const edit = useEditing(key)
  const del = useEditing(`del:${key}`)
  const remove = useDeleteGoalTask()
  useOtherOpenCancel(key, edit.current, surface, false)
  const taskProps = { task_id: t.taskId, source_type: t.sourceType }
  const sendDeleteResult = (err?: unknown) =>
    trackTaskList(surface, 'goal_task_list.result', {
      goal_category_id: props.goal.goalCategoryId,
      task_action: 'delete',
      result: err === undefined ? 'success' : 'fail',
      error_status: err === undefined ? undefined : errorStatusOf(err),
    })

  if (edit.isOpen) {
    return (
      <li className="py-2">
        <GoalTaskForm goal={props.goal} task={t} onDone={edit.close} surface={surface} />
      </li>
    )
  }
  if (del.isOpen) {
    return (
      <li className="py-2">
        <InlineConfirm
          message={`'${t.taskName}'을 삭제할까요?`}
          detail={t.sourceType === 'ROUTINE' ? '이 날짜의 루틴 Task 하나만 지워져요. 루틴은 그대로예요.' : undefined}
          loading={remove.isPending}
          onCancel={() => {
            trackTaskList(surface, 'goal_task_list.delete_cancel', taskProps)
            del.close()
          }}
          onConfirm={() => {
            trackTaskList(surface, 'goal_task_list.delete_confirm', taskProps)
            remove.mutate(t.taskId, {
              onSuccess: () => {
                sendDeleteResult()
                del.close()
              },
              onError: (e) => sendDeleteResult(e),
            })
          }}
        />
      </li>
    )
  }
  return (
    <TaskRow
      {...props}
      onNameClick={() =>
        trackTaskList(surface, 'goal_task_list.task_name_click', {
          task_id: t.taskId,
          days_from_today: daysFromToday(dayOf(t), props.today),
        })
      }
      onEdit={
        t.sourceType === 'MANUAL'
          ? () => {
              trackTaskList(surface, 'goal_task_list.edit_open', {
                goal_category_id: props.goal.goalCategoryId,
                ...taskProps,
              })
              edit.open()
            }
          : undefined
      }
      onDelete={
        t.sourceType !== 'MISSION'
          ? () => {
              trackTaskList(surface, 'goal_task_list.delete_click', taskProps)
              del.open()
            }
          : undefined
      }
    />
  )
}

function TaskRow({
  task: t,
  today,
  onToggle,
  toggling,
  onEdit,
  onDelete,
  onNameClick,
}: {
  task: TimeBlockTask
  today: string
  onToggle: (t: TimeBlockTask) => void
  toggling: boolean
  onNameClick?: () => void
  onEdit?: () => void
  onDelete?: () => void
}) {
  const done = isDone(t)
  const shown = shownTime(t)
  const day = dayOf(t)
  const time = shown ? `${hm(shown.startAt)}${shown.endAt ? `–${hm(shown.endAt)}` : ''}` : '시간 미정'
  const sub = [t.generalCategoryName, t.milestoneTitle].filter(Boolean).join(' · ')

  return (
    <li className="group flex items-center gap-3 py-3">
      <button
        type="button"
        onClick={() => onToggle(t)}
        disabled={toggling}
        aria-label={done ? `${t.taskName} 완료 취소` : `${t.taskName} 완료`}
        aria-pressed={done}
        className={cn(
          'grid size-6 shrink-0 place-items-center rounded-full border-[1.5px] transition-colors disabled:opacity-50',
          done ? 'border-success bg-success text-on-ink' : 'border-border-control hover:border-success'
        )}
      >
        {toggling ? <Spinner className="size-3" /> : done && <Check className="size-3.5" />}
      </button>

      {/* 날짜 */}
      <div className="w-[88px] shrink-0">
        <p
          className={cn(
            'text-[13px] font-semibold',
            day === today && !done ? 'text-text-brand' : t.missed ? 'text-danger' : done ? 'text-ink-4' : 'text-ink-2'
          )}
        >
          {day ? dayLabel(day, today) : '날짜 미정'}
        </p>
        <p className="text-ink-4 text-[11px] whitespace-nowrap tabular-nums">
          {shown?.actual && '실제 '}
          {time}
        </p>
      </div>

      <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(t.goalColorCode) }} />

      <div className="min-w-0 flex-1">
        {day ? (
          <Link
            href={`/calendar?view=day&date=${day}`}
            onClick={onNameClick}
            className={cn(
              'block truncate text-[15px] font-semibold hover:underline',
              done && 'text-ink-4 line-through'
            )}
          >
            {t.emoji ? `${t.emoji} ` : ''}
            {t.taskName}
          </Link>
        ) : (
          <p className={cn('truncate text-[15px] font-semibold', done && 'text-ink-4 line-through')}>
            {t.emoji ? `${t.emoji} ` : ''}
            {t.taskName}
          </p>
        )}
        {sub && <p className="text-ink-3 mt-0.5 truncate text-[12px]">{sub}</p>}
      </div>

      <div className="hidden shrink-0 items-center gap-1 sm:flex">
        {t.missed && <Badge tone="danger">놓침</Badge>}
        {t.taskStatus === 'SKIPPED' && <Badge>건너뜀</Badge>}
        {t.sourceType === 'ROUTINE' && (
          <Badge>
            <Repeat className="size-3" />
            루틴
          </Badge>
        )}
        {t.source === 'AI_GOAL' && (
          <Badge tone="brand">
            <AIBadge size="sm" className="-ml-2 rounded-full" />
            AI
          </Badge>
        )}
      </div>
      <RowActions label={t.taskName} onEdit={onEdit} onDelete={onDelete} />
    </li>
  )
}

/**
 * 목표 상세 · 관련 Task 전체.
 * AI 가 만든 Task · 직접 추가한 Task · 루틴 Task 를 한 번에 보고, 완료/미완료 · 할 일/루틴으로 거를 수 있어요.
 * 정렬은 서버 순서 그대로: 미완료 위 · 오늘과 가까운 날짜일수록 위 · 완료는 아래.
 */
export function GoalTaskList({
  goal,
  title = '관련 Task',
  hint,
  addLabel = 'Task 추가',
  emptyText = '이 목표에 연결된 Task가 아직 없어요. 위에서 바로 추가해 보세요.',
  initialPeriod = 'WEEK',
  surface = 'goal_detail',
}: {
  goal: GoalCategory
  title?: string
  /** 제목 아래 한 줄 설명 */
  hint?: string
  addLabel?: string
  emptyText?: string
  initialPeriod?: PeriodFilter
  /** 분석용 — 이 목록이 쓰인 화면 (goal_task_list.* 의 surface) */
  surface?: GoalTaskSurface
}) {
  const goalId = goal.goalCategoryId
  const { data, isPending, isError, error, refetch } = useGoalTasks(goalId)
  const add = useEditing('task:new')
  useOtherOpenCancel('task:new', add.current, surface, true)
  const toggle = useToggleGoalTask(goalId)
  const [period, setPeriod] = useState<PeriodFilter>(initialPeriod)
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const [kind, setKind] = useState<KindFilter>('ALL')
  const [limit, setLimit] = useState(PAGE)
  const today = todayKst()

  const all = data?.tasks ?? []
  const weekStart = startOfWeek(today)
  const weekEnd = addDays(weekStart, 6)
  const inPeriod = (t: TimeBlockTask, p: PeriodFilter) => {
    if (p === 'ALL') return true
    const day = dayOf(t)
    if (!day) return false
    return p === 'TODAY' ? day === today : day >= weekStart && day <= weekEnd
  }
  const periodCounts = {
    TODAY: all.filter((t) => inPeriod(t, 'TODAY')).length,
    WEEK: all.filter((t) => inPeriod(t, 'WEEK')).length,
    ALL: all.length,
  }
  const byKind = all.filter((t) => inPeriod(t, period) && matchKind(t, kind))
  const shown = byKind.filter((t) => matchStatus(t, status))
  const visible = shown.slice(0, limit)
  const counts = {
    ALL: byKind.length,
    TODO: byKind.filter((t) => !isDone(t)).length,
    DONE: byKind.filter(isDone).length,
  }
  const hasRoutine = all.some((t) => t.sourceType === 'ROUTINE')
  const firstDoneId = status === 'ALL' ? visible.find(isDone)?.taskId : undefined

  /** 필터를 바꾼 뒤 보이는(조건에 맞는) Task 수 */
  const countWith = (p: PeriodFilter, k: KindFilter, s: StatusFilter) =>
    all.filter((t) => inPeriod(t, p) && matchKind(t, k) && matchStatus(t, s)).length
  const send = (event: Parameters<typeof trackTaskList>[1], props?: Parameters<typeof trackTaskList>[2]) =>
    trackTaskList(surface, event, props)

  const pickStatus = (s: StatusFilter) => {
    send('goal_task_list.status_filter_select', { task_filter: s, result_count: countWith(period, kind, s) })
    setStatus(s)
    setLimit(PAGE)
  }
  const pickPeriod = (p: PeriodFilter) => {
    send('goal_task_list.period_select', { period: p, result_count: countWith(p, kind, status) })
    setPeriod(p)
    setLimit(PAGE)
  }
  const pickKind = (k: KindFilter) => {
    send('goal_task_list.kind_filter_select', { task_kind: k, result_count: countWith(period, k, status) })
    setKind(k)
    setLimit(PAGE)
  }
  const onToggle = (t: TimeBlockTask) => {
    send('goal_task_list.toggle_complete', {
      goal_category_id: goalId,
      task_id: t.taskId,
      check_action: isDone(t) ? 'uncomplete' : 'complete',
      source_type: t.sourceType,
      is_missed: !!t.missed,
      days_from_today: daysFromToday(dayOf(t), today),
    })
    const sendResult = (err?: unknown) =>
      send('goal_task_list.result', {
        goal_category_id: goalId,
        task_action: 'toggle',
        result: err === undefined ? 'success' : 'fail',
        error_status: err === undefined ? undefined : errorStatusOf(err),
      })
    toggle.mutate(t, { onSuccess: () => sendResult(), onError: (e) => sendResult(e) })
  }

  // 'Task를 불러오지 못했어요'가 보일 때 (실패할 때마다 1회)
  const failSent = useRef(false)
  useEffect(() => {
    if (!isError) {
      failSent.current = false
      return
    }
    if (failSent.current) return
    failSent.current = true
    trackTaskList(surface, 'goal_task_list.load_fail', { error_status: errorStatusOf(error) })
  }, [isError, error, surface])

  // 기간·필터 때문에 0개가 보일 때 (조건 조합마다 1회)
  const emptyFiltered = !isPending && !isError && all.length > 0 && shown.length === 0
  const unfilteredCount = all.length
  const emptySent = useRef<string | null>(null)
  useEffect(() => {
    if (!emptyFiltered) {
      emptySent.current = null
      return
    }
    const k = `${period}|${status}|${kind}`
    if (emptySent.current === k) return
    emptySent.current = k
    trackTaskList(surface, 'goal_task_list.empty_filter_view', {
      period,
      task_filter: status,
      unfiltered_count: unfilteredCount,
    })
  }, [emptyFiltered, period, status, kind, unfilteredCount, surface])

  return (
    <Card className="p-5 sm:p-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-h4">{title}</h2>
        {data && (
          <span className="text-ink-3 text-[13px]">
            {data.completedCount} / {data.totalCount} 완료
          </span>
        )}
      </div>

      {hint && <p className="text-ink-3 -mt-2 mb-3 text-[13px] leading-relaxed">{hint}</p>}

      {/* 추가: 이름·날짜만 넣고 Enter */}
      <div className="mb-4">
        {add.isOpen ? (
          <GoalTaskForm goal={goal} onDone={add.close} surface={surface} />
        ) : (
          <AddButton
            onClick={() => {
              send('goal_task_list.add_click', {
                goal_category_id: goalId,
                task_count: all.filter((t) => t.sourceType !== 'ROUTINE').length,
              })
              add.open()
            }}
          >
            {addLabel}
          </AddButton>
        )}
      </div>

      {isPending ? (
        <div className="grid min-h-[120px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError ? (
        <div className="text-ink-3 py-6 text-center text-sm">
          Task를 불러오지 못했어요.{' '}
          <button
            type="button"
            className="text-ink-2 font-semibold underline"
            onClick={() => {
              send('goal_task_list.retry_click')
              refetch()
            }}
          >
            다시 시도
          </button>
        </div>
      ) : all.length === 0 ? (
        <p className="text-ink-3 py-4 text-center text-sm">{emptyText}</p>
      ) : (
        <>
          {/* 기간: 오늘 · 이번 주(기본) · 전체 */}
          <div className={segmentedClass('mb-2.5')} role="tablist" aria-label="기간">
            {(
              [
                ['TODAY', '오늘'],
                ['WEEK', '이번 주'],
                ['ALL', '전체 기간'],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={period === key}
                onClick={() => pickPeriod(key)}
                className={segmentClass(period === key, 'px-3')}
              >
                {label} <span className="tabular-nums">{periodCounts[key]}</span>
              </button>
            ))}
          </div>

          {/* 필터 */}
          <div className="flex flex-wrap items-center gap-1.5">
            {(
              [
                ['ALL', '전체'],
                ['TODO', '미완료'],
                ['DONE', '완료'],
              ] as const
            ).map(([key, label]) => (
              <Chip key={key} size="sm" selected={status === key} onClick={() => pickStatus(key)}>
                {label} {counts[key]}
              </Chip>
            ))}
            {hasRoutine && (
              <>
                <span className="bg-line mx-1 h-4 w-px" aria-hidden />
                {(
                  [
                    ['ALL', '모두'],
                    ['ONE_TIME', '할 일'],
                    ['ROUTINE', '루틴'],
                  ] as const
                ).map(([key, label]) => (
                  <Chip key={key} size="sm" selected={kind === key} onClick={() => pickKind(key)}>
                    {label}
                  </Chip>
                ))}
              </>
            )}
          </div>

          {shown.length === 0 ? (
            <p className="text-ink-3 py-8 text-center text-sm">
              {byKind.length === 0
                ? period === 'TODAY'
                  ? '오늘 할 Task가 없어요.'
                  : period === 'WEEK'
                    ? '이번 주 Task가 없어요.'
                    : '조건에 맞는 Task가 없어요.'
                : status === 'DONE'
                  ? '아직 완료한 Task가 없어요.'
                  : '남은 Task가 없어요. 모두 끝냈어요! 🎉'}
            </p>
          ) : (
            <ul className="divide-line mt-3 divide-y">
              {visible.map((t) => (
                <Fragment key={t.taskId}>
                  {/* 전체 보기에서 미완료와 완료 사이 구분선 */}
                  {t.taskId === firstDoneId && (
                    <li className="text-ink-3 pt-5 pb-1 text-xs font-semibold">완료한 Task</li>
                  )}
                  <TaskItem
                    goal={goal}
                    task={t}
                    today={today}
                    onToggle={onToggle}
                    toggling={toggle.isPending && toggle.variables?.taskId === t.taskId}
                    surface={surface}
                  />
                </Fragment>
              ))}
            </ul>
          )}

          {shown.length > visible.length && (
            <button
              type="button"
              onClick={() => {
                send('goal_task_list.load_more_click', {
                  shown_count: visible.length,
                  remaining_count: shown.length - visible.length,
                })
                setLimit((n) => n + PAGE)
              }}
              className="text-ink-2 hover:bg-subtle mt-2 flex w-full items-center justify-center gap-1 rounded-xl py-2.5 text-sm font-semibold"
            >
              더 보기 ({shown.length - visible.length}개 남음)
              <ChevronDown className="size-4" />
            </button>
          )}
        </>
      )}
    </Card>
  )
}
