'use client'

import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Trash2 } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { DAY_LABEL, DAYS } from '../../constants'
import type { DayOfWeek, GoalPlan, PlanMilestone, PlanSubGoal, PlanTask } from '../../types'
import { Field, inputClass, textareaClass, toNumber } from '../formParts'
import { LIMITS, formatMd, newTempId, toYmd } from './planUtils'

/** 지금 편집 중인 대상. index 가 null 이면 새로 추가 */
export type EditTarget =
  | { kind: 'goal' }
  | { kind: 'subGoal'; index: number | null }
  | { kind: 'milestone'; index: number | null }
  | { kind: 'task'; index: number | null; taskType: PlanTask['type'] }

export type EditResult =
  | {
      kind: 'goal'
      title: string
      emoji: string | null
      startDate: string
      endDate: string
      /** 수치 목표일 때만 (시작값·목표값) */
      metricValues: { startValue: number; targetValue: number } | null
    }
  | { kind: 'subGoal'; index: number | null; item: PlanSubGoal }
  | { kind: 'milestone'; index: number | null; item: PlanMilestone }
  | { kind: 'task'; index: number | null; item: PlanTask }

interface FormProps<T extends EditTarget> {
  target: T
  plan: GoalPlan
  formId: string
  onDone: (result: EditResult) => void
  onError: (message: string) => void
}

const submitter = (fn: () => void) => (e: FormEvent) => {
  e.preventDefault()
  fn()
}

function EmojiTitle({
  emoji,
  title,
  onEmoji,
  onTitle,
  placeholder,
}: {
  emoji: string
  title: string
  onEmoji: (v: string) => void
  onTitle: (v: string) => void
  placeholder?: string
}) {
  return (
    <div className="grid grid-cols-[64px_1fr] gap-3">
      <Field label="이모지">
        <input
          className={`${inputClass} text-center`}
          value={emoji}
          maxLength={4}
          onChange={(e) => onEmoji(e.target.value)}
          placeholder="🙂"
        />
      </Field>
      <Field label="제목">
        <input
          autoFocus
          className={inputClass}
          value={title}
          maxLength={LIMITS.title}
          onChange={(e) => onTitle(e.target.value)}
          placeholder={placeholder}
        />
      </Field>
    </div>
  )
}

// ─── 목표 ───────────────────────────────────────────────────────
/**
 * 목표 이름 + "그래프를 정하는 값" (시작일 · 종료일 · 시작값 · 목표값).
 * 시작일을 미루면 루틴·할 일·마일스톤도 그 날 이후로 맞춰져요 (normalizePlan).
 * 마일스톤 날짜·수치는 이 값에서 자동으로 계산돼서, 그래프를 고치려고 마일스톤을 하나하나 맞출 필요가 없어요.
 */
