'use client'

import { useEffect, useRef } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { PageHeader } from '@/components/layout/PageHeader'
import { buttonClass } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { RefreshCw } from 'lucide-react'
import { track } from '@/lib/client/analytics'
import { referrerType } from '@/lib/client/commonEvents'
import { errorStatusOf } from '@/features/home/analytics'
import { usePatternReport, useRecalculatePattern } from '../api'
import type { PatternPeriod } from '../types'
import { rangeLabel, updatedLabel } from '../format'
import {
  emptySectionsOf,
  minutesSince,
  PatternAnalyticsProvider,
  suggestionTypesOf,
  usePatternAnalytics,
} from '../analytics'
import { BlockKeeping } from './BlockKeeping'
import { Heatmap } from './Heatmap'
import { RoutineLists } from './RoutineLists'
import { ShareButton } from './ShareButton'
import { StyleCard } from './StyleCard'
import { SuggestionBar } from './SuggestionBar'
import { SummaryTiles } from './SummaryTiles'
import { TaskHabits } from './TaskHabits'
import { TimeUsage } from './TimeUsage'
import { WeekdayBars } from './WeekdayBars'
import { WeeklyTrend } from './WeeklyTrend'

const PERIODS: { value: PatternPeriod; label: string }[] = [
  { value: 'RECENT_4W', label: '최근 4주' },
  { value: 'ALL', label: '전체' },
]

/** /patterns?period=RECENT_4W|ALL */
export function PatternView() {
  const params = useSearchParams()
  const period: PatternPeriod = params.get('period') === 'ALL' ? 'ALL' : 'RECENT_4W'
  return (
    <PatternAnalyticsProvider period={period}>
      <PatternScreen period={period} />
    </PatternAnalyticsProvider>
  )
}

