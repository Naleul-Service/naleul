'use client'

import { createContext, useContext, useEffect, useRef, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { Pencil, Plus, Trash2 } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import type { UserColor } from '@/features/color/api'
import type { JavaDayOfWeek } from '../api'
import { JAVA_DAYS, JAVA_DAY_LABEL } from '../format'

/**
 * 목표 상세의 "그 자리에서 바로 고치기" 부품들.
 *
 * - 모달 대신 항목이 있던 자리가 입력창으로 바뀌어요.
 * - Enter = 저장, Esc = 취소. (여러 줄 입력칸은 Shift+Enter 로 줄바꿈)
 * - 한 번에 하나만 열려요 — 다른 걸 열면 앞의 입력창은 닫혀요 (EditingProvider).
 */

// ─── 지금 열려 있는 입력창 (페이지에 하나) ─────────────────────────

interface EditingCtx {
  /** 예: "goal", "sub:12", "sub:new", "routine:3", "milestone:new", "task:91", "del:routine:3" */
  editing: string | null
  open: (key: string) => void
  close: () => void
}

export const EditingContext = createContext<EditingCtx>({ editing: null, open: () => {}, close: () => {} })

export function useEditing(key: string) {
  const ctx = useContext(EditingContext)
  return {
    isOpen: ctx.editing === key,
    open: () => ctx.open(key),
    close: ctx.close,
    /** 다른 키 열기 (예: 수정 → 삭제 확인) */
    openOther: ctx.open,
    current: ctx.editing,
  }
}

// ─── 입력 폼 ───────────────────────────────────────────────

export const inlineInput =
  'border-border-subtle bg-bg-surface placeholder:text-text-placeholder focus:border-border-focus text-body-md h-10 w-full min-w-0 rounded-input border px-3 outline-none transition-colors'

export function Field({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={cn('block min-w-0', className)}>
      <span className="text-ink-3 mb-1 block text-[12px] font-medium">{label}</span>
      {children}
    </label>
  )
}

/** 입력창을 닫은 방법 (분석용 close_method) */
export type InlineCloseMethod = 'esc' | 'cancel_button'
/** 저장한 방법 (분석용 submit_method) */
export type InlineSubmitMethod = 'button' | 'enter'

interface InlineFormProps {
  onSubmit: (method: InlineSubmitMethod) => void
  onCancel: (method: InlineCloseMethod) => void
  saving?: boolean
  /** false 면 저장 버튼 비활성 (Enter 도 무시) */
  valid?: boolean
  error?: string | null
  submitLabel?: string
  /** 왼쪽 아래 (예: 삭제 버튼) */
  extra?: ReactNode
  className?: string
  children: ReactNode
}

/**
 * 그 자리에 펼쳐지는 입력창.
 * 첫 입력칸에 자동으로 커서가 가요. Enter 로 저장, Esc 로 취소.
 */
export function InlineForm({
  onSubmit,
  onCancel,
  saving,
  valid = true,
  error,
  submitLabel = '저장',
  extra,
  className,
  children,
}: InlineFormProps) {
  const ref = useRef<HTMLFormElement>(null)
  /** 마지막 저장 시도가 Enter 키였는지 — keydown 에서 켜고 submit 에서 읽어요 */
  const byEnter = useRef(false)

  useEffect(() => {
    const root = ref.current
    const el =
      root?.querySelector<HTMLElement>('[data-autofocus]') ??
      root?.querySelector<HTMLElement>('input:not([type=hidden]), textarea, select')
    el?.focus()
    if (el instanceof HTMLInputElement && el.type === 'text') el.select()
  }, [])

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    const method: InlineSubmitMethod = byEnter.current ? 'enter' : 'button'
    byEnter.current = false
    if (valid && !saving) onSubmit(method)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLFormElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      if (!saving) onCancel('esc')
    }
    byEnter.current = e.key === 'Enter' && !e.nativeEvent.isComposing
    // 한글 조합 중 Enter 는 글자 확정용 — 저장하지 않아요 (마지막 글자가 두 번 들어가는 문제 방지)
    if (e.key === 'Enter' && e.nativeEvent.isComposing) {
      e.preventDefault()
      return
    }
    // textarea 는 Enter = 저장, Shift+Enter = 줄바꿈
    if (e.key === 'Enter' && !e.shiftKey && e.target instanceof HTMLTextAreaElement) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <form
      ref={ref}
      onSubmit={submit}
      onKeyDown={onKeyDown}
      className={cn(
        'border-border-subtle bg-brand-soft/40 animate-[fade-in_120ms_ease-out] rounded-2xl border p-3.5 sm:p-4',
        className
      )}
    >
      <div className="space-y-3">{children}</div>
      {error && <p className="text-danger mt-2 text-xs">{error}</p>}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {extra}
        <span className="text-ink-4 ml-auto hidden text-[11px] sm:inline">Enter 저장 · Esc 취소</span>
        <Button type="button" variant="ghost" size="sm" onClick={() => onCancel('cancel_button')} disabled={saving}>
          취소
        </Button>
        <Button type="submit" size="sm" loading={saving} disabled={!valid}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}

// ─── 삭제 확인 (그 줄 안에서) ───────────────────────────────────

