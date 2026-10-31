import type { ButtonHTMLAttributes, Ref } from 'react'
import { cn } from '@/lib/cn'

/** 아이콘만 있는 버튼 — MD 32×32 (아이콘 18) · SM 28×28 (아이콘 16) · 모서리 6 */
export function iconButtonClass(size: 'sm' | 'md' = 'md') {
  return cn(
    'rounded-icon-btn text-text-secondary hover:bg-bg-subtle hover:text-text-primary inline-grid shrink-0 place-items-center transition-colors',
    'disabled:text-disabled-fg disabled:pointer-events-none',
    size === 'sm' ? 'size-7 [&_svg]:size-4' : 'size-8 [&_svg]:size-[18px]'
  )
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 스크린리더용 이름 (필수) */
  label: string
  size?: 'sm' | 'md'
  ref?: Ref<HTMLButtonElement>
}

export function IconButton({ label, size = 'md', className, type = 'button', children, ...rest }: IconButtonProps) {
  return (
    <button type={type} aria-label={label} title={label} className={cn(iconButtonClass(size), className)} {...rest}>
      {children}
    </button>
  )
}
