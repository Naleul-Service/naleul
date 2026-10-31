'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Library } from 'lucide-react'
import { ChevronLeft, ChevronRight } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { iconButtonClass } from '@/components/ui/IconButton'
import { track } from '@/lib/client/analytics'
import { referrerType } from '@/lib/client/commonEvents'
import { errorStatusOf } from '@/features/home/analytics'
import { useGoalCategories, type GoalCategory } from '@/features/goal/api'
import { MOODS, moodOf } from '@/features/goal/ui/progress/DailyCheckInCard'
import {
  useReflectionFeed,
  useReflectionRange,
  type ReflectionEntry,
  type ReflectionPeriod,
} from '@/features/home/api'
import { goalAccent } from '@/lib/goalTone'
import { ReflectionDetailModal } from './ReflectionDetailModal'
import {
  addDays,
  addMonths,
  dayOfMonth,
  formatMonthDay,
  monthOf,
  startOfWeek,
  todayKst,
  yearOf,
} from '@/features/timetable/time'

const fmt = (v: number) => String(Number(v.toFixed(2)))

/** 회고를 남길 수 있는 목표 (기본 생활 습관 · 임시 목표 · 지운 목표 빼고) */
const filterable = (g: GoalCategory) => g.goalMode !== 'HABIT' && !g.temporary && g.goalCategoryStatus !== 'DELETED'

/** 날짜별로 묶기 (이미 최근 날짜 순으로 와요) */
function byDate(entries: ReflectionEntry[]) {
  const out: { date: string; items: ReflectionEntry[] }[] = []
  for (const e of entries) {
    const last = out[out.length - 1]
    if (last && last.date === e.date) last.items.push(e)
    else out.push({ date: e.date, items: [e] })
  }
  return out
}

/** 오늘부터 며칠 전 날짜인지 (오늘 0) */
const daysAgo = (date: string, today: string) => Math.round((Date.parse(today) - Date.parse(date)) / 86_400_000)

function relLabel(date: string, today: string) {
  const diff = daysAgo(date, today)
  if (diff === 0) return '오늘'
  if (diff === 1) return '어제'
  return `${diff}일 전`
}

/**
 * 회고 모아보기 — 목표 상세 · 홈 · 업무형 보드에서 남긴 회고를 날짜별로 한곳에서 봐요.
 *  - 목표형: 그날 수치 + 기분 + 한 줄 회고 (오늘 기록)
 *  - 업무형: 회고 글
 * 위의 목표 칩으로 한 목표만 볼 수 있어요 (?goal=id).
 */
