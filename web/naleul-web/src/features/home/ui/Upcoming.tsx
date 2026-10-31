'use client'

import { Fragment } from 'react'
import Link from 'next/link'
import { Flag } from 'lucide-react'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import { dayLabel } from '@/features/work/format'
import { goalAccent } from '@/lib/goalTone'
import { hm, todayKst, weekdayIndex, WEEKDAY_LABEL } from '@/features/timetable/time'
import type { TimeBlockTask, TimetableDay } from '@/features/timetable/types'
import type { DeadlineType, HomeDeadline } from '../api'
import { isRoutineTask, startMinutes, tasksOfDay } from '../today'
import { BODY, FILL } from './fill'
import { EmptyStateView, HE, useHomeAnalytics } from '../analytics'

/** 두 날짜(YYYY-MM-DD) 사이 날 수 */
const daysBetween = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000)

/**
 * 다가오는 일정 — 내일부터 6일 동안 시간이 정해진 일 (루틴은 매일 반복이라 빼요).
 * 약속(APPOINTMENT)은 강조해요.
 */
export function UpcomingSchedule({
  days,
  today,
  className,
}: {
  days: TimetableDay[]
  today: string
  className?: string
}) {
  const groups = days
    .map((d) => ({
      date: d.date,
      items: tasksOfDay(d)
        .filter((t) => !isRoutineTask(t) && t.taskStatus === 'TODO')
        .sort((a, b) => startMinutes(d.date, a) - startMinutes(d.date, b)),
    }))
    .filter((g) => g.items.length > 0)
  const { trackHome } = useHomeAnalytics()

  return (
    <Card data-home-section="upcoming" className={cn(FILL, className)}>
      <div className="mb-3 flex shrink-0 items-center justify-between gap-2">
        <h2 className="text-h4">다가오는 일정</h2>
        <span className="text-ink-3 text-[13px]">앞으로 6일</span>
      </div>
      <div className={BODY}>
        {groups.length === 0 ? (
          <p className="text-ink-3 py-4 text-sm">
            <EmptyStateView section="upcoming" />
            이번 주에 잡힌 일정이 없어요.
          </p>
        ) : (
          <div className="space-y-3">
            {groups.map((g) => (
              <section key={g.date}>
                <Link
                  href={`/calendar?view=day&date=${g.date}`}
                  onClick={() =>
                    trackHome(HE.upcomingDayClick, {
                      target_date: g.date,
                      days_from_today: daysBetween(today, g.date),
                      item_count: g.items.length,
                    })
                  }
                  className="text-ink-2 hover:text-ink mb-1 inline-block text-[13px] font-bold"
                >
                  {dayLabel(g.date, today)}
                  <span className="text-ink-4 ml-1 font-semibold">{g.items.length}개</span>
                </Link>
                <ul>
                  {g.items.map((t) => (
                    <ScheduleRow key={t.taskId} t={t} />
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </div>
    </Card>
  )
}

function ScheduleRow({ t }: { t: TimeBlockTask }) {
  const appointment = t.taskKind === 'APPOINTMENT'
  return (
    // 일정 줄은 눌러도 반응이 없어요 → 많이 눌리면 링크로 바꿀 후보 (home.dead_click)
    <li data-dead="upcoming_row" className="flex items-center gap-2 py-1">
      <span className="text-ink-3 w-[46px] shrink-0 text-[12px] tabular-nums">
        {t.plannedStartAt ? hm(t.plannedStartAt) : '미정'}
      </span>
      <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(t.goalColorCode) }} aria-hidden />
      <span className={cn('min-w-0 flex-1 truncate text-[14px]', appointment && 'font-semibold')}>
        {t.emoji ? `${t.emoji} ` : ''}
        {t.taskName}
      </span>
      {appointment && (
        <span className="bg-brand-soft text-text-brand shrink-0 rounded px-1.5 text-[10px] font-bold">약속</span>
      )}
    </li>
  )
}

const TYPE_LABEL: Record<DeadlineType, string> = { TASK: 'Task 마감', TOPIC: '세부 목표 마감', GOAL: '목표 종료' }

function ddayText(d: HomeDeadline) {
  if (d.dDay === 0) return '오늘'
  if (d.dDay === 1) return '내일'
  return d.dDay < 0 ? `${-d.dDay}일 지남` : `D-${d.dDay}`
}

/** 점 — Task 는 작은 점, 업무형 주제는 테두리 점, 목표 종료는 깃발 */
function Node({ d }: { d: HomeDeadline }) {
  const color = goalAccent(d.colorCode)
  if (d.type === 'GOAL') {
    return (
      <span
        className="relative z-[1] grid size-7 place-items-center rounded-full text-white"
        style={{ backgroundColor: color }}
      >
        <Flag className="size-3.5" />
      </span>
    )
  }
  if (d.type === 'TOPIC') {
    return (
      <span
        className="bg-surface relative z-[1] size-5 rounded-full border-[3px]"
        style={{ borderColor: d.overdue ? 'var(--color-danger)' : color }}
      />
    )
  }
  return (
    <span
      className="ring-surface relative z-[1] size-3 rounded-full ring-4"
      style={{ backgroundColor: d.overdue ? 'var(--color-danger)' : color }}
    />
  )
}

/**
 * 마감 로드맵 — 모든 목표의 마감을 날짜 순으로 세로 선 위에 (업무형 보드의 로드맵과 같은 모양).
 *  - Task 마감(앞으로 2주) · 업무형 주제 마감 · 목표형 종료일(앞으로 1년)
 *  - 마감이 지난 것(최근 7일)은 맨 위에 빨갛게, 그다음 "오늘" 줄
 */
export function DeadlineRoadmap({
  deadlines,
  failed,
  className,
}: {
  deadlines: HomeDeadline[] | undefined
  failed: boolean
  className?: string
}) {
  // 해가 바뀌는 마감은 "27년 · 금"처럼 해를 같이 적어요
  const year = todayKst().slice(0, 4)
  const list = deadlines ?? []
  const shown = list
  const todayAt = (() => {
    const i = shown.findIndex((d) => !d.overdue)
    return i === -1 ? shown.length : i
  })()
  const { trackHome } = useHomeAnalytics()

  return (
    <Card data-home-section="deadlines" className={cn(FILL, className)}>
      <div className="mb-3 flex shrink-0 items-center justify-between gap-2">
        <h2 className="text-h4">마감 로드맵</h2>
        {list.length > 0 && (
          <span className="text-ink-3 text-[13px]">{list.filter((d) => !d.overdue).length}개 남음</span>
        )}
      </div>
      {failed ? (
        <p className="text-ink-3 py-4 text-sm">마감을 불러오지 못했어요.</p>
      ) : !deadlines ? (
        <div className="bg-subtle h-32 animate-pulse rounded-xl" />
      ) : list.length === 0 ? (
        <p className="text-ink-3 py-4 text-sm">
          <EmptyStateView section="deadlines" />
          다가오는 마감이 없어요. 목표형 종료일 · 업무형 세부 목표 마감 · Task 마감이 여기 이어져요.
        </p>
      ) : (
        <>
          <div className={BODY}>
            <ol className="relative">
              {/* 세로 선 (날짜 칸 52px + 2px + 점 칸 28px 의 가운데) */}
              <span aria-hidden className="bg-line absolute top-2 bottom-2 left-[67px] w-0.5" />
              {shown.map((d, i) => {
                const newDate = i === 0 || shown[i - 1].date !== d.date
                return (
                  <Fragment key={`${d.type}-${d.id}`}>
                    {i === todayAt && <TodayLine />}
                    <li>
                      <Link
                        href={d.goalCategoryId ? `/goal/${d.goalCategoryId}` : `/calendar?view=day&date=${d.date}`}
                        onClick={() =>
                          trackHome(HE.deadlineItemClick, {
                            deadline_type: d.type,
                            d_day: d.dDay,
                            is_overdue: d.overdue,
                            goal_category_id: d.goalCategoryId ?? undefined,
                            destination: d.goalCategoryId ? 'goal' : 'calendar',
                          })
                        }
                        className="hover:bg-subtle/60 -mx-2 flex items-start gap-0 rounded-xl px-2 py-2"
                      >
                        <span className="w-[52px] shrink-0 pt-0.5 text-right">
                          {newDate && (
                            <>
                              <span
                                className={cn('block text-[13px] font-bold tabular-nums', d.overdue && 'text-danger')}
                              >
                                {Number(d.date.slice(5, 7))}/{Number(d.date.slice(8, 10))}
                              </span>
                              <span className="text-ink-4 block text-[11px]">
                                {d.date.slice(0, 4) !== year ? `${d.date.slice(2, 4)}년 · ` : ''}
                                {WEEKDAY_LABEL[weekdayIndex(d.date)]}
                              </span>
                            </>
                          )}
                        </span>
                        <span className="ml-[2px] grid w-7 shrink-0 place-items-center pt-0.5">
                          <Node d={d} />
                        </span>
                        <span className="ml-2.5 min-w-0 flex-1">
                          <span
                            className={cn(
                              'block truncate text-[14px]',
                              d.type === 'GOAL' ? 'font-bold' : 'font-semibold'
                            )}
                          >
                            {d.emoji ? `${d.emoji} ` : ''}
                            {d.title}
                          </span>
                          <span className="text-ink-3 block truncate text-[12px]">
                            {TYPE_LABEL[d.type]}
                            {d.type !== 'GOAL' && d.goalName ? ` · ${d.goalName}` : ''}
                            {d.type === 'TOPIC' && d.remaining ? ` · 남은 ${d.remaining}개` : ''}
                          </span>
                        </span>
                        <span
                          className={cn(
                            'ml-2 shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-bold tabular-nums',
                            d.overdue
                              ? 'bg-danger-soft text-danger'
                              : d.dDay <= 2
                                ? 'bg-warning-soft text-warning-ink'
                                : 'bg-subtle text-ink-2'
                          )}
                        >
                          {ddayText(d)}
                        </span>
                      </Link>
                    </li>
                  </Fragment>
                )
              })}
              {todayAt === shown.length && <TodayLine />}
            </ol>
          </div>
          <p className="text-ink-4 mt-2 flex shrink-0 flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
            <span className="flex items-center gap-1">
              <i className="bg-ink-3 inline-block size-2 rounded-full" /> Task
            </span>
            <span className="flex items-center gap-1">
              <i className="border-ink-3 inline-block size-2.5 rounded-full border-2" /> 업무형 세부 목표
            </span>
            <span className="flex items-center gap-1">
              <Flag className="size-3" /> 목표 종료일
            </span>
          </p>
        </>
      )}
    </Card>
  )
}

function TodayLine() {
  return (
    <li className="flex items-center py-1.5" aria-label="오늘">
      <span className="text-ink w-[52px] shrink-0 text-right text-[12px] font-bold">오늘</span>
      <span className="ml-[2px] grid w-7 shrink-0 place-items-center">
        <span className="bg-ink relative z-[1] size-2.5 rounded-full" />
      </span>
      <span className="bg-ink/40 ml-2.5 h-px flex-1" />
    </li>
  )
}
