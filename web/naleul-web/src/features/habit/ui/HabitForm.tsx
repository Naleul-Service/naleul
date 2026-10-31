'use client'

import { useEffect, useRef, useState } from 'react'
import { BellOff } from 'lucide-react'
import { Bell, CalendarClock, X } from '@/components/icons'
import { DaysPicker, Field, InlineForm, inlineInput, type InlineSubmitMethod } from '@/features/goal/edit/inline'
import { track } from '@/lib/client/analytics'
import { JAVA_DAYS } from '@/features/goal/format'
import type { JavaDayOfWeek } from '@/features/goal/api'
import type { Habit, HabitInput } from '../api'

/** 빠르게 고를 수 있는 기본 습관 — 누르면 바로 추가돼요 */
export const HABIT_SUGGESTIONS: { emoji: string; name: string; repeatDays?: JavaDayOfWeek[] }[] = [
  { emoji: '💊', name: '영양제 먹기' },
  { emoji: '💧', name: '물 2L 마시기' },
  { emoji: '🚿', name: '샤워하기' },
  { emoji: '🧹', name: '집안일 하기' },
  { emoji: '🦷', name: '치실하기' },
  { emoji: '📓', name: '일기 쓰기' },
  { emoji: '🧘', name: '스트레칭 5분' },
  { emoji: '🚭', name: '금연 지키기' },
  { emoji: '🍺', name: '금주 지키기' },
  { emoji: '🥗', name: '식단 지키기' },
  { emoji: '🌙', name: '자정 전에 자기' },
  { emoji: '🗑️', name: '분리수거', repeatDays: ['WEDNESDAY', 'SUNDAY'] },
]

/**
 * 습관 추가 · 수정 폼. 반복 요일을 하나도 안 고르면 매일, 알림 시각은 선택.
 * Enter 로 저장, Esc 로 닫기.
 */
export function HabitForm({
  initial,
  saving,
  onSubmit,
  onCancel,
  submitLabel,
  extra,
}: {
  initial?: Habit
  saving: boolean
  onSubmit: (v: HabitInput & { name: string }) => void
  onCancel: () => void
  submitLabel: string
  extra?: React.ReactNode
}) {
  const [emoji, setEmoji] = useState(initial?.emoji ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  // 매일이면 화면에선 7일 모두 켠 상태로 보여줘요 (DaysPicker 의 "매일" 칩이 켜지게)
  const [days, setDays] = useState<JavaDayOfWeek[]>(initial && !initial.daily ? initial.repeatDays : [...JAVA_DAYS])
  const [remind, setRemind] = useState(initial?.remindTime ?? '')
  const [start, setStart] = useState(initial?.startTime ?? '')
  const [end, setEnd] = useState(initial?.endTime ?? '')
  const value = name.trim()
  const hasTime = !!start || !!end
  const error =
    days.length === 0
      ? '요일을 하나 이상 골라 주세요.'
      : hasTime && (!start || !end)
        ? '시간대는 시작과 끝을 함께 정해 주세요.'
        : hasTime && end <= start
          ? '끝나는 시간이 시작보다 뒤여야 해요.'
          : null

  const isNew = !initial
  const shownError = value ? error : null

  // [UX] 빨간 문구가 새로 보일 때마다 한 번 (같은 문구가 계속 떠 있으면 다시 보내지 않아요)
  const lastError = useRef<string | null>(null)
  useEffect(() => {
    if (shownError && shownError !== lastError.current) {
      track('habit.edit_error', { is_new: isNew, validation_error: shownError })
    }
    lastError.current = shownError
  }, [shownError, isNew])

  const submit = (method: InlineSubmitMethod) => {
    track('habit.save_click', {
      is_new: isNew,
      habit_id: initial?.habitId,
      repeat_day_count: days.length,
      has_time: !!start && !!end,
      has_remind: !!remind,
      submit_method: method,
    })
    onSubmit({
      name: value,
      emoji: emoji.trim(),
      repeatDays: days.length === 7 ? [] : days,
      ...(remind ? { remindTime: remind } : initial?.remindTime ? { clearRemindTime: true } : {}),
      ...(start && end ? { startTime: start, endTime: end } : initial?.startTime ? { clearTimeRange: true } : {}),
    })
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={(method) => {
        track('habit.edit_cancel', { is_new: isNew, close_method: method })
        onCancel()
      }}
      saving={saving}
      valid={!!value && !error}
      error={shownError}
      submitLabel={submitLabel}
      extra={extra}
    >
      <div className="grid grid-cols-[64px_minmax(0,1fr)] gap-2">
        <Field label="이모지">
          <input
            value={emoji}
            onChange={(e) => setEmoji(e.target.value)}
            maxLength={16}
            placeholder="✅"
            className={`${inlineInput} text-center placeholder:opacity-40`}
          />
        </Field>
        <Field label="습관">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={30}
            placeholder="예: 영양제 먹기"
            className={inlineInput}
            data-autofocus
          />
        </Field>
      </div>
      <div>
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">언제 하나요?</span>
        <DaysPicker value={days} onChange={setDays} />
      </div>
      <div>
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">하는 시간 (선택)</span>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <CalendarClock className="text-ink-3 pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
            <input
              type="time"
              value={start}
              onChange={(e) => setStart(e.target.value)}
              aria-label="시작 시각"
              className={`${inlineInput} w-[150px] pl-9`}
            />
          </div>
          <span className="text-ink-3">~</span>
          <input
            type="time"
            value={end}
            onChange={(e) => setEnd(e.target.value)}
            aria-label="끝 시각"
            className={`${inlineInput} w-[120px]`}
          />
          {hasTime && (
            <button
              type="button"
              onClick={() => {
                setStart('')
                setEnd('')
              }}
              className="text-ink-3 hover:text-ink flex items-center gap-1 text-[13px] font-medium"
            >
              <X className="size-3.5" />
              지우기
            </button>
          )}
        </div>
        <p className="text-ink-4 mt-1 text-[12px]">
          정하면 캘린더에 그 시간이 블록으로 보이고, 자동 배치가 그 시간은 비워 둬요. (예: 샤워 20:00 ~ 20:30)
        </p>
      </div>
      <div>
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">알림 (선택)</span>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <Bell className="text-ink-3 pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
            <input
              type="time"
              value={remind}
              onChange={(e) => setRemind(e.target.value)}
              aria-label="알림 시각"
              className={`${inlineInput} w-[150px] pl-9`}
            />
          </div>
          {remind && (
            <button
              type="button"
              onClick={() => setRemind('')}
              className="text-ink-3 hover:text-ink flex items-center gap-1 text-[13px] font-medium"
            >
              <BellOff className="size-3.5" />
              알림 끄기
            </button>
          )}
        </div>
        <p className="text-ink-4 mt-1 text-[12px]">그 시각까지 체크하지 않았을 때만 앱으로 알려드려요.</p>
      </div>
    </InlineForm>
  )
}
