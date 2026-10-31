'use client'

import { Gauge } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { UsageItem } from '../api'

/** "오늘 AI 목표 만들기 3/5회 남음" — 한도가 없으면 아무것도 안 그려요 */
export function UsageLine({
  items,
  className,
  tone = 'muted',
}: {
  items: (UsageItem | undefined | null)[]
  className?: string
  /** muted = 회색 안내, onDark = 어두운 바탕 위 */
  tone?: 'muted' | 'onDark'
}) {
  const limited = items.filter((i): i is UsageItem => !!i && i.limit != null)
  if (!limited.length) return null
  const empty = limited.some((i) => i.remaining === 0)
  return (
    <p
      className={cn(
        'flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px]',
        tone === 'onDark' ? 'text-on-ink/70' : empty ? 'text-danger' : 'text-ink-3',
        className
      )}
    >
      <Gauge className="size-3.5 shrink-0" aria-hidden />
      {limited.map((i, n) => (
        <span key={i.key}>
          {n > 0 && <span className="mr-2 opacity-50">·</span>}
          {i.label} <b className={tone === 'onDark' ? 'text-on-ink' : 'text-ink-2'}>{i.remaining}</b>/{i.limit}회 남음
        </span>
      ))}
      <span className="opacity-70">
        ({limited.some((i) => i.key.endsWith('_WEEK')) ? '하루 횟수는 매일, 주간 횟수는 월요일 0시에' : '매일 0시에'}{' '}
        다시 채워져요)
      </span>
    </p>
  )
}
