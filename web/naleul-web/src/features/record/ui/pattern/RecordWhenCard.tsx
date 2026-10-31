import type { PointerEvent } from 'react'
import { JAVA_DAYS } from '@/features/goal/format'
import { DAY_SHORT } from '@/features/pattern/format'
import { NotEnough, Section } from '@/features/pattern/ui/Section'
import type { RecordPattern } from '../../api'
import { BAND_LABEL, BAND_RANGE, BANDS, LEVEL_BG, levelOf, shortDuration } from './format'

/** 언제 이 일을 하나요? — 요일 × 시간대 (완료한 Task 의 실제·계획 시각 기준, 처음부터) */
export function RecordWhenCard({
  when,
  wide = false,
  onCellHover,
}: {
  when: RecordPattern['when']
  /** 한 줄을 혼자 쓸 때(루틴 카드가 없을 때): 넓은 화면에서 표와 강조 박스를 옆으로 나란히 */
  wide?: boolean
  /** 마우스를 칸에 올려 말풍선을 볼 때 (분석용) */
  onCellHover?: () => void
}) {
  const hover = (e: PointerEvent) => {
    if (e.pointerType === 'mouse') onCellHover?.()
  }
  const total = when.bands.reduce((n, b) => n + b.count, 0)
  const cell = new Map(when.cells.map((c) => [`${c.dayOfWeek}-${c.band}`, c]))
  const max = Math.max(0, ...when.cells.map((c) => c.count))
  const peak = when.peakDay && when.peakBand ? cell.get(`${when.peakDay}-${when.peakBand}`) : null
  const topBand = [...when.bands].sort((a, b) => b.count - a.count)[0]

  return (
    <Section title="언제 이 일을 하나요?" aside="요일 × 시간대">
      {total < 3 ? (
        <NotEnough>시간을 정한 Task를 3개 이상 완료하면 주로 언제 하는지 보여드려요</NotEnough>
      ) : (
        <div className={wide ? 'xl:grid xl:grid-cols-[minmax(0,1fr)_300px] xl:items-start xl:gap-6' : undefined}>
          <table className="w-full border-separate border-spacing-[3px] text-center">
            <thead>
              <tr>
                <th className="w-6" />
                {BANDS.map((b) => (
                  <th key={b} scope="col" className="text-ink-3 pb-1 text-[11px] font-normal">
                    {BAND_LABEL[b]}
                    <span className="text-ink-4 block text-[10px]">{BAND_RANGE[b]}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {JAVA_DAYS.map((d) => (
                <tr key={d}>
                  <th scope="row" className="text-ink-2 pr-1 text-left text-[12px] font-medium">
                    {DAY_SHORT[d]}
                  </th>
                  {BANDS.map((b) => {
                    const c = cell.get(`${d}-${b}`)
                    const level = c ? levelOf(c.count, max) : 0
                    return (
                      <td
                        key={b}
                        onPointerEnter={hover}
                        title={`${DAY_SHORT[d]} ${BAND_LABEL[b]} · ${c?.count ? `Task ${c.count}개${c.minutes ? ` · ${shortDuration(c.minutes)}` : ''}` : '완료 없음'}`}
                        className={
                          level
                            ? `h-9 rounded-md text-[11px] font-semibold ${level >= 3 ? 'text-white' : 'text-ink-2'}`
                            : 'bg-subtle h-9 rounded-md'
                        }
                        style={level ? { background: LEVEL_BG[level] } : undefined}
                      >
                        {c?.count ? `${c.count}개` : ''}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>

          {peak && when.peakDay && when.peakBand && (
            <div
              className={
                wide ? 'mt-4 grid gap-3 sm:grid-cols-2 xl:mt-0 xl:grid-cols-1' : 'mt-4 grid gap-3 sm:grid-cols-2'
              }
            >
              <div className="bg-brand-soft rounded-2xl px-4 py-3.5">
                <p className="text-text-brand text-[13px] font-bold">🌟 가장 많이 끝내는 때</p>
                <p className="mt-0.5 text-[16px] font-bold">
                  {DAY_SHORT[when.peakDay]}요일 {BAND_LABEL[when.peakBand]}
                </p>
                <p className="text-ink-3 text-[13px]">
                  Task {peak.count}개 · 전체의 {Math.round((peak.count / Math.max(total, 1)) * 100)}%
                </p>
              </div>
              {topBand && topBand.count > 0 && (
                <div className="bg-subtle rounded-2xl px-4 py-3.5">
                  <p className="text-ink-2 text-[13px] font-bold">⏰ 주로 하는 시간대</p>
                  <p className="mt-0.5 text-[16px] font-bold">
                    {BAND_LABEL[topBand.band]} {BAND_RANGE[topBand.band]}
                  </p>
                  <p className="text-ink-3 text-[13px]">
                    완료한 Task의 {Math.round((topBand.count / Math.max(total, 1)) * 100)}%
                  </p>
                </div>
              )}
            </div>
          )}
          {when.untimedCount > 0 && (
            <p className="text-ink-3 mt-3 text-[12px] xl:col-span-2">
              시간을 정하지 않고 완료한 Task {when.untimedCount}개는 이 표에서 뺐어요.
            </p>
          )}
        </div>
      )}
    </Section>
  )
}
