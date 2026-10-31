'use client'

import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import { cn } from '@/lib/cn'

/**
 * 목표 진행 꺾은선 (라이브러리 없이 SVG).
 *
 * - plan  : 계획선 (점선) — 날짜 사이 값은 이어서(보간) 읽어요
 * - actual: 실제 기록 (실선 + 점) — 기록한 날만 값이 있어요
 * - 오늘 세로선 + 지금 위치 큰 점 + 목표값 가로선
 * - 마우스/손가락을 올리면 그날의 계획 · 기록 값을 보여줘요
 */

const DAY = 86_400_000
export const dayNum = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number)
  return Math.round(Date.UTC(y, m - 1, d) / DAY)
}
const isoOf = (n: number) => new Date(n * DAY).toISOString().slice(0, 10)
const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const mdLabel = (n: number) => {
  const d = new Date(n * DAY)
  return `${d.getUTCMonth() + 1}.${d.getUTCDate()}`
}
const mdwLabel = (n: number) => `${mdLabel(n)} (${WEEK[new Date(n * DAY).getUTCDay()]})`

export interface ChartPoint {
  date: string
  value: number
  label?: string | null
}

export interface ChartSeries {
  key: string
  name: string
  kind: 'plan' | 'actual'
  points: ChartPoint[]
}

interface Props {
  series: ChartSeries[]
  from: string
  to: string
  today: string
  /** 목표값 가로선 */
  target?: { value: number; label: string }
  /** 지금 위치 (오늘 또는 마지막 기록 날) */
  current?: { date: string; value: number; label: string }
  format: (v: number) => string
  height?: number
  /** y축을 0부터 (Task 개수처럼 누적값) */
  zeroBased?: boolean
  className?: string
  /** 처음으로 그래프를 만져 값 말풍선을 볼 때 (이 그래프에서 한 번만) — 분석용 */
  onFirstInteract?: () => void
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    ro.observe(el)
    setWidth(el.getBoundingClientRect().width)
    return () => ro.disconnect()
  }, [])
  return [ref, width] as const
}

/** 보기 좋은 눈금 (1, 2, 2.5, 5 × 10^n) */
function niceTicks(min: number, max: number, count = 4) {
  if (min === max) {
    const pad = Math.abs(min) * 0.1 || 1
    min -= pad
    max += pad
  }
  const raw = (max - min) / count
  const mag = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw
  const lo = Math.floor(min / step) * step
  const hi = Math.ceil(max / step) * step
  const ticks: number[] = []
  for (let v = lo; v <= hi + step / 2; v += step) ticks.push(Math.round(v * 1e6) / 1e6)
  return { lo, hi, ticks }
}

/** 계획선 위 x 날의 값 (양 끝 밖이면 null) */
function interpolate(points: { x: number; y: number }[], x: number) {
  if (points.length === 0 || x < points[0].x || x > points[points.length - 1].x) return null
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]
    const b = points[i]
    if (x <= b.x) return b.x === a.x ? b.y : a.y + ((b.y - a.y) * (x - a.x)) / (b.x - a.x)
  }
  return points[0].x === x ? points[0].y : null
}

const PAD = { top: 16, right: 16, bottom: 28, left: 44 }

