'use client'

import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Ellipsis, Flag, Layers, Link2, PinOff, Undo2 } from 'lucide-react'
import { CalendarClock, Check, Clock, Pencil, Repeat, Trash, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { track, type EventProps } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { isWorkGoal, useGoalCategories, useGoalCategory } from '@/features/goal/api'
import { useUpdateWorkTask, useWorkBoard } from '@/features/work/api'
import { InlineConfirm } from '@/features/goal/edit/inline'
import { useUpdateGoalTask, type GoalTaskInput } from '@/features/goal/edit/mutations'
import { useChangePriority, useRescheduleTask, useRoutineScopedReschedule, type ChangeScope } from '../api'
import { goalAccent } from '@/lib/goalTone'
import {
  addDays,
  formatDuration,
  formatMonthDay,
  minutesFrom,
  minutesToTime,
  nowKst,
  timeToMinutes,
  toDateTime,
  todayKst,
} from '../time'
import type { TaskPriority, TimeBlockTask } from '../types'
import type { TimetableSurface } from '../analytics'
import { ScopeChips, scopeOptions } from './ScopeChooser'
import { AIBadge } from '@/components/ui/AIBadge'
import { PRIORITY_LABEL, PriorityChips, priorityOf } from './Priority'

export interface TaskActions {
  onToggleComplete: (t: TimeBlockTask) => void
  onUnlock: (t: TimeBlockTask) => void
  onUnschedule: (t: TimeBlockTask) => void
  /** 다른 날로 옮기기 (2026-10) — 같은 시각으로 놓아 보고, 자리가 없으면 그날 시간 미정 */
  onMoveDate?: (t: TimeBlockTask, date: string, label: string) => void
  /** 삭제 확인 띄우기 (Delete 키와 같은 동작) */
  onDelete: (t: TimeBlockTask) => void
  /** "계획과 다른 시간에 했어요" — 실제 시각을 입력하고 완료 (나의 패턴 시작 지연 계산용) */
  onCompleteWithTime?: (t: TimeBlockTask) => void
  /** "이 시간에 다른 일을 했어요" — 계획 대신 실제로 한 일 기록 */
  onRecordInstead?: (t: TimeBlockTask) => void
  /** 앞뒤로 바로 이어진 Task 개수 (자기 포함). 2개 이상이면 "묶어서 옮기기"를 보여줘요 */
  chainSize?: (t: TimeBlockTask) => number
  /** 이어진 Task 들을 묶음으로 선택 → 하나를 끌면 같이 움직여요 */
  onSelectChain?: (t: TimeBlockTask) => void
}

/** 어떤 작업을 할 수 있는지 (백엔드 규칙과 같게) */
export function taskPermissions(t: TimeBlockTask) {
  const done = t.taskStatus === 'COMPLETED'
  const mission = t.sourceType === 'MISSION'
  return {
    editTime: !done && !mission, // 완료·미션 Task 는 시간 변경 409
    // 이름·세부 목표·마일스톤·마감일: 목표에 연결된 일회성 Task 만 (루틴은 루틴에서, 미션은 방장이 정함)
    editContent: t.sourceType === 'MANUAL' && !!t.goalCategoryId && !!t.generalCategoryId,
    unlock: t.locked && !done && !mission,
    unschedule: t.sourceType === 'MANUAL' && !!t.plannedStartAt && !done, // 루틴·미션은 400
    // 다른 날로: 완료·미션 X. 루틴은 시간이 있을 때만 (시간 미정이 될 수 없어서 같은 시각으로만 옮겨요)
    moveDate: !done && !mission && (t.sourceType !== 'ROUTINE' || !!t.plannedStartAt),
    delete: !mission,
    // 중요도: 루틴 · 미션도 그날 Task 만 바꿀 수 있어요 (완료한 Task 는 보기만)
    editPriority: !done,
  }
}

const pad = (n: number) => String(n).padStart(2, '0')
const hhmm = (m: number) => `${pad(Math.floor((m % 1440) / 60))}:${pad(m % 60)}`

/** "11월 18일 (수) · 09:00 – 10:30 (1시간 30분)" */
function rangeText(startAt: string, endAt?: string | null) {
  const base = startAt.slice(0, 10)
  const s = minutesFrom(base, startAt)
  const e = endAt ? minutesFrom(base, endAt) : null
  const range = e !== null ? `${hhmm(s)} – ${e >= 1440 ? `다음 날 ${hhmm(e)}` : hhmm(e)}` : hhmm(s)
  return `${formatMonthDay(base)} · ${range}${e !== null && e > s ? ` (${formatDuration(e - s)})` : ''}`
}

function timeLine(t: TimeBlockTask) {
  const day = t.date ?? t.scheduledDate
  if (!t.plannedStartAt) return day ? `${formatMonthDay(day)} · 시간 미정` : '날짜 미정'
  return rangeText(t.plannedStartAt, t.plannedEndAt)
}

/** 목표 상세 Task 수정 API 에 보낼 "지금 값 그대로"의 폼 (바꿀 항목만 덮어써서 보내요) */
function currentInput(t: TimeBlockTask): GoalTaskInput {
  const day = t.plannedStartAt?.slice(0, 10) ?? t.date ?? t.scheduledDate ?? todayKst()
  let startTime: string | null = null
  let endTime: string | null = null
  if (t.plannedStartAt) {
    const s = minutesFrom(day, t.plannedStartAt)
    const e = t.plannedEndAt ? minutesFrom(day, t.plannedEndAt) : s + (t.plannedDurationMinutes ?? 30)
    startTime = minutesToTime(s)
    endTime = minutesToTime(e)
  }
  const dur = t.plannedDurationMinutes
  return {
    taskName: t.taskName,
    emoji: t.emoji ?? null,
    generalCategoryId: t.generalCategoryId!,
    milestoneId: t.milestoneId ?? null,
    date: day,
    startTime,
    endTime,
    durationMinutes: !t.plannedStartAt && dur && dur >= 5 && dur <= 1440 ? dur : null,
    dueDate: t.dueDate ?? null,
  }
}

/** 상세 항목 고치기를 닫은 방법 (task_detail.field_cancel) */
/** same_select: 중요도에서 지금 값과 같은 칩을 다시 눌러 닫음 */
type FieldCloseMethod = 'esc' | 'cancel_button' | 'blur' | 'enter' | 'other_open' | 'same_select'

/** Enter = 저장, Esc = 취소 (창은 닫지 않음). 한글 조합 중 Enter 는 무시 */
const editKeys = (save: () => void, cancel: (method: 'esc') => void) => (e: KeyboardEvent<HTMLElement>) => {
  if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
    e.preventDefault()
    save()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    cancel('esc')
  }
}

