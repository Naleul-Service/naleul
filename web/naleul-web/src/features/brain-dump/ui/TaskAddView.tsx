'use client'

import { useEffect, useImperativeHandle, useRef, useState, type Ref } from 'react'
import { CalendarCheck, PenLine, RotateCcw } from 'lucide-react'
import { Check, NotebookPen, Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { PageHeader } from '@/components/layout/PageHeader'
import { cn } from '@/lib/cn'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { toast } from '@/stores/toastStore'
import { useGoalCategories } from '@/features/goal/api'
import { isOngoing } from '@/features/goal/format'
import { formatMonthDay, hm, todayKst } from '@/features/timetable/time'
import { LogDoneView } from '@/features/record/ui/LogDoneView'
import type { TaskAddSurface } from '../analytics'
import { useConfirmBrainDump, useParseBrainDump, useTaskQuota } from '../api'
import type { ConfirmResponse, ParseResponse, TempGoalInput } from '../types'
import { ManualTaskAdd } from './ManualTaskAdd'
import { StepGoals, trackGoalsConfirmResult, type MapItem } from './StepGoals'
import { StepInput, type DumpLine } from './StepInput'
import { StepResult } from './StepResult'

const STEPS = ['Task 입력', '목표 연결', 'TimeBlock 확인'] as const
/** task_add.* 의 ai_step — STEPS 순서 그대로 */
const AI_STEPS = ['input', 'goals', 'result'] as const

type AddMode = 'ai' | 'manual' | 'log'

/** 패널이 닫힐 때(task_add.panel_close) 부모가 읽는 지금 상태 */
export interface TaskAddViewHandle {
  closeInfo: () => { add_mode: AddMode; ai_step?: (typeof AI_STEPS)[number]; has_input: boolean }
}

/** 화면에 글자가 적힌 입력칸이 있는지 (탭을 바꾸거나 패널을 닫으면 확인 없이 사라지는 내용) — 원문은 보내지 않아요 */
const hasTypedInput = (root: HTMLElement | null) =>
  !!root &&
  Array.from(
    root.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('textarea, input:not([type]), input[type="text"]')
  ).some((el) => el.value.trim().length > 0)

/** "15:00:00" → "15:00" */
const toHm = (t?: string | null) => (t ? t.slice(0, 5) : null)

function toMapItems(res: ParseResponse): { items: MapItem[]; tempGoals: TempGoalInput[] } {
  const items: MapItem[] = res.items.map((i) => ({
    clientKey: i.clientKey,
    title: i.title,
    dateType: i.dateType,
    scheduledDate: i.scheduledDate ?? null,
    dueDate: i.dueDate ?? null,
    startTime: toHm(i.startTime),
    endTime: toHm(i.endTime),
    estimatedMinutes: i.estimatedMinutes,
    priority: i.priority ?? 'C',
    kind: i.kind ?? 'NORMAL',
    goalCategoryId: i.goal.type === 'EXISTING' ? (i.goal.goalCategoryId ?? null) : null,
    generalCategoryId: i.goal.type === 'EXISTING' ? (i.goal.generalCategoryId ?? null) : null,
    milestoneId: i.goal.type === 'EXISTING' ? (i.goal.milestoneId ?? null) : null,
    tempGoalKey: i.goal.type === 'TEMP' ? (i.goal.tempKey ?? null) : null,
    reason: i.goal.reason,
  }))
  const used = new Set(items.map((i) => i.tempGoalKey).filter(Boolean))
  const tempGoals = res.tempGoals
    .filter((t) => used.has(t.tempKey))
    .map((t) => ({ tempKey: t.tempKey, name: t.name, emoji: t.emoji ?? null }))
  return { items, tempGoals }
}

/**
 * /tasks/new — Task 추가 3단계 (명세 3-2, 8)
 *   1. Task 입력 (Brain dump) → POST /brain-dump/parse
 *   2. 목표 연결 → POST /brain-dump/confirm
 *   3. TimeBlock 확인
 */
/** 추가한 Task 알림 — 캘린더 옆 패널에서 열었을 때 캘린더를 그 날짜로 옮기고 새 블록을 표시해요 */
export interface TaskAddedInfo {
  taskIds: number[]
  /** 가장 이른 날짜 (시간 미정이면 그날) */
  date: string | null
  /** 저장만 하고 아직 시간을 정하지 않은 Task — 캘린더가 점선 미리보기로 "이대로 진행할까요?"를 물어요 */
  pendingTaskIds?: number[]
}

export function TaskAddView({
  initialMode = 'ai',
  variant = 'page',
  onAdded,
  ref,
  singleMode = false,
  prefill,
}: {
  /** 한 가지 방식만 (캘린더 + 메뉴에서 연 패널 — 방식 고르기 탭을 숨겨요, 명세 11.3) */
  singleMode?: boolean
  /** 직접 추가 폼에 미리 채울 날짜 · 시각 */
  prefill?: { date: string; startTime?: string | null; endTime?: string | null }
  initialMode?: 'ai' | 'manual' | 'log'
  /** page = /tasks/new 화면 · panel = 캘린더 오른쪽 패널 (좁은 폭, 결과는 옆 캘린더에서 확인) */
  variant?: 'page' | 'panel'
  onAdded?: (info: TaskAddedInfo) => void
  /** 캘린더 옆 패널이 닫힐 때 지금 방식·단계·적던 내용 여부를 읽어요 */
  ref?: Ref<TaskAddViewHandle>
}) {
  const panel = variant === 'panel'
  const surface: TaskAddSurface = panel ? 'calendar_panel' : 'page'
  const today = todayKst()
  // 목표 만들기처럼 "AI로 정리하기 / 직접 추가하기" 중에서 골라요
  // 할 일을 적거나(ai·manual), 이미 한 일을 기록하거나(log)
  const [mode, setMode] = useState<'ai' | 'manual' | 'log'>(initialMode)
  const [step, setStep] = useState<0 | 1 | 2>(0)
  const [lines, setLines] = useState<DumpLine[]>([])
  const [items, setItems] = useState<MapItem[]>([])
  const [tempGoals, setTempGoals] = useState<TempGoalInput[]>([])
  const [aiFailed, setAiFailed] = useState(false)
  const [aiLimited, setAiLimited] = useState(false)
  const [conflicts, setConflicts] = useState<Record<string, string>>({})
  const [result, setResult] = useState<ConfirmResponse | null>(null)

  const quota = useTaskQuota()
  // 기본 생활 습관으로도 분류할 수 있게 (2026-10)
  const goals = useGoalCategories({ includeHabit: true })
  const parse = useParseBrainDump()
  const confirm = useConfirmBrainDump()
  const ongoingGoals = (goals.data ?? []).filter((g) => isOngoing(g.goalCategoryStatus))

  // ── 분석 ──
  const contentRef = useRef<HTMLDivElement>(null)
  /** 지금 방식에 적어 둔 내용이 있는지 — AI 정리는 저장(3단계) 전까지 줄 목록도 내용이에요 */
  const hasInput = () => {
    if (mode === 'ai') return step < 2 && (lines.length > 0 || hasTypedInput(contentRef.current))
    return hasTypedInput(contentRef.current)
  }
  useImperativeHandle(ref, () => ({
    closeInfo: () => ({
      add_mode: mode,
      ai_step: mode === 'ai' ? AI_STEPS[step] : undefined,
      has_input: hasInput(),
    }),
  }))

  // 화면/패널 열림 — 오늘 남은 개수를 받은 뒤(실패해도) 한 번
  const viewSent = useRef(false)
  useEffect(() => {
    if (quota.isPending || viewSent.current) return
    viewSent.current = true
    const q = quota.data
    track('task_add.view', {
      surface,
      initial_mode: initialMode,
      is_premium: q?.premium,
      quota_remaining: q && !q.premium ? q.remaining : undefined,
    })
  }, [quota.isPending, quota.data, surface, initialMode])

  // 오늘 남은 개수 불러오기 실패 — 실패 상태로 바뀔 때 한 번
  const quotaFailSent = useRef(false)
  const quotaErrorStatus = quota.isError ? (errorStatusOf(quota.error) ?? 0) : undefined
  useEffect(() => {
    if (quotaErrorStatus === undefined) {
      quotaFailSent.current = false
      return
    }
    if (quotaFailSent.current) return
    quotaFailSent.current = true
    track('task_add.quota_load_fail', { surface, error_status: quotaErrorStatus })
  }, [quotaErrorStatus, surface])

  const goParse = () => {
    const valid = lines.filter((l) => l.text.trim())
    if (!valid.length) return
    const startedAt = Date.now()
    const secondsElapsed = () => Math.round((Date.now() - startedAt) / 1000)
    parse.mutate(
      valid.map((l) => ({
        clientKey: l.clientKey,
        text: l.text.trim(),
        date: l.date,
        dateType: l.date ? l.dateType : null,
        startTime: l.date || l.startTime ? l.startTime : null,
        endTime: l.date || l.startTime ? l.endTime : null,
        estimatedMinutes: l.minutes,
      })),
      {
        onSuccess: (res) => {
          const mapped = toMapItems(res)
          track('task_add_input.parse_result', {
            surface,
            result: 'success',
            item_count: mapped.items.length,
            ai_failed: res.aiFailed,
            ai_limited: !!res.aiLimited,
            temp_count: mapped.tempGoals.length,
            missing_goal_count: mapped.items.filter((i) => !i.goalCategoryId && !i.generalCategoryId && !i.tempGoalKey)
              .length,
            seconds_elapsed: secondsElapsed(),
          })
          setItems(mapped.items)
          setTempGoals(mapped.tempGoals)
          setAiFailed(res.aiFailed)
          setAiLimited(!!res.aiLimited)
          setConflicts({})
          setStep(1)
        },
        onError: (e) => {
          track('task_add_input.parse_result', {
            surface,
            result: 'fail',
            error_status: errorStatusOf(e) ?? 0,
            seconds_elapsed: secondsElapsed(),
          })
          toast.error(isApiError(e) ? e.message : '정리하지 못했어요. 다시 시도해 주세요.')
        },
      }
    )
  }

  const goConfirm = () => {
    const used = new Set(items.map((i) => i.tempGoalKey).filter(Boolean))
    confirm.mutate(
      {
        // reason 은 화면용이라 보내지 않아요
        items: items.map((i) => ({
          clientKey: i.clientKey,
          title: i.title,
          dateType: i.dateType,
          estimatedMinutes: i.estimatedMinutes,
          priority: i.priority,
          kind: i.kind,
          goalCategoryId: i.goalCategoryId,
          generalCategoryId: i.generalCategoryId,
          milestoneId: i.milestoneId,
          tempGoalKey: i.tempGoalKey,
          workTopicId: i.goalCategoryId ? (i.workTopicId ?? null) : null,
          scheduledDate: i.dateType === 'ON' ? i.scheduledDate : null,
          dueDate: i.dateType === 'DUE' ? i.dueDate : null,
          startTime: i.dateType === 'ON' ? i.startTime : null,
          endTime: i.dateType === 'ON' && i.startTime ? i.endTime : null,
        })),
        tempGoals: tempGoals.filter((t) => used.has(t.tempKey)).map((t) => ({ ...t, name: t.name.trim() })),
        // 시간을 정하지 않은 Task 는 저장만 → 점선 미리보기로 "이대로 진행할까요?" 물은 뒤 배치
        placeLater: true,
      },
      {
        onSuccess: (res) => {
          trackGoalsConfirmResult(surface, { res }, used.size)
          if (!res.created) {
            const map: Record<string, string> = {}
            for (const c of res.conflicts) {
              // 한 시각에 2개까지는 같이 둘 수 있어요 — 여기 오는 건 이미 2개가 겹친 시간에 3개째를 넣으려 한 경우
              map[c.clientKey] =
                `이 시간엔 이미 ${c.conflictTitle ? `"${c.conflictTitle}"` : '다른 일정 2개'}이(가) 겹쳐 있어요 (${hm(c.start)}–${hm(c.end)}). 시간을 조금 옮겨 주세요.`
            }
            setConflicts(map)
            toast.error('겹치는 일정이 있어서 저장하지 않았어요.')
            return
          }
          setResult(res)
          setStep(2)
          const pending = res.pending ?? []
          toast.success(
            pending.length
              ? `${items.length}개 Task를 추가했어요. 점선 자리를 확인해 주세요.`
              : `${items.length}개 Task를 추가했어요.`
          )
          const placedDates = [...res.placements, ...res.movedToOtherDays].map((p) => p.date)
          const allDates = [
            ...placedDates,
            ...[...res.unscheduled, ...pending].map((u) => u.date).filter((d): d is string => !!d),
          ].sort()
          onAdded?.({
            taskIds: [
              ...res.placements.map((p) => p.taskId),
              ...res.movedToOtherDays.map((p) => p.taskId),
              ...res.unscheduled.map((u) => u.taskId),
              ...pending.map((u) => u.taskId),
            ],
            date: allDates[0] ?? null,
            pendingTaskIds: pending.map((u) => u.taskId),
          })
        },
        onError: (e) => {
          trackGoalsConfirmResult(surface, { error: e }, used.size)
          toast.error(isApiError(e) ? e.message : '저장하지 못했어요. 다시 시도해 주세요.')
        },
      }
    )
  }

  const reset = () => {
    setLines([])
    setItems([])
    setTempGoals([])
    setConflicts({})
    setResult(null)
    setStep(0)
    quota.refetch()
  }

  const wide = panel ? '' : 'max-w-5xl'
  return (
    <>
      {!panel && (
        <PageHeader
          title="Task 추가"
          description="할 일을 AI로 정리하거나 직접 추가하고, 이미 한 일은 기록으로 남길 수 있어요."
        />
      )}

      {/* 추가 방식 고르기 (캘린더 + 메뉴에서 연 패널은 방식이 정해져 있어서 숨겨요) */}
      {!singleMode && (
      <div
        className={cn('grid gap-3', panel ? 'grid-cols-3 gap-1.5' : 'mt-5 max-w-5xl sm:grid-cols-3')}
        role="tablist"
        aria-label="추가 방식"
      >
        {(
          [
            ['ai', Sparkles, 'AI로 정리하기', '생각나는 일을 쏟아내면 목표에 연결하고 빈 시간에 배치해요.'],
            ['manual', PenLine, '직접 추가하기', '목표·날짜·시간을 정해서 하나씩 바로 추가해요.'],
            ['log', NotebookPen, '한 일 기록', '이미 한 일을 한 줄씩 적으면 목표에 연결해 기록으로 쌓아요.'],
          ] as const
        ).map(([key, Icon, title, desc]) => {
          const locked = mode === 'ai' && key !== 'ai' && step > 0 && !result
          const tab = (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={mode === key}
              onClick={() => {
                track('task_add.mode_click', {
                  surface,
                  add_mode: key,
                  from_mode: mode,
                  ai_step: AI_STEPS[step],
                  has_input: hasInput(),
                })
                setMode(key)
              }}
              disabled={locked}
              title={locked ? 'AI 정리를 마치거나 처음으로 돌아가면 바꿀 수 있어요' : panel ? desc : undefined}
              className={cn(
                'flex rounded-2xl border text-left transition-colors disabled:opacity-40',
                panel ? 'flex-col items-center gap-1 rounded-xl p-2 text-center' : 'items-start gap-3 p-4',
                mode === key
                  ? key === 'ai'
                    ? 'ai-border-flat' // AI 방식 (명세 6B)
                    : 'border-action-outline-border bg-bg-surface ring-action-outline-border ring-[0.5px]'
                  : 'border-border-subtle bg-bg-surface hover:border-line-strong'
              )}
            >
              <span
                className={cn(
                  'grid shrink-0 place-items-center rounded-xl',
                  panel ? 'size-7 rounded-lg' : 'size-9',
                  key === 'ai'
                    ? 'bg-ai-gradient text-white' // AIBadge 와 같은 모양
                    : mode === key
                      ? 'bg-action-primary-bg text-action-primary-fg'
                      : 'bg-bg-subtle text-text-secondary'
                )}
              >
                <Icon className="size-4" />
              </span>
              <span className="min-w-0">
                <span className={cn('block font-bold', panel ? 'text-[12px]' : 'text-[15px]')}>{title}</span>
                {!panel && <span className="text-ink-3 mt-0.5 block text-[13px] leading-snug">{desc}</span>}
              </span>
            </button>
          )
          if (!locked) return tab
          // 잠긴 탭 — disabled 버튼은 click 이 안 나서 감싼 span 의 pointerdown 으로 잡아요
          return (
            <span
              key={key}
              className="contents"
              onPointerDown={() =>
                track('task_add.mode_locked_click', { surface, add_mode: key, ai_step: AI_STEPS[step] })
              }
            >
              {tab}
            </span>
          )
        })}
      </div>
      )}

      <div ref={contentRef} className="contents">
        {mode === 'log' ? (
          <div className={cn('mt-4', wide)}>
            <LogDoneView goals={ongoingGoals} surface={surface} />
          </div>
        ) : mode === 'manual' ? (
          <div className={cn('mt-4', wide)}>
            <ManualTaskAdd
              inPanel={panel}
              surface={surface}
              prefill={prefill}
              onCreated={(t) =>
                onAdded?.({
                  taskIds: [t.taskId],
                  date: (t.plannedStartAt ?? t.date ?? t.scheduledDate ?? '').slice(0, 10) || null,
                })
              }
            />
          </div>
        ) : (
          <>
            <ol
              className={cn('mb-4 flex flex-wrap items-center gap-2', panel ? 'mt-4' : 'mt-5')}
              aria-label="진행 단계"
            >
              {STEPS.map((label, i) => (
                <li key={label} className="flex items-center gap-2" aria-current={step === i ? 'step' : undefined}>
                  <span
                    className={cn(
                      'grid size-6 place-items-center rounded-full text-xs font-bold',
                      i < step ? 'bg-success text-on-ink' : i === step ? 'bg-action-primary-bg text-action-primary-fg' : 'bg-subtle text-ink-3'
                    )}
                  >
                    {i < step ? <Check className="size-3.5" /> : i + 1}
                  </span>
                  <span className={cn(panel ? 'text-[12px]' : 'text-sm', i === step ? 'font-bold' : 'text-ink-3')}>
                    {panel && i === 2 ? '확인' : label}
                  </span>
                  {i < STEPS.length - 1 && (
                    <span className={cn('bg-line-strong mx-1 h-px', panel ? 'w-3' : 'w-6')} aria-hidden />
                  )}
                </li>
              ))}
            </ol>

            <div className={wide}>
              {step === 0 && (
                <StepInput
                  lines={lines}
                  onChange={setLines}
                  quota={quota.data}
                  quotaLoading={quota.isPending}
                  today={today}
                  loading={parse.isPending}
                  onNext={goParse}
                  surface={surface}
                />
              )}
              {step === 1 && (
                <StepGoals
                  items={items}
                  tempGoals={tempGoals}
                  goals={ongoingGoals}
                  aiFailed={aiFailed}
                  aiLimited={aiLimited}
                  conflicts={conflicts}
                  today={today}
                  loading={confirm.isPending}
                  surface={surface}
                  onChange={(nextItems, nextTemps) => {
                    setItems(nextItems)
                    setTempGoals(nextTemps)
                    // 고친 Task 는 겹침 표시를 지워요
                    setConflicts((c) => {
                      const changed = nextItems.filter((n) => items.find((o) => o.clientKey === n.clientKey) !== n)
                      if (!changed.length) return c
                      const next = { ...c }
                      changed.forEach((n) => delete next[n.clientKey])
                      return next
                    })
                  }}
                  onBack={() => setStep(0)}
                  onConfirm={goConfirm}
                />
              )}
              {step === 2 &&
                result &&
                (panel ? (
                  <PanelResult result={result} onAddMore={reset} />
                ) : (
                  <StepResult result={result} today={today} onAddMore={reset} surface={surface} />
                ))}
            </div>
          </>
        )}
      </div>
    </>
  )
}

/**
 * 패널에서 저장한 뒤 — 옆 캘린더에 새 블록(NEW)과 점선 제안이 보이므로 격자를 또 그리지 않고 요약만 해요.
 * 시간을 정하지 않은 Task 는 캘린더 아래 바에서 "이대로 진행할까요?"를 확인해야 놓여요.
 */
function PanelResult({ result, onAddMore }: { result: ConfirmResponse; onAddMore: () => void }) {
  const pending = result.pending ?? []

  // 3단계 노출 (패널은 요약만) — 결과마다 한 번
  const viewSent = useRef(false)
  useEffect(() => {
    if (viewSent.current) return
    viewSent.current = true
    track('task_add_result.view', {
      surface: 'calendar_panel',
      placed_count: result.placements.length,
      moved_count: result.movedToOtherDays.length,
      unscheduled_count: result.unscheduled.length,
      pending_count: (result.pending ?? []).length,
      temp_count: result.tempGoals.length,
    })
  }, [result])

  const rows = [
    ...result.placements.map((p) => ({
      id: p.taskId,
      title: p.title,
      when: `${formatMonthDay(p.date)} ${hm(p.start)}–${hm(p.end)}`,
    })),
    ...result.movedToOtherDays.map((p) => ({
      id: p.taskId,
      title: p.title,
      when: `${formatMonthDay(p.date)} ${hm(p.start)}–${hm(p.end)} (다른 날로)`,
    })),
    ...result.unscheduled.map((u) => ({
      id: u.taskId,
      title: u.title,
      when: u.date ? `${formatMonthDay(u.date)} 시간 미정` : '시간 미정',
    })),
    ...pending.map((u) => ({
      id: u.taskId,
      title: u.title,
      when: u.date ? `${formatMonthDay(u.date)} · 점선 확인` : '점선 확인',
    })),
  ]
  return (
    <div className="border-line bg-surface rounded-2xl border p-4">
      <p className="flex items-center gap-1.5 text-[15px] font-bold">
        <CalendarCheck className="text-success size-4" />
        {rows.length}개를 추가했어요
      </p>
      <p className="text-ink-3 mt-1 text-[13px]">
        {pending.length > 0
          ? `시간을 정하지 않은 ${pending.length}개는 왼쪽 캘린더에 점선으로 먼저 보여 드려요. 끌어서 고친 뒤 아래에서 "이대로 진행"을 누르면 그 자리에 놓이고, "아니요"면 시간 미정으로 남아요.`
          : '왼쪽 캘린더에 NEW로 표시돼요. 끌어서 바로 옮길 수 있어요.'}
      </p>
      <ul className="mt-3 space-y-1.5">
        {rows.map((r) => (
          <li key={r.id} className="flex items-center gap-2 text-[13px]">
            <Check className="text-success size-3.5 shrink-0" />
            <span className="min-w-0 flex-1 truncate font-medium">{r.title}</span>
            <span className="text-ink-3 shrink-0 tabular-nums">{r.when}</span>
          </li>
        ))}
      </ul>
      <Button
        variant="secondary"
        size="sm"
        className="mt-4"
        onClick={() => {
          // 점선 제안은 옆 캘린더(CalendarView)에서 확인해서 여기선 바로 놓인 개수만 알아요
          track('task_add_result.add_more_click', {
            surface: 'calendar_panel',
            placed_count: result.placements.length,
          })
          onAddMore()
        }}
      >
        <RotateCcw className="size-3.5" />더 추가하기
      </Button>
    </div>
  )
}
