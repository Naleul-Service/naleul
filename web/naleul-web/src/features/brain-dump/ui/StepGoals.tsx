'use client'

import { useEffect, useRef, useState } from 'react'
import { AlertCircle, Briefcase, ChevronDown } from 'lucide-react'
import { CalendarClock, Clock, Pencil } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { isWorkGoal, type GoalCategory } from '@/features/goal/api'
import { useWorkBoard, type WorkTopic } from '@/features/work/api'
import { goalAccent } from '@/lib/goalTone'
import { formatDuration, formatMonthDay } from '@/features/timetable/time'
import type { TaskPriority } from '@/features/timetable/types'
import { PRIORITY_LABEL, PriorityChips, priorityOf } from '@/features/timetable/ui/Priority'
import type { ConfirmItem, ConfirmResponse, TempGoalInput } from '../types'
import type { TaskAddSurface } from '../analytics'
import { ItemEditModal } from './ItemEditModal'
import { AIBadge } from '@/components/ui/AIBadge'

/** 2단계에서 고치는 Task 하나 = 확정 요청 Item + AI 가 붙인 연결 이유 */
export interface MapItem extends ConfirmItem {
  reason?: string | null
}

export const MAX_TEMP_GOALS = 5

/** 연결된 목표 종류 (노션 goal_match) — existing: 내 목표 / temp: 이번 임시 목표 / none: 없음 */
export const goalMatchOf = (i: Pick<ConfirmItem, 'goalCategoryId' | 'tempGoalKey'>) =>
  i.goalCategoryId ? 'existing' : i.tempGoalKey ? 'temp' : 'none'

/**
 * task_add_goals.confirm_result — 저장 요청 결과는 부모(TaskAddView)의 confirm.mutate 콜백에서만 알 수 있어요.
 * 부모가 onSuccess 맨 앞에서 { res }, onError 에서 { error } 로 한 번 불러 주세요.
 * tempCount = 요청에 담은 임시 목표 수.
 */
export function trackGoalsConfirmResult(
  surface: TaskAddSurface,
  outcome: { res: ConfirmResponse } | { error: unknown },
  tempCount: number
) {
  if ('error' in outcome) {
    track('task_add_goals.confirm_result', {
      surface,
      result: 'fail',
      error_status: errorStatusOf(outcome.error) ?? 0,
      temp_count: tempCount,
    })
    return
  }
  const { res } = outcome
  if (!res.created) {
    track('task_add_goals.confirm_result', {
      surface,
      result: 'conflict',
      conflict_count: res.conflicts.length,
      temp_count: tempCount,
    })
    return
  }
  track('task_add_goals.confirm_result', {
    surface,
    result: 'success',
    conflict_count: res.conflicts.length,
    placed_count: res.placements.length,
    moved_count: res.movedToOtherDays.length,
    unscheduled_count: res.unscheduled.length,
    pending_count: (res.pending ?? []).length,
    temp_count: tempCount,
  })
}

interface Props {
  items: MapItem[]
  tempGoals: TempGoalInput[]
  goals: GoalCategory[]
  aiFailed: boolean
  aiLimited?: boolean
  /** clientKey → 겹친 일정 안내 (확정 시 409 성격의 conflicts) */
  conflicts: Record<string, string>
  today: string
  loading: boolean
  onChange: (items: MapItem[], tempGoals: TempGoalInput[]) => void
  onBack: () => void
  onConfirm: () => void
  /** 어느 화면에서 열렸는지 (task_add_goals.* · task_add_item_edit.* 의 surface) */
  surface?: TaskAddSurface
}

type Group = { key: string; title: string; emoji?: string | null; color: string; temp: boolean; items: MapItem[] }

/**
 * 2단계 · 목표 연결 (명세 3-2)
 *  - AI 결과를 목표별로 묶어서 보여줘요. 관련 목표가 없으면 임시 목표.
 *  - "변경"으로 목표·날짜·시간·소요 시간·중요도를 고쳐요.
 *  - 모든 Task 가 목표에 연결돼야 "TimeBlock 만들기"를 누를 수 있어요 (백엔드 BRAIN_DUMP_GOAL_REQUIRED).
 */
