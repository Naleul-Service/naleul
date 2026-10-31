'use client'

import { useEffect, useMemo, useRef, useState, type MouseEvent } from 'react'
import { Link2 } from 'lucide-react'
import { X } from '@/components/icons'
import { Popover, rectOf, type AnchorRect } from '@/components/ui/Popover'
import { dateRelationOf, errorStatusOf } from '@/features/home/analytics'
import { track, type EventProps } from '@/lib/client/analytics'
import type { TimetableSurface } from '../analytics'
import {
  useCompleteWithActual,
  useDeletePatternOverride,
  useDeleteTask,
  useFillApply,
  useFillPreview,
  useLifePatterns,
  usePatternOverride,
  usePatternScopedChange,
  useRescheduleBatch,
  useRescheduleTask,
  useRoutineScopedReschedule,
  type ChangeScope,
  useToggleComplete,
  useUnlockTask,
  useUnscheduleTask,
  useMoveTaskDate,
  type FillPreviewResponse,
  type FillPreviewTarget,
} from '../api'
import { applyPending, applyProposals, type Pending, type Proposal } from '../layout'

/** 두 날짜 사이 일 수 (b - a) */
const dayDiff = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000)
import {
  SNAP_MINUTES,
  WEEKDAY_LABEL,
  addDays,
  formatMinutes,
  formatMonthDay,
  minutesFrom,
  minutesToTime,
  nowKst,
  timeToMinutes,
  toDateTime,
  todayKst,
  weekdayIndex,
} from '../time'
import type { ActualActivity, FixedBlock, HabitBlock, TimeBlockTask, TimetableDay } from '../types'
import { ActivityForm, draftOf, type ActivityDraft } from './ActivityForm'
import { CarryProposalBar } from './CarryProposalBar'
import { FillProposalBar } from './FillProposalBar'
import { FixedDetail } from './FixedDetail'
import { ScopeChooser, scopeOptions, type ScopeAutoReason, type ScopeTarget } from './ScopeChooser'
import { TaskDetail } from './TaskDetail'
import { ActualTimeModal, type ActualSegment } from './ActualTimeModal'
import { TimeEditModal, type TimeValue } from './TimeEditModal'
import { canDragTask, fixedKey, type Selection } from './TimeGrid'
import { groupMoves, type DragCancelReason, type DragMode, type DragSource, type DropTarget } from './useGridDrag'

type Opened =
  | { kind: 'task'; task: TimeBlockTask; anchor: AnchorRect }
  | { kind: 'fixed'; block: FixedBlock; anchor: AnchorRect }
  /** 실제로 한 일 기록 · 수정 */
  | { kind: 'activity'; draft: ActivityDraft; anchor: AnchorRect }
  | null

type Editing =
  | { kind: 'fixed'; block: FixedBlock; initial: TimeValue }
  /** 실제로 한 시간 입력 후 완료 */
  | { kind: 'actual'; task: TimeBlockTask; initial: TimeValue }
  | null

/** 드래그 직후 "어디까지 바꿀까요?"를 묻는 중인 변경 (그동안 새 자리에 임시로 그려 둬요) */
type ScopeAsk =
  | { kind: 'routine'; task: TimeBlockTask; date: string; start: number; end: number; pending: Pending }
  | {
      kind: 'fixed'
      block: FixedBlock
      /** 끌어놓은 칸의 날짜 (start/end 는 이 날 0시 기준 분 — 수면은 음수·1440 이상일 수 있어요) */
      date: string
      start: number
      end: number
      pending: Pending
      /** 수면: 기상(아침 블록의 끝)을 바꿨는지 취침(밤 블록의 시작)을 바꿨는지 */
      edge?: 'wake' | 'bed'
    }

/** 묶음에 넣을 수 있는 Task: 시간이 정해져 있고 드래그로 옮길 수 있는 것 */
const groupable = (t: TimeBlockTask) => !!t.plannedStartAt && canDragTask(t) && !t.proposed

/** 블록 날짜 (계획 시작일) */
const blockDay = (t: TimeBlockTask) => t.plannedStartAt?.slice(0, 10) ?? null

/**
 * t 와 앞뒤로 바로 이어진 Task 들 (사이 간격 10분 이하). 쪼개 둔 연속 작업을 한 번에 고르려고 써요.
 * 그날 블록을 시작 순으로 놓고 t 에서 위·아래로 이어지는 동안 모아요.
 */
function chainOf(t: TimeBlockTask, days: TimetableDay[]): TimeBlockTask[] {
  const date = blockDay(t)
  const day = days.find((d) => d.date === date)
  if (!day || !date || !groupable(t)) return [t]
  const range = (x: TimeBlockTask) => {
    const s = minutesFrom(date, x.plannedStartAt!)
    const e = x.plannedEndAt ? minutesFrom(date, x.plannedEndAt) : s + (x.plannedDurationMinutes ?? 30)
    return { s, e }
  }
  const list = day.tasks
    .filter((x) => groupable(x) && blockDay(x) === date)
    .map((x) => ({ x, ...range(x) }))
    .sort((a, b) => a.s - b.s)
  const i = list.findIndex((v) => v.x.taskId === t.taskId)
  if (i < 0) return [t]
  let lo = i
  let hi = i
  let end = list[i].e
  while (hi + 1 < list.length && list[hi + 1].s - end <= SNAP_MINUTES) {
    hi++
    end = Math.max(end, list[hi].e)
  }
  let start = list[i].s
  while (lo - 1 >= 0 && start - list[lo - 1].e <= SNAP_MINUTES) {
    lo--
    start = Math.min(start, list[lo].s)
  }
  return list.slice(lo, hi + 1).map((v) => v.x)
}

/** 루틴 Task 가 속한 날짜 (루틴 인스턴스 날짜) */
const routineDay = (t: TimeBlockTask) => t.date ?? t.plannedStartAt?.slice(0, 10) ?? null

/** 고정 블록이 자정을 넘기면(화면엔 24:00까지만) 다음 날 조각이나 패턴에서 실제 종료 시각을 찾아요 */
function fullFixedEnd(block: FixedBlock, days: TimetableDay[], patternEnd?: string) {
  const end = minutesFrom(block.targetDate, block.end)
  if (end < 1440) return end
  const nextPiece = days
    .flatMap((d) => d.fixedBlocks)
    .find(
      (b) => b.clippedFromPreviousDay && b.lifePatternId === block.lifePatternId && b.targetDate === block.targetDate
    )
  if (nextPiece) return minutesFrom(block.targetDate, nextPiece.end)
  if (patternEnd && !block.overridden) return 1440 + timeToMinutes(patternEnd)
  return end
}

