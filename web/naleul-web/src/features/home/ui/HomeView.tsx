'use client'

import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react'
import { PencilLine } from 'lucide-react'
import { ChevronLeft, ChevronRight, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { iconButtonClass } from '@/components/ui/IconButton'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useHabitHome, useToggleHabitCheck, type Habit } from '@/features/habit/api'
import { useTimetable, useToggleComplete } from '@/features/timetable/api'
import { addDays, formatMinutes, monthOf, dayOfMonth, nowKst, weekdayIndex } from '@/features/timetable/time'
import type { TimeBlockTask } from '@/features/timetable/types'
import { usePageEngagement } from '@/components/analytics/usePageEngagement'
import { track, type EventProps } from '@/lib/client/analytics'
import { useHomeToday } from '../api'
import { dateRelationOf, errorStatusOf, HE, HomeAnalyticsProvider, layoutBreakpoint } from '../analytics'
import { agendaOf, inLabel, isDone, isRoutineTask, startMinutes, tasksOfDay } from '../today'
import { HomeDayCalendar } from './HomeDayCalendar'
import { TodayRoutines, TodayTasks } from './TodayChecklist'
import { RetroPanel, hasRecord, type DiscardReason } from './TodayRetro'
import { DeadlineRoadmap, UpcomingSchedule } from './Upcoming'

const WEEKDAY_FULL = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일']

/**
 * 1분마다 바뀌는 지금 (한국 시간).
 * onDayChange: 날짜가 바뀌는 순간 화면이 바뀌기 "전에" 불려요 (회고 칸이 사라지기 전에 이유를 적어 두려고)
 */
function useNow(onDayChange?: (from: string, to: string) => void) {
  const [now, setNow] = useState(() => nowKst())
  const cb = useRef(onDayChange)
  useEffect(() => {
    cb.current = onDayChange
  })
  useEffect(() => {
    let lastDate = nowKst().date
    const id = setInterval(() => {
      const next = nowKst()
      if (next.date !== lastDate) cb.current?.(lastDate, next.date)
      lastDate = next.date
      setNow(next)
    }, 60_000)
    return () => clearInterval(id)
  }, [])
  return now
}

/**
 * 홈 — 오늘 해야 할 것만 한곳에.
 *  1. 오늘 요약 (얼마나 했는지 · 지금/다음 할 일)
 *  2. 오늘 일간 캘린더 (캘린더 화면과 같은 계획 | 실제) · 할 일 · 루틴과 생활 습관 (바로 체크)
 *  3. 다가오는 일정 (6일) · 마감 로드맵 (Task · 업무형 주제 · 목표형 종료일을 날짜순 세로 선으로)
 *  넓은 화면에선 스크롤 없이 한 화면: 요약 한 줄 · 캘린더(1/3) · 할 일 | 루틴·습관 / 다가오는 일정 | 마감 로드맵
 *  오늘 회고는 요약의 "오늘 회고" 버튼 → 오른쪽 패널 (캘린더의 Task 추가 패널처럼)
 */