export function StepGoals({
  items,
  tempGoals,
  goals,
  aiFailed,
  aiLimited = false,
  conflicts,
  today,
  loading,
  onChange,
  onBack,
  onConfirm,
  surface = 'page',
}: Props) {
  const [editing, setEditing] = useState<MapItem | null>(null)
  /** 2단계에서 "변경 → 저장"으로 실제로 바뀐 Task (edited_count) */
  const edited = useRef(new Set<string>())
  const viewed = useRef(false)
  /** 임시 목표 이름 칸에 들어갈 때의 값 — 벗어날 때 바뀌었으면 temp_rename */
  const renameFrom = useRef<string | null>(null)

  const groups: Group[] = []
  const byKey = new Map<string, Group>()
  for (const it of items) {
    let key = 'none'
    let g: Omit<Group, 'items'> = { key, title: '목표를 골라 주세요', color: goalAccent(null), temp: false }
    if (it.goalCategoryId) {
      const goal = goals.find((x) => x.goalCategoryId === it.goalCategoryId)
      key = `g${it.goalCategoryId}`
      g = {
        key,
        title: goal?.goalCategoryName ?? '목표',
        emoji: goal?.emoji,
        color: goalAccent(goal?.colorCode),
        temp: false,
      }
    } else if (it.tempGoalKey) {
      const t = tempGoals.find((x) => x.tempKey === it.tempGoalKey)
      key = `t:${it.tempGoalKey}`
      g = { key, title: t?.name ?? '임시 목표', emoji: t?.emoji, color: 'var(--color-warning)', temp: true }
    }
    if (!byKey.has(key)) {
      const group = { ...g, items: [] }
      byKey.set(key, group)
      groups.push(group)
    }
    byKey.get(key)!.items.push(it)
  }
  // 목표가 없는 묶음은 맨 위에 (먼저 고르도록)
  groups.sort((a, b) => (a.key === 'none' ? -1 : b.key === 'none' ? 1 : 0))
  const missing = items.filter((i) => !i.goalCategoryId && !i.tempGoalKey).length
  const blankTemp = tempGoals.some((t) => !t.name.trim())
  const usedTempCount = tempGoals.filter((t) => items.some((i) => i.tempGoalKey === t.tempKey)).length
  /** 화면에 보이는 순서 (목표 없는 묶음이 맨 위) — list_position */
  const order = groups.flatMap((g) => g.items.map((i) => i.clientKey))

  useEffect(() => {
    if (viewed.current) return
    viewed.current = true
    track('task_add_goals.view', {
      surface,
      item_count: items.length,
      missing_goal_count: missing,
      temp_count: usedTempCount,
      ai_failed: aiFailed,
      ai_limited: aiLimited,
    })
  }, [surface, items.length, missing, usedTempCount, aiFailed, aiLimited])

  const openEdit = (it: MapItem) => {
    track('task_add_goals.edit_open', {
      surface,
      list_position: order.indexOf(it.clientKey),
      goal_match: goalMatchOf(it),
      has_conflict: !!conflicts[it.clientKey],
    })
    setEditing(it)
  }

  /** 행에서 바로 중요도 고르기 — "변경" 모달을 열지 않아도 돼요 */
  const selectPriority = (it: MapItem, p: TaskPriority) => {
    track('task_add_goals.priority_select', {
      surface,
      list_position: order.indexOf(it.clientKey),
      priority: p,
      prev_priority: priorityOf(it.priority),
    })
    edited.current.add(it.clientKey)
    onChange(
      items.map((i) => (i.clientKey === it.clientKey ? { ...i, priority: p } : i)),
      tempGoals
    )
  }

  /** 업무형 목표의 주제를 줄에서 바로 고르기 — 고른 주제는 업무 보드에서 그 주제 아래에 보여요 */
  const selectTopic = (it: MapItem, topicId: number | null, topicCount: number) => {
    track('task_add_goals.topic_select', {
      surface,
      list_position: order.indexOf(it.clientKey),
      has_topic: topicId !== null,
      topic_count: topicCount,
    })
    edited.current.add(it.clientKey)
    onChange(
      items.map((i) => (i.clientKey === it.clientKey ? { ...i, workTopicId: topicId } : i)),
      tempGoals
    )
  }

  const back = () => {
    track('task_add_goals.back_click', { surface, item_count: items.length, edited_count: edited.current.size })
    onBack()
  }

  const confirmBlocked = missing > 0 || blankTemp
  const onConfirmPointerDown = () => {
    if (loading || !confirmBlocked) return
    track('task_add_goals.confirm_blocked', {
      surface,
      disabled_reason: missing > 0 ? 'no_goal' : 'blank_temp',
      missing_goal_count: missing,
    })
  }
  const confirm = () => {
    track('task_add_goals.confirm_click', {
      surface,
      item_count: items.length,
      temp_count: usedTempCount,
      timed_count: items.filter((i) => i.dateType === 'ON' && !!i.startTime).length,
      edited_count: edited.current.size,
      priority_a_count: items.filter((i) => priorityOf(i.priority) === 'A').length,
      work_topic_count: items.filter((i) => !!i.goalCategoryId && !!i.workTopicId).length,
    })
    onConfirm()
  }

  const renameTemp = (tempKey: string, name: string) =>
    onChange(
      items,
      tempGoals.map((t) => (t.tempKey === tempKey ? { ...t, name: name.slice(0, 30) } : t))
    )

  const save = (next: MapItem, newTemp?: TempGoalInput, changedFields: string[] = []) => {
    if (changedFields.length) edited.current.add(next.clientKey)
    const prev = items.find((i) => i.clientKey === next.clientKey)
    // 목표가 바뀌면 주제는 그 목표의 것이 아니라 비워요
    if (prev && prev.goalCategoryId !== next.goalCategoryId) next = { ...next, workTopicId: null }
    const list = items.map((i) => (i.clientKey === next.clientKey ? next : i))
    const temps = newTemp ? [...tempGoals, newTemp] : tempGoals
    // 아무 Task 도 가리키지 않는 임시 목표는 만들지 않아요
    const used = new Set(list.map((i) => i.tempGoalKey).filter(Boolean))
    onChange(
      list,
      temps.filter((t) => used.has(t.tempKey))
    )
    setEditing(null)
  }

  const nextTempKey = `t${tempGoals.reduce((m, t) => Math.max(m, Number(t.tempKey.slice(1)) || 0), 0) + 1}`

  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-h4">목표에 연결했어요</h2>
      <p className="text-ink-3 mt-1 text-sm">
        잘못 연결됐거나 날짜·시간이 다르면 &quot;변경&quot;을 눌러 고쳐 주세요. 관련 목표가 없는 일은 임시 목표로
        묶었어요.
      </p>

      {aiFailed && (
        <p className="bg-warning-soft text-warning-ink mt-4 flex items-start gap-2 rounded-xl px-4 py-3 text-sm">
          <AlertCircle className="mt-0.5 size-4 shrink-0" />
          {aiLimited
            ? '오늘 AI 정리 횟수를 다 써서 기본 정리로 나눠 두었어요. 목표는 "변경"에서 직접 골라 주세요. (내일 0시에 다시 채워져요 · Pro는 하루 30번)'
            : 'AI 정리가 잠시 안 돼서, 적은 내용만 나눠 두었어요. 목표는 "변경"에서 직접 골라 주세요.'}
        </p>
      )}
      {Object.keys(conflicts).length > 0 && (
        <p className="bg-danger-soft text-danger mt-4 flex items-start gap-2 rounded-xl px-4 py-3 text-sm">
          <AlertCircle className="mt-0.5 size-4 shrink-0" />
          직접 정한 시간이 다른 일정과 겹쳐서 저장하지 않았어요. 빨갛게 표시된 Task의 시간을 바꿔 주세요.
        </p>
      )}

      <div className="mt-5 space-y-5">
        {groups.map((g) => (
          <section key={g.key} aria-label={g.title}>
            <header className="mb-2 flex items-center gap-2">
              <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: g.color }} />
              {g.temp ? (
                <span className="flex min-w-0 items-center gap-1.5">
                  {g.emoji && <span>{g.emoji}</span>}
                  <input
                    value={g.title}
                    onChange={(e) => renameTemp(g.key.slice(2), e.target.value)}
                    onFocus={(e) => (renameFrom.current = e.target.value)}
                    onBlur={(e) => {
                      const from = renameFrom.current
                      renameFrom.current = null
                      if (from === null || from === e.target.value) return
                      track('task_add_goals.temp_rename', { surface, text_length: e.target.value.trim().length })
                    }}
                    aria-label="임시 목표 이름"
                    size={Math.max(g.title.length * 2, 6)}
                    className="focus:border-border-focus min-w-0 border-b border-transparent bg-transparent text-[15px] font-bold outline-none"
                  />
                  <Pencil className="text-ink-4 size-3.5 shrink-0" aria-hidden />
                  <Badge tone="warning">임시 목표</Badge>
                </span>
              ) : (
                <h3 className={cn('text-[15px] font-bold', g.key === 'none' && 'text-danger')}>
                  {g.emoji ? `${g.emoji} ` : ''}
                  {g.title}
                </h3>
              )}
              <span className="text-ink-4 text-xs">{g.items.length}개</span>
            </header>
            <ul className="space-y-2">
              {g.items.map((it) => (
                <ItemRow
                  key={it.clientKey}
                  item={it}
                  goals={goals}
                  conflict={conflicts[it.clientKey]}
                  onEdit={() => openEdit(it)}
                  onPriority={(p) => selectPriority(it, p)}
                  onTopic={(id, count) => selectTopic(it, id, count)}
                />
              ))}
            </ul>
          </section>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-end gap-3">
        {missing > 0 && <span className="text-danger text-sm">{missing}개 Task의 목표를 골라 주세요</span>}
        {blankTemp && <span className="text-danger text-sm">임시 목표 이름을 입력해 주세요</span>}
        <Button variant="secondary" onClick={back} disabled={loading}>
          이전
        </Button>
        {/* disabled 버튼은 click 이 안 나서 감싼 요소의 pointerdown 으로 막힌 클릭을 잡아요 */}
        <span className="contents" onPointerDown={onConfirmPointerDown}>
          <Button onClick={confirm} disabled={confirmBlocked || loading} loading={loading}>
            TimeBlock 만들기
          </Button>
        </span>
      </div>

      {editing && (
        <ItemEditModal
          key={editing.clientKey}
          item={editing}
          goals={goals}
          tempGoals={tempGoals}
          today={today}
          nextTempKey={nextTempKey}
          canAddTemp={tempGoals.length < MAX_TEMP_GOALS}
          onSave={save}
          onClose={() => setEditing(null)}
          surface={surface}
        />
      )}
    </Card>
  )
}

