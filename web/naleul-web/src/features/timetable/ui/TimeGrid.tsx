'use client'

import { rectOf, type AnchorRect } from '@/components/ui/Popover'
import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent, type PointerEvent } from 'react'
import { CheckCircle2, Circle } from 'lucide-react'
import { cn } from '@/lib/cn'
import { useToggleHabitBlock } from '../api'
import {
  MAX_TASKS_AT_ONCE,
  crowdedTasks,
  hexOf,
  layoutColumns,
  placeActivities,
  placeFixed,
  placeSleep,
  placeTasks,
  withAlpha,
  type Placed,
} from '../layout'
import { SNAP_MINUTES, WEEKDAY_LABEL, addDays, dayOfMonth, formatMinutes, hm, minutesFrom, nowKst, weekdayIndex } from '../time'
import type { ActualActivity, CarriedOutBlock, FixedBlock, HabitBlock, TimeBlockTask, TimetableDay } from '../types'
import { ActivityBlock, FixedBlockView, HATCH, SleepBlockView, TaskBlock, taskColors } from './Blocks'
import {
  canUnschedule,
  groupMoves,
  useGridDrag,
  type DragCancelReason,
  type DragMode,
  type DragSource,
  type DragState,
  type DropTarget,
  type GroupMember,
} from './useGridDrag'

export const HOUR_PX = 60
const PPM = HOUR_PX / 60
const UNSCHEDULED_VISIBLE = 3

export type Selection =
  { kind: 'task'; id: number } | { kind: 'fixed'; key: string } | { kind: 'activity'; id: number } | null

export const fixedKey = (b: FixedBlock) => `${b.lifePatternId}-${b.targetDate}-${b.start}`

/** 드래그로 옮길 수 있는 Task (완료·미션은 백엔드에서 409) */
export const canDragTask = (t: TimeBlockTask) => t.taskStatus !== 'COMPLETED' && t.sourceType !== 'MISSION'

/** 실제로 한 시간을 끌어서 고칠 수 있는 완료 Task (미션은 실제 시간을 따로 저장해서 제외) */
export const canDragActual = (t: TimeBlockTask) =>
  t.taskStatus === 'COMPLETED' && t.sourceType !== 'MISSION' && !!t.plannedStartAt

/**
 * 드래그로 옮길 수 있는 고정 블록: 그날 안에서 시작하고 끝나는 것만.
 * 자정을 넘기는 수면은 화면에 날짜별 조각만 보여서 끌어도 실제 길이를 알 수 없어요 → 상세의 "이날만 시간 변경"으로.
 */
export const canDragFixed = (b: FixedBlock, date: string) =>
  !b.clippedFromPreviousDay && b.targetDate === date && minutesFrom(date, b.end) < 1440

const HEADER_PX = 56

interface Props {
  days: TimetableDay[]
  startHour: number
  endHour: number
  selection: Selection
  onSelectTask: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  onSelectFixed: (b: FixedBlock, e: MouseEvent<HTMLElement>) => void
  /** 주간에서 날짜 머리글 클릭 → 일간 */
  onDayClick?: (date: string) => void
  /** 드래그해서 놓았을 때 (원래 자리와 같으면 호출되지 않아요) */
  onDrop?: (source: DragSource, target: DropTarget, mode: DragMode) => void
  /** 끌기 시작 후 저장 없이 끝났을 때 (분석용) */
  onDragCancel?: (source: DragSource, mode: DragMode, reason: DragCancelReason, invalid?: string) => void
  /** 기록할 수 없는 빈 칸(앞날 · 지금 이후)을 눌렀을 때 (분석용 — 반응 없는 클릭) */
  onBlockedEmptyClick?: (date: string, minutes: number) => void
  /** 드래그가 시작될 때 (열린 팝오버 닫기 등) */
  onDragStart?: () => void
  /** "NEW" 표시할 Task (방금 만든 것) */
  highlightIds?: ReadonlySet<number>
  /**
   * 확인을 기다리는 블록 (AI 배치 미리보기 · 이월 제안). 바뀌면 그 블록이 보이게 스크롤하고 테두리로 강조해요.
   * fadeOthers 면 나머지 블록·고정 시간을 흐리게 해서 제안만 눈에 들어오게 해요.
   */
  focusIds?: ReadonlySet<number>
  fadeOthers?: boolean
  /** 묶음으로 같이 옮길 Task (2개 이상이면 하나를 끌 때 같이 움직여요) */
  groupIds?: ReadonlySet<number>
  /** 격자 최대 높이 (기본: 화면 높이에 맞춤) */
  maxHeightClass?: string
  /** 실제로 한 일 — 있으면 그날 칸 오른쪽에 "실제" 칸이 생겨요 */
  activities?: ActualActivity[]
  onSelectActivity?: (a: ActualActivity, e: MouseEvent<HTMLElement>) => void
  /**
   * 일간: 칸을 "계획 | 실제"로 늘 나눠요. 왼쪽은 계획한 시각(완료한 일도 원래 자리), 오른쪽은 실제로 한 시각
   * (완료한 Task + 실제로 한 일 기록). 없으면 실제 기록이 있을 때만 나눠요.
   */
  planVsActual?: boolean
  /** 빈 칸(지난 시간)을 눌렀을 때 — 그 시각으로 "실제로 한 일" 기록 열기 */
  onEmptyClick?: (date: string, minutes: number, e: MouseEvent<HTMLElement>) => void
  /** 시간 미정 '+N개 더보기'를 눌렀을 때 (분석용) — remaining = 아직 안 보인 개수 */
  onUnscheduledMore?: (date: string, remaining: number) => void
  /** 습관 블록을 눌러 체크·해제할 때 (분석용) — habit.checked 는 누르기 전 상태 */
  onHabitToggle?: (habit: HabitBlock, date: string) => void
  /** 터치: Task 를 3초 꾹 → 묶음에 넣기 (명세 10.5) */
  onTaskHold?: (t: TimeBlockTask) => void
  /** 터치: 묶음 고르는 중 빈 곳을 탭 → 묶음 풀기 */
  onGroupClear?: () => void
  /** 앞날 · 지금 이후 빈 칸도 onEmptyClick 으로 (캘린더 + 메뉴 — 명세 11.4) */
  allowFutureEmpty?: boolean
  /** 다른 날로 옮겨진 Task 흔적을 눌렀을 때 (옮겨 간 날로 이동) — 없으면 흔적을 누를 수 없어요 */
  onCarriedClick?: (c: CarriedOutBlock, fromDate: string, anchor?: AnchorRect) => void
  /** 빈 칸 우클릭 → 그 시각(10분 단위)으로 추가 메뉴 (캘린더에서만). 없으면 브라우저 기본 메뉴 */
  onEmptyContextMenu?: (date: string, minutes: number, e: MouseEvent<HTMLElement>) => void
  /** Task 블록 우클릭 → 수정 · 삭제 메뉴 (캘린더에서만) */
  onTaskContextMenu?: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
}