function PatternScreen({ period }: { period: PatternPeriod }) {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const { data, isPending, isError, error, refetch, isFetching, isPlaceholderData } = usePatternReport(period)
  const recalc = useRecalculatePattern(period)
  const reportRef = useRef<HTMLDivElement>(null)
  const { once } = usePatternAnalytics()

  // ── 분석 ──
  // pattern.pageview — /patterns 를 불러온 직후 한 번 (기간을 바꿔도 다시 안 보냄)
  const pageviewSent = useRef(false)
  useEffect(() => {
    if (pageviewSent.current) return
    pageviewSent.current = true
    track('pattern.pageview', { period, referrer_type: referrerType() })
  }, [period])

  // 리포트를 받아 그린 직후 — 처음 · 기간을 바꿀 때마다 한 번 (기간을 바꾸는 동안 보이는 이전 리포트는 빼요)
  const current = data && !isPlaceholderData ? data : null
  const reportedPeriod = useRef<PatternPeriod | null>(null)
  useEffect(() => {
    if (!current || reportedPeriod.current === period) return
    reportedPeriod.current = period
    const styleReady = !!current.style?.title
    const hasSuggestion = current.evaluatedCount > 0 && !!current.suggestion?.items.length
    track('pattern.report_view', {
      period,
      range_days: current.range.days,
      evaluated_count: current.evaluatedCount,
      style_ready: styleReady,
      ai_status: current.aiStatus,
      empty_sections: emptySectionsOf(current),
      has_suggestion: hasSuggestion,
      minutes_since_update: minutesSince(current.generatedAt),
    })
    if (!styleReady) {
      const p = current.style?.progress
      track('pattern.locked_view', {
        period,
        evaluated_count: current.evaluatedCount,
        remaining_tasks: p?.remainingTasks,
        remaining_days: p?.remainingDays,
      })
    }
  }, [current, period])

  // 제안 바가 보일 때 — 리포트를 받을 때마다 (기간이 바뀌거나 제안 항목이 바뀌면 다시)
  const suggestion =
    current && current.evaluatedCount > 0 && current.suggestion?.items.length ? current.suggestion : null
  const suggestionKey = suggestion ? `${period}|${suggestion.items.map((i) => i.key).join(',')}` : null
  const lastSuggestionKey = useRef<string | null>(null)
  useEffect(() => {
    if (!suggestion || !suggestionKey || lastSuggestionKey.current === suggestionKey) return
    lastSuggestionKey.current = suggestionKey
    track('pattern.suggestion_view', {
      period,
      item_count: suggestion.items.length,
      suggestion_types: suggestionTypesOf(suggestion),
      expected_gain: suggestion.expectedGain ?? undefined,
    })
  }, [suggestion, suggestionKey, period])

  // '패턴을 불러오지 못했어요' 카드가 보일 때 (실패할 때마다 한 번)
  const failShown = !isPending && (isError || !data)
  const lastFail = useRef<unknown>(null)
  useEffect(() => {
    if (!failShown || lastFail.current === error) return
    lastFail.current = error
    track('pattern.load_fail', { period, error_status: errorStatusOf(error) })
  }, [failShown, error, period])

  // 스크롤 깊이 25 / 50 / 75 / 100% — 기간마다 한 번
  const showReport = !!data && data.evaluatedCount > 0 && !isError
  const hasSuggestionShown = showReport && !!data?.suggestion?.items.length
  useEffect(() => {
    if (!showReport) return
    const onScroll = () => {
      const max = document.documentElement.scrollHeight - window.innerHeight
      if (max <= 0) return
      const pct = (window.scrollY / max) * 100
      for (const mark of [25, 50, 75, 100]) {
        if (pct >= mark - 1)
          once(`scroll:${period}:${mark}`, () =>
            track('pattern.scroll_depth', { scroll_percent: mark, period, has_suggestion: hasSuggestionShown })
          )
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [showReport, hasSuggestionShown, period, once])

  const onRecalc = () => {
    const prevVersion = data?.dataVersion
    track('pattern.recalc_click', {
      period,
      minutes_since_update: data ? minutesSince(data.generatedAt) : undefined,
    })
    recalc.mutate(undefined, {
      onSuccess: (next) =>
        track('pattern.recalc_result', {
          period,
          result: 'success',
          data_changed: prevVersion != null && next.dataVersion != null ? prevVersion !== next.dataVersion : undefined,
        }),
      onError: (e) => track('pattern.recalc_result', { period, result: 'fail', error_status: errorStatusOf(e) }),
    })
  }

  const setPeriod = (p: PatternPeriod) => {
    if (p !== period) track('pattern.period_change', { period: p, from_period: period })
    const next = new URLSearchParams(params)
    if (p === 'RECENT_4W') next.delete('period')
    else next.set('period', p)
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }

  const header = (
    <PageHeader
      title="나의 패턴"
      description={
        data ? (
          <>
            {rangeLabel(data)}
            {/* 언제 계산한 결과인지 — 방금 한 일이 반영됐는지 알 수 있게 (오늘 완료한 일 · 시간이 지난 블록도 포함) */}
            <span className="text-ink-3 mt-0.5 flex flex-wrap items-center gap-x-2 text-[12px]" data-share-exclude>
              최근 업데이트 {updatedLabel(data.generatedAt)} · 오늘 완료한 일까지 반영
              <button
                type="button"
                onClick={onRecalc}
                disabled={recalc.isPending}
                className="text-text-brand inline-flex items-center gap-1 font-semibold disabled:opacity-50"
              >
                <RefreshCw className={cn('size-3', recalc.isPending && 'animate-spin')} />
                지금 다시 계산
              </button>
            </span>
          </>
        ) : (
          ' '
        )
      }
      actions={
        <>
          <div
            className={segmentedClass('flex')}
            role="tablist"
            aria-label="기간"
            data-share-exclude
          >
            {PERIODS.map((p) => (
              <button
                key={p.value}
                role="tab"
                aria-selected={period === p.value}
                onClick={() => setPeriod(p.value)}
                className={segmentClass(period === p.value)}
              >
                {p.label}
              </button>
            ))}
          </div>
          {data && data.evaluatedCount > 0 && (
            <ShareButton
              targetRef={reportRef}
              fileDate={data.range.end}
              evaluatedCount={data.evaluatedCount}
              styleReady={!!data.style?.title}
            />
          )}
        </>
      }
    />
  )

  if (isPending) {
    return (
      <>
        {header}
        <div className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      </>
    )
  }

  if (isError || !data) {
    return (
      <>
        {header}
        <Card className="mt-6 grid min-h-[280px] place-items-center p-10 text-center">
          <div>
            <p className="text-[17px] font-bold">패턴을 불러오지 못했어요</p>
            <p className="text-ink-3 mt-1.5 text-sm">{error?.message}</p>
            <button
              className={cn(buttonClass('secondary'), 'mt-4')}
              onClick={() => {
                track('pattern.retry_click', { period, error_status: errorStatusOf(error) })
                refetch()
              }}
            >
              다시 시도
            </button>
          </div>
        </Card>
      </>
    )
  }

  if (data.evaluatedCount === 0) {
    // 기록이 하나도 없어도 빈 화면 대신 "얼마나 하면 열리는지"를 보여줘요
    return (
      <>
        {header}
        <div className="mt-6">
          <StyleCard style={data.style} />
        </div>
        <Card className="mt-4 flex flex-wrap items-center gap-3 p-5">
          <p className="text-ink-2 min-w-0 flex-1 text-sm">
            Task를 완료하면 바로 패턴이 쌓이기 시작해요. 시간대별 실행률·요일별 패턴은 기록이 조금 더 모이면 열려요.
          </p>
          <Link
            href="/calendar"
            className={buttonClass('primary', 'sm')}
            onClick={() => track('pattern.empty_cta_click', { period })}
          >
            캘린더로 가기
          </Link>
        </Card>
      </>
    )
  }

  return (
    <div className={cn('transition-opacity', isFetching && 'opacity-70')}>
      {/* 리포트 공유 이미지에 들어가는 영역 (제안 바·안내 문구는 밖에 둠) */}
      <div ref={reportRef}>
        {header}

        <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-12">
          <div className="xl:col-span-7">
            <StyleCard style={data.style} />
          </div>
          <div className="xl:col-span-5">
            <SummaryTiles summary={data.summary} />
          </div>

          <div className="xl:col-span-8">
            <Heatmap heatmap={data.heatmap} />
          </div>
          <div className="xl:col-span-4">
            <WeekdayBars weekday={data.weekday} />
          </div>

          <div className="max-lg:flex max-lg:flex-col max-lg:gap-4 lg:grid lg:grid-cols-2 lg:gap-4 xl:col-span-12">
            <WeeklyTrend weekly={data.weekly} />
            <TimeUsage usage={data.timeUsage} />
          </div>

          <div className="xl:col-span-4">
            <BlockKeeping block={data.blockKeeping} />
          </div>
          <div className="xl:col-span-4">
            <RoutineLists routines={data.routines} />
          </div>
          <div className="xl:col-span-4">
            <TaskHabits habits={data.taskHabits} />
          </div>
        </div>
      </div>

      <div className="mt-4">
        <SuggestionBar suggestion={data.suggestion} />
      </div>

      {/* 숫자는 기록이 바뀔 때마다 바로, AI 문장 다듬기는 하루 한 번 (AI 비용 때문에) */}
      <p className="text-ink-4 mt-4 text-right text-[12px]">
        {data.aiStatus === 'AI'
          ? '문장은 AI가 다듬었고, 숫자는 실제 기록으로 계산했어요.'
          : '숫자는 실제 기록으로 바로 계산했어요. AI 문장 다듬기는 하루 한 번 해요.'}
      </p>
    </div>
  )
}
