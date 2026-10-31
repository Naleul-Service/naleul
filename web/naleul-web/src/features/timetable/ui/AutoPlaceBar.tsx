'use client'

import { Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import type { TimetableDay } from '../types'
import { AIBadge } from '@/components/ui/AIBadge'

/** 배치 대상: 오늘 이후 날짜의 "시간 미정" 미완료 Task (미션은 방장이 시간을 정하므로 제외) */
export function unplacedOf(days: TimetableDay[], today: string) {
  return days
    .filter((d) => d.date >= today)
    .map((d) => ({
      date: d.date,
      count: d.unscheduledTasks.filter((t) => t.taskStatus === 'TODO' && t.sourceType !== 'MISSION').length,
    }))
    .filter((d) => d.count > 0)
}

/**
 * 캘린더 위 "AI로 TimeTable 배치하기" 띠.
 * 시간 미정 Task 가 있을 때만 버튼이 켜져요. 직접 정한 시간(📌)과 고정 시간은 그대로 두고 남은 빈 시간에 넣어요.
 * 누르면 바로 저장하지 않고 점선으로 먼저 보여줘요 (onPreview) → 화면 아래에서 "이대로 진행할까요?"
 */
export function AutoPlaceBar({
  days,
  today,
  onPreview,
  loading,
  proposing,
  viewMode,
}: {
  days: TimetableDay[]
  today: string
  onPreview: (range: { startDate: string; endDate: string }) => void
  loading?: boolean
  /** 미리보기 확인 중 */
  proposing?: boolean
  /** 캘린더 보기 (calendar.auto_place_click 의 view_mode) */
  viewMode?: 'day' | 'week' | 'month'
}) {
  const targets = unplacedOf(days, today)
  const total = targets.reduce((n, d) => n + d.count, 0)
  const onlyToday = targets.length === 1 && targets[0].date === today
  const active = total > 0 && !proposing
  // 2026-10: 배치할 게 없으면 띠 자체를 숨겨요 — "AI로 일정 옮기기" 입력과 헷갈리지 않게, 필요할 때만 나타나요
  if (!active && !proposing) return null

  return (
    <div
      className={cn(
        'rounded-card mb-3 flex flex-wrap items-center gap-3 px-4 py-3',
        // AI 제안 상자 (명세 6B) — 배치할 게 없으면 평범한 카드
        active || proposing ? 'ai-border-flat' : 'border-border-subtle bg-bg-surface border'
      )}
    >
      {active || proposing ? (
        <AIBadge />
      ) : (
        <span className="bg-bg-subtle text-text-tertiary grid size-7 shrink-0 place-items-center rounded-[8px]">
          <Sparkles className="size-4" />
        </span>
      )}
      <div className="min-w-0 flex-1">
        <p className={cn('text-[14px] font-bold', !active && 'text-ink-3')}>
          {proposing
            ? 'AI가 제안한 자리를 점선으로 보여주고 있어요'
            : active
              ? `시간이 정해지지 않은 Task ${total}개 — AI가 빈 시간에 넣어 드릴까요?`
              : '모든 Task가 TimeTable에 배치돼 있어요'}
        </p>
        <p className="text-ink-3 text-xs leading-relaxed">
          직접 정한 시간과 고정 시간은 그대로 두고, 남은 빈 시간에 집중이 잘 되는 시간대부터 넣어요.
          {targets.some((d) => d.date === today) && ' 오늘은 지금 이후 시간만 써요.'}
        </p>
      </div>
      <Button
        variant="ai"
        size="sm"
        disabled={!active}
        loading={loading}
        title={proposing ? '아래에서 진행 여부를 골라 주세요' : active ? undefined : '배치할 시간 미정 Task가 없어요'}
        onClick={() => {
          track('calendar.auto_place_click', {
            view_mode: viewMode,
            unscheduled_count: total,
            day_count: targets.length,
          })
          onPreview({ startDate: targets[0].date, endDate: targets[targets.length - 1].date })
        }}
      >
        <Sparkles className="size-3.5" />
        {onlyToday || !active ? 'AI로 빈 시간에 자동 배치' : `AI로 빈 시간에 자동 배치 (${targets.length}일)`}
      </Button>
    </div>
  )
}
