'use client'

import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { Flag } from 'lucide-react'
import { CalendarDays, Check } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { dateRelationOf, errorStatusOf } from '@/features/home/analytics'
import { useMonthlyTimetable } from '../api'
import { shownTime } from '../layout'
import { goalAccent, goalBg, goalFg } from '@/lib/goalTone'
import { GoalTimeStats } from './GoalTimeStats'
import { WEEKDAY_LABEL, dDay, dayOfMonth, formatMonthDay, hm, monthOf, monthWeeks, weekdayIndex, yearOf } from '../time'
import type { MonthlyDay, TimeBlockTask, TimetableDay } from '../types'
import { isTopPriority, PriorityBadge } from './Priority'

interface Props {
  /** 선택한 날짜 (이 날짜가 속한 달을 보여줘요) */
  date: string
  today: string
  /** 선택한 날짜의 상세 (GET /timetable?startDate=date&endDate=date) */
  dayDetail?: TimetableDay
  dayLoading: boolean
  onSelectDate: (date: string) => void
  onOpenDay: (date: string) => void
  onSelectTask: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onToggleComplete: (t: TimeBlockTask) => void
  togglingId?: number | null
}

/**
 * 월간 보기 (명세 3-5, 8)
 *  - 날짜 칸: 실행률 막대, Task 칩 3개 + "+n개 더보기", 마일스톤 🏁
 *  - 날짜를 누르면 오른쪽 패널에 그날 실행률 · 이번 주 마감 · 시간순 Task (체크 가능)
 *  - 날짜를 두 번 누르거나 "일간 보기"를 누르면 그날 일간 보기
 */
export function MonthView({
  date,
  today,
  dayDetail,
  dayLoading,
  onSelectDate,
  onOpenDay,
  onSelectTask,
  onToggleComplete,
  togglingId,
}: Props) {
  const year = yearOf(date)
  const month = monthOf(date)
  const { data, isPending, isError, error, refetch } = useMonthlyTimetable(year, month)
  // placeholderData 로 이전 달 데이터가 잠깐 남아 있을 수 있어 해당 달인지 확인
  const current = data && data.year === year && data.month === month ? data : undefined
  const byDate = new Map((current?.days ?? []).map((d) => [d.date, d]))
  const weeks = monthWeeks(year, month)
  // 모바일(lg 미만): 날짜를 누르면 아래에서 올라오는 시트로 그날 실행률 · 할 일 (명세 9.9) — 넓은 화면은 옆 패널
  const [sheetOpen, setSheetOpen] = useState(false)

  // calendar_month.load_fail — '다시 시도'가 보이게 될 때 한 번
  const loadFailed = isError && !current
  const errorStatus = errorStatusOf(error)
  const loadFailSent = useRef(false)
  useEffect(() => {
    if (!loadFailed) {
      loadFailSent.current = false
      return
    }
    if (loadFailSent.current) return
    loadFailSent.current = true
    track('calendar_month.load_fail', { view_date: date, error_status: errorStatus })
  }, [loadFailed, date, errorStatus])

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="flex min-w-0 flex-col gap-3">
        <Card className="overflow-hidden p-0">
          {/* 디자인 시스템 Calendar/MonthGrid (명세 8.5) — 요일 줄 h28 · Caption/MD · 아래 1px border/soft */}
          <div className="border-border-soft grid grid-cols-7 border-b">
            {WEEKDAY_LABEL.map((w, i) => (
              <div
                key={w}
                className={cn(
                  'text-caption-md flex h-7 items-center justify-center',
                  i === 6 ? 'text-calendar-holiday' : i === 5 ? 'text-calendar-saturday' : 'text-text-secondary'
                )}
              >
                {w}
              </div>
            ))}
          </div>

          {isError && !current ? (
            <div className="grid min-h-[320px] place-items-center p-8 text-center">
              <div>
                <p className="font-bold">월간 TimeTable을 불러오지 못했어요</p>
                <Button
                  variant="secondary"
                  className="mt-4"
                  onClick={() => {
                    track('calendar_month.retry_click', { view_date: date })
                    refetch()
                  }}
                >
                  다시 시도
                </Button>
              </div>
            </div>
          ) : (
            <div className="relative" aria-busy={isPending}>
              {isPending && (
                <div className="bg-surface/60 absolute inset-0 z-10 grid place-items-center">
                  <Spinner className="text-ink-3 size-6" />
                </div>
              )}
              {weeks.map((week) => (
                <div key={week[0]} className="border-border-soft grid grid-cols-7 border-b last:border-b-0">
                  {week.map((d) => (
                    <DayCell
                      key={d}
                      date={d}
                      inMonth={monthOf(d) === month}
                      day={byDate.get(d)}
                      today={today}
                      selected={d === date}
                      onSelect={() => {
                        track('calendar_month.day_select', {
                          view_date: d,
                          date_relation: dateRelationOf(d, today),
                          task_count: byDate.get(d)?.taskCount ?? 0,
                        })
                        onSelectDate(d)
                        if (window.matchMedia('(max-width: 1023.98px)').matches) setSheetOpen(true)
                      }}
                      onOpen={() => onOpenDay(d)}
                    />
                  ))}
                </div>
              ))}
            </div>
          )}
        </Card>
        {/* 이 달 목표별 실천 시간 — 참고용 작은 막대 (2026-10) */}
        <GoalTimeStats
          start={`${year}-${String(month).padStart(2, '0')}-01`}
          end={`${year}-${String(month).padStart(2, '0')}-${String(new Date(Date.UTC(year, month, 0)).getUTCDate()).padStart(2, '0')}`}
          viewMode="month"
          periodLabel={`${month}월`}
        />
      </div>

      {/* 넓은 화면: 옆 패널 */}
      <div className="max-lg:hidden lg:contents">
        <DayPanel
          date={date}
          today={today}
          summary={byDate.get(date)}
          detail={dayDetail}
          loading={dayLoading}
          deadlines={current?.deadlinesThisWeek ?? []}
          onOpenDay={onOpenDay}
          onSelectTask={onSelectTask}
          onToggleComplete={onToggleComplete}
          togglingId={togglingId}
        />
      </div>

      {/* 모바일: 날짜를 누르면 바텀 시트 */}
      <div className="lg:hidden">
        <Modal open={sheetOpen} onClose={() => setSheetOpen(false)}>
          <DayPanel
            inSheet
            date={date}
            today={today}
            summary={byDate.get(date)}
            detail={dayDetail}
            loading={dayLoading}
            deadlines={current?.deadlinesThisWeek ?? []}
            onOpenDay={onOpenDay}
            onSelectTask={onSelectTask}
            onToggleComplete={onToggleComplete}
            togglingId={togglingId}
          />
        </Modal>
      </div>
    </div>
  )
}

