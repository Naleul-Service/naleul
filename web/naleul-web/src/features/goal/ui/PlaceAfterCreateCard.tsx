'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CalendarCheck } from 'lucide-react'
import { Sparkles } from '@/components/icons'
import { Button, buttonClass } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useFillTimetable, type FillResponse } from '@/features/timetable/api'
import { addDays, todayKst } from '@/features/timetable/time'
import type { GoalCategory } from '../api'
import { AIBadge } from '@/components/ui/AIBadge'

/** 목표를 만든 직후 채울 기간: 오늘부터 7일 */
const DAYS = 7

/**
 * AI 목표 확정 직후 "이어서 Task 를 TimeTable 에 배치할까요?".
 * 백엔드는 확정 직후 오늘만 자동으로 채우고 나머지 날은 밤 10시 작업이 하루씩 채워요.
 * 여기서 동의하면 오늘~6일 뒤까지 한 번에 채워서, 목표를 만들자마자 한 주 계획을 볼 수 있어요.
 * (이미 시간이 정해진 블록은 건드리지 않아요 — FILL)
 */
export function PlaceAfterCreateCard({ goal, className }: { goal: GoalCategory; className?: string }) {
  const router = useRouter()
  const fill = useFillTimetable()
  const [result, setResult] = useState<FillResponse | null>(null)
  const today = todayKst()
  const gid = goal.goalCategoryId
  const dismiss = (cardState: 'before_fill' | 'after_fill') => {
    track('goal_detail.place_dismiss_click', { goal_category_id: gid, card_state: cardState })
    router.replace(`/goal/${gid}`, { scroll: false })
  }

  // 카드가 보일 때 한 번 (새로고침해도 ?created=1 이 남아 있으면 다시)
  const viewSent = useRef(false)
  useEffect(() => {
    if (viewSent.current) return
    viewSent.current = true
    track('goal_detail.place_card_view', { goal_category_id: gid })
  }, [gid])

  const doFill = () => {
    track('goal_detail.place_fill_click', { goal_category_id: gid })
    fill
      .mutateAsync({ startDate: today, endDate: addDays(today, DAYS - 1) })
      .then((r) => {
        track('goal_detail.place_fill_result', {
          goal_category_id: gid,
          result: 'success',
          placed_count: r?.placedCount,
          unscheduled_count: r?.unscheduledCount,
        })
        setResult(r)
      })
      .catch((e: unknown) =>
        track('goal_detail.place_fill_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
        })
      )
  }

  return (
    <section
      className={cn(
        'flex flex-col gap-4 rounded-card p-5 sm:flex-row sm:items-center sm:p-6',
        // 배치 전 = AI 제안 상자 (명세 6B) · 배치 후 = 성공 카드
        result ? 'border-border-subtle bg-success-soft/50 border' : 'ai-border',
        className
      )}
    >
      {result ? (
        <span className="bg-success text-on-ink grid size-11 shrink-0 place-items-center rounded-[12px]">
          <CalendarCheck className="size-5" />
        </span>
      ) : (
        <AIBadge size="lg" />
      )}
      {result ? (
        <>
          <div className="min-w-0 flex-1">
            <p className="text-[17px] font-bold">이번 주 TimeTable에 배치했어요</p>
            <p className="text-ink-3 mt-1 text-sm">
              {result.placedCount}개 Task를 빈 시간에 넣었어요
              {result.unscheduledCount
                ? ` · ${result.unscheduledCount}개는 빈 시간이 없어 시간 미정으로 남았어요 (밤 10시에 다시 시도해요)`
                : ''}
              .
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <Button variant="ghost" onClick={() => dismiss('after_fill')}>
              닫기
            </Button>
            <Link
              href={`/calendar?view=week&date=${today}`}
              onClick={() =>
                track('goal_detail.place_calendar_click', { goal_category_id: gid, placed_count: result.placedCount })
              }
              className={buttonClass('primary')}
            >
              캘린더에서 보기
            </Link>
          </div>
        </>
      ) : (
        <>
          <div className="min-w-0 flex-1">
            <p className="text-[17px] leading-snug font-bold">
              목표가 만들어졌어요 🎉 이어서 Task를 TimeTable에 배치할까요?
            </p>
            <p className="text-ink-3 mt-1 text-sm leading-relaxed">
              오늘부터 {DAYS}일 동안 시간이 정해지지 않은 Task와 루틴을 빈 시간에 넣어요. 직접 정한 일정과 고정 시간은
              그대로예요. 나중에 해도 밤 10시마다 다음 날이 자동으로 채워져요.
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <Button variant="ghost" onClick={() => dismiss('before_fill')} disabled={fill.isPending}>
              나중에
            </Button>
            <Button variant="ai" loading={fill.isPending} onClick={doFill}>
              <Sparkles className="size-4" />
              AI로 이번 주 배치하기
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
