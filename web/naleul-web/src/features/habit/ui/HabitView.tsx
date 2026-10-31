'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { Flame, Pause, Play } from 'lucide-react'
import {
  BarChart3,
  Bell,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ListChecks,
  Pencil,
  Plus,
  Trash2,
} from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { JAVA_DAYS, JAVA_DAY_LABEL } from '@/features/goal/format'
import { InlineConfirm } from '@/features/goal/edit/inline'
import type { GoalCategory } from '@/features/goal/api'
import { addDays, todayKst, weekdayIndex, WEEKDAY_LABEL } from '@/features/timetable/time'
import {
  useCreateHabit,
  useDeleteHabit,
  useHabitHome,
  usePauseHabit,
  useResumeHabit,
  useToggleHabitCheck,
  useUpdateHabit,
  type Habit,
  type HabitDayState,
} from '../api'
import { HABIT_SUGGESTIONS, HabitForm } from './HabitForm'
import { GoalTaskList } from '@/features/goal/ui/GoalTaskList'
import { HabitHeatmap } from './HabitHeatmap'
import { GoalScrollTitle, GoalTabBar, useGoalTab, type TabDef } from '@/features/goal/ui/GoalTabs'
import { CalendarCheck } from 'lucide-react'
import { ProgressBar } from '@/components/ui/ProgressBar'

// ─── 표시 도우미 ───────────────────────────────────────────────

/** "매일" · "평일" · "주말" · "월·수·금" */
export function daysLabel(h: Pick<Habit, 'daily' | 'repeatDays'>) {
  if (h.daily || h.repeatDays.length === 7) return '매일'
  const set = new Set(h.repeatDays)
  const weekdays = JAVA_DAYS.slice(0, 5)
  if (set.size === 5 && weekdays.every((d) => set.has(d))) return '평일'
  if (set.size === 2 && set.has('SATURDAY') && set.has('SUNDAY')) return '주말'
  return JAVA_DAYS.filter((d) => set.has(d))
    .map((d) => JAVA_DAY_LABEL[d])
    .join('·')
}

/** "오늘" · "어제" · "10월 11일 (토)" */
function dayTitle(ymd: string, today: string) {
  if (ymd === today) return '오늘'
  if (ymd === addDays(today, -1)) return '어제'
  return `${Number(ymd.slice(5, 7))}월 ${Number(ymd.slice(8, 10))}일 (${WEEKDAY_LABEL[weekdayIndex(ymd)]})`
}

/** 오늘부터 며칠 전인지 (오늘 0) */
const daysAgoOf = (day: string, today: string) =>
  Math.round((Date.parse(`${today}T00:00:00Z`) - Date.parse(`${day}T00:00:00Z`)) / 86_400_000)

/**
 * 추가 · 수정 · 삭제 · 쉬기 · 다시 시작 요청이 끝나면 habit.result.
 * 삭제하면 그 줄이 사라져서 mutate 콜백이 안 불릴 수 있어 mutateAsync 의 promise 로 보내요. 성공하면 true.
 */
function trackHabitResult(p: Promise<unknown>, habitAction: 'create' | 'update' | 'delete' | 'pause' | 'resume') {
  return p.then(
    () => {
      track('habit.result', { habit_action: habitAction, result: 'success' })
      return true
    },
    (e: unknown) => {
      track('habit.result', { habit_action: habitAction, result: 'fail', error_status: errorStatusOf(e) })
      return false
    }
  )
}

const DOT: Record<HabitDayState, string> = {
  DONE: 'bg-brand',
  MISSED: 'bg-heat-empty ring-line ring-1 ring-inset',
  TODO: 'ring-brand bg-surface ring-[1.5px] ring-inset',
  OFF: 'bg-subtle',
  PAUSED: 'bg-hatch ring-hatch-line ring-1 ring-inset',
  BEFORE_START: 'bg-subtle',
  FUTURE: 'bg-subtle',
}