// ── 분석 도우미 (timetable.* · scope_chooser.*) ──
/** drag_source: task 계획 블록 / actual 실제로 한 시간 / fixed 고정 시간 / group 묶음 / proposal AI 배치 미리보기 */
function dragSourceOf(s: DragSource) {
  if (s.kind === 'task') return s.task.proposed ? 'proposal' : s.group?.length ? 'group' : 'task'
  return s.kind
}
/** drag_mode: move / resize_start(위 끝) / resize_end(아래 끝) */
const dragModeOf = (m?: DragMode) =>
  m === undefined ? undefined : m === 'resize-top' ? 'resize_start' : m === 'resize' ? 'resize_end' : 'move'
/** 놓을 수 없던 이유 문구 → cancel_reason (TimeGrid 의 validate 문구와 맞춰요) */
function invalidReasonOf(message?: string) {
  if (!message) return 'invalid'
  if (message.includes('겹쳐 있어요')) return 'crowded'
  if (message.includes('기상') || message.includes('취침')) return 'sleep_rule'
  if (message.includes('나눠서')) return 'actual_overlap'
  return 'invalid'
}
/** validation_error: 앱이 정한 문구만 — 묶음 겹침 문구 앞의 Task 이름("…" 자리에)은 빼요 */
const validationErrorOf = (message?: string) => {
  if (!message) return undefined
  const i = message.indexOf('이미 Task')
  return i > 0 ? message.slice(i) : message
}
/** 범위를 묻는 대상 */
const scopeTargetOf = (a: ScopeAsk): ScopeTarget =>
  a.kind === 'routine' ? 'routine' : a.edge === 'wake' ? 'sleep_wake' : a.edge === 'bed' ? 'sleep_bed' : 'fixed'
/** 오늘부터 며칠 전 (YYYY-MM-DD) */
const daysAgoOf = (date: string, today: string) => Math.round((Date.parse(today) - Date.parse(date)) / 86_400_000)

/** 배치 미리보기를 연 쪽에 알려 주는 콜백 */
export interface PreviewFillOptions {
  /** fill = "AI로 배치하기" · new = 방금 추가한 Task (확인 바 문구가 달라요) */
  kind?: 'fill' | 'new'
  onPreview?: (r: FillPreviewResponse) => void
  /** 이대로 진행(applied=true) 또는 취소 — final 은 끌어서 고친 최종 자리 */
  onResolve?: (applied: boolean, final: Proposal[]) => void
  /** 미리보기를 받지 못했을 때 (네트워크 오류 등) — 확인 단계를 닫을 수 있게 */
  onError?: () => void
}

/**
 * TimeTable 위에서 하는 모든 조작(상세 팝오버 · 완료 · 시간 변경 · 삭제 · 드래그)을 한곳에 모은 훅.
 * 캘린더(주간·일간·월간)와 Task 추가 3단계 · 내일 확인 화면이 같은 동작을 쓰도록 분리했어요.
 *
 * @param rawDays 서버에서 받은 날짜들 (드래그 직후 임시 위치를 덧입혀서 days 로 돌려줘요)
 * @param fallbackDate 시간 미정 Task 의 시간을 바꿀 때 기본 날짜
 */
