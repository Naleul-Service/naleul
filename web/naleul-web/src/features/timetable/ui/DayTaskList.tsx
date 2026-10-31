'use client'

import { rectOf, type AnchorRect } from '@/components/ui/Popover'
import { type MouseEvent } from 'react'
import { Check, Repeat } from '@/components/icons'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { dateRelationOf } from '@/features/home/analytics'
import { goalAccent } from '@/lib/goalTone'
import { formatMonthDay, hm, minutesFrom } from '../time'
import type { CarriedOutBlock, TimeBlockTask, TimetableDay } from '../types'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { isTopPriority, PriorityBadge } from './Priority'

/** 그날 정렬용 시작 분 (시간 미정은 맨 뒤) */
const startOf = (day: string, t: TimeBlockTask) => (t.plannedStartAt ? minutesFrom(day, t.plannedStartAt) : Infinity)

/**
 * 일간 캘린더 오른쪽 "오늘 할 일" 목록.
 * TimeTable 은 "언제" 하는지를, 이 목록은 "무엇을 남겼는지"를 한눈에 보여줘요.
 *  - 남은 일(시간순, 시간 미정은 아래) / 완료한 일
 *  - 동그라미를 누르면 바로 완료·완료 취소, 줄을 누르면 TimeTable 과 같은 상세 창
 */
