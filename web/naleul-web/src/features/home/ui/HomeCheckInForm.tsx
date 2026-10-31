'use client'

import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ArrowRight } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import type { ReflectionMood } from '@/features/goal/api'
import { inlineInput } from '@/features/goal/edit/inline'
import { MOODS } from '@/features/goal/ui/progress/DailyCheckInCard'
import { useHomeCheckIn, type HomeCheckIn } from '../api'
import { errorStatusOf, HE, useHomeAnalytics } from '../analytics'
import type { DiscardTracker } from './TodayRetro'

const NOTE_MAX = 500
const fmt = (v: number) => String(Number(v.toFixed(2)))

/** 받침에 맞는 조사: 체중은 / 점수는 */
const josa = (word: string, withFinal: string, without: string) => {
  const c = word.charCodeAt(word.length - 1)
  if (c < 0xac00 || c > 0xd7a3) return `${word}${without}`
  return `${word}${(c - 0xac00) % 28 ? withFinal : without}`
}

/**
 * 홈 회고 패널의 목표형 칸 — 목표 상세 "오늘 기록"과 같은 기록이에요 (PUT /goal-categories/{id}/check-in).
 *  - 수치 목표면 그날 수치 (예: 64kg)
 *  - 오늘 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
 *  - 한 줄 회고
 * 이미 남긴 기록이 있으면 채워진 채로 열리고, 저장하면 덮어써요.
 */
