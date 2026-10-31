import { cn } from '@/lib/cn'

/** 불러오는 중 — 글자색(currentColor)을 따라가요 */
export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="불러오는 중"
      className={cn(
        'inline-block size-4 animate-spin rounded-full border-2 border-current border-r-transparent',
        className
      )}
    />
  )
}