function ItemRow({
  item: it,
  goals,
  conflict,
  onEdit,
  onPriority,
  onTopic,
}: {
  item: MapItem
  goals: GoalCategory[]
  conflict?: string
  onEdit: () => void
  /** 행에서 바로 중요도 고르기 (2026-10) */
  onPriority: (p: TaskPriority) => void
  /** 업무형 목표면 주제를 바로 고르기 (2026-10) */
  onTopic: (topicId: number | null, topicCount: number) => void
}) {
  const [pickPriority, setPickPriority] = useState(false)
  const priority = priorityOf(it.priority)
  const goal = it.goalCategoryId ? goals.find((g) => g.goalCategoryId === it.goalCategoryId) : undefined
  const sub = goal?.generalCategories.find((c) => c.generalCategoryId === it.generalCategoryId)
  const ms = goal?.milestones?.find((m) => m.milestoneId === it.milestoneId)
  const dateLabel =
    it.dateType === 'DUE' && it.dueDate
      ? `${formatMonthDay(it.dueDate)}까지`
      : it.dateType === 'ON' && it.scheduledDate
        ? formatMonthDay(it.scheduledDate)
        : '날짜 없음'

  return (
    <li
      data-item={it.clientKey}
      className={cn('border-line rounded-2xl border px-4 py-3', conflict && 'border-danger bg-danger-soft/40')}
    >
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{it.title}</p>
          <p className="text-ink-3 mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
            <span className="flex items-center gap-1">
              <CalendarClock className="size-3.5" />
              {dateLabel}
            </span>
            {it.startTime && (
              <span className="flex items-center gap-1">
                <Clock className="size-3.5" />
                {it.startTime.slice(0, 5)}
                {it.endTime ? `–${it.endTime.slice(0, 5)}` : ''}
              </span>
            )}
            <span>예상 {formatDuration(it.estimatedMinutes ?? 30)}</span>
            <button
              type="button"
              onClick={() => setPickPriority((v) => !v)}
              aria-expanded={pickPriority}
              aria-label={`중요도 ${priority} ${PRIORITY_LABEL[priority]} 바꾸기`}
              className={cn(
                'hover:bg-subtle -mx-1 flex items-center gap-1 rounded-md px-1 py-0.5',
                priority === 'A' && 'text-text-brand font-semibold'
              )}
            >
              <span
                className={cn(
                  'grid size-3.5 place-items-center rounded-[4px] text-[9px] leading-none font-bold',
                  priority === 'A' ? 'bg-text-brand text-white' : 'bg-subtle text-ink-3'
                )}
                aria-hidden
              >
                {priority}
              </span>
              {PRIORITY_LABEL[priority]}
              <ChevronDown className={cn('size-3 transition-transform', pickPriority && 'rotate-180')} />
            </button>
            {it.kind === 'APPOINTMENT' && <Badge tone="warning">약속</Badge>}
            {sub && <span>› {sub.generalCategoryName}</span>}
            {ms && <span>🏁 {ms.title}</span>}
          </p>
          {isWorkGoal(goal) && goal && (
            <WorkTopicPicker goalId={goal.goalCategoryId} value={it.workTopicId ?? null} onChange={onTopic} />
          )}
          {pickPriority && (
            <PriorityChips
              className="mt-2"
              value={priority}
              autoFocus
              onChange={(p) => {
                setPickPriority(false)
                if (p !== priority) onPriority(p)
              }}
            />
          )}
          {it.reason && !conflict && (
            <p className="text-ink-3 mt-1.5 flex items-start gap-1 text-xs">
              <AIBadge size="sm" className="mt-px size-4 rounded-[4px] [&_svg]:size-2.5" />
              {it.reason}
            </p>
          )}
          {conflict && <p className="text-danger mt-1.5 text-xs font-semibold">{conflict}</p>}
        </div>
        <Button variant="secondary" size="sm" onClick={onEdit} aria-label={`${it.title} 변경`}>
          변경
        </Button>
      </div>
    </li>
  )
}

