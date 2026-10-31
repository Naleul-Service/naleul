import type { ButtonHTMLAttributes, ReactNode, Ref } from 'react'
import { cn } from '@/lib/cn'
import { Spinner } from './Spinner'

/**
 * 디자인 시스템 Button — "Style A Soft Flat"
 *  primary · secondary · outline · ghost · danger · ai(그라데이션)
 *  brand 는 예전 이름이에요 (= primary)
 */
export type ButtonVariant = 'primary' | 'brand' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'ai'
export type ButtonSize = 'sm' | 'md' | 'lg'

const VARIANT: Record<ButtonVariant, string> = {
  primary:
    'bg-action-primary-bg text-action-primary-fg shadow-primary hover:bg-action-primary-bg-hover active:bg-action-primary-bg-pressed',
  brand:
    'bg-action-primary-bg text-action-primary-fg shadow-primary hover:bg-action-primary-bg-hover active:bg-action-primary-bg-pressed',
  secondary:
    'bg-action-secondary-bg text-action-secondary-fg border border-action-secondary-border hover:bg-action-secondary-bg-hover active:bg-action-secondary-bg-pressed',
  outline:
    'bg-bg-surface text-text-brand border border-action-outline-border hover:bg-action-ghost-hover active:bg-action-ghost-pressed',
  ghost: 'bg-transparent text-text-brand hover:bg-action-ghost-hover active:bg-action-ghost-pressed',
  danger:
    'bg-action-danger-bg text-action-danger-fg hover:bg-action-danger-bg-hover active:bg-action-danger-bg-pressed',
  // 명세 6A — 115° 그라데이션 (기본/hover/pressed) · 흰 글자 · primary 그림자 (누르는 동안 없음)
  ai: 'bg-ai-gradient text-white shadow-primary hover:bg-ai-gradient-hover active:bg-ai-gradient-pressed active:shadow-none',
}

// LG h48 px20 · MD h40 px16 · SM h32 px12 (아이콘 쪽은 4px 줄여요)
const SIZE: Record<ButtonSize, string> = {
  lg: 'h-12 px-5 text-body-md font-medium rounded-btn gap-1.5 has-[>svg:first-child]:pl-4 has-[>svg:last-child]:pr-4',
  md: 'h-10 px-4 text-body-sm font-medium rounded-btn gap-1.5 has-[>svg:first-child]:pl-3 has-[>svg:last-child]:pr-3',
  sm: 'h-8 px-3 text-body-sm font-medium rounded-btn-sm gap-1 has-[>svg:first-child]:pl-2.5 has-[>svg:last-child]:pr-2.5',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  fullWidth?: boolean
  /** 글자 왼쪽 / 오른쪽 아이콘 (lucide 아이콘 — 크기는 버튼이 맞춰요) */
  leftIcon?: ReactNode
  rightIcon?: ReactNode
  ref?: Ref<HTMLButtonElement>
}

/** 클래스만 필요할 때 (예: <Link className={buttonClass('primary')}>) */
export function buttonClass(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', fullWidth = false) {
  return cn(
    // 누르면 살짝 눌려요 (scale 0.97)
    'inline-flex shrink-0 items-center justify-center whitespace-nowrap transition-[color,background-color,border-color,box-shadow,transform] duration-150 active:scale-[0.97]',
    '[&_svg]:shrink-0',
    'focus-visible:outline-border-focus focus-visible:outline-2 focus-visible:outline-offset-2',
    // 비활성: 모든 종류 같은 회색 (테두리 · 그림자 없음)
    'disabled:pointer-events-none disabled:border-transparent disabled:bg-disabled-bg disabled:bg-none disabled:text-disabled-fg disabled:shadow-none',
    VARIANT[variant],
    SIZE[size],
    fullWidth && 'w-full'
  )
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  leftIcon,
  rightIcon,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(buttonClass(variant, size, fullWidth), className)}
      {...rest}
    >
      {loading ? <Spinner className="size-4" /> : leftIcon}
      {children}
      {rightIcon}
    </button>
  )
}
