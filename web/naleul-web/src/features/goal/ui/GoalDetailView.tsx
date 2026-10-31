'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { CalendarCheck, KanbanSquare, Library, ListChecks, Sparkles } from 'lucide-react'
import { BarChart3, EllipsisVertical, NotebookPen } from '@/components/icons'
import { Flag } from 'lucide-react'
import { IconButton } from '@/components/ui/IconButton'
import { rectOf, type AnchorRect } from '@/components/ui/Popover'
import { ActionMenu } from '@/features/timetable/ui/ActionMenu'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { track } from '@/lib/client/analytics'
import { goalVars } from '@/lib/goalTone'
import { isApiError } from '@/lib/client/api'
import { errorStatusOf } from '@/features/home/analytics'
import { RecordPatternSection } from '@/features/record/ui/pattern/RecordPatternSection'
import { HabitView } from '@/features/habit/ui/HabitView'
import { ReflectionTimeline } from '@/features/reflection/ui/ReflectionsView'
import { isHabitGoal, isRecordGoal, useGoalCategory, type GoalCategory } from '../api'
import { EditingContext } from '../edit/inline'
import { dDayLabel, periodProgress } from '../format'
import { GoalAiEditBar } from './GoalAiEditBar'
import { GoalHeader, GoalNotes, routineCountOf } from './GoalHeader'
import { GoalScrollTitle, GoalTabBar, useGoalTab, type TabDef } from './GoalTabs'
import { GoalFab, GoalFabs } from './GoalFab'
import { Modal } from '@/components/ui/Modal'
import { WorkBoardView, WorkSummaryCard } from '@/features/work/ui/WorkBoardView'
import {
  GoalAdjustmentBanner,
  GoalOutcomeSection,
  GoalWrapUpSection,
  type GoalWrapUpHandle,
} from './GoalOutcomeSection'
import { GoalTaskList } from './GoalTaskList'
import { PlaceAfterCreateCard } from './PlaceAfterCreateCard'
import { DailyCheckInCard } from './progress/DailyCheckInCard'
import { ExtraMetricsSection } from './progress/ExtraMetricsSection'
import { ProgressOverview } from './progress/ProgressOverview'
import { MilestonesSection } from './MilestonesSection'
import { RefineWithAiCard } from './RefineWithAiCard'
import { RoutineHeatmapSection } from './RoutineHeatmapSection'
import { RoutinesSection } from './RoutinesSection'
import { Section } from './sections'

// ─── 작은 조각들 ───────────────────────────────────────────────

function Ring({ ratio, label }: { ratio: number; label: string }) {
  const R = 52
  const C = 2 * Math.PI * R
  return (
    <div className="relative size-32 shrink-0">
      <svg viewBox="0 0 120 120" className="size-full -rotate-90">
        <circle cx="60" cy="60" r={R} fill="none" className="stroke-bg-surface/70" strokeWidth="10" />
        <circle
          cx="60"
          cy="60"
          r={R}
          fill="none"
          style={{ stroke: 'var(--goal-accent)' }}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={C}
          strokeDashoffset={C * (1 - Math.min(Math.max(ratio, 0), 1))}
        />
      </svg>
      <span className="text-text-primary absolute inset-0 grid place-items-center text-[26px] font-bold">{label}</span>
    </div>
  )
}

function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-bg-surface/70 min-w-[120px] flex-1 rounded-xl px-4 py-3">
      <p className="text-caption-md text-text-secondary">{label}</p>
      <p className="text-h2 text-text-primary mt-0.5 whitespace-nowrap">{value}</p>
    </div>
  )
}

// ─── 영역별 ────────────────────────────────────────────────────

