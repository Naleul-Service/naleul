'use client'

import { useState, type KeyboardEvent, type Ref } from 'react'
import { Check, Plus, X } from '@/components/icons'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'

export const MUST_DO_MAX = 5
const ITEM_MAX = 30

const clean = (v: string) =>
  v
    .trim()
    .replace(/\s{2,}/g, ' ')
    .slice(0, ITEM_MAX)

/**
 * 고른 일 + 입력창에 적어 두고 아직 추가 안 한 일 → 실제로 보낼 목록.
 * 하나만 할 거면 Enter·추가 없이 입력창에 적기만 해도 들어가요.
 */
export function withDraft(value: string[], draft: string): string[] {
  const d = clean(draft)
  const all = d && !value.includes(d) ? [...value, d] : value
  return all.slice(0, MUST_DO_MAX)
}

/**
 * "이 목표를 위해 꼭 해야 할 일" 고르기 — 추천 칩을 누르거나 입력창에 적어요.
 * 예) 다이어트 → 헬스 · 식단 기록. 고른 일은 계획 AI 가 반드시 루틴으로 넣어요.
 *  - 하나만: 입력창에 적기만 하면 돼요 (부모가 withDraft 로 함께 보내요)
 *  - 여러 개: "+ 추가"(또는 Enter)로 칩을 만들고 다음 걸 적어요
 * "적기만 한 것"도 보내려면 부모가 draft 를 알아야 해서 draft/onDraftChange 를 받을 수 있어요.
 */
export function MustDoPicker({
  value,
  onChange,
  suggestions = [],
  disabled,
  draft: draftProp,
  onDraftChange,
  inputRef,
  surface,
  sessionId,
}: {
  value: string[]
  onChange: (next: string[]) => void
  suggestions?: string[]
  disabled?: boolean
  draft?: string
  onDraftChange?: (v: string) => void
  inputRef?: Ref<HTMLInputElement>
  /** 분석: 이 부품이 쓰인 화면 (goal_new 시작 화면 / goal_review 정리 확인의 꼭 할 일 창). 있으면 이벤트를 보내요 */
  surface?: 'goal_new' | 'goal_review'
  sessionId?: number
}) {
  const [innerDraft, setInnerDraft] = useState('')
  const draft = draftProp ?? innerDraft
  const setDraft = onDraftChange ?? setInnerDraft
  const full = value.length >= MUST_DO_MAX
  const has = (v: string) => value.includes(v)

  const toggle = (v: string) => {
    const removing = has(v)
    if (!removing && full) return
    if (surface) {
      track('must_do_picker.toggle', {
        surface,
        must_do_action: removing ? 'remove' : 'add',
        item_source: suggestions.includes(v) ? 'suggestion' : 'custom',
        must_do_count: value.length + (removing ? -1 : 1),
        session_id: sessionId,
      })
    }
    if (removing) onChange(value.filter((x) => x !== v))
    else onChange([...value, v])
  }
  const addDraft = (method: 'button' | 'enter') => {
    const v = clean(draft)
    const added = !!v && !has(v) && !full
    // 이미 있는 일 · 빈 글은 칩이 안 생기고 입력칸만 비워져요 (조용히 무시) → 실제로 들어간 것만 셉니다
    if (surface && added) {
      track('must_do_picker.add', {
        surface,
        submit_method: method,
        must_do_count: value.length + 1,
        session_id: sessionId,
      })
    }
    if (added) onChange([...value, v])
    setDraft('')
  }
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter' || e.nativeEvent.isComposing) return
    e.preventDefault()
    addDraft('enter')
  }

  // 추천에 없는, 직접 적은 일
  const custom = value.filter((v) => !suggestions.includes(v))

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {suggestions.map((s) => {
          const on = has(s)
          return (
            <button
              key={s}
              type="button"
              onClick={() => toggle(s)}
              disabled={disabled || (!on && full)}
              aria-pressed={on}
              className={cn(
                'inline-flex h-9 items-center gap-1 rounded-full border px-3.5 text-[14px] font-medium transition-colors disabled:opacity-40',
                on ? 'border-brand bg-brand-soft text-text-brand' : 'border-border-subtle text-ink-2 hover:border-line-strong'
              )}
            >
              {on && <Check className="size-3.5" />}
              {s}
            </button>
          )
        })}
        {custom.map((s) => (
          <span
            key={s}
            className="border-brand bg-brand-soft text-text-brand inline-flex h-9 items-center gap-1 rounded-full border pr-1.5 pl-3.5 text-[14px] font-medium"
          >
            {s}
            <button
              type="button"
              onClick={() => toggle(s)}
              disabled={disabled}
              aria-label={`${s} 빼기`}
              className="hover:bg-brand/10 rounded-full p-0.5"
            >
              <X className="size-3.5" />
            </button>
          </span>
        ))}
      </div>

      <div className="border-border-subtle bg-surface focus-within:border-border-focus mt-3 flex items-center gap-2 rounded-xl border px-3">
        <input
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKey}
          maxLength={ITEM_MAX}
          disabled={disabled || full}
          placeholder={
            full
              ? `최대 ${MUST_DO_MAX}개까지 고를 수 있어요`
              : value.length
                ? '하나 더 있다면 적고 + 추가'
                : '직접 적어 주세요 (예: 주말 등산)'
          }
          aria-label="꼭 하고 싶은 일 직접 적기"
          className="placeholder:text-text-placeholder h-10 min-w-0 flex-1 bg-transparent text-[14px] outline-none"
        />
        <button
          type="button"
          onClick={() => addDraft('button')}
          disabled={disabled || full || !draft.trim()}
          className="text-ink-2 hover:text-ink inline-flex items-center gap-1 text-[13px] font-semibold disabled:opacity-40"
        >
          <Plus className="size-3.5" />
          추가
        </button>
      </div>
    </div>
  )
}