/** 업무형 목표의 주제 고르기 — 끝내지 않은 주제만, 비우면 "주제 없음" */
function WorkTopicPicker({
  goalId,
  value,
  onChange,
}: {
  goalId: number
  value: number | null
  onChange: (topicId: number | null, topicCount: number) => void
}) {
  const board = useWorkBoard(goalId)
  const topics = (board.data?.topics ?? []).filter(
    (t): t is WorkTopic & { topicId: number } => t.topicId !== null && (!t.completed || t.topicId === value)
  )
  if (board.isPending) return null
  if (!topics.length)
    return (
      <p className="text-ink-3 mt-2 flex items-center gap-1.5 text-xs">
        <Briefcase className="size-3.5 shrink-0" />
        세부 목표 없음 · 목표 상세의 로드맵에서 세부 목표를 만들면 여기서 고를 수 있어요
      </p>
    )
  return (
    <label className="text-ink-3 mt-2 flex items-center gap-1.5 text-xs">
      <Briefcase className="size-3.5 shrink-0" />
      <span className="shrink-0">세부 목표</span>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null, topics.length)}
        aria-label="세부 목표"
        className={cn(
          'border-border-subtle bg-surface focus:border-border-focus h-7 max-w-[220px] min-w-0 rounded-lg border px-2 text-xs outline-none',
          value ? 'text-ink' : 'text-ink-3'
        )}
      >
        <option value="">선택 안 함 (기타 할 일)</option>
        {topics.map((t) => (
          <option key={t.topicId} value={t.topicId}>
            {t.emoji ? `${t.emoji} ` : ''}
            {t.name}
          </option>
        ))}
      </select>
    </label>
  )
}
