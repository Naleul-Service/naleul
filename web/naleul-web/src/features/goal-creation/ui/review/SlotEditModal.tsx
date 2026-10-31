'use client'

import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Field, inputClass, textareaClass, toNumber } from '../formParts'
import { Plus, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { DAY_LABEL, DAYS } from '../../constants'
import type { DayOfWeek, GoalSlots, SlotKey, SlotsPatch } from '../../types'

// ─── 공용 입력 요소 ─────────────────────────────────────────────

/** yyyy-MM-dd (사용자 PC 시간대 기준) */
function ymd(date: Date) {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}
function addDays(days: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d
}

// ─── 슬롯별 폼 ──────────────────────────────────────────────────
// 각 폼은 "검증 통과 시 patch 반환, 실패 시 에러 문구 반환" 하는 build 함수를 가져요.

type BuildResult = { patch: SlotsPatch } | { error: string }

interface FormProps {
  slots: GoalSlots
  formId: string
  onSubmit: (result: BuildResult) => void
}

function useSubmit(onSubmit: FormProps['onSubmit'], build: () => BuildResult) {
  return (e: FormEvent) => {
    e.preventDefault()
    onSubmit(build())
  }
}

function GoalStatementForm({ slots, formId, onSubmit }: FormProps) {
  const [value, setValue] = useState(slots.goalStatement.value ?? '')
  const submit = useSubmit(onSubmit, () => {
    const v = value.trim()
    if (!v) return { error: '목표를 입력해 주세요.' }
    return { patch: { goalStatement: v } }
  })
  return (
    <form id={formId} onSubmit={submit}>
      <Field label="이루고 싶은 목표">
        <input
          autoFocus
          className={inputClass}
          value={value}
          maxLength={100}
          onChange={(e) => setValue(e.target.value)}
          placeholder="예) 몸무게 10kg 감량"
        />
      </Field>
    </form>
  )
}

function MetricForm({ slots, formId, onSubmit }: FormProps) {
  const m = slots.metric.value
  const [name, setName] = useState(m?.name ?? '')
  const [current, setCurrent] = useState(m?.currentValue?.toString() ?? '')
  const [target, setTarget] = useState(m?.targetValue?.toString() ?? '')
  const [unit, setUnit] = useState(m?.unit ?? '')

  const submit = useSubmit(onSubmit, () => {
    if (!name.trim()) return { error: '무엇을 잴지 이름을 입력해 주세요. (예: 체중)' }
    const c = toNumber(current)
    const t = toNumber(target)
    if (Number.isNaN(c) || Number.isNaN(t)) return { error: '수치는 숫자로 입력해 주세요.' }
    if (t === null) return { error: '목표 수치를 입력해 주세요.' }
    return { patch: { metric: { name: name.trim(), currentValue: c, targetValue: t, unit: unit.trim() || null } } }
  })

  return (
    <form id={formId} onSubmit={submit} className="space-y-4">
      <div className="grid grid-cols-[1fr_96px] gap-3">
        <Field label="무엇을 잴까요?">
          <input
            autoFocus
            className={inputClass}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="체중"
          />
        </Field>
        <Field label="단위">
          <input className={inputClass} value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="kg" />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="지금">
          <input
            className={inputClass}
            inputMode="decimal"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            placeholder="78"
          />
        </Field>
        <Field label="목표">
          <input
            className={inputClass}
            inputMode="decimal"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
            placeholder="68"
          />
        </Field>
      </div>
    </form>
  )
}

function MotivationForm({ slots, formId, onSubmit }: FormProps) {
  const [value, setValue] = useState(slots.motivation.value ?? '')
  const submit = useSubmit(onSubmit, () => {
    const v = value.trim()
    if (!v) return { error: '이유를 입력해 주세요.' }
    return { patch: { motivation: v } }
  })
  return (
    <form id={formId} onSubmit={submit}>
      <Field label="왜 이루고 싶나요?" hint="이유가 구체적일수록 계획 문구에 동기 부여가 잘 담겨요.">
        <textarea
          autoFocus
          rows={4}
          maxLength={300}
          className={textareaClass}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="예) 건강검진에서 지방간이 나와서"
        />
      </Field>
    </form>
  )
}

function DeadlineForm({ slots, formId, onSubmit }: FormProps) {
  const d = slots.deadline.value
  const min = ymd(addDays(1))
  const max = ymd(addDays(365 * 2))
  const today = ymd(addDays(0))
  const [startDate, setStartDate] = useState(d?.startDate ?? '')
  const [endDate, setEndDate] = useState(d?.endDate ?? '')
  const [flexible, setFlexible] = useState(d?.isFlexible ?? false)

  const submit = useSubmit(onSubmit, () => {
    if (!endDate) return { error: '날짜를 골라 주세요.' }
    if (endDate < min) return { error: '오늘 이후 날짜를 골라 주세요.' }
    if (endDate > max) return { error: '기한은 최대 2년까지 정할 수 있어요.' }
    if (startDate && startDate < today) return { error: '시작일은 오늘부터 고를 수 있어요.' }
    if (startDate && endDate <= startDate) return { error: '마감일은 시작일 뒤여야 해요.' }
    return { patch: { deadline: { endDate, isFlexible: flexible, startDate: startDate || null } } }
  })

  return (
    <form id={formId} onSubmit={submit} className="space-y-4">
      <Field label="언제부터 시작할까요? (비우면 오늘부터)">
        <input
          type="date"
          className={inputClass}
          value={startDate}
          min={today}
          max={max}
          onChange={(e) => setStartDate(e.target.value)}
        />
      </Field>
      <Field label="언제까지 이루고 싶나요?">
        <input
          autoFocus
          type="date"
          className={inputClass}
          value={endDate}
          min={min}
          max={max}
          onChange={(e) => setEndDate(e.target.value)}
        />
      </Field>
      <label className="flex cursor-pointer items-center gap-2.5 text-sm">
        <input
          type="checkbox"
          checked={flexible}
          onChange={(e) => setFlexible(e.target.checked)}
          className="accent-brand size-4"
        />
        상황에 따라 조금 늦어져도 괜찮아요
      </label>
    </form>
  )
}

interface TimeRange {
  start: string
  end: string
}

function PracticeForm({ slots, formId, onSubmit }: FormProps) {
  const p = slots.practicePreference.value
  const [days, setDays] = useState<DayOfWeek[]>(p?.preferredDays ?? [])
  const [ranges, setRanges] = useState<TimeRange[]>(p?.preferredTimeRanges ?? [])
  const [unavailable, setUnavailable] = useState(p?.unavailableNote ?? '')
  const [workStyle, setWorkStyle] = useState(p?.workStyleAnswer ?? '')

  const toggleDay = (day: DayOfWeek) =>
    setDays((prev) => (prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]))
  const updateRange = (i: number, patch: Partial<TimeRange>) =>
    setRanges((prev) => prev.map((r, idx) => (idx === i ? { ...r, ...patch } : r)))

  const submit = useSubmit(onSubmit, () => {
    for (const r of ranges) {
      if (!r.start || !r.end) return { error: '시간대의 시작·끝 시각을 모두 정해 주세요.' }
      if (r.start >= r.end) return { error: '끝 시각은 시작 시각보다 늦어야 해요.' }
    }
    return {
      patch: {
        practicePreference: {
          // 요일 순서를 월→일로 정렬해서 보내요
          preferredDays: days.length ? DAYS.filter((d) => days.includes(d)) : null,
          preferredTimeRanges: ranges.length ? ranges : null,
          unavailableNote: unavailable.trim() || null,
          workStyleAnswer: workStyle.trim() || null,
        },
      },
    }
  })

  return (
    <form id={formId} onSubmit={submit} className="space-y-5">
      <div>
        <span className="text-ink-2 mb-1.5 block text-[13px] font-semibold">실천하기 좋은 요일</span>
        <div className="flex flex-wrap gap-1.5">
          {DAYS.map((day) => (
            <Chip key={day} size="sm" variant="day" selected={days.includes(day)} onClick={() => toggleDay(day)} className="w-10">
              {DAY_LABEL[day]}
            </Chip>
          ))}
        </div>
      </div>

      <div>
        <span className="text-ink-2 mb-1.5 block text-[13px] font-semibold">시간대</span>
        <div className="space-y-2">
          {ranges.map((r, i) => (
            <div key={i} className="flex items-center gap-2">
              <input
                type="time"
                className={inputClass}
                value={r.start}
                onChange={(e) => updateRange(i, { start: e.target.value })}
                aria-label={`${i + 1}번째 시간대 시작`}
              />
              <span className="text-ink-3">~</span>
              <input
                type="time"
                className={inputClass}
                value={r.end}
                onChange={(e) => updateRange(i, { end: e.target.value })}
                aria-label={`${i + 1}번째 시간대 끝`}
              />
              <button
                type="button"
                onClick={() => setRanges((prev) => prev.filter((_, idx) => idx !== i))}
                aria-label="시간대 삭제"
                className="text-ink-3 hover:bg-subtle hover:text-ink shrink-0 rounded-lg p-2"
              >
                <X className="size-4" />
              </button>
            </div>
          ))}
          {ranges.length < 3 && (
            <button
              type="button"
              onClick={() => setRanges((prev) => [...prev, { start: '19:00', end: '21:00' }])}
              className="text-text-brand inline-flex items-center gap-1 text-sm font-semibold"
            >
              <Plus className="size-4" />
              시간대 추가
            </button>
          )}
        </div>
      </div>

      <Field label="안 되는 시간 (선택)">
        <input
          className={inputClass}
          value={unavailable}
          maxLength={100}
          onChange={(e) => setUnavailable(e.target.value)}
          placeholder="예) 평일 9~18시 회사"
        />
      </Field>
      <Field label="평소 일하는 방식 (선택)">
        <input
          className={inputClass}
          value={workStyle}
          maxLength={100}
          onChange={(e) => setWorkStyle(e.target.value)}
          placeholder="예) 미리미리 나눠서 하는 편"
        />
      </Field>
    </form>
  )
}

