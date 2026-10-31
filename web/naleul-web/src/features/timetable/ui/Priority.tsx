'use client'

import { Chip } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import type { TaskPriority } from '../types'

/**
 * Task 우선순위 (A 가 가장 중요) — brain-dump 2단계 · Task 상세 · TimeBlock 이 같이 써요.
 * 백엔드 기본값은 C (루틴 · 목표 · 미션에서 자동으로 생긴 Task 도 C 로 시작, 2026-10).
 */
export const PRIORITIES: TaskPriority[] = ['A', 'B', 'C', 'D', 'E']
export const DEFAULT_PRIORITY: TaskPriority = 'C'
export const PRIORITY_LABEL: Record<TaskPriority, string> = {
  A: '가장 중요',
  B: '중요',
  C: '보통',
  D: '가벼움',
  E: '여유',
}

export const priorityOf = (p?: TaskPriority | null): TaskPriority => p ?? DEFAULT_PRIORITY
/** TimeBlock 에서 강조하는 우선순위 */
export const isTopPriority = (p?: TaskPriority | null) => p === 'A'

/** A~E 칩 한 줄 — 누르면 바로 골라져요 */
export function PriorityChips({
  value,
  onChange,
  disabled,
  autoFocus,
  className,
}: {
  value: TaskPriority
  onChange: (p: TaskPriority) => void
  disabled?: boolean
  autoFocus?: boolean
  className?: string
}) {
  return (
    <div role="group" aria-label="중요도" className={cn('flex gap-1', className)}>
      {PRIORITIES.map((p) => (
        <Chip
          key={p}
          size="sm"
          selected={value === p}
          disabled={disabled}
          autoFocus={autoFocus && value === p}
          onClick={() => onChange(p)}
          title={PRIORITY_LABEL[p]}
          aria-label={`중요도 ${p} ${PRIORITY_LABEL[p]}`}
          className="w-9 px-0"
        >
          {p}
        </Chip>
      ))}
    </div>
  )
}

/** TimeBlock · 목록 제목 옆 작은 'A' — 목표 색으로 채워요 (color 가 없으면 --goal-accent) */
export function PriorityBadge({
  priority,
  color,
  className,
}: {
  priority: TaskPriority
  color?: string
  className?: string
}) {
  return (
    <span
      style={color ? { backgroundColor: color } : undefined}
      aria-label={`중요도 ${priority} ${PRIORITY_LABEL[priority]}`}
      title={`중요도 ${priority} · ${PRIORITY_LABEL[priority]}`}
      className={cn(
        'grid size-3.5 shrink-0 place-items-center rounded-[4px] bg-(--goal-accent) text-[9px] leading-none font-bold text-white',
        className
      )}
    >
      {priority}
    </span>
  )
}
