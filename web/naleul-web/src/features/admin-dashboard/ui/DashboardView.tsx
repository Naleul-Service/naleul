'use client'

import { useState } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { Chip } from '@/components/ui/Chip'
import { Spinner } from '@/components/ui/Spinner'
import { isApiError } from '@/lib/client/api'
import { cn } from '@/lib/cn'
import { useAdminDashboard } from '../api'
import type { AdminDashboard } from '../types'
import { dateTimeLabel, dotDate, num, pct } from '../format'
import { GoalGroups, GoalHero } from './GoalSection'
import { GoalPathPanel, PracticePanel } from './PracticeSection'
import { AiQualityPanel, OutcomeCheckPanel } from './AiQualitySection'
import { ChatReviewPanel } from './ChatReviewSection'
import { DraftReviewPanel } from './DraftReviewSection'
import { AhaPanel } from './AhaSection'
import { AiInterestPanel } from './AiInterestSection'
import { AiCostPanel, ProfitPanel, RevenueAndServerPanel } from './MoneySection'
import { Empty, Panel, Stat, Tile } from './Panel'
import { MiniBars, RateTrend } from './RateTrend'

const PERIODS = [
  { days: 7, label: '7일' },
  { days: 30, label: '30일' },
  { days: 90, label: '90일' },
  { days: 365, label: '1년' },
  { days: 3650, label: '전체' },
] as const

/** 정보가 많아서 항목별 탭으로 나눠요 (2026-10-25) */
const TABS = [
  { value: 'service', label: '서비스 지표' },
  { value: 'aha', label: 'Aha-moment' },
  { value: 'interest', label: 'AI 관심도' },
  { value: 'ai', label: 'AI 품질' },
  { value: 'chat', label: 'AI 채팅 정리' },
  { value: 'draft', label: 'AI 초안' },
  { value: 'money', label: '수익 · 비용' },
] as const
type DashTab = (typeof TABS)[number]['value']
const TAB_KEY = 'naleul.admin-dashboard.tab'

/**
 * 운영 대시보드 (dev 전용 화면).
 * 숫자는 전부 백엔드에서 계산해요 — 화면은 보여주기만 해요.
 */