function ApproachForm({ slots, formId, onSubmit }: FormProps) {
  const [value, setValue] = useState(slots.approach?.value ?? '')
  const submit = useSubmit(onSubmit, () => {
    const v = value.trim()
    if (!v) return { error: '어떻게 하고 싶은지 적어 주세요. 정한 게 없으면 "정한 계획 없음"이라고 적어도 돼요.' }
    return { patch: { approach: v } }
  })
  return (
    <form id={formId} onSubmit={submit}>
      <Field
        label="어떻게 이루고 싶나요?"
        hint="이미 정해 둔 순서 · 교재 · 꼭 할 일을 적으면 그대로 계획의 뼈대가 돼요."
      >
        <textarea
          autoFocus
          rows={4}
          maxLength={500}
          className={textareaClass}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="예) 2주는 이론 강의, 남은 기간은 기출 5회독"
        />
      </Field>
    </form>
  )
}

// ─── 모달 ─────────────────────────────────────────────────────

const TITLES: Record<SlotKey, string> = {
  goalStatement: '목표 수정',
  metric: '수치 수정',
  motivation: '이유 수정',
  deadline: '기한 수정',
  approach: '계획 수정',
  practicePreference: '실천 시간 수정',
}

const FORMS: Record<SlotKey, (props: FormProps) => ReactNode> = {
  goalStatement: GoalStatementForm,
  metric: MetricForm,
  motivation: MotivationForm,
  deadline: DeadlineForm,
  approach: ApproachForm,
  practicePreference: PracticeForm,
}