const field =
  'border-border-subtle bg-surface focus:border-border-focus h-9 w-full min-w-0 rounded-lg border px-2.5 text-[14px] outline-none'

/** 한 줄 속성 — 누르면 그 자리에서 수정 (노션처럼) */
function Prop({
  icon,
  editable,
  onEdit,
  label,
  children,
}: {
  icon: ReactNode
  editable?: boolean
  onEdit?: () => void
  label: string
  children: ReactNode
}) {
  const body = <span className="min-w-0 flex-1">{children}</span>
  return (
    <div className="flex items-start gap-2">
      <span className="mt-[3px] shrink-0">{icon}</span>
      {editable ? (
        <button
          type="button"
          onClick={onEdit}
          aria-label={`${label} 수정`}
          className="group/prop hover:bg-subtle -mx-1.5 -my-0.5 flex min-w-0 flex-1 items-start gap-1 rounded-md px-1.5 py-0.5 text-left"
        >
          {body}
          <Pencil className="text-ink-4 mt-1 size-3 shrink-0 opacity-0 group-hover/prop:opacity-100" />
        </button>
      ) : (
        body
      )}
    </div>
  )
}

type EditKey = 'title' | 'time' | 'sub' | 'milestone' | 'due' | 'priority' | null

/** 시간 바로 고치기: 날짜 · 시작 · 종료 (+ 루틴이면 적용 범위) */
function TimeEditor({
  task: t,
  saving,
  onSave,
  onCancel,
}: {
  task: TimeBlockTask
  saving: boolean
  onSave: (date: string, start: number, end: number, scope: ChangeScope) => void
  onCancel: (method: FieldCloseMethod) => void
}) {
  const today = todayKst()
  const day = t.plannedStartAt?.slice(0, 10) ?? t.date ?? t.scheduledDate ?? today
  const s0 = t.plannedStartAt ? minutesFrom(day, t.plannedStartAt) : 9 * 60
  const e0 = t.plannedEndAt ? minutesFrom(day, t.plannedEndAt) : s0 + (t.plannedDurationMinutes ?? 60)
  const [date, setDate] = useState(day)
  const [start, setStart] = useState(minutesToTime(s0))
  const [end, setEnd] = useState(minutesToTime(e0))
  const [scope, setScope] = useState<ChangeScope>('DAY')
  const routine = t.sourceType === 'ROUTINE'

  const s = start ? timeToMinutes(start) : NaN
  let e = end ? timeToMinutes(end) : NaN
  if (e <= s) e += 1440
  const valid = !!date && !Number.isNaN(s) && !Number.isNaN(e) && e - s > 0 && e - s < 1440
  const options = routine
    ? scopeOptions({ kind: 'routine', isToday: date === today, sameDay: date === day, crossesMidnight: e >= 1440 })
    : null
  const effective = options?.find((o) => o.scope === scope && !o.disabledReason) ? scope : 'DAY'
  const save = () => valid && !saving && onSave(date, s, e, effective)

  return (
    <div
      className="border-border-subtle bg-brand-soft/40 -mx-1.5 space-y-2 rounded-xl border p-2.5"
      onKeyDown={editKeys(save, onCancel)}
    >
      <input type="date" value={date} onChange={(ev) => setDate(ev.target.value)} className={field} aria-label="날짜" />
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-1.5">
        <input
          type="time"
          step={600}
          value={start}
          onChange={(ev) => setStart(ev.target.value)}
          className={field}
          aria-label="시작"
          autoFocus
        />
        <span className="text-ink-3 text-xs">–</span>
        <input
          type="time"
          step={600}
          value={end}
          onChange={(ev) => setEnd(ev.target.value)}
          className={field}
          aria-label="종료"
        />
      </div>
      {options && <ScopeChips options={options} value={effective} onChange={setScope} />}
      <div className="flex items-center gap-1.5">
        <span className="text-ink-3 flex-1 text-[11px]">
          {valid
            ? `${formatDuration(e - s)}${e >= 1440 ? ' · 다음 날까지' : ''} · Enter 저장 · Esc 취소`
            : '시간을 확인해 주세요'}
        </span>
        <Button size="sm" variant="ghost" onClick={() => onCancel('cancel_button')} disabled={saving}>
          취소
        </Button>
        <Button size="sm" onClick={save} disabled={!valid} loading={saving}>
          저장
        </Button>
      </div>
    </div>
  )
}