export function HomeCheckInForm({
  goalCategoryId,
  goalName,
  date,
  checkIn,
  prompt,
  goalMode,
  tracker,
  onSaved,
  onSkip,
}: {
  goalCategoryId: number
  goalName: string
  date: string
  checkIn: HomeCheckIn
  prompt: string
  goalMode?: string | null
  /** 분석: 입력 시작 · 버려짐 (회고 패널이 내려줘요) */
  tracker?: DiscardTracker
  onSaved: () => void
  /** noteLength: 건너뛸 때 한 줄 회고 칸에 남아 있던 글자 수 */
  onSkip: (noteLength: number) => void
}) {
  const { trackHome } = useHomeAnalytics()
  const save = useHomeCheckIn()
  const had = checkIn.value != null || checkIn.mood != null || !!checkIn.note
  const [value, setValue] = useState(checkIn.value != null ? fmt(checkIn.value) : '')
  const [mood, setMood] = useState<ReflectionMood | null>(checkIn.mood)
  const [note, setNote] = useState(checkIn.note ?? '')
  const name = checkIn.metricName || '수치'

  const valueNum = value.trim() === '' ? null : Number(value)
  const valueOk = valueNum == null || Number.isFinite(valueNum)
  // 처음 쓰는 날은 하나라도 채워야 해요 (이미 남긴 기록은 비워서 지울 수도 있어요)
  const hasSomething = (checkIn.hasMetric && valueNum != null) || !!mood || !!note.trim() || had
  const canSave = valueOk && hasSomething && !save.isPending

  // ── 분석 ──
  // 입력값이 처음과 달라졌는지 (저장하면 깨끗해져요) — 이 칸이 사라질 때 남아 있으면 "버려짐"
  const dirty = useRef(false)
  const latest = useRef({ noteLength: 0 })
  useEffect(() => {
    latest.current.noteLength = note.trim().length
  }, [note])
  const touch = () => {
    if (!dirty.current) tracker?.inputStart(goalCategoryId, 'checkin', goalMode ?? null)
    dirty.current = true
  }
  // '숫자로 입력해 주세요'가 처음 보일 때 (폼마다 1회)
  const invalidSent = useRef(false)
  useEffect(() => {
    if (valueOk || invalidSent.current) return
    invalidSent.current = true
    trackHome(HE.checkinValueInvalid, { goal_category_id: goalCategoryId })
  }, [valueOk, goalCategoryId, trackHome])
  useEffect(
    () => () => {
      if (!dirty.current || !tracker) return
      // 패널째 닫히면 패널이 정한 이유, 아니면 (목표형) 다른 목표로 바뀐 것
      trackHome(HE.retroDraftDiscard, {
        discard_reason: tracker.panelClosing.current ? tracker.reasonNow() : 'goal_switch',
        content_length: latest.current.noteLength,
        form_type: 'checkin',
        goal_category_id: goalCategoryId,
      })
    },
    // 사라질 때 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  )

  const submit = (method: 'button' | 'enter', e?: FormEvent) => {
    e?.preventDefault()
    if (!canSave) return
    trackHome(HE.checkinSaveClick, {
      goal_category_id: goalCategoryId,
      has_metric: checkIn.hasMetric,
      has_value: checkIn.hasMetric && valueNum != null,
      mood: mood ?? undefined,
      note_length: note.trim().length,
      is_edit: had,
      submit_method: method,
    })
    save.mutate(
      {
        goalCategoryId,
        date,
        value: checkIn.hasMetric ? valueNum : null,
        mood,
        note: note.trim() || null,
      },
      {
        onSuccess: () => {
          dirty.current = false
          trackHome(HE.retroSaveResult, { retro_action: 'checkin', result: 'success', goal_category_id: goalCategoryId })
          onSaved()
        },
        onError: (err) =>
          trackHome(HE.retroSaveResult, {
            retro_action: 'checkin',
            result: 'fail',
            error_status: errorStatusOf(err),
            goal_category_id: goalCategoryId,
          }),
      }
    )
  }

  return (
    <form onSubmit={(e) => submit('button', e)} className="border-border-subtle bg-brand-soft/30 mt-3 space-y-3 rounded-2xl border p-3">
      {checkIn.hasMetric && (
        <label className="block">
          <span className="text-ink-2 mb-1 block text-[13px] font-semibold">{josa(name, '은', '는')} 얼마예요?</span>
          <div className="relative max-w-[200px]">
            <input
              inputMode="decimal"
              value={value}
              onChange={(e) => {
                touch()
                setValue(e.target.value.replace(/[^0-9.-]/g, ''))
              }}
              placeholder={checkIn.currentValue != null ? fmt(checkIn.currentValue) : ''}
              aria-label={`${goalName} ${name} 값`}
              className={cn(inlineInput, 'bg-surface h-10 pr-12 text-[16px] font-bold')}
            />
            {checkIn.unit && (
              <span className="text-ink-3 pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-[13px]">
                {checkIn.unit}
              </span>
            )}
          </div>
          {!valueOk && <span className="text-danger mt-1 block text-[12px]">숫자로 입력해 주세요</span>}
        </label>
      )}

      <div>
        <span className="text-ink-2 mb-1 block text-[13px] font-semibold">어땠어요?</span>
        <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label={`${goalName} 어땠어요`}>
          {MOODS.map((o) => (
            <button
              key={o.value}
              type="button"
              role="radio"
              aria-checked={mood === o.value}
              onClick={() => {
                const off = mood === o.value
                touch()
                trackHome(HE.checkinMoodSelect, { mood: o.value, mood_action: off ? 'deselect' : 'select' })
                setMood(off ? null : o.value)
              }}
              className={cn(
                'flex h-8 items-center gap-1 rounded-full border px-2.5 text-[12px] font-semibold transition-colors',
                mood === o.value
                  ? 'border-brand bg-brand text-white'
                  : 'border-border-subtle bg-surface text-ink-2 hover:border-brand'
              )}
            >
              <span aria-hidden>{o.emoji}</span>
              {o.label}
            </button>
          ))}
        </div>
      </div>

      <label className="block">
        <span className="text-ink-2 mb-1 block text-[13px] font-semibold">무엇을 했나요? (한 줄 회고)</span>
        <textarea
          value={note}
          onChange={(e) => {
            touch()
            setNote(e.target.value.slice(0, NOTE_MAX))
          }}
          onKeyDown={(e) => {
            // Enter 저장, Shift+Enter 줄바꿈
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit('enter')
            }
          }}
          rows={3}
          aria-label={`${goalName} 한 줄 회고`}
          placeholder={prompt}
          className={cn(inlineInput, 'bg-surface h-auto resize-none py-2 text-[14px] leading-relaxed')}
        />
      </label>

      <div className="flex items-center gap-2">
        <button type="button" onClick={() => onSkip(note.trim().length)} className="text-ink-3 hover:text-ink text-[13px] font-semibold">
          건너뛰기
        </button>
        <Button type="submit" size="sm" className="ml-auto" loading={save.isPending} disabled={!canSave}>
          {had ? '고치고 다음' : '저장하고 다음'}
          <ArrowRight className="size-3.5" />
        </Button>
      </div>
      <p className="text-ink-4 text-[11px]">목표 상세의 &lsquo;오늘 기록&rsquo;과 같은 기록이에요 · Enter 저장</p>
    </form>
  )
}
