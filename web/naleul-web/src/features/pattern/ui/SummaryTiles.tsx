'use client'

import type { ReactNode } from 'react'
import { useDeadClick } from '../analytics'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import type { PatternSummary } from '../types'
import { signedMinutes } from '../format'

function Tile({
  label,
  value,
  sub,
  subTone,
}: {
  label: string
  value: ReactNode
  sub?: ReactNode
  subTone?: 'up' | 'down'
}) {
  const onClick = useDeadClick('summary_tile')
  return (
    <Card className="flex min-h-[124px] flex-col justify-between p-5" onClick={onClick}>
      <p className="text-caption-md text-text-secondary">{label}</p>
      <div>
        <p className="text-h2 text-text-primary leading-none font-semibold">{value}</p>
        {sub && (
          <p
            className={cn(
              'mt-1 text-[13px] font-semibold',
              subTone === 'up' ? 'text-success' : subTone === 'down' ? 'text-danger' : 'text-ink-3'
            )}
          >
            {sub}
          </p>
        )}
      </div>
    </Card>
  )
}

const DASH = '–'

/** 요약 4칸: 전체 실행률 · 최고 연속 · 평균 시작 지연 · AI 배치 수정률 */
export function SummaryTiles({ summary }: { summary?: PatternSummary | null }) {
  const s = summary
  const delta = s?.firstWeekDelta
  const morning = s?.morningDelayMinutes
  const evening = s?.eveningDelayMinutes
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4">
      <Tile
        label="전체 실행률"
        value={s?.executionRate != null ? `${s.executionRate}%` : DASH}
        sub={delta != null ? `${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta)}%p 첫 주 대비` : '기록이 쌓이면 보여드려요'}
        subTone={delta == null ? undefined : delta >= 0 ? 'up' : 'down'}
      />
      <Tile
        label="최고 연속 실행"
        value={
          <>
            {(s?.bestStreak ?? 0) > 0 && <span aria-hidden>🔥 </span>}
            {s?.bestStreak ?? 0}일
          </>
        }
        sub={s?.currentStreak ? `현재 ${s.currentStreak}일째` : '오늘부터 다시 시작해봐요'}
      />
      <Tile
        label="평균 시작 지연"
        value={s?.avgStartDelayMinutes != null ? signedMinutes(s.avgStartDelayMinutes) : DASH}
        sub={
          morning != null || evening != null
            ? [morning != null && `아침 ${signedMinutes(morning)}`, evening != null && `저녁 ${signedMinutes(evening)}`]
                .filter(Boolean)
                .join(' · ')
            : '완료할 때 실제 시각을 남기면 더 정확해요'
        }
      />
      <Tile
        label="AI 배치 수정률"
        value={s?.aiPlacementEditRate != null ? `${s.aiPlacementEditRate}%` : DASH}
        sub={
          s?.aiPlacementKeptPerTen != null
            ? `10개 중 ${s.aiPlacementKeptPerTen}개는 그대로 사용`
            : 'AI 배치 기록이 쌓이면 보여드려요'
        }
      />
    </div>
  )
}
