import { Sparkles } from '@/components/icons'
import { cn } from '@/lib/cn'

/**
 * 디자인 시스템 AIBadge (명세 6C) — AI 기능 표시
 *  md 28×28 · 모서리 8 · 아이콘 16 / sm 20×20 · 모서리 6 · 아이콘 12 (촘촘한 줄)
 *  lg 는 큰 빈 화면 · 진행 화면용 (아이콘 비율 같음)
 */
export function AIBadge({ size = 'md', className }: { size?: 'sm' | 'md' | 'lg'; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'bg-ai-gradient grid shrink-0 place-items-center text-white',
        size === 'sm' && 'size-5 rounded-[6px]',
        size === 'md' && 'size-7 rounded-[8px]',
        size === 'lg' && 'size-11 rounded-[12px]',
        className
      )}
    >
      <Sparkles
        className={size === 'sm' ? 'size-3' : size === 'md' ? 'size-4' : 'size-6'}
      />
    </span>
  )
}