function GoalForm({ plan, formId, onDone, onError }: FormProps<{ kind: 'goal' }>) {
  const { goal } = plan
  const [title, setTitle] = useState(goal.title)
  const [emoji, setEmoji] = useState(goal.emoji ?? '')
  const [startDate, setStartDate] = useState(goal.startDate)
  const [endDate, setEndDate] = useState(goal.endDate)
  const today = toYmd(new Date())
  const [startValue, setStartValue] = useState(goal.metric?.startValue.toString() ?? '')
  const [targetValue, setTargetValue] = useState(goal.metric?.targetValue.toString() ?? '')
  const metric = goal.metric
  return (
    <form
      id={formId}
      className="space-y-4"
      onSubmit={submitter(() => {
        if (!title.trim()) return onError('목표 이름을 입력해 주세요.')
        if (!startDate || startDate < today) return onError('시작일은 오늘 이후여야 해요.')
        if (!endDate || endDate <= startDate) return onError('종료일은 시작일보다 뒤여야 해요.')
        let metricValues: { startValue: number; targetValue: number } | null = null
        if (metric) {
          const sv = toNumber(startValue)
          const tv = toNumber(targetValue)
          if (sv === null || tv === null || Number.isNaN(sv) || Number.isNaN(tv))
            return onError('시작값과 목표값을 숫자로 입력해 주세요.')
          if (sv === tv) return onError('목표값이 시작값과 같아요.')
          metricValues = { startValue: sv, targetValue: tv }
        }
        onDone({ kind: 'goal', title: title.trim(), emoji: emoji.trim() || null, startDate, endDate, metricValues })
      })}
    >
      <EmojiTitle emoji={emoji} title={title} onEmoji={setEmoji} onTitle={setTitle} />
      <div className={cn('grid grid-cols-2 gap-3', metric ? 'sm:grid-cols-4' : 'sm:max-w-[400px]')}>
        <Field label="시작일">
          <input
            type="date"
            className={inputClass}
            value={startDate}
            min={today}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </Field>
        <Field label="종료일">
          <input
            type="date"
            className={inputClass}
            value={endDate}
            min={startDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </Field>
        {metric && (
          <>
            <Field label={`지금 ${metric.name} (${metric.unit})`}>
              <input
                className={inputClass}
                inputMode="decimal"
                value={startValue}
                onChange={(e) => setStartValue(e.target.value)}
              />
            </Field>
            <Field label={`목표 ${metric.name} (${metric.unit})`}>
              <input
                className={inputClass}
                inputMode="decimal"
                value={targetValue}
                onChange={(e) => setTargetValue(e.target.value)}
              />
            </Field>
          </>
        )}
      </div>
      <p className="text-ink-3 text-xs">
        마일스톤 날짜{metric ? '와 단계별 수치' : ''}는 이 값에 맞춰 자동으로 다시 계산돼요.
      </p>
    </form>
  )
}

// ─── 세부 목표 ──────────────────────────────────────────────────
function SubGoalForm({ target, plan, formId, onDone, onError }: FormProps<{ kind: 'subGoal'; index: number | null }>) {
  const current = target.index !== null ? plan.subGoals[target.index] : null
  const [title, setTitle] = useState(current?.title ?? '')
  const [emoji, setEmoji] = useState(current?.emoji ?? '')
  const [description, setDescription] = useState(current?.description ?? '')
  return (
    <form
      id={formId}
      className="space-y-4"
      onSubmit={submitter(() => {
        if (!title.trim()) return onError('제목을 입력해 주세요.')
        onDone({
          kind: 'subGoal',
          index: target.index,
          item: {
            tempId: current?.tempId ?? newTempId('sg'),
            metric: current?.metric ?? null,
            title: title.trim(),
            emoji: emoji.trim() || null,
            description: description.trim() || null,
          },
        })
      })}
    >
      <EmojiTitle emoji={emoji} title={title} onEmoji={setEmoji} onTitle={setTitle} placeholder="예) 식단 관리" />
      <Field label="설명 (선택)">
        <textarea
          rows={3}
          maxLength={200}
          className={textareaClass}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="예) 하루 섭취량 1,800kcal 이하 유지"
        />
      </Field>
    </form>
  )
}

// ─── 마일스톤 ───────────────────────────────────────────────────
function MilestoneForm({
  target,
  plan,
  formId,
  onDone,
  onError,
}: FormProps<{ kind: 'milestone'; index: number | null }>) {
  const current = target.index !== null ? plan.milestones[target.index] : null
  const [title, setTitle] = useState(current?.title ?? '')
  const [description, setDescription] = useState(current?.description ?? '')
  const [dueDate, setDueDate] = useState(current?.dueDate ?? '')
  const metric = plan.goal.metric
  // 마지막 단계는 항상 목표 종료일 (자동) — 날짜를 바꾸려면 목표의 종료일을 고쳐요
  const isLast = target.index !== null && target.index === plan.milestones.length - 1

  return (
    <form
      id={formId}
      className="space-y-4"
      onSubmit={submitter(() => {
        if (!title.trim()) return onError('제목을 입력해 주세요.')
        if (!dueDate) return onError('날짜를 정해 주세요.')
        if (dueDate < plan.goal.startDate || dueDate > plan.goal.endDate)
          return onError('목표 기간 안의 날짜를 골라 주세요.')
        onDone({
          kind: 'milestone',
          index: target.index,
          item: {
            tempId: current?.tempId ?? newTempId('ms'),
            title: title.trim(),
            description: description.trim() || null,
            dueDate,
            // 수치는 저장할 때 시작값→목표값 직선에서 자동 계산돼요 (normalizePlan)
            targetValue: current?.targetValue ?? null,
          },
        })
      })}
    >
      <Field label="제목">
        <input
          autoFocus
          className={inputClass}
          value={title}
          maxLength={LIMITS.title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예) 적응기: 습관 세팅"
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label={isLast ? '날짜 (목표 종료일)' : '날짜'}>
          <input
            type="date"
            className={inputClass}
            value={dueDate}
            min={plan.goal.startDate}
            max={plan.goal.endDate}
            disabled={isLast}
            onChange={(e) => setDueDate(e.target.value)}
          />
        </Field>
        {metric && (
          <div className="text-ink-3 self-end pb-2 text-xs leading-relaxed">
            이 시점 {metric.name}은(는) 날짜에 맞춰
            <br />
            자동으로 계산돼요
          </div>
        )}
      </div>
      <Field label="설명 (선택)">
        <textarea
          rows={2}
          maxLength={200}
          className={textareaClass}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
    </form>
  )
}

// ─── Task (루틴 / 일회성) ───────────────────────────────────────
function TaskForm({
  target,
  plan,
  formId,
  onDone,
  onError,
}: FormProps<{ kind: 'task'; index: number | null; taskType: PlanTask['type'] }>) {
  const current = target.index !== null ? plan.tasks[target.index] : null
  const routine = target.taskType === 'ROUTINE'
  const [limitMin, limitMax] = routine ? LIMITS.routineMinutes : LIMITS.oneTimeMinutes

  const [title, setTitle] = useState(current?.title ?? '')
  const [emoji, setEmoji] = useState(current?.emoji ?? '')
  const [minutes, setMinutes] = useState(String(current?.durationMinutes ?? (routine ? 20 : 30)))
  const [subGoalId, setSubGoalId] = useState(current?.subGoalTempId ?? plan.subGoals[0]?.tempId ?? '')
  // 루틴
  const [days, setDays] = useState<DayOfWeek[]>(current?.routineDays ?? [])
  const [startTime, setStartTime] = useState(current?.preferredStartTime ?? '')
  const [howTo, setHowTo] = useState(current?.description ?? '')
  // 일회성
  const [milestoneId, setMilestoneId] = useState(current?.milestoneTempId ?? '')
  const [scheduledDate, setScheduledDate] = useState(current?.scheduledDate ?? '')
  const [dueDate, setDueDate] = useState(current?.dueDate ?? '')

  const milestone = plan.milestones.find((m) => m.tempId === milestoneId)
  const maxDue = milestone?.dueDate ?? plan.goal.endDate

  const toggleDay = (d: DayOfWeek) => setDays((prev) => (prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d]))

  const submit = () => {
    if (!title.trim()) return onError('제목을 입력해 주세요.')
    const m = Number(minutes)
    if (!Number.isInteger(m) || m < limitMin || m > limitMax)
      return onError(`${routine ? '루틴' : '할 일'}은 ${limitMin}~${limitMax}분으로 설정해 주세요.`)

    // 제목에 쓴 시간("유산소 25분")은 걸리는 시간을 바꾸면 같이 바꿔요 (제목과 시간이 어긋나지 않게)
    const oldMinutes = current?.durationMinutes
    const syncedTitle =
      oldMinutes && oldMinutes !== m && title.includes(`${oldMinutes}분`)
        ? title.replace(`${oldMinutes}분`, `${m}분`)
        : title
    const base = {
      tempId: current?.tempId ?? newTempId('t'),
      type: target.taskType,
      subGoalTempId: subGoalId || null,
      title: syncedTitle.trim(),
      emoji: emoji.trim() || null,
      durationMinutes: m,
    }

    if (routine) {
      if (!days.length) return onError('요일을 하나 이상 골라 주세요.')
      return onDone({
        kind: 'task',
        index: target.index,
        item: {
          ...base,
          milestoneTempId: current?.milestoneTempId ?? null,
          routineDays: DAYS.filter((d) => days.includes(d)),
          preferredStartTime: startTime || null,
          startDate: current?.startDate ?? plan.goal.startDate,
          endDate: current?.endDate ?? null,
          scheduledDate: null,
          dueDate: null,
          description: howTo.trim() || null,
          reason: current?.reason ?? null,
        },
      })
    }

    if (!dueDate) return onError('마감일을 정해 주세요.')
    if (scheduledDate && scheduledDate > dueDate) return onError('실행일은 마감일보다 늦을 수 없어요.')
    if (dueDate > maxDue)
      return onError(
        milestone
          ? `마감일은 연결된 마일스톤(${formatMd(milestone.dueDate, false)})보다 늦을 수 없어요.`
          : '마감일은 목표 종료일보다 늦을 수 없어요.'
      )
    onDone({
      kind: 'task',
      index: target.index,
      item: {
        ...base,
        milestoneTempId: milestoneId || null,
        routineDays: null,
        preferredStartTime: null,
        startDate: null,
        endDate: null,
        scheduledDate: scheduledDate || null,
        dueDate,
      },
    })
  }

  return (
    <form id={formId} className="space-y-4" onSubmit={submitter(submit)}>
      <EmojiTitle
        emoji={emoji}
        title={title}
        onEmoji={setEmoji}
        onTitle={setTitle}
        placeholder={routine ? '예) 하체 근력 50분' : '예) 5km 기록 측정'}
      />

      <div className="grid grid-cols-2 gap-3">
        <Field label="걸리는 시간 (분)" hint={`${limitMin}~${limitMax}분`}>
          <input
            type="number"
            min={limitMin}
            max={limitMax}
            className={inputClass}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </Field>
        <Field label="세부 목표">
          <select className={inputClass} value={subGoalId} onChange={(e) => setSubGoalId(e.target.value)}>
            {plan.subGoals.map((sg) => (
              <option key={sg.tempId} value={sg.tempId}>
                {sg.emoji} {sg.title}
              </option>
            ))}
          </select>
        </Field>
      </div>

      {routine ? (
        <>
          <div>
            <span className="text-ink-2 mb-1.5 block text-[13px] font-semibold">반복 요일</span>
            <div className="flex flex-wrap gap-1.5">
              {DAYS.map((d) => (
                <Chip key={d} size="sm" variant="day" selected={days.includes(d)} onClick={() => toggleDay(d)} className="w-10">
                  {DAY_LABEL[d]}
                </Chip>
              ))}
            </div>
          </div>
          <Field label="시작 시각 (선택)">
            <input
              type="time"
              className={inputClass}
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
          </Field>
          <Field label="하는 방법 (선택)" hint={`${howTo.length}/${LIMITS.routineDescription}`}>
            <textarea
              rows={3}
              maxLength={LIMITS.routineDescription}
              className={textareaClass}
              value={howTo}
              placeholder="예) 스쿼트 4x10 → 런지 3x12(각) → 레그프레스 3x12, 세트 사이 60~90초 휴식"
              onChange={(e) => setHowTo(e.target.value)}
            />
          </Field>
        </>
      ) : (
        <>
          <Field label="마일스톤 (선택)">
            <select className={inputClass} value={milestoneId} onChange={(e) => setMilestoneId(e.target.value)}>
              <option value="">연결 안 함</option>
              {plan.milestones.map((ms) => (
                <option key={ms.tempId} value={ms.tempId}>
                  {ms.title} (~{formatMd(ms.dueDate, false)})
                </option>
              ))}
            </select>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="실행일 (선택)">
              <input
                type="date"
                className={inputClass}
                value={scheduledDate}
                min={plan.goal.startDate}
                max={dueDate || maxDue}
                onChange={(e) => setScheduledDate(e.target.value)}
              />
            </Field>
            <Field label="마감일">
              <input
                type="date"
                className={inputClass}
                value={dueDate}
                min={scheduledDate || plan.goal.startDate}
                max={maxDue}
                onChange={(e) => setDueDate(e.target.value)}
              />
            </Field>
          </div>
        </>
      )}
    </form>
  )
}

