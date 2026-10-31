'use client'

import { useEffect, useMemo, useState, type MouseEvent } from 'react'
import Link from 'next/link'
import { createPortal } from 'react-dom'
import { CalendarDays, Plus } from '@/components/icons'
import { rectOf } from '@/components/ui/Popover'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import { useActivities } from '@/features/timetable/api'
import { visibleHours } from '@/features/timetable/layout'
import { todayKst } from '@/features/timetable/time'
import type { ActualActivity, FixedBlock, TimeBlockTask, TimetableDay } from '@/features/timetable/types'
import { canDragTask, fixedKey, TimeGrid } from '@/features/timetable/ui/TimeGrid'
import type { DragCancelReason, DragMode, DragSource, DropTarget } from '@/features/timetable/ui/useGridDrag'
import { errorStatusOf, HE, useHomeAnalytics } from '../analytics'
import { useTimetableInteractions } from '@/features/timetable/ui/useTimetableInteractions'
import { useSlotAddMenu, type AddPrefill, type SlotMenuEvent } from '@/features/timetable/ui/useSlotAddMenu'
import { TaskAddPanel } from '@/features/brain-dump/ui/TaskAddPanel'

/**
 * 홈의 오늘 일간 캘린더 — 캘린더 화면의 일간 보기(계획 | 실제)를 그대로 넣었어요.
 * 블록을 눌러 상세 · 완료, 끌어서 시간 바꾸기까지 똑같이 돼요.
 * 추가도 캘린더 일간과 같아요 (2026-10): 머리글 "추가" · 빈 칸 왼쪽 클릭/우클릭 → AI로 자동 Task 추가 · 계획 할 일 추가 ·
 * 실제 한 일 추가 · 기본 생활 패턴 추가, Task 블록 우클릭 → 수정 · 삭제. AI · 계획 할 일은 홈 위에 Task 추가 패널을 열어요.
 */
