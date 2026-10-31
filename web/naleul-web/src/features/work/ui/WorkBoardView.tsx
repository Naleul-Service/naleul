'use client'

import {
  Fragment,
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type RefObject,
} from 'react'
import { AlertTriangle, CircleDashed, Flag, ListTodo, Loader, MessageSquareText, PenLine } from 'lucide-react'
import { Check, ChevronDown, Pencil, Plus, SquarePen, Trash2, Upload, X } from '@/components/icons'
import Link from 'next/link'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { IconButton } from '@/components/ui/IconButton'
import { Modal } from '@/components/ui/Modal'
import { GoalFab, GoalFabs } from '@/features/goal/ui/GoalFab'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { goalVars } from '@/lib/goalTone'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import type { GoalCategory } from '@/features/goal/api'
import { inlineInput } from '@/features/goal/edit/inline'
import { Section } from '@/features/goal/ui/sections'
import { GoalNotes, routineCountOf } from '@/features/goal/ui/GoalHeader'
import { RoutinesSection } from '@/features/goal/ui/RoutinesSection'
import { RecordJournal } from '@/features/record/ui/RecordJournal'
import { RecordQuickLog } from '@/features/record/ui/RecordQuickLog'
import { todayKst, weekdayIndex, WEEKDAY_LABEL } from '@/features/timetable/time'
import {
  useCreateWorkRetro,
  useCreateWorkTask,
  useCreateWorkTasks,
  useCreateWorkTopic,
  useDeleteWorkRetro,
  useDeleteWorkTopic,
  useMoveWorkTask,
  useUpdateWorkTopic,
  useWorkBoard,
  type TopicStatus,
  type WorkBoard,
  type WorkColumn,
  type WorkRetro,
  type WorkTask,
  type WorkTopic,
} from '../api'
import { COLUMN_LABEL, dayLabel, daysAgo, dueLabel, goalDDay } from '../format'
import { WorkShareModal } from './WorkShareModal'
import { WorkTaskModal } from './WorkTaskModal'
import { ReflectionDetailModal } from '@/features/reflection/ui/ReflectionDetailModal'

/** 주제 필터: 전체 · 주제 id · 주제 없음 */
type TopicFilter = 'ALL' | 'NONE' | number

const matchTopic = (t: WorkTask, f: TopicFilter) =>
  f === 'ALL' ? true : f === 'NONE' ? t.topicId === null : t.topicId === f

// ─── 분석 (Tracking Plan 「업무형 목표」) ───────────────────────────

/** 같은 부품이 쓰인 화면 — 로드맵 · 보드 */
type Surface = 'roadmap' | 'board'
type MoveMethod = 'check' | 'drag' | 'next_button'
type TopicAction = 'create' | 'update' | 'delete' | 'complete' | 'reopen'
/** 보드 필터를 바꾼 곳 — 보드 위 주제 칩 · 로드맵 주제 이름 · 새 주제를 만든 직후 */
type FilterEntry = 'topic_bar' | 'roadmap' | 'topic_created'

/** 실패한 요청의 HTTP 상태코드 (네트워크 끊김 0) */
const failStatus = (e: unknown) => errorStatusOf(e) ?? 0

const filterKind = (f: TopicFilter) => (f === 'ALL' ? 'ALL' : f === 'NONE' ? 'NONE' : 'TOPIC')

/** 주제 요청이 끝나면 work_roadmap.topic_result — 성공했는지 돌려줘요 */
const trackTopicResult = (goalId: number, topicId: number | null, action: TopicAction, req: Promise<unknown>) =>
  req.then(
    () => {
      track('work_roadmap.topic_result', {
        goal_category_id: goalId,
        topic_id: topicId,
        topic_action: action,
        result: 'success',
      })
      return true
    },
    (e: unknown) => {
      track('work_roadmap.topic_result', {
        goal_category_id: goalId,
        topic_id: topicId,
        topic_action: action,
        result: 'fail',
        error_status: failStatus(e),
      })
      return false
    }
  )

// ─── 1. 다음 마감 · 진행 요약 ────────────────────────────────────


/** 마감까지 속도 — 하루 1개 이상이면 "하루 2개씩", 적으면 "3일에 1개씩" */
function paceLabel(perDay: number) {
  if (perDay >= 1) return `하루 ${String(Math.round(perDay * 10) / 10).replace(/\.0$/, '')}개씩`
  return `${Math.max(Math.round(1 / perDay), 2)}일에 1개씩`
}

/** 주제 마감 표시 — "D-3" · "오늘 마감" · "2일 지남" */
function topicDueLabel(dDay: number | null) {
  if (dDay == null) return null
  if (dDay === 0) return '오늘 마감'
  return dDay > 0 ? `D-${dDay}` : `${-dDay}일 지남`
}

/** "~10/16(금)" */
const shortDate = (ymd: string) =>
  `${Number(ymd.slice(5, 7))}/${Number(ymd.slice(8, 10))}(${WEEKDAY_LABEL[weekdayIndex(ymd)]})`

function SummaryHero({
  board,
  colorCode,
  onShare,
  header,
}: {
  /** 목표 머리말 (이름 · 태그 · 시작일) — 요약 카드 맨 위 (명세 13.8) */
  header?: React.ReactNode
  board: WorkBoard
  /** 목표 색 — 카드 바탕을 목표 톤으로 */
  colorCode?: string | null
  onShare: () => void
}) {
  const s = board.summary
  const next = s.nextTopic
  const overdue = next?.dDay != null && next.dDay < 0
  const meta = [
    `끝낸 세부 목표 ${s.topicsDone}/${s.topicsTotal}`,
    next?.dueDate && `${shortDate(next.dueDate)}까지`,
    next?.remainingPerDay != null && next.remainingPerDay > 0 && !overdue && `마감까지 ${paceLabel(next.remainingPerDay)}`,
  ].filter(Boolean) as string[]

  return (
    // 업무형 요약 카드 (명세 13.8) — 목표 톤 바탕 · 모서리 16 · 여백 20 · 그림자 없음
    <section style={goalVars(colorCode)} className="text-text-primary rounded-card bg-(--goal-bg) p-5">
      {/* 1줄: 이름 · 공유 아이콘 / 2줄: 태그 · 시작일 */}
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">{header}</div>
        <IconButton label="공유" onClick={onShare} className="hover:bg-bg-surface/70 -mt-0.5 -mr-1">
          <Upload />
        </IconButton>
      </div>

      <div className="border-border-soft my-4 border-t" />

      {/* 3칸: 다음 마감 · 진행 · 남은 Task */}
      <dl className="divide-border-soft grid grid-cols-3 divide-x">
        <div className="min-w-0 pr-3">
          <dt className="text-caption-md text-text-secondary">다음 마감</dt>
          <dd className="text-label-md mt-1 truncate">
            {next ? `${next.emoji ? `${next.emoji} ` : ''}${next.name}` : s.topicsTotal ? '마감 없음' : '세부 목표 없음'}
          </dd>
          <dd className={cn('text-h2 mt-0.5 tabular-nums', overdue ? 'text-status-error-fg' : 'text-(--goal-fg)')}>
            {next ? goalDDay(next.dDay) : '–'}
          </dd>
        </div>
        <div className="min-w-0 px-3">
          <dt className="text-caption-md text-text-secondary">진행</dt>
          <dd className="text-h2 mt-1 tabular-nums">
            {s.done}/{s.total}
          </dd>
        </div>
        <div className="min-w-0 pl-3">
          <dt className="text-caption-md text-text-secondary">남은 Task</dt>
          <dd className="text-h2 mt-1 tabular-nums">{s.remaining}개</dd>
        </div>
      </dl>

      {/* Task 완료 막대 + 한 줄 설명 */}
      {s.progressPercent != null && (
        <div className="mt-4">
          <div className="text-label-md flex items-center justify-between">
            <span>Task 완료</span>
            <span className="tabular-nums">{s.progressPercent}%</span>
          </div>
          <ProgressBar
            value={Math.min(Math.max(s.progressPercent, 0), 100) / 100}
            className="bg-bg-surface/70 mt-1.5 h-1.5 rounded-[3px]"
            fillClassName="bg-(--goal-accent) rounded-[3px]"
          />
        </div>
      )}
      <p className="text-caption-md text-text-secondary mt-2">{meta.join(' · ')}</p>
    </section>
  )
}