export function ProgressChart({
  series,
  from,
  to,
  today,
  target,
  current,
  format,
  height = 220,
  zeroBased = false,
  className,
  onFirstInteract,
}: Props) {
  const [wrapRef, width] = useWidth<HTMLDivElement>()
  const [hoverX, setHoverX] = useState<number | null>(null)
  const interacted = useRef(false)

  const x0 = dayNum(from)
  const x1 = Math.max(dayNum(to), x0 + 1)
  const todayX = dayNum(today)

  const prepared = useMemo(
    () =>
      series.map((s) => ({
        ...s,
        xy: s.points.map((p) => ({ x: dayNum(p.date), y: p.value, label: p.label ?? null })).sort((a, b) => a.x - b.x),
      })),
    [series]
  )

  const { lo, hi, ticks } = useMemo(() => {
    const ys = prepared.flatMap((s) => s.xy.map((p) => p.y))
    if (target) ys.push(target.value)
    if (current) ys.push(current.value)
    if (zeroBased) ys.push(0)
    const min = Math.min(...ys)
    const max = Math.max(...ys)
    return niceTicks(Number.isFinite(min) ? min : 0, Number.isFinite(max) ? max : 1)
  }, [prepared, target, current, zeroBased])

  const innerW = Math.max(width - PAD.left - PAD.right, 10)
  const innerH = height - PAD.top - PAD.bottom
  const sx = (x: number) => PAD.left + ((x - x0) / (x1 - x0)) * innerW
  const sy = (y: number) => PAD.top + (1 - (y - lo) / (hi - lo || 1)) * innerH

  // x 눈금: 시작 · 끝 포함 4~5개
  const xTicks = useMemo(() => {
    const n = width < 420 ? 3 : 5
    const out: number[] = []
    for (let i = 0; i < n; i++) out.push(Math.round(x0 + ((x1 - x0) * i) / (n - 1)))
    return [...new Set(out)]
  }, [x0, x1, width])

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const px = e.clientX - rect.left
    const x = Math.round(x0 + ((px - PAD.left) / innerW) * (x1 - x0))
    setHoverX(Math.min(Math.max(x, x0), x1))
    if (!interacted.current) {
      interacted.current = true
      onFirstInteract?.()
    }
  }

  const hover =
    hoverX == null
      ? null
      : prepared
          .map((s) => {
            const y = s.kind === 'plan' ? interpolate(s.xy, hoverX) : (s.xy.find((p) => p.x === hoverX)?.y ?? null)
            return { key: s.key, name: s.name, kind: s.kind, y }
          })
          .filter((r) => r.y != null)

  const showToday = todayX >= x0 && todayX <= x1

  return (
    <div ref={wrapRef} className={cn('relative w-full select-none', className)} style={{ height }}>
      {width > 0 && (
        <svg
          width={width}
          height={height}
          className="touch-pan-y overflow-visible"
          onPointerMove={onMove}
          onPointerDown={onMove}
          onPointerLeave={() => setHoverX(null)}
          role="img"
          aria-label="목표 진행 그래프"
        >
          {/* y 눈금 */}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={width - PAD.right} y1={sy(t)} y2={sy(t)} className="stroke-line" />
              <text x={PAD.left - 8} y={sy(t)} dy="0.32em" textAnchor="end" className="fill-ink-4 text-[11px]">
                {format(t)}
              </text>
            </g>
          ))}
          {/* x 눈금 */}
          {xTicks.map((x, i) => (
            <text
              key={x}
              x={sx(x)}
              y={height - 8}
              textAnchor={i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'}
              className="fill-ink-4 text-[11px]"
            >
              {mdLabel(x)}
            </text>
          ))}

          {/* 목표값 */}
          {target && (
            <g>
              <line
                x1={PAD.left}
                x2={width - PAD.right}
                y1={sy(target.value)}
                y2={sy(target.value)}
                className="stroke-success"
                strokeOpacity={0.6}
                strokeDasharray="2 4"
              />
              <text
                x={width - PAD.right}
                y={sy(target.value)}
                dy={sy(target.value) < PAD.top + 14 ? 14 : -6}
                textAnchor="end"
                className="fill-success text-[11px] font-semibold"
              >
                {target.label}
              </text>
            </g>
          )}

          {/* 오늘 */}
          {showToday && (
            <g>
              <line
                x1={sx(todayX)}
                x2={sx(todayX)}
                y1={PAD.top}
                y2={PAD.top + innerH}
                className="stroke-ink-3"
                strokeDasharray="3 3"
              />
              <text x={sx(todayX)} y={PAD.top - 4} textAnchor="middle" className="fill-ink-3 text-[11px] font-semibold">
                오늘
              </text>
            </g>
          )}

          {/* 선 */}
          {prepared.map((s) =>
            s.xy.length === 0 ? null : (
              <g key={s.key}>
                <polyline
                  points={s.xy.map((p) => `${sx(p.x)},${sy(p.y)}`).join(' ')}
                  fill="none"
                  className={s.kind === 'plan' ? 'stroke-ink-4' : 'stroke-brand'}
                  strokeWidth={s.kind === 'plan' ? 2 : 2.5}
                  strokeDasharray={s.kind === 'plan' ? '6 5' : undefined}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
                {s.kind === 'plan'
                  ? // 계획선의 꺾이는 점(마일스톤)
                    s.xy
                      .slice(1, -1)
                      .filter((p) => p.label)
                      .map((p) => (
                        <circle
                          key={p.x}
                          cx={sx(p.x)}
                          cy={sy(p.y)}
                          r={3.5}
                          className="fill-surface stroke-ink-4"
                          strokeWidth={2}
                        >
                          {p.label && <title>{`${p.label} · ${mdLabel(p.x)} · ${format(p.y)}`}</title>}
                        </circle>
                      ))
                  : s.xy.length <= 60 &&
                    s.xy.map((p) => <circle key={p.x} cx={sx(p.x)} cy={sy(p.y)} r={3} className="fill-brand" />)}
              </g>
            )
          )}

          {/* 지금 위치 */}
          {current && dayNum(current.date) >= x0 && dayNum(current.date) <= x1 && (
            <g>
              <circle
                cx={sx(dayNum(current.date))}
                cy={sy(current.value)}
                r={11}
                className="fill-brand"
                opacity={0.18}
              />
              <circle
                cx={sx(dayNum(current.date))}
                cy={sy(current.value)}
                r={6}
                className="fill-brand stroke-surface"
                strokeWidth={2.5}
              />
            </g>
          )}

          {/* 호버 */}
          {hoverX != null && (
            <line
              x1={sx(hoverX)}
              x2={sx(hoverX)}
              y1={PAD.top}
              y2={PAD.top + innerH}
              className="stroke-ink-2"
              strokeOpacity={0.35}
            />
          )}
        </svg>
      )}

      {/* 지금 위치 말풍선 (호버 중엔 숨김) */}
      {width > 0 && current && hoverX == null && dayNum(current.date) >= x0 && dayNum(current.date) <= x1 && (
        <span
          className="bg-brand pointer-events-none absolute -translate-x-1/2 rounded-full px-2 py-0.5 text-[11px] font-bold whitespace-nowrap text-white shadow-sm"
          style={{
            left: Math.min(Math.max(sx(dayNum(current.date)), 40), width - 40),
            top: Math.max(sy(current.value) - 32, 0),
          }}
        >
          {current.label}
        </span>
      )}

      {width > 0 && hoverX != null && hover && hover.length > 0 && (
        <div
          className="border-line bg-surface pointer-events-none absolute top-2 z-10 min-w-[120px] rounded-xl border px-3 py-2 text-[12px] shadow-md"
          style={sx(hoverX) > width / 2 ? { right: width - sx(hoverX) + 10 } : { left: sx(hoverX) + 10 }}
        >
          <p className="text-ink-3 mb-1 font-medium">
            {mdwLabel(hoverX)}
            {isoOf(hoverX) === today && ' · 오늘'}
          </p>
          {hover.map((r) => (
            <p key={r.key} className="flex items-center justify-between gap-3">
              <span className="text-ink-3 flex items-center gap-1.5">
                <i className={cn('inline-block h-0.5 w-3 rounded', r.kind === 'plan' ? 'bg-ink-4' : 'bg-brand')} />
                {r.name}
              </span>
              <b>{format(r.y!)}</b>
            </p>
          ))}
        </div>
      )}
    </div>
  )
}
