import { Check } from '@/components/icons'
import { cn } from '@/lib/cn'

/** 디자인 시스템 Checkbox 모양 (20×20 · 모서리 6) — 버튼 · 라벨 안에 넣어 쓰는 장식이에요 */
export function CheckboxMark({ checked, className }: { checked: boolean; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'rounded-checkbox grid size-5 shrink-0 place-items-center transition-colors',
        checked
          ? 'bg-action-primary-bg text-action-primary-fg'
          : 'border-border-control bg-bg-surface border-[1.5px]',
        className
      )}
    >
      {checked && <Check className="size-3.5" />}
    </span>
  )
}

/** 디자인 시스템 Radio 모양 (20×20) */
export function RadioMark({ checked, className }: { checked: boolean; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'grid size-5 shrink-0 place-items-center rounded-full',
        checked ? 'border-action-primary-bg border-2' : 'border-border-control border-[1.5px]',
        className
      )}
    >
      {checked && <span className="bg-action-primary-bg size-2 rounded-full" />}
    </span>
  )
}
