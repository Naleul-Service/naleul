import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/Chip'
import { PLANNING_STYLE_LABEL } from '../constants'
import type { GoalSummary } from '../types'

const EMPTY = <span className="text-ink-4">AI가 알맞게 정해줄게요</span>

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-4 py-2.5">
      <dt className="text-ink-3 w-16 shrink-0 text-[13px] leading-6">{label}</dt>
      <dd className="min-w-0 flex-1 text-[15px] leading-6 font-medium">{children}</dd>
    </div>
  )
}

/** 인터뷰 결과 요약 (G-2 정리 확인 화면에서도 재사용 예정) */
export function InterviewSummaryCard({ summary }: { summary: GoalSummary }) {
  return (
    <dl className="divide-line divide-y">
      <Row label="목표">{summary.goalStatement ?? EMPTY}</Row>
      <Row label="수치">{summary.metricText ?? EMPTY}</Row>
      <Row label="기한">{summary.deadlineText ?? EMPTY}</Row>
      <Row label="계획">{summary.approach ?? EMPTY}</Row>
      {/* 예전 세션(이유를 묻던 때)에 답한 이유가 있으면 보여줘요 */}
      {summary.motivation && <Row label="이유">{summary.motivation}</Row>}
      <Row label="실천 시간">{summary.preferenceText ?? EMPTY}</Row>
      <Row label="성향">
        {summary.planningStyle ? <Badge tone="brand">{PLANNING_STYLE_LABEL[summary.planningStyle]}</Badge> : EMPTY}
      </Row>
    </dl>
  )
}
