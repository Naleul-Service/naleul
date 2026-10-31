'use client'

import { cn } from '@/lib/cn'
import { useChartInteract } from '../analytics'
import type { PatternWeekday } from '../types'
import { DAY_SHORT } from '../format'
import { Emphasis } from './Emphasis'
import { NotEnough, Section } from './Section'

const BAR_MAX_PX = 128

/** 요일별 실행률 막대 — 가장 낮은 요일만 파란색 */
export function WeekdayBars({ weekday }: { weekday?: PatternWeekday | null }) {
  const hasData = weekday?.days.some((d) => d.rate != null)
  const chartProps = useChartInteract('weekday')
  return (
    <Section title="요일별 실행률" aside={weekday?.average != null ? `평균 ${weekday.average}%` : undefined}>
      {!weekday || !hasData ? (
        <NotEnough />
      ) : (
        <>
          <div
            className="flex items-end justify-between gap-2 pt-6"
            role="list"
            aria-label="요일별 실행률"
            {...chartProps}
          >
            {weekday.days.map((d) => {
              const low = d.dayOfWeek === weekday.lowest
              return (
                <div key={d.dayOfWeek} role="listitem" className="flex flex-1 flex-col items-center gap-2">
                  <span className={cn('text-[12px] font-semibold', low ? 'text-ink' : 'text-ink-3')}>
                    {d.rate != null ? `${d.rate}%` : '–'}
                  </span>
                  <div
                    className={cn('w-full max-w-9 rounded-md', low ? 'bg-brand' : 'bg-line-strong')}
                    style={{ height: d.rate != null ? Math.max(6, (d.rate / 100) * BAR_MAX_PX) : 4 }}
                    aria-label={`${DAY_SHORT[d.dayOfWeek]} ${d.rate ?? '-'}%`}
                  />
                  <span className={cn('text-[13px]', low ? 'text-ink font-bold' : 'text-ink-3')}>
                    {DAY_SHORT[d.dayOfWeek]}
                  </span>
                </div>
              )
            })}
          </div>
          {weekday.insight && (
            <p className="bg-subtle text-ink-2 mt-5 rounded-2xl px-4 py-3 text-[14px] leading-relaxed">
              <Emphasis text={weekday.insight} className="text-text-brand" />
            </p>
          )}
        </>
      )}
    </Section>
  )
}