/** 기상은 취침보다, 취침은 기상보다 30분 이상 떨어져야 해요 (같은 날 칸 기준) */
const MIN_AWAKE_MINUTES = 30

function validateSleep(days: TimetableDay[], block: FixedBlock, target: Extract<DropTarget, { type: 'grid' }>) {
  if (block.patternType !== 'SLEEP') return null
  const day = days.find((d) => d.date === target.date)
  const s = day?.sleep
  if (!day || !s) return null
  const key = fixedKey(block)
  if (s.wake && fixedKey(s.wake) === key && s.bed) {
    if (target.end > minutesFrom(day.date, s.bed.start) - MIN_AWAKE_MINUTES)
      return '취침 시간보다 늦게 일어날 수 없어요'
  }
  if (s.bed && fixedKey(s.bed) === key && s.wake) {
    if (target.start < minutesFrom(day.date, s.wake.end) + MIN_AWAKE_MINUTES) return '기상 시간보다 일찍 잘 수 없어요'
  }
  return null
}

/**
 * 실제로 한 시간: 나눠서 한 다른 구간과 겹치면 안 돼요 (백엔드 TASK_ACTUAL_INVALID_SEGMENT 와 같은 규칙)
 */
function validateActual(
  source: Extract<DragSource, { kind: 'actual' }>,
  target: Extract<DropTarget, { type: 'grid' }>
) {
  const segs = source.task.actualSegments ?? []
  if (segs.length < 2) return null
  for (let i = 0; i < segs.length; i++) {
    if (i === source.part) continue
    const s = minutesFrom(target.date, segs[i].start)
    const e = minutesFrom(target.date, segs[i].end)
    if (target.start < e && s < target.end) return '나눠서 한 다른 구간과 겹쳐요'
  }
  return null
}

/** 자동 배치가 나눠 넣은 Task 의 전체 길이(분) — 나눠 넣지 않았으면 null */
function splitLength(t: TimeBlockTask): number | null {
  if (t.taskStatus === 'COMPLETED' || (t.plannedSegments?.length ?? 0) < 2) return null
  return t.plannedDurationMinutes ?? null
}

function sameAsSource(source: DragSource, target: DropTarget) {
  // 시간 미정 → 같은 날 시간 미정은 그대로 (다른 날 시간 미정 칸이면 날짜만 옮겨요)
  if (target.type === 'unscheduled') return source.start === null && source.date === target.date
  return target.date === source.date && target.start === source.start && target.end === source.end
}

/** 1분마다 바뀌는 현재 한국 시각 */
function useNowKst() {
  const [now, setNow] = useState(() => nowKst())
  useEffect(() => {
    const id = window.setInterval(() => setNow(nowKst()), 60_000)
    return () => window.clearInterval(id)
  }, [])
  return now
}