// ─── 1-2. 로드맵 (주제를 마감 순서대로 세로 선 위에) ─────────────────

const NODE_CLASS: Record<TopicStatus, string> = {
  DONE: 'border-brand bg-brand text-white',
  NEXT: 'border-brand bg-surface text-transparent ring-4 ring-brand/15',
  OVERDUE: 'border-danger bg-danger-soft text-danger',
  UPCOMING: 'border-border-subtle bg-surface text-transparent',
  NO_DUE: 'border-border-subtle border-dashed bg-surface text-transparent',
}

function TopicEditor({
  goalId,
  topic,
  onDone,
  onCancel,
}: {
  goalId: number
  topic: WorkTopic
  onDone: () => void
  onCancel: (method: 'esc' | 'cancel_button') => void
}) {
  const update = useUpdateWorkTopic(goalId)
  const remove = useDeleteWorkTopic(goalId)
  const [name, setName] = useState(topic.name)
  const [due, setDue] = useState(topic.dueDate ?? '')
  // Enter 로 저장했는지 (keydown 에서 켜고 submit 에서 읽어요)
  const enterRef = useRef(false)
  const id = topic.topicId!
  const save = () => {
    const method = enterRef.current ? 'enter' : 'button'
    enterRef.current = false
    const v = name.trim()
    if (!v) return
    const changed = [v !== topic.name && 'name', (due || null) !== topic.dueDate && 'due_date'].filter(Boolean)
    track('work_roadmap.topic_edit_save', {
      goal_category_id: goalId,
      topic_id: id,
      has_due_date: !!due,
      changed_fields: changed.join(','),
      submit_method: method,
    })
    trackTopicResult(
      goalId,
      id,
      'update',
      update.mutateAsync({
        topicId: id,
        name: v,
        ...(due ? { dueDate: due } : topic.dueDate ? { clearDueDate: true } : {}),
      })
    ).then((ok) => ok && onDone())
  }
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save()
      }}
      onPointerDown={() => (enterRef.current = false)}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel('esc')
        else if (e.key === 'Enter' && !e.nativeEvent.isComposing && e.target instanceof HTMLInputElement)
          enterRef.current = true
      }}
      className="border-line bg-subtle/60 mt-2 space-y-2 rounded-xl border p-3"
    >
      <input
        autoFocus
        value={name}
        maxLength={30}
        onChange={(e) => setName(e.target.value)}
        aria-label="세부 목표 이름"
        className={`${inlineInput} h-9`}
      />
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="date"
          value={due}
          onChange={(e) => setDue(e.target.value)}
          aria-label="세부 목표 마감"
          className="border-border-subtle bg-surface h-8 rounded-lg border px-2 text-[13px]"
        />
        {due && (
          <button
            type="button"
            onClick={() => setDue('')}
            className="text-ink-3 hover:text-ink text-[12px] font-semibold"
          >
            마감 없이
          </button>
        )}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => {
            track('work_roadmap.topic_delete_click', {
              goal_category_id: goalId,
              topic_id: id,
              topic_task_count: topic.remaining,
              has_due_date: !!topic.dueDate,
            })
            trackTopicResult(goalId, id, 'delete', remove.mutateAsync(id)).then((ok) => ok && onDone())
          }}
          className="text-ink-3 hover:text-danger flex items-center gap-0.5 text-[12px] font-semibold"
        >
          <Trash2 className="size-3" />
          세부 목표 지우기
        </button>
        <button
          type="button"
          onClick={() => onCancel('cancel_button')}
          className="text-ink-3 hover:text-ink ml-auto text-[12px] font-semibold"
        >
          취소
        </button>
        <Button size="sm" type="submit" loading={update.isPending} disabled={!name.trim()}>
          저장
        </Button>
      </div>
    </form>
  )
}

/** 로드맵 주제 아래 할 일 한 줄 — 체크하면 완료, 이름을 누르면 자세히 */
function RoadmapTask({
  task,
  today,
  onOpen,
  onToggle,
}: {
  task: WorkTask
  today: string
  onOpen: () => void
  onToggle: () => void
}) {
  const done = task.status === 'DONE'
  return (
    <li className="flex items-start gap-2.5 py-1">
      <span className="pt-px">
        <CheckButton task={task} onToggle={onToggle} small />
      </span>
      <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
        <span className={cn('block text-[14px] leading-5', done ? 'text-ink-3 line-through' : 'font-medium')}>
          {task.emoji ? `${task.emoji} ` : ''}
          {task.taskName}
        </span>
        {!done && <TaskMeta task={task} today={today} showTopic={false} />}
      </button>
    </li>
  )
}

/** 주제에 딸린 할 일 — 남은 일은 펼치고, 끝낸 일은 접어 둬요 */
function TopicTasks({
  goalId,
  topicId,
  open,
  done,
  today,
  collapsed,
  onOpen,
  onToggle,
  onAdd,
}: {
  goalId: number
  /** null = 주제 없음 */
  topicId: number | null
  open: WorkTask[]
  done: WorkTask[]
  today: string
  /** 끝난 주제는 할 일을 접어서 시작 */
  collapsed: boolean
  onOpen: (t: WorkTask) => void
  onToggle: (t: WorkTask) => void
  onAdd: () => void
}) {
  const [showOpen, setShowOpen] = useState(!collapsed)
  const [showDone, setShowDone] = useState(false)
  return (
    <div className="mt-2">
      {open.length > 0 &&
        (showOpen ? (
          <ul className="border-line bg-subtle/40 rounded-xl border px-3 py-1.5">
            {open.map((t) => (
              <RoadmapTask
                key={t.taskId}
                task={t}
                today={today}
                onOpen={() => onOpen(t)}
                onToggle={() => onToggle(t)}
              />
            ))}
          </ul>
        ) : (
          <button
            type="button"
            onClick={() => {
              track('work_roadmap.open_tasks_expand', {
                goal_category_id: goalId,
                topic_id: topicId,
                item_count: open.length,
              })
              setShowOpen(true)
            }}
            className="text-ink-3 hover:text-ink text-[12px] font-semibold"
          >
            남은 할 일 {open.length}개 보기
          </button>
        ))}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
        <button
          type="button"
          onClick={onAdd}
          className="text-ink-3 hover:text-text-brand flex items-center gap-0.5 text-[12px] font-semibold"
        >
          <Plus className="size-3.5" />할 일
        </button>
        {done.length > 0 && (
          <button
            type="button"
            onClick={() => {
              track('work_roadmap.done_tasks_toggle', {
                goal_category_id: goalId,
                topic_id: topicId,
                expanded: !showDone,
                item_count: done.length,
              })
              setShowDone(!showDone)
            }}
            aria-expanded={showDone}
            className="text-success flex items-center gap-0.5 text-[12px] font-semibold"
          >
            끝낸 일 {done.length}
            <ChevronDown className={cn('size-3.5 transition-transform', showDone && 'rotate-180')} />
          </button>
        )}
      </div>
      {showDone && done.length > 0 && (
        <ul className="mt-1">
          {done.map((t) => (
            <RoadmapTask key={t.taskId} task={t} today={today} onOpen={() => onOpen(t)} onToggle={() => onToggle(t)} />
          ))}
        </ul>
      )}
    </div>
  )
}