function DayCell({
  date,
  inMonth,
  day,
  today,
  selected,
  onSelect,
  onOpen,
}: {
  date: string
  inMonth: boolean
  day?: MonthlyDay
  today: string
  selected: boolean
  onSelect: () => void
  onOpen: () => void
}) {
  const wd = weekdayIndex(date)
  const isToday = date === today
  const past = date <= today
  const rate = day?.rate ?? null

  return (
    <button
      type="button"
      data-month-date={date}
      onClick={onSelect}
      onDoubleClick={onOpen}
      aria-pressed={selected}
      aria-label={`${formatMonthDay(date)}${day?.taskCount ? ` Task ${day.taskCount}개` : ''}${rate != null && past ? ` 실행률 ${rate}%` : ''}`}
      className={cn(
        // 칸: 같은 너비 · h56 · 모서리/바탕 없음 · 오른쪽 1px border/soft (마지막 열 없음) · 안쪽 5 · 간격 4
        'border-border-soft relative flex min-h-14 min-w-0 flex-col gap-1 border-r p-[5px] text-left transition-colors last:border-r-0 sm:min-h-[118px] sm:p-2',
        inMonth && !isToday && 'hover:bg-bg-sunken',
        // 오늘: ai/tint-soft 60% + 2px text/brand 안쪽 테두리
        isToday && 'bg-ai-tint-soft/60 ring-text-brand ring-2 ring-inset',
        selected && !isToday && 'ring-border-focus ring-1 ring-inset'
      )}
    >
      <div className="flex items-center justify-between gap-1">
        <span
          className={cn(
            'text-body-sm grid size-[22px] shrink-0 place-items-center rounded-full font-medium',
            isToday
              ? 'bg-calendar-today-bg text-calendar-today-fg'
              : !inMonth
                ? 'text-text-placeholder'
                : wd === 6
                  ? 'text-calendar-holiday'
                  : wd === 5
                    ? 'text-calendar-saturday'
                    : 'text-text-primary'
          )}
        >
          {dayOfMonth(date)}
        </span>
        {/* 실행률: 지난 날·오늘만 (앞으로 올 날은 개수) */}
        {inMonth && day && day.taskCount > 0 && (
          <span className="text-ink-3 hidden truncate text-[11px] font-semibold tabular-nums sm:inline">
            {past && rate != null ? `${rate}%` : `${day.taskCount}개`}
          </span>
        )}
      </div>

      {/* 실행률 막대 h3 · 트랙 teal tint · 채움 민트 (Task 없는 날은 없음) */}
      {inMonth && day && day.taskCount > 0 && past && rate != null && (
        <span className="bg-progress-track block h-[3px] w-full overflow-hidden rounded-[2px]" aria-hidden>
          <span className="bg-progress-fill block h-full rounded-[2px]" style={{ width: `${Math.max(rate, 4)}%` }} />
        </span>
      )}

      {inMonth && day && (
        <>
          {day.milestones.map((m) => (
            <span
              key={m.milestoneId}
              className="hidden truncate rounded px-1 py-0.5 text-[11px] font-semibold sm:block"
              style={{ color: goalFg(m.colorCode), backgroundColor: goalBg(m.colorCode) }}
            >
              🏁 {m.title}
            </span>
          ))}
          {/* 넓은 화면: 칩 / 좁은 화면: 점 */}
          <span className="hidden min-w-0 flex-col gap-0.5 sm:flex">
            {day.chips.map((c) => (
              <span key={c.taskId} className="flex min-w-0 items-center gap-1 text-[11px] leading-tight">
                <span className="size-1.5 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(c.colorCode) }} />
                <span className={cn('truncate', c.completed ? 'text-ink-4 line-through' : 'text-ink-2')}>
                  {c.title}
                </span>
              </span>
            ))}
            {day.moreCount > 0 && <span className="text-ink-3 text-[11px]">+{day.moreCount}개 더보기</span>}
          </span>
          {/* 좁은 화면: Task 칩 3개 (h14 · 모서리 3 · 목표 연한 바탕 + 왼쪽 2px 색 막대) + "+N" (명세 9.9) */}
          <span className="flex min-w-0 flex-col gap-0.5 sm:hidden" aria-hidden>
            {day.chips.slice(0, 3).map((c) => (
              <span
                key={c.taskId}
                className={cn(
                  'block h-3.5 min-w-0 truncate rounded-[3px] pr-0.5 pl-1 text-[9px] leading-[14px] font-medium',
                  c.completed && 'line-through opacity-50'
                )}
                style={{
                  backgroundColor: goalBg(c.colorCode),
                  color: goalFg(c.colorCode),
                  boxShadow: `inset 2px 0 0 ${goalAccent(c.colorCode)}`,
                }}
              >
                {c.title}
              </span>
            ))}
            {Math.max(day.chips.length - 3, 0) + day.moreCount > 0 && (
              <span className="text-caption-sm text-text-tertiary leading-none">
                +{Math.max(day.chips.length - 3, 0) + day.moreCount}
              </span>
            )}
          </span>
        </>
      )}
    </button>
  )
}