export function useTimetableInteractions(
  rawDays: TimetableDay[],
  // 시간 미정 Task 의 기본 날짜는 이제 상세 창이 직접 정해요 (호출부 호환용으로 남김)
  fallbackDate: string,
  /**
   * sidePanel: 오른쪽 Task 추가 패널이 열려 있음 → 확인 바를 그만큼 비켜서 띄워요
   * surface: 이 TimeTable 이 쓰인 화면 (분석). 없으면 'calendar' 로 봐요.
   *          'home' 이면 격자 이벤트(timetable.*)는 보내지 않아요 — 홈은 home.calendar_* 로 따로 보내요.
   */
  opts: {
    sidePanel?: boolean
    surface?: TimetableSurface
    /**
     * 빈 곳 클릭 → (실제 기록 바로 열기 대신) + 메뉴를 그 자리에 띄워요 (명세 11.4). 캘린더에서만 넘겨요.
     * 앞날 · 지금 이후 칸도 메뉴는 떠요 (계획 할 일 추가는 앞날이 맞으니까)
     */
    onEmptyMenu?: (slot: { date: string; minutes: number }, anchor: AnchorRect) => void
  } = {}
) {
  const surface: TimetableSurface = opts.surface ?? 'calendar'
  // 캘린더 보기: 격자는 일간(1일) · 주간(7일)에서만 그려요. 월간에서 Task 를 눌러도 격자 이벤트는 아니에요
  const viewMode = surface === 'calendar' ? (rawDays.length > 1 ? 'week' : 'day') : undefined
  /** timetable.* (격자 클릭·끌기·묶음) — 홈에서는 보내지 않아요 */
  const trackGrid = (event: Parameters<typeof track>[0], props: EventProps = {}) => {
    if (surface !== 'home') track(event, { surface, ...props })
  }
  const [opened, setOpened] = useState<Opened>(null)
  const [editing, setEditing] = useState<Editing>(null)
  // "삭제할까요?"를 띄운 Task (상세 창 안에서 확인해요)
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  // 드래그로 옮긴 뒤 서버 응답을 기다리는 동안 새 위치에 그려 둘 블록들
  const [pending, setPending] = useState<Pending[]>([])
  const [ask, setAsk] = useState<ScopeAsk | null>(null)
  // 묶음 드래그로 같이 옮길 Task id (Shift·⌘·Ctrl+클릭, 또는 상세의 "이어진 N개 묶어 옮기기")
  const [group, setGroup] = useState<number[]>([])
  // 마지막으로 누른 입력 종류 (명세 13.1) — click 이벤트에 pointerType 이 없는 브라우저(구형 iOS Safari)도
  // 묶음 고르는 중 터치 탭이 상세 창을 열지 않고 묶음만 토글하게
  const lastPointer = useRef<string>('mouse')
  useEffect(() => {
    const onDown = (e: PointerEvent) => {
      lastPointer.current = e.pointerType
    }
    document.addEventListener('pointerdown', onDown, true)
    return () => document.removeEventListener('pointerdown', onDown, true)
  }, [])
  // "AI로 배치하기" 미리보기 제안 (저장 전 점선 블록). null 이면 미리보기 중 아님
  const [proposals, setProposals] = useState<Proposal[] | null>(null)
  const [proposalUnscheduled, setProposalUnscheduled] = useState(0)
  const [proposalKind, setProposalKind] = useState<'fill' | 'new'>('fill')
  const resolveRef = useRef<PreviewFillOptions['onResolve']>(undefined)
  const today = todayKst()

  const toggleComplete = useToggleComplete()
  const deleteTask = useDeleteTask()
  const unlock = useUnlockTask()
  const unschedule = useUnscheduleTask()
  const moveDate = useMoveTaskDate()
  const reschedule = useRescheduleTask()
  const fillPreview = useFillPreview()
  const fillApply = useFillApply()
  const rescheduleBatch = useRescheduleBatch()
  const completeWithActual = useCompleteWithActual()
  const override = usePatternOverride()
  const routineScoped = useRoutineScopedReschedule()
  const patternScoped = usePatternScopedChange()
  const resetOverride = useDeletePatternOverride()
  // 자정을 넘기는 고정 시간 수정 때만 필요 → 그때만 불러와요
  const needsPatterns = editing?.kind === 'fixed' || opened?.kind === 'fixed'
  const patterns = useLifePatterns(needsPatterns)

  const days = useMemo(
    () => applyProposals(applyPending(rawDays, pending, fixedKey), proposals),
    [rawDays, pending, proposals]
  )

  // ── AI로 배치하기: 미리보기 → (끌어서 고치기) → 이대로 진행 / 취소 ──
  const editedProposals = (proposals ?? []).filter((p) => p.start !== p.proposedStart || p.end !== p.proposedEnd).length
  const previewFill = (target: FillPreviewTarget, o: PreviewFillOptions = {}) =>
    fillPreview.mutate(target, {
      onSuccess: (r) => {
        if (!r) return
        resolveRef.current = o.onResolve
        setProposalKind(o.kind ?? 'fill')
        track('fill_proposal.view', {
          surface,
          proposal_kind: o.kind ?? 'fill',
          placed_count: r.proposals.length,
          unscheduled_count: r.unscheduled.length,
        })
        o.onPreview?.(r)
        setProposals(
          r.proposals.map((p) => ({
            taskId: p.taskId,
            reason: p.reason,
            proposedStart: p.start,
            proposedEnd: p.end,
            start: p.start,
            end: p.end,
          }))
        )
        setProposalUnscheduled(r.unscheduled.length)
      },
      onError: () => o.onError?.(),
    })
  const cancelProposals = (method: 'cancel_button') => {
    track('fill_proposal.cancel', {
      surface,
      proposal_kind: proposalKind,
      close_method: method,
      dragged_count: editedProposals,
    })
    resolveRef.current?.(false, proposals ?? [])
    resolveRef.current = undefined
    setProposals(null)
  }
  const applyProposalsNow = () => {
    if (!proposals?.length) return
    track('fill_proposal.apply_click', {
      surface,
      proposal_kind: proposalKind,
      placed_count: proposals.length,
      dragged_count: editedProposals,
    })
    const kind = proposalKind
    fillApply.mutate(
      {
        items: proposals.map((p) => ({
          taskId: p.taskId,
          proposedStart: p.proposedStart,
          proposedEnd: p.proposedEnd,
          start: p.start,
          end: p.end,
          reason: p.reason,
        })),
      },
      {
        onSuccess: () => {
          track('fill_proposal.result', { surface, proposal_kind: kind, result: 'success' })
          resolveRef.current?.(true, proposals)
          resolveRef.current = undefined
          setProposals(null)
        },
        onError: (e) =>
          track('fill_proposal.result', {
            surface,
            proposal_kind: kind,
            result: 'fail',
            error_status: errorStatusOf(e),
          }),
      }
    )
  }
  // 미리보기 중 Esc → 취소 (상세 창·범위 고르기가 떠 있으면 그쪽이 먼저)
  const proposing = proposals !== null
  useEffect(() => {
    if (!proposing) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !e.defaultPrevented && !opened && !editing) {
        track('fill_proposal.cancel', {
          surface,
          proposal_kind: proposalKind,
          close_method: 'esc',
          dragged_count: editedProposals,
        })
        resolveRef.current?.(false, [])
        resolveRef.current = undefined
        setProposals(null)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [proposing, opened, editing, surface, proposalKind, editedProposals])

  // 묶음 중 지금도 옮길 수 있는 Task 만 (완료했거나 시간 미정이 된 Task 는 빠져요)
  const groupIds = useMemo(() => {
    const live = new Set(
      days
        .flatMap((d) => d.tasks)
        .filter(groupable)
        .map((t) => t.taskId)
    )
    return new Set(group.filter((id) => live.has(id))) as ReadonlySet<number>
  }, [days, group])
  const clearGroup = () => {
    trackGrid('timetable.group_clear', { group_size: groupIds.size, close_method: 'cancel_button' })
    setGroup([])
  }

  // 22시 이월 제안 (아직 확인 안 한 것) — 오늘 이후, 시간순
  const carryProposals = useMemo(() => {
    const seen = new Set<number>()
    return days
      .flatMap((d) => [...d.tasks, ...d.unscheduledTasks])
      .filter((t) => {
        if (!t.carryPending || t.taskStatus !== 'TODO' || seen.has(t.taskId)) return false
        seen.add(t.taskId)
        return (t.plannedStartAt?.slice(0, 10) ?? t.date ?? today) >= today
      })
      .sort((a, b) =>
        (a.plannedStartAt ?? `${a.date ?? today}T99`).localeCompare(b.plannedStartAt ?? `${b.date ?? today}T99`)
      )
  }, [days, today])

  // 확인을 기다리는 블록 (화면 이동 · 강조용)
  const focusIds = useMemo<ReadonlySet<number>>(() => {
    if (proposals) return new Set(proposals.map((p) => p.taskId))
    const carry = carryProposals[0]
    return new Set(carry && !ask && groupIds.size === 0 ? [carry.taskId] : [])
  }, [proposals, carryProposals, ask, groupIds])

  // 데이터가 새로 오면 팝오버 안의 Task 도 최신 값으로
  const liveTask = (t: TimeBlockTask) =>
    days.flatMap((d) => [...d.tasks, ...d.unscheduledTasks]).find((x) => x.taskId === t.taskId) ?? t

  const selection: Selection =
    opened?.kind === 'task'
      ? { kind: 'task', id: opened.task.taskId }
      : opened?.kind === 'fixed'
        ? { kind: 'fixed', key: fixedKey(opened.block) }
        : opened?.kind === 'activity' && opened.draft.activity
          ? { kind: 'activity', id: opened.draft.activity.activityId }
          : null

  const close = () => {
    setOpened(null)
    setConfirmDeleteId(null)
  }

  // ⚠️ e.currentTarget 은 이벤트 처리 중에만 값이 있어요 → setState 콜백 밖에서 먼저 위치를 읽어 둬요
  /**
   * 묶음에 넣기/빼기 — Shift·⌘·Ctrl+클릭, 터치 3초 꾹(long_press), 묶음 고르는 중 터치 탭(touch_tap)
   * method 는 터치일 때만 timetable.group_select 의 select_method 로 보내요 (Shift+클릭은 예전 그대로)
   */
  const toggleGroup = (task: TimeBlockTask, method?: 'long_press' | 'touch_tap', onlyAdd = false) => {
    {
      const date = blockDay(task)
      const all = days.flatMap((d) => d.tasks)
      // 묶음은 같은 날 Task 끼리만 — 다른 날 Task 를 고르면 새로 시작해요
      const sameDay = group.filter((id) => blockDay(all.find((x) => x.taskId === id) ?? task) === date)
      // 처음 묶을 때 상세 창이 열려 있던 같은 날 Task 도 같이 넣어요
      const seed =
        !sameDay.length && opened?.kind === 'task' && opened.task.taskId !== task.taskId
          ? [opened.task].filter((o) => groupable(o) && blockDay(o) === date).map((o) => o.taskId)
          : []
      const base = [...seed, ...sameDay]
      const removing = base.includes(task.taskId)
      if (removing && onlyAdd) return
      const next = removing ? base.filter((id) => id !== task.taskId) : [...base, task.taskId]
      trackGrid('timetable.group_select', {
        task_id: task.taskId,
        group_action: removing ? 'remove' : 'add',
        group_size: next.length,
        ...(method ? { select_method: method } : {}),
      })
      setGroup(next)
      setOpened(null)
      setConfirmDeleteId(null)
    }
  }
  const onSelectTask = (task: TimeBlockTask, e: MouseEvent<HTMLElement>) => {
    // Shift · ⌘ · Ctrl + 클릭 → 묶음에 넣기/빼기 (상세 창은 열지 않아요)
    if ((e.shiftKey || e.metaKey || e.ctrlKey) && groupable(task)) {
      e.preventDefault()
      toggleGroup(task)
      return
    }
    // 터치로 묶음을 고르는 중이면 탭 = 넣기/빼기 (명세 10.5)
    const touchTap =
      ('pointerType' in e.nativeEvent && (e.nativeEvent as globalThis.PointerEvent).pointerType === 'touch') ||
      lastPointer.current === 'touch'
    // 묶음 고르는 중 터치 탭은 묶음 토글만 — 상세 창은 열지 않아요 (묶을 수 없는 Task 도 창을 열지 않음)
    if (touchTap && groupIds.size > 0) {
      e.preventDefault()
      if (groupable(task)) toggleGroup(task, 'touch_tap')
      return
    }
    const anchor = rectOf(e.currentTarget)
    // 격자(시간 미정 칸 포함)에서 눌렀을 때만 — 월간·할 일 목록에서 연 건 그쪽 이벤트가 있어요. 같은 블록을 다시 눌러 닫을 땐 빼요
    const fromGrid = !!e.currentTarget.closest('[data-testid="time-grid"]')
    if (fromGrid && !(opened?.kind === 'task' && opened.task.taskId === task.taskId)) {
      trackGrid('timetable.task_block_click', {
        view_mode: viewMode,
        task_id: task.taskId,
        source_type: task.sourceType,
        task_status: task.taskStatus,
        is_scheduled: !!task.plannedStartAt,
        is_locked: !!task.locked,
        drag_source: task.proposed ? 'proposal' : e.currentTarget.dataset.actual ? 'actual' : 'task',
      })
    }
    setConfirmDeleteId(null)
    setOpened((o) => (o?.kind === 'task' && o.task.taskId === task.taskId ? null : { kind: 'task', task, anchor }))
  }
  const onSelectFixed = (block: FixedBlock, e: MouseEvent<HTMLElement>) => {
    const anchor = rectOf(e.currentTarget)
    if (!(opened?.kind === 'fixed' && fixedKey(opened.block) === fixedKey(block))) {
      trackGrid('timetable.fixed_block_click', { pattern_type: block.patternType, is_overridden: !!block.overridden })
    }
    setOpened((o) =>
      o?.kind === 'fixed' && fixedKey(o.block) === fixedKey(block) ? null : { kind: 'fixed', block, anchor }
    )
  }

  // ── 실제로 한 일 ──
  const onSelectActivity = (a: ActualActivity, e: MouseEvent<HTMLElement>) => {
    const anchor = rectOf(e.currentTarget)
    if (!(opened?.kind === 'activity' && opened.draft.activity?.activityId === a.activityId)) {
      trackGrid('timetable.activity_block_click', { days_ago: daysAgoOf(a.startAt.slice(0, 10), today) })
    }
    setConfirmDeleteId(null)
    setOpened((o) =>
      o?.kind === 'activity' && o.draft.activity?.activityId === a.activityId
        ? null
        : { kind: 'activity', draft: draftOf(a), anchor }
    )
  }
  /** 새 기록 열기 (끝은 지금을 넘지 않게) */
  const openActivity = (draft: ActivityDraft, anchor: AnchorRect) => {
    const now = nowKst()
    const nowMin = draft.date === now.date ? now.minutes : Infinity
    const end = Math.min(draft.end, nowMin)
    const start = Math.min(draft.start, end - 10)
    setConfirmDeleteId(null)
    setOpened({ kind: 'activity', draft: { ...draft, start: Math.max(start, 0), end }, anchor })
  }
  /** 빈 칸 클릭 → 그 시각부터 1시간 */
  const onEmptyClick = (date: string, minutes: number, e: MouseEvent<HTMLElement>) => {
    trackGrid('timetable.empty_slot_click', {
      view_date: date,
      date_relation: dateRelationOf(date, today),
      slot_minutes: minutes,
    })
    const anchor = { top: e.clientY, bottom: e.clientY, left: e.clientX, right: e.clientX }
    if (opts.onEmptyMenu) {
      setOpened(null)
      setConfirmDeleteId(null)
      opts.onEmptyMenu({ date, minutes }, anchor)
      return
    }
    openActivity({ date, start: minutes, end: minutes + 60 }, anchor)
  }

  // ── Task 작업 ──
  const taskActions = {
    // 계획했던 시간에 다른 일을 했어요 → 그 시간으로 실제 기록 (계획 Task 연결)
    onRecordInstead: (t: TimeBlockTask) => {
      if (!t.plannedStartAt) return
      const day = t.plannedStartAt.slice(0, 10)
      const s = minutesFrom(day, t.plannedStartAt)
      const e = t.plannedEndAt ? minutesFrom(day, t.plannedEndAt) : s + (t.plannedDurationMinutes ?? 30)
      const anchor = opened?.anchor ?? { top: 120, bottom: 120, left: 120, right: 120 }
      openActivity({ date: day, start: s, end: e, replaced: { id: t.taskId, name: t.taskName } }, anchor)
    },
    onToggleComplete: (t: TimeBlockTask) => toggleComplete.mutate(t, { onSuccess: close }),
    onCompleteWithTime: (t: TimeBlockTask) => {
      if (!t.plannedStartAt) return
      close()
      // 이미 실제 시간을 남겼으면 그 시간부터 고쳐요 (나눠서 한 구간은 모달이 actualSegments 로 채워요)
      const hasActual = t.taskStatus === 'COMPLETED' && !!t.actualStartAt && !!t.actualEndAt
      // 실제로 다른 날 했으면 그 날 기준
      const day = (hasActual ? t.actualStartAt! : t.plannedStartAt).slice(0, 10)
      const s = minutesFrom(day, hasActual ? t.actualStartAt! : t.plannedStartAt)
      const e = hasActual
        ? minutesFrom(day, t.actualEndAt!)
        : t.plannedEndAt
          ? minutesFrom(day, t.plannedEndAt)
          : s + (t.plannedDurationMinutes ?? 30)
      setEditing({ kind: 'actual', task: t, initial: { date: day, start: s, end: Math.max(e, s + 10) } })
    },
    onUnlock: (t: TimeBlockTask) => unlock.mutate(t.taskId, { onSuccess: close }),
    onUnschedule: (t: TimeBlockTask) => unschedule.mutate(t.taskId, { onSuccess: close }),
    onMoveDate: (t: TimeBlockTask, date: string, label: string) =>
      moveDate.mutate({ taskId: t.taskId, date, keepTime: true, label }, { onSuccess: close }),
    // 삭제는 바로 지우지 않고 상세 창 안에 "삭제할까요?"를 띄워요 (Delete 키와 같음)
    onDelete: (t: TimeBlockTask) => setConfirmDeleteId(t.taskId),
    chainSize: (t: TimeBlockTask) => chainOf(t, days).length,
    onSelectChain: (t: TimeBlockTask) => {
      setGroup(chainOf(t, days).map((x) => x.taskId))
      close()
    },
  }

  // ── Delete / Backspace 키로 삭제 ──
  // 열린 상세 창의 Task, 또는 키보드로 고른(포커스된) 블록이 대상이에요. 입력칸에서 누른 건 무시.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Delete' && e.key !== 'Backspace') return
      const el = e.target instanceof HTMLElement ? e.target : null
      if (el?.closest('input, textarea, select, [contenteditable="true"]')) return

      const all = days.flatMap((d) => [...d.tasks, ...d.unscheduledTasks])
      let task: TimeBlockTask | undefined
      if (opened?.kind === 'task') {
        task = all.find((x) => x.taskId === opened.task.taskId) ?? opened.task
      } else {
        const block = el?.closest<HTMLElement>('[data-task-id]')
        const id = Number(block?.dataset.taskId)
        task = all.find((x) => x.taskId === id)
        // 포커스만 된 블록이면 상세 창을 열고 그 안에서 확인해요
        if (task && block) setOpened({ kind: 'task', task, anchor: rectOf(block) })
      }
      if (!task || task.sourceType === 'MISSION') return
      e.preventDefault()
      if (surface !== 'home') {
        track('timetable.delete_key', { surface, task_id: task.taskId, source_type: task.sourceType })
      }
      setConfirmDeleteId(task.taskId)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [opened, days, surface])

  /** 우클릭 메뉴 "수정" — 블록을 누른 것과 같은 상세 창 */
  const openTaskDetail = (task: TimeBlockTask, anchor: AnchorRect) => {
    setConfirmDeleteId(null)
    setOpened({ kind: 'task', task, anchor })
  }
  /** 우클릭 메뉴 "삭제" — 상세 창을 열고 그 안에서 "삭제할까요?" (Delete 키와 같음) */
  const requestDeleteTask = (task: TimeBlockTask, anchor: AnchorRect) => {
    if (task.sourceType === 'MISSION') return
    setOpened({ kind: 'task', task, anchor })
    setConfirmDeleteId(task.taskId)
  }

  const confirmDeleteTask = () => {
    if (confirmDeleteId === null) return
    deleteTask.mutate(confirmDeleteId, { onSuccess: close })
  }

  // ── 고정 시간 작업 ──
  const fixedActions = {
    onEditTime: (b: FixedBlock) => {
      close()
      const patternEnd = patterns.data?.find((p) => p.lifePatternId === b.lifePatternId)?.endTime
      setEditing({
        kind: 'fixed',
        block: b,
        initial: {
          date: b.targetDate,
          start: minutesFrom(b.targetDate, b.start),
          end: fullFixedEnd(b, days, patternEnd),
        },
      })
    },
    onSkip: (b: FixedBlock) =>
      override.mutate(
        { lifePatternId: b.lifePatternId, targetDate: b.targetDate, skipped: true },
        { onSuccess: close }
      ),
    onReset: (b: FixedBlock) =>
      resetOverride.mutate({ lifePatternId: b.lifePatternId, targetDate: b.targetDate }, { onSuccess: close }),
  }

  // ── 범위를 골라 실제로 저장 (이날만 / 이번 주 / 앞으로) ──
  const saveRoutine = (
    task: TimeBlockTask,
    date: string,
    start: number,
    end: number,
    scope: ChangeScope,
    opts: object
  ) =>
    routineScoped.mutate(
      { taskId: task.taskId, plannedStartAt: toDateTime(date, start), plannedEndAt: toDateTime(date, end), scope },
      opts
    )
  /**
   * @param date start/end 의 기준 날짜 (끌어놓은 칸). 블록 소속 날짜(targetDate)와 시작 날짜가 다르면
   *             startDayOffset 으로 알려줘요 — 예: 토요일 소속 수면(00:00)을 금요일 밤 23:30 취침으로 → -1
   */
  const saveFixed = (block: FixedBlock, date: string, start: number, end: number, scope: ChangeScope, opts: object) => {
    const startDate = addDays(date, Math.floor(start / 1440))
    const startDayOffset = Math.round(minutesFrom(block.targetDate, `${startDate}T00:00:00`) / 1440)
    patternScoped.mutate(
      {
        lifePatternId: block.lifePatternId,
        targetDate: block.targetDate,
        startTime: minutesToTime(start),
        endTime: minutesToTime(end),
        scope,
        startDayOffset,
      },
      opts
    )
  }

  const dropPending = (p: Pending) => setPending((list) => list.filter((x) => x !== p))

  const chooseScope = (scope: ChangeScope) => {
    if (!ask) return
    const result = { surface, scope_target: scopeTargetOf(ask), change_scope: scope }
    // 성공·실패 모두 TimeTable 을 다시 불러온 뒤 임시 표시를 지워요
    const opts = {
      onSettled: () => dropPending(ask.pending),
      onSuccess: () => track('scope_chooser.result', { ...result, result: 'success' }),
      onError: (e: unknown) =>
        track('scope_chooser.result', { ...result, result: 'fail', error_status: errorStatusOf(e) }),
    }
    if (ask.kind === 'routine') saveRoutine(ask.task, ask.date, ask.start, ask.end, scope, opts)
    else saveFixed(ask.block, ask.date, ask.start, ask.end, scope, opts)
    setAsk(null)
  }
  const cancelAsk = () => {
    if (ask) dropPending(ask.pending)
    setAsk(null)
  }

  // 범위를 고르지 않고 떠나면(화면 이동 등) "이날만"으로 저장해요
  const autoDay = (reason: ScopeAutoReason) => {
    if (ask) track('scope_chooser.auto_day', { surface, scope_target: scopeTargetOf(ask), auto_reason: reason })
  }
  const askRef = useRef<{
    ask: ScopeAsk | null
    save: (scope: ChangeScope) => void
    autoDay: (reason: ScopeAutoReason) => void
  }>({ ask: null, save: () => {}, autoDay: () => {} })
  useEffect(() => {
    askRef.current = { ask, save: chooseScope, autoDay }
  })
  useEffect(
    () => () => {
      if (!askRef.current.ask) return
      askRef.current.autoDay('leave_page')
      askRef.current.save('DAY')
    },
    []
  )

  // 묶음: Esc 로 풀기 (상세 창·범위 고르기가 떠 있을 땐 그쪽이 Esc 를 받아요)
  const groupSize = groupIds.size
  useEffect(() => {
    if (!group.length) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !opened && !ask && !editing) {
        if (surface !== 'home') {
          track('timetable.group_clear', { surface, group_size: groupSize, close_method: 'esc' })
        }
        setGroup([])
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [group.length, opened, ask, editing, surface, groupSize])

  // ── 드래그 앤 드롭 (명세 3-4) ──
  /** @param mode 끌기 종류 (분석용 — TimeGrid 가 넘겨줘요) */
  const onDrop = (source: DragSource, target: DropTarget, mode?: DragMode) => {
    const dragged = source.kind === 'fixed' ? null : source.task
    const groupSizeOf = source.kind === 'task' && source.group?.length ? source.group.length + 1 : undefined
    trackGrid('timetable.drag_drop', {
      view_mode: viewMode,
      drag_source: dragSourceOf(source),
      drag_mode: dragModeOf(mode),
      drop_target: target.type,
      task_id: dragged?.taskId,
      source_type: dragged?.sourceType,
      // 같은 날 루틴 · 고정 시간은 놓은 뒤 "어디까지 바꿀까요?"를 물어요 (아래 분기와 같은 조건)
      needs_scope:
        target.type === 'grid' &&
        !dragged?.proposed &&
        (source.kind === 'fixed' ||
          (source.kind === 'task' &&
            !source.group?.length &&
            source.task.sourceType === 'ROUTINE' &&
            routineDay(source.task) === target.date)),
      group_size: groupSizeOf,
      // 2026-10: 다른 날로 옮겼는지 (시간 미정 → 다른 날 시간 미정 포함)
      day_offset: source.kind === 'task' && target.date !== source.date ? dayDiff(source.date, target.date) : undefined,
      from_unscheduled: source.kind === 'task' ? source.start === null : undefined,
    })
    /** timetable.move_result — 저장 요청이 끝난 직후 */
    const moveResult = (props: EventProps) => ({
      onSuccess: () => trackGrid('timetable.move_result', { ...props, result: 'success' }),
      onError: (e: unknown) =>
        trackGrid('timetable.move_result', { ...props, result: 'fail', error_status: errorStatusOf(e) }),
    })
    // 미리보기 제안 블록: 저장하지 않고 제안 자리만 바꿔요 (시간 미정 칸으로 끌면 이번 배치에서 빼요)
    if (source.kind === 'task' && source.task.proposed) {
      setProposals((list) =>
        (list ?? []).flatMap((p) =>
          p.taskId !== source.task.taskId
            ? [p]
            : target.type === 'grid'
              ? [{ ...p, start: toDateTime(target.date, target.start), end: toDateTime(target.date, target.end) }]
              : []
        )
      )
      return
    }
    // 범위를 고르지 않고 다음 드래그를 하면 앞의 변경은 "이날만"으로 저장해요
    if (ask) {
      autoDay('next_drag')
      chooseScope('DAY')
    }

    // 실제로 한 시간 블록: 끌어 놓은 자리로 실제 시간을 바로 고쳐요 (나눠서 했으면 그 구간만)
    if (source.kind === 'actual') {
      if (target.type !== 'grid') return
      const segs = source.task.actualSegments ?? []
      const ranges =
        segs.length > 1
          ? segs.map((g, i) =>
              i === source.part
                ? { start: toDateTime(target.date, target.start), end: toDateTime(target.date, target.end) }
                : g
            )
          : [{ start: toDateTime(target.date, target.start), end: toDateTime(target.date, target.end) }]
      ranges.sort((a, b) => (a.start < b.start ? -1 : a.start > b.start ? 1 : 0))
      completeWithActual.mutate(
        {
          taskId: source.task.taskId,
          actualStartAt: ranges[0].start,
          actualEndAt: ranges[ranges.length - 1].end,
          segments: ranges.length > 1 ? ranges : undefined,
        },
        moveResult({ drag_source: 'actual', drop_target: 'grid' })
      )
      return
    }

    // 묶음 드래그: 끈 만큼 묶음 전체를 같이 옮기고 한 번에 저장 (루틴도 이날만 바뀌어요)
    if (source.kind === 'task' && source.group?.length && target.type === 'grid') {
      const moves = groupMoves(source, target)
      const ps: Pending[] = moves.map((m) => ({
        kind: 'task',
        taskId: m.task.taskId,
        date: m.date,
        start: m.start,
        end: m.end,
      }))
      setPending((list) => [...list, ...ps])
      rescheduleBatch.mutate(
        {
          items: moves.map((m) => ({
            taskId: m.task.taskId,
            plannedStartAt: toDateTime(m.date, m.start),
            plannedEndAt: toDateTime(m.date, m.end),
          })),
        },
        {
          onSettled: () => setPending((list) => list.filter((x) => !ps.includes(x))),
          ...moveResult({ drag_source: 'group', drop_target: 'grid', group_size: moves.length }),
        }
      )
      return
    }
    const p: Pending | null =
      source.kind === 'task'
        ? target.type === 'grid'
          ? { kind: 'task', taskId: source.task.taskId, date: target.date, start: target.start, end: target.end }
          : {
              kind: 'task',
              taskId: source.task.taskId,
              date: null,
              start: 0,
              end: 0,
              toDate: target.date !== source.date ? target.date : undefined,
            }
        : target.type === 'grid'
          ? { kind: 'fixed', key: fixedKey(source.block), date: target.date, start: target.start, end: target.end }
          : null
    if (!p) return
    setPending((list) => [...list, p])

    // 루틴을 같은 날 안에서 옮겼거나, 고정 시간을 옮겼으면 → 범위를 물어봐요
    if (
      source.kind === 'task' &&
      target.type === 'grid' &&
      source.task.sourceType === 'ROUTINE' &&
      routineDay(source.task) === target.date
    ) {
      setAsk({
        kind: 'routine',
        task: source.task,
        date: target.date,
        start: target.start,
        end: target.end,
        pending: p,
      })
      return
    }
    if (source.kind === 'fixed' && target.type === 'grid') {
      setAsk({
        kind: 'fixed',
        block: source.block,
        date: target.date,
        start: target.start,
        end: target.end,
        pending: p,
        edge: source.block.patternType === 'SLEEP' ? (target.start !== source.start ? 'bed' : 'wake') : undefined,
      })
      return
    }

    const done = {
      onSettled: () => dropPending(p),
      ...moveResult({ drag_source: 'task', drop_target: target.type }),
    }
    if (source.kind === 'task' && target.type === 'grid') {
      reschedule.mutate(
        {
          taskId: source.task.taskId,
          plannedStartAt: toDateTime(target.date, target.start),
          plannedEndAt: toDateTime(target.date, target.end),
        },
        done
      )
    } else if (source.kind === 'task' && target.type === 'unscheduled' && target.date !== source.date) {
      // 다른 날의 시간 미정 칸 → 그날 시간 미정으로 (시간 미정 → 시간 미정도 여기로, 2026-10)
      moveDate.mutate({ taskId: source.task.taskId, date: target.date, keepTime: false }, done)
    } else if (source.kind === 'task') {
      unschedule.mutate(source.task.taskId, done)
    }
  }

  const submitEdit = (v: TimeValue, scope: ChangeScope) => {
    if (!editing) return
    const done = { onSuccess: () => setEditing(null) }
    if (editing.kind === 'actual') {
      submitActual([{ start: v.start, end: v.end }])
    } else {
      saveFixed(editing.block, v.date, v.start, v.end, scope, done)
    }
  }

  /** 실제로 한 시간 저장 (여러 번 나눠서 했으면 구간마다) */
  const submitActual = (segments: ActualSegment[]) => {
    if (editing?.kind !== 'actual' || !segments.length) return
    const date = editing.initial.date
    const first = segments[0]
    const last = segments[segments.length - 1]
    completeWithActual.mutate(
      {
        taskId: editing.task.taskId,
        actualStartAt: toDateTime(date, first.start),
        actualEndAt: toDateTime(date, last.end),
        segments:
          segments.length > 1
            ? segments.map((g) => ({ start: toDateTime(date, g.start), end: toDateTime(date, g.end) }))
            : undefined,
      },
      { onSuccess: () => setEditing(null) }
    )
  }

  const busyTask = toggleComplete.isPending || unlock.isPending || unschedule.isPending
  const busyFixed = override.isPending || resetOverride.isPending || patternScoped.isPending

  const overlays = (
    <>
      {/* 블록 상세 */}
      {opened?.kind === 'task' && (
        <Popover anchor={opened.anchor} onClose={close} label="Task 상세" width={360}>
          <TaskDetail
            key={opened.task.taskId}
            task={liveTask(opened.task)}
            busy={busyTask}
            actions={taskActions}
            confirmingDelete={confirmDeleteId === opened.task.taskId}
            deleting={deleteTask.isPending}
            onConfirmDelete={confirmDeleteTask}
            onCancelDelete={() => setConfirmDeleteId(null)}
            surface={surface}
          />
        </Popover>
      )}
      {opened?.kind === 'activity' && (
        <Popover anchor={opened.anchor} onClose={close} label="실제로 한 일" width={340}>
          <ActivityForm
            key={`${opened.draft.activity?.activityId ?? 'new'}-${opened.draft.date}-${opened.draft.start}`}
            draft={opened.draft}
            onDone={close}
            surface={surface}
          />
        </Popover>
      )}
      {opened?.kind === 'fixed' && (
        <Popover anchor={opened.anchor} onClose={close} label="고정 시간 상세">
          <FixedDetail block={opened.block} busy={busyFixed} actions={fixedActions} surface={surface} />
        </Popover>
      )}

      {/* 실제로 한 시간 (나눠서 한 경우 여러 구간) */}
      {editing?.kind === 'actual' && (
        <ActualTimeModal
          key={`a${editing.task.taskId}`}
          task={liveTask(editing.task)}
          date={editing.initial.date}
          initial={editing.initial}
          loading={completeWithActual.isPending}
          onSubmit={submitActual}
          onClose={() => setEditing(null)}
          surface={surface}
        />
      )}

      {/* 시간 변경 */}
      {editing?.kind === 'fixed' && (
        <TimeEditModal
          key={`f${fixedKey(editing.block)}`}
          title={`${formatMonthDay(editing.block.targetDate)} ${editing.block.title} 시간`}
          description="적용 범위를 골라 주세요. 이날만 바꾸면 다음 날부터는 기본 시간이에요."
          scope={{ kind: 'fixed', today }}
          initial={editing.initial}
          dateEditable={false}
          loading={reschedule.isPending || patternScoped.isPending || routineScoped.isPending}
          onSubmit={submitEdit}
          onClose={() => setEditing(null)}
          surface={surface}
          patternType={editing.block.patternType}
        />
      )}

      {/* 드래그 후 범위 고르기 */}
      {ask && (
        <ScopeChooser
          // 다른 변경으로 바뀌면 새로 떠요 (scope_chooser.view 한 번씩)
          key={`${ask.kind}-${ask.date}-${ask.start}-${ask.end}-${ask.kind === 'routine' ? ask.task.taskId : fixedKey(ask.block)}`}
          surface={surface}
          scopeTarget={scopeTargetOf(ask)}
          title={
            ask.kind === 'routine'
              ? `'${ask.task.taskName}' → ${formatMinutes(ask.start)}–${formatMinutes(ask.end)}`
              : ask.edge
                ? ask.edge === 'wake'
                  ? `☀️ 기상 ${minutesToTime(minutesFrom(ask.date, ask.block.end))} → ${minutesToTime(ask.end)}`
                  : `🌙 취침 ${minutesToTime(minutesFrom(ask.date, ask.block.start))} → ${minutesToTime(ask.start)}`
                : `${ask.block.emoji ? `${ask.block.emoji} ` : ''}${ask.block.title} → ${formatMinutes(ask.start)}–${formatMinutes(ask.end)}`
          }
          options={scopeOptions({
            kind: ask.kind,
            isToday: ask.date === today,
            crossesMidnight: ask.end >= 1440,
            // 고정 시간: "앞으로 ○요일마다" — 수면은 끌어놓은 칸 기준 "토요일 아침/밤"
            weekdayLabel:
              ask.kind === 'fixed'
                ? `${WEEKDAY_LABEL[weekdayIndex(ask.date)]}요일${ask.edge ? (ask.edge === 'wake' ? ' 아침' : ' 밤') : ''}`
                : undefined,
          })}
          onChoose={chooseScope}
          onCancel={cancelAsk}
          onDismiss={() => chooseScope('DAY')}
        />
      )}

      {/* AI 배치 미리보기 확인 — 범위 고르기 바가 없을 때 */}
      {proposals && !ask && (
        <FillProposalBar
          kind={proposalKind}
          sidePanel={opts.sidePanel}
          count={proposals.length}
          edited={editedProposals}
          unscheduled={proposalUnscheduled}
          loading={fillApply.isPending}
          onApply={applyProposalsNow}
          onCancel={cancelProposals}
        />
      )}

      {/* 22시 이월 제안 — 다른 안내 바가 없을 때만 (범위 고르기·묶음·AI 배치 확인이 먼저) */}
      {!ask && !proposals && groupIds.size === 0 && (
        <CarryProposalBar tasks={carryProposals} today={today} surface={surface} />
      )}

      {/* 묶음 드래그 안내 */}
      {groupIds.size > 0 && !ask && !proposals && (
        <div className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--tabbar-h)+16px)] z-40 flex justify-center px-4">
          <div
            role="status"
            className="bg-inverse pointer-events-auto flex max-w-[560px] items-center gap-2 rounded-full py-2 pr-2 pl-4 text-[13px] text-on-ink shadow-pop"
          >
            <Link2 className="size-4 shrink-0" />
            <span className="min-w-0">
              {groupIds.size > 1 ? (
                <>
                  <b>{groupIds.size}개 묶음</b> · 하나를 끌면 같이 움직여요
                </>
              ) : (
                <>
                  <b>1개 선택</b> · Shift(⌘)+클릭으로 더 묶어요
                </>
              )}
            </span>
            <button
              type="button"
              onClick={clearGroup}
              className="flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold hover:bg-on-ink/15"
            >
              <X className="size-3.5" />
              묶음 풀기
            </button>
          </div>
        </div>
      )}
    </>
  )

  return {
    days,
    selection,
    /** 실제로 한 시간 입력 창 열기 ("그때 했어요" — 이월 흔적에서 되돌린 뒤) */
    completeWithTime: taskActions.onCompleteWithTime,
    onSelectTask,
    onSelectFixed,
    onSelectActivity,
    onEmptyClick,
    openActivity,
    openTaskDetail,
    requestDeleteTask,
    onDrop,
    close,
    toggleComplete,
    overlays,
    /** 묶음 드래그로 같이 옮길 Task (TimeGrid groupIds 로 넘겨요) */
    groupIds,
    /** AI로 배치하기 — 미리보기(점선)부터 */
    previewFill,
    fillPreviewing: fillPreview.isPending,
    proposing,
    /** 지금 보여 주는 점선 제안 (끌어서 고친 자리 반영) — 미리보기 중이 아니면 null */
    proposals,
    /**
     * 확인을 기다리는 블록 — AI 배치 미리보기면 제안들(나머지는 흐리게), 아니면 화면 아래에서 묻고 있는 이월 제안 하나.
     * TimeGrid focusIds · fadeOthers 로 넘겨요.
     */
    focusIds,
    fadeOthers: proposing,
    /**
     * 격자 분석 콜백 (timetable.drag_cancel · unscheduled_more_click · habit_block_toggle).
     * TimeGrid 에 그대로 펼쳐 넘겨요: <TimeGrid {...gridEvents} … />. 홈(surface='home')에서는 아무것도 안 보내요.
     */
    gridEvents: {
      // 터치 묶음 고르기 (명세 10.5 · 13.1) — 2초 꾹 = 묶음에 넣기 · 빈 곳 탭 = 묶음 풀기
      onTaskHold: (t: TimeBlockTask) => {
        if (groupable(t)) toggleGroup(t, 'long_press', true)
      },
      onGroupClear: () => {
        trackGrid('timetable.group_clear', { group_size: groupIds.size, close_method: 'empty_tap' })
        setGroup([])
      },
      onDragCancel: (source: DragSource, mode: DragMode, reason: DragCancelReason, invalid?: string) =>
        trackGrid('timetable.drag_cancel', {
          drag_source: dragSourceOf(source),
          drag_mode: dragModeOf(mode),
          cancel_reason: reason === 'invalid' ? invalidReasonOf(invalid) : reason,
          validation_error: validationErrorOf(invalid),
        }),
      onUnscheduledMore: (date: string, remaining: number) =>
        trackGrid('timetable.unscheduled_more_click', { view_date: date, remaining_count: remaining }),
      onHabitToggle: (habit: HabitBlock, date: string) =>
        trackGrid('timetable.habit_block_toggle', {
          habit_id: habit.habitId,
          check_action: habit.checked ? 'uncomplete' : 'complete',
          date_relation: dateRelationOf(date, today),
        }),
    },
  }
}
