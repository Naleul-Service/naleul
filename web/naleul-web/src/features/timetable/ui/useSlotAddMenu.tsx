'use client'

import { useState, type MouseEvent, type ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { NotebookPen, Pencil, Repeat, Sparkles, SquarePen, Trash2 } from '@/components/icons'
import type { AnchorRect } from '@/components/ui/Popover'
import { track as trackEvent, type EventProps } from '@/lib/client/analytics'
import { dateRelationOf, errorStatusOf } from '@/features/home/analytics'
import { ADD_MODE_LABEL } from '@/features/brain-dump/ui/TaskAddPanel'
import { useCreateLifePattern } from '@/features/life-pattern/api'
import { ALL_DAYS } from '@/features/life-pattern/presets'
import { LifePatternFormModal, type LifePatternFormValue } from '@/features/life-pattern/ui/LifePatternFormModal'
import { useLifePatterns } from '../api'
import { addDays, nowKst, weekdayIndex } from '../time'
import type { TimeBlockTask } from '../types'
import type { ActivityDraft } from './ActivityForm'
import { ActionMenu, type ActionMenuItem } from './ActionMenu'

type Slot = { date: string; minutes: number }
type Trigger = 'left_click' | 'right_click'
type EntrySource = 'empty_tap' | 'header_button' | 'context_menu'

/** 이 메뉴가 보내는 이벤트 종류 — 화면마다 이름 앞부분만 달라요 (calendar.* / home.calendar_*) */
export type SlotMenuEvent = 'task_add_click' | 'activity_add_click' | 'context_menu_open' | 'context_menu_select'

/** 계획 할 일 추가 패널에 미리 채울 날짜 · 시각 */
export type AddPrefill = { date: string; startTime: string; endTime: string }

const pad = (n: number) => String(n).padStart(2, '0')
const hhmm = (m: number) => `${pad(Math.floor(m / 60) % 24)}:${pad(m % 60)}`
/** 자정 뒤 칸(24:00~)은 다음 날 */
const normalize = (slot: Slot): Slot =>
  slot.minutes >= 1440 ? { date: addDays(slot.date, 1), minutes: slot.minutes - 1440 } : slot
const pointAnchor = (e: { clientX: number; clientY: number }): AnchorRect => ({
  top: e.clientY,
  bottom: e.clientY,
  left: e.clientX,
  right: e.clientX,
})

/**
 * TimeTable 의 "추가" 메뉴 — 캘린더 일간 · 주간과 홈 오늘 일정이 똑같이 써요 (2026-10).
 *  - 머리글 "Task 추가": AI로 자동 Task 추가 · 계획 할 일 추가 · 실제 한 일 추가
 *  - 빈 칸 왼쪽 클릭 · 우클릭: 위 3개 + 기본 생활 패턴 추가 (이날만 / 앞으로 계속)
 *  - Task 블록 우클릭: 수정 · 삭제
 * AI · 계획 할 일은 onAdd 로 넘겨서 화면이 Task 추가 패널을 열어요. 실제 한 일 · 수정 · 삭제는 TimeTable 조작 훅의 함수를 써요.
 */
export function useSlotAddMenu({
  viewMode,
  viewDate,
  today,
  singleDay,
  track,
  onAdd,
  patternEntrySource,
}: {
  /** 분석 view_mode (홈은 undefined) */
  viewMode?: 'day' | 'week' | 'month'
  /** 지금 보고 있는 날 (머리글에서 "실제 한 일"을 열 때 지난 날이면 그날) */
  viewDate: string
  today: string
  /** 하루만 보는 화면 (일간 · 홈) */
  singleDay: boolean
  track: (event: SlotMenuEvent, props: EventProps) => void
  /** AI · 계획 할 일 추가를 골랐을 때 (at: 빈 칸을 눌러 열었으면 그 날짜 · 시각) */
  onAdd: (mode: 'ai' | 'manual', at: Slot | undefined, prefill: AddPrefill | undefined) => void
  /** life_pattern.save_result entry_source */
  patternEntrySource: (trigger: Trigger) => string
}) {
  const [menu, setMenu] = useState<{
    kind: 'header' | 'slot' | 'task'
    anchor: AnchorRect
    slot?: Slot
    trigger?: Trigger
    task?: TimeBlockTask
  } | null>(null)
  // 기본 생활 패턴(고정 시간) 추가 창에 미리 채울 값 · 연 빈 칸의 날짜 · 어느 클릭으로 열었는지
  const [fixedForm, setFixedForm] = useState<LifePatternFormValue | null>(null)
  const [fixedAt, setFixedAt] = useState<{ date: string; trigger: Trigger } | null>(null)
  const lifePatterns = useLifePatterns(!!fixedForm)
  const createPattern = useCreateLifePattern()
  const router = useRouter()

  const slotProps = (slot: Slot) => {
    const at = normalize(slot)
    const nowMin = nowKst().minutes
    return {
      at,
      props: {
        view_mode: viewMode,
        view_date: at.date,
        date_relation: dateRelationOf(at.date),
        slot_minutes: at.minutes,
        is_past: at.date < today || (at.date === today && at.minutes < nowMin),
      } satisfies EventProps,
    }
  }
  const taskProps = (t: TimeBlockTask) => ({
    view_mode: viewMode,
    task_id: t.taskId,
    source_type: t.sourceType,
    task_status: t.taskStatus,
    is_scheduled: !!t.plannedStartAt,
  })

  /** + 메뉴 3개 (명세 11.2) */
  const addItems = (
    slot: Slot | undefined,
    anchor: AnchorRect,
    source: EntrySource,
    openActivity: (draft: ActivityDraft, anchor: AnchorRect) => void
  ): ActionMenuItem[] => {
    const at = slot ? normalize(slot) : undefined
    const nowMin = nowKst().minutes
    const future = !!at && (at.date > today || (at.date === today && at.minutes >= nowMin))
    return [
      {
        key: 'ai',
        label: ADD_MODE_LABEL.ai,
        icon: <Sparkles />,
        onSelect: () => {
          track('task_add_click', {
            view_mode: viewMode,
            view_date: at?.date ?? viewDate,
            add_mode: 'ai',
            entry_source: source,
          })
          onAdd('ai', at, undefined)
        },
      },
      {
        key: 'manual',
        label: ADD_MODE_LABEL.manual,
        icon: <SquarePen />,
        onSelect: () => {
          track('task_add_click', {
            view_mode: viewMode,
            view_date: at?.date ?? viewDate,
            add_mode: 'manual',
            entry_source: source,
          })
          onAdd(
            'manual',
            at,
            at
              ? { date: at.date, startTime: hhmm(at.minutes), endTime: hhmm(Math.min(at.minutes + 60, 1439)) }
              : undefined
          )
        },
      },
      {
        key: 'log',
        label: ADD_MODE_LABEL.log,
        icon: <NotebookPen />,
        disabled: future,
        hint: future ? '지난 시간만 남길 수 있어요' : undefined,
        onSelect: () => {
          if (at) {
            track('activity_add_click', {
              view_mode: viewMode,
              date_relation: dateRelationOf(at.date),
              entry_source: source,
            })
            openActivity({ date: at.date, start: at.minutes, end: at.minutes + 60 }, anchor)
            return
          }
          // 머리글에서 열면 오늘(또는 보고 있는 지난 날)의 최근 1시간
          const day = singleDay && viewDate < today ? viewDate : today
          const endMin = day === today ? Math.floor(nowKst().minutes / 10) * 10 : 13 * 60
          track('activity_add_click', { view_mode: viewMode, date_relation: dateRelationOf(day), entry_source: source })
          openActivity({ date: day, start: Math.max(endMin - 60, 0), end: endMin }, anchor)
        },
      },
    ]
  }

  /** 빈 칸 메뉴 — 왼쪽 클릭 · 우클릭 모두 같은 4개 */
  const slotItems = (
    slot: Slot,
    anchor: AnchorRect,
    trigger: Trigger,
    openActivity: (draft: ActivityDraft, anchor: AnchorRect) => void
  ): ActionMenuItem[] => {
    const { at, props } = slotProps(slot)
    const base = addItems(slot, anchor, trigger === 'left_click' ? 'empty_tap' : 'context_menu', openActivity)
    const pick = (key: string, option: string) => {
      const item = base.find((i) => i.key === key)!
      return {
        ...item,
        onSelect: () => {
          track('context_menu_select', { ...props, target: 'empty_slot', option, trigger })
          item.onSelect()
        },
      }
    }
    return [
      pick('ai', 'ai_add'),
      pick('manual', 'plan_add'),
      pick('log', 'actual_add'),
      {
        key: 'pattern',
        label: '비워둘 시간 추가',
        icon: <Repeat />,
        hint: '낮잠 · 외출처럼 Task를 넣지 않을 시간 (오늘만 · 앞으로 계속)',
        onSelect: () => {
          track('context_menu_select', { ...props, target: 'empty_slot', option: 'pattern_add', trigger })
          setFixedAt({ date: at.date, trigger })
          setFixedForm({
            patternType: 'CUSTOM',
            title: '',
            emoji: '',
            startTime: hhmm(at.minutes),
            endTime: hhmm(Math.min(at.minutes + 60, 1439)),
            days: [ALL_DAYS[weekdayIndex(at.date)]],
          })
        },
      },
    ]
  }

  const taskItems = (
    t: TimeBlockTask,
    anchor: AnchorRect,
    openTaskDetail: (t: TimeBlockTask, anchor: AnchorRect) => void,
    requestDeleteTask: (t: TimeBlockTask, anchor: AnchorRect) => void
  ): ActionMenuItem[] => [
    {
      key: 'edit',
      label: '수정',
      icon: <Pencil />,
      onSelect: () => {
        track('context_menu_select', { ...taskProps(t), target: 'task_block', option: 'edit' })
        openTaskDetail(t, anchor)
      },
    },
    ...(t.sourceType === 'MISSION'
      ? []
      : [
          {
            key: 'delete',
            label: '삭제',
            icon: <Trash2 />,
            onSelect: () => {
              track('context_menu_select', { ...taskProps(t), target: 'task_block', option: 'delete' })
              requestDeleteTask(t, anchor)
            },
          },
        ]),
  ]

  const nowStamp = () => {
    const n = nowKst()
    return `${n.date}T${hhmm(n.minutes)}`
  }

  return {
    /** 머리글 "Task 추가" 버튼 — 메뉴 열기는 이벤트 없음, 항목을 고를 때 보내요 */
    openHeader: (anchor: AnchorRect) => setMenu({ kind: 'header', anchor }),
    /** useTimetableInteractions 의 onEmptyMenu 로 넘겨요 (빈 칸 왼쪽 클릭) */
    openSlot: (slot: Slot, anchor: AnchorRect) => setMenu({ kind: 'slot', anchor, slot, trigger: 'left_click' }),
    /** TimeGrid onEmptyContextMenu (빈 칸 우클릭) */
    onEmptyContextMenu: (date: string, minutes: number, e: MouseEvent<HTMLElement>, close: () => void) => {
      close()
      track('context_menu_open', { ...slotProps({ date, minutes }).props, target: 'empty_slot' })
      setMenu({ kind: 'slot', anchor: pointAnchor(e), slot: { date, minutes }, trigger: 'right_click' })
    },
    /** TimeGrid onTaskContextMenu (Task 블록 우클릭) */
    onTaskContextMenu: (t: TimeBlockTask, e: MouseEvent<HTMLElement>, close: () => void) => {
      close()
      track('context_menu_open', {
        ...taskProps(t),
        target: 'task_block',
        is_past: !!t.plannedEndAt && t.plannedEndAt.slice(0, 16) < nowStamp(),
      })
      setMenu({ kind: 'task', anchor: pointAnchor(e), task: t })
    },
    /** 메뉴 · 기본 생활 패턴 추가 창 — TimeTable 조작 훅의 함수를 받아서 그려요 */
    render: (actions: {
      openActivity: (draft: ActivityDraft, anchor: AnchorRect) => void
      openTaskDetail: (t: TimeBlockTask, anchor: AnchorRect) => void
      requestDeleteTask: (t: TimeBlockTask, anchor: AnchorRect) => void
    }): ReactNode => {
      const closeMenu = () => setMenu(null)
      const closeFixed = () => {
        setFixedForm(null)
        setFixedAt(null)
      }
      return (
        <>
          {menu?.kind === 'header' && (
            <ActionMenu
              anchor={menu.anchor}
              onClose={closeMenu}
              label="추가"
              items={addItems(undefined, menu.anchor, 'header_button', actions.openActivity)}
            />
          )}
          {menu?.kind === 'slot' && menu.slot && (
            <ActionMenu
              anchor={menu.anchor}
              onClose={closeMenu}
              label="이 시간에 추가"
              items={slotItems(menu.slot, menu.anchor, menu.trigger ?? 'left_click', actions.openActivity)}
            />
          )}
          {menu?.kind === 'task' && menu.task && (
            <ActionMenu
              anchor={menu.anchor}
              onClose={closeMenu}
              label="Task"
              items={taskItems(menu.task, menu.anchor, actions.openTaskDetail, actions.requestDeleteTask)}
            />
          )}
          {fixedForm && (
            <LifePatternFormModal
              initial={fixedForm}
              patterns={lifePatterns.data ?? []}
              loading={createPattern.isPending}
              scopeDate={fixedAt?.date}
              scopeIsToday={fixedAt?.date === today}
              onEditExisting={() => {
                closeFixed()
                router.push('/settings/life-pattern')
              }}
              onClose={closeFixed}
              onSubmit={(v) => {
                const entry = patternEntrySource(fixedAt?.trigger ?? 'right_click')
                const base = {
                  is_new: true,
                  pattern_type: v.patternType,
                  pattern_scope: v.onlyDate ? 'once' : 'repeat',
                  entry_source: entry,
                }
                createPattern.mutate(v, {
                  onSuccess: () => {
                    trackLifePattern({ ...base, result: 'success' })
                    closeFixed()
                  },
                  onError: (err: unknown) =>
                    trackLifePattern({ ...base, result: 'fail', error_status: errorStatusOf(err) }),
                })
              }}
            />
          )}
        </>
      )
    },
  }
}

// life_pattern.save_result 는 화면과 상관없이 같은 이름이에요
const trackLifePattern = (props: EventProps) => trackEvent('life_pattern.save_result', props)
