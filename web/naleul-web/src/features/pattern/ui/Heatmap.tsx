'use client'

import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { TrendingDownIcon, TrendingUpIcon } from '@/components/icons'
import type { HeatCell, PatternHeatmap } from '../types'
import { useChartInteract, useDeadClick } from '../analytics'
import { DAY_SHORT, HATCH, HEAT_BG, heatText, windowLabel } from '../format'
import { NotEnough, Section } from './Section'

/**
 * 언제 가장 잘 실행하나요? — 요일 × 시간에 실제로 한 횟수
 * 계획한 시간과 상관없이, 그 시간대에 Task · 루틴을 했으면 +1 이에요.
 * 가장 많이 한 칸이 가장 진하고, 한 번도 안 한 칸은 색이 없어요.
 */
/** 말풍선 문구 — "화 · 09시 · 실행률 72% (5/7)" */
function tipText(day: string, c: HeatCell, fixedLabel?: string | null) {
  const head = `${day} · ${String(c.hour).padStart(2, '0')}시`
  if (c.state === 'FIXED') return `${head} · ${fixedLabel ?? '고정 시간'}`
  if (c.state !== 'VALUE' || !c.level) return `${head} · 한 게 없어요`
  if (c.rate != null) return `${head} · 실행률 ${c.rate}%${c.samples ? ` (${c.count ?? 0}/${c.samples})` : ''}`
  return `${head} · ${c.count ?? 0}번 했어요`
}

interface Tip {
  key: string
  text: string
  /** 감싼 상자 기준 칸 가운데 x · 위 · 아래 */
  x: number
  top: number
  bottom: number
  /** 눌러서 고정한 말풍선 (마우스 올려 미리보기는 false) */
  pinned: boolean
}

/** 말풍선이 들어갈 자리가 칸 위에 없으면(이만큼보다 위) 아래로 뒤집어요 */
const FLIP_PX = 44

