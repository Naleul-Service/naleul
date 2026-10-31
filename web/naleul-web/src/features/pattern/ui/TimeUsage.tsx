'use client'

import type { PatternTimeUsage } from '../types'
import { goalAccent } from '@/lib/goalTone'
import { useChartInteract, useDeadClick } from '../analytics'
import { hours1 } from '../format'
import { NotEnough, Section } from './Section'

/** 어디에 시간을 쓰고 있나요? — 목표별 주 평균 실제 시간 (세로선 = 계획) */
export function TimeUsage({ usage }: { usage?: PatternTimeUsage | null }) {
  const goals = usage?.goals ?? []
  const chartProps = useChartInteract('time_usage')
  const onRowClick = useDeadClick('time_usage_row')
  const max = Math.max(1, ...goals.map((g) => Math.max(g.actualHoursPerWeek, g.plannedHoursPerWeek)))
  return (
    <Section
      title="어디에 시간을 쓰고 있나요?"
      aside={usage && goals.length ? `주 평균 ${hours1(usage.weeklyTotalHours)}h · ┃ 계획` : undefined}
    >
      {!goals.length ? (
        <NotEnough>목표에 연결된 Task를 실행하면 보여드려요</NotEnough>
      ) : (
        <ul className="flex flex-col gap-5" {...chartProps}>
          {goals.map((g) => {
            const color = goalAccent(g.colorCode)
            return (
              <li key={g.goalCategoryId} onClick={onRowClick}>
                <div className="flex items-baseline justify-between gap-3">
                  <p className="min-w-0 truncate text-[15px] font-bold">
                    {g.emoji && <span className="mr-1">{g.emoji}</span>}
                    {g.name}
                  </p>
                  <p className="text-ink-3 shrink-0 text-[13px]">
                    <span className="text-ink text-[16px] font-bold">{hours1(g.actualHoursPerWeek)}h</span> / 주 ·{' '}
                    {g.newTemporary ? '새 임시 목표' : g.planRatio != null ? `계획의 ${g.planRatio}%` : '계획 없음'}
                  </p>
                </div>
                <div className="bg-subtle relative mt-2 h-3 rounded-full">
                  <div
                    className="h-full rounded-full"
                    style={{ width: `${(g.actualHoursPerWeek / max) * 100}%`, background: color }}
                  />
                  {g.plannedHoursPerWeek > 0 && (
                    <span
                      className="bg-ink absolute -top-1 h-5 w-0.5 rounded"
                      style={{ left: `calc(${(g.plannedHoursPerWeek / max) * 100}% - 1px)` }}
                      title={`계획 ${hours1(g.plannedHoursPerWeek)}h / 주`}
                    />
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Section>
  )
}