export function ReflectionsView() {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const goalParam = Number(params.get('goal'))
  const goalId = Number.isFinite(goalParam) && goalParam > 0 ? goalParam : undefined

  const goals = useGoalCategories()
  const options = (goals.data ?? []).filter(filterable)
  const optionCount = options.length
  const select = (id?: number) => {
    const index = id ? options.findIndex((g) => g.goalCategoryId === id) : -1
    track('reflections.goal_filter_select', {
      goal_category_id: id,
      goal_mode: index >= 0 ? (options[index].goalMode ?? 'ACHIEVEMENT') : undefined,
      list_position: index + 1, // 칩 줄 순서 ('전체' 0, 목표 칩 1부터)
      filter_option_count: optionCount,
    })
    const q = new URLSearchParams(params)
    if (id) q.set('goal', String(id))
    else q.delete('goal')
    router.replace(`${pathname}${q.size ? `?${q}` : ''}`, { scroll: false })
  }

  // 들어온 길 — 링크에 붙인 ?from= (없으면 주소 직접 · 새로고침). 주소에서 지우지 않아요 (로그인 ?from= 과 같은 관례)
  const fromParam = params.get('from')
  const entrySource =
    fromParam === 'sidebar' || fromParam === 'home_retro' || fromParam === 'goal_detail' ? fromParam : 'url'
  const goalsPending = goals.isPending
  const goalsOk = goals.isSuccess
  const pageviewSent = useRef(false)
  useEffect(() => {
    if (pageviewSent.current || goalsPending) return
    pageviewSent.current = true
    track('reflections.pageview', {
      entry_source: entrySource,
      referrer_type: referrerType(),
      goal_category_id: goalId,
      filter_option_count: goalsOk ? optionCount : undefined,
    })
  }, [goalsPending, goalsOk, entrySource, goalId, optionCount])

  // 목표 칩을 못 불러와 '전체'만 남음 (실패할 때마다 한 번)
  const goalsFailed = goals.isError && !goals.data
  const goalsErrorAt = goals.errorUpdatedAt
  const goalsError = goals.error
  const goalsFailSent = useRef(0)
  useEffect(() => {
    if (!goalsFailed || goalsFailSent.current === goalsErrorAt) return
    goalsFailSent.current = goalsErrorAt
    track('reflections.goals_load_fail', { error_status: errorStatusOf(goalsError) })
  }, [goalsFailed, goalsErrorAt, goalsError])

  // ?goal= 이 칩에 없음 (생활 습관 · 임시 · 지운 목표 · 내 목표 아님) — 목표마다 한 번
  const unmatched = goalsOk && goalId !== undefined && !options.some((g) => g.goalCategoryId === goalId)
  const unmatchedSent = useRef<number | undefined>(undefined)
  useEffect(() => {
    if (!unmatched || unmatchedSent.current === goalId) return
    unmatchedSent.current = goalId
    track('reflections.goal_param_unmatched', { goal_category_id: goalId })
  }, [unmatched, goalId])

  return (
    <div className="mx-auto flex max-w-[860px] flex-col gap-5">
      <PageHeader
        title="회고 모아보기"
        description="목표마다 남긴 기록과 회고를 날짜별로 한 번에 봐요. 홈 · 목표 상세 어디서 쓴 것이든 여기 모여요."
      />

      {/* 목표 고르기 */}
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="목표 고르기">
        <FilterChip active={!goalId} onClick={() => select()}>
          전체
        </FilterChip>
        {options.map((g) => (
          <FilterChip
            key={g.goalCategoryId}
            active={goalId === g.goalCategoryId}
            onClick={() => select(g.goalCategoryId)}
          >
            <span
              className="size-2 shrink-0 rounded-full"
              style={{ backgroundColor: goalAccent(g.colorCode) }}
              aria-hidden
            />
            <span className="truncate">
              {g.emoji ? `${g.emoji} ` : ''}
              {g.goalCategoryName}
            </span>
          </FilterChip>
        ))}
      </div>

      <ReflectionTimeline goalId={goalId} showGoal={!goalId} surface="reflections" />
    </div>
  )
}

/** 전체(기간 없이 최근부터) + 일간 · 주간 · 월간 */
type TimelinePeriod = 'ALL' | ReflectionPeriod

/** Amplitude period 값 — ALL · DAY · WEEK · MONTH (iOS · Android 와 같은 대문자) */
const periodProp = (p: TimelinePeriod) => p

const PERIODS: { value: TimelinePeriod; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'DAY', label: '일간' },
  { value: 'WEEK', label: '주간' },
  { value: 'MONTH', label: '월간' },
]

const pad2 = (n: number) => String(n).padStart(2, '0')

/** 기준 날짜가 든 기간 [from, to] */
export function periodRange(period: ReflectionPeriod, anchor: string) {
  if (period === 'DAY') return { from: anchor, to: anchor }
  if (period === 'WEEK') {
    const from = startOfWeek(anchor)
    return { from, to: addDays(from, 6) }
  }
  const y = yearOf(anchor)
  const m = monthOf(anchor)
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate()
  return { from: `${y}-${pad2(m)}-01`, to: `${y}-${pad2(m)}-${pad2(last)}` }
}