interface SlotEditModalProps {
  slot: SlotKey | null
  slots: GoalSlots
  saving: boolean
  /** 서버에서 거절된 이유 (400 등) */
  serverError: string | null
  /** method: 취소 버튼 · X · 바깥 · Esc (분석용) */
  onClose: (method: ModalCloseMethod | 'cancel_button') => void
  onSave: (patch: SlotsPatch) => void
  /** 분석: '저장'을 눌렀을 때 (Enter 로 제출해도) */
  onSaveClick?: (method: 'button' | 'enter') => void
  /** 분석: 화면 검사에서 막혔을 때 (앱이 정한 오류 문구) */
  onValidationError?: (message: string) => void
}

export function SlotEditModal({
  slot,
  slots,
  saving,
  serverError,
  onClose,
  onSave,
  onSaveClick,
  onValidationError,
}: SlotEditModalProps) {
  const [localError, setLocalError] = useState<string | null>(null)
  const formId = 'slot-edit-form'
  // 이번 제출이 입력칸 Enter 로 된 건지 (form 제출만으론 구분이 안 돼서 keydown 에서 표시해 둬요)
  const byEnter = useRef(false)

  const close = (method: ModalCloseMethod | 'cancel_button') => {
    setLocalError(null)
    onClose(method)
  }

  const handleResult = (result: BuildResult) => {
    onSaveClick?.(byEnter.current ? 'enter' : 'button')
    byEnter.current = false
    if ('error' in result) {
      onValidationError?.(result.error)
      setLocalError(result.error)
      return
    }
    setLocalError(null)
    onSave(result.patch)
  }

  const Form = slot ? FORMS[slot] : null
  const error = localError ?? serverError

  return (
    <Modal
      open={!!slot}
      onClose={close}
      title={slot ? TITLES[slot] : undefined}
      dismissible={!saving}
      footer={
        <>
          <Button variant="secondary" onClick={() => close('cancel_button')} disabled={saving}>
            취소
          </Button>
          <Button type="submit" form={formId} loading={saving}>
            저장
          </Button>
        </>
      }
    >
      {/* key: 다른 항목을 열 때마다 폼 상태를 새로 시작 */}
      <div
        onKeyDownCapture={(e) => {
          if (e.key === 'Enter' && !e.nativeEvent.isComposing && e.target instanceof HTMLInputElement)
            byEnter.current = true
        }}
      >
        {Form && <Form key={slot} slots={slots} formId={formId} onSubmit={handleResult} />}
      </div>
      {error && (
        <p role="alert" className="text-danger mt-3 text-sm font-medium">
          {error}
        </p>
      )}
    </Modal>
  )
}
