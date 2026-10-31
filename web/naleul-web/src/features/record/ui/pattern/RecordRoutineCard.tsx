import { TrendingUp } from '@/components/icons'
import { Section } from '@/features/pattern/ui/Section'
import { formatDuration } from '@/features/timetable/time'
import type { RecordPattern } from '../../api'

/**
 * 무엇에 시간을 썼나요? — 이 목표의 루틴별로 완료한 횟수와 쌓인 시간.
 * 루틴이 없는 목표에서는 그리지 않아요 (RecordPatternSection 에서 숨김).
 * 최근 4주에 그 전보다 눈에 띄게 더 한 루틴에는 "요즘 늘었어요" 표시.
 */
export function RecordRoutineCard({ routines }: { routines: RecordPattern['routines'] }) {
  const items = routines.items
  const totalMinutes = items.reduce((n, r) => n + r.minutes, 0)
  // 루틴에 시간 정보가 하나도 없으면 막대를 횟수로 그려요
  const byTime = totalMinutes > 0
  const max = Math.max(1, ...items.map((r) => (byTime ? r.minutes : r.completedCount)))
  const top = items[0]

  return (
    <Section title="무엇에 시간을 썼나요?" aside="루틴 · 처음부터">
      <ul className="space-y-4">
        {items.map((r, i) => {
          const rising = r.routineId === routines.risingRoutineId
          const more = r.recentCount - r.previousCount
          const value = byTime ? r.minutes : r.completedCount
          return (
            <li key={r.routineId}>
              <div className="flex items-baseline gap-2">
                <p className="min-w-0 flex-1 truncate text-[14px] font-semibold">
                  {r.emoji && <span className="mr-1">{r.emoji}</span>}
                  {r.routineName}
                </p>
                {rising && (
                  <span className="bg-success-soft text-success inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-bold">
                    <TrendingUp className="size-3" />
                    요즘 늘었어요
                  </span>
                )}
                <p className="shrink-0 text-[13px] tabular-nums">
                  {byTime && <b>{formatDuration(r.minutes)}</b>}
                  <span className={byTime ? 'text-ink-3' : 'font-bold'}>
                    {byTime ? ' · ' : ''}
                    {r.completedCount}번
                  </span>
                </p>
              </div>
              <div className="bg-progress-track mt-1.5 h-2 overflow-hidden rounded-full">
                <div
                  className={i === 0 && value > 0 ? 'bg-brand h-full rounded-full' : 'h-full rounded-full bg-heat-2'}
                  style={{ width: `${(value / max) * 100}%` }}
                />
              </div>
              <p className="text-ink-3 mt-1 text-[12px]">
                {r.completedCount === 0 ? (
                  '아직 완료한 날이 없어요'
                ) : (
                  <>
                    최근 4주 {r.recentCount}번
                    {more > 0 && r.previousCount > 0 && (
                      <span className="text-success font-semibold"> · 그 전 4주보다 {more}번 더</span>
                    )}
                  </>
                )}
              </p>
            </li>
          )
        })}
      </ul>

      {byTime && top && top.minutes > 0 && items.length > 1 && (
        <p className="bg-subtle text-ink-2 mt-5 rounded-2xl px-4 py-3 text-[14px] leading-relaxed">
          루틴 시간의{' '}
          <strong className="text-text-brand font-bold">{Math.round((top.minutes / totalMinutes) * 100)}%</strong>를{' '}
          <strong className="font-bold">{top.routineName}</strong>에 썼어요
        </p>
      )}
    </Section>
  )
}
