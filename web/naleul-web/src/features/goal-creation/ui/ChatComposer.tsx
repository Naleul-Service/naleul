'use client'

import { useLayoutEffect, useRef, type KeyboardEvent } from 'react'
import { ArrowUp } from '@/components/icons'
import { cn } from '@/lib/cn'

/** 서버 USER_ANSWER_MAX_LENGTH 와 같아요 (마지막 "더 알려줄 정보" 질문은 길게 적을 수 있게 600) */
const MAX_LENGTH = 600

interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  /** method: Enter 로 보냈는지 버튼으로 보냈는지 (분석용) */
  onSubmit: (method: 'enter' | 'button') => void
  disabled?: boolean
  placeholder?: string
  autoFocus?: boolean
}

/**
 * 채팅 입력창. Enter 전송, Shift+Enter 줄바꿈, 내용에 맞춰 높이가 늘어나요.
 *
 * ⚠️ 한글 입력 주의: 한글은 "조합 중"(ㅎ→하→한) 상태에서 Enter 를 누르면
 * keydown 이 한 번 더 발생해요. 조합 중 Enter 를 무시하지 않으면
 * 마지막 글자가 한 번 더 전송되는 버그가 생겨요. → isComposing 으로 막아요.
 */
export function ChatComposer({ value, onChange, onSubmit, disabled, placeholder, autoFocus }: ChatComposerProps) {
  const ref = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }, [value])

  const canSend = !disabled && value.trim().length > 0

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== 'Enter' || e.shiftKey) return
    if (e.nativeEvent.isComposing) return
    e.preventDefault()
    if (canSend) onSubmit('enter')
  }

  const nearLimit = value.length > MAX_LENGTH - 50

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        if (canSend) onSubmit('button')
      }}
      className={cn(
        // 디자인 시스템 ChatComposer — 최소 높이 52 · 모서리 26 · 6/6/6/18
        'border-border-subtle bg-bg-surface rounded-composer flex min-h-[52px] items-end gap-2 border p-1.5 pl-[18px] transition-colors',
        'focus-within:border-border-focus',
        disabled && 'bg-disabled-bg'
      )}
    >
      <textarea
        ref={ref}
        rows={1}
        value={value}
        maxLength={MAX_LENGTH}
        autoFocus={autoFocus}
        disabled={disabled}
        placeholder={placeholder ?? '답변을 입력하세요'}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        aria-label="답변 입력"
        className="placeholder:text-text-placeholder max-h-40 min-h-10 flex-1 resize-none bg-transparent text-body-lg py-2 outline-none disabled:cursor-not-allowed"
      />
      {nearLimit && (
        <span className="text-ink-3 self-center text-xs tabular-nums">
          {value.length}/{MAX_LENGTH}
        </span>
      )}
      <button
        type="submit"
        disabled={!canSend}
        aria-label="보내기"
        className="bg-action-primary-bg text-action-primary-fg hover:bg-action-primary-bg-hover disabled:bg-border-subtle disabled:text-text-tertiary grid size-10 shrink-0 place-items-center rounded-full transition-colors"
      >
        <ArrowUp className="size-5" />
      </button>
    </form>
  )
}
