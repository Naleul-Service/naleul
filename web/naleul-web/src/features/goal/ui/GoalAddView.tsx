'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { BriefcaseBusiness, MessageCircle, PenLine } from 'lucide-react'
import { ArrowRight, ListChecks, Target } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useUserColors } from '@/features/color/api'
import { addDays, todayKst } from '@/features/timetable/time'
import { useCreateGoal } from '../edit/mutations'
import {
  ColorSwatches,
  Field,
  InlineForm,
  colorIdOf,
  inlineInput,
  toNum,
  type InlineCloseMethod,
  type InlineSubmitMethod,
} from '../edit/inline'
import { isKindComplete, kindBody, type GoalKindValue } from '../kind'
import { periodProgress } from '../format'
import { RecordGoalForm } from '@/features/record/ui/RecordGoalForm'
import { useHabitHome } from '@/features/habit/api'
import { GoalKindPicker } from './GoalKindPicker'

/** 직접 목표 만들기 폼 — Enter 로 만들기, Esc 로 닫기 */
function ManualGoalForm({ onCancel }: { onCancel: () => void }) {
  const router = useRouter()
  const colors = useUserColors()
  const create = useCreateGoal()
  const today = todayKst()

  const [f, setF] = useState({
    emoji: '',
    name: '',
    start: today,
    end: addDays(today, 90),
    colorId: null as number | null,
    motive: '',
    metricName: '',
    metricUnit: '',
    startValue: '',
    targetValue: '',
  })
  const [showMetric, setShowMetric] = useState(false)
  const [kind, setKind] = useState<GoalKindValue | null>(null)
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((x) => ({ ...x, [k]: v }))

  const colorId = f.colorId ?? colorIdOf(colors.data, null)
  const nums = [toNum(f.startValue), toNum(f.targetValue)]
  const error = !f.name.trim()
    ? null
    : !f.start || !f.end
      ? '기간을 입력해 주세요.'
      : f.start > f.end
        ? '종료일이 시작일보다 빨라요.'
        : nums.some((n) => n !== null && Number.isNaN(n))
          ? '수치는 숫자로 입력해 주세요.'
          : !isKindComplete(kind)
            ? '카테고리를 골라 주세요. (기타는 이름 1~10자)'
            : null
  const valid = !!f.name.trim() && !error && colorId !== null

  // ── 분석: 폼 아래 빨간 문구가 새로 뜰 때마다 1회 ──
  const lastError = useRef<string | null>(null)
  useEffect(() => {
    if (error === lastError.current) return
    lastError.current = error
    if (error) track('goal_add.manual_error', { validation_error: error })
  }, [error])

  const hasInput =
    !!f.name.trim() ||
    !!f.emoji.trim() ||
    !!f.motive.trim() ||
    !!f.metricName.trim() ||
    !!f.metricUnit.trim() ||
    !!f.startValue.trim() ||
    !!f.targetValue.trim() ||
    kind !== null ||
    f.colorId !== null ||
    f.start !== today ||
    f.end !== addDays(today, 90)

  const cancel = (method: InlineCloseMethod) => {
    track('goal_add.manual_cancel', { close_method: method, has_input: hasInput })
    onCancel()
  }

  const submit = (method: InlineSubmitMethod) => {
    const hasMetric = showMetric && !!f.metricName.trim()
    track('goal_add.manual_submit', {
      goal_type: kind?.goalType,
      goal_sub_type: kind?.goalSubType,
      period_days: periodProgress(f.start, f.end)?.total,
      has_metric: hasMetric,
      metric_dropped: showMetric && !hasMetric && (!!f.startValue.trim() || !!f.targetValue.trim()),
      has_motive: !!f.motive.trim(),
      has_emoji: !!f.emoji.trim(),
      color_changed: f.colorId !== null && f.colorId !== colorIdOf(colors.data, null),
      submit_method: method,
    })
    create.mutate(
      {
        goalCategoryName: f.name.trim(),
        goalCategoryStartDate: f.start,
        goalCategoryEndDate: f.end,
        colorId: colorId!,
        motive: f.motive.trim() || undefined,
        emoji: f.emoji.trim() || undefined,
        ...kindBody(kind),
        ...(showMetric && f.metricName.trim()
          ? {
              metricName: f.metricName.trim(),
              metricUnit: f.metricUnit.trim(),
              startValue: nums[0],
              targetValue: nums[1],
            }
          : {}),
        // V52: 세부 목표는 화면에서 숨겨서 직접 만들 때도 받지 않아요
        subGoals: [],
      },
      {
        onSuccess: ({ goal }) => {
          track('goal_add.create_result', {
            goal_mode: 'ACHIEVEMENT',
            result: 'success',
            goal_category_id: goal.goalCategoryId,
          })
          router.push(`/goal/${goal.goalCategoryId}`)
        },
        onError: (e) =>
          track('goal_add.create_result', { goal_mode: 'ACHIEVEMENT', result: 'fail', error_status: errorStatusOf(e) }),
      }
    )
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={cancel}
      saving={create.isPending}
      valid={valid}
      error={error}
      submitLabel="목표 만들기"
      className="mt-4"
    >
      <div className="grid grid-cols-[64px_minmax(0,1fr)] gap-2">
        <Field label="이모지">
          <input
            value={f.emoji}
            onChange={(e) => set('emoji', e.target.value)}
            maxLength={16}
            placeholder="🎯"
            className={`${inlineInput} text-center placeholder:opacity-40`}
          />
        </Field>
        <Field label="목표 이름">
          <input
            value={f.name}
            onChange={(e) => set('name', e.target.value)}
            maxLength={50}
            placeholder="예: 토익 900점 달성"
            className={inlineInput}
            data-autofocus
          />
        </Field>
      </div>
      <div>
        <span className="text-ink-3 mb-1 block text-[12px] font-medium">카테고리</span>
        <GoalKindPicker value={kind} onChange={setKind} compact />
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Field label="시작일">
          <input type="date" value={f.start} onChange={(e) => set('start', e.target.value)} className={inlineInput} />
        </Field>
        <Field label="종료일">
          <input type="date" value={f.end} onChange={(e) => set('end', e.target.value)} className={inlineInput} />
        </Field>
      </div>
      <Field label="색상">
        <ColorSwatches colors={colors.data} value={colorId} onChange={(id) => set('colorId', id)} />
      </Field>
      <Field label="이 목표를 시작한 이유 (선택)">
        <textarea
          value={f.motive}
          onChange={(e) => set('motive', e.target.value)}
          rows={2}
          maxLength={500}
          placeholder="Shift+Enter 로 줄바꿈"
          className={`${inlineInput} h-auto resize-none py-2 leading-relaxed`}
        />
      </Field>

      {showMetric ? (
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <Field label="수치 이름">
            <input
              value={f.metricName}
              onChange={(e) => set('metricName', e.target.value)}
              maxLength={30}
              placeholder="체중"
              className={inlineInput}
            />
          </Field>
          <Field label="단위">
            <input
              value={f.metricUnit}
              onChange={(e) => set('metricUnit', e.target.value)}
              maxLength={10}
              placeholder="kg"
              className={inlineInput}
            />
          </Field>
          <Field label="시작">
            <input
              inputMode="decimal"
              value={f.startValue}
              onChange={(e) => set('startValue', e.target.value)}
              placeholder="80"
              className={inlineInput}
            />
          </Field>
          <Field label="목표">
            <input
              inputMode="decimal"
              value={f.targetValue}
              onChange={(e) => set('targetValue', e.target.value)}
              placeholder="72"
              className={inlineInput}
            />
          </Field>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => {
            track('goal_add.manual_metric_add_click')
            setShowMetric(true)
          }}
          className="text-text-brand self-start text-[13px] font-semibold"
        >
          + 수치 목표 추가 (예: 체중 80 → 72kg)
        </button>
      )}
      <p className="text-ink-3 text-xs">만든 뒤 목표 상세에서 루틴·Task와 그래프를 바로 추가할 수 있어요.</p>
    </InlineForm>
  )
}

