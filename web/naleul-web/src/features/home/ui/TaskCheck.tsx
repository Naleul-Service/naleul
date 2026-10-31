'use client'

import { Check } from '@/components/icons'
import { cn } from '@/lib/cn'

/** 동그란 완료 체크 — 홈의 Task · 루틴 · 습관 공통 */
export function TaskCheck({
  done,
  label,
  onToggle,
  disabled,
  onDisabledPress,
  size = 'md',
}: {
  done: boolean
  label: string
  onToggle: () => void
  disabled?: boolean
  /** 비활성 체크를 눌렀을 때 — disabled 버튼은 click 이 안 나서 감싼 span 의 pointerdown 으로 잡아요 */
  onDisabledPress?: () => void
  size?: 'sm' | 'md'
}) {
  const button = (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      aria-pressed={done}
      aria-label={`${label} ${done ? '완료 취소' : '완료'}`}
      className={cn(
        'grid shrink-0 place-items-center rounded-full border-[1.5px] transition-colors disabled:opacity-50',
        size === 'sm' ? 'size-5' : 'size-6',
        done ? 'border-success bg-success text-on-ink' : 'border-border-control hover:border-success text-transparent'
      )}
    >
      <Check className={size === 'sm' ? 'size-3' : 'size-3.5'} />
    </button>
  )
  if (!disabled || !onDisabledPress) return button
  return (
    <span className="contents" onPointerDown={onDisabledPress}>
      {button}
    </span>
  )
}