// ─── 습관 한 줄 ────────────────────────────────────────────────

function HabitRow({ habit, day, isToday, daysAgo }: { habit: Habit; day: string; isToday: boolean; daysAgo: number }) {
  const toggle = useToggleHabitCheck(isToday ? undefined : day)
  const update = useUpdateHabit()
  const pause = usePauseHabit()
  const resume = useResumeHabit()
  const remove = useDeleteHabit()
  const [mode, setMode] = useState<'view' | 'edit' | 'delete'>('view')
  const paused = habit.status === 'PAUSED'

  if (mode === 'edit') {
    return (
      <li className="py-2">
        <HabitForm
          initial={habit}
          saving={update.isPending}
          submitLabel="저장"
          onCancel={() => setMode('view')}
          onSubmit={(v) =>
            void trackHabitResult(update.mutateAsync({ habitId: habit.habitId, ...v }), 'update').then(
              (ok) => ok && setMode('view')
            )
          }
          extra={
            <button
              type="button"
              onClick={() => {
                track('habit.delete_click', { habit_id: habit.habitId, streak: habit.streak })
                setMode('delete')
              }}
              className="text-danger hover:bg-danger-soft flex items-center gap-1 rounded-lg px-2 py-1.5 text-[13px] font-semibold"
            >
              <Trash2 className="size-3.5" />
              습관 지우기
            </button>
          }
        />
      </li>
    )
  }
  if (mode === 'delete') {
    return (
      <li className="py-2">
        <InlineConfirm
          message={`'${habit.name}' 습관을 지울까요?`}
          detail="지금까지 체크한 기록도 화면에서 사라져요. 잠깐 쉬려면 '쉬기'를 써 주세요."
          loading={remove.isPending}
          onCancel={() => {
            track('habit.delete_cancel', { habit_id: habit.habitId })
            setMode('view')
          }}
          onConfirm={() => {
            track('habit.delete_confirm', { habit_id: habit.habitId, streak: habit.streak })
            void trackHabitResult(remove.mutateAsync(habit.habitId), 'delete')
          }}
        />
      </li>
    )
  }

  return (
    <li className={cn('group flex items-center gap-3 py-3', paused && 'opacity-60')}>
      <button
        type="button"
        onClick={() => {
          const checkAction = habit.checked ? 'uncomplete' : 'complete'
          track('habit.check_toggle', {
            habit_id: habit.habitId,
            check_action: checkAction,
            days_ago: daysAgo,
            is_due_day: habit.scheduled,
            streak: habit.streak,
          })
          toggle.mutateAsync({ habit, day }).then(
            () =>
              track('habit.check_result', { habit_id: habit.habitId, check_action: checkAction, result: 'success' }),
            (e: unknown) =>
              track('habit.check_result', {
                habit_id: habit.habitId,
                check_action: checkAction,
                result: 'fail',
                error_status: errorStatusOf(e),
              })
          )
        }}
        disabled={paused}
        aria-pressed={habit.checked}
        aria-label={`${habit.name} ${habit.checked ? '체크 풀기' : '했어요'}`}
        className={cn(
          // 명세 8.6 — 둥근 체크 22 · 아이콘 14
          'grid size-[22px] shrink-0 place-items-center rounded-full border-[1.5px] transition-all active:scale-95',
          habit.checked
            ? 'border-brand bg-brand text-white'
            : 'border-border-control hover:border-brand hover:text-text-brand/40 text-transparent',
          paused && 'cursor-not-allowed'
        )}
      >
        <Check className="size-3.5" />
      </button>

      <div className="min-w-0 flex-1">
        <p
          className={cn('truncate text-[15px] font-semibold', habit.checked && 'text-ink-3 line-through decoration-2')}
        >
          {habit.emoji ? `${habit.emoji} ` : ''}
          {habit.name}
        </p>
        <p className="text-ink-3 mt-0.5 flex flex-wrap items-center gap-x-2 text-[12px]">
          <span>{daysLabel(habit)}</span>
          {habit.startTime && habit.endTime && (
            <span className="tabular-nums">
              {habit.startTime}–{habit.endTime}
            </span>
          )}
          {habit.remindTime && (
            <span className="flex items-center gap-0.5">
              <Bell className="size-3" />
              {habit.remindTime}
            </span>
          )}
          {habit.rate30 !== null && <span>30일 {habit.rate30}%</span>}
          {paused && <span className="text-warning-ink font-semibold">쉬는 중</span>}
        </p>
      </div>

      {habit.streak > 1 && (
        <span className="hidden shrink-0 items-center gap-0.5 text-[12px] font-semibold text-goal-orange-fg sm:flex">
          <Flame className="size-3.5" />
          {habit.streak}일
        </span>
      )}
      {/* 최근 7일 — 점 아래 작은 요일 범례 (기준 날짜로 끝나서 요일 순서가 날마다 달라요, 2026-10-24) */}
      <span className="hidden shrink-0 gap-[3px] sm:flex" aria-label="최근 7일">
        {habit.recent7.map((c) => (
          <span key={c.date} className="flex w-2.5 flex-col items-center gap-[3px]">
            <i
              title={`${Number(c.date.slice(5, 7))}/${Number(c.date.slice(8, 10))} (${WEEKDAY_LABEL[weekdayIndex(c.date)]})`}
              className={cn('block size-2.5 rounded-[3px]', DOT[c.state])}
            />
            <span
              aria-hidden
              className={cn('text-[9px] leading-none', c.date === day ? 'text-text-primary font-semibold' : 'text-ink-4')}
            >
              {WEEKDAY_LABEL[weekdayIndex(c.date)]}
            </span>
          </span>
        ))}
      </span>

      <span className="flex shrink-0 items-center transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-focus-within:opacity-100 [@media(hover:hover)]:group-hover:opacity-100">
        <IconButton
          label={`${habit.name} 수정`}
          onClick={() => {
            track('habit.edit_open', { habit_id: habit.habitId })
            setMode('edit')
          }}
        >
          <Pencil className="size-3.5" />
        </IconButton>
        {paused ? (
          <IconButton
            label={`${habit.name} 다시 시작`}
            onClick={() => {
              track('habit.pause_toggle', { habit_id: habit.habitId, pause_action: 'resume', streak: habit.streak })
              void trackHabitResult(resume.mutateAsync(habit.habitId), 'resume')
            }}
          >
            <Play className="size-3.5" />
          </IconButton>
        ) : (
          <IconButton
            label={`${habit.name} 쉬기`}
            onClick={() => {
              track('habit.pause_toggle', { habit_id: habit.habitId, pause_action: 'pause', streak: habit.streak })
              void trackHabitResult(pause.mutateAsync(habit.habitId), 'pause')
            }}
          >
            <Pause className="size-3.5" />
          </IconButton>
        )}
      </span>
    </li>
  )
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1.5"
    >
      {children}
    </button>
  )
}