function RoadmapItem({
  goalId,
  topic: t,
  selected,
  onSelect,
  children,
}: {
  goalId: number
  topic: WorkTopic
  selected: boolean
  onSelect: () => void
  /** 주제 아래 할 일 목록 (로드맵 점과 점 사이) */
  children?: React.ReactNode
}) {
  const update = useUpdateWorkTopic(goalId)
  const [editing, setEditing] = useState(false)
  const done = t.status === 'DONE'
  // Task 를 모두 끝내서 "끝"인 주제는 되돌릴 게 없어요 (직접 끝냈다고 표시한 것만 다시 열기)
  const autoDone = done && !t.completed
  const due = done ? null : topicDueLabel(t.dDay)

  const toggleDone = () => {
    const checked = !t.completed
    track('work_roadmap.topic_complete_toggle', {
      goal_category_id: goalId,
      topic_id: t.topicId,
      checked,
      topic_task_count: t.remaining,
      d_day: t.dDay,
    })
    trackTopicResult(
      goalId,
      t.topicId,
      checked ? 'complete' : 'reopen',
      update.mutateAsync({ topicId: t.topicId!, completed: checked })
    )
  }
  const toggleEdit = () => {
    if (editing)
      track('work_roadmap.topic_edit_cancel', { goal_category_id: goalId, topic_id: t.topicId, close_method: 'toggle' })
    else track('work_roadmap.topic_edit_open', { goal_category_id: goalId, topic_id: t.topicId })
    setEditing(!editing)
  }

  return (
    <li className="relative flex gap-3 pb-5">
      {/* 세로 선 */}
      <span aria-hidden className="bg-line absolute top-7 bottom-0 left-[13px] w-0.5" />
      {/* 자동으로 끝난 주제의 점은 비활성 — 감싼 요소에서 누른 것만 남겨요 */}
      <span
        className="relative z-[1] flex shrink-0 self-start"
        title={autoDone ? 'Task를 모두 끝내서 끝난 세부 목표예요' : t.completed ? '다시 열기' : '끝냈어요로 표시'}
        onPointerDown={
          autoDone
            ? () => track('work_roadmap.topic_locked_click', { goal_category_id: goalId, topic_id: t.topicId })
            : undefined
        }
      >
        <button
          type="button"
          disabled={autoDone || update.isPending}
          onClick={toggleDone}
          aria-pressed={done}
          aria-label={
            autoDone ? `${t.name}: Task를 모두 끝냈어요` : `${t.name} ${t.completed ? '다시 열기' : '끝냈어요'}`
          }
          className={cn(
            'relative z-[1] grid size-7 shrink-0 place-items-center rounded-full border-[1.5px] transition-colors',
            NODE_CLASS[t.status],
            !done && 'hover:border-brand hover:text-text-brand/60',
            autoDone && 'pointer-events-none'
          )}
        >
          {t.status === 'OVERDUE' ? (
            <AlertTriangle className="size-3.5" strokeWidth={2.5} />
          ) : (
            <Check className="size-3.5" />
          )}
        </button>
      </span>

      <div className="min-w-0 flex-1 pt-0.5">
        <div className="flex items-start gap-2">
          <button
            type="button"
            onClick={onSelect}
            aria-pressed={selected}
            className={cn(
              'min-w-0 flex-1 text-left text-[15px] leading-snug font-bold',
              done && 'text-ink-3 line-through decoration-1',
              selected && 'text-text-brand'
            )}
          >
            {t.emoji ? `${t.emoji} ` : ''}
            {t.name}
          </button>
          <button
            type="button"
            onClick={toggleEdit}
            aria-label={`${t.name} 고치기`}
            className="text-ink-4 hover:text-ink shrink-0 p-0.5"
          >
            <Pencil className="size-3.5" />
          </button>
        </div>
        <p className="text-ink-3 mt-0.5 flex flex-wrap items-center gap-x-2 text-[12px]">
          {t.dueDate ? <span>~{shortDate(t.dueDate)}</span> : <span>마감 없음</span>}
          {due && (
            <span
              className={cn(
                'font-semibold',
                t.status === 'OVERDUE'
                  ? 'text-danger'
                  : t.status === 'NEXT'
                    ? 'text-text-brand'
                    : t.dDay! <= 3 && 'text-warning-ink'
              )}
            >
              {due}
            </span>
          )}
          {done && <span className="text-success font-semibold">끝</span>}
          {!done && t.total > 0 && <span>남은 {t.remaining}개</span>}
          {t.status === 'NEXT' && t.remainingPerDay != null && t.remainingPerDay > 0 && (
            <span>{paceLabel(t.remainingPerDay)}</span>
          )}
        </p>
        {t.total > 0 && (
          <div className="mt-1.5 flex items-center gap-2">
            <div className="bg-progress-track h-1.5 flex-1 overflow-hidden rounded-full">
              <div
                className={cn('h-full rounded-full', done ? 'bg-success' : 'bg-progress-fill')}
                style={{ width: `${t.progressPercent ?? 0}%` }}
              />
            </div>
            <span className="text-ink-3 text-[11px] tabular-nums">
              {t.done}/{t.total}
            </span>
          </div>
        )}
        {editing && (
          <TopicEditor
            goalId={goalId}
            topic={t}
            onDone={() => setEditing(false)}
            onCancel={(method) => {
              track('work_roadmap.topic_edit_cancel', {
                goal_category_id: goalId,
                topic_id: t.topicId,
                close_method: method,
              })
              setEditing(false)
            }}
          />
        )}
        {children}
      </div>
    </li>
  )
}

/** "주제 없음" 할 일 — 로드맵 맨 끝(🏁 앞)에 점선 점으로 */
function NoTopicItem({ children, count }: { children: React.ReactNode; count: number }) {
  return (
    <li className="relative flex gap-3 pb-5">
      <span aria-hidden className="bg-line absolute top-7 bottom-0 left-[13px] w-0.5" />
      <span className="border-border-subtle bg-surface relative z-[1] size-7 shrink-0 rounded-full border-2 border-dotted" />
      <div className="min-w-0 flex-1 pt-0.5">
        <p className="text-ink-2 text-[15px] leading-snug font-bold">세부 목표 없음</p>
        <p className="text-ink-3 mt-0.5 text-[12px]">아직 세부 목표를 정하지 않은 할 일 {count}개</p>
        {children}
      </div>
    </li>
  )
}

function TodayMarker() {
  return (
    <li className="relative flex items-center gap-3 pb-5" aria-label="오늘">
      <span aria-hidden className="bg-line absolute top-3 bottom-0 left-[13px] w-0.5" />
      <span className="relative z-[1] grid w-7 shrink-0 place-items-center">
        <span className="bg-ink size-2.5 rounded-full" />
      </span>
      <span className="text-ink text-[12px] font-bold">오늘</span>
      <span className="bg-line h-px flex-1" />
    </li>
  )
}