function Hero({ goal }: { goal: GoalCategory }) {
  const period = periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
  const hasMetric = goal.targetValue != null && goal.startValue != null
  const unit = goal.metricUnit ?? ''

  // 수치 목표면 수치 진행률, 아니면 기간 경과율
  let ratio = period?.ratio ?? 0
  let caption = '기간 경과'
  let headline = period ? `${period.elapsed}일째` : '-'
  if (hasMetric) {
    const current = goal.currentValue ?? goal.startValue!
    const span = goal.targetValue! - goal.startValue!
    ratio = span === 0 ? 1 : (current - goal.startValue!) / span
    caption = `목표까지 남은 ${goal.metricName ?? ''}`.trim()
    headline = `${Math.abs(goal.targetValue! - current)
      .toFixed(1)
      .replace(/\.0$/, '')}${unit}`
  }

  return (
    // 목표 요약 카드 (Figma NowCard) — 목표 톤 바탕 (다크 모드는 목표 dark bg)
    <section
      style={goalVars(goal.colorCode)}
      className="text-text-primary rounded-card flex flex-col gap-6 border border-[color-mix(in_srgb,var(--goal-accent)_20%,transparent)] bg-(--goal-bg) p-6 sm:p-8 lg:flex-row lg:items-center"
    >
      <div className="flex items-center gap-6">
        <Ring ratio={ratio} label={`${Math.round(Math.min(Math.max(ratio, 0), 1) * 100)}%`} />
        <div>
          <p className="text-text-secondary text-sm">{caption}</p>
          <p className="mt-1 text-[40px] leading-none font-bold tracking-tight text-(--goal-fg)">{headline}</p>
        </div>
      </div>
      {period && (
        <div className="flex flex-1 flex-wrap gap-3 lg:justify-end">
          {hasMetric && <StatBox label="현재" value={`${goal.currentValue ?? goal.startValue}${unit}`} />}
          <StatBox label="경과" value={`${period.elapsed} / ${period.total}일`} />
          <StatBox label="남은 기간" value={dDayLabel(period.remain)} />
        </div>
      )}
    </section>
  )
}

/** "회고" 탭 — 이 목표에서 남긴 기록 · 회고를 날짜별로 (회고 모아보기와 같은 목록) */
function ReflectionsPanel({ goal, hint }: { goal: GoalCategory; hint: string }) {
  return (
    <section id="goal-panel-reflections" role="tabpanel" aria-labelledby="goal-tab-reflections" className="mt-5">
      <div className="mb-4 flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <h2 className="text-h4">이 목표의 회고</h2>
        <p className="text-ink-3 text-[13px]">{hint}</p>
        <Link
          href="/reflections?from=goal_detail"
          onClick={() =>
            track('goal_detail.reflections_all_click', {
              goal_category_id: goal.goalCategoryId,
              goal_mode: goal.goalMode ?? 'ACHIEVEMENT',
            })
          }
          className="text-text-brand ml-auto text-[12px] font-semibold hover:underline"
          title="모든 목표의 회고를 한 번에"
        >
          모든 목표 회고 보기
        </Link>
      </div>
      <ReflectionTimeline goalId={goal.goalCategoryId} showGoal={false} emptyHint={hint} surface="goal_detail" />
    </section>
  )
}

// ─── 기록형 목표 ───────────────────────────────────────────────

type RecordTab = 'board' | 'stats' | 'reflections'

const RECORD_TABS: TabDef<RecordTab>[] = [
  { value: 'board', label: '업무관리', icon: KanbanSquare }, // 명세 13.9 — 분석 값은 그대로 board
  { value: 'stats', label: '통계', icon: BarChart3 },
  { value: 'reflections', label: '회고', icon: Library },
]

/**
 * 업무형 목표 — 탭 세 개.
 *  - 보드(기본): 다음 마감 · 로드맵(주제별 남은 할 일) · Task 보드 · 업무 일지 · 루틴 · 메모 + 오른쪽 "적기" 패널
 *  - 통계: 이 목표에 대한 나의 패턴 + 루틴 실천
 *  - 회고: 이 목표에 남긴 회고를 날짜별로
 */
function RecordGoalBody({ goal }: { goal: GoalCategory }) {
  const [tab, setTab] = useGoalTab(RECORD_TABS)

  return (
    <>
      <GoalTabBar tabs={RECORD_TABS} tab={tab} onChange={(t) => setTab(t, goal)} />

      {tab === 'board' ? (
        <div id="goal-panel-board" role="tabpanel" aria-labelledby="goal-tab-board" className="mt-5">
          <WorkBoardView goal={goal} />
        </div>
      ) : tab === 'stats' ? (
        <section id="goal-panel-stats" role="tabpanel" aria-labelledby="goal-tab-stats" className="mt-5">
          <h2 className="sr-only">이 목표에 대한 나의 패턴</h2>
          <div className="space-y-5">
            <RecordPatternSection goalId={goal.goalCategoryId} />
            <RoutineHeatmapSection goal={goal} />
          </div>
        </section>
      ) : (
        <ReflectionsPanel
          goal={goal}
          hint="업무관리 탭의 '적기' 버튼(회고)이나 홈의 '오늘 회고'에서 남기면 여기 모여요."
        />
      )}
    </>
  )
}

// ─── 화면 ──────────────────────────────────────────────────────