export function Heatmap({ heatmap }: { heatmap?: PatternHeatmap | null }) {
  const hasValue = heatmap?.rows.some((r) => r.cells.some((c) => c.state === 'VALUE'))
  const chartProps = useChartInteract('heatmap')
  const onGoldenClick = useDeadClick('golden_time')
  const boxRef = useRef<HTMLDivElement>(null)
  const tipRef = useRef<HTMLDivElement>(null)
  const [tip, setTip] = useState<Tip | null>(null)

  // 칸을 눌렀을 때(또는 마우스를 올렸을 때) 그 칸 위에 말풍선
  const show = (e: ReactPointerEvent<HTMLElement> | React.MouseEvent<HTMLElement>, key: string, text: string, pinned: boolean) => {
    const box = boxRef.current
    if (!box) return
    const b = box.getBoundingClientRect()
    const r = e.currentTarget.getBoundingClientRect()
    setTip({ key, text, x: r.left + r.width / 2 - b.left, top: r.top - b.top, bottom: r.bottom - b.top, pinned })
  }

  // 카드 안으로 가로 위치를 맞춰요 (그리기 전에 → 깜빡임 없음)
  useLayoutEffect(() => {
    const el = tipRef.current
    const box = boxRef.current
    if (!el || !box || !tip) return
    const w = el.offsetWidth
    const left = Math.min(Math.max(tip.x - w / 2, 0), box.clientWidth - w)
    el.style.left = `${left}px`
    const tail = el.querySelector<HTMLElement>('[data-tail]')
    if (tail) tail.style.left = `${Math.min(Math.max(tip.x - left, 10), w - 10)}px`
  }, [tip])

  // 바깥을 누르거나 Esc · 가로 스크롤 하면 닫혀요
  useEffect(() => {
    if (!tip?.pinned) return
    const onDown = (e: PointerEvent) => {
      if (!(e.target instanceof Element) || !e.target.closest('[data-heat-cell]')) setTip(null)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setTip(null)
    }
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [tip?.pinned])

  const cellProps = (key: string, text: string) => ({
    'data-heat-cell': true,
    role: 'button' as const,
    tabIndex: 0,
    'aria-label': text,
    onClick: (e: React.MouseEvent<HTMLElement>) =>
      tip?.pinned && tip.key === key ? setTip(null) : show(e, key, text, true),
    onKeyDown: (e: React.KeyboardEvent<HTMLElement>) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        const box = boxRef.current
        if (!box) return
        const b = box.getBoundingClientRect()
        const r = e.currentTarget.getBoundingClientRect()
        setTip({ key, text, x: r.left + r.width / 2 - b.left, top: r.top - b.top, bottom: r.bottom - b.top, pinned: true })
      }
    },
    // 넓은 화면 마우스: 올리면 미리보기 (눌러서 고정한 게 있으면 그대로)
    onPointerEnter: (e: ReactPointerEvent<HTMLElement>) => {
      if (e.pointerType === 'mouse' && !tip?.pinned) show(e, key, text, false)
    },
    onPointerLeave: (e: ReactPointerEvent<HTMLElement>) => {
      if (e.pointerType === 'mouse') setTip((t) => (t && !t.pinned ? null : t))
    },
  })
  return (
    <Section title="언제 가장 잘 실행하나요?" aside="요일 × 시간 · 한 횟수">
      {!heatmap || !hasValue ? (
        <NotEnough>Task나 루틴을 완료하면 언제 많이 했는지 보여요</NotEnough>
      ) : (
        <>
          <div ref={boxRef} className="relative">
          {/* 좁은 화면에서는 카드 안에서만 가로 스크롤 */}
          <div
            className="-mx-1 overflow-x-auto px-1 pb-1"
            {...chartProps}
            onScroll={() => setTip(null)}
          >
            <table className="w-full min-w-[560px] border-separate border-spacing-[3px] text-center">
              <thead>
                <tr>
                  <th className="w-6" />
                  {heatmap.hours.map((h) => (
                    <th key={h} scope="col" className="text-ink-3 pb-1 text-[11px] font-normal">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {heatmap.rows.map((row) => (
                  <tr key={row.dayOfWeek}>
                    <th scope="row" className="text-ink-2 pr-1 text-left text-[12px] font-medium">
                      {DAY_SHORT[row.dayOfWeek]}
                    </th>
                    {row.cells.map((c) => {
                      const key = `${row.dayOfWeek}-${c.hour}`
                      const props = cellProps(key, tipText(DAY_SHORT[row.dayOfWeek], c, heatmap.fixedLabel))
                      const on = tip?.key === key
                      if (c.state === 'VALUE' && c.level) {
                        return (
                          <td
                            key={c.hour}
                            {...props}
                            className={`h-9 cursor-pointer rounded-md text-[11px] font-semibold ${heatText(c.level)} ${on ? 'ring-text-primary ring-2 ring-inset' : ''}`}
                            style={{ background: HEAT_BG[c.level] }}
                          >
                            {c.count ?? ''}
                          </td>
                        )
                      }
                      if (c.state === 'FIXED') {
                        return (
                          <td
                            key={c.hour}
                            {...props}
                            className={`h-9 cursor-pointer rounded-md ${on ? 'ring-text-primary ring-2 ring-inset' : ''}`}
                            style={{ background: HATCH }}
                          />
                        )
                      }
                      return (
                        <td
                          key={c.hour}
                          {...props}
                          className={`border-line h-9 cursor-pointer rounded-md border border-dashed ${on ? 'ring-text-primary ring-2 ring-inset' : ''}`}
                        />
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 말풍선 — bg/inverse · Caption/MD · 꼬리 · 카드 안으로 맞추고 위 공간이 없으면 아래로 */}
          {tip && (
            <div
              ref={tipRef}
              role="tooltip"
              className="bg-bg-inverse text-text-inverse text-caption-md pointer-events-none absolute z-20 rounded-[8px] px-2.5 py-1.5 whitespace-nowrap shadow-pop"
              style={
                tip.top < FLIP_PX
                  ? { top: tip.bottom + 8, left: tip.x }
                  : { top: tip.top - 8, left: tip.x, transform: 'translateY(-100%)' }
              }
            >
              {tip.text}
              <span
                data-tail
                aria-hidden
                className={`bg-bg-inverse absolute size-2 -translate-x-1/2 rotate-45 ${tip.top < FLIP_PX ? '-top-1' : '-bottom-1'}`}
              />
            </div>
          )}
          </div>

          <div className="text-ink-3 mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[12px]">
            <span className="flex items-center gap-1.5">
              적게
              {[1, 2, 3, 4, 5].map((l) => (
                <span key={l} className="h-3 w-4 rounded-[3px]" style={{ background: HEAT_BG[l] }} />
              ))}
              많이{heatmap.maxCount ? ` (최대 ${heatmap.maxCount}번)` : ''}
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-4 rounded-[3px]" style={{ background: HATCH }} />
              {heatmap.fixedLabel ?? '고정 시간'}
            </span>
            <span className="flex items-center gap-1.5">
              <span className="border-line h-3 w-4 rounded-[3px] border border-dashed" />한 게 없음
            </span>
          </div>

          {(heatmap.goldenTime || heatmap.weakTime) && (
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              {heatmap.goldenTime && (
                <div className="bg-brand-soft flex items-center gap-3 rounded-2xl px-4 py-3.5" onClick={onGoldenClick}>
                  <span className="text-label-md text-text-brand flex items-center gap-2 whitespace-nowrap">
                    <span className="bg-status-success-bg text-status-success-fg grid size-7 shrink-0 place-items-center rounded-[8px]">
                      <TrendingUpIcon className="size-4" />
                    </span>
                    골든 타임
                  </span>
                  <div>
                    <p className="text-[16px] font-bold">{windowLabel(heatmap.goldenTime)}</p>
                    <p className="text-ink-3 text-[13px]">실행률 {heatmap.goldenTime.rate}%</p>
                  </div>
                </div>
              )}
              {heatmap.weakTime && (
                <div className="bg-danger-soft flex items-center gap-3 rounded-2xl px-4 py-3.5">
                  <span className="text-label-md text-danger flex items-center gap-2 whitespace-nowrap">
                    <span className="bg-status-warning-bg text-status-warning-fg grid size-7 shrink-0 place-items-center rounded-[8px]">
                      <TrendingDownIcon className="size-4" />
                    </span>
                    취약 시간
                  </span>
                  <div>
                    <p className="text-[16px] font-bold">{windowLabel(heatmap.weakTime)}</p>
                    <p className="text-ink-3 text-[13px]">실행률 {heatmap.weakTime.rate}%</p>
                  </div>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </Section>
  )
}