function DayPanel({
  date,
  today,
  summary,
  detail,
  loading,
  deadlines,
  onOpenDay,
  onSelectTask,
  onToggleComplete,
  togglingId,
  inSheet = false,
}: {
  /** 바텀 시트 안 (카드 테두리 · 고정 위치 없이) */
  inSheet?: boolean
  date: string
  today: string
  summary?: MonthlyDay
  detail?: TimetableDay
  loading: boolean
  deadlines: { type: 'TASK' | 'MILESTONE'; id: number; title: string; date: string; colorCode?: string | null }[]
  onOpenDay: (date: string) => void
  onSelectTask: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onToggleComplete: (t: TimeBlockTask) => void
  togglingId?: number | null
}) {
  // 시간순 (시간 미정은 맨 뒤)
  const tasks = detail
    ? [...detail.tasks].sort((a, b) => (shownTime(a)?.startAt ?? '').localeCompare(shownTime(b)?.startAt ?? ''))
    : []
  const unscheduled = detail?.unscheduledTasks ?? []
  const stats = detail?.stats
  const rate = stats?.rate ?? null

  return (
    <Card
      className={cn(
        'flex flex-col gap-5 p-5 lg:sticky lg:top-6 lg:max-h-[calc(100dvh-48px)] lg:overflow-y-auto',
        inSheet && 'border-0 bg-transparent p-0 shadow-none'
      )}
      aria-label="선택한 날짜"
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-h4">
          {formatMonthDay(date)}
          {date === today && <span className="text-text-brand ml-1.5 text-[13px]">오늘</span>}
        </h3>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            track('calendar_month.open_day_click', { view_date: date })
            onOpenDay(date)
          }}
        >
          <CalendarDays className="size-3.5" />
          일간 보기
        </Button>
      </div>

      {/* 실행률 */}
      <div className={cn('bg-bg-sunken p-4', inSheet ? 'rounded-xl' : 'rounded-2xl')}>
        <div className="flex items-end justify-between">
          <div>
            <p className="text-caption-md text-text-secondary">{date > today ? '계획된 Task' : '실행률'}</p>
            <p className="mt-0.5 text-[26px] leading-none font-bold tabular-nums">
              {date > today ? `${stats?.total ?? summary?.taskCount ?? 0}개` : rate != null ? `${rate}%` : '–'}
            </p>
          </div>
          {stats && stats.total > 0 && (
            <p className="text-caption-md text-text-secondary tabular-nums">
              완료 {stats.completed} / 전체 {stats.total}
              {stats.missed > 0 && <span className="text-danger"> · 놓침 {stats.missed}</span>}
              {!!stats.carriedOver && <span className="text-ink-4"> (다음 날로 넘김 {stats.carriedOver} 포함)</span>}
            </p>
          )}
        </div>
        {date <= today && rate != null && (
          <span className="bg-progress-track mt-3 block h-1.5 overflow-hidden rounded-full">
            <span className="bg-progress-fill block h-full rounded-full" style={{ width: `${Math.max(rate, 3)}%` }} />
          </span>
        )}
      </div>

      {/* 그날 마일스톤 */}
      {summary && summary.milestones.length > 0 && (
        <section>
          <h4 className="text-ink-3 mb-2 text-xs font-semibold">마일스톤</h4>
          <ul className="space-y-1.5">
            {summary.milestones.map((m) => (
              <li key={m.milestoneId} className="flex items-center gap-2 text-sm">
                <Flag className="size-3.5 shrink-0" style={{ color: goalAccent(m.colorCode) }} />
                <span className="truncate">{m.title}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* 이번 주 마감 */}
      {deadlines.length > 0 && (
        <section>
          <h4 className="text-ink-3 mb-2 text-xs font-semibold">이번 주 마감</h4>
          <ul className="space-y-1.5">
            {deadlines.map((d) => (
              <li key={`${d.type}-${d.id}`} className="flex items-center gap-2 text-sm">
                {d.type === 'MILESTONE' ? (
                  <Flag className="size-3.5 shrink-0" style={{ color: goalAccent(d.colorCode) }} />
                ) : (
                  <span className="mx-1 size-2 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(d.colorCode) }} />
                )}
                <span className="min-w-0 flex-1 truncate">{d.title}</span>
                <span
                  className={cn(
                    'shrink-0 text-xs font-semibold tabular-nums',
                    d.date <= today ? 'text-danger' : 'text-ink-3'
                  )}
                >
                  {dDay(d.date, today)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* 그날 Task (시간순, 체크 가능) */}
      <section>
        <h4 className="text-ink-3 mb-2 text-xs font-semibold">할 일</h4>
        {loading && !detail ? (
          <div className="grid h-24 place-items-center">
            <Spinner className="text-ink-4 size-5" />
          </div>
        ) : tasks.length + unscheduled.length === 0 ? (
          <p className="text-ink-3 bg-subtle rounded-xl px-4 py-6 text-center text-sm">이날은 Task가 없어요.</p>
        ) : (
          <ul className="space-y-1">
            {[...tasks, ...unscheduled].map((t) => (
              <TaskRow
                key={t.taskId}
                task={t}
                busy={togglingId === t.taskId}
                onSelect={(task, e) => {
                  track('calendar_month.task_click', { task_id: task.taskId, task_status: task.taskStatus })
                  onSelectTask(task, e)
                }}
                onToggle={(task) => {
                  track('calendar_month.task_check', {
                    task_id: task.taskId,
                    check_action: task.taskStatus === 'COMPLETED' ? 'uncomplete' : 'complete',
                    date_relation: dateRelationOf(date, today),
                  })
                  onToggleComplete(task)
                }}
              />
            ))}
          </ul>
        )}
      </section>
    </Card>
  )
}

function TaskRow({
  task: t,
  busy,
  onSelect,
  onToggle,
}: {
  task: TimeBlockTask
  busy: boolean
  onSelect: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onToggle: (t: TimeBlockTask) => void
}) {
  const done = t.taskStatus === 'COMPLETED'
  return (
    <li className="hover:bg-subtle/70 flex items-center gap-2.5 rounded-xl px-2 py-1.5">
      <button
        type="button"
        role="checkbox"
        aria-checked={done}
        aria-label={`${t.taskName} ${done ? '완료 취소' : '완료'}`}
        disabled={busy}
        onClick={() => onToggle(t)}
        className={cn(
          'grid size-5 shrink-0 place-items-center rounded-[6px] border-[1.5px] transition-colors disabled:opacity-50',
          done ? 'border-success bg-success text-on-ink' : 'border-border-control hover:border-success bg-surface'
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
        <span className="text-ink-3 w-[52px] shrink-0 text-xs tabular-nums">
          {hm(shownTime(t)?.startAt) || '시간 미정'}
        </span>
        <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(t.goalColorCode) }} />
        {isTopPriority(t.taskPriority) && <PriorityBadge priority="A" color={goalAccent(t.goalColorCode)} />}
        <span className={cn('min-w-0 flex-1 truncate text-sm', done && 'text-ink-4 line-through')}>
          {t.emoji ? `${t.emoji} ` : ''}
          {t.taskName}
        </span>
        {t.missed && <span className="text-danger shrink-0 text-[11px] font-semibold">놓침</span>}
      </button>
    </li>
  )
}