export function HomeView({ userName }: { userName: string }) {
  // 날짜 ‹ › 로 고른 날 (null = 오늘을 따라가요 — 자정이 지나면 저절로 다음 날)
  const [picked, setPicked] = useState<string | null>(null)
  const pickedRef = useRef(picked)
  useEffect(() => {
    pickedRef.current = picked
  })
  // 회고 칸이 사라질 때 읽는 "버려진 이유"
  const discardReason = useRef<DiscardReason>('page_leave')
  // 오늘을 보던 채로 자정이 지나 다음 날로 바뀜
  const now = useNow((from, to) => {
    if (pickedRef.current !== null) return
    discardReason.current = 'day_rollover'
    track(HE.dayRollover, { from_date: from, to_date: to })
  })
  const realToday = now.date
  const today = picked ?? realToday
  const isToday = today === realToday
  const isFuture = today > realToday
  const week = useTimetable(today, addDays(today, 6))
  // 습관은 앞날 체크가 안 돼서 오늘까지만 그날 기준으로 불러와요
  const habits = useHabitHome(isToday ? undefined : isFuture ? undefined : today)
  const home = useHomeToday(isToday ? undefined : today)
  const toggle = useToggleComplete()
  const toggleHabit = useToggleHabitCheck(isToday || isFuture ? undefined : today)
  const [retroOpen, setRetroOpen] = useState(false)

  // ── 분석 (노션 Tracking Plan 「홈」) ──
  const rel = dateRelationOf(today, realToday)
  const th = useCallback(
    (event: Parameters<typeof track>[0], props?: EventProps) =>
      track(event, { view_date: today, date_relation: rel, ...props }),
    [today, rel]
  )
  const sent = useRef(new Set<string>())
  const once = useCallback((key: string, fn: () => void) => {
    if (sent.current.has(key)) return
    sent.current.add(key)
    fn()
  }, [])
  // 회고 패널: 남긴 수 · 열린 시각
  const writtenCount = useRef(0)
  const onWrittenChange = useCallback((n: number) => {
    writtenCount.current = n
  }, [])
  const retroOpenedAt = useRef(0)
  // 이유를 적어 둔 변경(날짜 바꿈 · 자정 · 패널 닫기)이 끝나면 다시 기본값으로
  // (안쪽 회고 칸이 사라지며 이유를 읽은 다음에 돌아요 — 같은 커밋에서 자식 정리가 먼저)
  useEffect(() => {
    discardReason.current = 'page_leave'
  }, [today, retroOpen])

  const loaded = !!week.data
  usePageEngagement('home', { view_date: today }, loaded)

  // 불러오기 실패 (날짜마다 1회)
  useEffect(() => {
    if (week.isError) once(`load_fail:${today}`, () => th(HE.loadFail, { error_status: errorStatusOf(week.error) }))
  }, [week.isError, week.error, today, once, th])
  useEffect(() => {
    if (!loaded) return
    if (home.isError) {
      once(`section_fail:home_today:${today}`, () =>
        th(HE.sectionLoadFail, { failed_section: 'home_today', error_status: errorStatusOf(home.error) })
      )
    }
    if (habits.isError) {
      once(`section_fail:habits:${today}`, () =>
        th(HE.sectionLoadFail, { failed_section: 'habits', error_status: errorStatusOf(habits.error) })
      )
    }
  }, [loaded, home.isError, home.error, habits.isError, habits.error, today, once, th])

  // 스크롤: 좁은 화면(lg 미만)은 페이지 25·50·75·100% / 넓은 화면은 카드 안 첫 스크롤 (카드마다 1회)
  useEffect(() => {
    if (!loaded) return
    const onWindowScroll = () => {
      if (window.innerWidth >= 1024) return
      const max = document.documentElement.scrollHeight - window.innerHeight
      if (max <= 0) return
      const pct = (window.scrollY / max) * 100
      for (const mark of [25, 50, 75, 100]) {
        if (pct >= mark - 1) once(`scroll:${mark}`, () => track(HE.scrollDepth, { scroll_percent: mark }))
      }
    }
    const onAnyScroll = (e: Event) => {
      if (window.innerWidth < 1024 || !(e.target instanceof HTMLElement)) return
      const section = e.target.closest<HTMLElement>('[data-home-section]')?.dataset.homeSection
      if (section) once(`section_scroll:${section}`, () => track(HE.sectionScroll, { section }))
    }
    window.addEventListener('scroll', onWindowScroll, { passive: true })
    document.addEventListener('scroll', onAnyScroll, { capture: true, passive: true })
    return () => {
      window.removeEventListener('scroll', onWindowScroll)
      document.removeEventListener('scroll', onAnyScroll, { capture: true })
    }
  }, [loaded, once])

  // 눌릴 것처럼 보이지만 반응 없는 곳 (data-dead="...") — 많이 눌리면 링크로 바꿀 후보
  const onDeadClick = (e: MouseEvent<HTMLElement>) => {
    if (!(e.target instanceof Element)) return
    const target = e.target.closest<HTMLElement>('[data-dead]')?.dataset.dead
    // 어느 카드였는지 — home.section_scroll 과 같은 data-home-section 기준
    const section = e.target.closest<HTMLElement>('[data-home-section]')?.dataset.homeSection
    if (target) th(HE.deadClick, { dead_target: target, section })
  }

  const go = (d: string, event: typeof HE.datePrevClick | typeof HE.dateNextClick | typeof HE.dateTodayClick) => {
    track(event, { from_date: today, to_date: d, date_relation: dateRelationOf(d, realToday) })
    discardReason.current = 'date_change'
    setPicked(d === realToday ? null : d)
  }
  const nav = {
    today,
    isToday,
    onPrev: () => go(addDays(today, -1), HE.datePrevClick),
    onNext: () => go(addDays(today, 1), HE.dateNextClick),
    onToday: () => go(realToday, HE.dateTodayClick),
  }

  if (week.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <DayNav {...nav} className="self-start" />
        <div className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      </div>
    )
  }
  if (week.isError || !week.data) {
    return (
      <div className="flex flex-col gap-4">
        <DayNav {...nav} className="self-start" />
        <Card className="p-8 text-center">
          <p className="font-bold">{isToday ? '오늘' : '이날'} 일정을 불러오지 못했어요</p>
          <p className="text-ink-3 mt-1 text-sm">{week.error?.message}</p>
          <Button
            className="mt-4"
            onClick={() => {
              th(HE.retryClick)
              // 다시 실패하면 load_fail 을 또 남겨요
              sent.current.delete(`load_fail:${today}`)
              week.refetch()
            }}
          >
            다시 시도
          </Button>
        </Card>
      </div>
    )
  }

  const [day, ...rest] = week.data.days
  const all = tasksOfDay(day)
  const tasks = all.filter((t) => !isRoutineTask(t)).sort((a, b) => startMinutes(today, a) - startMinutes(today, b))
  const routines = all.filter(isRoutineTask).sort((a, b) => startMinutes(today, a) - startMinutes(today, b))
  const togglingId = toggle.isPending ? (toggle.variables?.taskId ?? null) : null

  // 완료하면 캘린더 · 홈(목표별 오늘 끝낸 일 · 마감)이 같이 다시 불러와져요 (timetable/api 의 공통 처리)
  const onToggle = (t: TimeBlockTask) =>
    toggle.mutate(t, checkResult(isRoutineTask(t) ? 'routine' : 'task', isDone(t) ? 'uncomplete' : 'complete'))
  const onToggleHabit = (h: Habit) =>
    toggleHabit.mutate({ habit: h, day: today }, checkResult('habit', h.checked ? 'uncomplete' : 'complete'))

  const retroLeft = (home.data?.goals ?? []).filter((g) => g.activeToday && !hasRecord(g)).length
  // 앞날은 습관을 체크할 수 없어서 빼요 (루틴 Task 는 보여요)
  const habitsShown = isFuture ? undefined : habits.data

  // 체크 결과 (성공 / 실패 · 되돌림) — 둘러보기 401 도 fail 로 남고 로그인 모달이 떠요
  const checkResult = (itemType: 'task' | 'routine' | 'habit', action: 'complete' | 'uncomplete') => ({
    onSuccess: () => th(HE.checkResult, { item_type: itemType, check_action: action, result: 'success' }),
    onError: (e: unknown) =>
      th(HE.checkResult, { item_type: itemType, check_action: action, result: 'fail', error_status: errorStatusOf(e) }),
  })

  // home.pageview — 일정을 다 불러와 화면이 그려진 직후 (이 화면에 들어올 때마다 1회)
  const agendaTasks = agendaOf(day).flatMap((it) => (it.kind === 'task' ? [it] : []))
  const habitTotal = habitsShown?.scheduledCount ?? 0
  const habitDone = habitsShown?.doneCount ?? 0
  const doneCount = tasks.filter(isDone).length + routines.filter(isDone).length + habitDone
  const totalCount = tasks.length + routines.length + habitTotal
  const deadlines = home.data?.deadlines ?? []
  const openAgenda = agendaTasks.filter((a) => !isDone(a.task))
  const focusState = !isToday
    ? 'hidden'
    : openAgenda.some((a) => a.start <= now.minutes && now.minutes < a.end)
      ? 'current'
      : openAgenda.some((a) => a.start > now.minutes)
        ? 'next'
        : 'none'
  const sendPageview = () =>
    once('pageview', () =>
    th(HE.pageview, {
      total_count: totalCount,
      done_count: doneCount,
      progress_percent: totalCount ? Math.round((doneCount / totalCount) * 100) : 0,
      task_count: tasks.length,
      routine_count: routines.length,
      habit_count: habitTotal,
      retro_left_count: retroLeft,
      due_today_count: deadlines.filter((d) => d.dDay === 0).length,
      overdue_count: deadlines.filter((d) => d.overdue).length,
      focus_state: focusState,
      layout_breakpoint: layoutBreakpoint(),
    })
  )

  const openRetro = () => {
    th(HE.retroButtonClick, { retro_left_count: retroLeft })
    if (retroOpen) return
    const goals = home.data?.goals
    th(HE.retroPanelView, {
      goal_count: goals?.length,
      active_goal_count: goals?.filter((g) => g.activeToday).length,
      retro_done_count: goals?.filter((g) => g.activeToday && hasRecord(g)).length,
      layout_breakpoint: layoutBreakpoint(),
    })
    retroOpenedAt.current = Date.now()
    setRetroOpen(true)
  }
  const closeRetro = (method: 'x_button' | 'esc') => {
    th(HE.retroPanelClose, {
      close_method: method,
      written_count: writtenCount.current,
      open_duration_seconds: Math.round((Date.now() - retroOpenedAt.current) / 1000),
    })
    discardReason.current = 'panel_close'
    setRetroOpen(false)
  }

  return (
    // 넓은 화면(lg~): 한 화면에 다 보이게 — 요약 한 줄 + 칸 나누기(캘린더 1/3 · 나머지 2×2), 넘치면 칸 안에서 스크롤
    // 회고 패널이 열리면 아주 넓은 화면(2xl~)에선 옆으로 비켜서 나란히, 그보다 좁으면 위에 덮어요
    <HomeAnalyticsProvider viewDate={today}>
    <RunOnMount run={sendPageview} />
    <div
      onClick={onDeadClick}
      className={cn('flex flex-col gap-4 lg:h-[calc(100dvh-4rem)] lg:min-h-[640px]', retroOpen && '2xl:pr-[416px]')}
    >
      <Summary
        userName={userName}
        nav={nav}
        nowMinutes={now.minutes}
        tasks={tasks}
        routines={routines}
        habitDone={habitDone}
        habitTotal={habitTotal}
        agendaTasks={agendaTasks}
        dueToday={deadlines.filter((d) => d.dDay === 0).length}
        overdue={deadlines.filter((d) => d.overdue).length}
        retroLeft={retroLeft}
        onRetro={openRetro}
      />

      <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)] gap-4 lg:grid-cols-3 lg:grid-rows-2">
        <HomeDayCalendar day={day} className="lg:row-span-2" dayLabel={isToday ? '오늘' : '이날'} />
        <TodayTasks
          today={today}
          tasks={tasks}
          onToggle={onToggle}
          togglingId={togglingId}
          dayLabel={isToday ? '오늘' : '이날'}
        />
        <TodayRoutines
          dayLabel={isToday ? '오늘' : '이날'}
          today={today}
          routines={routines}
          habits={habitsShown}
          onToggleTask={onToggle}
          onToggleHabit={onToggleHabit}
          togglingId={togglingId}
        />
        <UpcomingSchedule days={rest} today={today} />
        <DeadlineRoadmap deadlines={home.data?.deadlines} failed={home.isError} />
      </div>

      {retroOpen && (
        <RetroSidePanel
          title={isToday ? '오늘 회고' : `${monthOf(today)}월 ${dayOfMonth(today)}일 회고`}
          onClose={closeRetro}
        >
          {/* 날짜가 바뀌면 입력 중이던 상태를 새로 시작해요 */}
          <RetroPanel
            key={today}
            goals={home.data?.goals}
            today={today}
            failed={home.isError}
            bare
            discardReason={discardReason}
            onWrittenChange={onWrittenChange}
          />
        </RetroSidePanel>
      )}
    </div>
    </HomeAnalyticsProvider>
  )
}

