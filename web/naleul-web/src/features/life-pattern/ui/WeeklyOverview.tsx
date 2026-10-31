'use client'

import { Card, CardHeader } from '@/components/ui/Card'
import type { LifePattern } from '@/features/timetable/types'
import { minutesToTime } from '@/features/timetable/time'
import { occurrenceOf } from '../dayTimes'
import { ALL_DAYS, TYPE_COLOR, dayShort } from '../presets'

interface Segment {
  pattern: LifePattern
  /** 0 ~ 1440 */
  start: number
  end: number
}

/**
 * 요일별로 고정 시간이 어떻게 깔리는지 한눈에 보는 막대.
 * 자정을 넘기는 패턴(수면 23:00~07:00)은 시작한 요일에 23:00~24:00, 다음 요일에 00:00~07:00 으로 나눠 그려요.
 * 요일별 시간(예: 주말 수면 01:00~09:30)과 "전날/다음 날 시작"도 반영해요.
 * (백엔드 FixedBlockCalculator 와 같은 규칙: 요일 판정은 블록 "소속 요일" 기준)
 */
function segmentsByDay(patterns: LifePattern[]) {
  const rows: Segment[][] = ALL_DAYS.map(() => [])
  for (const p of patterns) {
    const days = p.days.length ? p.days : ALL_DAYS
    for (const d of days) {
      const i = ALL_DAYS.indexOf(d)
      const { start, end } = occurrenceOf(p, d) // 소속 요일 0시 기준 분 (전날·다음 날로 넘어갈 수 있음)
      // 하루(1440분)씩 잘라서 해당 요일 줄에 그려요
      for (let dayStart = Math.floor(start / 1440) * 1440; dayStart < end; dayStart += 1440) {
        const s = Math.max(start, dayStart) - dayStart
        const e = Math.min(end, dayStart + 1440) - dayStart
        if (e > s) rows[(((i + dayStart / 1440) % 7) + 7) % 7].push({ pattern: p, start: s, end: e })
      }
    }
  }
  return rows
}

const HOURS = [0, 6, 12, 18, 24]

export function WeeklyOverview({
  patterns,
  onSelect,
}: {
  patterns: LifePattern[]
  onSelect: (p: LifePattern) => void
}) {
  const rows = segmentsByDay(patterns)

  return (
    <Card className="p-5">
      <CardHeader title="한 주 미리보기" />
      <div className="mt-4">
        {/* 시간 눈금 */}
        <div className="text-ink-4 relative ml-7 h-4 text-[10px]">
          {HOURS.map((h) => (
            <span key={h} className="absolute -translate-x-1/2" style={{ left: `${(h / 24) * 100}%` }}>
              {h}
            </span>
          ))}
        </div>
        <div className="mt-1 space-y-1.5">
          {rows.map((segs, i) => (
            <div key={ALL_DAYS[i]} className="flex items-center gap-2">
              <span className={i >= 5 ? 'text-ink-3 w-5 text-xs' : 'text-ink-2 w-5 text-xs font-medium'}>
                {dayShort(ALL_DAYS[i])}
              </span>
              <div className="bg-subtle relative h-6 flex-1 overflow-hidden rounded-md">
                {[6, 12, 18].map((h) => (
                  <span key={h} className="bg-line absolute inset-y-0 w-px" style={{ left: `${(h / 24) * 100}%` }} />
                ))}
                {segs.map((seg, j) => (
                  <button
                    key={`${seg.pattern.lifePatternId}-${j}`}
                    type="button"
                    onClick={() => onSelect(seg.pattern)}
                    title={`${seg.pattern.title} ${minutesToTime(seg.start)}–${seg.end === 1440 ? '24:00' : minutesToTime(seg.end)}`}
                    className="absolute inset-y-0.5 overflow-hidden rounded px-1 text-left text-[10px] leading-5 font-semibold whitespace-nowrap text-accent-on-mint hover:brightness-95"
                    style={{
                      left: `${(seg.start / 1440) * 100}%`,
                      width: `${((seg.end - seg.start) / 1440) * 100}%`,
                      backgroundColor: TYPE_COLOR[seg.pattern.patternType],
                    }}
                  >
                    {seg.end - seg.start >= 90 ? seg.pattern.title : ''}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </Card>
  )
}
