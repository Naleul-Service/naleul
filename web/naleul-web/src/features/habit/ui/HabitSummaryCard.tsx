'use client'

import Link from 'next/link'
import { ArrowRight, Check } from '@/components/icons'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { useHabitHome } from '../api'
import { ProgressBar } from '@/components/ui/ProgressBar'

/**
 * 목표 목록 맨 위 — 기본 생활 습관 요약 (오늘 몇 개 챙겼는지 + 습관 칩).
 * 누르면 습관 화면(/goal/{기본 생활 습관 id})으로 가요. 처음 열 때 서버가 그릇을 만들어 줘요.
 */
export function HabitSummaryCard({ className }: { className?: string }) {
  const { data, isPending } = useHabitHome()
  if (isPending)
    return <div className={cn('bg-surface border-line h-[120px] animate-pulse rounded-card border', className)} />
  if (!data) return null

  const due = data.habits.filter((h) => h.status === 'ACTIVE' && h.scheduled)
  const done = due.filter((h) => h.checked).length
  const ratio = due.length ? done / due.length : 0

  return (
    <Link
      href={`/goal/${data.goalCategoryId}`}
      onClick={() =>
        track('goal_list.habit_card_click', {
          habit_count: data.habits.length,
          due_today_count: due.length,
          done_count: done,
        })
      }
      className={cn(
        'border-line bg-surface hover:border-line-strong group block rounded-card border p-5 transition-colors',
        className
      )}
    >
      <div className="flex items-center gap-3">
        <span className="bg-success-soft grid size-10 shrink-0 place-items-center rounded-2xl text-xl" aria-hidden>
          {data.emoji ?? '🌱'}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[16px] font-bold">{data.goalName}</p>
          <p className="text-ink-3 text-[13px]">
            {data.habits.length === 0
              ? '영양제 · 물 마시기처럼 매일 챙길 것을 추가해 보세요'
              : due.length === 0
                ? '오늘은 해야 하는 습관이 없어요'
                : done === due.length
                  ? '오늘 할 습관을 모두 챙겼어요 🎉'
                  : `오늘 ${due.length - done}개 남았어요`}
          </p>
        </div>
        {due.length > 0 && (
          <span className="shrink-0 text-[17px] font-bold tabular-nums">
            {done}
            <span className="text-ink-3 text-[14px] font-semibold"> / {due.length}</span>
          </span>
        )}
        <ArrowRight className="text-ink-4 group-hover:text-ink-2 size-4 shrink-0 transition-transform group-hover:translate-x-0.5" />
      </div>
      {due.length > 0 && (
        <>
          <ProgressBar value={ratio} className="mt-3" />
          <div className="mt-3 flex flex-wrap gap-1.5">
            {due.slice(0, 8).map((h) => (
              <span
                key={h.habitId}
                className={cn(
                  'inline-flex h-7 items-center gap-1 rounded-full px-2.5 text-[12px] font-medium',
                  // 명세 8.6 — 연한 칩 (bg/sunken · 1px border/soft · text/secondary)
                  h.checked ? 'bg-success-soft text-success' : 'bg-bg-sunken border-border-soft text-text-secondary border'
                )}
              >
                {h.checked && <Check className="size-3" />}
                {h.emoji ? `${h.emoji} ` : ''}
                {h.name}
              </span>
            ))}
            {due.length > 8 && <span className="text-ink-3 self-center text-[12px]">+{due.length - 8}</span>}
          </div>
        </>
      )}
    </Link>
  )
}