export function DashboardView() {
  const [days, setDays] = useState<number>(30)
  // 마지막으로 본 탭 기억 (이 브라우저에서만, 실패하면 첫 탭).
  // 데이터를 받기 전엔 탭을 그리지 않아서 서버 렌더와 어긋나지 않아요.
  const [tab, setTab] = useState<DashTab>(() => {
    if (typeof window === 'undefined') return 'service'
    try {
      const saved = window.localStorage.getItem(TAB_KEY)
      return TABS.some((t) => t.value === saved) ? (saved as DashTab) : 'service'
    } catch {
      return 'service'
    }
  })
  const { data, isPending, isError, error, refetch, isFetching } = useAdminDashboard(days)

  const pickTab = (t: DashTab) => {
    setTab(t)
    try {
      window.localStorage.setItem(TAB_KEY, t)
    } catch {
      // 무시
    }
  }

  const header = (
    <PageHeader
      breadcrumb="설정 · 개발자"
      title="운영 대시보드"
      description={
        data
          ? `${dotDate(data.period.from)} – ${dotDate(data.period.to)} · ${data.period.days}일 · ${dateTimeLabel(data.period.generatedAt)} 기준`
          : '전체 사용자 기준 서비스 지표와 수익 · 비용'
      }
      actions={
        <button
          type="button"
          onClick={() => refetch()}
          className="text-ink-2 hover:text-ink inline-flex items-center gap-1.5 text-[13px]"
          disabled={isFetching}
        >
          <RefreshCw className={isFetching ? 'size-4 animate-spin' : 'size-4'} />
          새로고침
        </button>
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
    const forbidden = isApiError(error) && error.httpStatus === 403
    return (
      <>
        {header}
        <Card className="mt-6 grid min-h-[240px] place-items-center p-6 text-center">
          <div>
            <p className="text-[15px] font-bold">
              {forbidden ? '운영진만 볼 수 있어요' : '대시보드를 불러오지 못했어요'}
            </p>
            <p className="text-ink-3 mt-1 text-[13px]">
              {forbidden
                ? '백엔드 admin.dashboard.admin-user-ids 에 내 user_id 가 있는지 확인해 주세요.'
                : isApiError(error)
                  ? error.message
                  : '잠시 후 다시 시도해 주세요.'}
            </p>
          </div>
        </Card>
      </>
    )
  }

  return (
    <>
      {header}

      <div className="mt-5 flex flex-wrap items-center gap-2">
        {PERIODS.map((p) => (
          <Chip key={p.days} size="sm" selected={days === p.days} onClick={() => setDays(p.days)}>
            {p.label}
          </Chip>
        ))}
        {isFetching && <Spinner className="text-ink-3 ml-1 size-3.5" />}
      </div>

      <div
        className="border-border-soft mt-4 flex scrollbar-none gap-1 overflow-x-auto border-b"
        role="tablist"
        aria-label="대시보드 항목"
      >
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            id={`dash-tab-${t.value}`}
            aria-selected={tab === t.value}
            aria-controls={`dash-panel-${t.value}`}
            onClick={() => pickTab(t.value)}
            className={cn(
              'text-label-lg -mb-px flex h-11 shrink-0 items-center border-b-2 px-3 transition-colors',
              tab === t.value
                ? 'border-action-primary-bg text-text-primary'
                : 'text-text-secondary hover:text-text-primary border-transparent'
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`dash-panel-${tab}`} aria-labelledby={`dash-tab-${tab}`} className="mt-5 space-y-4">
        {tab === 'service' && (
          <>
            <Notes notes={data.notes} />
            <GoalHero goals={data.goals} check={data.aiQuality?.outcomeCheck} />
            {/* Main 핵심 지표 × AI 정확도 (2026-10-25) — AI 초안을 어떻게 받아들였는지별 달성률 · 진행 중 실천률 */}
            <GoalPathPanel rows={data.goals.byPath} inProgress={data.goals.inProgressByPath} />
            <ExecutionTiles data={data} />

            <Panel
              title="Task · 루틴 실천률 추이"
              aside={data.period.bucket === 'WEEK' ? '주 단위 · 하기로 한 날 기준' : '하기로 한 날 기준'}
            >
              <RateTrend points={data.series} />
            </Panel>

            {/* Sub 핵심 지표 · Task 실천률 쪼개 보기 (2026-10-25) — 쓴 날 기준(B) · 출처별 */}
            <PracticePanel practice={data.practice} taskRate={data.tasks.rate} routineRate={data.routines.rate} />

            <GoalGroups title="목표 종류별 달성률" rows={data.goals.byType} aside="기간 중 끝난 목표" />

            <UsersPanel data={data} />
          </>
        )}

        {/* Aha-moment (V69) — 가입 후 7일 안에 목표 2개 · Task 3개 · 3일 방문 */}
        {tab === 'aha' && <AhaPanel days={days} />}

        {/* AI 관심도 (V70) — 기능별 시도 · 완료 · 중단, Free / Pro */}
        {tab === 'interest' && <AiInterestPanel days={days} />}

        {tab === 'ai' &&
          (data.aiQuality ? (
            <>
              <AiQualityPanel quality={data.aiQuality} onOpenDraft={() => pickTab('draft')} />
              <OutcomeCheckPanel check={data.aiQuality.outcomeCheck} />
            </>
          ) : (
            <Panel title="AI 품질">
              <Empty />
            </Panel>
          ))}

        {/* AI 채팅 정리 정확도 (V67) — 정리 화면에서 항목별로 얼마나 고쳤는지 */}
        {tab === 'chat' && <ChatReviewPanel days={days} />}

        {/* AI 초안 정확도 (V68) — 초안 화면 · 확정 뒤 첫 행동부터 14 · 30일 */}
        {tab === 'draft' && <DraftReviewPanel days={days} />}

        {tab === 'money' && (
          <>
            <ProfitPanel finance={data.finance} activeUsers={data.users.activeInPeriod} />
            <div className="grid gap-4 lg:grid-cols-2">
              <AiCostPanel finance={data.finance} />
              <RevenueAndServerPanel finance={data.finance} />
            </div>
            <Panel title={data.period.bucket === 'WEEK' ? '주별 AI 비용' : '일별 AI 비용'} aside="달러">
              <MiniBars
                points={data.series}
                value={(p) => p.aiUsd}
                format={(n) => `$${n.toFixed(n < 1 ? 3 : 2)}`}
                color="var(--color-warning)"
                label="AI 비용 추이"
              />
            </Panel>
          </>
        )}
      </div>
    </>
  )
}

/** 설정이 비었거나 기록이 아직 없을 때 백엔드가 알려주는 안내 */
function Notes({ notes }: { notes: string[] }) {
  if (!notes.length) return null
  return (
    <div className="bg-warning-soft text-warning-ink-strong mt-4 rounded-xl px-4 py-3 text-[13px]">
      <ul className="space-y-1">
        {notes.map((n) => (
          <li key={n} className="flex gap-2">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" />
            <span>{n}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Task · 루틴 실천률 · AI 목표 만들기 전환율 · 활동 사용자 */
function ExecutionTiles({ data }: { data: AdminDashboard }) {
  const { tasks: t, routines: r, aiGoalFunnel: f, users: u } = data
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      <Tile label="Task 실천률" value={pct(t.rate)} sub={`${num(t.completed)} / ${num(t.due)}개 · ${num(t.users)}명`} />
      <Tile
        label="루틴 실천률"
        value={pct(r.rate)}
        sub={`${num(r.completed)} / ${num(r.due)}회 · 건너뜀 ${num(r.skipped)}`}
      />
      <Tile
        label="AI 목표 만들기 → 확정"
        value={pct(f.conversionRate)}
        sub={`대화 ${num(f.sessions)} · 초안 ${num(f.drafts)} · 확정 ${num(f.confirmed)}`}
      />
      <Tile label="활동 사용자" value={num(u.activeInPeriod)} sub="기간 중 Task 를 1개 이상 완료" />
    </div>
  )
}

function UsersPanel({ data }: { data: AdminDashboard }) {
  const u = data.users
  return (
    <Panel title="사용자" aside="활동 = Task(루틴 포함)를 1개 이상 완료">
      <div className="grid grid-cols-2 gap-x-4 gap-y-5 sm:grid-cols-5">
        <Stat label="전체 가입자" value={num(u.total)} />
        <Stat label="Pro" value={num(u.pro)} sub={`전환율 ${pct(u.proRatePercent)}`} />
        <Stat label="최근 7일 활동" value={num(u.active7d)} sub={`가입자의 ${pct(percent(u.active7d, u.total))}`} />
        <Stat label="최근 30일 활동" value={num(u.active30d)} sub={`가입자의 ${pct(percent(u.active30d, u.total))}`} />
        <Stat
          label="진행 중 목표 보유"
          value={num(u.withActiveGoal)}
          sub={`가입자의 ${pct(percent(u.withActiveGoal, u.total))}`}
        />
      </div>
      <div className="border-line mt-5 border-t pt-4">
        <p className="text-ink-3 mb-2 text-[12px]">
          {data.period.bucket === 'WEEK' ? '주별 활동 사용자 (그 주 하루 최대)' : '일별 활동 사용자'}
        </p>
        <MiniBars points={data.series} value={(p) => p.activeUsers} format={(n) => `${n}명`} label="활동 사용자 추이" />
      </div>
    </Panel>
  )
}

const percent = (part: number, whole: number) => (whole > 0 ? Math.round((100 * part) / whole) : null)
