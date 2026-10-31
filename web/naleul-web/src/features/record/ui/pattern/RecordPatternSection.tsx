'use client'

import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useRecordPattern } from '../../api'
import { RecordRhythmCard } from './RecordRhythmCard'
import { RecordRoutineCard } from './RecordRoutineCard'
import { RecordSummaryTiles } from './RecordSummaryTiles'
import { RecordWeeklyCard } from './RecordWeeklyCard'
import { RecordWhenCard } from './RecordWhenCard'

/**
 * 기록형 목표 상세 — "이 목표에 대한 나의 패턴" (이 목표의 완료한 Task 기준).
 *
 * 기록형은 기한도 목표 수치도 없어서 달성률(%)을 낼 수 없어요.
 * 대신 완료한 Task 가 "언제·얼마나" 쌓였는지와 루틴별로 한 것을 숫자로 보여주고,
 * 성장은 "그 전 4주보다 늘어난 것"과 "줄지 않는 누적"으로 보여줘요.
 * (유형 이름·분석 문장 같은 AI 말투는 쓰지 않아요)
 */
type StatsCard = 'summary' | 'weekly' | 'rhythm' | 'when' | 'routine'

/** 카드가 화면에 절반 이상 처음 보일 때 record_stats.card_view (카드마다 1회) */
function StatsCardView({
  goalId,
  card,
  className,
  children,
}: {
  goalId: number
  card: StatsCard
  className?: string
  children: ReactNode
}) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el || typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver(
      (entries) => {
        if (!entries.some((e) => e.isIntersecting)) return
        io.disconnect()
        track('record_stats.card_view', { goal_category_id: goalId, stats_card: card })
      },
      { threshold: 0.5 }
    )
    io.observe(el)
    return () => io.disconnect()
  }, [goalId, card])
  return (
    <div ref={ref} className={className}>
      {children}
    </div>
  )
}

export function RecordPatternSection({ goalId }: { goalId: number }) {
  const { data, isPending, isError, error, refetch } = useRecordPattern(goalId)

  // '이 목표의 패턴을 불러오지 못했어요'가 보일 때 (실패할 때마다 1회)
  const showFail = !isPending && (isError || !data)
  const failSent = useRef(false)
  useEffect(() => {
    if (!showFail) {
      failSent.current = false
      return
    }
    if (failSent.current) return
    failSent.current = true
    track('record_stats.load_fail', { goal_category_id: goalId, error_status: errorStatusOf(error) })
  }, [showFail, error, goalId])

  // 패턴을 다 불러와 그려진 직후 한 번
  const viewSent = useRef(false)
  useEffect(() => {
    if (!data || isError || viewSent.current) return
    viewSent.current = true
    track('record_stats.view', {
      goal_category_id: goalId,
      stats_state: data.summary.totalCount === 0 ? 'empty' : 'ok',
      completed_task_total: data.summary.totalCount,
      routine_count: data.routines.items.length,
    })
  }, [data, isError, goalId])

  // 잔디 · 요일×시간대 칸에 마우스를 올렸을 때 (카드마다 화면당 1회)
  const hovered = useRef(new Set<StatsCard>())
  const cellHover = (card: StatsCard) => {
    if (hovered.current.has(card)) return
    hovered.current.add(card)
    track('record_stats.cell_hover', { goal_category_id: goalId, stats_card: card })
  }

  if (isPending) {
    return (
      <div className="grid h-48 place-items-center">
        <Spinner className="text-ink-3 size-6" />
      </div>
    )
  }

  if (isError || !data) {
    return (
      <Card className="flex flex-wrap items-center gap-3 p-5">
        <p className="text-ink-2 min-w-0 flex-1 text-sm">이 목표의 패턴을 불러오지 못했어요.</p>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            track('record_stats.retry_click', { goal_category_id: goalId })
            refetch()
          }}
        >
          다시 시도
        </Button>
      </Card>
    )
  }

  const hasRoutines = data.routines.items.length > 0

  // 완료한 Task 가 아직 없으면 빈 그래프 대신 안내 한 줄 (루틴이 있으면 루틴 카드는 보여줘요)
  if (data.summary.totalCount === 0) {
    return (
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-12">
        <Card className={hasRoutines ? 'p-6 xl:col-span-6' : 'p-6 xl:col-span-12'}>
          <p className="text-[16px] font-bold">아직 완료한 Task가 없어요</p>
          <p className="text-ink-3 mt-1.5 text-sm leading-relaxed">
            이 목표의 Task를 완료하면 주차별로 완료한 Task, 완료의 리듬, 주로 하는 때가 여기에 보여요.
          </p>
        </Card>
        {hasRoutines && (
          <StatsCardView goalId={goalId} card="routine" className="xl:col-span-6">
            <RecordRoutineCard routines={data.routines} />
          </StatsCardView>
        )}
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-12">
      <StatsCardView goalId={goalId} card="summary" className="xl:col-span-12">
        <RecordSummaryTiles summary={data.summary} />
      </StatsCardView>

      <StatsCardView goalId={goalId} card="weekly" className="xl:col-span-7">
        <RecordWeeklyCard weeks={data.rhythm.weeks} />
      </StatsCardView>
      <StatsCardView goalId={goalId} card="rhythm" className="xl:col-span-5">
        <RecordRhythmCard rhythm={data.rhythm} onCellHover={() => cellHover('rhythm')} />
      </StatsCardView>

      <StatsCardView goalId={goalId} card="when" className={hasRoutines ? 'xl:col-span-6' : 'xl:col-span-12'}>
        <RecordWhenCard when={data.when} wide={!hasRoutines} onCellHover={() => cellHover('when')} />
      </StatsCardView>
      {hasRoutines && (
        <StatsCardView goalId={goalId} card="routine" className="xl:col-span-6">
          <RecordRoutineCard routines={data.routines} />
        </StatsCardView>
      )}
    </div>
  )
}
