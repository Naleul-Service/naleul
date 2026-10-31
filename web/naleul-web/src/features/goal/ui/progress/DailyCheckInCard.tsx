'use client'

import { useEffect, useRef, useState, type FormEvent } from 'react'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import Link from 'next/link'
import { PencilLine } from 'lucide-react'
import { NotebookPen } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import {
  useCheckIn,
  useGoalProgress,
  type GoalCategory,
  type GoalProgress,
  type GoalReflection,
  type ReflectionMood,
} from '../../api'
import { inlineInput } from '../../edit/inline'
import { AIBadge } from '@/components/ui/AIBadge'

/** "말로 고치기" 입력창에 문장을 채워 넣어요 (GoalAiEditBar 가 듣고 있어요) */
export const AI_EDIT_PREFILL_EVENT = 'goal-ai-edit:prefill'
export const prefillAiEdit = (text: string) =>
  window.dispatchEvent(new CustomEvent<string>(AI_EDIT_PREFILL_EVENT, { detail: text }))

export const MOODS: { value: ReflectionMood; emoji: string; label: string }[] = [
  { value: 'GOOD', emoji: '😊', label: '잘했어요' },
  { value: 'OK', emoji: '🙂', label: '그럭저럭' },
  { value: 'BAD', emoji: '😣', label: '아쉬워요' },
]
export const moodOf = (m: ReflectionMood | null | undefined) => MOODS.find((x) => x.value === m)

const fmt = (v: number) => String(Number(v.toFixed(2)))
const md = (iso: string) => `${Number(iso.slice(5, 7))}월 ${Number(iso.slice(8, 10))}일`
const NOTE_MAX = 500

/** 받침에 맞는 조사: 체중은 / 점수는 */
const josa = (word: string, withFinal: string, without: string) => {
  const c = word.charCodeAt(word.length - 1)
  if (c < 0xac00 || c > 0xd7a3) return `${word}${without}`
  return `${word}${(c - 0xac00) % 28 ? withFinal : without}`
}

/**
 * 목표 상세 맨 위 "오늘 기록" — 사용자가 직접 채우는 칸이라는 게 한눈에 보이게 진행 요약 바로 아래 크게 둬요.
 *  - 수치 목표면 오늘 수치 (예: 64kg)
 *  - 오늘 어땠는지 (잘했어요 · 그럭저럭 · 아쉬워요)
 *  - 무엇을 했는지 한 줄 회고 (예: "점심에 마라탕 먹음")
 * 남긴 기록은 "말로 고치기" AI 가 계획을 조정할 때 근거로 써요.
 */
export function DailyCheckInCard({ goal }: { goal: GoalCategory }) {
  const { data: p, isPending } = useGoalProgress(goal.goalCategoryId)
  if (isPending) {
    return (
      <section className="border-border-subtle bg-surface grid h-32 place-items-center rounded-card border">
        <Spinner className="text-ink-3 size-5" />
      </section>
    )
  }
  if (!p) return null
  // 오늘 기록이 바뀌면 입력칸 초기값도 다시
  const today = todayOf(p)
  return (
    <CheckInBody
      key={`${today.value ?? ''}|${today.reflection?.note ?? ''}|${today.reflection?.mood ?? ''}`}
      goal={goal}
      p={p}
    />
  )
}

function todayOf(p: GoalProgress) {
  const value = p.metric?.logs.find((l) => l.date === p.today)?.value ?? null
  const reflection = p.reflections?.find((r) => r.date === p.today) ?? null
  return { value, reflection }
}