// ─── 그 자리 편집 (모달 대신) ──────────────────────────────────────

function titleOf(target: EditTarget): string {
  const isNew = 'index' in target && target.index === null
  const name =
    target.kind === 'goal'
      ? '목표'
      : target.kind === 'subGoal'
        ? '세부 목표'
        : target.kind === 'milestone'
          ? '마일스톤'
          : target.taskType === 'ROUTINE'
            ? '루틴'
            : '할 일'
  return `${name} ${isNew ? '추가' : '수정'}`
}

/** 두 편집 대상이 같은 항목인지 (카드가 "이 줄을 편집기로 바꿀지" 판단할 때) */
export function sameTarget(a: EditTarget | null, b: EditTarget): boolean {
  if (!a || a.kind !== b.kind) return false
  if (a.kind === 'goal') return true
  if (a.kind === 'task' && b.kind === 'task' && a.taskType !== b.taskType) return false
  return (a as { index: number | null }).index === (b as { index: number | null }).index
}

interface InlineItemEditorProps {
  target: EditTarget
  plan: GoalPlan
  onClose: () => void
  onSave: (result: EditResult) => void
  /** 기존 항목 삭제 */
  onDelete?: () => void
  /** 최소 개수 등으로 지울 수 없을 때 이유 */
  deleteDisabledReason?: string | null
  className?: string
  /** 분석용 알림 (저장 · 취소 · 입력 오류 · 삭제) */
  analytics?: {
    onSaved?: (method: 'button' | 'enter') => void
    onCancel?: (method: 'esc' | 'cancel_button') => void
    onError?: (message: string) => void
    onDeleteClick?: () => void
    onDeleteBlocked?: (message: string) => void
    onDeleteConfirm?: () => void
  }
}

