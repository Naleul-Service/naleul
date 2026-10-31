import type { ButtonHTMLAttributes, CSSProperties, ReactNode } from 'react'
import { X } from '@/components/icons'
import { cn } from '@/lib/cn'
import { goalVars } from '@/lib/goalTone'

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean
  size?: 'sm' | 'md'
  /**
   * choice: 선택지 (QuickReply 스타일 — 선택 시 ai/tint-soft + 1.5px brand 테두리)
   * day: 요일 선택 (DayChip — 선택 시 primary 채움)
   */
  variant?: 'choice' | 'day'
}

/** 선택 가능한 알약 모양 버튼 (quickReplies, 요일 선택, 성향 선택 등) */
export function Chip({
  selected = false,
  size = 'md',
  variant = 'choice',
  className,
  children,
  type = 'button',
  ...rest
}: ChipProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        'inline-flex items-center justify-center rounded-full font-medium transition-colors',
        'disabled:bg-disabled-bg disabled:text-disabled-fg disabled:pointer-events-none disabled:border-transparent',
        size === 'sm' ? 'text-body-sm h-7 px-2.5 font-medium' : 'text-body-md h-9 px-4 font-medium',
        variant === 'day'
          ? selected
            ? 'bg-action-primary-bg text-action-primary-fg border border-transparent'
            : 'bg-bg-sunken text-text-secondary hover:text-text-primary border-border-subtle border'
          : selected
            ? 'bg-ai-tint-soft text-text-brand border-[1.5px] border-text-brand'
            : 'border-border-subtle bg-bg-surface text-text-primary hover:bg-bg-sunken border',
        className
      )}
      {...rest}
    >
      {children}
    </button>
  )
}

/**
 * 목표 색 칩 — 연한 목표 바탕 + 왼쪽 색 막대 + 목표 글자색
 *  MD h32 · SM h24. selected = 1.5px accent 테두리. onRemove 를 주면 X 버튼
 */
export function GoalChip({
  colorCode,
  size = 'md',
  selected = false,
  disabled = false,
  onClick,
  onRemove,
  className,
  children,
}: {
  colorCode?: string | null
  size?: 'sm' | 'md'
  selected?: boolean
  disabled?: boolean
  onClick?: () => void
  onRemove?: () => void
  className?: string
  children: ReactNode
}) {
  const md = size === 'md'
  const Tag = onClick ? 'button' : 'span'
  return (
    <Tag
      {...(onClick ? { type: 'button' as const, onClick, disabled, 'aria-pressed': selected } : {})}
      style={goalVars(colorCode) as CSSProperties}
      className={cn(
        'inline-flex max-w-full min-w-0 items-center border border-transparent transition-colors',
        md ? 'rounded-chip text-label-md h-8 gap-1 pr-2 pl-2.5' : 'rounded-chip-sm text-label-sm h-6 gap-[3px] pr-1.5 pl-2',
        disabled
          ? 'bg-disabled-bg text-disabled-fg'
          : 'bg-(--goal-bg) text-(--goal-fg) hover:border-[color-mix(in_srgb,var(--goal-accent)_30%,transparent)]',
        selected && !disabled && 'border-[1.5px] border-(--goal-accent) hover:border-(--goal-accent)',
        className
      )}
    >
      <span
        aria-hidden
        className={cn(
          'shrink-0',
          md ? 'h-4 w-1 rounded-[2px]' : 'h-3 w-[3px] rounded-[1.5px]',
          disabled ? 'bg-disabled-fg' : 'bg-(--goal-accent)'
        )}
      />
      <span className="min-w-0 truncate">{children}</span>
      {onRemove && (
        <span
          role="button"
          tabIndex={0}
          aria-label="빼기"
          onClick={(e) => {
            e.stopPropagation()
            onRemove()
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault()
              onRemove()
            }
          }}
          className="-mr-0.5 grid place-items-center rounded opacity-70 hover:opacity-100"
        >
          <X className={md ? 'size-3.5' : 'size-3'} />
        </span>
      )}
    </Tag>
  )
}

type BadgeTone = 'brand' | 'success' | 'warning' | 'danger' | 'neutral' | 'mint' | 'info'

const BADGE_TONE: Record<BadgeTone, string> = {
  brand: 'bg-action-secondary-bg text-text-brand',
  success: 'bg-status-success-bg text-status-success-fg',
  warning: 'bg-status-warning-bg text-status-warning-fg',
  danger: 'bg-status-error-bg text-status-error-fg',
  neutral: 'bg-bg-subtle text-text-secondary',
  mint: 'bg-mood-mint text-goal-mint-fg',
  info: 'bg-status-info-bg text-status-info-fg',
}

const DOT_TONE: Record<BadgeTone, string> = {
  brand: 'bg-text-brand',
  success: 'bg-status-success-fg',
  warning: 'bg-warning',
  danger: 'bg-status-error-fg',
  neutral: 'bg-text-tertiary',
  mint: 'bg-accent-mint',
  info: 'bg-status-info-fg',
}

/** 클릭 안 되는 작은 라벨 (디자인 시스템 Tag — "AI가 설계한 목표", "루틴", "Task" 등) */
export function Badge({
  tone = 'neutral',
  dot = false,
  className,
  children,
}: {
  tone?: BadgeTone
  /** 앞에 6px 점 */
  dot?: boolean
  className?: string
  children: React.ReactNode
}) {
  return (
    <span
      className={cn(
        'text-label-sm inline-flex h-6 shrink-0 items-center gap-1.5 rounded-full px-2.5 whitespace-nowrap',
        BADGE_TONE[tone],
        className
      )}
    >
      {dot && <span aria-hidden className={cn('size-1.5 shrink-0 rounded-full', DOT_TONE[tone])} />}
      {children}
    </span>
  )
}

/** 디자인 시스템 이름 (= Badge) */
export const Tag = Badge
