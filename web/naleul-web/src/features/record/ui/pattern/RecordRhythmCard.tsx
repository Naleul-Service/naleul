import type { PointerEvent } from 'react'
import { NotEnough, Section } from '@/features/pattern/ui/Section'
import { addDays, formatMonthDay, monthOf, todayKst } from '@/features/timetable/time'
import type { RecordPattern } from '../../api'
import { LEVEL_BG, levelOf, shortDuration } from './format'

const ROW_LABEL = ['월', '', '수', '', '금', '', '일']

/**
 * 완료의 리듬 — 최근 16주 잔디 (하루 한 칸, Task 를 많이 끝낸 날일수록 진하게).
 * 기록형 목표의 핵심은 "얼마나"보다 "끊기지 않았는지"라서, 빈칸·채운 칸이 한눈에 보이게 했어요.
 */
export function RecordRhythmCard({
  rhythm,
  onCellHover,
}: {
  rhythm: RecordPattern['rhythm']
  /** 마우스를 칸에 올려 말풍선을 볼 때 (분석용) */
  onCellHover?: () => void
}) {
  const today = todayKst()
  const hover = (e: PointerEvent) => {
    if (e.pointerType === 'mouse') onCellHover?.()
  }
  const byDate = new Map(rhythm.days.map((d) => [d.date, d]))
  const max = Math.max(0, ...rhythm.days.map((d) => d.count))
  const weeks = Array.from({ length: 16 }, (_, w) => addDays(rhythm.heatmapStart, w * 7))
  const recordedWeeks = weeks.filter((w) =>
    Array.from({ length: 7 }, (_, i) => addDays(w, i)).some((d) => byDate.has(d))
  ).length
  const best = rhythm.days.reduce<(typeof rhythm.days)[number] | null>(
    (b, d) => (!b || d.count > b.count ? d : b),
    null
  )

  return (
    <Section title="완료의 리듬" aside="최근 16주">
      {rhythm.days.length === 0 ? (
        <NotEnough>Task를 완료하면 하루 한 칸씩 채워져요</NotEnough>
      ) : (
        <>
          <div className="-mx-1 overflow-x-auto px-1 pb-1">
            <div className="flex min-w-[420px] gap-2">
              {/* 요일 */}
              <div className="grid grid-rows-[16px_repeat(7,minmax(0,1fr))] gap-[3px] pt-px">
                <span />
                {ROW_LABEL.map((l, i) => (
                  <span key={i} className="text-ink-3 flex items-center text-[11px] leading-none">
                    {l}
                  </span>
                ))}
              </div>
              <div className="grid flex-1 grid-cols-[repeat(16,minmax(0,1fr))] gap-[3px]">
                {weeks.map((w, wi) => {
                  const showMonth = wi === 0 || monthOf(w) !== monthOf(weeks[wi - 1])
                  return (
                    <div key={w} className="grid grid-rows-[16px_repeat(7,auto)] gap-[3px]">
                      <span className="text-ink-3 text-[11px] leading-4 whitespace-nowrap">
                        {showMonth ? `${monthOf(w)}월` : ''}
                      </span>
                      {Array.from({ length: 7 }, (_, i) => {
                        const d = addDays(w, i)
                        const stat = byDate.get(d)
                        const level = stat ? levelOf(stat.count, max) : 0
                        const future = d > today
                        return (
                          <span
                            key={d}
                            onPointerEnter={future ? undefined : hover}
                            title={
                              future
                                ? undefined
                                : `${formatMonthDay(d)} · ${stat ? `Task ${stat.count}개${stat.minutes ? ` · ${shortDuration(stat.minutes)}` : ''}` : '완료 없음'}`
                            }
                            className={
                              future
                                ? 'aspect-square rounded-[4px]'
                                : level
                                  ? 'aspect-square rounded-[4px]'
                                  : 'bg-subtle aspect-square rounded-[4px]'
                            }
                            style={level ? { background: LEVEL_BG[level] } : undefined}
                          />
                        )
                      })}
                    </div>
                  )
                })}
              </div>
            </div>
          </div>

          <div className="text-ink-3 mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-[12px]">
            <span className="flex items-center gap-1.5">
              적게
              <span className="bg-subtle h-3 w-3 rounded-[3px]" />
              {[1, 2, 3, 4].map((l) => (
                <span key={l} className="h-3 w-3 rounded-[3px]" style={{ background: LEVEL_BG[l] }} />
              ))}
              많이
            </span>
            {best && (
              <span>
                가장 많이 끝낸 날 · {formatMonthDay(best.date)} {best.count}개
              </span>
            )}
          </div>

          <p className="bg-subtle text-ink-2 mt-4 rounded-2xl px-4 py-3 text-[14px] leading-relaxed">
            16주 중 <strong className="text-text-brand font-bold">{recordedWeeks}주</strong> Task를 완료했어요
            {recordedWeeks >= 12 ? '. 거의 매주 이어오고 있어요' : recordedWeeks >= 6 ? '. 리듬이 생기고 있어요' : ''}
          </p>
        </>
      )}
    </Section>
  )
}
