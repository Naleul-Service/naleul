import type { CSSProperties } from 'react'
import { cn } from '@/lib/cn'

/** 디자인 시스템 ProgressBar — SM h4 · MD h6, 트랙 progress/track · 채움 progress/fill (밝은 민트) */
export function ProgressBar({
  value,
  size = 'md',
  className,
  fillClassName,
  fillStyle,
  label,
}: {
  /** 0 ~ 1 */
  value: number
  size?: 'sm' | 'md'
  className?: string
  /** 채움 색을 바꿀 때 (예: 목표 색) */
  fillClassName?: string
  fillStyle?: CSSProperties
  /** 스크린리더용 — 없으면 장식으로 숨겨요 */
  label?: string
}) {
  const v = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0))
  return (
    <div
      {...(label
        ? { role: 'progressbar', 'aria-label': label, 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': Math.round(v * 100) }
        : { 'aria-hidden': true })}
      className={cn('bg-progress-track overflow-hidden rounded-full', size === 'sm' ? 'h-1' : 'h-1.5', className)}
    >
      <div
        className={cn('bg-progress-fill h-full rounded-full transition-all', fillClassName)}
        style={{ width: `${v * 100}%`, ...fillStyle }}
      />
    </div>
  )
}
