import type { DailyPoint } from '../types'
import { dotDate } from '../format'
import { Empty } from './Panel'

const W = 640
const H = 220
const PAD = { left: 34, right: 12, top: 16, bottom: 28 }

const LINES = [
  { key: 'taskRate', label: 'Task 실천률', color: 'var(--color-brand)' },
  { key: 'routineRate', label: '루틴 실천률', color: 'var(--color-accent-mint)' },
] as const

/**
 * 날짜(또는 주)별 Task · 루틴 실천률 선 그래프.
 * SVG 로 직접 그려요 (패턴 화면 WeeklyTrend 와 같은 방식 — 차트 라이브러리를 추가하지 않으려고).
 * 해야 할 것이 없던 날(null)에서는 선을 끊어요 — 0% 와 "없음"은 다른 뜻이라서.
 */
export function RateTrend({ points }: { points: DailyPoint[] }) {
  const hasAny = points.some((p) => p.taskRate != null || p.routineRate != null)
  if (!hasAny) return <Empty>기간 안에 해야 했던 Task · 루틴이 없어요</Empty>

  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom
  const x = (i: number) => PAD.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (r: number) => PAD.top + (1 - r / 100) * innerH
  const labelEvery = Math.max(1, Math.ceil(points.length / 7))

  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Task · 루틴 실천률 추이">
        {[0, 25, 50, 75, 100].map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className="stroke-line" />
            <text x={PAD.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" className="fill-ink-3">
              {t}
            </text>
          </g>
        ))}

        {LINES.map(({ key, color }) => {
          const segments: { i: number; r: number }[][] = []
          let cur: { i: number; r: number }[] = []
          points.forEach((p, i) => {
            const r = p[key]
            if (r == null) {
              if (cur.length) segments.push(cur)
              cur = []
            } else cur.push({ i, r })
          })
          if (cur.length) segments.push(cur)
          return (
            <g key={key}>
              {segments.map((seg, si) => (
                <g key={si}>
                  {seg.length > 1 && (
                    <path
                      d={seg.map((p, k) => `${k ? 'L' : 'M'}${x(p.i)},${y(p.r)}`).join(' ')}
                      fill="none"
                      style={{ stroke: color }}
                      strokeWidth="2.25"
                      strokeLinejoin="round"
                    />
                  )}
                  {seg.map((p) => (
                    <circle key={p.i} cx={x(p.i)} cy={y(p.r)} r={points.length > 45 ? 2 : 3.5} style={{ fill: color }}>
                      <title>
                        {dotDate(points[p.i].date)} {key === 'taskRate' ? 'Task' : '루틴'} {p.r}% (
                        {key === 'taskRate'
                          ? `${points[p.i].taskDone}/${points[p.i].taskDue}`
                          : `${points[p.i].routineDone}/${points[p.i].routineDue}`}
                        )
                      </title>
                    </circle>
                  ))}
                </g>
              ))}
            </g>
          )
        })}

        {points.map((p, i) =>
          i % labelEvery === 0 || i === points.length - 1 ? (
            <text
              key={p.date}
              x={x(i)}
              y={H - 8}
              textAnchor={i === 0 ? 'start' : i === points.length - 1 ? 'end' : 'middle'}
              fontSize="11"
              className="fill-ink-3"
            >
              {dotDate(p.date)}
            </text>
          ) : null
        )}
      </svg>
      <div className="text-ink-2 mt-2 flex gap-4 text-[12px]">
        {LINES.map((l) => (
          <span key={l.key} className="inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-full" style={{ background: l.color }} />
            {l.label}
          </span>
        ))}
      </div>
    </div>
  )
}

/** 날짜(또는 주)별 막대 — 활동 사용자 수 · AI 비용 */
export function MiniBars({
  points,
  value,
  format,
  color = 'var(--color-brand)',
  label,
}: {
  points: DailyPoint[]
  value: (p: DailyPoint) => number
  format: (n: number) => string
  color?: string
  label: string
}) {
  const values = points.map(value)
  const max = Math.max(...values, 0)
  if (max <= 0) return <Empty>기간 안에 기록이 없어요</Empty>
  const h = 72
  const gap = points.length > 60 ? 1 : 2
  const bw = (W - gap * (points.length - 1)) / points.length
  return (
    <div>
      <p className="text-ink-3 text-right text-[11px] tabular-nums">최대 {format(max)}</p>
      <svg viewBox={`0 0 ${W} ${h}`} preserveAspectRatio="none" className="mt-1 h-20 w-full" role="img" aria-label={label}>
        {points.map((p, i) => {
          const bh = values[i] > 0 ? Math.max((values[i] / max) * h, 1.5) : 0
          return (
            <rect key={p.date} x={i * (bw + gap)} y={h - bh} width={bw} height={bh} rx="1.5" style={{ fill: color }}>
              <title>
                {dotDate(p.date)} {format(values[i])}
              </title>
            </rect>
          )
        })}
      </svg>
      <div className="text-ink-3 mt-1 flex justify-between text-[11px] tabular-nums">
        <span>{dotDate(points[0].date)}</span>
        <span>{dotDate(points[points.length - 1].date)}</span>
      </div>
    </div>
  )
}
