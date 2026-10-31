import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/**
 * 디자인 시스템 SegmentedTabs / ViewSwitch
 *  틀: bg/subtle · 모서리 12 · 안쪽 3px · 간격 2
 *  칸: 선택 = bg/surface · 모서리 9 · 그림자 · text/primary / 나머지 = text/secondary · Label/MD
 * 버튼을 직접 그리는 곳은 segmentedClass · segmentClass 만 가져다 써요.
 */
export const segmentedClass = (className?: string) =>
  cn('bg-bg-subtle rounded-seg inline-flex gap-0.5 p-[3px]', className)

export const segmentClass = (selected: boolean, className?: string) =>
  cn(
    'rounded-seg-item text-label-md flex h-8 items-center justify-center gap-1.5 px-3.5 transition-colors',
    selected ? 'bg-bg-surface text-text-primary shadow-seg' : 'text-text-secondary hover:text-text-primary',
    className
  )

export function SegmentedTabs<T extends string>({
  options,
  value,
  onChange,
  label,
  role = 'tablist',
  className,
  itemClassName,
}: {
  options: readonly { value: T; label: ReactNode; icon?: ReactNode }[]
  value: T
  onChange: (v: T) => void
  /** 스크린리더용 이름 */
  label: string
  role?: 'tablist' | 'radiogroup'
  className?: string
  itemClassName?: string
}) {
  const tab = role === 'tablist'
  return (
    <div role={role} aria-label={label} className={segmentedClass(className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role={tab ? 'tab' : 'radio'}
          {...(tab ? { 'aria-selected': value === o.value } : { 'aria-checked': value === o.value })}
          onClick={() => onChange(o.value)}
          className={segmentClass(value === o.value, itemClassName)}
        >
          {o.icon}
          {o.label}
        </button>
      ))}
    </div>
  )
}
