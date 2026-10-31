'use client'

import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { CalendarRange, Infinity as InfinityIcon } from 'lucide-react'
import { CalendarDays, Repeat, X } from '@/components/icons'
import { Chip } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import type { ChangeScope } from '../api'
import type { TimetableSurface } from '../analytics'

/** 범위를 묻는 대상 (scope_chooser.* scope_target) — 루틴 / 고정 시간 / 수면 기상 / 수면 취침 */
export type ScopeTarget = 'routine' | 'fixed' | 'sleep_wake' | 'sleep_bed'
/** 고르지 않고 '이날만'으로 저장된 이유 (scope_chooser.auto_day auto_reason) */
export type ScopeAutoReason = 'outside_click' | 'focus_out' | 'next_drag' | 'leave_page'

export interface ScopeOption {
  scope: ChangeScope
  label: string
  hint: string
  /** 고를 수 없는 이유 (있으면 비활성) */
  disabledReason?: string
}

/**
 * 고를 수 있는 범위.
 * @param kind      루틴 / 고정 시간
 * @param sameDay   원래 날짜 그대로인지 (루틴을 다른 날로 옮기면 이날만)
 * @param crossesMidnight 새 시간이 자정을 넘기는지 (루틴은 자정을 넘겨도 "앞으로"가 돼요 — 12시간까지)
 * @param weekdayLabel 고정 시간: "앞으로 ○마다"에 넣을 요일 이름 (예: "수요일", "토요일 아침"). 있으면 요일별 옵션이 생겨요
 */
export function scopeOptions({
  kind,
  isToday,
  sameDay = true,
  weekdayLabel,
}: {
  kind: 'routine' | 'fixed'
  isToday: boolean
  sameDay?: boolean
  crossesMidnight?: boolean
  weekdayLabel?: string
}): ScopeOption[] {
  if (kind === 'fixed' && weekdayLabel) {
    return [
      { scope: 'DAY', label: isToday ? '오늘만' : '이날만', hint: '이 하루만 바뀌어요' },
      { scope: 'WEEK', label: '이번 주', hint: '이날부터 이번 주 일요일까지' },
      {
        scope: 'WEEKDAY',
        label: `앞으로 ${weekdayLabel}마다`,
        hint: '이 요일만 기본 시간이 바뀌어요 (지난 기록은 그대로)',
      },
      {
        scope: 'FOLLOWING',
        label: '앞으로 매일',
        hint: '모든 요일이 이 시간으로 (요일별로 다르게 정한 시간도 맞춰요)',
      },
    ]
  }
  const otherDay = kind === 'routine' && !sameDay ? '다른 날로 옮길 때는 이날만 바꿀 수 있어요' : undefined
  return [
    { scope: 'DAY', label: isToday ? '오늘만' : '이날만', hint: '이 하루만 바뀌어요' },
    {
      scope: 'WEEK',
      label: '이번 주',
      hint: '이날부터 이번 주 일요일까지',
      disabledReason: otherDay,
    },
    {
      scope: 'FOLLOWING',
      label: '앞으로 계속',
      hint: kind === 'routine' ? '남은 루틴 전부 + 루틴 기본 시간' : '기본 시간이 바뀌어요 (지난 기록은 그대로)',
      disabledReason: otherDay,
    },
  ]
}

const ICON = { DAY: CalendarDays, WEEK: CalendarRange, WEEKDAY: Repeat, FOLLOWING: InfinityIcon } as const

/** 시간 직접 입력 창 안에서 쓰는 범위 고르기 (알약 버튼) */
export function ScopeChips({
  options,
  value,
  onChange,
}: {
  options: ScopeOption[]
  value: ChangeScope
  onChange: (s: ChangeScope) => void
}) {
  const current = options.find((o) => o.scope === value)
  return (
    <div>
      <span className="text-ink-3 mb-1 block text-xs font-medium">적용 범위</span>
      <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="적용 범위">
        {options.map((o) => (
          <Chip
            key={o.scope}
            size="sm"
            role="radio"
            aria-checked={value === o.scope}
            selected={value === o.scope}
            disabled={!!o.disabledReason}
            title={o.disabledReason ?? o.hint}
            onClick={() => onChange(o.scope)}
          >
            {o.label}
          </Chip>
        ))}
      </div>
      <p className="text-ink-3 mt-1 text-xs">
        {options.find((o) => o.disabledReason)?.disabledReason ?? current?.hint}
      </p>
    </div>
  )
}

/**
 * 드래그로 루틴·고정 시간을 옮긴 직후 화면 아래에 뜨는 선택 바.
 * 1 / 2 / 3 (/ 4) 키 또는 버튼으로 고르고, Enter = 첫 번째(이날만), Esc·X = 취소(원래 자리로).
 * 아무것도 고르지 않고 바깥을 누르거나 포커스가 빠져나가면 onDismiss (= "이날만"으로 저장).
 */