function AddTopic({
  goalId,
  topicCount,
  onCreated,
}: {
  goalId: number
  topicCount: number
  onCreated: (id: number) => void
}) {
  const create = useCreateWorkTopic(goalId)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [due, setDue] = useState('')
  // Enter 로 만들었는지 (keydown 에서 켜고 submit 에서 읽어요)
  const enterRef = useRef(false)
  if (!open) {
    return (
      <button
        type="button"
        onClick={() => {
          track('work_roadmap.add_topic_click', { goal_category_id: goalId, topic_count: topicCount })
          setOpen(true)
        }}
        className="text-ink-3 hover:text-ink border-border-subtle mt-1 flex h-9 w-full items-center justify-center gap-1 rounded-xl border border-dashed text-[13px] font-semibold"
      >
        <Plus className="size-3.5" />
        세부 목표 추가
      </button>
    )
  }
  const cancel = (method: 'x_button' | 'esc') => {
    track('work_roadmap.add_topic_cancel', { goal_category_id: goalId, close_method: method })
    setOpen(false)
  }
  const submit = () => {
    const method = enterRef.current ? 'enter' : 'button'
    enterRef.current = false
    const v = name.trim()
    if (!v) return
    track('work_roadmap.add_topic_submit', {
      goal_category_id: goalId,
      has_due_date: !!due,
      topic_count: topicCount,
      submit_method: method,
    })
    create.mutateAsync({ name: v, dueDate: due || null }).then(
      (t) => {
        track('work_roadmap.topic_result', {
          goal_category_id: goalId,
          topic_id: t.topicId,
          topic_action: 'create',
          result: 'success',
        })
        setName('')
        setDue('')
        setOpen(false)
        onCreated(t.topicId)
      },
      (e: unknown) =>
        track('work_roadmap.topic_result', {
          goal_category_id: goalId,
          topic_action: 'create',
          result: 'fail',
          error_status: failStatus(e),
        })
    )
  }
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      onPointerDown={() => (enterRef.current = false)}
      onKeyDown={(e) => {
        if (e.key === 'Escape') cancel('esc')
        else if (e.key === 'Enter' && !e.nativeEvent.isComposing && e.target instanceof HTMLInputElement)
          enterRef.current = true
      }}
      className="border-border-subtle mt-1 space-y-2 rounded-xl border p-3"
    >
      <input
        autoFocus
        value={name}
        maxLength={30}
        onChange={(e) => setName(e.target.value)}
        placeholder="예: 이력서 제출"
        aria-label="새 세부 목표 이름"
        className={`${inlineInput} h-9`}
      />
      <div className="flex flex-wrap items-center gap-2">
        <label className="text-ink-3 flex items-center gap-1 text-[12px]">
          마감
          <input
            type="date"
            value={due}
            onChange={(e) => setDue(e.target.value)}
            className="border-border-subtle bg-surface text-ink h-8 rounded-lg border px-2 text-[13px]"
          />
        </label>
        <button
          type="button"
          onClick={() => cancel('x_button')}
          aria-label="취소"
          className="text-ink-3 hover:text-ink ml-auto p-1"
        >
          <X className="size-4" />
        </button>
        <Button size="sm" type="submit" loading={create.isPending} disabled={!name.trim()}>
          만들기
        </Button>
      </div>
    </form>
  )
}

/**
 * 로드맵 — 주제를 마감 순서대로 세로 선 위에 놓아요. 끝난 주제는 채운 점, 다음 마감은 강조,
 * 마감 지난 주제는 빨간 점, 마감 없는 주제는 점선. 점을 누르면 "끝냈어요", 이름을 누르면 보드가 그 주제만 보여줘요.
 */
function Roadmap({
  goalId,
  board,
  filter,
  onFilter,
  onOpen,
  onToggle,
  onAdd,
}: {
  goalId: number
  board: WorkBoard
  filter: TopicFilter
  onFilter: (f: TopicFilter, entry: FilterEntry) => void
  onOpen: (t: WorkTask) => void
  onToggle: (t: WorkTask) => void
  /** 이 주제(null = 주제 없음)에 할 일 적기 — 오른쪽 "적기" 패널로 */
  onAdd: (topicId: number | null) => void
}) {
  const { timeline, today, summary: s } = board
  // 주제별 할 일 — 진행 중을 위로, 그다음 마감 · 하는 날 순 (보드 칸 순서 그대로)
  const byTopic = useMemo(() => {
    const open = new Map<number | null, WorkTask[]>()
    const done = new Map<number | null, WorkTask[]>()
    const push = (m: Map<number | null, WorkTask[]>, t: WorkTask) => m.set(t.topicId, [...(m.get(t.topicId) ?? []), t])
    board.board.inProgress.forEach((t) => push(open, t))
    board.board.notStarted.forEach((t) => push(open, t))
    board.board.done.forEach((t) => push(done, t))
    return { open, done }
  }, [board])
  const noTopicOpen = byTopic.open.get(null) ?? []
  // "오늘" 표시는 마감이 오늘 이후인 첫 주제 앞 (마감 없는 주제는 맨 뒤라 그 앞)
  const todayAt = (() => {
    const i = timeline.findIndex((t) => t.dueDate === null || t.dueDate >= today)
    return i === -1 ? timeline.length : i
  })()

  return (
    <Section title="로드맵" aside={s.topicsTotal ? `세부 목표 ${s.topicsDone} / ${s.topicsTotal} 끝` : undefined}>
      {timeline.length === 0 ? (
        <p className="text-ink-3 mb-3 text-sm">
          포트폴리오 · 이력서 제출처럼 큰 단계를 세부 목표로 만들고, 세부 목표마다 마감을 정해 보세요.
        </p>
      ) : (
        <ol>
          {timeline.map((t, i) => (
            <Fragment key={t.topicId}>
              {i === todayAt && <TodayMarker />}
              <RoadmapItem
                goalId={goalId}
                topic={t}
                selected={filter === t.topicId}
                onSelect={() => onFilter(filter === t.topicId ? 'ALL' : t.topicId!, 'roadmap')}
              >
                <TopicTasks
                  goalId={goalId}
                  topicId={t.topicId}
                  open={byTopic.open.get(t.topicId) ?? []}
                  done={byTopic.done.get(t.topicId) ?? []}
                  today={today}
                  collapsed={t.status === 'DONE'}
                  onOpen={onOpen}
                  onToggle={onToggle}
                  onAdd={() => onAdd(t.topicId)}
                />
              </RoadmapItem>
            </Fragment>
          ))}
          {todayAt === timeline.length && <TodayMarker />}
          {noTopicOpen.length > 0 && (
            <NoTopicItem count={noTopicOpen.length}>
              <TopicTasks
                goalId={goalId}
                topicId={null}
                open={noTopicOpen}
                done={[]}
                today={today}
                collapsed={false}
                onOpen={onOpen}
                onToggle={onToggle}
                onAdd={() => onAdd(null)}
              />
            </NoTopicItem>
          )}
          <li className="flex items-center gap-3">
            <span className="bg-action-primary-bg text-action-primary-fg grid size-7 shrink-0 place-items-center rounded-full">
              <Flag className="size-3.5" />
            </span>
            <span className="text-[13px] font-bold">
              {s.topicsDone === s.topicsTotal ? '모두 끝냈어요' : `${s.topicsTotal - s.topicsDone}개 남았어요`}
            </span>
          </li>
        </ol>
      )}
      <div className="mt-4">
        <AddTopic goalId={goalId} topicCount={s.topicsTotal} onCreated={(id) => onFilter(id, 'topic_created')} />
      </div>
      {timeline.length > 0 && (
        <p className="text-ink-4 mt-2 text-[11px]">
          세부 목표 점을 누르면 &lsquo;끝냈어요&rsquo;로 표시돼요 · 세부 목표 이름을 누르면 아래 보드에서 그 세부 목표만 봐요
        </p>
      )}
    </Section>
  )
}

// ─── 2. Task 한 줄 (오늘 · 마감 임박 · 할 일 목록) ───────────────────

function CheckButton({ task, onToggle, small = false }: { task: WorkTask; onToggle: () => void; small?: boolean }) {
  const done = task.status === 'DONE'
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={done}
      aria-label={`${task.taskName} ${done ? '완료 취소' : '완료'}`}
      className={cn(
        'grid shrink-0 place-items-center rounded-full border-[1.5px] transition-colors',
        small ? 'size-5' : 'size-6',
        done ? 'border-brand bg-brand text-white' : 'border-border-control hover:border-brand text-transparent'
      )}
    >
      <Check className={small ? 'size-3' : 'size-3.5'} />
    </button>
  )
}

function TaskMeta({ task, today, showTopic = true }: { task: WorkTask; today: string; showTopic?: boolean }) {
  const due = dueLabel(task)
  return (
    <span className="text-ink-3 mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px]">
      {showTopic && task.topicName && <span className="bg-subtle text-ink-2 rounded px-1.5">{task.topicName}</span>}
      {task.routine && <span className="text-text-brand font-semibold">반복</span>}
      {task.status === 'IN_PROGRESS' && <span className="text-text-brand font-semibold">진행 중</span>}
      {task.date && task.date !== today && <span>{dayLabel(task.date, today)}</span>}
      {task.startTime && <span>{task.startTime}</span>}
      {due && (
        <span
          className={cn('font-semibold', task.overdue ? 'text-danger' : task.dueInDays! <= 2 ? 'text-warning-ink' : '')}
        >
          {due}
        </span>
      )}
    </span>
  )
}

