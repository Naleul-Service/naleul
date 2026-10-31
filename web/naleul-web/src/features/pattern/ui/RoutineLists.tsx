'use client'

import type { PatternRoutines } from '../types'
import { useDeadClick } from '../analytics'
import { Card } from '@/components/ui/Card'

/** 잘 지키는 루틴 / 자주 놓치는 블록 */
export function RoutineLists({ routines }: { routines?: PatternRoutines | null }) {
  const kept = routines?.kept ?? []
  const missed = routines?.missed ?? []
  const onKeptClick = useDeadClick('kept_routine')
  const onMissedClick = useDeadClick('missed_block')
  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-[16px] font-bold">💪 잘 지키는 루틴</h2>
      {kept.length ? (
        <ul className="mt-3 flex flex-col gap-3">
          {kept.map((r) => (
            <li key={r.routineId} className="flex items-center justify-between gap-3" onClick={onKeptClick}>
              <span className="min-w-0 truncate text-[15px] font-semibold">
                {r.emoji && <span className="mr-2">{r.emoji}</span>}
                {r.title}
              </span>
              <span className="text-success text-[15px] font-bold">{r.rate}%</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-ink-3 mt-3 text-[13px]">4번 이상 한 루틴 중 75% 넘게 지킨 루틴이 여기에 보여요</p>
      )}

      <div className="border-line my-5 border-t" />

      <h2 className="text-[16px] font-bold">🫠 자주 놓치는 블록</h2>
      {missed.length ? (
        <ul className="mt-3 flex flex-col gap-4">
          {missed.map((r) => (
            <li
              key={`${r.routineId}-${r.label}`}
              className="flex items-center justify-between gap-3"
              onClick={onMissedClick}
            >
              <div className="min-w-0">
                <p className="truncate text-[15px] font-semibold">
                  {r.emoji && <span className="mr-2">{r.emoji}</span>}
                  {r.label}
                </p>
                <p className="text-ink-3 mt-0.5 text-[13px]">{r.description}</p>
              </div>
              <span className="text-danger text-[15px] font-bold">{r.rate}%</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-ink-3 mt-3 text-[13px]">3번 이상 한 루틴 중 자주 놓친 게 없어요 👏</p>
      )}
    </Card>
  )
}