/**
 * 초안 항목을 그 자리에서 고치는 편집기 (노션처럼).
 * - Enter 저장 (여러 줄 칸은 Shift+Enter 로 줄바꿈), Esc 취소
 * - 삭제는 같은 자리에서 한 번 더 확인
 */
export function InlineItemEditor({
  target,
  plan,
  onClose,
  onSave,
  onDelete,
  deleteDisabledReason,
  className,
  analytics,
}: InlineItemEditorProps) {
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const formId = 'plan-item-form'
  // 이번 저장이 Enter 로 된 건지 (form 제출만으론 구분이 안 돼서 keydown 에서 표시해 둬요)
  const byEnter = useRef(false)

  const done = (result: EditResult) => {
    analytics?.onSaved?.(byEnter.current ? 'enter' : 'button')
    byEnter.current = false
    setError(null)
    onSave(result)
  }
  const showError = (message: string) => {
    byEnter.current = false
    analytics?.onError?.(message)
    setError(message)
  }
  const formProps = { plan, formId, onDone: done, onError: showError }
  const canDelete = 'index' in target && target.index !== null && !!onDelete

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.nativeEvent.isComposing) return
    if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      if (confirmDelete) setConfirmDelete(false)
      else {
        analytics?.onCancel?.('esc')
        onClose()
      }
      return
    }
    if (e.key === 'Enter' && !e.shiftKey) byEnter.current = true
    // 여러 줄 칸에서도 Enter 는 저장 (Shift+Enter 는 줄바꿈)
    if (e.key === 'Enter' && !e.shiftKey && e.target instanceof HTMLTextAreaElement) {
      e.preventDefault()
      ;(document.getElementById(formId) as HTMLFormElement | null)?.requestSubmit()
    }
  }

  return (
    <div
      onKeyDown={onKeyDown}
      className={cn('border-border-subtle bg-brand-soft/30 rounded-2xl border p-4 text-left', className)}
      role="group"
      aria-label={titleOf(target)}
    >
      <p className="text-text-brand mb-3 text-[13px] font-bold">{titleOf(target)}</p>
      {target.kind === 'goal' && <GoalForm target={target} {...formProps} />}
      {target.kind === 'subGoal' && <SubGoalForm target={target} {...formProps} />}
      {target.kind === 'milestone' && <MilestoneForm target={target} {...formProps} />}
      {target.kind === 'task' && <TaskForm target={target} {...formProps} />}
      {error && (
        <p role="alert" className="text-danger mt-3 text-sm font-medium">
          {error}
        </p>
      )}

      {confirmDelete ? (
        <div className="border-border-subtle bg-danger-soft/60 mt-4 flex flex-wrap items-center gap-2 rounded-xl border px-3 py-2.5">
          <p className="min-w-0 flex-1 text-[14px] font-semibold">이 항목을 초안에서 뺄까요?</p>
          <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>
            취소
          </Button>
          <Button
            variant="danger"
            size="sm"
            autoFocus
            onClick={() => {
              analytics?.onDeleteConfirm?.()
              onDelete?.()
            }}
          >
            삭제
          </Button>
        </div>
      ) : (
        <div className="mt-4 flex flex-wrap items-center gap-2">
          {canDelete && (
            <Button
              variant="ghost"
              size="sm"
              className="text-danger hover:bg-danger-soft"
              onClick={() => {
                analytics?.onDeleteClick?.()
                if (deleteDisabledReason) {
                  analytics?.onDeleteBlocked?.(deleteDisabledReason)
                  return setError(deleteDisabledReason)
                }
                setError(null)
                setConfirmDelete(true)
              }}
            >
              <Trash2 className="size-4" />
              삭제
            </Button>
          )}
          <span className="text-ink-4 ml-auto hidden text-[12px] sm:inline">Enter 저장 · Esc 취소</span>
          <Button
            variant="ghost"
            size="sm"
            className="max-sm:ml-auto"
            onClick={() => {
              analytics?.onCancel?.('cancel_button')
              onClose()
            }}
          >
            취소
          </Button>
          <Button size="sm" type="submit" form={formId}>
            저장
          </Button>
        </div>
      )}
    </div>
  )
}