function CheckInBody({ goal, p }: { goal: GoalCategory; p: GoalProgress }) {
  const m = p.metric
  const checkIn = useCheckIn(goal.goalCategoryId)
  const today = todayOf(p)
  const done = today.value != null || !!today.reflection
  const [editing, setEditing] = useState(!done)
  const [value, setValue] = useState(today.value != null ? fmt(today.value) : '')
  const [mood, setMood] = useState<ReflectionMood | null>(today.reflection?.mood ?? null)
  const [note, setNote] = useState(today.reflection?.note ?? '')
  const name = m?.name || '수치'
  const recent = (p.reflections ?? []).filter((r) => r.date !== p.today).slice(0, 3)

  const valueNum = value.trim() === '' ? null : Number(value)
  const valueOk = valueNum == null || Number.isFinite(valueNum)
  const hasSomething = (m && valueNum != null) || !!mood || !!note.trim() || !!today.reflection
  const canSave = valueOk && hasSomething && !checkIn.isPending
  const goalId = goal.goalCategoryId

  // [UX] '숫자로 입력해 주세요'가 보일 때
  useEffect(() => {
    if (!valueOk) track('goal_detail.checkin_invalid', { goal_category_id: goalId })
  }, [valueOk, goalId])

  /** 마지막 저장 시도가 Enter 키였는지 — keydown 에서 켜고 submit 에서 읽어요 */
  const byEnter = useRef(false)

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    const method = byEnter.current ? 'enter' : 'button'
    byEnter.current = false
    if (!canSave) return
    track('goal_detail.checkin_submit', {
      goal_category_id: goalId,
      has_value: !!m && valueNum != null,
      mood: mood ?? null,
      note_length: note.trim().length,
      is_edit: done,
      submit_method: method,
    })
    const result = (err?: unknown) =>
      track('goal_detail.checkin_result', {
        goal_category_id: goalId,
        result: err === undefined ? 'success' : 'fail',
        error_status: err === undefined ? undefined : errorStatusOf(err),
      })
    checkIn.mutate(
      { value: m ? valueNum : null, mood, note: note.trim() || null },
      {
        onSuccess: () => {
          result()
          setEditing(false)
        },
        onError: (err) => result(err),
      }
    )
  }

  return (
    <section
      aria-labelledby="checkin-title"
      className={cn(
        'rounded-card border px-4 py-4 sm:px-5',
        editing ? 'border-border-focus bg-brand-soft/40' : 'border-border-subtle bg-surface'
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="bg-brand grid size-8 shrink-0 place-items-center rounded-full text-white">
          <NotebookPen className="size-4" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="checkin-title" className="text-[16px] font-bold">
            오늘 기록 <span className="text-ink-3 text-[13px] font-medium">{md(p.today)}</span>
          </h2>
          <p className="text-ink-3 text-[12px]">
            {m ? `${name}와(과) 오늘 한 일을 남기면` : '오늘 한 일을 남기면'} 그래프와 AI 조정에 바로 반영돼요
          </p>
        </div>
        {!editing && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              track('goal_detail.checkin_edit_click', { goal_category_id: goalId })
              setEditing(true)
            }}
          >
            <PencilLine className="size-3.5" />
            수정
          </Button>
        )}
      </div>

      {!editing ? (
        <TodaySummary
          goal={goal}
          value={today.value}
          unit={m?.unit ?? null}
          name={name}
          reflection={today.reflection}
        />
      ) : (
        <form
          onSubmit={submit}
          onKeyDown={(e) => {
            byEnter.current = e.key === 'Enter' && !e.nativeEvent.isComposing
          }}
          className="mt-3 space-y-3"
        >
          {m && (
            <label className="block">
              <span className="text-ink-2 mb-1 block text-[13px] font-semibold">
                오늘 {josa(name, '은', '는')} 얼마예요?
              </span>
              <div className="relative max-w-[220px]">
                <input
                  inputMode="decimal"
                  value={value}
                  onChange={(e) => setValue(e.target.value.replace(/[^0-9.-]/g, ''))}
                  placeholder={fmt(m.currentValue)}
                  aria-label={`${name} 값`}
                  className={cn(inlineInput, 'bg-surface h-11 pr-12 text-[17px] font-bold')}
                />
                {m.unit && (
                  <span className="text-ink-3 pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-[13px]">
                    {m.unit}
                  </span>
                )}
              </div>
            </label>
          )}

          <div>
            <span className="text-ink-2 mb-1 block text-[13px] font-semibold">오늘 어땠어요?</span>
            <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="오늘 어땠어요">
              {MOODS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  role="radio"
                  aria-checked={mood === o.value}
                  onClick={() => {
                    const deselect = mood === o.value
                    track('goal_detail.checkin_mood_select', {
                      goal_category_id: goalId,
                      mood: o.value,
                      mood_action: deselect ? 'deselect' : 'select',
                    })
                    setMood(deselect ? null : o.value)
                  }}
                  className={cn(
                    'flex h-9 items-center gap-1.5 rounded-full border px-3 text-[13px] font-semibold transition-colors',
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
              onChange={(e) => setNote(e.target.value.slice(0, NOTE_MAX))}
              onKeyDown={(e) => {
                // Enter 저장, Shift+Enter 줄바꿈
                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault()
                  byEnter.current = true
                  submit()
                }
              }}
              rows={2}
              placeholder={
                m
                  ? '예) 점심에 마라탕 먹음, 저녁은 샐러드 · 30분 걸음'
                  : '예) 영어 단어 50개 외움, 저녁 약속 때문에 인강은 못 들음'
              }
              className={cn(inlineInput, 'bg-surface h-auto min-h-[64px] resize-y py-2 leading-relaxed')}
            />
          </label>

          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" variant="brand" loading={checkIn.isPending} disabled={!canSave}>
              기록하기
            </Button>
            {done && (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  track('goal_detail.checkin_cancel_click', { goal_category_id: goalId })
                  setEditing(false)
                }}
              >
                취소
              </Button>
            )}
            {!valueOk && <span className="text-danger text-[12px]">숫자로 입력해 주세요</span>}
            <span className="text-ink-4 ml-auto text-[11px]">Enter 저장 · Shift+Enter 줄바꿈</span>
          </div>
        </form>
      )}

      {recent.length > 0 && (
        <RecentReflections goalId={goal.goalCategoryId} list={recent} logs={m?.logs ?? []} unit={m?.unit ?? null} />
      )}
    </section>
  )
}

