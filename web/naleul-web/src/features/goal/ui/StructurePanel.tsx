'use client'

import { useContext, useState } from 'react'
import { ChevronDown } from '@/components/icons'
import { cn } from '@/lib/cn'
import { todayKst } from '@/features/timetable/time'
import type { GoalCategory } from '../api'
import { EditingContext } from '../edit/inline'
import { MilestonesSection } from './MilestonesSection'
import { SubGoalsSection, areasOf } from './SubGoalsSection'

/**
 * 세부 구조 (세부 목표 · 마일스톤) — 기본은 접어 둬요.
 *
 * 왜: 예전 상세 화면은 세부 목표·루틴·Task·마일스톤을 같은 무게로 펼쳐 놓아서, 하나를 고치려면 트리 구조와
 * 마일스톤 수치 계산 방식을 먼저 이해해야 했어요. 이제 매일 보는 건 "할 것(루틴·Task)"이고,
 * 세부 목표는 라벨, 마일스톤은 자동으로 맞춰지니 꼭 바꿀 때만 열면 돼요.
 * 접혀 있어도 다음 마일스톤은 크게 보여줘요.
 */
export function StructurePanel({ goal }: { goal: GoalCategory }) {
  const [open, setOpen] = useState(false)
  const { editing } = useContext(EditingContext)
  // 이 안의 항목을 편집 중이면 접히지 않게
  const editingInside = !!editing && /^(del:)?(milestone|sub):/.test(editing)
  const expanded = open || editingInside

  const today = todayKst()
  const milestones = [...(goal.milestones ?? [])].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  const next = milestones.find((m) => m.status !== 'ACHIEVED' && m.dueDate >= today)
  const achieved = milestones.filter((m) => m.status === 'ACHIEVED').length
  const areas = areasOf(goal)
  const unit = goal.metricUnit ?? ''
  const dLeft = next ? Math.max(Math.round((Date.parse(next.dueDate) - Date.parse(today)) / 86_400_000), 0) : 0

  return (
    <section className="border-line bg-surface overflow-hidden rounded-card border">
      {/* 다음 마일스톤 — 접혀 있어도 한눈에 보이게 크게 */}
      {next && (
        <div className="bg-brand-soft px-5 pt-5 pb-4 sm:px-6">
          <div className="flex items-center justify-between gap-2">
            <span className="text-text-brand text-[13px] font-semibold">다음 마일스톤</span>
            <span className="bg-brand rounded-full px-2.5 py-0.5 text-[13px] font-bold text-white tabular-nums">
              {dLeft === 0 ? 'D-DAY' : `D-${dLeft}`}
            </span>
          </div>
          <p className="mt-1.5 text-[18px] leading-snug font-bold">{next.title}</p>
          <p className="text-ink-2 mt-1 flex flex-wrap items-center gap-x-2 text-[14px]">
            <span>
              {Number(next.dueDate.slice(5, 7))}월 {Number(next.dueDate.slice(8, 10))}일까지
            </span>
            {next.targetValue != null && (
              <span className="text-text-brand font-bold">
                목표 {next.targetValue}
                {unit}
              </span>
            )}
          </p>
        </div>
      )}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={expanded}
        className="flex w-full items-center gap-3 px-5 py-4 text-left sm:px-6"
      >
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] font-bold">세부 구조</span>
          <span className="text-ink-3 mt-0.5 block text-[13px]">
            세부 목표 {areas.length}개 · 마일스톤 {milestones.length ? `${achieved}/${milestones.length} 달성` : '없음'}
          </span>
        </span>
        <span className="text-ink-3 shrink-0 text-[13px]">{expanded ? '접기' : '펼쳐서 수정'}</span>
        <ChevronDown className={cn('text-ink-3 size-5 shrink-0 transition-transform', expanded && 'rotate-180')} />
      </button>
      {expanded && (
        <div className="border-line space-y-4 border-t p-3 sm:p-4">
          <MilestonesSection goal={goal} />
          <SubGoalsSection goal={goal} />
        </div>
      )}
    </section>
  )
}