// ─── 3. 적기 패널 (할 일 · 한 일 · 회고) ─────────────────────────────

type WhenOption = 'BACKLOG' | 'TODAY' | 'DATE'

/** 할 일 적기 — 브레인덤프처럼 여러 줄을 붙여 넣으면 한 번에 추가 */
function TaskCompose({
  goalId,
  topics,
  topicId,
  onTopic,
  inputRef,
}: {
  goalId: number
  topics: WorkTopic[]
  topicId: number | null
  onTopic: (id: number | null) => void
  inputRef: RefObject<HTMLTextAreaElement | null>
}) {
  const one = useCreateWorkTask(goalId)
  const many = useCreateWorkTasks(goalId)
  const [text, setText] = useState('')
  const [when, setWhen] = useState<WhenOption>('BACKLOG')
  const [date, setDate] = useState('')
  const [dueDate, setDueDate] = useState('')
  const lines = text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  const saving = one.isPending || many.isPending
  const realTopics = topics.filter((t) => t.topicId !== null)

  const day = when === 'TODAY' ? todayKst() : when === 'DATE' ? date || null : null
  // '날짜'를 골랐는데 날짜가 비어 있으면 버튼이 비활성 (안내 문구 없음)
  const blocked = when === 'DATE' && !date
  const submit = (method: 'button' | 'enter') => {
    if (!lines.length || saving) return
    if (blocked) {
      track('work_compose.task_blocked', {
        goal_category_id: goalId,
        validation_error: 'date_required',
        line_count: lines.length,
      })
      return
    }
    const lineCount = lines.length
    track('work_compose.task_submit', {
      goal_category_id: goalId,
      line_count: lineCount,
      has_topic: topicId !== null,
      when_option: when,
      has_due_date: !!dueDate,
      is_truncated: lines.some((l) => l.length > 100),
      due_before_date: !!(dueDate && day && dueDate < day),
      submit_method: method,
    })
    const common = { topicId, date: day, dueDate: dueDate || null }
    const req =
      lineCount === 1
        ? one.mutateAsync({ taskName: lines[0].slice(0, 100), ...common }).then(() => 1)
        : many.mutateAsync({ text, ...common }).then((r) => r.length)
    req.then(
      (created) => {
        setText('')
        track('work_compose.task_result', {
          goal_category_id: goalId,
          line_count: lineCount,
          created_count: created,
          result: 'success',
        })
      },
      (e: unknown) =>
        track('work_compose.task_result', {
          goal_category_id: goalId,
          line_count: lineCount,
          created_count: 0,
          result: 'fail',
          error_status: failStatus(e),
        })
    )
  }
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit('enter')
    }
  }

  return (
    <div>
      <select
        value={topicId ?? ''}
        onChange={(e) => {
          const id = e.target.value ? Number(e.target.value) : null
          track('work_compose.topic_select', { goal_category_id: goalId, topic_id: id, has_topic: id !== null })
          onTopic(id)
        }}
        aria-label="세부 목표"
        className="border-border-subtle bg-surface h-9 w-full rounded-xl border px-2.5 text-[14px] font-semibold"
      >
        <option value="">세부 목표 없음</option>
        {realTopics.map((t) => (
          <option key={t.topicId} value={t.topicId!}>
            {t.emoji ? `${t.emoji} ` : ''}
            {t.name}
            {t.dueDate ? ` (~${shortDate(t.dueDate)})` : ''}
          </option>
        ))}
      </select>
      <textarea
        ref={inputRef}
        id="work-quick-add"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKey}
        rows={Math.min(Math.max(lines.length + 1, 4), 10)}
        placeholder={'해야 할 일을 한 줄에 하나씩 적어 주세요\n여러 줄을 붙여 넣으면 한 번에 추가돼요'}
        aria-label="해야 할 일"
        className={`${inlineInput} mt-2 h-auto resize-none py-2.5 leading-relaxed`}
      />
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <div className={segmentedClass()} role="radiogroup" aria-label="언제 할지">
          {(
            [
              ['BACKLOG', '나중에'],
              ['TODAY', '오늘'],
              ['DATE', '날짜'],
            ] as const
          ).map(([v, label]) => (
            <button
              key={v}
              type="button"
              role="radio"
              aria-checked={when === v}
              onClick={() => {
                if (v !== when) track('work_compose.when_select', { goal_category_id: goalId, when_option: v })
                setWhen(v)
              }}
              className={segmentClass(when === v, 'text-label-sm h-7 px-2.5')}
            >
              {label}
            </button>
          ))}
        </div>
        {when === 'DATE' && (
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            aria-label="하는 날"
            className="border-border-subtle bg-surface h-8 rounded-lg border px-2 text-[13px]"
          />
        )}
        <label className="text-ink-3 flex items-center gap-1 text-[12px]">
          마감
          <input
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            className="border-border-subtle bg-surface text-ink h-8 rounded-lg border px-2 text-[13px]"
          />
        </label>
      </div>
      {/* 비활성 버튼을 누른 것도 남기려고 감싼 요소에서 받아요 (비활성 버튼은 pointer-events 가 꺼져 있어요) */}
      <div className="mt-3" onPointerDown={() => blocked && submit('button')}>
        <Button fullWidth loading={saving} disabled={!lines.length || blocked} onClick={() => submit('button')}>
          {lines.length > 1 ? `${lines.length}개 추가` : '할 일 추가'}
        </Button>
      </div>
      <p className="text-ink-4 mt-1.5 text-[11px] leading-relaxed">
        Enter 추가 · Shift+Enter 줄바꿈 · &lsquo;나중에&rsquo;는 캘린더에 안 올리고 로드맵 · 보드에만 담아요
      </p>
    </div>
  )
}

