import { NotEnough, Section } from '@/features/pattern/ui/Section'
import { dayOfMonth, formatDuration, monthOf } from '@/features/timetable/time'
import type { RecordPattern } from '../../api'

const W = 600
const H = 250
const PAD = { left: 40, right: 44, top: 28, bottom: 30 }

/**
 * 주차별로 완료한 Task (막대) + 처음부터의 누적 (선).
 * 막대는 주마다 오르내려도, 누적 선은 절대 내려가지 않아요 → "계속 쌓이고 있다"는 걸 보여주려고 같이 그렸어요.
 * SVG 로 직접 그려요 (차트 라이브러리를 추가하지 않으려고 — 나의 패턴 화면과 같은 방식).
 */
export function RecordWeeklyCard({ weeks }: { weeks: RecordPattern['rhythm']['weeks'] }) {
  const last = weeks[weeks.length - 1]
  if (!last || last.cumulativeCount === 0) {
    return (
      <Section title="주차별로 완료한 Task" aside="최근 12주">
        <NotEnough>Task를 완료하면 주마다 얼마나 했는지 보여드려요</NotEnough>
      </Section>
    )
  }

  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom
  const slot = innerW / weeks.length
  const barW = Math.min(28, slot * 0.56)
  const maxWeek = Math.max(4, ...weeks.map((w) => w.count))
  const maxCum = Math.max(4, last.cumulativeCount)
  const cx = (i: number) => PAD.left + slot * i + slot / 2
  const yBar = (m: number) => PAD.top + innerH - (m / maxWeek) * innerH
  const yCum = (m: number) => PAD.top + innerH - (m / maxCum) * innerH
  const line = weeks.map((w, i) => `${i ? 'L' : 'M'}${cx(i)},${yCum(w.cumulativeCount)}`).join(' ')
  const lastIdx = weeks.length - 1

  // 최근 4주 평균 vs 그 전 4주 평균
  const avg = (list: typeof weeks) => list.reduce((n, w) => n + w.count, 0) / Math.max(list.length, 1)
  const round1 = (n: number) => Math.round(n * 10) / 10
  const recentAvg = round1(avg(weeks.slice(-4)))
  const prevAvg = round1(avg(weeks.slice(-8, -4)))

  return (
    <Section title="주차별로 완료한 Task" aside={`누적 ${last.cumulativeCount}개`}>
      <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="주차별 완료한 Task와 누적 그래프">
        {[0, 0.5, 1].map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={yBar(maxWeek * t)} y2={yBar(maxWeek * t)} stroke="var(--color-line)" />
            <text x={PAD.left - 8} y={yBar(maxWeek * t) + 4} textAnchor="end" fontSize="11" fill="var(--color-ink-3)">
              {Math.round(maxWeek * t)}
            </text>
          </g>
        ))}

        {weeks.map((w, i) => {
          const h = Math.max(w.count ? 3 : 0, innerH - (yBar(w.count) - PAD.top))
          return (
            <rect
              key={w.weekStart}
              x={cx(i) - barW / 2}
              y={PAD.top + innerH - h}
              width={barW}
              height={h}
              rx="5"
              fill={i === lastIdx ? 'var(--color-brand)' : 'var(--color-heat-2)'}
            >
              <title>{`${monthOf(w.weekStart)}/${dayOfMonth(w.weekStart)} 주 · ${w.count}개${w.minutes ? ` (${formatDuration(w.minutes)})` : ''}`}</title>
            </rect>
          )
        })}

        {/* 누적 선 */}
        <path d={line} fill="none" stroke="var(--color-ink)" strokeWidth="2" strokeLinejoin="round" strokeDasharray="0" />
        {weeks.map((w, i) => (
          <circle
            key={w.weekStart}
            cx={cx(i)}
            cy={yCum(w.cumulativeCount)}
            r={i === lastIdx ? 4.5 : 2.5}
            fill="var(--color-ink)"
            stroke="var(--color-surface)"
            strokeWidth="1.5"
          />
        ))}
        <text x={cx(lastIdx) + 8} y={yCum(last.cumulativeCount) + 4} fontSize="11" fontWeight="700" fill="var(--color-ink)">
          {last.cumulativeCount}개
        </text>

        {weeks.map((w, i) =>
          i % 2 === lastIdx % 2 ? (
            <text
              key={w.weekStart}
              x={cx(i)}
              y={H - 8}
              textAnchor="middle"
              fontSize="11"
              fontWeight={i === lastIdx ? 700 : 400}
              fill={i === lastIdx ? 'var(--color-ink)' : 'var(--color-ink-3)'}
            >
              {i === lastIdx ? '이번 주' : `${monthOf(w.weekStart)}/${dayOfMonth(w.weekStart)}`}
            </text>
          ) : null
        )}
      </svg>

      <div className="text-ink-3 mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px]">
        <span className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-[3px] bg-heat-2" /> 그 주에 완료한 Task
        </span>
        <span className="flex items-center gap-1.5">
          <span className="bg-ink h-0.5 w-4 rounded" /> 처음부터 누적
        </span>
      </div>

      {recentAvg > 0 && (
        <p className="bg-subtle text-ink-2 mt-4 rounded-2xl px-4 py-3 text-[14px] leading-relaxed">
          최근 4주는 한 주에 평균 <strong className="text-text-brand font-bold">{recentAvg}개</strong>
          {prevAvg > 0 && recentAvg > prevAvg ? (
            <>
              , 그 전 4주보다 <strong className="text-text-brand font-bold">{round1(recentAvg - prevAvg)}개 더</strong>{' '}
              완료하고 있어요
            </>
          ) : (
            ' 완료하고 있어요'
          )}
        </p>
      )}
    </Section>
  )
}
