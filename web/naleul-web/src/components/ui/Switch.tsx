'use client'

import { cn } from '@/lib/cn'

interface SwitchProps {
  checked: boolean
  onChange: (next: boolean) => void
  /** 화면에 글자 라벨이 따로 있어도 스크린리더용으로 넣어주세요 */
  label: string
  disabled?: boolean
  className?: string
}

/** 디자인 시스템 Toggle — 44×26, 손잡이 20 (켜짐 primary · 꺼짐 border/subtle) */
export function Switch({ checked, onChange, label, disabled, className }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-[26px] w-11 shrink-0 items-center rounded-full transition-colors',
        'disabled:bg-disabled-bg disabled:pointer-events-none',
        checked ? 'bg-action-primary-bg' : 'bg-border-subtle',
        className
      )}
    >
      <span
        className={cn(
          'bg-bg-surface inline-block size-5 rounded-full shadow-[0_1px_3px_rgb(0_0_0/0.15)] transition-transform',
          checked ? 'translate-x-[21px]' : 'translate-x-[3px]'
        )}
      />
    </button>
  )
}