/** 지금 기간에서 몇 칸 떨어진 기간인지 (0 = 오늘 · 이번 주 · 이번 달, -1 = 바로 이전) */
function periodOffset(period: ReflectionPeriod, from: string, today: string) {
  const cur = periodRange(period, today).from
  if (period === 'MONTH') return (yearOf(from) - yearOf(cur)) * 12 + monthOf(from) - monthOf(cur)
  const days = Math.round((Date.parse(from) - Date.parse(cur)) / 86_400_000)
  return period === 'WEEK' ? Math.round(days / 7) : days
}

/** 같은 타임라인이 쓰인 화면 (Amplitude surface) */
export type ReflectionTimelineSurface = 'reflections' | 'goal_detail' | 'work_board'

/** 기간 이동 (‹ ›) */
const shift = (period: ReflectionPeriod, anchor: string, n: number) =>
  period === 'DAY' ? addDays(anchor, n) : period === 'WEEK' ? addDays(anchor, n * 7) : addMonths(anchor, n)

function periodTitle(period: ReflectionPeriod, from: string, to: string, today: string) {
  if (period === 'DAY') return from === today ? `오늘 · ${formatMonthDay(from)}` : formatMonthDay(from)
  if (period === 'MONTH') return `${yearOf(from)}년 ${monthOf(from)}월`
  const thisWeek = startOfWeek(today) === from
  return `${thisWeek ? '이번 주 · ' : ''}${monthOf(from)}월 ${dayOfMonth(from)}일 – ${
    monthOf(to) !== monthOf(from) ? `${monthOf(to)}월 ` : ''
  }${dayOfMonth(to)}일`
}

/**
 * 기간별 회고 목록 — 일간 · 주간 · 월간 탭 + ‹ › 로 기간 이동. 회고 모아보기와 목표 상세 "회고" 탭이 같이 써요.
 *  - 일간: 그날 남긴 기록 · 회고
 *  - 주간(기본): 월~일, 날짜별로 묶어서 + 기분 요약
 *  - 월간: 그 달 전체, 날짜별로 묶어서 + 기분 요약 · 가장 많이 기록한 목표
 * @param goalId    한 목표만 (없으면 전체)
 * @param showGoal  줄마다 목표 이름을 보일지 (한 목표만 볼 땐 끄기)
 * @param emptyHint 비었을 때 안내 문구
 * @param surface   Amplitude surface — 회고 모아보기(reflections) / 목표 상세 회고 탭(goal_detail)
 */