/** /goal/[goalId] — 목표 상세. 각 영역을 그 자리에서 바로 추가·수정·삭제할 수 있어요 */
export function GoalDetailView({ goalId, justCreated = false }: { goalId: number; justCreated?: boolean }) {
  const { data: goal, isPending, isError, error, refetch } = useGoalCategory(goalId)
  // 한 번에 하나의 입력창만 열어요 (다른 걸 열면 앞의 것은 닫힘)
  const [editing, setEditing] = useState<string | null>(null)
  const [moreAt, setMoreAt] = useState<AnchorRect | null>(null)
  const wrapUp = useRef<GoalWrapUpHandle>(null)
  const editingCtx = useMemo(
    () => ({ editing, open: (key: string) => setEditing(key), close: () => setEditing(null) }),
    [editing]
  )
  const params = useSearchParams()
  const rawTab = params.get('tab')

  // 불러오기 실패 — 실패 상태로 바뀔 때마다 한 번 (다시 시도 후 또 실패하면 다시)
  const failSent = useRef(false)
  const errorStatus = errorStatusOf(error) ?? (isError ? 0 : undefined)
  useEffect(() => {
    if (!isError) {
      failSent.current = false
      return
    }
    if (failSent.current) return
    failSent.current = true
    track('goal_detail.load_fail', { goal_category_id: goalId, error_status: errorStatus })
  }, [isError, errorStatus, goalId])

  // 목표 상세 진입 — 처음 그려졌을 때 한 번. 기본 생활 습관 그릇은 habit.pageview 가 대신해요
  const viewSent = useRef(false)
  useEffect(() => {
    if (!goal || viewSent.current) return
    viewSent.current = true
    if (isHabitGoal(goal)) return
    const record = isRecordGoal(goal)
    const tabs: string[] = record ? RECORD_TABS.map((t) => t.value) : ACHIEVEMENT_TABS.map((t) => t.value)
    const period = periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
    track('goal_detail.pageview', {
      goal_category_id: goal.goalCategoryId,
      goal_mode: goal.goalMode ?? 'ACHIEVEMENT',
      goal_status: goal.goalCategoryStatus,
      is_temporary: !!goal.temporary,
      just_created: justCreated,
      tab: rawTab && tabs.includes(rawTab) ? rawTab : tabs[0],
      has_metric: goal.targetValue != null,
      routine_count: routineCountOf(goal),
      d_day: period?.remain,
    })
  }, [goal, justCreated, rawTab])

  if (isPending) {
    return (
      <div className="grid min-h-[60vh] place-items-center">
        <Spinner className="text-ink-3 size-6" />
      </div>
    )
  }

  if (isError || !goal) {
    const notFound = isApiError(error) && error.httpStatus === 404
    return (
      <Card className="mt-6 p-10 text-center">
        <p className="text-lg font-bold">{notFound ? '목표를 찾을 수 없어요' : '목표를 불러오지 못했어요'}</p>
        {!notFound && <p className="text-ink-3 mt-1 text-sm">{error?.message}</p>}
        <div className="mt-5 flex justify-center gap-2">
          <Link
            href="/goal"
            onClick={() => track('goal_detail.load_fail_cta_click', { cta: 'goal_list', error_status: errorStatus })}
            className="text-ink-2 px-3 py-2 text-sm font-semibold"
          >
            목표 목록
          </Link>
          {!notFound && (
            <Button
              onClick={() => {
                track('goal_detail.load_fail_cta_click', { cta: 'retry', error_status: errorStatus })
                refetch()
              }}
            >
              다시 시도
            </Button>
          )}
        </div>
      </Card>
    )
  }

  // 기본 생활 습관 그릇은 목표 상세 대신 습관 체크 화면 (V52)
  if (isHabitGoal(goal)) {
    return (
      <EditingContext.Provider value={editingCtx}>
        <HabitView goal={goal} />
      </EditingContext.Provider>
    )
  }

  // ⋯ 더보기 (명세 13.5) — 진행 중인 달성형 목표: 목표 마무리 (예전엔 개요 맨 아래 카드)
  const canWrapUp = !isRecordGoal(goal) && !goal.temporary && goal.goalCategoryStatus === 'IN_PROGRESS'
  const moreButton = canWrapUp ? (
    <IconButton label="더보기" onClick={(e) => setMoreAt(rectOf(e.currentTarget))}>
      <EllipsisVertical />
    </IconButton>
  ) : undefined

  return (
    <EditingContext.Provider value={editingCtx}>
      {/* goal-detail: 이 화면 카드 그림자는 더 연하게 (명세 12.3 — globals.css) */}
      <div className="goal-detail">
        <GoalScrollTitle title={`${goal.emoji ? `${goal.emoji} ` : ''}${goal.goalCategoryName}`} more={moreButton} />
        {canWrapUp && (
          <>
            <GoalWrapUpSection goal={goal} headless ref={wrapUp} />
            {moreAt && (
              <ActionMenu
                anchor={moreAt}
                onClose={() => setMoreAt(null)}
                label="더보기"
                items={[
                  { key: 'wrapup', label: '목표 마무리', icon: <Flag />, onSelect: () => wrapUp.current?.open('menu') },
                ]}
              />
            )}
          </>
        )}
        {isRecordGoal(goal) ? (
          // 업무형: 머리말 + 다음 마감 · 진행 · 공유를 한 요약 카드로 (명세 12.1)
          <div id="goal-summary">
            <WorkSummaryCard goal={goal} header={<GoalHeader goal={goal} embedded />} />
          </div>
        ) : (
          <div className="relative">
            <GoalHeader goal={goal} />
            {/* 넓은 화면: 머리말 오른쪽 위 ⋯ (모바일은 위 내비 줄에) */}
            {moreButton && <div className="absolute top-0 right-0 max-lg:hidden">{moreButton}</div>}
          </div>
        )}

        {/* 기록형은 만들 때 루틴·Task 가 없어서 "TimeTable 에 배치할까요?"를 묻지 않아요 */}
        {justCreated && !isRecordGoal(goal) && <PlaceAfterCreateCard goal={goal} className="mt-6" />}
        {goal.temporary && <RefineWithAiCard goal={goal} className="mt-6" />}

        {isRecordGoal(goal) ? <RecordGoalBody goal={goal} /> : <AchievementGoalBody goal={goal} />}
      </div>
    </EditingContext.Provider>
  )
}