export function InlineConfirm({
  message,
  detail,
  onConfirm,
  onCancel,
  loading,
  confirmLabel = '삭제',
}: {
  message: ReactNode
  detail?: ReactNode
  onConfirm: () => void
  onCancel: (method: InlineCloseMethod) => void
  loading?: boolean
  confirmLabel?: string
}) {
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => ref.current?.focus(), [])
  return (
    <div
      role="alertdialog"
      aria-label="삭제 확인"
      onKeyDown={(e) => {
        if (e.key === 'Escape' && !loading) {
          e.preventDefault()
          e.stopPropagation()
          onCancel('esc')
        }
      }}
      className="border-border-subtle bg-danger-soft/60 flex flex-wrap items-center gap-2 rounded-2xl border px-3.5 py-3"
    >
      <div className="min-w-0 flex-1">
        <p className="text-[14px] font-semibold">{message}</p>
        {detail && <p className="text-ink-3 mt-0.5 text-xs">{detail}</p>}
      </div>
      <Button variant="ghost" size="sm" onClick={() => onCancel('cancel_button')} disabled={loading}>
        취소
      </Button>
      <Button ref={ref} variant="danger" size="sm" onClick={onConfirm} loading={loading}>
        {confirmLabel}
      </Button>
    </div>
  )
}

// ─── 줄 끝 아이콘 버튼 (마우스를 올리면 보여요, 터치 화면에선 항상) ─────────

export function RowActions({
  label,
  onEdit,
  onDelete,
  className,
}: {
  label: string
  onEdit?: () => void
  onDelete?: () => void
  className?: string
}) {
  return (
    <span
      className={cn(
        'flex shrink-0 items-center transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-focus-within:opacity-100 [@media(hover:hover)]:group-hover:opacity-100',
        className
      )}
    >
      {onEdit && (
        <button
          type="button"
          onClick={onEdit}
          aria-label={`${label} 수정`}
          className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-1.5"
        >
          <Pencil className="size-3.5" />
        </button>
      )}
      {onDelete && (
        <button
          type="button"
          onClick={onDelete}
          aria-label={`${label} 삭제`}
          className="text-ink-3 hover:bg-danger-soft hover:text-danger rounded-lg p-1.5"
        >
          <Trash2 className="size-3.5" />
        </button>
      )}
    </span>
  )
}

/** "+ 추가" 점선 버튼 */
export function AddButton({
  onClick,
  children,
  className,
}: {
  onClick: () => void
  children: ReactNode
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'border-border-subtle text-ink-3 hover:border-line-strong hover:text-ink-2 flex w-full items-center justify-center gap-1.5 rounded-2xl border border-dashed py-2.5 text-sm font-semibold transition-colors',
        className
      )}
    >
      <Plus className="size-4" />
      {children}
    </button>
  )
}

// ─── 작은 입력 부품 ───────────────────────────────────────────

const WEEKDAYS = JAVA_DAYS.slice(0, 5)
const WEEKEND = JAVA_DAYS.slice(5)

export function DaysPicker({ value, onChange }: { value: JavaDayOfWeek[]; onChange: (v: JavaDayOfWeek[]) => void }) {
  const isSet = (t: JavaDayOfWeek[]) => value.length === t.length && t.every((d) => value.includes(d))
  const toggle = (d: JavaDayOfWeek) =>
    onChange(value.includes(d) ? value.filter((x) => x !== d) : JAVA_DAYS.filter((x) => x === d || value.includes(x)))
  return (
    <div className="flex flex-wrap items-center gap-1">
      {JAVA_DAYS.map((d) => (
        <Chip key={d} size="sm" variant="day" selected={value.includes(d)} onClick={() => toggle(d)} className="w-8 px-0">
          {JAVA_DAY_LABEL[d]}
        </Chip>
      ))}
      <span className="bg-line mx-1 h-4 w-px" aria-hidden />
      {(
        [
          ['매일', JAVA_DAYS],
          ['평일', WEEKDAYS],
          ['주말', WEEKEND],
        ] as const
      ).map(([label, days]) => (
        <Chip key={label} size="sm" selected={isSet([...days])} onClick={() => onChange([...days])}>
          {label}
        </Chip>
      ))}
    </div>
  )
}

export function ColorSwatches({
  colors,
  value,
  onChange,
}: {
  colors: UserColor[] | undefined
  value: number | null
  onChange: (id: number) => void
}) {
  if (!colors?.length) return null
  return (
    <div className="flex flex-wrap items-center gap-1.5" role="radiogroup" aria-label="색상">
      {colors.map((c) => (
        <button
          key={c.userColorId}
          type="button"
          role="radio"
          aria-checked={value === c.userColorId}
          aria-label={`색상 ${c.hex}`}
          onClick={() => onChange(c.userColorId)}
          className={cn(
            'size-6 rounded-full ring-offset-2 transition-shadow',
            value === c.userColorId ? 'ring-ink ring-2' : 'hover:ring-line-strong hover:ring-2'
          )}
          style={{ backgroundColor: c.hex }}
        />
      ))}
    </div>
  )
}

/** colorCode("3D5AFE" | "#3d5afe") → 내 색상 목록의 id (없으면 기본 색 → 첫 색) */
export function colorIdOf(colors: UserColor[] | undefined, code: string | null | undefined) {
  if (!colors?.length) return null
  const norm = (v: string) => v.replace('#', '').toUpperCase()
  const hit = code ? colors.find((c) => norm(c.hex) === norm(code)) : undefined
  return (hit ?? colors.find((c) => c.isDefault) ?? colors[0]).userColorId
}

/** "", "1.5" → null, 1.5 (숫자가 아니면 NaN) */
export const toNum = (v: string) => (v.trim() === '' ? null : Number(v))