export function DayTaskList({
  day,
  today,
  onSelect,
  onToggle,
  togglingId,
  analytics = true,
  onCarriedClick,
}: {
  day: TimetableDay | undefined
  today: string
  onSelect: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onToggle: (t: TimeBlockTask) => void
  togglingId: number | null
  /** calendar_day_list.* 를 보낼지 — 캘린더 밖에서 쓰면 false */
  analytics?: boolean
  /** 다른 날로 옮긴 일(흔적)을 눌렀을 때 */
  onCarriedClick?: (c: CarriedOutBlock, fromDate: string, anchor?: AnchorRect) => void
}) {
  if (!day) return null
  const dayDate = day.date
  // calendar_day_list.* — 누르기 전 상태 기준
  const select = (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => {
    if (analytics)
      track('calendar_day_list.row_click', {
        task_id: t.taskId,
        task_status: t.taskStatus,
        is_scheduled: !!t.plannedStartAt,
      })
    onSelect(t, e)
  }
  const toggle = (t: TimeBlockTask) => {
    if (analytics)
      track('calendar_day_list.check_click', {
        task_id: t.taskId,
        check_action: t.taskStatus === 'COMPLETED' ? 'uncomplete' : 'complete',
        is_scheduled: !!t.plannedStartAt,
        date_relation: dateRelationOf(dayDate, today),
      })
    onToggle(t)
  }
  // 전날 밤부터 이어진 블록은 그날 칸에도 보이지만, 목록은 "이날 하기로 한 일"만 (계획 시작일 또는 시간 미정 날짜)
  const seen = new Set<number>()
  const all = [...day.tasks, ...day.unscheduledTasks].filter((t) => {
    if (seen.has(t.taskId) || t.taskStatus === 'SKIPPED' || t.taskStatus === 'DELETED') return false
    seen.add(t.taskId)
    const planDay = t.plannedStartAt?.slice(0, 10) ?? t.date ?? t.scheduledDate
    return !planDay || planDay === day.date || t.completedAt?.slice(0, 10) === day.date
  })
  const sorted = [...all].sort((a, b) => startOf(day.date, a) - startOf(day.date, b))
  const todo = sorted.filter((t) => t.taskStatus !== 'COMPLETED')
  const done = sorted.filter((t) => t.taskStatus === 'COMPLETED')
  const title = day.date === today ? '오늘 할 일' : `${formatMonthDay(day.date)} 할 일`
  // 이날 못 해서 다른 날로 옮겨진 일 — 목록 맨 아래에 흔적으로 (개수 · 진행률에는 넣지 않아요)
  const carried = (day.carriedOut ?? []).filter((c) => !all.some((t) => t.taskId === c.taskId))

  return (
    <section
      aria-label={title}
      className="border-line bg-surface flex max-h-[max(480px,calc(100dvh-230px))] flex-col overflow-hidden rounded-2xl border"
    >
      <header className="border-line flex items-center gap-2 border-b px-4 py-3">
        <h2 className="text-[15px] font-bold">{title}</h2>
        <span className="text-ink-3 ml-auto text-[13px] tabular-nums">
          {done.length} / {all.length} 완료
        </span>
      </header>
      {all.length > 0 && (
        <ProgressBar value={done.length / all.length} className="mx-4 mt-3 shrink-0" />
      )}
      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-2">
        {all.length === 0 && !carried.length ? (
          <p className="text-ink-3 px-2 py-8 text-center text-[13px]">이날 할 일이 없어요.</p>
        ) : (
          <>
            <ul>
              {todo.map((t) => (
                <Row key={t.taskId} t={t} onSelect={select} onToggle={toggle} busy={togglingId === t.taskId} />
              ))}
            </ul>
            {todo.length === 0 && all.length > 0 && (
              <p className="text-success px-2 py-3 text-[13px] font-semibold">모두 끝냈어요 🎉</p>
            )}
            {carried.length > 0 && (
              <>
                <p className="text-ink-3 mt-3 px-2 pb-1 text-[12px] font-semibold">
                  못 해서 다른 날로 옮긴 일 {carried.length}
                </p>
                <ul>
                  {carried.map((c) => (
                    <li key={`carried-${c.taskId}`}>
                      <button
                        type="button"
                        disabled={!onCarriedClick}
                        onClick={(ev) => onCarriedClick?.(c, dayDate, rectOf(ev.currentTarget))}
                        className="hover:bg-subtle flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-[14px] disabled:hover:bg-transparent"
                      >
                        <b className="text-danger w-[18px] shrink-0 text-center">*</b>
                        <s className="text-ink-3 decoration-ink-4 min-w-0 flex-1 truncate">
                          {c.emoji ? `${c.emoji} ` : ''}
                          {c.taskName}
                        </s>
                        <span className="text-ink-3 shrink-0 text-[12px] font-semibold">
                          → {formatMonthDay(c.toDate)}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
            {done.length > 0 && (
              <>
                <p className="text-ink-3 mt-3 px-2 pb-1 text-[12px] font-semibold">완료 {done.length}</p>
                <ul>
                  {done.map((t) => (
                    <Row key={t.taskId} t={t} onSelect={select} onToggle={toggle} busy={togglingId === t.taskId} />
                  ))}
                </ul>
              </>
            )}
          </>
        )}
      </div>
    </section>
  )
}

function Row({
  t,
  onSelect,
  onToggle,
  busy,
}: {
  t: TimeBlockTask
  onSelect: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onToggle: (t: TimeBlockTask) => void
  busy: boolean
}) {
  const done = t.taskStatus === 'COMPLETED'
  const mission = t.sourceType === 'MISSION'
  return (
    <li className="hover:bg-subtle/70 flex items-center gap-2 rounded-xl px-2 py-1.5">
      <button
        type="button"
        onClick={() => onToggle(t)}
        disabled={busy || mission}
        aria-label={done ? `${t.taskName} 완료 취소` : `${t.taskName} 완료`}
        className={cn(
          'grid size-5 shrink-0 place-items-center rounded-full border-[1.5px] transition-colors disabled:opacity-50',
          done ? 'border-success bg-success text-on-ink' : 'border-border-control hover:border-success'
        )}
      >
        {done && <Check className="size-3" />}
      </button>
      <button
        type="button"
        data-task-id={t.taskId}
        onClick={(e) => onSelect(t, e)}
        className="flex min-w-0 flex-1 items-center gap-2 text-left"
      >
        <span
          className="size-2 shrink-0 rounded-full"
          style={{ backgroundColor: goalAccent(t.goalColorCode) }}
          aria-hidden
        />
        {isTopPriority(t.taskPriority) && <PriorityBadge priority="A" color={goalAccent(t.goalColorCode)} />}
        <span className={cn('min-w-0 flex-1 truncate text-[14px]', done && 'text-ink-3 line-through')}>
          {t.emoji ? `${t.emoji} ` : ''}
          {t.taskName}
        </span>
        {t.sourceType === 'ROUTINE' && <Repeat className="text-ink-4 size-3.5 shrink-0" aria-label="루틴" />}
        {t.missed && !done && (
          <span className="bg-danger-soft text-danger shrink-0 rounded px-1 text-[10px] font-bold">놓침</span>
        )}
        <span className="text-ink-3 w-[42px] shrink-0 text-right text-[12px] tabular-nums">
          {t.plannedStartAt ? hm(t.plannedStartAt) : '미정'}
        </span>
      </button>
    </li>
  )
}