export function TimeGrid({
  days,
  startHour,
  endHour,
  selection,
  onSelectTask,
  onSelectFixed,
  onDayClick,
  onDrop,
  onDragCancel,
  onBlockedEmptyClick,
  onDragStart,
  highlightIds,
  focusIds,
  fadeOthers = false,
  groupIds,
  maxHeightClass = 'max-h-[max(480px,calc(100dvh-230px))]',
  activities = [],
  onSelectActivity,
  onEmptyClick,
  planVsActual = false,
  onUnscheduledMore,
  onHabitToggle,
  onTaskHold,
  onGroupClear,
  allowFutureEmpty = false,
  onCarriedClick,
  onEmptyContextMenu,
  onTaskContextMenu,
}: Props) {
  const now = useNowKst()
  const from = startHour * 60
  const to = endHour * 60
  const geometry = { from, ppm: PPM }
  const hours = useMemo(
    () => Array.from({ length: endHour - startHour }, (_, i) => startHour + i),
    [startHour, endHour]
  )
  const single = days.length === 1
  // 시간 눈금 폭 — 주간은 좁게 36 (명세 9.8), 일간 56
  const gutterPx = single ? 56 : 36
  const scrollRef = useRef<HTMLDivElement>(null)
  const rangeKey = `${days[0]?.date}_${days.length}`

  // 처음 보거나 기간이 바뀌면 (명세 9.6): 오늘이 보이면 지금 시각이 위에서 1/3 쯤에,
  // 아니면 첫 블록(없으면 08:00) 이 보이게 스크롤
  const scrolledFor = useRef<string | null>(null)
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !days.length || scrolledFor.current === rangeKey) return
    scrolledFor.current = rangeKey
    const hasToday = days.some((d) => d.date === now.date)
    if (hasToday) {
      el.scrollTop = Math.max(0, (now.minutes - from) * PPM - (el.clientHeight - HEADER_PX) / 3)
      return
    }
    const raf = window.requestAnimationFrame(() => {
      const tops = [...el.querySelectorAll<HTMLElement>('[data-date] [data-task-id]')].map((b) => b.offsetTop)
      const first = tops.length ? Math.min(...tops) : (8 * 60 - from) * PPM
      el.scrollTop = Math.max(0, first - 24)
    })
    return () => window.cancelAnimationFrame(raf)
  }, [rangeKey, days, now, from])

  // 확인을 기다리는 블록이 생기면 그 블록으로 화면을 옮겨요 (격자 안 스크롤 + 페이지 스크롤)
  const focusKey = focusIds?.size ? [...focusIds].sort((a, b) => a - b).join(',') : ''
  useEffect(() => {
    const root = scrollRef.current
    if (!focusKey || !root) return
    const ids = new Set(focusKey.split(',').map(Number))
    const raf = window.requestAnimationFrame(() => {
      const els = [...root.querySelectorAll<HTMLElement>('[data-date] [data-task-id]')].filter((el) =>
        ids.has(Number(el.dataset.taskId))
      )
      if (!els.length) return
      const rect = (el: HTMLElement) => el.getBoundingClientRect()
      const first = els.reduce((a, b) => (rect(a).top <= rect(b).top ? a : b))
      const r = rect(first)
      const box = root.getBoundingClientRect()
      root.scrollTo({
        top: root.scrollTop + (r.top - box.top) - HEADER_PX - 72,
        // 주간: 가로로도 그 날짜 칸이 보이게 (왼쪽 시간 눈금 56px 만큼 비켜서)
        left:
          r.left < box.left + gutterPx || r.right > box.right ? root.scrollLeft + (r.left - box.left) - 72 : root.scrollLeft,
        behavior: 'smooth',
      })
      // 격자 자체가 화면 밖이면 페이지도 격자 쪽으로
      if (box.top < 0 || box.top > window.innerHeight * 0.6) root.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
    return () => window.cancelAnimationFrame(raf)
  }, [focusKey, gutterPx])
  const fadeTask = (id: number) => fadeOthers && !!focusIds?.size && !focusIds.has(id)

  // ── 드래그 ──
  // 한 시각에 Task 는 2개까지 겹쳐 둘 수 있어요 (백엔드 TaskScheduleService 와 같은 규칙)
  const validate = useCallback(
    (source: DragSource, target: DropTarget) => {
      if (source.kind === 'fixed' && target.type === 'grid') return validateSleep(days, source.block, target)
      if (source.kind === 'actual' && target.type === 'grid') return validateActual(source, target)
      if (source.kind !== 'task' || target.type !== 'grid') return null
      const moves = groupMoves(source, target)
      const moving = new Set(moves.map((m) => m.task.taskId))
      for (const m of moves) {
        const day = days.find((d) => d.date === m.date)
        if (!day) continue
        // 같이 옮기는 다른 Task 들의 새 자리도 자리를 차지해요
        const others = moves.filter((o) => o !== m).map((o) => ({ start: o.start, end: o.end }))
        const hit = crowdedTasks(day, m.start, m.end, moving, others)
        if (hit) {
          const who = moves.length > 1 ? `"${m.task.taskName}" 자리에 ` : ''
          return `${who}이미 Task ${MAX_TASKS_AT_ONCE}개가 겹쳐 있어요`
        }
      }
      return null
    },
    [days]
  )
  const { drag, startDrag: rawStartDrag, consumeClick } = useGridDrag({
    scrollRef,
    from,
    to,
    ppm: PPM,
    stickyTop: HEADER_PX,
    validate,
    onDrop: (source, target, mode) => {
      if (!sameAsSource(source, target)) onDrop?.(source, target, mode)
    },
    onActivate: onDragStart,
    onCancel: onDragCancel,
    onHold: onTaskHold ? (source) => source.kind === 'task' && onTaskHold(source.task) : undefined,
  })
  const enabled = !!onDrop

  /**
   * 끌기 시작할 때 블록이 칸 안에서 차지하던 가로 자리(왼쪽 · 폭, 칸 너비에 대한 비율)를 기억해요 (명세 9.7)
   * → 미리보기가 칸 전체로 넓어지지 않고 겹쳐서 반쪽이던 블록은 반쪽 그대로 움직여요.
   */
  const [ghostBox, setGhostBox] = useState<Record<string, { left: number; width: number }>>({})
  const boxOf = (col: Element, el: Element | null) => {
    if (!el) return undefined
    const c = col.getBoundingClientRect()
    const r = el.getBoundingClientRect()
    if (!c.width) return undefined
    return { left: (r.left - c.left) / c.width, width: r.width / c.width }
  }
  const startDrag: typeof rawStartDrag = (e, source, mode) => {
    const target = e.currentTarget as HTMLElement
    const col = target.closest('[data-date]')
    if (col) {
      const own = target.closest('[data-task-id],[data-fixed-id],[data-activity-id]')
      const next: Record<string, { left: number; width: number }> = {}
      const mine = boxOf(col, own)
      if (mine) next.source = mine
      if (source.kind === 'task')
        for (const m of source.group ?? []) {
          const b = boxOf(col, col.querySelector(`[data-task-id="${m.task.taskId}"]`))
          if (b) next[`task-${m.task.taskId}`] = b
        }
      setGhostBox(next)
    }
    rawStartDrag(e, source, mode)
  }
  const isDragging = (kind: 'task' | 'fixed', id: string | number) =>
    !!drag &&
    (kind === 'task'
      ? (drag.source.kind === 'task' &&
          (drag.source.task.taskId === id || !!drag.source.group?.some((m) => m.task.taskId === id))) ||
        (drag.source.kind === 'actual' && drag.source.task.taskId === id)
      : drag.source.kind === 'fixed' && fixedKey(drag.source.block) === id)
  // 묶음 드래그 중 같이 움직이는 나머지 Task 들의 새 자리
  const groupGhosts =
    drag?.source.kind === 'task' && drag.source.group?.length && drag.target?.type === 'grid'
      ? groupMoves(drag.source, drag.target).slice(1)
      : []

  const selectTask = (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => {
    if (!consumeClick()) onSelectTask(t, e)
  }
  const selectFixed = (b: FixedBlock, e: MouseEvent<HTMLElement>) => {
    if (!consumeClick()) onSelectFixed(b, e)
  }
  /**
   * 그날 칸에서 "지금" 빨간 선 위치 (분). 없으면 null.
   * 새벽 00:30 이고 어제 칸이 01:00 까지 이어지면 어제 칸 맨 아래(24:30)에 그려요.
   */
  const nowLine = (date: string) => {
    if (date === now.date && now.minutes >= from && now.minutes <= to) return now.minutes
    if (addDays(date, 1) === now.date && now.minutes + 1440 <= to) return now.minutes + 1440
    return null
  }
  const selectActivity = (a: ActualActivity, e: MouseEvent<HTMLElement>) => {
    if (!consumeClick()) onSelectActivity?.(a, e)
  }
  // 블록이 없는 빈 곳을 누르면 그 시각(30분 단위)으로 실제 기록 열기 — 지난 시간만
  const clickEmpty = (date: string, e: MouseEvent<HTMLDivElement>) => {
    if (e.target !== e.currentTarget) return
    // 터치로 묶음을 고르는 중이면 빈 곳 탭 = 묶음 풀기
    const touch = 'pointerType' in e.nativeEvent && (e.nativeEvent as globalThis.PointerEvent).pointerType === 'touch'
    if (touch && groupIds?.size && onGroupClear) {
      onGroupClear()
      return
    }
    if (!onEmptyClick || consumeClick()) return
    const rect = e.currentTarget.getBoundingClientRect()
    const minutes = Math.floor((from + (e.clientY - rect.top) / PPM) / 30) * 30
    // 자정 뒤 칸(24:00~)은 다음 날 시각
    const nowOnDay = date === now.date ? now.minutes : addDays(date, 1) === now.date ? now.minutes + 1440 : null
    if (!allowFutureEmpty && (date > now.date || (nowOnDay !== null && minutes >= nowOnDay))) {
      onBlockedEmptyClick?.(date, minutes)
      return
    }
    onEmptyClick(date, minutes, e)
  }

  // 우클릭: Task 블록이면 수정 · 삭제, 블록 없는 빈 곳이면 추가 메뉴. 고정 시간 · 실제 기록 · 제안 블록은 기본 메뉴 그대로
  const contextMenu = (d: TimetableDay, e: MouseEvent<HTMLDivElement>) => {
    if (drag) return
    // 터치 길게 누르기(묶음 고르기)와 겹치지 않게 마우스 우클릭만
    if ('pointerType' in e.nativeEvent && (e.nativeEvent as globalThis.PointerEvent).pointerType === 'touch') return
    const el = e.target instanceof HTMLElement ? e.target : null
    const block = el?.closest<HTMLElement>('[data-task-id]')
    if (block && e.currentTarget.contains(block)) {
      if (!onTaskContextMenu) return
      const id = Number(block.dataset.taskId)
      const t = d.tasks.find((x) => x.taskId === id) ?? days.flatMap((x) => x.tasks).find((x) => x.taskId === id)
      if (!t || t.proposed) return
      e.preventDefault()
      onTaskContextMenu(t, e)
      return
    }
    if (!onEmptyContextMenu || e.target !== e.currentTarget) return
    e.preventDefault()
    const rect = e.currentTarget.getBoundingClientRect()
    const minutes = Math.floor((from + (e.clientY - rect.top) / PPM) / SNAP_MINUTES) * SNAP_MINUTES
    onEmptyContextMenu(d.date, minutes, e)
  }

  // 날짜 수만큼 칸 (주간 7칸, Task 추가 결과 화면은 1~7칸)
  const gridTemplateColumns = single
    ? `${gutterPx}px minmax(0,1fr)`
    : `${gutterPx}px repeat(${days.length}, minmax(112px, 1fr))`
  // 지금 시각 선이 보이는 칸이 있으면 시간 눈금에 시각 표시 (명세 9.6)
  const nowMinutes = days.map((d) => nowLine(d.date)).find((m) => m !== null) ?? null
  // Task 를 끌고 있으면 "시간 미정"으로 놓을 수 있게 빈 줄이라도 보여줘요
  const showUnscheduledRow =
    days.some((d) => d.unscheduledTasks.length > 0 || (d.carriedOut ?? []).some((c) => !c.start)) ||
    (!!drag && canUnschedule(drag.source))

  return (
    <div
      ref={scrollRef}
      className={cn(
        'border-line bg-surface relative overflow-auto rounded-2xl border',
        // 일간 "계획 | 실제" 폭 — 넓은 화면 60/40, 모바일 50/50 (명세 9.4)
        '[--actual-w:40%] max-lg:[--actual-w:50%]',
        maxHeightClass
      )}
      data-testid="time-grid"
    >
      <div className={cn('grid min-w-full', !single && 'w-max lg:w-full')} style={{ gridTemplateColumns }}>
        {/* ── 머리글 (날짜) ── */}
        <div className="bg-surface border-line sticky top-0 left-0 z-40 h-14 border-b" />
        {days.map((d) => {
          const isToday = d.date === now.date
          const wd = weekdayIndex(d.date)
          const Head = onDayClick && !single ? 'button' : 'div'
          const compareHead = single && planVsActual
          return (
            <Head
              key={d.date}
              {...(Head === 'button' ? { type: 'button' as const, onClick: () => onDayClick?.(d.date) } : {})}
              className={cn(
                'bg-surface border-line sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-l px-3 text-left',
                Head === 'button' && 'hover:bg-subtle/60',
                compareHead && 'pb-4'
              )}
              aria-label={Head === 'button' ? `${d.date} 일간 보기` : undefined}
            >
              <span
                className={cn(
                  'grid size-8 shrink-0 place-items-center rounded-full text-[15px] font-bold',
                  isToday
                    ? 'bg-calendar-today-bg text-calendar-today-fg'
                    : wd === 6
                      ? 'text-calendar-holiday'
                      : wd === 5
                        ? 'text-calendar-saturday'
                        : 'text-ink'
                )}
              >
                {dayOfMonth(d.date)}
              </span>
              <span className="min-w-0">
                <span className={cn('block text-xs font-semibold', isToday ? 'text-text-brand' : 'text-ink-3')}>
                  {WEEKDAY_LABEL[wd]}요일
                </span>
                <span
                  className="text-ink-3 block truncate text-[11px]"
                  title={
                    d.stats.carriedOver
                      ? `다음 날로 넘어간 ${d.stats.carriedOver}개도 이날 못 한 일로 세요 (넘어가도 실행률은 그대로)`
                      : undefined
                  }
                >
                  {/* 아직 오지 않은 날은 실행률 대신 개수 */}
                  {!d.stats.total
                    ? 'Task 없음'
                    : d.date > now.date
                      ? `Task ${d.stats.total}개`
                      : `실행률 ${d.stats.rate ?? 0}%`}
                </span>
              </span>
              {/* 일간 "계획 | 실제" 칸 이름 — 아래 격자의 폭(--actual-w)과 맞춰요 */}
              {compareHead && (
                <span className="text-ink-3 absolute inset-x-0 bottom-0 flex text-[11px] font-semibold" aria-hidden>
                  {/* 좁은 칸(홈의 1/3 캘린더)에서도 두 줄로 넘쳐 날짜를 덮지 않게 한 줄로 자르기 */}
                  <span className="w-[calc(100%-var(--actual-w))] truncate px-3 whitespace-nowrap">📋 계획한 일</span>
                  <span className="border-line w-(--actual-w) truncate border-l border-dashed px-2 whitespace-nowrap">
                    ✅ 실제로 한 일
                  </span>
                </span>
              )}
            </Head>
          )
        })}

        {/* ── 시간 미정 ── */}
        {showUnscheduledRow && (
          <>
            {/* 머리글 바로 아래에 붙어 있어서 스크롤해도 보여요 */}
            <div className="bg-surface border-line text-ink-3 sticky top-14 left-0 z-40 border-b px-1 py-2 text-center text-[11px] leading-tight">
              시간
              <br />
              미정
            </div>
            {days.map((d) => (
              <UnscheduledCell
                key={d.date}
                date={d.date}
                tasks={d.unscheduledTasks}
                carried={(d.carriedOut ?? []).filter((c) => !c.start)}
                onCarriedClick={onCarriedClick}
                selection={selection}
                onSelect={selectTask}
                drag={drag}
                onMore={onUnscheduledMore}
                onDragDown={
                  enabled
                    ? (e, t) => startDrag(e, { kind: 'task', task: t, date: d.date, start: null, end: null })
                    : undefined
                }
              />
            ))}
          </>
        )}

        {/* ── 시간 눈금 ── */}
        <div className="bg-surface sticky left-0 z-[25] select-none" style={{ height: (to - from) * PPM }}>
          {hours.map((h, i) => (
            <span
              key={h}
              className={cn(
                'text-ink-4 absolute -translate-y-1/2 tabular-nums',
                single ? 'right-2 text-[11px]' : 'right-1 text-[10px]'
              )}
              style={{ top: i * HOUR_PX }}
            >
              {/* 24시 이후는 다음 날 새벽 (취침이 자정 뒤일 때) */}
              {i === 0 ? '' : `${String(h % 24).padStart(2, '0')}${single ? ':00' : ''}`}
            </span>
          ))}
          {/* 지금 시각 (Caption/SM · primary) */}
          {nowMinutes !== null && (
            <span
              className={cn(
                'text-caption-sm text-action-primary-bg bg-bg-surface absolute z-[1] -translate-y-1/2 font-medium tabular-nums',
                single ? 'right-1.5' : 'right-0.5 text-[10px]'
              )}
              style={{ top: (nowMinutes - from) * PPM }}
            >
              {formatMinutes(nowMinutes % 1440)}
            </span>
          )}
        </div>

        {/* ── 날짜 칸 ── */}
        {days.map((d) => {
          const fixed = placeFixed(d, from, to)
          const acts = placeActivities(d.date, activities, from, to)
          // 일간: 실제 기록이 있으면 "계획 | 실제"로 나눠서 나란히 보여줘요
          // 주간: 칸이 좁아서 나누지 않고 한 열에 같이 그려요 (겹칠 때만 나란히)
          const compare = single && planVsActual
          const split = compare || (single && acts.length > 0)
          const tasks = placeTasks(d, from, to, compare ? 'planned' : 'shown')
          // 계획 | 실제 비교: 실제 칸에는 완료한 Task(실제 시각)와 실제로 한 일 기록을 같이, 겹치면 나란히
          const doneTasks = compare ? placeTasks(d, from, to, 'done') : []
          if (compare) layoutColumns<Placed<unknown>>([...doneTasks, ...acts])
          // 한 열로 그릴 땐 Task 와 실제 기록을 같이 놓고 겹치는 것끼리만 나란히 (col/cols 를 다시 정해요)
          if (!split && acts.length) layoutColumns<Placed<unknown>>([...tasks, ...acts])
          const isToday = d.date === now.date
          const canClickEmpty = !!onEmptyClick && (allowFutureEmpty || d.date <= now.date)
          return (
            <div
              key={d.date}
              data-date={d.date}
              onClick={(e) => clickEmpty(d.date, e)}
              onContextMenu={(e) => contextMenu(d, e)}
              className={cn(
                'border-line relative border-l',
                isToday && 'bg-ai-tint-soft/40',
                canClickEmpty && 'cursor-cell'
              )}
              style={{ height: (to - from) * PPM }}
            >
              {hours.map((h, i) => (
                <div
                  key={h}
                  className="border-border-soft pointer-events-none absolute inset-x-0 border-t"
                  style={{ top: i * HOUR_PX }}
                />
              ))}
              {placeSleep(d, from, to).map((p) => (
                <SleepBlockView
                  key={`${p.edge}-${fixedKey(p.item)}`}
                  placed={p}
                  geometry={geometry}
                  selected={selection?.kind === 'fixed' && selection.key === fixedKey(p.item)}
                  onSelect={selectFixed}
                  dimmed={isDragging('fixed', fixedKey(p.item)) || (fadeOthers && !!focusIds?.size)}
                  drag={
                    enabled
                      ? (e, mode) =>
                          startDrag(e, { kind: 'fixed', block: p.item, date: d.date, start: p.start, end: p.end }, mode)
                      : undefined
                  }
                />
              ))}
              {fixed.map((p) => (
                <FixedBlockView
                  key={fixedKey(p.item)}
                  placed={p}
                  geometry={geometry}
                  selected={selection?.kind === 'fixed' && selection.key === fixedKey(p.item)}
                  onSelect={selectFixed}
                  dimmed={isDragging('fixed', fixedKey(p.item)) || (fadeOthers && !!focusIds?.size)}
                  drag={
                    enabled && canDragFixed(p.item, d.date)
                      ? (e, mode) =>
                          startDrag(e, { kind: 'fixed', block: p.item, date: d.date, start: p.start, end: p.end }, mode)
                      : undefined
                  }
                />
              ))}
              <HabitBlocks day={d} from={from} to={to} right={split ? 'var(--actual-w)' : '0'} onToggle={onHabitToggle} />
              <CarriedTraces day={d} from={from} to={to} right={split ? 'var(--actual-w)' : '0'} onClick={onCarriedClick} />
              <div
                className={cn(
                  'pointer-events-none absolute inset-y-0 left-0 *:pointer-events-auto',
                  split ? 'right-(--actual-w)' : 'right-0'
                )}
              >
                {tasks.map((p) => {
                  // 묶음 고르는 중이면 1개부터 점선 테두리 (터치 3초 꾹 · Shift+클릭 같은 모양)
                  const grouped = !!groupIds && groupIds.size > 0 && groupIds.has(p.item.taskId)
                  // 묶음에 든 Task 를 옮기면(크기 조절 말고) 같은 날의 나머지 묶음도 같이 움직여요
                  const group: GroupMember[] = grouped
                    ? tasks
                        .filter(
                          (q) => q.item.taskId !== p.item.taskId && groupIds!.has(q.item.taskId) && canDragTask(q.item)
                        )
                        .map((q) => ({ task: q.item, date: d.date, start: q.start, end: q.end }))
                    : []
                  return (
                    <TaskBlock
                      key={`${p.item.taskId}-${p.part ?? 0}`}
                      placed={p}
                      geometry={geometry}
                      selected={selection?.kind === 'task' && selection.id === p.item.taskId}
                      onSelect={selectTask}
                      dimmed={isDragging('task', p.item.taskId)}
                      faded={fadeTask(p.item.taskId)}
                      focused={!!focusIds?.has(p.item.taskId)}
                      isNew={highlightIds?.has(p.item.taskId)}
                      grouped={grouped}
                      planned={compare}
                      dense={!single}
                      noResize={splitLength(p.item) != null}
                      drag={
                        enabled && canDragTask(p.item)
                          ? (e, mode) =>
                              startDrag(
                                e,
                                {
                                  kind: 'task',
                                  task: p.item,
                                  date: d.date,
                                  start: p.start,
                                  // 점심 앞뒤로 나눠 넣은 Task 는 어느 조각을 끌어도 전체 길이 한 덩어리로 옮겨요
                                  end:
                                    mode === 'move' && splitLength(p.item) != null
                                      ? p.start + splitLength(p.item)!
                                      : p.end,
                                  group: mode === 'move' && group.length ? group : undefined,
                                },
                                mode
                              )
                          : // 완료한 Task 가 실제 시각에 그려져 있으면(계획 | 실제 비교가 아닐 때) 끌어서 실제 시간을 고쳐요
                            enabled && !compare && canDragActual(p.item)
                            ? (e, mode) =>
                                startDrag(
                                  e,
                                  {
                                    kind: 'actual',
                                    task: p.item,
                                    date: d.date,
                                    start: p.start,
                                    end: p.end,
                                    part: p.part ?? 0,
                                  },
                                  mode
                                )
                            : undefined
                      }
                    />
                  )
                })}
              </div>
              {!split && acts.length > 0 && (
                <div
                  className={cn(
                    'pointer-events-none absolute inset-0 *:pointer-events-auto',
                    fadeOthers && !!focusIds?.size && 'opacity-55'
                  )}
                >
                  {acts.map((p) => (
                    <ActivityBlock
                      key={p.item.activityId}
                      placed={p}
                      geometry={geometry}
                      selected={selection?.kind === 'activity' && selection.id === p.item.activityId}
                      onSelect={selectActivity}
                    />
                  ))}
                </div>
              )}
              {split && (
                <div
                  className={cn(
                    'border-border-soft pointer-events-none absolute inset-y-0 right-0 w-(--actual-w) border-l border-dashed *:pointer-events-auto',
                    fadeOthers && !!focusIds?.size && 'opacity-55'
                  )}
                >
                  {doneTasks.map((p) => (
                    <TaskBlock
                      key={`done-${p.item.taskId}-${p.part ?? 0}`}
                      placed={p}
                      geometry={geometry}
                      selected={selection?.kind === 'task' && selection.id === p.item.taskId}
                      onSelect={selectTask}
                      dimmed={
                        drag?.source.kind === 'actual' &&
                        drag.source.task.taskId === p.item.taskId &&
                        drag.source.part === (p.part ?? 0)
                      }
                      // 실제 칸의 완료 Task: 끌어서 옮기고 위·아래 끝으로 늘이고 줄여서 실제 시간을 고쳐요
                      drag={
                        enabled && canDragActual(p.item)
                          ? (e, mode) =>
                              startDrag(
                                e,
                                {
                                  kind: 'actual',
                                  task: p.item,
                                  date: d.date,
                                  start: p.start,
                                  end: p.end,
                                  part: p.part ?? 0,
                                },
                                mode
                              )
                          : undefined
                      }
                    />
                  ))}
                  {acts.map((p) => (
                    <ActivityBlock
                      key={p.item.activityId}
                      placed={p}
                      geometry={geometry}
                      selected={selection?.kind === 'activity' && selection.id === p.item.activityId}
                      onSelect={selectActivity}
                    />
                  ))}
                </div>
              )}
              {drag?.target?.type === 'grid' && drag.target.date === d.date && (
                <DragGhost drag={drag} target={drag.target} from={from} box={ghostBox.source} />
              )}
              {drag &&
                groupGhosts
                  .filter((m) => m.date === d.date)
                  .map((m) => (
                    <GroupGhost
                      key={m.task.taskId}
                      member={m}
                      from={from}
                      invalid={!!drag.invalid}
                      box={ghostBox[`task-${m.task.taskId}`]}
                    />
                  ))}
              {nowLine(d.date) !== null && (
                // 지금 시각 선 (명세 9.6) — 1.5px primary · 왼쪽 끝 8px 원
                <div
                  className="border-action-primary-bg pointer-events-none absolute inset-x-0 z-20 h-0 border-t-[1.5px]"
                  style={{ top: (nowLine(d.date)! - from) * PPM }}
                  aria-hidden
                >
                  <span className="bg-action-primary-bg absolute -top-[4.75px] -left-1 size-2 rounded-full" />
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function UnscheduledCell({
  date,
  tasks,
  carried = [],
  onCarriedClick,
  selection,
  onSelect,
  drag,
  onDragDown,
  onMore,
}: {
  date: string
  tasks: TimeBlockTask[]
  /** 시간 미정이던 Task 가 다른 날로 옮겨진 흔적 */
  carried?: CarriedOutBlock[]
  onCarriedClick?: (c: CarriedOutBlock, fromDate: string, anchor?: AnchorRect) => void
  selection: Selection
  onSelect: (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => void
  drag: DragState | null
  onDragDown?: (e: PointerEvent<HTMLElement>, t: TimeBlockTask) => void
  onMore?: (date: string, remaining: number) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded ? tasks : tasks.slice(0, UNSCHEDULED_VISIBLE)
  const more = tasks.length - shown.length
  const isTarget = drag?.target?.type === 'unscheduled' && drag.target.date === date
  return (
    <div
      data-unscheduled-date={date}
      className={cn(
        'bg-surface border-line sticky top-14 z-30 flex max-h-32 min-h-10 min-w-0 flex-col gap-1 overflow-y-auto border-b border-l p-1.5',
        isTarget && 'bg-brand-soft outline-brand outline-2 -outline-offset-2 outline-dashed'
      )}
    >
      {isTarget && (
        <p className="text-text-brand px-1 text-[11px] font-semibold">
          {drag?.source.date !== date ? '여기에 놓으면 이날 시간 미정' : '여기에 놓으면 시간 미정'}
        </p>
      )}
      {carried.map((c) => (
        <CarriedChip key={`carried-${c.taskId}`} c={c} fromDate={date} onClick={onCarriedClick} />
      ))}
      {shown.map((t) => {
        const draggable = !!onDragDown && canDragTask(t)
        return (
          <button
            key={t.taskId}
            type="button"
            data-task-id={t.taskId}
            onClick={(e) => onSelect(t, e)}
            onPointerDown={draggable ? (e) => onDragDown(e, t) : undefined}
            style={{ ...taskColors(t), WebkitTouchCallout: 'none', userSelect: 'none' }}
            className={cn(
              // 왼쪽은 각지게 — 왼쪽 목표 색 막대가 위아래 끝까지 곧게 (TimeBlock 과 같게, 2026-10)
              'rounded-r-event text-label-sm shrink-0 truncate py-1 pr-2 pl-3 text-left',
              draggable && 'cursor-grab',
              t.taskStatus === 'COMPLETED' && 'line-through opacity-55',
              drag?.source.kind === 'task' && drag.source.task.taskId === t.taskId && 'opacity-30',
              selection?.kind === 'task' && selection.id === t.taskId && 'ring-ink ring-2'
            )}
          >
            {t.emoji ? `${t.emoji} ` : ''}
            {t.taskName}
          </button>
        )
      })}
      {more > 0 && (
        <button
          type="button"
          onClick={() => {
            onMore?.(date, more)
            setExpanded(true)
          }}
          className="text-ink-3 hover:text-ink px-1 text-left text-[11px]"
        >
          +{more}개 더보기
        </button>
      )}
    </div>
  )
}

/** "11/19" */
const shortDate = (ymd: string) => `${Number(ymd.slice(5, 7))}/${Number(ymd.slice(8, 10))}`

/**
 * 못 해서 다른 날로 옮겨진 Task 의 흔적 (2026-10) — 원래 자리에 흐린 점선 "* 이름 → 11/19".
 * 끌 수 없고 실행률에도 안 들어가요. 누르면 옮겨 간 날로 가요.
 */
function CarriedTraces({
  day,
  from,
  to,
  right,
  onClick,
}: {
  day: TimetableDay
  from: number
  to: number
  right: string
  onClick?: (c: CarriedOutBlock, fromDate: string, anchor?: AnchorRect) => void
}) {
  const list = (day.carriedOut ?? []).flatMap((c) => {
    if (!c.start) return []
    const s = Math.max(minutesFrom(day.date, c.start), from)
    const e = Math.min(c.end ? minutesFrom(day.date, c.end) : s + 30, to)
    return e > s ? [{ c, s, e }] : []
  })
  if (!list.length) return null
  return (
    <div className="pointer-events-none absolute inset-y-0 left-0" style={{ right }}>
      {list.map(({ c, s, e }) => {
        const h = Math.max((e - s) * PPM - 2, 16)
        return (
          <button
            key={`carried-${c.taskId}`}
            type="button"
            disabled={!onClick}
            onClick={(ev) => {
              ev.stopPropagation()
              onClick?.(c, day.date, rectOf(ev.currentTarget))
            }}
            title={`못 한 일 · ${shortDate(c.toDate)}로 옮겼어요 — 눌러서 그때 했다고 남기거나 되돌려요`}
            className="rounded-event text-label-sm text-ink-3 pointer-events-auto absolute inset-x-[2px] flex flex-col overflow-hidden border border-dashed py-0.5 pr-1.5 pl-2 text-left opacity-70 hover:opacity-100 disabled:cursor-default"
            style={{
              top: (s - from) * PPM,
              height: h,
              borderColor: withAlpha(hexOf(c.goalColorCode), 0.7),
              background: withAlpha(hexOf(c.goalColorCode), 0.06),
            }}
          >
            <span className="truncate">
              <b className="text-danger mr-0.5">*</b>
              <s className="decoration-ink-4">
                {c.emoji ? `${c.emoji} ` : ''}
                {c.taskName}
              </s>
            </span>
            {h >= 30 && <span className="truncate text-[10px] font-semibold">→ {shortDate(c.toDate)}로 옮김</span>}
          </button>
        )
      })}
    </div>
  )
}

function CarriedChip({
  c,
  fromDate,
  onClick,
}: {
  c: CarriedOutBlock
  fromDate: string
  onClick?: (c: CarriedOutBlock, fromDate: string, anchor?: AnchorRect) => void
}) {
  return (
    <button
      type="button"
      disabled={!onClick}
      onClick={(ev) => onClick?.(c, fromDate, rectOf(ev.currentTarget))}
      title={`못 한 일 · ${shortDate(c.toDate)}로 옮겼어요`}
      className="rounded-event text-label-sm text-ink-3 border-line shrink-0 truncate border border-dashed py-1 pr-2 pl-2 text-left opacity-75 hover:opacity-100 disabled:cursor-default"
    >
      <b className="text-danger mr-0.5">*</b>
      <s className="decoration-ink-4">
        {c.emoji ? `${c.emoji} ` : ''}
        {c.taskName}
      </s>
      <span className="ml-1 text-[10px] font-semibold">→ {shortDate(c.toDate)}</span>
    </button>
  )
}

/** 묶음 드래그 중 같이 움직이는 Task 의 새 자리 */
/** 미리보기 가로 자리 — 끌기 시작할 때 블록 자리(비율) 그대로, 모르면 칸 전체 */
const ghostX = (box?: { left: number; width: number }) =>
  box ? { left: `${box.left * 100}%`, width: `${box.width * 100}%` } : { left: 2, right: 2 }

function GroupGhost({
  member,
  from,
  invalid,
  box,
}: {
  member: GroupMember
  from: number
  invalid: boolean
  box?: { left: number; width: number }
}) {
  const t = member.task
  return (
    <div
      aria-hidden
      className={cn(
        'rounded-event text-label-sm shadow-pop pointer-events-none absolute z-30 flex flex-col overflow-hidden border py-1 pr-2 pl-3 opacity-90',
        invalid ? 'border-danger ring-danger ring-2' : 'border-(--goal-accent)'
      )}
      style={{
        ...taskColors(t),
        ...ghostX(box),
        top: (member.start - from) * PPM,
        height: Math.max((member.end - member.start) * PPM - 2, 14),
      }}
    >
      <span className="truncate font-semibold">
        {t.emoji ? `${t.emoji} ` : ''}
        {t.taskName}
      </span>
      {(member.end - member.start) * PPM >= 34 && (
        <span className="truncate font-bold tabular-nums">
          {formatMinutes(member.start)} – {formatMinutes(member.end)}
        </span>
      )}
    </div>
  )
}

/** 드래그 중 놓일 자리 미리보기 */
function DragGhost({
  drag,
  target,
  from,
  box,
}: {
  drag: DragState
  target: Extract<DropTarget, { type: 'grid' }>
  from: number
  box?: { left: number; width: number }
}) {
  const { source, invalid } = drag
  const title =
    source.kind === 'task' || source.kind === 'actual'
      ? `${source.task.emoji ? `${source.task.emoji} ` : ''}${source.task.taskName}${source.kind === 'actual' ? ' · 실제' : ''}`
      : source.block.title
  const style =
    source.kind === 'task' || source.kind === 'actual'
      ? taskColors(source.task)
      : { ...HATCH, color: 'var(--color-text-secondary)' }
  return (
    <div
      data-drag-ghost
      aria-live="polite"
      className={cn(
        'rounded-event text-label-sm shadow-pop pointer-events-none absolute z-30 flex flex-col overflow-hidden border py-1 pr-2 pl-3',
        invalid ? 'border-danger ring-danger ring-2' : 'border-[var(--goal-accent,var(--color-border-subtle))]'
      )}
      style={{
        ...style,
        ...ghostX(box),
        top: (target.start - from) * PPM,
        height: Math.max((target.end - target.start) * PPM - 2, 18),
      }}
    >
      <span className="truncate font-semibold">{title}</span>
      <span className="truncate font-bold tabular-nums">
        {formatMinutes(target.start)} – {formatMinutes(target.end)}
      </span>
      {invalid && (
        <span className="bg-danger mt-auto self-start rounded px-1 py-0.5 text-[10px] font-semibold text-white">
          {invalid}
        </span>
      )}
    </div>
  )
}

/**
 * 시간대를 정한 습관 (V56) — 캘린더의 그 시간에 연한 블록으로. 누르면 체크 · 체크 풀기.
 * Task 블록보다 아래 층이라 겹치면 Task 가 위에 보여요 (자동 배치는 이 시간을 비워 둬요).
 */
function HabitBlocks({
  day,
  from,
  to,
  right,
  onToggle,
}: {
  day: TimetableDay
  from: number
  to: number
  right: string
  onToggle?: (habit: HabitBlock, date: string) => void
}) {
  const toggle = useToggleHabitBlock()
  const list = day.habits ?? []
  if (!list.length) return null
  return (
    <>
      {list.map((h) => {
        const s = Math.max(minutesFrom(day.date, h.start), from)
        const e = Math.min(minutesFrom(day.date, h.end), to)
        if (e <= s) return null
        const height = (e - s) * PPM
        return (
          <button
            key={`habit-${h.habitId}`}
            type="button"
            onClick={(ev) => {
              ev.stopPropagation()
              onToggle?.(h, day.date)
              toggle.mutate({ habitId: h.habitId, date: day.date, checked: h.checked })
            }}
            disabled={toggle.isPending}
            title={`${h.name} ${hm(h.start)}–${hm(h.end)} · 눌러서 ${h.checked ? '체크 풀기' : '체크'}`}
            className={cn(
              'absolute left-0.5 z-[1] flex items-start gap-1 overflow-hidden rounded-md border border-dashed px-1.5 py-0.5 text-left text-[11px] font-semibold',
              h.checked
                ? 'border-success/50 bg-success-soft text-success'
                : 'border-success/40 bg-success-soft/40 text-success hover:bg-success-soft'
            )}
            style={{ top: (s - from) * PPM, height: Math.max(height, 16), right: `calc(${right} + 2px)` }}
          >
            {h.checked ? (
              <CheckCircle2 className="mt-px size-3 shrink-0" />
            ) : (
              <Circle className="mt-px size-3 shrink-0" />
            )}
            <span className={cn('truncate', h.checked && 'line-through')}>
              {h.emoji ? `${h.emoji} ` : ''}
              {h.name}
            </span>
          </button>
        )
      })}
    </>
  )
}