/** 오른쪽 회고 패널 — 캘린더의 "Task 추가" 패널과 같은 자리 · 모양 */
function RetroSidePanel({
  title,
  onClose,
  children,
}: {
  title: string
  onClose: (method: 'x_button' | 'esc') => void
  children: React.ReactNode
}) {
  // Esc 로 닫기 — 입력 중이거나 다른 창(모달 · 팝오버)이 떠 있으면 그쪽이 먼저
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || e.defaultPrevented) return
      const el = e.target instanceof HTMLElement ? e.target : null
      if (el?.closest('input, textarea, select, [role="dialog"]')) return
      onClose('esc')
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <aside
      aria-label={title}
      className="bg-bg-surface border-border-subtle shadow-pop fixed inset-0 z-40 flex animate-[slide-in-right_180ms_ease-out] flex-col sm:inset-y-0 sm:right-0 sm:left-auto sm:w-[400px] sm:border-l 2xl:shadow-none"
    >
      {/* 명세 13.2 — 머리: H3 제목 왼쪽 · 닫기 / 내용: 좌우 24 · 위 20 · 안쪽 카드 테두리 · 그림자 없이 */}
      <div className="flex shrink-0 items-center gap-2 px-6 pt-6 pb-1">
        <h2 className="text-h3 text-text-primary min-w-0 flex-1 truncate">{title}</h2>
        <button
          type="button"
          onClick={() => onClose('x_button')}
          aria-label={`${title} 닫기`}
          className={iconButtonClass('md')}
        >
          <X className="size-[18px]" />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-5 pb-[max(24px,env(safe-area-inset-bottom))] [&_.shadow-card]:border-0 [&_.shadow-card]:bg-transparent [&_.shadow-card]:p-0 [&_.shadow-card]:shadow-none">
        {children}
      </div>
    </aside>
  )
}