const HABIT_EXAMPLES = ['영양제 먹기', '물 2L 마시기', '샤워하기', '집안일 하기', '금연 · 금주', '일기 쓰기']
const WORK_EXAMPLES = ['취업 준비', 'OO팀 업무', '동아리 활동', '사이드 프로젝트']
const GOAL_EXAMPLES = ['3개월에 5kg 감량', 'SQLD 합격', '토익 900점', '1000만원 모으기', '올해 책 24권']

function Examples({ items }: { items: string[] }) {
  return (
    <div className="mt-4">
      <p className="text-ink-3 mb-1.5 text-[12px] font-medium">이런 걸 담아요</p>
      <div className="flex flex-wrap gap-1.5">
        {items.map((x) => (
          <span key={x} className="bg-subtle text-ink-2 rounded-full px-2.5 py-1 text-[12px]">
            {x}
          </span>
        ))}
      </div>
    </div>
  )
}

/** 기본 생활 습관은 이미 하나씩 있어요 — 만들기 대신 습관 화면으로 보내요 */
function HabitCardAction() {
  const { data, isPending } = useHabitHome()
  if (isPending || !data) return <span className="bg-subtle block h-5 w-32 animate-pulse rounded" />
  return (
    <Link
      href={`/goal/${data.goalCategoryId}`}
      onClick={() => track('goal_add.habit_click', { habit_count: data.habits.length })}
      className="text-success group flex items-center gap-1 text-sm font-semibold"
    >
      {data.habits.length ? `습관 ${data.habits.length}개 보러 가기` : '습관 추가하러 가기'}
      <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
    </Link>
  )
}