export function HomeDayCalendar({
  day,
  className,
  dayLabel = '오늘',
}: {
  day: TimetableDay
  className?: string
  /** 홈에서 다른 날을 보고 있으면 "이날" */
  dayLabel?: string
}) {
  const activities = useActivities(day.date, day.date)
  const acts = useMemo(() => activities.data ?? [], [activities.data])
  const rawDays = useMemo(() => [day], [day])
  const { trackHome, once } = useHomeAnalytics()
  // Task 추가 패널 (AI로 자동 Task 추가 · 계획 할 일 추가) + 방금 추가한 Task 에 NEW 표시
  const [panel, setPanel] = useState<{ mode: 'ai' | 'manual'; prefill?: AddPrefill } | null>(null)
  const [newIds, setNewIds] = useState<ReadonlySet<number>>(new Set())
  const slotMenu = useSlotAddMenu({
    viewDate: day.date,
    today: todayKst(),
    singleDay: true,
    track: (event, props) => trackHome(HOME_SLOT_EVENT[event], props),
    onAdd: (mode, _at, prefill) => setPanel({ mode, prefill: mode === 'manual' ? prefill : undefined }),
    patternEntrySource: (trigger) => (trigger === 'left_click' ? 'home_empty_tap' : 'home_context_menu'),
  })
  const {
    days,
    selection,
    onSelectTask,
    onSelectFixed,
    onSelectActivity,
    onEmptyClick,
    openActivity,
    openTaskDetail,
    requestDeleteTask,
    previewFill,
    onDrop,
    close,
    overlays,
    groupIds,
    focusIds,
    fadeOthers,
  } = useTimetableInteractions(rawDays, day.date, { surface: 'home', onEmptyMenu: slotMenu.openSlot })
  const { startHour, endHour } = useMemo(() => visibleHours(days, acts), [days, acts])

  // 실제로 한 일만 따로 실패하면 화면엔 안 보이고 빠져요 (조용한 실패) → 분석에만 남겨요
  useEffect(() => {
    if (!activities.isError) return
    once(`section_fail:activities:${day.date}`, () =>
      trackHome(HE.sectionLoadFail, { failed_section: 'activities', error_status: errorStatusOf(activities.error) })
    )
  }, [activities.isError, activities.error, day.date, once, trackHome])

  // ── 분석: 원래 동작은 그대로 두고 앞에서 이벤트만 보내요 ──
  const selectTask = (t: TimeBlockTask, e: MouseEvent<HTMLElement>) => {
    const modifier = e.shiftKey || e.metaKey || e.ctrlKey
    if (modifier && !!t.plannedStartAt && canDragTask(t) && !t.proposed) {
      const removing = groupIds.has(t.taskId)
      trackHome(HE.calendarTaskGroupSelect, {
        task_id: t.taskId,
        group_action: removing ? 'remove' : 'add',
        group_size: groupIds.size + (removing ? -1 : 1),
      })
    } else if (!(selection?.kind === 'task' && selection.id === t.taskId)) {
      // 같은 블록을 다시 눌러 닫을 땐 보내지 않아요
      trackHome(HE.calendarTaskBlockClick, {
        task_id: t.taskId,
        source_type: t.sourceType,
        task_status: t.taskStatus,
        is_scheduled: !!t.plannedStartAt,
        goal_category_id: t.goalCategoryId ?? undefined,
      })
    }
    onSelectTask(t, e)
  }
  const selectFixed = (b: FixedBlock, e: MouseEvent<HTMLElement>) => {
    if (!(selection?.kind === 'fixed' && selection.key === fixedKey(b))) {
      trackHome(HE.calendarFixedBlockClick, { pattern_type: b.patternType })
    }
    onSelectFixed(b, e)
  }
  const selectActivity = (a: ActualActivity, e: MouseEvent<HTMLElement>) => {
    if (!(selection?.kind === 'activity' && selection.id === a.activityId)) trackHome(HE.calendarActivityBlockClick)
    onSelectActivity(a, e)
  }
  const emptyClick = (date: string, minutes: number, e: MouseEvent<HTMLElement>) => {
    trackHome(HE.calendarEmptySlotClick, { slot_minutes: minutes })
    onEmptyClick(date, minutes, e)
  }
  const drop = (source: DragSource, target: DropTarget, mode: DragMode) => {
    const task = source.kind === 'fixed' ? null : source.task
    trackHome(HE.calendarDragDrop, {
      drag_source: dragSourceOf(source),
      drag_mode: dragModeOf(mode),
      drop_target: target.type,
      task_id: task?.taskId,
      source_type: task?.sourceType,
      // 같은 날 루틴 · 고정 시간은 놓은 뒤 "어디까지 바꿀까요?"를 물어요 (useTimetableInteractions 와 같은 조건)
      needs_scope:
        target.type === 'grid' &&
        !task?.proposed &&
        (source.kind === 'fixed' ||
          (source.kind === 'task' &&
            !source.group?.length &&
            source.task.sourceType === 'ROUTINE' &&
            (source.task.date ?? source.task.plannedStartAt?.slice(0, 10)) === target.date)),
      group_size: source.kind === 'task' && source.group?.length ? source.group.length + 1 : undefined,
    })
    onDrop(source, target)
  }
  const dragCancel = (source: DragSource, mode: DragMode, reason: DragCancelReason, invalid?: string) =>
    trackHome(HE.calendarDragCancel, {
      drag_source: dragSourceOf(source),
      drag_mode: dragModeOf(mode),
      cancel_reason: reason === 'invalid' ? invalidReasonOf(invalid) : reason,
    })

  return (
    <Card data-home-section="calendar" className={cn('flex min-h-0 flex-col overflow-hidden p-0', className)}>
      <div className="flex shrink-0 flex-wrap items-center gap-1 px-4 pt-4 pb-2 sm:px-5">
        <h2 className="text-h4">{dayLabel} 일정</h2>
        <div className="ml-auto flex items-center gap-1">
          {/* 캘린더 머리글 "Task 추가"와 같은 메뉴 — AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가 */}
          <Button
            variant="ghost"
            size="sm"
            aria-haspopup="menu"
            onClick={(e) => slotMenu.openHeader(rectOf(e.currentTarget))}
          >
            <Plus className="size-4" />
            추가
          </Button>
          <Link
            href={`/calendar?view=day&date=${day.date}`}
            onClick={() => trackHome(HE.calendarLinkClick)}
            className="text-ink-3 hover:text-ink hover:bg-subtle flex h-8 items-center gap-1 rounded-lg px-2.5 text-[13px] font-semibold"
          >
            <CalendarDays className="size-4" />
            캘린더
          </Link>
        </div>
      </div>
      <div className="min-h-0 flex-1 px-2 pb-2 sm:px-3 sm:pb-3">
        <TimeGrid
          days={days}
          startHour={startHour}
          endHour={endHour}
          selection={selection}
          onSelectTask={selectTask}
          onSelectFixed={selectFixed}
          activities={acts}
          onSelectActivity={selectActivity}
          onEmptyClick={emptyClick}
          // 앞으로의 빈 칸도 눌러서 계획 할 일을 넣을 수 있어요 (메뉴의 "실제 한 일"만 지난 시간 전용)
          allowFutureEmpty
          onEmptyContextMenu={(d, minutes, e) => slotMenu.onEmptyContextMenu(d, minutes, e, close)}
          onTaskContextMenu={(t, e) => slotMenu.onTaskContextMenu(t, e, close)}
          highlightIds={newIds}
          onDrop={drop}
          onDragCancel={dragCancel}
          onDragStart={close}
          groupIds={groupIds}
          focusIds={focusIds}
          fadeOthers={fadeOthers}
          // 넓은 화면: 칸 높이를 꽉 채우고 안에서 스크롤 (지금 시각 근처로 열려요) · 좁은 화면: 520px
          maxHeightClass="max-h-[520px] lg:max-h-none lg:h-full"
          planVsActual
        />
      </div>
      {overlays}
      {slotMenu.render({ openActivity, openTaskDetail, requestDeleteTask })}
      {/* 카드 안(overflow · transform)에 갇히지 않게 body 로 띄워요 */}
      {panel &&
        typeof document !== 'undefined' &&
        createPortal(
          <TaskAddPanel
            floating
            mode={panel.mode}
            prefill={panel.prefill}
            onClose={() => setPanel(null)}
            onAdded={(info) => {
              setNewIds((prev) => new Set([...prev, ...info.taskIds]))
              // AI로 정리한 Task 는 캘린더처럼 점선으로 먼저 보여 주고 아래 바에서 "이대로 진행할까요?"
              if (info.pendingTaskIds?.length) previewFill({ taskIds: info.pendingTaskIds }, { kind: 'new' })
            }}
          />,
          document.body
        )}
    </Card>
  )
}