export function ReflectionTimeline({
  goalId,
  showGoal = true,
  emptyHint,
  surface,
}: {
  goalId?: number
  showGoal?: boolean
  emptyHint?: string
  surface: ReflectionTimelineSurface
}) {
  const today = todayKst()
  const [period, setPeriod] = useState<TimelinePeriod>('ALL')
  const [anchor, setAnchor] = useState(today)
  const all = period === 'ALL'
  // 전체는 기간이 없어요 — 아래 계산은 주간 기준 값을 써도 화면에 안 쓰여요
  const ranged: ReflectionPeriod = all ? 'WEEK' : period
  const { from, to } = periodRange(ranged, anchor)
  const range = useReflectionRange(from, to, goalId, !all)
  const recent = useReflectionFeed(goalId, all)
  const feed = all ? recent : range
  const entries = all ? (recent.data?.pages.flatMap((p) => p.entries) ?? []) : (range.data?.entries ?? [])
  const groups = byDate(entries)
  const atLatest = to >= today

  const checkIns = entries.filter((e) => e.kind === 'CHECK_IN')
  const moodCounts = MOODS.map((m) => ({ ...m, count: checkIns.filter((e) => e.mood === m.value).length }))
  const days = groups.length
  const totalDays = Math.min(
    Math.round((Date.parse(atLatest ? today : to) - Date.parse(from)) / 86_400_000) + 1,
    Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1
  )

  // ── Amplitude ──
  const offset = all ? 0 : periodOffset(ranged, from, today)
  const viewKey = `${period}:${all ? '' : from}:${goalId ?? ''}`
  const entryCount = entries.length
  const checkInCount = checkIns.length
  const retroCount = entries.filter((e) => e.kind === 'RETRO').length
  const truncated = all ? recent.hasNextPage : (range.data?.hasMore ?? false)
  // 지금 기간의 데이터를 그렸는지 (이전 기간 자리 표시 데이터는 빼요)
  const shown = feed.isSuccess && !feed.isPlaceholderData

  // 보기 · 빈 화면 — 처음 · 기간 탭 · ‹ › 이동 · 목표 칩마다 한 번
  const lastViewKey = useRef('')
  useEffect(() => {
    if (!shown || lastViewKey.current === viewKey) return
    lastViewKey.current = viewKey
    track('reflection_timeline.view', {
      surface,
      goal_category_id: goalId,
      period: periodProp(period),
      period_offset: offset,
      entry_count: entryCount,
      check_in_count: checkInCount,
      retro_count: retroCount,
      day_count: days,
      elapsed_day_count: all ? undefined : totalDays,
      is_truncated: truncated,
    })
    if (entryCount === 0) {
      track('reflection_timeline.empty_view', {
        surface,
        goal_category_id: goalId,
        period: periodProp(period),
        period_offset: offset,
      })
    }
  }, [
    shown,
    viewKey,
    surface,
    goalId,
    period,
    offset,
    entryCount,
    checkInCount,
    retroCount,
    days,
    totalDays,
    truncated,
    all,
  ])

  // 불러오기 실패 카드 (실패할 때마다 한 번)
  const failed = feed.isError
  const failedAt = feed.errorUpdatedAt
  const feedError = feed.error
  const lastFailAt = useRef(0)
  useEffect(() => {
    if (!failed || lastFailAt.current === failedAt) return
    lastFailAt.current = failedAt
    track('reflection_timeline.load_fail', {
      surface,
      period: periodProp(period),
      period_offset: offset,
      error_status: errorStatusOf(feedError),
    })
  }, [failed, failedAt, feedError, surface, period, offset])

  // 목록 스크롤 깊이 25 · 50 · 75 · 100% (목록 끝이 화면 아래에 얼마나 들어왔는지, 기간마다 구간별 한 번)
  const listRef = useRef<HTMLOListElement>(null)
  const scrollSent = useRef(new Set<string>())
  useEffect(() => {
    if (!shown || entryCount === 0) return
    const onScroll = () => {
      const el = listRef.current
      if (!el) return
      const rect = el.getBoundingClientRect()
      if (rect.height <= 0) return
      const pct = ((window.innerHeight - rect.top) / rect.height) * 100
      for (const mark of [25, 50, 75, 100]) {
        const key = `${viewKey}:${mark}`
        if (pct >= mark - 1 && !scrollSent.current.has(key)) {
          scrollSent.current.add(key)
          track('reflection_timeline.scroll_depth', {
            surface,
            period: periodProp(period),
            scroll_percent: mark,
            entry_count: entryCount,
          })
        }
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [shown, viewKey, surface, period, entryCount])

  const changePeriod = (p: TimelinePeriod) => {
    if (p === period) return
    track('reflection_timeline.period_change', {
      surface,
      period: periodProp(p),
      from_period: periodProp(period),
      period_offset: p === 'ALL' ? 0 : periodOffset(p, periodRange(p, anchor).from, today),
    })
    setPeriod(p)
  }
  /** ‹ › · 오늘/이번 주/이번 달 — period_offset 은 이동한 뒤 기간 */
  const move = (action: 'prev' | 'next' | 'today', next: string) => {
    track('reflection_timeline.nav_click', {
      surface,
      period: periodProp(period),
      date_action: action,
      period_offset: periodOffset(ranged, periodRange(ranged, next).from, today),
    })
    setAnchor(next)
  }

  return (
    <div className="flex flex-col gap-4">
      {/* 기간 고르기 (전체 · 일간 · 주간 · 월간) */}
      <div className={segmentedClass('self-start')} role="tablist" aria-label="기간">
        {PERIODS.map((p) => (
          <button
            key={p.value}
            type="button"
            role="tab"
            aria-selected={period === p.value}
            onClick={() => changePeriod(p.value)}
            className={segmentClass(period === p.value)}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* 기간 이동 — 왼쪽에 붙은 작은 묶음: ‹ 기간 › + 오늘/이번 주/이번 달 */}
      {!all && (
        <div className="-ml-1 flex w-fit items-center gap-1">
          <button
            type="button"
            onClick={() => move('prev', shift(ranged, anchor, -1))}
            aria-label="이전 기간"
            className={iconButtonClass('sm')}
          >
            <ChevronLeft className="size-4" />
          </button>
          <p className="text-body-md text-text-primary font-medium whitespace-nowrap tabular-nums">
            {periodTitle(ranged, from, to, today)}
          </p>
          <button
            type="button"
            onClick={() => move('next', shift(ranged, anchor, 1))}
            disabled={atLatest}
            aria-label="다음 기간"
            className={iconButtonClass('sm')}
          >
            <ChevronRight className="size-4" />
          </button>
          {!atLatest && (
            <button
              type="button"
              onClick={() => move('today', today)}
              className="text-label-md text-text-brand hover:bg-action-ghost-hover h-7 rounded-[6px] px-2 whitespace-nowrap"
            >
              {period === 'DAY' ? '오늘' : period === 'WEEK' ? '이번 주' : '이번 달'}
            </button>
          )}
        </div>
      )}

      {feed.isPending ? (
        <div className="grid min-h-[240px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      ) : feed.isError ? (
        <Card className="p-6 text-center">
          <p className="text-ink-2 text-sm">회고를 불러오지 못했어요.</p>
          <Button
            size="sm"
            variant="secondary"
            className="mt-3"
            onClick={() => {
              track('reflection_timeline.retry_click', {
                surface,
                period: periodProp(period),
                error_status: errorStatusOf(feed.error),
              })
              feed.refetch()
            }}
          >
            다시 불러오기
          </Button>
        </Card>
      ) : (
        <div className={cn('flex flex-col gap-4 transition-opacity', feed.isPlaceholderData && 'opacity-60')}>
          {/* 이 기간 요약 (주간 · 월간) */}
          {/* 이 기간 요약 (전체 · 주간 · 월간 — 전체는 불러온 회고 기준, 분모 없이) — surface · border/subtle · 모서리 16 · p-4 */}
          {period !== 'DAY' && entries.length > 0 && (
            <Card className="flex flex-col gap-3 p-4 shadow-none">
              <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
                <div>
                  <p className="text-caption-md text-text-secondary">기록한 날</p>
                  <p className="text-h3 text-text-primary mt-0.5 font-semibold tabular-nums">
                    {days}일{!all && <span className="text-text-tertiary"> / {totalDays}일</span>}
                  </p>
                </div>
                {checkIns.some((e) => e.mood) && (
                  <ul className="flex items-center gap-3" aria-label="기분별 날 수">
                    {moodCounts.map((m) => (
                      <li key={m.value} className="flex items-center gap-1" title={m.label} aria-label={`${m.label} ${m.count}`}>
                        <span aria-hidden className="text-[20px] leading-none">
                          {m.emoji}
                        </span>
                        <span className="text-label-lg text-text-primary tabular-nums">{m.count}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              {showGoal && <TopGoal entries={entries} />}
            </Card>
          )}

          {groups.length === 0 ? (
            <Card className="flex flex-col items-center gap-2 px-6 py-12 text-center">
              <span className="bg-subtle text-ink-3 grid size-11 place-items-center rounded-full">
                <Library className="size-5" />
              </span>
              <p className="text-[15px] font-bold">
                {all ? '아직' : period === 'DAY' ? '이날' : period === 'WEEK' ? '이번 주' : '이 달'} 남긴 회고가 없어요
              </p>
              <p className="text-ink-3 text-[13px]">
                {emptyHint ??
                  '홈의 \u2018오늘 회고\u2019나 목표 상세의 \u2018오늘 기록\u2019에서 하루를 돌아보면 여기 모여요.'}
              </p>
            </Card>
          ) : (
            <ol ref={listRef} className="flex flex-col gap-4">
              {groups.map((g) => (
                <li key={g.date}>
                  {period !== 'DAY' && (
                    <h3 className="mb-2 flex items-baseline gap-2 px-1">
                      <span className="text-[15px] font-bold">{formatMonthDay(g.date)}</span>
                      <span className="text-ink-3 text-[12px]">{relLabel(g.date, today)}</span>
                    </h3>
                  )}
                  <Card>
                    {g.items.map((e, i) => (
                      <div key={`${e.kind}-${e.goalCategoryId}-${e.retrospectiveId ?? e.date}`}>
                        {/* 줄 사이 구분선 — 양옆 16 안쪽 */}
                        {i > 0 && <div className="bg-divider mx-4 h-px" aria-hidden />}
                        <EntryRow
                          entry={e}
                          showGoal={showGoal && (!goalId || new Set(g.items.map((x) => x.goalCategoryId)).size > 1)}
                          surface={surface}
                          today={today}
                        />
                      </div>
                    ))}
                  </Card>
                </li>
              ))}
            </ol>
          )}

          {/* 전체: 그 전 회고 더 보기 (30일씩) */}
          {all && recent.hasNextPage && (
            <Button
              variant="secondary"
              size="sm"
              className="self-center"
              loading={recent.isFetchingNextPage}
              onClick={() => void recent.fetchNextPage()}
            >
              이전 회고 더 보기
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

/** 이 기간에 기록을 가장 많이 남긴 목표 (전체 보기에서만) */
function TopGoal({ entries }: { entries: ReflectionEntry[] }) {
  const byGoal = new Map<number, { name: string; emoji: string | null; color: string | null; days: Set<string> }>()
  for (const e of entries) {
    const g = byGoal.get(e.goalCategoryId) ?? {
      name: e.goalName,
      emoji: e.goalEmoji,
      color: e.colorCode,
      days: new Set<string>(),
    }
    g.days.add(e.date)
    byGoal.set(e.goalCategoryId, g)
  }
  const top = [...byGoal.values()].sort((a, b) => b.days.size - a.days.size)[0]
  if (!top || byGoal.size < 2) return null
  return (
    <div className="border-border-soft flex min-w-0 items-center gap-2 border-t pt-3">
      <span className="text-caption-md text-text-secondary shrink-0">가장 많이 돌아본 목표</span>
      <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: goalAccent(top.color) }} aria-hidden />
      <span className="text-body-md text-text-primary min-w-0 truncate font-medium">
        {top.emoji ? `${top.emoji} ` : ''}
        {top.name}
      </span>
      <span className="text-caption-md text-text-tertiary shrink-0">· {top.days.size}일</span>
    </div>
  )
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'flex h-8 max-w-[220px] items-center gap-1.5 rounded-full border px-3 text-[12px] font-semibold transition-colors',
        active ? 'border-text-brand bg-ai-tint-soft text-text-brand' : 'border-border-subtle bg-bg-surface text-text-primary hover:bg-bg-sunken'
      )}
    >
      {children}
    </button>
  )
}

function EntryRow({
  entry: e,
  showGoal,
  surface,
  today,
}: {
  entry: ReflectionEntry
  showGoal: boolean
  surface: ReflectionTimelineSurface
  today: string
}) {
  const mood = moodOf(e.mood)
  const isRetro = e.kind === 'RETRO'
  const ago = daysAgo(e.date, today)
  const [detailOpen, setDetailOpen] = useState(false)
  // 줄을 누르면 자세히 보기 모달 (목표 이름 링크 · 글자 고르기는 빼요)
  const onRowClick = (ev: React.MouseEvent<HTMLDivElement>) => {
    if (ev.target instanceof Element && ev.target.closest('a')) return
    if (window.getSelection()?.toString()) return
    track('reflection_timeline.entry_click', {
      surface,
      entry_kind: e.kind,
      days_ago: ago,
      goal_mode: e.goalMode ?? undefined,
    })
    setDetailOpen(true)
  }
  return (
    // 회고 카드 줄 — 목표 줄 → 배지 줄(한 줄) → 내용
    // 모달은 줄 밖에 둬요 (포털 안 클릭이 React 트리로 줄까지 올라와 다시 열리지 않게)
    <>
      <div
        role="button"
        tabIndex={0}
        aria-haspopup="dialog"
        className="hover:bg-bg-sunken/60 cursor-pointer px-4 py-3 transition-colors"
        onClick={onRowClick}
        onKeyDown={(ev) => {
          if (ev.target !== ev.currentTarget) return
          if (ev.key === 'Enter' || ev.key === ' ') {
            ev.preventDefault()
            setDetailOpen(true)
          }
        }}
      >
        {showGoal && (
          <Link
            href={`/goal/${e.goalCategoryId}`}
            onClick={() =>
              track('reflection_timeline.goal_link_click', {
                surface,
                goal_category_id: e.goalCategoryId,
                goal_mode: e.goalMode ?? undefined,
                entry_kind: e.kind,
                days_ago: ago,
              })
            }
            className="text-label-sm text-text-secondary mb-1.5 flex w-fit max-w-full min-w-0 items-center gap-1.5 hover:underline"
          >
            <span
              className="size-2 shrink-0 rounded-full"
              style={{ backgroundColor: goalAccent(e.colorCode) }}
              aria-hidden
            />
            <span className="truncate">
              {e.goalEmoji ? `${e.goalEmoji} ` : ''}
              {e.goalName}
            </span>
          </Link>
        )}
        {(e.value != null || mood || (isRetro && e.reviewType && e.reviewType !== 'DAILY')) && (
          <div className="flex min-w-0 flex-nowrap items-center gap-1.5">
            {e.value != null && (
              <span className="bg-badge-bg flex min-w-0 shrink items-center gap-1 rounded-full px-2 py-1 text-[13px] whitespace-nowrap">
                <span className="text-text-secondary truncate font-medium">{e.metricName ?? '수치'}</span>
                <span className="text-text-primary shrink-0 font-semibold">
                  {fmt(e.value)}
                  {e.unit ?? ''}
                </span>
              </span>
            )}
            {mood && (
              <span className="bg-badge-bg min-w-0 shrink truncate rounded-full px-2 py-1 text-[12px] font-semibold whitespace-nowrap">
                {mood.emoji} {mood.label}
              </span>
            )}
            {isRetro && e.reviewType && e.reviewType !== 'DAILY' && (
              <span className="text-text-secondary shrink-0 text-[11px] font-semibold whitespace-nowrap">
                {e.reviewType === 'WEEKLY' ? '주간 회고' : '월간 회고'}
              </span>
            )}
          </div>
        )}
        {e.note ? (
          <p className="text-ink-2 mt-1.5 line-clamp-4 text-[14px] leading-relaxed whitespace-pre-wrap">{e.note}</p>
        ) : (
          !isRetro && <p className="text-ink-4 mt-1.5 text-[12px]">수치만 남겼어요</p>
        )}
      </div>
      {detailOpen && (
        <ReflectionDetailModal
          entry={e}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          surface={surface}
          daysAgo={ago}
        />
      )}
    </>
  )
}