/**
 * 달성형 목표 — 탭 세 개 (2026-10: 개요 → 실천 · 계획 분리).
 *  - 실천(기본, ?tab 없음): 주간 점검 제안 · 진행(수치 그래프 · 결과 · 추가 그래프 · 달성 기록)
 *  - 계획(?tab=plan): 마일스톤 → 루틴 설정 → 관련 Task
 *  - 회고(?tab=reflections): 이 목표에 남긴 기록 · 회고를 날짜별로
 *  "말로 고치기"(AI)는 계획 탭 안 입력 바 대신 오른쪽 아래 떠 있는 AI 버튼으로 (오늘 기록 버튼 위)
 */
type AchievementTab = 'practice' | 'plan' | 'reflections'

const ACHIEVEMENT_TABS: TabDef<AchievementTab>[] = [
  { value: 'practice', label: '실천', icon: CalendarCheck },
  { value: 'plan', label: '계획', icon: ListChecks },
  { value: 'reflections', label: '회고', icon: Library },
]

function AchievementGoalBody({ goal }: { goal: GoalCategory }) {
  const [tab, setTab] = useGoalTab(ACHIEVEMENT_TABS)
  const [checkInOpen, setCheckInOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  const canAiEdit = !goal.temporary && goal.goalCategoryStatus !== 'COMPLETED'
  const gid = goal.goalCategoryId
  const openAi = (entry: 'fab' | 'prefill') => {
    track('goal_detail.ai_edit_open', { goal_category_id: gid, entry_point: entry, tab })
    setAiOpen(true)
  }
  return (
    <>
      {/* 요약 (진행 링 · 경과) — 탭 위에 두어 어느 탭에서도 보여요. 임시 목표는 기간·수치가 없어 빼요 */}
      <div id="goal-summary">
        {!goal.temporary && (
          <div className="mt-5">
            <Hero goal={goal} />
          </div>
        )}
      </div>
      {/* 이 목표를 시작한 이유 — 요약 카드 바로 아래 (명세 13.3) */}
      {(goal.motive || goal.aiNote) && (
        <div className="mt-4">
          <GoalNotes goal={goal} />
        </div>
      )}

      {/* 오늘 기록(수치 · 기분 · 한 줄 회고) — 카드 대신 떠 있는 버튼으로 (명세 12.7 · 12.9) */}
      {goal.goalCategoryStatus !== 'COMPLETED' && (
        <>
          <GoalFabs>
            {/* AI 로 계획 고치기 — "오늘 기록" 위 (예전엔 개요 탭 계획 위 입력 바) */}
            {canAiEdit && (
              <GoalFab label="AI로 계획 고치기" tone="ai" icon={<Sparkles />} onClick={() => openAi('fab')} />
            )}
            <GoalFab label="오늘 기록" icon={<NotebookPen />} onClick={() => setCheckInOpen(true)} />
          </GoalFabs>
          <Modal open={checkInOpen} onClose={() => setCheckInOpen(false)} size="md">
            {/* 시트 안에서는 카드 테두리 · 여백 없이 (명세 13.2) */}
            <div className="[&>section]:border-0 [&>section]:bg-transparent [&>section]:p-0">
              <DailyCheckInCard goal={goal} />
            </div>
          </Modal>
        </>
      )}

      {canAiEdit && (
        <GoalAiEditBar
          goal={goal}
          asSheet
          open={aiOpen}
          onOpenChange={(next, how) => {
            if (next) {
              if (how === 'prefill') {
                setCheckInOpen(false) // "오늘 기록" 창의 "계획 조정받기"에서 왔으면 그 창은 닫고 AI 창으로
                openAi('prefill')
              }
              return
            }
            if (how !== 'applied') track('goal_detail.ai_edit_close', { goal_category_id: gid, close_method: how })
            setAiOpen(false)
          }}
        />
      )}

      <GoalTabBar tabs={ACHIEVEMENT_TABS} tab={tab} onChange={(t) => setTab(t, goal)} />
      {tab === 'practice' ? (
        <div id="goal-panel-practice" role="tabpanel" aria-labelledby="goal-tab-practice">
          <AchievementPractice goal={goal} />
        </div>
      ) : tab === 'plan' ? (
        <div id="goal-panel-plan" role="tabpanel" aria-labelledby="goal-tab-plan">
          <AchievementPlan goal={goal} onAiEdit={canAiEdit ? () => openAi('fab') : undefined} />
        </div>
      ) : (
        <ReflectionsPanel
          goal={goal}
          hint="오른쪽 아래 '오늘 기록'이나 홈의 '오늘 회고'에서 수치 · 기분 · 한 줄 회고를 남기면 여기 모여요."
        />
      )}
    </>
  )
}

/** 달성형 "실천" 탭 — 주간 점검 제안 · 진행 (예전 개요의 위쪽) */
function AchievementPractice({ goal }: { goal: GoalCategory }) {
  const completed = goal.goalCategoryStatus === 'COMPLETED'
  const ongoing = !goal.temporary && goal.goalCategoryStatus === 'IN_PROGRESS'
  return (
    <>
      {/* 주간 점검: 2주 연속 너무 힘들거나 너무 쉬우면 루틴 하루 빼기/더하기 제안 */}
      {ongoing && (
        <div className="mt-5 empty:hidden">
          <GoalAdjustmentBanner goal={goal} />
        </div>
      )}

      {/* ── 진행 (명세 13.5 · 13.7): 칩으로 고르는 그래프 · 결과 · 추가 그래프 ── */}
      <section className="mt-6 space-y-5" aria-labelledby="practice-progress">
        <h2 id="practice-progress" className="text-h3">
          진행
        </h2>
        {completed && !goal.temporary && <GoalOutcomeSection goal={goal} />}
        {/* 그래프 여러 개 (V52, "함께 보는 그래프") — "지금 어디쯤?" 칩을 골랐을 때만 그 아래에 (2026-10-24) */}
        <ProgressOverview
          goal={goal}
          positionExtra={!goal.temporary ? <ExtraMetricsSection goal={goal} readOnly={completed} /> : null}
        />
        {goal.achievement && (
          <Section title="달성 기록">
            <p className="text-[15px] leading-relaxed whitespace-pre-wrap">{goal.achievement}</p>
          </Section>
        )}
      </section>
    </>
  )
}

/** 달성형 "계획" 탭 — 마일스톤 → 루틴 설정 → 관련 Task (AI 로 고치기는 떠 있는 AI 버튼) */
function AchievementPlan({ goal, onAiEdit }: { goal: GoalCategory; onAiEdit?: () => void }) {
  const hasMilestones = (goal.milestones ?? []).length > 0
  return (
    <section className="mt-6 space-y-5" aria-labelledby="plan-title">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h2 id="plan-title" className="text-h3">
          계획
        </h2>
        {onAiEdit && (
          <button
            type="button"
            onClick={onAiEdit}
            className="text-text-brand inline-flex items-center gap-1 text-[13px] font-semibold hover:underline"
          >
            <Sparkles className="size-3.5" />
            AI로 고치기
          </button>
        )}
      </div>
      {hasMilestones && <MilestonesSection goal={goal} />}
      <RoutinesSection goal={goal} />
      <GoalTaskList goal={goal} />
    </section>
  )
}