/**
 * /goal/add — 목표는 성격에 따라 세 가지 (V52)
 *  - 기본 생활 습관: 했는지만 체크하는 루틴. 모든 사용자에게 하나씩 이미 있어서 습관만 추가해요 (goal_mode = HABIT)
 *  - 업무형: 회사 업무 · 취업 준비 · 동아리 등. 주제마다 마감을 둔 로드맵 + Task 보드 (goal_mode = RECORD)
 *  - 목표형: 수치로 잴 수 있는 목표. AI 가 대화로 "어떻게 이룰지"를 묻고 기간별 루틴까지 계획 (/goal/new), 계획이 있으면 직접 입력
 */
export function GoalAddView({ initialMode = null }: { initialMode?: 'manual' | 'record' | null }) {
  const [open, setOpen] = useState<'manual' | 'record' | null>(initialMode)
  const formOpen = open ?? 'none'

  // ── 분석 (노션 Tracking Plan 「목표 추가」) ──
  const pageviewSent = useRef(false)
  useEffect(() => {
    if (pageviewSent.current) return
    pageviewSent.current = true
    track('goal_add.pageview', { initial_mode: initialMode ?? 'none' })
  }, [initialMode])

  /** 직접 만들기 폼 열기 — 다른 폼이 열려 있었으면 바뀌어요 (적던 내용 사라짐) */
  const openForm = (mode: 'manual' | 'record') => {
    if (open === mode) return
    track('goal_add.mode_open', { goal_mode: mode === 'record' ? 'RECORD' : 'ACHIEVEMENT', from_mode: formOpen })
    setOpen(mode)
  }

  const card = 'border-line bg-surface flex flex-col rounded-card border p-5 text-left transition-colors sm:p-6'

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link href="/goal" onClick={() => track('goal_add.breadcrumb_click', { form_open: formOpen })}>
            목표
          </Link>
        }
        title="목표 추가"
        description="어떤 성격의 목표인가요?"
      />

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        {/* 기본 생활 습관 */}
        <section className={card} aria-labelledby="habit-title">
          <span className="bg-success-soft text-success grid size-11 place-items-center rounded-2xl">
            <ListChecks className="size-5" />
          </span>
          <p id="habit-title" className="mt-4 flex flex-wrap items-center gap-2 text-[18px] font-bold">
            기본 생활 습관 <Badge tone="success">체크만</Badge>
          </p>
          <p className="text-ink-3 mt-1.5 text-sm leading-relaxed">
            매일 챙겨야 하는데 잊기 쉬운 것들이에요. 마감 없이 했는지만 체크하고, 잔디로 쌓여요. 필요 없으면 쉬거나
            지우면 돼요.
          </p>
          <Examples items={HABIT_EXAMPLES} />
          <div className="mt-auto pt-5">
            <HabitCardAction />
          </div>
        </section>

        {/* 업무형 */}
        <section className={cn(card, open === 'record' && 'border-action-outline-border ring-action-outline-border ring-1')} aria-labelledby="work-title">
          <span className="bg-action-secondary-bg text-text-brand grid size-11 place-items-center rounded-[12px]">
            <BriefcaseBusiness className="size-5" />
          </span>
          <p id="work-title" className="mt-4 flex flex-wrap items-center gap-2 text-[18px] font-bold">
            업무형 <Badge tone="neutral">Task 관리</Badge>
          </p>
          <p className="text-ink-3 mt-1.5 text-sm leading-relaxed">
            수치보다 해야 할 일이 많은 일이에요. 취업 준비 · 동아리 · 회사 업무처럼 단계(세부 목표)마다 마감을 정하고 남은
            일을 체크해요.
          </p>
          <Examples items={WORK_EXAMPLES} />
          <div className="mt-auto pt-5">
            <button
              type="button"
              onClick={() => openForm('record')}
              aria-expanded={open === 'record'}
              className="text-ink group flex items-center gap-1 text-sm font-semibold"
            >
              {open === 'record' ? '아래에서 정해 주세요' : '업무 목표 만들기'}
              <ArrowRight
                className={cn(
                  'size-4 transition-transform',
                  open === 'record' ? 'rotate-90' : 'group-hover:translate-x-0.5'
                )}
              />
            </button>
          </div>
        </section>

        {/* 목표형 */}
        <section className={cn(card, open === 'manual' && 'border-action-outline-border ring-action-outline-border ring-1')} aria-labelledby="goal-title">
          <span className="bg-brand-soft text-text-brand grid size-11 place-items-center rounded-2xl">
            <Target className="size-5" />
          </span>
          <p id="goal-title" className="mt-4 flex flex-wrap items-center gap-2 text-[18px] font-bold">
            목표형 <Badge tone="brand">수치 · 기한</Badge>
          </p>
          <p className="text-ink-3 mt-1.5 text-sm leading-relaxed">
            숫자로 잴 수 있는 목표예요. AI가 지금 상황과 계획을 물어본 뒤, 오늘 어디까지 하면 되는지 그래프와 루틴으로
            짜 드려요.
          </p>
          <Examples items={GOAL_EXAMPLES} />
          <div className="mt-auto flex flex-wrap items-center gap-x-5 gap-y-2 pt-5">
            <Link
              href="/goal/new"
              onClick={() => track('goal_add.ai_click', { form_open: formOpen })}
              className="text-text-brand group flex items-center gap-1 text-sm font-semibold"
            >
              <MessageCircle className="size-4" />
              AI와 계획 세우기
              <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
            <button
              type="button"
              onClick={() => openForm('manual')}
              aria-expanded={open === 'manual'}
              className="text-ink-3 hover:text-ink-2 flex items-center gap-1 text-[13px] font-medium"
            >
              <PenLine className="size-3.5" />
              {open === 'manual' ? '아래에서 입력해 주세요' : '직접 입력하기'}
            </button>
          </div>
        </section>
      </div>

      {open === 'record' && <RecordGoalForm onCancel={() => setOpen(null)} className="mt-4 max-w-2xl" />}
      {open === 'manual' && <ManualGoalForm onCancel={() => setOpen(null)} />}
    </>
  )
}