/** drag_source: task 계획 블록 / actual 실제로 한 시간 / fixed 고정 시간 / group 묶음 / proposal AI 배치 미리보기 */
function dragSourceOf(s: DragSource) {
  if (s.kind === 'task') return s.task.proposed ? 'proposal' : s.group?.length ? 'group' : 'task'
  return s.kind
}
/** drag_mode: move / resize_start(위 끝) / resize_end(아래 끝) */
const dragModeOf = (m: DragMode) => (m === 'resize-top' ? 'resize_start' : m === 'resize' ? 'resize_end' : 'move')
/** 빨간 안내 문구 → cancel_reason (TimeGrid 의 validate 문구와 맞춰요) */
function invalidReasonOf(message?: string) {
  if (!message) return 'invalid'
  if (message.includes('겹쳐 있어요')) return 'crowded'
  if (message.includes('기상') || message.includes('취침')) return 'sleep_rule'
  if (message.includes('나눠서')) return 'actual_overlap'
  return 'invalid'
}

/** 추가 메뉴 이벤트 → 홈 이벤트 이름 (캘린더는 calendar.* 로 같은 값을 보내요) */
const HOME_SLOT_EVENT: Record<SlotMenuEvent, (typeof HE)[keyof typeof HE]> = {
  task_add_click: HE.calendarTaskAddClick,
  activity_add_click: HE.calendarAddActivityClick,
  context_menu_open: HE.calendarContextMenuOpen,
  context_menu_select: HE.calendarContextMenuSelect,
}
