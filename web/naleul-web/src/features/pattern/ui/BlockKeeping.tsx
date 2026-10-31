'use client'

import { useState } from 'react'
import { Repeat } from '@/components/icons'
import { cn } from '@/lib/cn'
import { goalAccent } from '@/lib/goalTone'
import type { BlockKeepItem, PatternBlockKeeping } from '../types'
import { track } from '@/lib/client/analytics'
import { useDeadClick, usePatternAnalytics } from '../analytics'
import { NotEnough, Section } from './Section'

type KeepKind = 'onTime' | 'late' | 'missed'

/** keep_kind 이벤트 값 */
const KEEP_KIND_PROP: Record<KeepKind, string> = { onTime: 'on_time', late: 'late', missed: 'missed' }

const KINDS: { kind: KeepKind; label: string; color: string; text: string }[] = [
  { kind: 'onTime', label: '제시간 시작', color: 'bg-success', text: 'text-success' },
  { kind: 'late', label: '늦게 시작', color: 'bg-warning', text: 'text-warning' },
  { kind: 'missed', label: '놓침', color: 'bg-danger', text: 'text-danger' },
]

/** 블록을 어떻게 지키나요? — 제시간 / 늦게 / 놓침 + 자주 옮기는 블록 */
export function BlockKeeping({ block }: { block?: PatternBlockKeeping | null }) {
  const hasRatio = block && block.total > 0 && block.onTime != null
  const onMoveClick = useDeadClick('moved_routine')
  return (
    <Section
      title="블록을 어떻게 지키나요?"
      aside={block?.total ? `${block.total.toLocaleString()}개 기준` : undefined}
    >
      {!hasRatio ? (
        <NotEnough>시간을 정한 블록이 쌓이면 보여드려요</NotEnough>
      ) : (
        <>
          <div className="flex h-3.5 overflow-hidden rounded-full" aria-hidden>
            <span className="bg-success" style={{ width: `${block!.onTime}%` }} />
            <span className="bg-warning" style={{ width: `${block!.late}%` }} />
            <span className="bg-danger" style={{ width: `${block!.missed}%` }} />
          </div>
          <KeepFilter block={block!} />
        </>
      )}

      <h3 className="mt-6 text-[15px] font-bold">자주 옮기는 루틴</h3>
      <p className="text-ink-3 mt-0.5 text-[12px]">
        반복되는 루틴만 봐요 — 늘 같은 방향으로 옮긴다면 루틴 시간을 바꾸는 게 나아요.
      </p>
      {block?.frequentMoves.length ? (
        <ul className="divide-line mt-2 divide-y">
          {block.frequentMoves.map((m) => (
            <li
              key={`${m.routineId ?? m.title}`}
              className="flex items-center justify-between gap-3 py-3"
              onClick={onMoveClick}
            >
              <div className="min-w-0">
                <p className="truncate text-[15px] font-semibold">
                  {m.emoji && <span className="mr-1">{m.emoji}</span>}
                  {m.title}
                </p>
                <p className="text-ink-3 mt-0.5 text-[13px]">{m.description}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2 text-[14px]">
                <span className="text-ink-4 line-through">{m.fromLabel}</span>
                <span aria-label="에서">→</span>
                <span className="font-bold">{m.toLabel}</span>
                <span className="bg-subtle text-ink-2 rounded-md px-2 py-0.5 text-[12px] font-semibold">
                  {m.count}회
                </span>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-ink-3 mt-2 text-[13px]">캘린더에서 루틴 블록을 옮긴 기록이 쌓이면 보여드려요</p>
      )}
    </Section>
  )
}

/**
 * 제시간 / 늦게 / 놓침 칩 — 누르면 그렇게 된 루틴 · Task 를 많은 순으로 보여줘요.
 * 다시 누르면 접혀요.
 */
function KeepFilter({ block }: { block: PatternBlockKeeping }) {
  const [kind, setKind] = useState<KeepKind | null>(null)
  const items = block.items ?? []
  const { period } = usePatternAnalytics()
  const toggle = (k: KeepKind) => {
    const expanded = kind !== k
    track('pattern.keep_filter_click', {
      keep_kind: KEEP_KIND_PROP[k],
      expanded,
      item_count: items.filter((i) => i[k] > 0).length,
      period,
    })
    setKind(expanded ? k : null)
  }
  const picked = kind ? KINDS.find((k) => k.kind === kind)! : null
  const list: BlockKeepItem[] = kind
    ? items
        .filter((i) => i[kind] > 0)
        .sort((a, b) => b[kind] - a[kind] || b.onTime + b.late + b.missed - (a.onTime + a.late + a.missed))
    : []
  return (
    <>
      <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="블록 지킴 필터">
        {KINDS.map((k) => {
          const on = kind === k.kind
          return (
            <button
              key={k.kind}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(k.kind)}
              className={cn(
                'flex h-8 items-center gap-1.5 rounded-full border px-3 text-[13px] transition-colors',
                on ? 'border-text-brand bg-ai-tint-soft text-text-brand' : 'border-line text-ink-2 hover:border-line-strong'
              )}
            >
              <span className={`size-2.5 rounded-sm ${k.color}`} />
              {k.label} <b className={on ? '' : 'text-ink'}>{block[k.kind] ?? 0}%</b>
            </button>
          )
        })}
      </div>
      {!kind && items.length > 0 && (
        <p className="text-ink-4 mt-2 text-[12px]">칩을 누르면 어떤 루틴 · Task였는지 볼 수 있어요</p>
      )}
      {picked &&
        (list.length ? (
          <ul className="divide-line border-line mt-3 divide-y rounded-2xl border">
            {list.map((i) => {
              const total = i.onTime + i.late + i.missed
              return (
                <li key={i.key} className="flex items-center justify-between gap-3 px-3.5 py-2.5">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 truncate text-[14px] font-semibold">
                      {i.routine && <Repeat className="text-text-brand size-3.5 shrink-0" aria-label="루틴" />}
                      {i.emoji && <span>{i.emoji}</span>}
                      <span className="truncate">{i.title}</span>
                    </p>
                    {i.goalName && (
                      <p className="text-ink-3 mt-0.5 flex items-center gap-1 text-[12px]">
                        <span
                          className="size-2 rounded-full"
                          style={{ backgroundColor: goalAccent(i.goalColorCode) }}
                          aria-hidden
                        />
                        {i.goalName}
                      </p>
                    )}
                  </div>
                  <span className="shrink-0 text-[13px]">
                    <b className={picked.text}>{i[picked.kind]}번</b>
                    <span className="text-ink-4"> / {total}번</span>
                  </span>
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="text-ink-3 mt-3 text-[13px]">
            {picked.kind === 'missed' ? '놓친 블록이 없어요 👏' : `${picked.label}한 블록이 없어요`}
          </p>
        ))}
    </>
  )
}
