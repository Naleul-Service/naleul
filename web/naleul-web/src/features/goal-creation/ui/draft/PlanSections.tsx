'use client'

import type { ReactNode } from 'react'
import { ArrowRight, Pencil, Plus } from '@/components/icons'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import type { UserColor } from '@/features/color/api'
import { DAY_LABEL, DAYS } from '../../constants'
import type { GoalPlan, PlanTask } from '../../types'
import type { EditTarget } from './ItemEditModal'
import { LIMITS, dDay, daysBetween, formatMd, formatYmdDot, isExistingTask, isRoutine } from './planUtils'

type Issues = Map<string, string[]>

/** 이 항목을 지금 편집 중이면 그 자리에 놓을 편집기, 아니면 null */
export type EditorSlot = (t: EditTarget) => ReactNode | null

// ─── 공통 ───────────────────────────────────────────────────────

/** 검증 실패 표시 틀: data-vkey 로 스크롤 대상이 되고, 문제가 있으면 빨간 테두리 + 메시지 */
export function IssueFrame({
  vkey,
  issues,
  className,
  children,
}: {
  vkey: string
  issues: Issues
  className?: string
  children: ReactNode
}) {
  const messages = issues.get(vkey)
  return (
    <div
      data-vkey={vkey}
      className={cn(
        'scroll-mt-28 rounded-2xl transition-shadow',
        messages && 'ring-danger bg-danger-soft/40 ring-2',
        className
      )}
    >
      {children}
      {messages && (
        <ul className="text-danger px-4 pb-3 text-[13px] font-medium">
          {messages.map((m) => (
            <li key={m}>· {m}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function PanelCard({
  title,
  aside,
  vkey,
  issues,
  children,
  footer,
}: {
  title: string
  aside?: ReactNode
  vkey: string
  issues: Issues
  children: ReactNode
  footer?: ReactNode
}) {
  const messages = issues.get(vkey)
  return (
    <section
      data-vkey={vkey}
      className={cn(
        'border-line bg-surface rounded-card scroll-mt-28 border p-5 sm:p-6',
        messages && 'ring-danger ring-2'
      )}
    >
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-h4">{title}</h2>
        {aside && <span className="text-ink-3 text-[13px]">{aside}</span>}
      </div>
      {messages && (
        <ul className="text-danger mb-3 text-[13px] font-medium">
          {messages.map((m) => (
            <li key={m}>· {m}</li>
          ))}
        </ul>
      )}
      {children}
      {footer}
    </section>
  )
}

function AddButton({ label, onClick, disabled }: { label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="border-border-subtle text-ink-2 hover:border-line-strong hover:text-ink mt-3 flex h-11 w-full items-center justify-center gap-1.5 rounded-xl border border-dashed text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-40"
    >
      <Plus className="size-4" />
      {label}
    </button>
  )
}

function EmojiBox({ emoji, tone = 'bg-bg-sunken' }: { emoji: string | null; tone?: string }) {
  return (
    <span className={cn('grid size-10 shrink-0 place-items-center rounded-xl text-lg', tone)} aria-hidden>
      {emoji || '•'}
    </span>
  )
}

// ─── 목표 카드 (왼쪽 큰 카드) ───────────────────────────────────

export function GoalHeroCard({
  plan,
  issues,
  onEdit,
  styleChip,
  colors,
  colorId,
  onColor,
  editor,
  showMilestones = true,
}: {
  plan: GoalPlan
  issues: Issues
  onEdit: (t: EditTarget) => void
  editor: EditorSlot
  styleChip: ReactNode
  colors: UserColor[] | undefined
  colorId: number | null
  onColor: (id: number | null) => void
  /** false 면 마일스톤은 화면 아래 "자세히" 안에서 따로 보여줘요 */
  showMilestones?: boolean
}) {
  const { goal } = plan
  const days = daysBetween(goal.startDate, goal.endDate)
  const weeks = Math.max(Math.ceil(days / 7), 1)
  const perWeek =
    goal.metric && Number.isFinite(goal.metric.targetValue - goal.metric.startValue)
      ? (goal.metric.targetValue - goal.metric.startValue) / weeks
      : null

  return (
    <section className="border-line bg-surface rounded-card border p-5 sm:p-7">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="brand">✦ AI가 설계한 목표</Badge>
        {styleChip}
      </div>

      <IssueFrame vkey="goal" issues={issues} className="-mx-2 mt-3">
        {editor({ kind: 'goal' }) ?? (
          <button
            type="button"
            onClick={() => onEdit({ kind: 'goal' })}
            className="group hover:bg-bg-sunken w-full rounded-2xl px-2 py-1 text-left"
          >
            <h2 className="text-h2 sm:text-h1 flex items-center gap-2">
              {goal.emoji && <span>{goal.emoji}</span>}
              {goal.title}
              <Pencil className="text-ink-4 group-hover:text-ink-2 size-4 shrink-0" />
            </h2>
            <p className="text-ink-3 mt-1.5 text-sm">
              {formatYmdDot(goal.startDate)} – {formatYmdDot(goal.endDate)} · {weeks}주
              {perWeek !== null && goal.metric && (
                <>
                  {' '}
                  · 주 평균 {perWeek > 0 ? '+' : ''}
                  {perWeek.toFixed(1)}
                  {goal.metric.unit}
                </>
              )}
            </p>
          </button>
        )}
      </IssueFrame>

      {goal.aiNote && (
        <p className="bg-bg-sunken mt-4 rounded-2xl px-4 py-3.5 text-[14px] leading-relaxed">💡 {goal.aiNote}</p>
      )}

      {/* 목표 색상 */}
      <div className="mt-5">
        <p className="text-ink-3 mb-2 text-[13px] font-medium">목표 색상</p>
        <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label="목표 색상">
          <button
            type="button"
            role="radio"
            aria-checked={colorId === null}
            onClick={() => onColor(null)}
            className={cn(
              'h-8 rounded-full border px-3 text-xs font-semibold',
              colorId === null ? 'border-text-brand bg-ai-tint-soft text-text-brand' : 'border-border-subtle text-ink-2'
            )}
          >
            자동
          </button>
          {colors?.map((c) => (
            <button
              key={c.userColorId}
              type="button"
              role="radio"
              aria-checked={colorId === c.userColorId}
              aria-label={`색상 ${c.hex}`}
              onClick={() => onColor(c.userColorId)}
              className={cn(
                'size-8 rounded-full ring-offset-2 transition-shadow',
                colorId === c.userColorId ? 'ring-ink ring-2' : 'hover:ring-line-strong hover:ring-2'
              )}
              style={{ backgroundColor: c.hex }}
            />
          ))}
        </div>
      </div>

      {showMilestones && <MilestoneTimeline plan={plan} issues={issues} onEdit={onEdit} editor={editor} />}
    </section>
  )
}

export function MilestoneTimeline({
  plan,
  issues,
  onEdit,
  editor,
}: {
  plan: GoalPlan
  issues: Issues
  onEdit: (t: EditTarget) => void
  editor: EditorSlot
}) {
  // 타임라인 칸은 좁아서, 편집기는 타임라인 아래 전체 폭으로 열어요
  const editingIndex = editingMilestoneIndex(plan, editor)
  const { milestones, goal } = plan
  const unit = goal.metric?.unit ?? ''
  const messages = issues.get('milestones')

  return (
    <div data-vkey="milestones" className="mt-7 scroll-mt-28">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-h4">마일스톤</h3>
        <span className="text-ink-3 text-[13px]">
          {milestones.length}단계{goal.metric ? ' · 수치는 자동 계산' : ''}
        </span>
      </div>
      {messages && (
        <ul className="text-danger mb-3 text-[13px] font-medium">
          {messages.map((m) => (
            <li key={m}>· {m}</li>
          ))}
        </ul>
      )}
      <div className="-mx-2 overflow-x-auto pb-2">
        <ol className="flex gap-1 px-2">
          {milestones.map((ms, i) => {
            const last = i === milestones.length - 1
            const days = daysBetween(goal.startDate, ms.dueDate)
            // 한 달 안쪽은 주 단위로 (2주 목표에 "1개월"이라고 나오지 않게)
            const when = days < 28 ? `${Math.max(Math.round(days / 7), 1)}주` : `${Math.round(days / 30)}개월`
            return (
              <li key={ms.tempId} className="min-w-[190px] flex-1">
                <div className="flex items-center" aria-hidden>
                  <span
                    className={cn(
                      'size-5 shrink-0 rounded-full border-[3px]',
                      i === 0
                        ? 'border-brand bg-brand-soft'
                        : last
                          ? 'border-success bg-success'
                          : 'border-border-subtle bg-surface'
                    )}
                  />
                  {!last && <span className="bg-line-strong h-0.5 flex-1" />}
                </div>
                <IssueFrame vkey={`milestones[${i}]`} issues={issues} className="mt-2 mr-3">
                  <button
                    type="button"
                    onClick={() => onEdit({ kind: 'milestone', index: i })}
                    className={cn(
                      'group hover:bg-bg-sunken w-full rounded-2xl p-2 text-left',
                      editingIndex === i && 'bg-brand-soft ring-brand/40 ring-1'
                    )}
                  >
                    <p className="text-ink-3 flex items-center gap-1 text-xs">
                      {when} · ~{formatMd(ms.dueDate, false)}
                      <Pencil className="text-ink-4 group-hover:text-ink-2 ml-auto size-3" />
                    </p>
                    <p className="mt-1 flex flex-wrap items-center gap-1.5 text-[15px] font-bold">
                      {ms.title}
                      {ms.targetValue !== null && (
                        <Badge tone={last ? 'success' : 'brand'} className="h-5 px-1.5 text-[11px]">
                          {ms.targetValue}
                          {unit}
                        </Badge>
                      )}
                    </p>
                    {ms.description && <p className="text-ink-3 mt-1 line-clamp-2 text-[13px]">{ms.description}</p>}
                  </button>
                </IssueFrame>
              </li>
            )
          })}
        </ol>
      </div>
      {editingIndex !== null && editingIndex >= 0 && (
        <div className="mt-3">{editor({ kind: 'milestone', index: editingIndex })}</div>
      )}
      {editor({ kind: 'milestone', index: null }) ?? (
        <AddButton
          label="마일스톤 추가"
          onClick={() => onEdit({ kind: 'milestone', index: null })}
          disabled={milestones.length >= LIMITS.milestones[1]}
        />
      )}
    </div>
  )
}

/** 지금 편집 중인 마일스톤 index (없으면 null) */
function editingMilestoneIndex(plan: GoalPlan, editor: EditorSlot): number | null {
  const i = plan.milestones.findIndex((_, idx) => editor({ kind: 'milestone', index: idx }) !== null)
  return i >= 0 ? i : null
}

// ─── 수치 카드 (오른쪽 파란 카드) ───────────────────────────────

function PlanCurve({ plan }: { plan: GoalPlan }) {
  const metric = plan.goal.metric
  if (!metric) return null
  const points = [
    { date: plan.goal.startDate, value: metric.startValue },
    ...plan.milestones.filter((m) => m.targetValue !== null).map((m) => ({ date: m.dueDate, value: m.targetValue! })),
  ]
  if (points.length < 2) return null

  const W = 300
  const H = 110
  const PAD = 14
  const total = Math.max(daysBetween(plan.goal.startDate, plan.goal.endDate), 1)
  const values = points.map((p) => p.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const x = (d: string) =>
    PAD + (Math.min(Math.max(daysBetween(plan.goal.startDate, d), 0), total) / total) * (W - PAD * 2)
  const y = (v: number) => PAD + 12 + ((max - v) / span) * (H - PAD * 2 - 12)
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.date).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="mt-2 w-full" role="img" aria-label="계획 곡선">
      <path d={path} fill="none" className="stroke-text-brand" strokeWidth="2" strokeDasharray="5 4" />
      {points.map((p, i) => (
        <g key={`${p.date}-${i}`}>
          <circle cx={x(p.date)} cy={y(p.value)} r={i === points.length - 1 ? 5 : 4} className="fill-text-brand" />
          {i > 0 && (
            <text
              x={x(p.date)}
              y={y(p.value) - 9}
              textAnchor="middle"
              fontSize="11"
              fontWeight="700"
              className="fill-text-primary"
            >
              {p.value}
            </text>
          )}
        </g>
      ))}
    </svg>
  )
}

export function MetricCard({ plan, onEditGoal }: { plan: GoalPlan; onEditGoal?: () => void }) {
  const { goal } = plan
  const routines = plan.tasks.filter(isRoutine)
  const oneTimes = plan.tasks.length - routines.length
  const weeklyCount = routines.reduce((n, t) => n + (t.routineDays?.length ?? 0), 0)
  const remain = dDay(goal.endDate)

  // 기간별로 나눈 루틴(이론 → 기출)이 있으면 단계 수를 보여줘요 (V52: 마일스톤 대신)
  const phases = new Set(routines.map((t) => `${t.startDate ?? ''}~${t.endDate ?? ''}`)).size
  const stats = [
    ['루틴', `${routines.length}개`],
    ['주간 실천', `주 ${weeklyCount}회`],
    ['일회성 Task', `${oneTimes}개`],
    ...(phases > 1
      ? ([['단계', `${phases}단계`]] as const)
      : plan.milestones.length
        ? ([['마일스톤', `${plan.milestones.length}단계`]] as const)
        : []),
  ] as const

  return (
    // AI 가 설계한 계획 요약 — 밝은 AI 카드 (ai-tint-soft + 그라데이션 테두리)
    <section className="ai-border ai-card text-text-primary rounded-card p-6 sm:p-7">
      {goal.metric ? (
        <>
          <div className="text-text-secondary flex items-end justify-between text-[13px]">
            <span>현재</span>
            <span>목표</span>
          </div>
          <div className="mt-1 flex items-center justify-between gap-2">
            <span className="text-[32px] font-bold tracking-tight">
              {goal.metric.startValue}
              <span className="text-xl">{goal.metric.unit}</span>
            </span>
            <ArrowRight className="text-text-tertiary size-6" />
            <span className="text-[32px] font-bold tracking-tight">
              {goal.metric.targetValue}
              <span className="text-xl">{goal.metric.unit}</span>
            </span>
          </div>
        </>
      ) : (
        <>
          <p className="text-text-secondary text-[13px]">기간</p>
          <p className="mt-1 text-2xl font-bold">
            {formatMd(goal.startDate, false)} → {formatMd(goal.endDate, false)}
          </p>
        </>
      )}

      <div className="bg-bg-surface/70 mt-4 h-1.5 rounded-full">
        <div className="bg-ai-gradient h-full w-[3%] rounded-full" />
      </div>
      <div className="text-text-secondary mt-2 flex justify-between text-[13px]">
        <span>시작 {formatMd(goal.startDate, false)}</span>
        <span>{remain >= 0 ? `D-${remain}` : '종료'}</span>
      </div>

      {goal.metric && (
        <>
          <p className="text-text-secondary mt-5 text-[13px]">계획 {goal.metric.name} 곡선</p>
          <PlanCurve plan={plan} />
        </>
      )}
      {onEditGoal && (
        <button
          type="button"
          onClick={onEditGoal}
          className="bg-bg-surface/70 text-text-secondary hover:bg-bg-surface mt-3 inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-[13px] font-semibold"
        >
          <Pencil className="size-3.5" />
          {goal.metric ? '시작값·목표값·종료일 고치기' : '종료일 고치기'}
        </button>
      )}

      <dl className="mt-5 grid grid-cols-2 gap-2.5">
        {stats.map(([label, value]) => (
          <div key={label} className="bg-bg-surface rounded-xl px-4 py-3">
            <dt className="text-caption-md text-text-secondary">{label}</dt>
            <dd className="mt-0.5 text-xl font-bold">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

// ─── 세부 목표 ──────────────────────────────────────────────────

const SUBGOAL_TONES = ['bg-warning-soft', 'bg-brand-soft', 'bg-success-soft', 'bg-danger-soft']

export function SubGoalsCard({
  plan,
  issues,
  onEdit,
  editor,
}: {
  plan: GoalPlan
  issues: Issues
  onEdit: (t: EditTarget) => void
  editor: EditorSlot
}) {
  return (
    <PanelCard title="세부 목표" aside={`${plan.subGoals.length}개`} vkey="subGoals" issues={issues}>
      <div className="space-y-2.5">
        {plan.subGoals.map((sg, i) => (
          <IssueFrame key={sg.tempId} vkey={`subGoals[${i}]`} issues={issues}>
            {editor({ kind: 'subGoal', index: i }) ?? (
              <button
                type="button"
                onClick={() => onEdit({ kind: 'subGoal', index: i })}
                className="border-line hover:border-line-strong flex w-full items-start gap-3 rounded-2xl border p-4 text-left transition-colors"
              >
                <EmojiBox emoji={sg.emoji} tone={SUBGOAL_TONES[i % SUBGOAL_TONES.length]} />
                <span className="min-w-0 flex-1">
                  <span className="block text-[15px] font-bold">{sg.title}</span>
                  {sg.description && (
                    <span className="text-ink-3 mt-0.5 block text-[13px] leading-relaxed">{sg.description}</span>
                  )}
                </span>
                <Pencil className="text-ink-4 mt-1 size-3.5 shrink-0" />
              </button>
            )}
          </IssueFrame>
        ))}
      </div>
      {editor({ kind: 'subGoal', index: null }) ?? (
        <AddButton
          label="세부 목표 추가"
          onClick={() => onEdit({ kind: 'subGoal', index: null })}
          disabled={plan.subGoals.length >= LIMITS.subGoals[1]}
        />
      )}
    </PanelCard>
  )
}

// ─── 생성될 Task (일회성) ───────────────────────────────────────

export function OneTimeTasksCard({
  plan,
  issues,
  onEdit,
  editor,
}: {
  plan: GoalPlan
  issues: Issues
  onEdit: (t: EditTarget) => void
  editor: EditorSlot
}) {
  // 원래 배열 위치(index)를 유지해야 서버 violations 의 tasks[i] 와 맞아요
  const items = plan.tasks
    .map((task, index) => ({ task, index }))
    .filter(({ task }) => !isRoutine(task))
    .sort((a, b) =>
      (a.task.scheduledDate ?? a.task.dueDate ?? '').localeCompare(b.task.scheduledDate ?? b.task.dueDate ?? '')
    )

  const subGoalName = (t: PlanTask) => plan.subGoals.find((sg) => sg.tempId === t.subGoalTempId)?.title

  return (
    <PanelCard title="생성될 Task" aside={`일회성 · ${items.length}개`} vkey="tasks" issues={issues}>
      {items.length === 0 && (
        <p className="text-ink-3 px-2 py-2 text-[13px] leading-relaxed">
          따로 챙길 일회성 Task는 없어요. 위의 루틴만 꾸준히 하면 돼요.
        </p>
      )}
      <ul className="divide-line divide-y">
        {items.map(({ task, index }) => (
          <li key={task.tempId}>
            <IssueFrame vkey={`tasks[${index}]`} issues={issues} className="my-1">
              {editor({ kind: 'task', index, taskType: 'ONE_TIME' }) ?? (
                <button
                  type="button"
                  onClick={() => onEdit({ kind: 'task', index, taskType: 'ONE_TIME' })}
                  className="hover:bg-bg-sunken flex w-full items-center gap-3 rounded-xl px-2 py-3 text-left"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-semibold">
                      {task.emoji} {task.title}
                    </span>
                    <span className="text-ink-3 mt-0.5 block text-[13px]">
                      {task.scheduledDate ? formatMd(task.scheduledDate) : '날짜 자동'}
                      {task.dueDate && ` · 마감 ${formatMd(task.dueDate, false)}`} · {task.durationMinutes}분
                    </span>
                  </span>
                  {subGoalName(task) && (
                    <Badge tone="neutral" className="max-w-[96px] shrink-0 truncate">
                      {subGoalName(task)}
                    </Badge>
                  )}
                  <Pencil className="text-ink-4 size-3.5 shrink-0" />
                </button>
              )}
            </IssueFrame>
          </li>
        ))}
      </ul>
      {editor({ kind: 'task', index: null, taskType: 'ONE_TIME' }) ?? (
        <AddButton
          label="Task 추가"
          onClick={() => onEdit({ kind: 'task', index: null, taskType: 'ONE_TIME' })}
          disabled={items.length >= LIMITS.oneTimes[1]}
        />
      )}
    </PanelCard>
  )
}

// ─── 필요 루틴 ──────────────────────────────────────────────────

export function RoutinesCard({
  plan,
  issues,
  onEdit,
  editor,
}: {
  plan: GoalPlan
  issues: Issues
  onEdit: (t: EditTarget) => void
  editor: EditorSlot
}) {
  const items = plan.tasks.map((task, index) => ({ task, index })).filter(({ task }) => isRoutine(task))

  /** 목표 기간 전체가 아니면 "10.21부터", "~10.20", "10.7~10.20" */
  const periodOf = (t: PlanTask) => {
    const from = t.startDate && t.startDate > plan.goal.startDate ? t.startDate : null
    const to = t.endDate && t.endDate < plan.goal.endDate ? t.endDate : null
    if (from && to) return `${formatMd(from, false)}~${formatMd(to, false)}`
    if (from) return `${formatMd(from, false)}부터`
    if (to) return `~${formatMd(to, false)}`
    return null
  }

  return (
    <PanelCard title="필요 루틴" aside={`반복 · ${items.length}개`} vkey="routines" issues={issues}>
      <ul className="divide-line divide-y">
        {items.map(({ task, index }) => {
          const period = periodOf(task)
          return (
            <li key={task.tempId}>
              <IssueFrame vkey={`tasks[${index}]`} issues={issues} className="my-1">
                {editor({ kind: 'task', index, taskType: 'ROUTINE' }) ?? (
                  <button
                    type="button"
                    onClick={() => onEdit({ kind: 'task', index, taskType: 'ROUTINE' })}
                    className="group hover:bg-bg-sunken flex w-full gap-3 rounded-xl px-2 py-3 text-left"
                  >
                    <span className="w-6 shrink-0 text-lg" aria-hidden>
                      {task.emoji}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-start gap-2">
                        <span className="min-w-0 flex-1 text-[15px] font-semibold">
                          {task.title}
                          {/* 예전 목표에서 하던 루틴 — 확정하면 새로 만들지 않고 이어서 써요 (빼면 오늘까지만 하고 끝나요) */}
                          {isExistingTask(task) && (
                            <span className="bg-subtle text-ink-2 ml-1.5 inline-block rounded-md px-1.5 py-0.5 align-middle text-[11px] font-semibold">
                              지금 하는 루틴
                            </span>
                          )}
                        </span>
                        <Pencil className="text-ink-4 group-hover:text-ink-2 mt-1 size-3.5 shrink-0" />
                      </span>
                      <span className="text-ink-3 mt-0.5 block text-[13px]">
                        {task.preferredStartTime ?? '시간 자동'} · {task.durationMinutes}분
                        {period && <b className="text-text-brand font-semibold"> · {period}</b>}
                      </span>
                      <span className="mt-2 flex gap-1">
                        {DAYS.map((d) => {
                          const on = task.routineDays?.includes(d)
                          return (
                            <span
                              key={d}
                              className={cn(
                                'grid size-7 place-items-center rounded-lg text-xs font-semibold',
                                on ? 'bg-brand text-white' : 'bg-subtle text-ink-4'
                              )}
                            >
                              {DAY_LABEL[d]}
                            </span>
                          )
                        })}
                      </span>
                      {task.description && (
                        <span className="bg-subtle text-ink-2 mt-2 block rounded-lg px-2.5 py-1.5 text-[13px] leading-relaxed whitespace-pre-wrap">
                          {task.description}
                        </span>
                      )}
                      {task.reason && (
                        <span className="text-ink-3 mt-1.5 block text-[12px] leading-relaxed">💡 {task.reason}</span>
                      )}
                    </span>
                  </button>
                )}
              </IssueFrame>
            </li>
          )
        })}
      </ul>
      {editor({ kind: 'task', index: null, taskType: 'ROUTINE' }) ?? (
        <AddButton
          label="루틴 추가"
          onClick={() => onEdit({ kind: 'task', index: null, taskType: 'ROUTINE' })}
          disabled={items.length >= LIMITS.routines[1]}
        />
      )}
    </PanelCard>
  )
}