interface DayNavProps {
  today: string
  isToday: boolean
  onPrev: () => void
  onNext: () => void
  onToday: () => void
}

/** 날짜 ‹ › — 지난날 · 앞날의 홈을 볼 수 있어요 (오늘이 아니면 "오늘" 버튼) */
function DayNav({
  today,
  isToday,
  onPrev,
  onNext,
  onToday,
  className,
  dark = false,
}: DayNavProps & { className?: string; dark?: boolean }) {
  const btn = cn(
    'grid size-7 place-items-center rounded-lg',
    dark ? 'text-on-ink/70 hover:bg-on-ink/10 hover:text-on-ink' : 'text-ink-3 hover:bg-subtle hover:text-ink'
  )
  return (
    <div className={cn('flex items-center gap-1', className)}>
      <button type="button" onClick={onPrev} aria-label="이전 날" className={btn}>
        <ChevronLeft className="size-4" />
      </button>
      <span className={cn('text-[13px] font-semibold tabular-nums', dark ? 'text-on-ink/80' : 'text-ink-2')}>
        {monthOf(today)}월 {dayOfMonth(today)}일 {WEEKDAY_FULL[weekdayIndex(today)]}
      </span>
      <button type="button" onClick={onNext} aria-label="다음 날" className={btn}>
        <ChevronRight className="size-4" />
      </button>
      {!isToday && (
        <button
          type="button"
          onClick={onToday}
          className={cn(
            'ml-1 rounded-full px-2.5 py-0.5 text-[12px] font-semibold',
            dark ? 'bg-on-ink/15 text-on-ink hover:bg-on-ink/25' : 'bg-subtle text-ink hover:bg-line'
          )}
        >
          오늘
        </button>
      )}
    </div>
  )
}

