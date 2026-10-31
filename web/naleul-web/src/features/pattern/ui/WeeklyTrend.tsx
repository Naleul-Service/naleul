'use client'

import { useId } from 'react'
import { useChartInteract } from '../analytics'
import type { PatternWeekly } from '../types'
import { NotEnough, Section } from './Section'

const W = 600
const H = 250
const PAD = { left: 36, right: 16, top: 24, bottom: 30 }

/** 주차별 실행률 선 그래프 (SVG 로 직접 그림 — 차트 라이브러리를 추가하지 않으려고) */
export function WeeklyTrend({ weekly }: { weekly?: PatternWeekly | null }) {
  const gradientId = useId()
  const chartProps = useChartInteract('weekly')
  const points = weekly?.points ?? []
  const valued = points.filter((p) => p.rate != null)

  if (valued.length < 2) {
    return (
      <Section title="주차별 흐름" aside="실행률 %">
        <NotEnough>2주 이상 기록이 쌓이면 흐름을 보여드려요</NotEnough>
      </Section>
    )
  }

  const rates = valued.map((p) => p.rate!)
  const yMin = Math.max(0, Math.floor((Math.min(...rates) - 10) / 10) * 10)
  const yMax = 100
  const ticks = Array.from({ length: Math.round((yMax - yMin) / 10) + 1 }, (_, i) => yMin + i * 10).filter(
    (_, i, all) => all.length <= 6 || i % 2 === 0 || i === all.length - 1
  )
  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom
  const x = (i: number) => PAD.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (r: number) => PAD.top + (1 - (r - yMin) / (yMax - yMin)) * innerH

  // 표본이 부족한 주(null)에서 선을 끊는다
  const segments: { i: number; r: number }[][] = []
  let cur: { i: number; r: number }[] = []
  points.forEach((p, i) => {
    if (p.rate == null) {
      if (cur.length) segments.push(cur)
      cur = []
    } else cur.push({ i, r: p.rate })
  })
  if (cur.length) segments.push(cur)

  const firstIdx = points.findIndex((p) => p.rate != null)
  const maxIdx = weekly?.maxIndex != null ? points.findIndex((p) => p.index === weekly.maxIndex) : -1
  const lastIdx = points.length - 1
  const labelEvery = points.length > 12 ? Math.ceil(points.length / 8) : 1

  return (
    <Section title="주차별 흐름" aside="실행률 %">
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="h-auto w-full"
        role="img"
        aria-label="주차별 실행률 그래프"
        {...chartProps}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--color-brand)" stopOpacity="0.16" />
            <stop offset="100%" stopColor="var(--color-brand)" stopOpacity="0" />
          </linearGradient>
        </defs>

        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--color-line)" />
            <text x={PAD.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--color-ink-3)">
              {t}
            </text>
          </g>
        ))}

        {segments.map((seg, si) => {
          const line = seg.map((p, k) => `${k ? 'L' : 'M'}${x(p.i)},${y(p.r)}`).join(' ')
          const area = `${line} L${x(seg[seg.length - 1].i)},${PAD.top + innerH} L${x(seg[0].i)},${PAD.top + innerH} Z`
          return (
            <g key={si}>
              {seg.length > 1 && <path d={area} fill={`url(#${gradientId})`} />}
              <path d={line} fill="none" stroke="var(--color-brand)" strokeWidth="2.5" strokeLinejoin="round" />
              {seg.map((p) => (
                <circle
                  key={p.i}
                  cx={x(p.i)}
                  cy={y(p.r)}
                  r="4.5"
                  fill="var(--color-brand)"
                  stroke="var(--color-surface)"
                  strokeWidth="2"
                />
              ))}
            </g>
          )
        })}

        {maxIdx >= 0 && points[maxIdx].rate != null && (
          <text
            x={x(maxIdx)}
            y={y(points[maxIdx].rate!) - 12}
            textAnchor="middle"
            fontSize="12"
            fontWeight="700"
            fill="var(--color-ink)"
          >
            {points[maxIdx].rate}%
          </text>
        )}
        {firstIdx >= 0 && firstIdx !== maxIdx && (
          <text x={x(firstIdx) + 8} y={y(points[firstIdx].rate!) + 18} fontSize="11" fill="var(--color-ink-3)">
            {points[firstIdx].rate}%
          </text>
        )}

        {points.map((p, i) =>
          i % labelEvery === 0 || i === lastIdx ? (
            <text
              key={p.index}
              x={x(i)}
              y={H - 8}
              textAnchor="middle"
              fontSize="11"
              fontWeight={i === lastIdx ? 700 : 400}
              fill={i === lastIdx ? 'var(--color-ink)' : 'var(--color-ink-3)'}
            >
              {p.index}주
            </text>
          ) : null
        )}
      </svg>
    </Section>
  )
}
