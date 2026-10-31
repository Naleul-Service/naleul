import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import { formatDuration } from '@/features/timetable/time'
import type { RecordPattern } from '../../api'

function Tile({ label, value, sub, up }: { label: string; value: ReactNode; sub: ReactNode; up?: boolean }) {
  return (
    <Card className="flex min-h-[132px] flex-col justify-between p-5">
      <p className="text-ink-3 text-[13px]">{label}</p>
      <div>
        <p className="text-[28px] leading-none font-extrabold tracking-tight">{value}</p>
        <p className={cn('mt-2 text-[13px] font-semibold', up ? 'text-success' : 'text-ink-3')}>{sub}</p>
      </div>
    </Card>
  )
}

/**
 * 요약 4칸 (완료한 Task 기준). 기한·목표 수치가 없으니 "쌓인 양"과 "그 전 4주보다 늘어난 것"만 보여줘요.
 * 줄었을 때는 빨간색 대신 그냥 최근 값을 보여줘요 (기록형은 쉬어가는 주도 자연스러워요).
 */
export function RecordSummaryTiles({ summary: s }: { summary: RecordPattern['summary'] }) {
  const r = s.recent
  const p = s.previous
  const moreCount = p.count > 0 && r.count > p.count ? r.count - p.count : 0
  const moreDays = p.activeDays > 0 && r.activeDays > p.activeDays ? r.activeDays - p.activeDays : 0
  const moreMinutes = p.minutes > 0 && r.minutes > p.minutes ? r.minutes - p.minutes : 0

  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      <Tile
        label="완료한 Task"
        value={`${s.totalCount}개`}
        sub={moreCount ? `▲ 최근 4주 ${r.count}개 · ${moreCount}개 더` : `최근 4주 ${r.count}개`}
        up={!!moreCount}
      />
      <Tile
        label="완료한 날"
        value={`${s.activeDays}일`}
        sub={moreDays ? `▲ 최근 4주 ${r.activeDays}일 · ${moreDays}일 더` : `최근 4주 ${r.activeDays}일`}
        up={!!moreDays}
      />
      <Tile
        label="쌓인 시간"
        value={s.totalMinutes ? formatDuration(s.totalMinutes) : '–'}
        sub={
          !s.totalMinutes
            ? 'Task에 시간을 정하면 쌓여요'
            : moreMinutes
              ? `▲ 최근 4주 ${formatDuration(moreMinutes)} 더`
              : `최근 4주 ${formatDuration(r.minutes)}`
        }
        up={!!moreMinutes}
      />
      <Tile
        label="연속 완료"
        value={
          <>
            {s.weekStreak > 0 && <span aria-hidden>🔥 </span>}
            {s.weekStreak}주
          </>
        }
        sub={
          s.thisWeekDone
            ? '이번 주도 완료했어요'
            : s.weekStreak > 0
              ? `이번 주에 하나 끝내면 ${s.weekStreak + 1}주째예요`
              : '이번 주에 첫 칸을 채워보세요'
        }
        up={s.thisWeekDone && s.weekStreak >= 2}
      />
    </div>
  )
}