// ─── 추가 ─────────────────────────────────────────────────────

/** dueCount: 그날 예정된 습관 수 (habit_count) */
function AddHabit({ existing, dueCount }: { existing: Habit[]; dueCount: number }) {
  const [open, setOpen] = useState(false)
  const create = useCreateHabit()
  const names = new Set(existing.map((h) => h.name))
  const suggestions = HABIT_SUGGESTIONS.filter((s) => !names.has(s.name))

  return (
    <div className="mt-4">
      {open ? (
        <HabitForm
          saving={create.isPending}
          submitLabel="습관 추가"
          onCancel={() => setOpen(false)}
          onSubmit={(v) => {
            // [UX] 같은 이름도 막지 않고 만들어져요
            if (names.has(v.name)) track('habit.duplicate_add', { habit_count: dueCount })
            void trackHabitResult(create.mutateAsync(v), 'create').then((ok) => ok && setOpen(false))
          }}
        />
      ) : (
        <button
          type="button"
          onClick={() => {
            track('habit.add_click', { habit_count: dueCount })
            setOpen(true)
          }}
          className="border-border-subtle text-ink-3 hover:border-line-strong hover:text-ink-2 flex w-full items-center justify-center gap-1.5 rounded-2xl border border-dashed py-2.5 text-sm font-semibold transition-colors"
        >
          <Plus className="size-4" />
          습관 추가
        </button>
      )}
      {suggestions.length > 0 && !open && (
        <div className="mt-3">
          <p className="text-ink-3 mb-1.5 text-[12px] font-medium">눌러서 바로 추가</p>
          <div className="flex flex-wrap gap-1.5">
            {suggestions.slice(0, 10).map((s, i) => (
              <button
                key={s.name}
                type="button"
                disabled={create.isPending}
                onClick={() => {
                  track('habit.suggestion_click', { suggestion_index: i, habit_count: dueCount })
                  void trackHabitResult(
                    create.mutateAsync({ name: s.name, emoji: s.emoji, repeatDays: s.repeatDays ?? [] }),
                    'create'
                  )
                }}
                className="border-line hover:border-line-strong hover:bg-subtle h-8 rounded-full border px-3 text-[13px] disabled:opacity-50"
              >
                {s.emoji} {s.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ─── 화면 ──────────────────────────────────────────────────────

/**
 * 기본 생활 습관 (goalMode = HABIT) — 목표 상세 대신 이 화면을 보여줘요.
 *  - 위: 날짜(어제 · 그제도 체크할 수 있어요) + 오늘 몇 개 했는지
 *  - 왼쪽: 해야 하는 습관 체크 목록 · 오늘은 안 해도 되는 습관 · 쉬는 습관
 *  - 오른쪽: 습관별 잔디 (‹ › 로 한 달씩, 세로 요일 × 가로 주)
 *  - 아래: 일회성 할 일 (병원 가기 · 친구 만나기) — 습관과 달리 한 번 하고 끝나요
 */
/** 기본 생활 습관 탭 (명세 12.5) — 오늘(체크) · 할 일 · 진행(잔디) */
type HabitTab = 'today' | 'tasks' | 'progress'
const HABIT_TABS: TabDef<HabitTab>[] = [
  { value: 'today', label: '루틴', icon: CalendarCheck }, // 2026-10: '오늘' → '루틴' (값은 분석 연속성 때문에 today 그대로)
  { value: 'tasks', label: '할 일', icon: ListChecks },
  { value: 'progress', label: '진행', icon: BarChart3 },
]

export function HabitView({ goal }: { goal: GoalCategory }) {
  const [tab, setTab] = useGoalTab(HABIT_TABS)
  const today = todayKst()
  const [day, setDay] = useState(today)
  const isToday = day === today
  const { data, isPending, isError, error, errorUpdatedAt, refetch } = useHabitHome(isToday ? undefined : day)
  const daysAgo = daysAgoOf(day, today)
  const [showOff, setShowOff] = useState(false)

  const habits = data?.habits ?? []
  const due = habits.filter((h) => h.status === 'ACTIVE' && h.scheduled)
  const off = habits.filter((h) => h.status === 'ACTIVE' && !h.scheduled)
  const paused = habits.filter((h) => h.status === 'PAUSED')
  const done = due.filter((h) => h.checked).length
  const ratio = due.length ? done / due.length : 0

  // ── 분석 ──
  // 습관을 처음 다 불러온 뒤 한 번
  const viewed = useRef(false)
  useEffect(() => {
    if (!data || viewed.current) return
    viewed.current = true
    track('habit.pageview', {
      goal_category_id: goal.goalCategoryId,
      habit_count: due.length,
      due_today_count: due.length,
      done_count: done,
      paused_count: paused.length,
    })
  }, [data, goal.goalCategoryId, due.length, done, paused.length])

  // '모두 챙겼어요' — 그날 화면에서 체크해서 다 채웠을 때 처음 한 번 (불러올 때 이미 다 한 날은 빼요)
  const allDone = data ? due.length > 0 && done === due.length : null
  const lastAllDone = useRef<string | null>(null)
  const allDoneSent = useRef(new Set<string>())
  useEffect(() => {
    if (allDone === null) return
    if (allDone && lastAllDone.current === `${day}:false` && !allDoneSent.current.has(day)) {
      allDoneSent.current.add(day)
      track('habit.all_done_view', { days_ago: daysAgo, due_today_count: due.length })
    }
    lastAllDone.current = `${day}:${allDone}`
  }, [allDone, day, daysAgo, due.length])

  // '습관을 불러오지 못했어요' 가 보일 때 (날짜를 바꿔 다시 실패해도 한 번씩)
  const lastFail = useRef('')
  useEffect(() => {
    if (!isError) return
    const key = `${day}:${errorUpdatedAt}`
    if (lastFail.current === key) return
    lastFail.current = key
    track('habit.load_fail', { error_status: errorStatusOf(error), days_ago: daysAgo })
  }, [isError, error, errorUpdatedAt, day, daysAgo])

  const changeDay = (next: string, action: 'prev' | 'next' | 'today') => {
    track('habit.date_change', { date_action: action, days_ago: daysAgoOf(next, today) })
    setDay(next)
  }

  return (
    <div className="goal-detail">
      <GoalScrollTitle title={`${goal.emoji ? `${goal.emoji} ` : ''}${goal.goalCategoryName}`} />
      <div id="goal-summary">
      <p className="text-ink-3 text-[13px]">
        <Link href="/goal" className="hover:text-ink" onClick={() => track('habit.breadcrumb_click')}>
          목표
        </Link>{' '}
        › {goal.goalCategoryName}
      </p>
      <h1 className="mt-1 text-h2 sm:text-h1">
        {goal.emoji ? `${goal.emoji} ` : ''}
        {goal.goalCategoryName}
      </h1>
      <p className="text-ink-3 mt-1.5 text-sm">
        했는지만 체크하는 생활 습관과, 병원 가기처럼 한 번만 할 일을 여기서 챙겨요.
      </p>
      </div>

      <GoalTabBar tabs={HABIT_TABS} tab={tab} onChange={(t) => setTab(t, goal)} />

      {tab === 'today' && (
        <div id="goal-panel-today" role="tabpanel" aria-labelledby="goal-tab-today" className="mt-5 max-w-3xl">
        <Card className="p-5 sm:p-6">
          {/* 날짜 + 진행 */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => changeDay(addDays(day, -1), 'prev')}
              aria-label="전날"
              className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1.5"
            >
              <ChevronLeft className="size-5" />
            </button>
            <h2 className="min-w-[96px] text-center text-h4">{dayTitle(day, today)}</h2>
            <button
              type="button"
              onClick={() => changeDay(addDays(day, 1), 'next')}
              disabled={isToday}
              aria-label="다음 날"
              className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1.5 disabled:opacity-30"
            >
              <ChevronRight className="size-5" />
            </button>
            {!isToday && (
              <button
                type="button"
                onClick={() => changeDay(today, 'today')}
                className="text-text-brand text-[13px] font-semibold"
              >
                오늘로
              </button>
            )}
            <span className="ml-auto text-[15px] font-bold tabular-nums">
              {done}
              <span className="text-ink-3 font-semibold"> / {due.length}</span>
            </span>
          </div>
          <ProgressBar value={ratio} className="mt-3" />
          {due.length > 0 && done === due.length && (
            <p className="text-success mt-2 text-[13px] font-semibold">
              {isToday ? '오늘 할 습관을 모두 챙겼어요 🎉' : '이날 할 습관을 모두 챙겼어요'}
            </p>
          )}

          {isPending ? (
            <div className="grid h-40 place-items-center">
              <Spinner className="text-ink-3 size-5" />
            </div>
          ) : isError ? (
            <div className="py-10 text-center">
              <p className="font-bold">습관을 불러오지 못했어요</p>
              <p className="text-ink-3 mt-1 text-sm">{error.message}</p>
              <Button
                className="mt-4"
                onClick={() => {
                  track('habit.retry_click', { days_ago: daysAgo })
                  refetch()
                }}
              >
                다시 시도
              </Button>
            </div>
          ) : habits.length === 0 ? (
            <div className="py-8 text-center">
              <p className="text-[16px] font-bold">아직 습관이 없어요</p>
              <p className="text-ink-3 mt-1 text-sm">영양제 · 물 마시기처럼 매일 챙길 것을 아래에서 골라 보세요.</p>
            </div>
          ) : (
            <>
              {due.length > 0 ? (
                <ul className="divide-line mt-2 divide-y">
                  {due.map((h) => (
                    <HabitRow key={h.habitId} habit={h} day={day} isToday={isToday} daysAgo={daysAgo} />
                  ))}
                </ul>
              ) : (
                <p className="text-ink-3 py-6 text-center text-sm">{dayTitle(day, today)}은 해야 하는 습관이 없어요.</p>
              )}

              {off.length > 0 && (
                <div className="border-line mt-2 border-t pt-3">
                  <button
                    type="button"
                    onClick={() => {
                      track('habit.off_list_toggle', { days_ago: daysAgo, expanded: !showOff, item_count: off.length })
                      setShowOff((v) => !v)
                    }}
                    aria-expanded={showOff}
                    className="text-ink-3 hover:text-ink-2 flex items-center gap-1 text-[13px] font-semibold"
                  >
                    {dayTitle(day, today)}은 안 해도 되는 습관 {off.length}
                    <ChevronDown className={cn('size-4 transition-transform', showOff && 'rotate-180')} />
                  </button>
                  {showOff && (
                    <ul className="divide-line divide-y">
                      {off.map((h) => (
                        <HabitRow key={h.habitId} habit={h} day={day} isToday={isToday} daysAgo={daysAgo} />
                      ))}
                    </ul>
                  )}
                </div>
              )}

              {paused.length > 0 && (
                <div className="border-line mt-2 border-t pt-3">
                  <p className="text-ink-3 text-[13px] font-semibold">쉬는 중 {paused.length}</p>
                  <ul className="divide-line divide-y">
                    {paused.map((h) => (
                      <HabitRow key={h.habitId} habit={h} day={day} isToday={isToday} daysAgo={daysAgo} />
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}

          {!isPending && !isError && <AddHabit existing={habits} dueCount={due.length} />}
        </Card>

        </div>
      )}

      {/* 진행: 습관별 잔디 (한 달씩) */}
      {tab === 'progress' && (
        <div id="goal-panel-progress" role="tabpanel" aria-labelledby="goal-tab-progress" className="mt-5">
          {isPending ? (
            <div className="grid h-40 place-items-center">
              <Spinner className="text-ink-3 size-5" />
            </div>
          ) : !isError ? (
            <HabitHeatmap habits={habits} today={today} />
          ) : null}
        </div>
      )}

      {tab === 'tasks' && (
      <div id="goal-panel-tasks" role="tabpanel" aria-labelledby="goal-tab-tasks" className="mt-5">
        <GoalTaskList
          goal={goal}
          title="할 일"
          hint="병원 가기 · 친구 만나기처럼 한 번만 할 일이에요. 날짜만 정하면 캘린더 빈 시간에 넣어 드려요."
          addLabel="할 일 추가"
          emptyText="아직 할 일이 없어요. 위에서 바로 추가해 보세요."
          initialPeriod="ALL"
          surface="habit"
        />
      </div>
      )}
    </div>
  )
}
