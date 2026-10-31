import { cn } from '@/lib/cn'

/** 질문 2 / 5 ●●○○○ */
export function InterviewProgress({ current, max }: { current: number; max: number }) {
  const shown = Math.min(Math.max(current, 0), max)
  return (
    <div className="flex items-center gap-2" aria-label={`질문 ${shown} / ${max}`}>
      <span className="text-ink-3 hidden text-[13px] font-medium tabular-nums sm:inline">
        질문 {shown} / {max}
      </span>
      <span className="flex gap-1" aria-hidden>
        {Array.from({ length: max }, (_, i) => (
          <span
            key={i}
            className={cn(
              'size-1.5 rounded-full transition-colors',
              i < shown - 1 ? 'bg-ink' : i === shown - 1 ? 'bg-brand' : 'bg-line-strong'
            )}
          />
        ))}
      </span>
    </div>
  )
}