/** 요약 한 줄 — 날짜(‹ ›) · 인사 · 진행률 · 지금/다음 할 일 · 회고 쓰기 */
function Summary({
  userName,
  nav,
  nowMinutes,
  tasks,
  routines,
  habitDone,
  habitTotal,
  agendaTasks,
  dueToday,
  overdue,
  retroLeft,
  onRetro,
}: {
  userName: string
  nav: DayNavProps
  nowMinutes: number
  tasks: TimeBlockTask[]
  routines: TimeBlockTask[]
  habitDone: number
  habitTotal: number
  agendaTasks: { start: number; end: number; task: TimeBlockTask }[]
  dueToday: number
  overdue: number
  retroLeft: number
  onRetro: () => void
}) {
  const { today, isToday } = nav
  const isPast = !isToday && today < nowKst().date
  const taskDone = tasks.filter(isDone).length
  const routineDone = routines.filter(isDone).length
  const total = tasks.length + routines.length + habitTotal
  const done = taskDone + routineDone + habitDone
  const percent = total ? Math.round((done / total) * 100) : 0
  const left = total - done

  const open = agendaTasks.filter((a) => !isDone(a.task))
  const current = open.find((a) => a.start <= nowMinutes && nowMinutes < a.end)
  const next = open.find((a) => a.start > nowMinutes)
  const focus = current ?? next

  return (
    // 오늘 요약 카드 — 예전 검은(inverse) 바 → 밝은 카드 (bg/surface · border/subtle)
    <section className="bg-bg-surface border-border-subtle shadow-card text-text-primary rounded-card flex shrink-0 flex-wrap items-center gap-x-6 gap-y-3 border px-5 py-4">
      <div className="min-w-0">
        <DayNav {...nav} className="-ml-1.5" />
        <h1 className="text-h3 text-text-primary truncate">
          {isToday
            ? total === 0
              ? `${userName}님, 오늘은 비어 있어요`
              : left === 0
                ? `${userName}님, 오늘 할 일을 모두 끝냈어요 🎉`
                : `${userName}님, 오늘 ${left}개 남았어요`
            : total === 0
              ? '이날은 비어 있어요'
              : isPast
                ? left === 0
                  ? `이날 할 일을 모두 끝냈어요 🎉`
                  : `이날 ${total}개 중 ${done}개를 했어요`
                : `이날 할 일 ${total}개가 잡혀 있어요`}
        </h1>
      </div>

      <div data-dead="progress" className="flex min-w-[180px] items-center gap-3">
        <span className="text-display-lg leading-none tabular-nums">{percent}%</span>
        <div>
          <ProgressBar value={percent / 100} className="w-28" />
          <p className="text-caption-sm text-text-secondary mt-1 whitespace-nowrap tabular-nums">
            할 일 {taskDone}/{tasks.length}
            {routines.length > 0 && ` · 루틴 ${routineDone}/${routines.length}`}
            {habitTotal > 0 && ` · 습관 ${habitDone}/${habitTotal}`}
          </p>
        </div>
      </div>

      <div className="flex flex-wrap gap-1.5 text-[12px]">
        {dueToday > 0 && (
          <Pill tone="warning" dead="due_pill">
            오늘 마감 {dueToday}
          </Pill>
        )}
        {overdue > 0 && (
          <Pill tone="danger" dead="overdue_pill">
            마감 지남 {overdue}
          </Pill>
        )}
      </div>

      <div className="ml-auto flex flex-wrap items-center gap-2">
        {isToday && (
          <div data-dead="focus_box" className="bg-bg-sunken max-w-[260px] min-w-0 rounded-xl px-3 py-1.5">
            <p className="text-label-xs text-text-secondary">{current ? '지금 할 일' : '다음 일정'}</p>
            {focus ? (
              <p className="truncate text-[13px] font-bold">
                {formatMinutes(focus.start)} {focus.task.emoji ? `${focus.task.emoji} ` : ''}
                {focus.task.taskName}
                {!current && <span className="text-text-secondary font-medium"> · {inLabel(focus.start - nowMinutes)}</span>}
              </p>
            ) : (
              <p className="text-text-secondary text-[13px] font-semibold">남은 일정이 없어요</p>
            )}
          </div>
        )}
        <Button variant="secondary" size="sm" onClick={onRetro}>
          <PencilLine className="size-4" />
          {isToday ? '오늘 회고' : '이날 회고'}
          {retroLeft > 0 && (
            <span className="bg-brand grid h-4 min-w-4 place-items-center rounded-full px-1 text-[10px] font-bold text-white">
              {retroLeft}
            </span>
          )}
        </Button>
      </div>
    </section>
  )
}

function Pill({ children, tone, dead }: { children: React.ReactNode; tone?: 'danger' | 'warning'; dead?: string }) {
  return (
    <span
      data-dead={dead}
      className={cn(
        'rounded-full px-2.5 py-0.5 font-semibold tabular-nums',
        tone === 'danger'
          ? 'bg-status-error-bg text-status-error-fg'
          : tone === 'warning'
            ? 'bg-status-warning-bg text-status-warning-fg'
            : 'bg-bg-subtle text-text-secondary'
      )}
    >
      {children}
    </span>
  )
}

/** 화면이 그려진 직후 한 번 실행 (렌더 중에 이벤트를 보내지 않으려고) */
function RunOnMount({ run }: { run: () => void }) {
  useEffect(() => {
    run()
    // 처음 그려질 때 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return null
}