/** 회고 적기 + 최근 회고 */
function RetroCompose({ goalId, board }: { goalId: number; board: WorkBoard }) {
  const create = useCreateWorkRetro(goalId)
  const remove = useDeleteWorkRetro(goalId)
  const [openRetro, setOpenRetro] = useState<WorkRetro | null>(null)
  const [text, setText] = useState('')
  const retroResult = (action: 'create' | 'delete', req: Promise<unknown>) =>
    req.then(
      () => {
        track('work_compose.retro_result', { goal_category_id: goalId, retro_action: action, result: 'success' })
        return true
      },
      (e: unknown) => {
        track('work_compose.retro_result', {
          goal_category_id: goalId,
          retro_action: action,
          result: 'fail',
          error_status: failStatus(e),
        })
        return false
      }
    )
  const submit = (method: 'button' | 'enter') => {
    const content = text.trim()
    if (!content || create.isPending) return
    track('work_compose.retro_submit', {
      goal_category_id: goalId,
      content_length: content.length,
      submit_method: method,
    })
    retroResult('create', create.mutateAsync(content)).then((ok) => ok && setText(''))
  }
  return (
    <div>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            submit('enter')
          }
        }}
        rows={4}
        aria-label="회고"
        placeholder="오늘 일하며 배운 점 · 막힌 점 · 다음에 바꿀 점"
        className={`${inlineInput} h-auto resize-none py-2.5 leading-relaxed`}
      />
      <Button
        className="mt-3"
        fullWidth
        loading={create.isPending}
        disabled={!text.trim()}
        onClick={() => submit('button')}
      >
        회고 남기기
      </Button>
      {board.retrospectives.length > 0 && (
        <>
          <div className="mt-4 mb-1 flex items-center">
            <p className="text-ink-3 text-[12px] font-semibold">최근 회고</p>
            <Link
              href={`/goal/${goalId}?tab=reflections`}
              onClick={() =>
                track('work_compose.retro_all_click', {
                  goal_category_id: goalId,
                  item_count: board.retrospectives.length,
                })
              }
              className="text-text-brand ml-auto text-[12px] font-semibold hover:underline"
            >
              모두 보기
            </Link>
          </div>
          <ul className="divide-line divide-y">
            {board.retrospectives.map((r) => (
              <li
                key={r.retrospectiveId}
                role="button"
                tabIndex={0}
                aria-haspopup="dialog"
                onClick={(ev) => {
                  if (ev.target instanceof Element && ev.target.closest('button')) return
                  track('reflection_timeline.entry_click', {
                    surface: 'work_board',
                    entry_kind: 'RETRO',
                    days_ago: daysAgo(r.reviewDate, board.today),
                    goal_mode: 'RECORD',
                  })
                  setOpenRetro(r)
                }}
                onKeyDown={(ev) => {
                  if (ev.target !== ev.currentTarget) return
                  if (ev.key === 'Enter' || ev.key === ' ') {
                    ev.preventDefault()
                    setOpenRetro(r)
                  }
                }}
                className="group hover:bg-bg-sunken/60 -mx-2 flex cursor-pointer gap-2 rounded-lg px-2 py-2"
              >
                <span className="text-ink-3 w-12 shrink-0 text-[12px] leading-5">
                  {dayLabel(r.reviewDate, board.today)}
                </span>
                <p className="min-w-0 flex-1 line-clamp-3 text-[13px] leading-5 whitespace-pre-wrap">{r.content}</p>
                <button
                  type="button"
                  onClick={() => {
                    track('work_compose.retro_delete_click', {
                      goal_category_id: goalId,
                      days_ago: daysAgo(r.reviewDate, board.today),
                    })
                    retroResult('delete', remove.mutateAsync(r.retrospectiveId))
                  }}
                  aria-label="회고 지우기"
                  className="text-ink-4 hover:text-danger shrink-0 self-start p-0.5 [@media(hover:hover)]:opacity-50 [@media(hover:hover)]:group-hover:opacity-100"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
      {openRetro && (
        <ReflectionDetailModal
          entry={{
            kind: 'RETRO',
            date: openRetro.reviewDate,
            goalCategoryId: goalId,
            goalName: board.goalName,
            goalEmoji: board.emoji,
            colorCode: null,
            goalMode: 'RECORD',
            retrospectiveId: openRetro.retrospectiveId,
            reviewType: openRetro.reviewType,
            mood: null,
            note: openRetro.content,
            value: null,
            metricName: null,
            unit: null,
          }}
          open
          onClose={() => setOpenRetro(null)}
          surface="work_board"
          daysAgo={daysAgo(openRetro.reviewDate, board.today)}
        />
      )}
    </div>
  )
}

type ComposeTab = 'task' | 'log' | 'retro'

const COMPOSE_TABS: { value: ComposeTab; label: string; icon: typeof Check }[] = [
  { value: 'task', label: '할 일', icon: ListTodo },
  { value: 'log', label: '한 일', icon: PenLine },
  { value: 'retro', label: '회고', icon: MessageSquareText },
]

/**
 * 적기 패널 — 할 일 · 실제로 한 일 · 회고를 한곳에서 적어요 (넓은 화면에선 오른쪽에 붙어 있어요).
 * 화면 가운데에 입력창이 흩어져 있으면 "여기에 적는 건가?" 헷갈려서 한데 모았어요.
 */
function ComposePanel({
  goal,
  board,
  tab,
  onTab,
  topicId,
  onTopic,
  inputRef,
}: {
  goal: GoalCategory
  board: WorkBoard
  tab: ComposeTab
  onTab: (t: ComposeTab) => void
  topicId: number | null
  onTopic: (id: number | null) => void
  inputRef: RefObject<HTMLTextAreaElement | null>
}) {
  return (
    // 떠 있는 "적기" 버튼으로 여는 창 안 (명세 12.2 — 오른쪽 적기 카드는 없앴어요)
    <div id="work-compose">
      <div className={segmentedClass('grid grid-cols-3')} role="tablist" aria-label="무엇을 적을지">
        {COMPOSE_TABS.map(({ value, label, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            onClick={() => onTab(value)}
            className={segmentClass(tab === value, 'h-9 gap-1 px-0')}
          >
            <Icon className="size-3.5" />
            {label}
          </button>
        ))}
      </div>
      <div className="mt-4">
        {tab === 'task' ? (
          <TaskCompose
            goalId={goal.goalCategoryId}
            topics={board.topics}
            topicId={topicId}
            onTopic={onTopic}
            inputRef={inputRef}
          />
        ) : tab === 'log' ? (
          <RecordQuickLog goal={goal} bare />
        ) : (
          <RetroCompose goalId={goal.goalCategoryId} board={board} />
        )}
      </div>
    </div>
  )
}

// ─── 4. 주제 필터 ─────────────────────────────────────────────

function TopicBar({
  topics,
  filter,
  onFilter,
}: {
  topics: WorkTopic[]
  filter: TopicFilter
  onFilter: (f: TopicFilter, entry: FilterEntry) => void
}) {
  const total = topics.reduce((n, t) => n + t.remaining, 0)
  const chip = (selected: boolean) =>
    cn(
      'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[13px] font-semibold transition-colors',
      selected ? 'border-text-brand bg-ai-tint-soft text-text-brand' : 'border-border-subtle bg-bg-surface text-text-primary hover:bg-bg-sunken'
    )

  return (
    <div className="flex flex-wrap items-center gap-1.5" role="tablist" aria-label="세부 목표">
      <button
        type="button"
        role="tab"
        aria-selected={filter === 'ALL'}
        onClick={() => onFilter('ALL', 'topic_bar')}
        className={chip(filter === 'ALL')}
      >
        전체 <span className="tabular-nums opacity-60">{total}</span>
      </button>
      {topics.map((t) => {
        const selected = t.topicId === null ? filter === 'NONE' : filter === t.topicId
        return (
          <button
            key={t.topicId ?? 'none'}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onFilter(t.topicId === null ? 'NONE' : t.topicId, 'topic_bar')}
            className={chip(selected)}
          >
            {t.emoji ? `${t.emoji} ` : ''}
            {t.name} <span className="tabular-nums opacity-60">{t.remaining}</span>
          </button>
        )
      })}
    </div>
  )
}

// ─── 5. 보드 (시작 전 · 진행 중 · 완료) ─────────────────────────────

const COLUMNS: { key: WorkColumn; icon: typeof Check; list: 'notStarted' | 'inProgress' | 'done' }[] = [
  { key: 'NOT_STARTED', icon: CircleDashed, list: 'notStarted' },
  { key: 'IN_PROGRESS', icon: Loader, list: 'inProgress' },
  { key: 'DONE', icon: Check, list: 'done' },
]

function BoardCard({
  task,
  today,
  showTopic,
  onOpen,
  onMove,
  onDragStart,
  onDragEnd,
}: {
  task: WorkTask
  today: string
  showTopic: boolean
  onOpen: () => void
  onMove: (to: WorkColumn, method: MoveMethod) => void
  onDragStart: () => void
  onDragEnd: () => void
}) {
  const next: WorkColumn | null =
    task.status === 'NOT_STARTED' ? 'IN_PROGRESS' : task.status === 'IN_PROGRESS' ? 'DONE' : null
  return (
    <li
      draggable
      onDragStart={(e) => {
        e.dataTransfer.setData('text/plain', String(task.taskId))
        e.dataTransfer.effectAllowed = 'move'
        onDragStart()
      }}
      onDragEnd={onDragEnd}
      className={cn(
        'border-line bg-surface hover:border-line-strong group cursor-grab rounded-xl border p-3 shadow-card active:cursor-grabbing',
        task.overdue && 'border-border-subtle'
      )}
    >
      <div className="flex items-start gap-2">
        <CheckButton task={task} onToggle={() => onMove(task.status === 'DONE' ? 'NOT_STARTED' : 'DONE', 'check')} />
        <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
          <span
            className={cn(
              'block text-[14px] leading-snug font-semibold',
              task.status === 'DONE' && 'text-ink-3 line-through'
            )}
          >
            {task.taskName}
          </span>
          <TaskMeta task={task} today={today} showTopic={showTopic} />
          {task.date === null && task.status !== 'DONE' && (
            <span className="text-ink-4 mt-0.5 block text-[11px]">할 일 목록</span>
          )}
          {task.memo && <span className="text-ink-3 mt-1 line-clamp-2 block text-[12px]">{task.memo}</span>}
        </button>
      </div>
      {next && (
        <button
          type="button"
          onClick={() => onMove(next, 'next_button')}
          className="text-ink-3 hover:text-text-brand mt-2 ml-8 text-[12px] font-semibold [@media(hover:hover)]:hidden [@media(hover:hover)]:group-hover:block"
        >
          {COLUMN_LABEL[next]}으로 →
        </button>
      )}
    </li>
  )
}

function Kanban({
  goalId,
  board,
  filter,
  onOpen,
  onMove,
}: {
  goalId: number
  board: WorkBoard
  filter: TopicFilter
  onOpen: (t: WorkTask) => void
  onMove: (t: WorkTask, to: WorkColumn, method: MoveMethod) => void
}) {
  const [over, setOver] = useState<WorkColumn | null>(null)
  const all = useMemo(() => [...board.board.notStarted, ...board.board.inProgress, ...board.board.done], [board])
  // 칸 위에 놓았는지 — 놓지 않고 끝난 끌기는 '칸 밖' 취소 (drop 이 dragend 보다 먼저 와요)
  const dropped = useRef(false)

  // 주제 필터 때문에 빈 칸 안내가 보일 때 — 필터를 바꿀 때마다 한 번
  const emptySent = useRef<TopicFilter | null>(null)
  useEffect(() => {
    if (emptySent.current === filter) return
    emptySent.current = filter
    if (filter === 'ALL') return
    for (const { key, list } of COLUMNS) {
      if (board.board[list].some((t) => matchTopic(t, filter))) continue
      track('work_board.empty_column_view', {
        goal_category_id: goalId,
        to_status: key,
        topic_filter: filterKind(filter),
        unfiltered_count: key === 'DONE' ? board.board.doneTotal : board.board[list].length,
      })
    }
  }, [filter, board, goalId])

  const onDrop = (e: DragEvent, to: WorkColumn) => {
    e.preventDefault()
    setOver(null)
    dropped.current = true
    const id = Number(e.dataTransfer.getData('text/plain'))
    const task = all.find((t) => t.taskId === id)
    if (!task) return
    if (task.status !== to) onMove(task, to, 'drag')
    else
      track('work_board.card_drag_cancel', {
        goal_category_id: goalId,
        task_id: task.taskId,
        from_status: task.status,
        drag_cancel_reason: 'same_column',
      })
  }
  const dragStart = (t: WorkTask) => {
    dropped.current = false
    track('work_board.card_drag_start', { goal_category_id: goalId, task_id: t.taskId, from_status: t.status })
  }
  const dragEnd = (t: WorkTask) => {
    if (dropped.current) return
    track('work_board.card_drag_cancel', {
      goal_category_id: goalId,
      task_id: t.taskId,
      from_status: t.status,
      drag_cancel_reason: 'outside',
    })
  }

  return (
    <div className="grid gap-3 md:grid-cols-3">
      {COLUMNS.map(({ key, icon: Icon, list }) => {
        const items = board.board[list].filter((t) => matchTopic(t, filter))
        const count = key === 'DONE' && filter === 'ALL' ? board.board.doneTotal : items.length
        return (
          <section
            key={key}
            aria-label={COLUMN_LABEL[key]}
            onDragOver={(e) => {
              e.preventDefault()
              setOver(key)
            }}
            onDragLeave={() => setOver((o) => (o === key ? null : o))}
            onDrop={(e) => onDrop(e, key)}
            className={cn(
              'bg-subtle/70 min-h-[160px] rounded-2xl p-3 transition-colors',
              over === key && 'bg-brand-soft ring-brand/40 ring-2'
            )}
          >
            <h3 className="mb-2.5 flex items-center gap-1.5 px-1 text-[13px] font-bold">
              <Icon
                className={cn(
                  'size-4',
                  key === 'DONE' ? 'text-success' : key === 'IN_PROGRESS' ? 'text-text-brand' : 'text-ink-3'
                )}
              />
              {COLUMN_LABEL[key]}
              <span className="text-ink-3 font-semibold tabular-nums">{count}</span>
            </h3>
            {items.length === 0 ? (
              <p className="text-ink-4 px-1 py-6 text-center text-[12px]">
                {key === 'IN_PROGRESS'
                  ? '하고 있는 일을 여기로 끌어 오세요'
                  : key === 'DONE'
                    ? '끝낸 일이 여기 쌓여요'
                    : '할 일이 없어요'}
              </p>
            ) : (
              <ul className="space-y-2">
                {items.map((t) => (
                  <BoardCard
                    key={t.taskId}
                    task={t}
                    today={board.today}
                    showTopic={filter === 'ALL'}
                    onOpen={() => onOpen(t)}
                    onMove={(to, method) => onMove(t, to, method)}
                    onDragStart={() => dragStart(t)}
                    onDragEnd={() => dragEnd(t)}
                  />
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  )
}

// ─── 화면 ──────────────────────────────────────────────────────

/**
 * 업무형 목표의 "보드" 탭. 업무형은 목표 전체 마감이 없고 주제마다 마감이 있어요 (V53).
 *  왼쪽(넓게)
 *   1. 다음 마감 · 진행 요약
 *   2. 로드맵 — 주제를 마감 순서대로 세로 선 위에, 점과 점 사이에 그 주제의 남은 할 일
 *   3. 보드 (시작 전 · 진행 중 · 완료) — 끌어서 옮기기
 *   4. 업무 일지 (완료한 Task + 직접 남긴 기록) · 메모
 *  오른쪽(붙어 있는 패널): 적기 — 할 일 · 한 일 · 회고 입력을 한곳에 + 반복 루틴
 * 오늘 할 일 · 마감 임박은 홈에서 모든 목표를 모아 보여줘서 여기선 뺐어요.
 */
export function WorkBoardView({ goal }: { goal: GoalCategory }) {
  const goalId = goal.goalCategoryId
  const { data: board, isPending, isError, error, errorUpdatedAt, refetch } = useWorkBoard(goalId)
  const move = useMoveWorkTask(goalId)
  const [filter, setFilter] = useState<TopicFilter>('ALL')
  const [opened, setOpened] = useState<WorkTask | null>(null)
  /** 자세히 창을 연 화면 — 창 안에서 칸을 바꾸면 work_task.status_change 의 surface 로 써요 */
  const [openedFrom, setOpenedFrom] = useState<Surface>('board')
  const [composeTab, setComposeTab] = useState<ComposeTab>('task')
  const [composeTopic, setComposeTopic] = useState<number | null>(null)
  const composeInput = useRef<HTMLTextAreaElement>(null)
  const [composeOpen, setComposeOpen] = useState(false)
  const routineCount = routineCountOf(goal)

  // 보드를 다 불러와 그렸을 때 한 번 (탭을 다시 열면 다시 마운트돼서 또 한 번)
  const viewSent = useRef(false)
  useEffect(() => {
    if (!board || isError || viewSent.current) return
    viewSent.current = true
    const s = board.summary
    track('work_board.view', {
      goal_category_id: goalId,
      topic_count: s.topicsTotal,
      topic_done_count: s.topicsDone,
      overdue_topic_count: board.timeline.filter((t) => t.status === 'OVERDUE').length,
      work_total_count: s.total,
      work_done_count: s.done,
      open_task_count: s.remaining,
      in_progress_count: s.inProgress,
      overdue_count: s.overdue,
      due_this_week_count: s.dueThisWeek,
      has_next_due: !!s.nextTopic,
      d_day: s.nextTopic?.dDay,
      routine_count: routineCount,
    })
  }, [board, isError, goalId, routineCount])

  // '보드를 불러오지 못했어요' — 실패할 때마다 한 번 (다시 시도 후 또 실패하면 다시)
  const failSent = useRef(0)
  useEffect(() => {
    if (!isError || failSent.current === errorUpdatedAt) return
    failSent.current = errorUpdatedAt
    track('work_board.load_fail', { goal_category_id: goalId, error_status: failStatus(error) })
  }, [isError, error, errorUpdatedAt, goalId])

  if (isPending) {
    return (
      <div className="grid h-60 place-items-center">
        <Spinner className="text-ink-3 size-6" />
      </div>
    )
  }
  if (isError || !board) {
    return (
      <Card className="p-8 text-center">
        <p className="font-bold">보드를 불러오지 못했어요</p>
        <p className="text-ink-3 mt-1 text-sm">{error?.message}</p>
        <Button
          className="mt-4"
          onClick={() => {
            track('work_board.retry_click', { goal_category_id: goalId, error_status: failStatus(error) })
            refetch()
          }}
        >
          다시 시도
        </Button>
      </Card>
    )
  }

  // Task 자세히 창 열기 (로드맵 이름 · 보드 카드 · 루틴 체크)
  const openTask = (t: WorkTask, surface: Surface) => {
    track('work_task.open', {
      surface,
      goal_category_id: goalId,
      task_id: t.taskId,
      task_status: t.status === 'DONE' ? 'COMPLETED' : 'TODO',
      is_routine: t.routine,
      is_overdue: t.overdue,
    })
    setOpenedFrom(surface)
    setOpened(t)
  }
  // 칸 옮기기 — 화면은 먼저 바뀌고 실패하면 되돌려요 (useMoveWorkTask)
  const moveTask = (t: WorkTask, to: WorkColumn, surface: Surface, method: MoveMethod) => {
    const common = { surface, task_id: t.taskId, to_status: to, move_method: method }
    track('work_task.status_change', {
      ...common,
      goal_category_id: goalId,
      topic_id: t.topicId,
      from_status: t.status,
      is_overdue: t.overdue,
    })
    move.mutateAsync({ task: t, to }).then(
      () => track('work_task.move_result', { ...common, result: 'success' }),
      (e: unknown) => track('work_task.move_result', { ...common, result: 'fail', error_status: failStatus(e) })
    )
  }
  const toggle = (t: WorkTask) => {
    if (t.routine) {
      // 루틴 Task 는 보드 칸이 없어서 상세 창에서 처리
      openTask(t, 'roadmap')
      return
    }
    moveTask(t, t.status === 'DONE' ? 'NOT_STARTED' : 'DONE', 'roadmap', 'check')
  }
  const changeFilter = (f: TopicFilter, entry: FilterEntry) => {
    if (f !== filter) {
      const visible = [...board.board.notStarted, ...board.board.inProgress, ...board.board.done]
      track('work_board.topic_filter_change', {
        goal_category_id: goalId,
        topic_filter: filterKind(f),
        topic_id: typeof f === 'number' ? f : undefined,
        entry_point: entry,
        result_count: visible.filter((t) => matchTopic(t, f)).length,
      })
    }
    setFilter(f)
  }
  // 로드맵에서 주제를 고르면 보드가 그 주제만 보여주고, 보드로 내려가요
  const pickTopic = (f: TopicFilter, entry: FilterEntry) => {
    changeFilter(f, entry)
    if (f !== 'ALL') document.getElementById('work-board')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  // 로드맵 "+ 할 일" → 적기 패널을 그 주제로 맞추고 입력창에 커서
  const composeFor = (topicId: number | null) => {
    track('work_roadmap.add_task_click', { goal_category_id: goalId, topic_id: topicId, compose_tab: composeTab })
    setComposeTab('task')
    setComposeTopic(topicId)
    setComposeOpen(true)
    // 창이 열린 뒤 입력칸에 커서
    window.setTimeout(() => composeInput.current?.focus({ preventScroll: true }), 50)
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-[minmax(0,1fr)] items-start gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
        {/* 반복 루틴 — 넓은 화면에선 오른쪽에 붙어서 따라와요 */}
        <aside className="space-y-5 lg:sticky lg:top-4 lg:order-last lg:max-h-[calc(100dvh-2rem)] lg:overflow-y-auto lg:pb-1">
          <RoutinesSection goal={goal} />
        </aside>

        <div className="min-w-0 space-y-5">
          <Roadmap
            goalId={goalId}
            board={board}
            filter={filter}
            onFilter={pickTopic}
            onOpen={(t) => openTask(t, 'roadmap')}
            onToggle={toggle}
            onAdd={composeFor}
          />

          <Card id="work-board" className="scroll-mt-4 p-4 sm:p-5">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-h4">보드</h2>
              <span className="text-ink-3 text-[12px]">카드를 끌어서 칸을 옮기거나, 눌러서 자세히 고쳐요</span>
            </div>
            <TopicBar topics={board.topics} filter={filter} onFilter={changeFilter} />
            <div className="mt-4">
              <Kanban
                goalId={goalId}
                board={board}
                filter={filter}
                onOpen={(t) => openTask(t, 'board')}
                onMove={(t, to, method) => moveTask(t, to, 'board', method)}
              />
            </div>
          </Card>

          <RecordJournal goalId={goalId} goalName={goal.goalCategoryName} />

          <GoalNotes goal={goal} />
        </div>
      </div>

      {opened && (
        <WorkTaskModal
          key={opened.taskId}
          goalId={goalId}
          task={opened}
          topics={board.topics}
          surface={openedFrom}
          onClose={() => setOpened(null)}
        />
      )}
      {/* 적기 — 떠 있는 버튼 → 창 (할 일 · 한 일 · 회고) (명세 12.2 · 12.9) */}
      <GoalFabs>
        <GoalFab label="적기" icon={<SquarePen />} onClick={() => setComposeOpen(true)} />
      </GoalFabs>
      <Modal open={composeOpen} onClose={() => setComposeOpen(false)} title="적기" size="md">
        <ComposePanel
          goal={goal}
          board={board}
          tab={composeTab}
          onTab={(t) => {
            if (t !== composeTab)
              track('work_compose.tab_click', { goal_category_id: goalId, compose_tab: t, from_tab: composeTab })
            setComposeTab(t)
          }}
          topicId={composeTopic}
          onTopic={setComposeTopic}
          inputRef={composeInput}
        />
      </Modal>
    </div>
  )
}

/**
 * 업무형 목표 요약 카드 (명세 12.1) — 목표 머리말(header)과 다음 마감 · 진행 · 공유를 한 카드로.
 * 탭 위에 놓여서 보드 · 통계 · 회고 어느 탭에서도 보여요.
 */
export function WorkSummaryCard({ goal, header }: { goal: GoalCategory; header: React.ReactNode }) {
  const goalId = goal.goalCategoryId
  const { data: board } = useWorkBoard(goalId)
  const [sharing, setSharing] = useState(false)
  if (!board) {
    return (
      <section
        style={goalVars(goal.colorCode)}
        className="rounded-card bg-(--goal-bg) p-5"
      >
        {header}
      </section>
    )
  }
  return (
    <>
      <SummaryHero
        board={board}
        colorCode={goal.colorCode}
        header={header}
        onShare={() => {
          track('work_board.share_click', { goal_category_id: goalId, open_task_count: board.summary.remaining })
          setSharing(true)
        }}
      />
      <WorkShareModal goalId={goalId} open={sharing} onClose={() => setSharing(false)} />
    </>
  )
}