export function TaskDetail({
  task: t,
  busy,
  actions,
  confirmingDelete,
  deleting,
  onConfirmDelete,
  onCancelDelete,
  surface,
}: {
  task: TimeBlockTask
  busy?: boolean
  actions: TaskActions
  /** Delete 키·삭제 메뉴로 "삭제할까요?"를 띄운 상태 */
  confirmingDelete?: boolean
  deleting?: boolean
  onConfirmDelete?: () => void
  onCancelDelete?: () => void
  /** 이 상세 창이 쓰인 화면 (task_detail.* 의 surface) */
  surface?: TimetableSurface
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [movePanel, setMovePanel] = useState(false)
  const [editing, setEditing] = useState<EditKey>(null)
  const [draft, setDraft] = useState({ emoji: '', name: '' })
  const perm = taskPermissions(t)
  const done = t.taskStatus === 'COMPLETED'
  const chain = actions.onSelectChain ? (actions.chainSize?.(t) ?? 0) : 0
  const color = goalAccent(t.goalColorCode)
  const actual = t.taskStatus === 'COMPLETED' && t.actualStartAt && t.actualEndAt ? t : null

  const goalId = t.goalCategoryId ?? 0
  const updateTask = useUpdateGoalTask(goalId)
  const reschedule = useRescheduleTask()
  const routineScoped = useRoutineScopedReschedule()
  const changePriority = useChangePriority()
  // 업무형 목표: 세부 목표 = 로드맵 세부 목표(WorkTopic) — 목표 목록(캐시)에서 판별
  const goalList = useGoalCategories({ includeHabit: true })
  const work = isWorkGoal(goalList.data?.find((g) => g.goalCategoryId === t.goalCategoryId))
  const workBoard = useWorkBoard(goalId, 30, work && perm.editContent && editing === 'sub')
  const workTopics = (workBoard.data?.topics ?? []).filter(
    (x): x is typeof x & { topicId: number } => x.topicId !== null && (!x.completed || x.topicId === t.workTopicId)
  )
  const updateWork = useUpdateWorkTask(goalId)
  // 세부 목표·마일스톤을 고를 때만 목표 정보를 불러와요
  const goal = useGoalCategory(goalId, perm.editContent && (editing === 'sub' || editing === 'milestone'))
  const subs = (goal.data?.generalCategories ?? []).filter((s) => s.generalCategoryStatus !== 'DELETED')
  const milestones = [...(goal.data?.milestones ?? [])].sort((a, b) => a.dueDate.localeCompare(b.dueDate))

  // ── 분석 이벤트 (task_detail.*) ──
  const taskId = t.taskId
  const base = { surface, task_id: taskId }
  const qc = useQueryClient()
  const rootRef = useRef<HTMLDivElement>(null)
  /** 저장 요청을 보낸 뒤 목록 칸이 비활성화되며 생기는 blur 를 '취소'로 세지 않도록 */
  const savingFieldRef = useRef(false)
  /** 삭제 확인을 Enter 로 눌렀는지 */
  const deleteEnterRef = useRef(false)
  /** 삭제 확인을 … 메뉴로 띄웠는지 (아니면 Delete 키) */
  const menuDeleteRef = useRef(false)
  const deleteViewTrackedRef = useRef(false)
  const reasonTrackedRef = useRef(false)
  /** 창을 닫게 될 마지막 입력 (X · 바깥 · Esc · 같은 블록) */
  const closeMethodRef = useRef<'x_button' | 'backdrop' | 'esc' | 'toggle' | null>(null)

  /**
   * 완료·고정 풀기·시간 미정·삭제 요청은 훅(useTimetableInteractions)이 보내요.
   * 방금 시작된 요청을 MutationCache 에서 찾아 결과를 기다려요 (성공하면 이 창은 곧 닫혀요).
   */
  const runAction = (
    action: 'complete' | 'uncomplete' | 'unlock' | 'unschedule' | 'move_date' | 'delete',
    fn: () => void,
    /** 결과 이벤트에 더할 속성 (move_date: move_option · to_date, 성공하면 kept_time) */
    extra?: EventProps,
    onData?: (data: unknown) => EventProps
  ) => {
    const cache = qc.getMutationCache()
    const before = new Set(cache.getAll())
    fn()
    const m = cache.getAll().find((x) => !before.has(x))
    if (!m) return
    const unsub = cache.subscribe((ev) => {
      if (ev.type !== 'updated' || ev.mutation !== m) return
      if (ev.action.type === 'success') {
        unsub()
        track('task_detail.action_result', {
          surface,
          task_action: action,
          result: 'success',
          ...extra,
          ...onData?.(ev.action.data),
        })
      } else if (ev.action.type === 'error') {
        unsub()
        track('task_detail.action_result', {
          surface,
          task_action: action,
          result: 'fail',
          error_status: errorStatusOf(ev.action.error),
          ...extra,
        })
      }
    })
  }

  // 배치 이유가 보일 때 한 번
  const showReason = !!t.placementReason && !t.locked
  const reasonCodes = (t.placementReasonCodes ?? []).join(',')
  useEffect(() => {
    if (!showReason || reasonTrackedRef.current) return
    reasonTrackedRef.current = true
    track('task_detail.reason_view', { surface, task_id: taskId, reason_codes: reasonCodes })
  }, [showReason, surface, taskId, reasonCodes])

  // '삭제할까요?'가 뜰 때 한 번 (… 메뉴 또는 Delete 키 — Delete 키는 훅이 띄워요)
  useEffect(() => {
    if (!confirmingDelete) {
      deleteViewTrackedRef.current = false
      return
    }
    if (deleteViewTrackedRef.current) return
    deleteViewTrackedRef.current = true
    track('task_detail.delete_click', {
      surface,
      task_id: taskId,
      source_type: t.sourceType,
      entry_point: menuDeleteRef.current ? 'menu' : 'delete_key',
    })
    menuDeleteRef.current = false
  }, [confirmingDelete, surface, taskId, t.sourceType])

  // 창 닫기: 닫는 건 Popover(훅)가 하지만, 무엇으로 닫혔는지는 여기서 입력을 지켜보다가 사라질 때 보내요.
  // 작업 성공·다른 창으로 넘어가며 닫힌 경우엔 보내지 않아요.
  useEffect(() => {
    const dialog = rootRef.current?.closest('[role="dialog"]')
    if (!dialog) return
    const onDown = (e: PointerEvent) => {
      if (e.target instanceof Node && dialog.contains(e.target)) return
      const block = e.target instanceof Element ? e.target.closest<HTMLElement>('[data-task-id]') : null
      closeMethodRef.current = Number(block?.dataset.taskId) === taskId ? 'toggle' : 'backdrop'
    }
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape' && !e.defaultPrevented) closeMethodRef.current = 'esc'
    }
    const onClick = (e: Event) => {
      const btn = e.target instanceof Element ? e.target.closest('button') : null
      if (btn && btn.parentElement === dialog && btn.getAttribute('aria-label') === '닫기')
        closeMethodRef.current = 'x_button'
    }
    const methodRef = closeMethodRef
    document.addEventListener('pointerdown', onDown, true)
    document.addEventListener('keydown', onKey)
    dialog.addEventListener('click', onClick, true)
    return () => {
      document.removeEventListener('pointerdown', onDown, true)
      document.removeEventListener('keydown', onKey)
      dialog.removeEventListener('click', onClick, true)
      const method = methodRef.current
      methodRef.current = null
      if (method) track('task_detail.close', { surface, task_id: taskId, close_method: method })
    }
  }, [surface, taskId])

  const stop = () => setEditing(null)
  const openField = (k: Exclude<EditKey, null>) => {
    if (editing && editing !== k)
      track('task_detail.field_cancel', { surface, edit_field: editing, close_method: 'other_open' })
    track('task_detail.field_edit_open', { ...base, edit_field: k, source_type: t.sourceType })
    setEditing(k)
  }
  const cancelField = (method: FieldCloseMethod) => {
    if (editing && !savingFieldRef.current)
      track('task_detail.field_cancel', { surface, edit_field: editing, close_method: method })
    stop()
  }
  const fieldResult = (f: Exclude<EditKey, null>) => ({
    onSuccess: () => {
      savingFieldRef.current = false
      track('task_detail.field_result', { surface, edit_field: f, result: 'success' })
      stop()
    },
    onError: (e: unknown) => {
      savingFieldRef.current = false
      track('task_detail.field_result', { surface, edit_field: f, result: 'fail', error_status: errorStatusOf(e) })
    },
  })
  const saveContent = (f: 'title' | 'sub' | 'milestone' | 'due', patch: Partial<GoalTaskInput>) => {
    savingFieldRef.current = true
    track('task_detail.field_save', { ...base, edit_field: f, source_type: t.sourceType })
    updateTask.mutate({ taskId: t.taskId, ...currentInput(t), ...patch }, fieldResult(f))
  }
  const savePriority = (p: TaskPriority) => {
    const prev = priorityOf(t.taskPriority)
    if (p === prev) return cancelField('same_select')
    savingFieldRef.current = true
    track('task_detail.field_save', {
      ...base,
      edit_field: 'priority',
      source_type: t.sourceType,
      priority: p,
      prev_priority: prev,
    })
    changePriority.mutate({ taskId: t.taskId, taskPriority: p }, fieldResult('priority'))
  }

  /** 업무형 목표의 세부 목표 바꾸기 (업무 Task 수정 API · null = 세부 목표 없음) */
  const saveWorkTopic = (topicId: number | null) => {
    if (topicId === (t.workTopicId ?? null)) return cancelField('same_select')
    savingFieldRef.current = true
    track('task_detail.field_save', { ...base, edit_field: 'sub', source_type: t.sourceType })
    updateWork.mutate(
      { taskId: t.taskId, ...(topicId ? { topicId } : { clearTopic: true }) },
      fieldResult('sub')
    )
  }

  const startTitle = () => {
    setDraft({ emoji: t.emoji ?? '', name: t.taskName })
    openField('title')
  }
  const saveTitle = () => {
    if (updateTask.isPending) return // Enter 로 저장한 직후 blur 가 한 번 더 부르는 것 방지
    const name = draft.name.trim()
    if (!name) {
      track('task_detail.title_empty_cancel', base)
      return stop()
    }
    if (name === t.taskName && (draft.emoji.trim() || null) === (t.emoji ?? null)) return stop()
    saveContent('title', { taskName: name, emoji: draft.emoji.trim() || null })
  }

  const saveTime = (date: string, s: number, e: number, scope: ChangeScope) => {
    const body = { taskId: t.taskId, plannedStartAt: toDateTime(date, s), plannedEndAt: toDateTime(date, e) }
    const routine = t.sourceType === 'ROUTINE'
    savingFieldRef.current = true
    track('task_detail.field_save', {
      ...base,
      edit_field: 'time',
      ...(routine ? { change_scope: scope } : {}),
      source_type: t.sourceType,
    })
    if (routine) routineScoped.mutate({ ...body, scope }, fieldResult('time'))
    else reschedule.mutate(body, fieldResult('time'))
  }

  const empty = (label: string) => <span className="text-ink-4">{label}</span>

  return (
    <div ref={rootRef} className="p-5">
      {/* 상태 표시 */}
      <div className="flex flex-wrap items-center gap-1.5 pr-8">
        {done && <Badge tone="success">완료</Badge>}
        {t.missed && <Badge tone="danger">놓침</Badge>}
        {t.sourceType === 'ROUTINE' && (
          <Badge>
            <Repeat className="size-3" />
            루틴
          </Badge>
        )}
        {t.sourceType === 'MISSION' && <Badge>미션</Badge>}
        {t.taskKind === 'APPOINTMENT' && <Badge tone="warning">약속</Badge>}
        {t.locked ? (
          <Badge>직접 정한 시간</Badge>
        ) : (
          t.placedBy === 'ENGINE' && (
            <Badge tone="brand">
              <AIBadge size="sm" className="-ml-2 rounded-full" />
              자동 배치
            </Badge>
          )
        )}
      </div>

      {/* 제목 — 누르면 바로 수정 */}
      {editing === 'title' ? (
        <div className="mt-2 flex gap-1.5" onKeyDown={(e) => editKeys(saveTitle, cancelField)(e)}>
          <input
            value={draft.emoji}
            onChange={(e) => setDraft((d) => ({ ...d, emoji: e.target.value }))}
            maxLength={16}
            placeholder="🙂"
            aria-label="이모지"
            className={cn(field, 'h-10 w-12 shrink-0 text-center placeholder:opacity-40')}
          />
          <input
            value={draft.name}
            onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))}
            onBlur={(e) => {
              // 이모지 칸으로 옮겨 가는 건 저장하지 않아요
              if (!e.currentTarget.parentElement?.contains(e.relatedTarget as Node)) saveTitle()
            }}
            maxLength={100}
            autoFocus
            onFocus={(e) => e.currentTarget.select()}
            aria-label="Task 이름"
            className={cn(field, 'h-10 text-[16px] font-bold')}
          />
        </div>
      ) : perm.editContent ? (
        <button
          type="button"
          onClick={startTitle}
          className="group/title hover:bg-subtle -mx-1.5 mt-2 flex w-[calc(100%+12px)] items-start gap-1.5 rounded-lg px-1.5 py-0.5 text-left"
        >
          <h3 className={cn('min-w-0 flex-1 text-lg leading-snug font-bold', done && 'text-ink-3 line-through')}>
            {t.emoji ? `${t.emoji} ` : ''}
            {t.taskName}
          </h3>
          <Pencil className="text-ink-4 mt-1.5 size-3.5 shrink-0 opacity-0 group-hover/title:opacity-100" />
        </button>
      ) : (
        <h3 className={cn('mt-2.5 text-lg leading-snug font-bold', done && 'text-ink-3 line-through')}>
          {t.emoji ? `${t.emoji} ` : ''}
          {t.taskName}
        </h3>
      )}

      <div className="text-ink-2 mt-3 space-y-2 text-sm">
        {/* 시간 */}
        {actual && (
          <Prop icon={<Check className="text-success size-4" />} label="실제 시간">
            <span className="text-ink-3 mr-1">실제</span>
            {actual.actualSegments && actual.actualSegments.length > 1
              ? actual.actualSegments.map((g) => rangeText(g.start, g.end)).join(' · ')
              : rangeText(actual.actualStartAt!, actual.actualEndAt)}
          </Prop>
        )}
        {editing === 'time' ? (
          <TimeEditor
            task={t}
            saving={reschedule.isPending || routineScoped.isPending}
            onSave={saveTime}
            onCancel={cancelField}
          />
        ) : (
          <Prop
            icon={<Clock className="text-ink-3 size-4" />}
            label="시간"
            editable={perm.editTime}
            onEdit={() => openField('time')}
          >
            {actual && <span className="text-ink-3 mr-1">계획</span>}
            <span className={actual ? 'text-ink-3' : undefined}>{timeLine(t)}</span>
          </Prop>
        )}

        {/* 중요도 — A 는 TimeBlock 에서 배지 + 테두리로 강조돼요 */}
        {editing === 'priority' ? (
          <div
            className="border-border-subtle bg-brand-soft/40 -mx-1.5 space-y-1.5 rounded-xl border p-2.5"
            onKeyDown={(e) => {
              // Enter · Space 는 칩(버튼)이 직접 처리해요 — Esc 만 닫기
              if (e.key !== 'Escape') return
              e.preventDefault()
              e.stopPropagation()
              cancelField('esc')
            }}
          >
            <PriorityChips
              value={priorityOf(t.taskPriority)}
              onChange={savePriority}
              disabled={changePriority.isPending}
              autoFocus
            />
            <div className="flex items-center gap-1.5">
              <span className="text-ink-3 flex-1 text-[11px]">
                {t.sourceType === 'MANUAL' ? 'A는 캘린더에서 눈에 띄게 보여요' : '이 날 Task만 바뀌어요 · A는 눈에 띄게 보여요'}
              </span>
              <Button size="sm" variant="ghost" onClick={() => cancelField('cancel_button')}>
                닫기
              </Button>
            </div>
          </div>
        ) : (
          <Prop
            icon={
              <span
                className={cn(
                  'mt-px grid size-4 place-items-center rounded-[4px] text-[10px] leading-none font-bold',
                  priorityOf(t.taskPriority) === 'A' ? 'text-white' : 'bg-subtle text-ink-3'
                )}
                style={priorityOf(t.taskPriority) === 'A' ? { backgroundColor: color } : undefined}
                aria-hidden
              >
                {priorityOf(t.taskPriority)}
              </span>
            }
            label="중요도"
            editable={perm.editPriority}
            onEdit={() => openField('priority')}
          >
            중요도 {priorityOf(t.taskPriority)}
            <span className="text-ink-3"> · {PRIORITY_LABEL[priorityOf(t.taskPriority)]}</span>
          </Prop>
        )}

        {/* 목표 › 세부 목표 */}
        {t.goalCategoryName &&
          (work && editing === 'sub' ? (
            <select
              autoFocus
              value={t.workTopicId ?? ''}
              disabled={workBoard.isPending || updateWork.isPending}
              onChange={(e) => saveWorkTopic(e.target.value ? Number(e.target.value) : null)}
              onBlur={() => cancelField('blur')}
              onKeyDown={(e) => editKeys(() => cancelField('enter'), cancelField)(e)}
              className={field}
              aria-label="세부 목표"
            >
              <option value="">{workBoard.isPending ? '불러오는 중…' : '세부 목표 없음'}</option>
              {workTopics.map((x) => (
                <option key={x.topicId} value={x.topicId}>
                  {x.emoji ? `${x.emoji} ` : ''}
                  {x.name}
                </option>
              ))}
            </select>
          ) : editing === 'sub' ? (
            <select
              autoFocus
              value={t.generalCategoryId ?? ''}
              disabled={goal.isPending || updateTask.isPending}
              onChange={(e) => saveContent('sub', { generalCategoryId: Number(e.target.value) })}
              onBlur={() => cancelField('blur')}
              onKeyDown={(e) => editKeys(() => cancelField('enter'), cancelField)(e)}
              className={field}
              aria-label="세부 목표"
            >
              {goal.isPending && <option>불러오는 중…</option>}
              {subs.map((s) => (
                <option key={s.generalCategoryId} value={s.generalCategoryId}>
                  {s.generalCategoryName}
                </option>
              ))}
            </select>
          ) : (
            <Prop
              icon={<span className="mt-0.5 block size-3 rounded-full" style={{ backgroundColor: color }} />}
              label="세부 목표"
              editable={perm.editContent}
              onEdit={() => openField('sub')}
            >
              {t.goalEmoji ? `${t.goalEmoji} ` : ''}
              {t.goalCategoryName}
              {t.goalTemporary && <span className="text-ink-3"> · 임시 목표</span>}
              {work ? (
                <span className="text-ink-3"> › {t.workTopicName ?? '세부 목표 없음'}</span>
              ) : (
                t.generalCategoryName && <span className="text-ink-3"> › {t.generalCategoryName}</span>
              )}
            </Prop>
          ))}

        {/* 마일스톤 */}
        {(t.milestoneTitle || perm.editContent) &&
          (editing === 'milestone' ? (
            <select
              autoFocus
              value={t.milestoneId ?? ''}
              disabled={goal.isPending || updateTask.isPending}
              onChange={(e) =>
                saveContent('milestone', { milestoneId: e.target.value ? Number(e.target.value) : null })
              }
              onBlur={() => cancelField('blur')}
              onKeyDown={(e) => editKeys(() => cancelField('enter'), cancelField)(e)}
              className={field}
              aria-label="마일스톤"
            >
              <option value="">없음</option>
              {milestones.map((m) => (
                <option key={m.milestoneId} value={m.milestoneId}>
                  {m.title}
                </option>
              ))}
            </select>
          ) : (
            <Prop
              icon={<Flag className="text-ink-3 size-4" />}
              label="마일스톤"
              editable={perm.editContent}
              onEdit={() => openField('milestone')}
            >
              {t.milestoneTitle ?? empty('마일스톤 없음')}
            </Prop>
          ))}

        {/* 마감일 */}
        {(t.dueDate || perm.editContent) &&
          (editing === 'due' ? (
            <div
              className="flex items-center gap-1.5"
              onKeyDown={(e) => editKeys(() => cancelField('enter'), cancelField)(e)}
            >
              <input
                type="date"
                autoFocus
                defaultValue={t.dueDate ?? ''}
                disabled={updateTask.isPending}
                onChange={(e) => e.target.value && saveContent('due', { dueDate: e.target.value })}
                className={field}
                aria-label="마감일"
              />
              {t.dueDate && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => saveContent('due', { dueDate: null })}
                  disabled={updateTask.isPending}
                >
                  <X className="size-3.5" />
                  지우기
                </Button>
              )}
              <Button size="sm" variant="ghost" onClick={() => cancelField('cancel_button')}>
                닫기
              </Button>
            </div>
          ) : (
            <Prop
              icon={<CalendarClock className="text-ink-3 size-4" />}
              label="마감일"
              editable={perm.editContent}
              onEdit={() => openField('due')}
            >
              {t.dueDate ? `${formatMonthDay(t.dueDate)}까지` : empty('마감일 없음')}
            </Prop>
          ))}

        {t.carryOverCount > 0 && (
          <Prop icon={<Undo2 className="text-ink-3 size-4" />} label="미뤄진 횟수">
            {t.carryOverCount}번 미뤄진 Task예요
          </Prop>
        )}
        {t.sourceType === 'ROUTINE' && (
          <Prop icon={<Layers className="text-ink-3 size-4" />} label="루틴 안내">
            <span className="text-ink-3 text-[13px]">이름은 목표 상세의 루틴에서 바꿀 수 있어요.</span>
          </Prop>
        )}
      </div>

      {/* 배치 이유 (명세: 항상 설명) */}
      {showReason && t.placementReason && (
        <div className="ai-border-flat mt-4 rounded-xl px-3.5 py-3">
          <p className="text-text-brand flex items-center gap-1.5 text-xs font-bold">
            <AIBadge size="sm" />이 시간에 놓은 이유
          </p>
          <PlacementReason text={t.placementReason} codes={t.placementReasonCodes ?? []} />
        </div>
      )}
      {t.locked && !done && (
        <p className="bg-subtle text-ink-3 mt-4 rounded-xl px-3.5 py-2.5 text-xs leading-relaxed">
          직접 정한 시간이라 자동 배치가 옮기지 않아요.
        </p>
      )}

      {/* 삭제 확인 (Delete 키 · 삭제 메뉴) */}
      {confirmingDelete ? (
        <div
          className="mt-5"
          onKeyDown={(e) => {
            if (e.key === 'Enter') deleteEnterRef.current = true
          }}
        >
          <InlineConfirm
            message={`'${t.taskName}'을 삭제할까요?`}
            detail={
              t.sourceType === 'ROUTINE'
                ? '이 날짜의 루틴 Task 하나만 지워져요.'
                : '되돌릴 수 없어요. Enter 삭제 · Esc 취소'
            }
            loading={deleting}
            onConfirm={() => {
              track('task_detail.delete_confirm', {
                ...base,
                source_type: t.sourceType,
                submit_method: deleteEnterRef.current ? 'enter' : 'button',
              })
              deleteEnterRef.current = false
              runAction('delete', () => onConfirmDelete?.())
            }}
            onCancel={(method) => {
              deleteEnterRef.current = false
              track('task_detail.delete_cancel', { ...base, close_method: method })
              onCancelDelete?.()
            }}
          />
        </div>
      ) : (
        <div className="mt-5 flex items-center gap-2">
          <Button
            variant={done ? 'secondary' : 'brand'}
            className="flex-1"
            loading={busy}
            onClick={() => {
              track('task_detail.complete_click', {
                ...base,
                source_type: t.sourceType,
                check_action: done ? 'uncomplete' : 'complete',
                is_missed: !!t.missed,
                is_scheduled: !!t.plannedStartAt,
              })
              runAction(done ? 'uncomplete' : 'complete', () => actions.onToggleComplete(t))
            }}
          >
            {!done && <Check className="size-4" />}
            {done ? '완료 취소' : '완료'}
          </Button>
          {/* 계획과 다른 시간에 · 나눠서 했을 때 — 완료만큼 자주 써서 같은 크기 버튼으로 (2026-10) */}
          {t.plannedStartAt && t.sourceType !== 'MISSION' && actions.onCompleteWithTime && (
            <Button
              variant="secondary"
              className="flex-1"
              title={done ? '실제로 한 시간을 고쳐요 (나눠서 한 것도)' : '계획과 다른 시간에 했거나 나눠서 했을 때 실제 시간을 넣고 완료해요'}
              onClick={() => {
                track('task_detail.actual_time_click', { ...base, task_status: t.taskStatus })
                actions.onCompleteWithTime?.(t)
              }}
            >
              <Clock className="size-4" />
              {done ? '실제 시간 고치기' : '다른 시간에 했어요'}
            </Button>
          )}
          {(perm.unlock || perm.unschedule || perm.moveDate || perm.delete || chain > 1) && (
            <div className="relative">
              <Button
                variant="secondary"
                className="w-10 px-0"
                aria-label="다른 작업"
                aria-expanded={menuOpen}
                onClick={() => {
                  if (!menuOpen) track('task_detail.menu_open', { ...base, source_type: t.sourceType })
                  setMenuOpen((v) => !v)
                  setMovePanel(false)
                }}
              >
                <Ellipsis className="size-4" />
              </Button>
              {menuOpen && (
                <div
                  role="menu"
                  className="bg-surface shadow-pop border-line absolute right-0 bottom-12 z-10 w-64 overflow-hidden rounded-xl border py-1"
                >
                  {chain > 1 && (
                    <MenuItem
                      onClick={() => {
                        setMenuOpen(false)
                        track('task_detail.chain_select_click', { ...base, group_size: chain })
                        actions.onSelectChain?.(t)
                      }}
                    >
                      <Link2 className="size-4" />
                      이어진 {chain}개 묶어 옮기기
                    </MenuItem>
                  )}
                  {perm.unlock && (
                    <MenuItem
                      onClick={() => {
                        track('task_detail.unlock_click', base)
                        runAction('unlock', () => actions.onUnlock(t))
                      }}
                    >
                      <PinOff className="size-4 shrink-0 self-start mt-0.5" />
                      <span className="min-w-0">
                        AI 자동 배치에 맡기기
                        <span className="text-ink-3 block text-[11px] leading-snug">
                          직접 정한 시간을 풀어서, 다음 자동 배치 때 빈 시간으로 옮길 수 있어요
                        </span>
                      </span>
                    </MenuItem>
                  )}
                  {perm.unschedule && (
                    <MenuItem
                      onClick={() => {
                        track('task_detail.unschedule_click', base)
                        runAction('unschedule', () => actions.onUnschedule(t))
                      }}
                    >
                      <Clock className="size-4" />
                      시간 미정으로
                    </MenuItem>
                  )}
                  {perm.moveDate && actions.onMoveDate && (
                    <MenuItem
                      onClick={() => {
                        if (!movePanel) track('task_detail.move_date_click', { ...base, source_type: t.sourceType })
                        setMovePanel((v) => !v)
                      }}
                    >
                      <CalendarClock className="size-4" />
                      다른 날로
                      <span className="text-ink-4 ml-auto text-[11px]">{movePanel ? '▴' : '▾'}</span>
                    </MenuItem>
                  )}
                  {perm.moveDate && actions.onMoveDate && movePanel && (
                    <MoveDatePanel
                      t={t}
                      onPick={(date, label, option) => {
                        track('task_detail.move_date_select', {
                          ...base,
                          source_type: t.sourceType,
                          move_option: option,
                          day_offset: Math.round((Date.parse(date) - Date.parse(todayKst())) / 86_400_000),
                          is_scheduled: !!t.plannedStartAt,
                        })
                        setMenuOpen(false)
                        setMovePanel(false)
                        runAction(
                          'move_date',
                          () => actions.onMoveDate?.(t, date, label),
                          { move_option: option, to_date: date },
                          (data) => ({ kept_time: !!(data as TimeBlockTask | undefined)?.plannedStartAt })
                        )
                      }}
                    />
                  )}
                  {perm.delete && (
                    <MenuItem
                      danger
                      onClick={() => {
                        setMenuOpen(false)
                        menuDeleteRef.current = true
                        actions.onDelete(t)
                      }}
                    >
                      <Trash className="size-4" />
                      삭제
                      <kbd className="text-ink-4 ml-auto text-[10px]">Del</kbd>
                    </MenuItem>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      )}
      {/* 계획 시각이 이미 시작됐는데 안 했으면: 그 시간에 실제로 한 일을 남길 수 있게 */}
      {!done &&
        !confirmingDelete &&
        t.plannedStartAt &&
        t.plannedStartAt <= toDateTime(nowKst().date, nowKst().minutes) &&
        actions.onRecordInstead && (
          <button
            type="button"
            onClick={() => {
              track('task_detail.record_instead_click', { ...base, source_type: t.sourceType })
              actions.onRecordInstead?.(t)
            }}
            className="text-success hover:bg-success-soft mt-1 w-full rounded-lg py-1.5 text-center text-[13px] font-semibold"
          >
            이 시간에 다른 일을 했어요
          </button>
        )}
    </div>
  )
}

/** "다른 날로" — 내일 · 모레 · 날짜 지정 (오늘 기준). 지금 있는 날은 고를 수 없어요 */
function MoveDatePanel({
  t,
  onPick,
}: {
  t: TimeBlockTask
  onPick: (date: string, label: string, option: 'tomorrow' | 'day_after' | 'custom') => void
}) {
  const today = todayKst()
  const current = t.plannedStartAt?.slice(0, 10) ?? t.date ?? t.scheduledDate ?? null
  const [custom, setCustom] = useState('')
  const md = (d: string) => `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`
  const quick: { key: 'tomorrow' | 'day_after'; label: string; date: string }[] = [
    { key: 'tomorrow', label: '내일', date: addDays(today, 1) },
    { key: 'day_after', label: '모레', date: addDays(today, 2) },
  ]
  return (
    <div className="bg-subtle/60 border-line border-y px-2 py-2">
      <div className="flex gap-1.5">
        {quick.map((q) => (
          <button
            key={q.key}
            type="button"
            disabled={q.date === current}
            onClick={() => onPick(q.date, q.label, q.key)}
            className="bg-surface border-line hover:border-line-strong flex-1 rounded-lg border px-2 py-1.5 text-[13px] font-semibold disabled:opacity-40"
          >
            {q.label}
            <span className="text-ink-3 ml-1 text-[11px] font-normal tabular-nums">{md(q.date)}</span>
          </button>
        ))}
      </div>
      <div className="mt-1.5 flex items-center gap-1.5">
        <input
          type="date"
          value={custom}
          min={today}
          onChange={(e) => setCustom(e.target.value)}
          aria-label="옮길 날짜"
          className="bg-surface border-line h-8 min-w-0 flex-1 rounded-lg border px-2 text-[13px]"
        />
        <button
          type="button"
          disabled={!custom || custom < today || custom === current}
          onClick={() => onPick(custom, md(custom), 'custom')}
          className="bg-action-primary-bg text-action-primary-fg h-8 shrink-0 rounded-lg px-2.5 text-[12px] font-semibold disabled:opacity-40"
        >
          옮기기
        </button>
      </div>
      <p className="text-ink-4 mt-1.5 px-0.5 text-[11px] leading-snug">
        {t.plannedStartAt
          ? '같은 시간으로 옮겨요. 자리가 없으면 그날 시간 미정으로 가요.'
          : '그날 시간 미정으로 옮겨요.'}
      </p>
    </div>
  )
}

function MenuItem({ danger, onClick, children }: { danger?: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={cn(
        'hover:bg-subtle flex w-full items-center gap-2 px-3.5 py-2.5 text-left text-sm',
        danger ? 'text-danger' : 'text-ink-2'
      )}
    >
      {children}
    </button>
  )
}

/** 배치 이유 코드 → 짧은 근거 꼬리표 */
const REASON_TAG: Record<string, string> = {
  OVERDUE: '⏰ 마감 지남',
  DEADLINE_TODAY: '⏰ 오늘 마감',
  DEADLINE_SOON: '⏰ 마감 임박',
  CARRIED_OVER: '↩️ 어제 못 한 일',
  MILESTONE_SOON: '🚩 마일스톤 임박',
  HIGH_EFFICIENCY: '📈 잘 지키는 시간',
  BEST_REMAINING: '📈 남은 시간 중 최선',
  PREFERRED_TIME: '⭐ 고른 선호 시간',
  LOW_LOAD_SLOT: '🗓️ 마감 여유',
  GOAL_TIME_FIT: '🎯 이 목표와 잘 맞는 시간',
  MOVE_HABIT: '↔️ 자주 옮겨 온 시간',
  DURATION_ADJUSTED: '⏱️ 실제 걸린 시간 반영',
}

/**
 * 배치 이유: 첫 문장 = 왜 이 시간에, 뒤 문장들 = 내 기록 근거 ("최근 4주 … 12개 중 10개를 해냈어요").
 * 근거가 눈에 들어오도록 문장을 나눠 보여주고, 이유 코드는 꼬리표로.
 */
function PlacementReason({ text, codes }: { text: string; codes: string[] }) {
  // 문장 끝("요." · "요)." 등) 기준으로 나눠요
  const sentences = text
    .match(/[^.]+?(?:요\)?\.|$)/g)
    ?.map((x) => x.trim())
    .filter(Boolean) ?? [text]
  const [head, ...evidence] = sentences
  const tags = codes.map((c) => REASON_TAG[c]).filter(Boolean)
  return (
    <>
      <p className="text-ink-2 mt-1 text-[13px] leading-relaxed">{head}</p>
      {evidence.length > 0 && (
        <ul className="text-ink-2 mt-1.5 space-y-0.5 text-[12px] leading-relaxed">
          {evidence.map((e) => (
            <li key={e} className="flex gap-1.5">
              <span className="text-text-brand" aria-hidden>
                •
              </span>
              <span>{e}</span>
            </li>
          ))}
        </ul>
      )}
      {tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {tags.map((tag) => (
            <span key={tag} className="bg-surface text-ink-2 rounded-full px-2 py-0.5 text-[11px] font-semibold">
              {tag}
            </span>
          ))}
        </div>
      )}
    </>
  )
}