export function ScopeChooser({
  title,
  options,
  loading,
  onChoose,
  onCancel,
  onDismiss,
  surface,
  scopeTarget,
}: {
  title: string
  options: ScopeOption[]
  loading?: boolean
  onChoose: (s: ChangeScope) => void
  onCancel: () => void
  /** 고르지 않고 바깥을 누르거나 포커스가 빠져나감 (없으면 아무것도 안 함) — reason 은 분석용 */
  onDismiss?: (reason: ScopeAutoReason) => void
  /** 분석: 이 바가 쓰인 화면 */
  surface?: TimetableSurface
  /** 분석: 범위를 묻는 대상 */
  scopeTarget?: ScopeTarget
}) {
  const firstRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  // Enter 로 버튼을 눌렀는지 (keydown 에서 켜고 click 에서 읽어요)
  const enterRef = useRef(false)

  // ── 분석 (scope_chooser.*) ──
  const base = { surface: surface ?? 'calendar', scope_target: scopeTarget }
  const choose = (scope: ChangeScope, method: 'button' | 'enter' | 'number_key') => {
    track('scope_chooser.choose', { ...base, change_scope: scope, submit_method: method })
    onChoose(scope)
  }
  const cancel = (method: 'x_button' | 'esc') => {
    track('scope_chooser.cancel', { ...base, close_method: method })
    onCancel()
  }
  const dismiss = (reason: ScopeAutoReason) => {
    if (!onDismiss) return
    track('scope_chooser.auto_day', { ...base, auto_reason: reason })
    onDismiss(reason)
  }
  // 바가 뜰 때 한 번 (같은 변경이면 다시 그려도 한 번만)
  const needsScope = options.some((o) => o.scope !== 'DAY' && !o.disabledReason)
  const viewed = useRef(false)
  useEffect(() => {
    if (viewed.current) return
    viewed.current = true
    track('scope_chooser.view', { surface: surface ?? 'calendar', scope_target: scopeTarget, needs_scope: needsScope })
  }, [surface, scopeTarget, needsScope])

  // 렌더마다 새 함수여도 키보드 핸들러가 최신 값을 쓰도록
  const latest = useRef({ options, choose, cancel, dismiss, loading })
  useEffect(() => {
    latest.current = { options, choose, cancel, dismiss, loading }
  })

  // 바깥 클릭 · 포커스 이동 → "이날만" (사용자가 그냥 다른 일을 하러 가면 바꾼 대로 두는 게 자연스러워요)
  useEffect(() => {
    const outside = (target: EventTarget | null) =>
      target instanceof Node && !!dialogRef.current && !dialogRef.current.contains(target)
    const onPointerDown = (e: PointerEvent) => {
      // 다른 블록을 끌려고 누른 경우도 여기서 먼저 "이날만"으로 저장돼요
      if (!outside(e.target) || latest.current.loading) return
      const onBlock =
        e.target instanceof Element && !!e.target.closest('[data-task-id], [data-fixed-id], [data-resize-handle]')
      latest.current.dismiss(onBlock ? 'next_drag' : 'outside_click')
    }
    const onFocusIn = (e: FocusEvent) => {
      if (outside(e.target) && !latest.current.loading) latest.current.dismiss('focus_out')
    }
    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('focusin', onFocusIn)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true)
      document.removeEventListener('focusin', onFocusIn)
    }
  }, [])

  useEffect(() => {
    firstRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      const { options: opts, choose, cancel, loading: busy } = latest.current
      if (busy) return
      if (e.key === 'Escape') {
        e.preventDefault()
        cancel('esc')
      } else if (e.key === 'Enter') {
        // 포커스된 버튼의 click 이 바로 이어져요 → 그 click 에서 'enter' 로 읽어요
        enterRef.current = true
        window.setTimeout(() => (enterRef.current = false), 0)
      } else if (['1', '2', '3', '4'].includes(e.key)) {
        const o = opts[Number(e.key) - 1]
        if (o && !o.disabledReason) choose(o.scope, 'number_key')
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  if (typeof document === 'undefined') return null

  return createPortal(
    <div className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--tabbar-h)+16px)] z-50 flex justify-center px-4">
      <div
        ref={dialogRef}
        role="dialog"
        aria-label="적용 범위 고르기"
        className="bg-bg-surface shadow-pop pointer-events-auto w-full max-w-[560px] animate-[modal-in_160ms_ease-out] rounded-[20px] px-6 pt-5 pb-4"
      >
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <p className="text-h3 text-text-primary">{title}</p>
            <p className="text-body-sm text-text-secondary mt-1">어디까지 바꿀까요?</p>
          </div>
          <button
            type="button"
            onClick={() => cancel('x_button')}
            disabled={loading}
            aria-label="취소하고 원래대로"
            className="text-ink-3 hover:bg-subtle hover:text-ink -mt-1 -mr-1 rounded-lg p-1.5"
          >
            <X className="size-4" />
          </button>
        </div>
        <div className={cn('mt-3 grid gap-2', options.length > 3 ? 'grid-cols-2 sm:grid-cols-4' : 'grid-cols-3')}>
          {options.map((o, i) => {
            const Icon = ICON[o.scope]
            return (
              <button
                key={o.scope}
                ref={i === 0 ? firstRef : undefined}
                type="button"
                disabled={!!o.disabledReason || loading}
                title={o.disabledReason}
                onClick={() => {
                  const method = enterRef.current ? 'enter' : 'button'
                  enterRef.current = false
                  choose(o.scope, method)
                }}
                className={cn(
                  'border-border-subtle hover:border-brand hover:bg-brand-soft focus-visible:border-border-focus focus-visible:bg-brand-soft flex flex-col items-start gap-1 rounded-input border px-3 py-2.5 text-left transition-colors outline-none',
                  'disabled:hover:border-line-strong disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent'
                )}
              >
                <span className="flex w-full items-center gap-1.5 text-[14px] font-bold">
                  <Icon className="text-text-brand size-4" />
                  {o.label}
                  <kbd className="text-ink-4 ml-auto hidden text-[10px] font-medium sm:inline">{i + 1}</kbd>
                </span>
                <span className="text-ink-3 text-[11px] leading-snug">{o.disabledReason ?? o.hint}</span>
              </button>
            )
          })}
        </div>
      </div>
    </div>,
    document.body
  )
}