function TodaySummary({
  goal,
  value,
  unit,
  name,
  reflection,
}: {
  goal: GoalCategory
  value: number | null
  unit: string | null
  name: string
  reflection: GoalReflection | null
}) {
  const mood = moodOf(reflection?.mood)
  return (
    <div className="mt-3 flex flex-wrap items-start gap-x-4 gap-y-2">
      {value != null && (
        <p className="text-[15px]">
          <span className="text-ink-3 text-[13px]">{name} </span>
          <b className="text-[18px]">
            {fmt(value)}
            {unit ?? ''}
          </b>
        </p>
      )}
      {mood && (
        <span className="bg-subtle rounded-full px-2.5 py-1 text-[13px] font-semibold">
          {mood.emoji} {mood.label}
        </span>
      )}
      {reflection?.note && (
        <p className="text-ink-2 min-w-0 basis-full text-[14px] leading-relaxed whitespace-pre-wrap">
          {reflection.note}
        </p>
      )}
      <AdjustHint goal={goal} />
    </div>
  )
}

/** 기록을 남긴 뒤 — 쌓인 기록으로 계획을 조정받을 수 있다는 걸 알려줘요 */
function AdjustHint({ goal }: { goal: GoalCategory }) {
  return (
    <button
      type="button"
      onClick={() => {
        // 말로 고치기 칸은 임시·완료 목표엔 없어요 (GoalDetailView 와 같은 조건)
        track('goal_detail.checkin_adjust_hint_click', {
          goal_category_id: goal.goalCategoryId,
          has_ai_edit: !goal.temporary && goal.goalCategoryStatus !== 'COMPLETED',
        })
        prefillAiEdit('최근 기록과 회고를 보고 계획을 조정해줘')
      }}
      className="text-text-brand hover:bg-action-ghost-hover flex items-center gap-1.5 rounded-lg px-2 py-1 text-[12px] font-semibold"
    >
      <AIBadge size="sm" />
      최근 기록 보고 계획 조정받기
    </button>
  )
}

function RecentReflections({
  goalId,
  list,
  logs,
  unit,
}: {
  goalId: number
  list: GoalReflection[]
  logs: { date: string; value: number }[]
  unit: string | null
}) {
  return (
    <div className="border-line mt-4 border-t pt-3">
      <div className="mb-1.5 flex items-center">
        <p className="text-ink-3 text-[12px] font-semibold">최근 기록</p>
        <Link
          href={`/goal/${goalId}?tab=reflections`}
          onClick={() => track('goal_detail.recent_records_all_click', { goal_category_id: goalId })}
          className="text-text-brand ml-auto text-[12px] font-semibold hover:underline"
        >
          모두 보기
        </Link>
      </div>
      <ul className="space-y-1">
        {list.map((r) => {
          const v = logs.find((l) => l.date === r.date)?.value
          const mood = moodOf(r.mood)
          return (
            <li key={r.date} className="flex items-baseline gap-2 text-[13px]">
              <span className="text-ink-3 w-[52px] shrink-0">{md(r.date).replace('월 ', '/').replace('일', '')}</span>
              {mood && <span aria-label={mood.label}>{mood.emoji}</span>}
              {v != null && (
                <b className="shrink-0">
                  {fmt(v)}
                  {unit ?? ''}
                </b>
              )}
              <span className="text-ink-2 min-w-0 flex-1 truncate">{r.note}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
