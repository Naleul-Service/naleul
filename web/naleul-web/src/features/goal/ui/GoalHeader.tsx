'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Pencil, Trash2 } from '@/components/icons'
import { Badge } from '@/components/ui/Chip'
import { Card } from '@/components/ui/Card'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useUserColors } from '@/features/color/api'
import type { GoalCategory } from '../api'
import { useDeleteGoal, useUpdateGoal } from '../edit/mutations'
import {
  ColorSwatches,
  Field,
  InlineConfirm,
  InlineForm,
  colorIdOf,
  inlineInput,
  toNum,
  useEditing,
} from '../edit/inline'
import { formatDot, goalColor, periodProgress, statusLabel } from '../format'
import { metricSentence } from './metricSentence'
import { isKindComplete, kindBody, type GoalKindValue } from '../kind'
import { GoalKindPicker } from './GoalKindPicker'
import { AIBadge } from '@/components/ui/AIBadge'
import { LegacyGoalSetup, isLegacyGoal } from './LegacyGoalSetup'

const numStr = (v: number | null | undefined) => (v == null ? '' : String(v))

/** 목표 루틴 수 (분석용 routine_count) — 세부 목표(영역)마다 달린 루틴을 모두 더해요 */
export const routineCountOf = (goal: GoalCategory) =>
  goal.generalCategories?.reduce((n, g) => n + (g.routines?.length ?? 0), 0) ?? 0

const modeOf = (goal: GoalCategory) => goal.goalMode ?? 'ACHIEVEMENT'

/** 수정 폼을 연 위치 (분석용 entry_point) */
export type GoalEditEntryPoint = 'header' | 'notes' | 'progress_cta'

const trackEditOpen = (goal: GoalCategory, entryPoint: GoalEditEntryPoint) =>
  track('goal_detail.edit_open', {
    goal_category_id: goal.goalCategoryId,
    goal_mode: modeOf(goal),
    entry_point: entryPoint,
  })

/**
 * 전체 목표 수정 폼 — 이름·이모지·기간·색·시작한 이유·수치 목표.
 * 수치는 "시작값 · 목표값 · 종료일" 세 가지만 정해요. 점검 시점 수치는 이 값으로 자동 계산되고,
 * "현재" 값은 "지금 어디쯤?"에서 남긴 기록으로만 바뀌어요 (숫자를 고치는 곳이 여러 군데라 헷갈리던 문제).
 */
function GoalEditForm({ goal, onDone, onDelete }: { goal: GoalCategory; onDone: () => void; onDelete: () => void }) {
  const gid = goal.goalCategoryId
  const colors = useUserColors()
  const update = useUpdateGoal(goal.goalCategoryId)
  const [f, setF] = useState({
    emoji: goal.emoji ?? '',
    name: goal.goalCategoryName,
    start: goal.goalCategoryStartDate ?? '',
    end: goal.goalCategoryEndDate ?? '',
    colorId: null as number | null,
    motive: goal.motive ?? '',
    metricName: goal.metricName ?? '',
    metricUnit: goal.metricUnit ?? '',
    startValue: numStr(goal.startValue),
    targetValue: numStr(goal.targetValue),
  })
  const [kind, setKind] = useState<GoalKindValue | null>(
    goal.goalType && goal.goalSubType
      ? { goalType: goal.goalType, goalSubType: goal.goalSubType, goalKindLabel: goal.goalKindLabel ?? '' }
      : null
  )
  const [showMetric, setShowMetric] = useState(goal.targetValue != null || !!goal.metricName)
  // 업무형은 수치 · 전체 마감 · 카테고리가 없어요 (마감은 주제마다, V53) → 기간 · 수치 · 카테고리 칸을 숨겨요
  const isWork = goal.goalMode === 'RECORD'
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((x) => ({ ...x, [k]: v }))

  const colorId = f.colorId ?? colorIdOf(colors.data, goal.colorCode)
  const nums = [f.startValue, f.targetValue].map(toNum)
  const hadMetric = goal.targetValue != null || !!goal.metricName
  // 수치를 지우려고 칸을 비웠는지 ("수치 없이 진행하기")
  const [clearMetric, setClearMetric] = useState(false)
  const error = !f.name.trim()
    ? '목표 이름을 입력해 주세요.'
    : f.start && f.end && f.start > f.end
      ? '종료일이 시작일보다 빨라요.'
      : nums.some((n) => n !== null && Number.isNaN(n))
        ? '수치는 숫자로 입력해 주세요.'
        : kind && !isKindComplete(kind)
          ? '기타 카테고리 이름을 1~10자로 적어 주세요.'
          : null

  // [UX] 폼 아래 빨간 문구가 뜰 때 (이름이 비면 문구 없이 저장만 막혀요 → 보내지 않아요)
  const shownError = f.name.trim() ? error : null
  const lastError = useRef<string | null>(null)
  useEffect(() => {
    if (shownError === lastError.current) return
    lastError.current = shownError
    if (shownError) track('goal_detail.edit_error', { goal_category_id: gid, validation_error: shownError })
  }, [shownError, gid])

  const trackMetricToggle = (action: 'add' | 'clear' | 'undo') =>
    track('goal_detail.metric_toggle_click', { goal_category_id: gid, metric_action: action })

  /** 바뀐 칸들 (분석용 changed_fields) + 수치 칸에서 한 일 (metric_action) */
  const changesOf = () => {
    const fields: string[] = []
    if (f.name.trim() !== goal.goalCategoryName) fields.push('name')
    if (f.emoji.trim() !== (goal.emoji ?? '')) fields.push('emoji')
    if (f.start !== (goal.goalCategoryStartDate ?? '') || (!isWork && f.end !== (goal.goalCategoryEndDate ?? '')))
      fields.push('period')
    if (
      !isWork &&
      isKindComplete(kind) &&
      (kind.goalType !== goal.goalType ||
        kind.goalSubType !== goal.goalSubType ||
        (kind.goalKindLabel ?? '') !== (goal.goalKindLabel ?? ''))
    )
      fields.push('kind')
    if (f.colorId !== null && f.colorId !== colorIdOf(colors.data, goal.colorCode)) fields.push('color')
    if (f.motive.trim() !== (goal.motive ?? '')) fields.push('motive')
    let metricAction: 'add' | 'clear' | 'update' | 'none' = 'none'
    if (!isWork && clearMetric) metricAction = 'clear'
    else if (!isWork && showMetric) {
      const changed =
        f.metricName.trim() !== (goal.metricName ?? '') ||
        f.metricUnit.trim() !== (goal.metricUnit ?? '') ||
        nums[0] !== (goal.startValue ?? null) ||
        nums[1] !== (goal.targetValue ?? null)
      if (!hadMetric) metricAction = 'add'
      else if (changed) metricAction = 'update'
    }
    if (metricAction !== 'none') fields.push('metric')
    return { fields, metricAction }
  }

  const submit = (method: 'button' | 'enter') => {
    const { fields, metricAction } = changesOf()
    track('goal_detail.edit_save', {
      goal_category_id: gid,
      goal_mode: modeOf(goal),
      changed_fields: fields.join(','),
      metric_action: metricAction,
      submit_method: method,
    })
    update
      .mutateAsync({
        goalCategoryName: f.name.trim(),
        emoji: f.emoji.trim(),
        motive: f.motive.trim(),
        goalCategoryStartDate: f.start || undefined,
        // 업무형은 전체 마감이 없어요 (V53 — 마감은 주제마다)
        goalCategoryEndDate: isWork ? undefined : f.end || undefined,
        ...(f.colorId !== null ? { colorId: f.colorId } : {}),
        // 카테고리는 2단계까지 고른 경우에만 (1단계만 고르다 만 건 기존 값 유지)
        ...(!isWork && isKindComplete(kind) ? kindBody(kind) : {}),
        ...(clearMetric
          ? { clearMetric: true }
          : showMetric
            ? {
                metricName: f.metricName.trim(),
                metricUnit: f.metricUnit.trim(),
                startValue: nums[0],
                targetValue: nums[1],
              }
            : {}),
      })
      .then(() => {
        track('goal_detail.edit_result', { goal_category_id: gid, result: 'success' })
        onDone()
      })
      .catch((e: unknown) =>
        track('goal_detail.edit_result', { goal_category_id: gid, result: 'fail', error_status: errorStatusOf(e) })
      )
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={(method) => {
        track('goal_detail.edit_cancel', { goal_category_id: gid, close_method: method })
        onDone()
      }}
      saving={update.isPending}
      valid={!error}
      error={shownError}
      extra={
        <button
          type="button"
          onClick={() => {
            track('goal_detail.delete_click', {
              goal_category_id: gid,
              goal_mode: modeOf(goal),
              goal_status: goal.goalCategoryStatus,
            })
            onDelete()
          }}
          className="text-danger hover:bg-danger-soft flex items-center gap-1 rounded-lg px-2 py-1.5 text-[13px] font-semibold"
        >
          <Trash2 className="size-3.5" />
          목표 삭제
        </button>
      }
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
            className={inlineInput}
            data-autofocus
          />
        </Field>
      </div>
      {!isWork && (
        <div className="grid grid-cols-2 gap-2">
          <Field label="시작일">
            <input type="date" value={f.start} onChange={(e) => set('start', e.target.value)} className={inlineInput} />
          </Field>
          <Field label="종료일">
            <input type="date" value={f.end} onChange={(e) => set('end', e.target.value)} className={inlineInput} />
          </Field>
        </div>
      )}
      {/* 업무형은 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 카테고리를 고르지 않아요 */}
      {!isWork && (
        <div>
          <span className="text-ink-3 mb-1 block text-[12px] font-medium">카테고리</span>
          <GoalKindPicker value={kind} onChange={setKind} compact />
        </div>
      )}
      <Field label="색상">
        <ColorSwatches colors={colors.data} value={colorId} onChange={(id) => set('colorId', id)} />
      </Field>
      <Field label={isWork ? `${motiveLabel(goal)} (선택)` : '이 목표를 시작한 이유'}>
        <textarea
          value={f.motive}
          onChange={(e) => set('motive', e.target.value)}
          rows={2}
          maxLength={500}
          placeholder="Shift+Enter 로 줄바꿈"
          className={`${inlineInput} h-auto resize-none py-2 leading-relaxed`}
        />
      </Field>

      {isWork ? null : showMetric && !clearMetric ? (
        <div className="bg-subtle/60 space-y-2 rounded-xl p-3">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Field label="무엇을 재나요?">
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
            <Field label="처음 값">
              <input
                inputMode="decimal"
                value={f.startValue}
                onChange={(e) => set('startValue', e.target.value)}
                placeholder="80"
                className={inlineInput}
              />
            </Field>
            <Field label="목표 값">
              <input
                inputMode="decimal"
                value={f.targetValue}
                onChange={(e) => set('targetValue', e.target.value)}
                placeholder="72"
                className={inlineInput}
              />
            </Field>
          </div>
          {/* 숫자의 의미를 문장으로 — "체중 80kg → 72kg, 1월 8일까지 · 주 평균 -0.6kg" */}
          <p className="text-ink-2 text-[13px] font-medium">
            {metricSentence({
              name: f.metricName,
              unit: f.metricUnit,
              start: nums[0],
              target: nums[1],
              startDate: f.start,
              endDate: f.end,
            }) ?? '처음 값과 목표 값을 넣으면 어떤 계획인지 문장으로 보여드려요.'}
          </p>
          <p className="text-ink-3 text-xs leading-relaxed">
            마일스톤 수치는 이 값과 기간으로 자동 계산돼요. 지금 값은 &lsquo;지금 어디쯤?&rsquo;에서 기록하면 바뀌어요.
          </p>
          {hadMetric && (
            <button
              type="button"
              onClick={() => {
                trackMetricToggle('clear')
                setClearMetric(true)
              }}
              className="text-ink-3 hover:text-danger text-[13px] font-semibold"
            >
              수치 없이 진행하기
            </button>
          )}
        </div>
      ) : clearMetric ? (
        <div className="bg-warning-soft text-warning-ink-strong flex flex-wrap items-center gap-2 rounded-xl px-3 py-2.5 text-[13px]">
          <span className="flex-1">저장하면 수치 목표와 마일스톤의 수치가 지워져요. 기록한 값은 남아요.</span>
          <button
            type="button"
            onClick={() => {
              trackMetricToggle('undo')
              setClearMetric(false)
            }}
            className="font-semibold underline"
          >
            되돌리기
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => {
            trackMetricToggle('add')
            setShowMetric(true)
          }}
          className="text-text-brand text-[13px] font-semibold"
        >
          + 수치 목표 추가 (예: 체중 80 → 72kg)
        </button>
      )}
    </InlineForm>
  )
}

/**
 * 목표 상세 머리말 — 이름·상태·기간 + 시작한 이유/AI 노트.
 * 연필을 누르면 그 자리가 수정 폼으로 바뀌어요. 삭제는 폼 안의 "목표 삭제" → 그 자리에서 확인.
 */
export function GoalHeader({
  goal,
  embedded = false,
}: {
  goal: GoalCategory
  /** 요약 카드 안에 합쳐 넣을 때 (명세 12.1) — 경로 줄 없이, 제목 H2 20 · 둘째 줄에 태그 · 📅 기간 */
  embedded?: boolean
}) {
  const edit = useEditing('goal')
  const del = useEditing('del:goal')
  const remove = useDeleteGoal(goal.goalCategoryId)
  const router = useRouter()
  const period = periodProgress(goal.goalCategoryStartDate, goal.goalCategoryEndDate)
  const gid = goal.goalCategoryId

  // 수정 폼이 열린 채로 다른 항목 입력창을 열어 닫힌 경우 (삭제 확인으로 넘어간 건 제외)
  const current = edit.current
  const prevEditing = useRef<string | null>(null)
  useEffect(() => {
    const prev = prevEditing.current
    prevEditing.current = current
    if (prev === 'goal' && current !== null && current !== 'goal' && current !== 'del:goal')
      track('goal_detail.edit_cancel', { goal_category_id: gid, close_method: 'other_open' })
  }, [current, gid])

  return (
    <div>
      {!embedded && (
        <p className="text-ink-3 text-[13px]">
          <Link
            href="/goal"
            onClick={() => track('goal_detail.breadcrumb_click', { goal_category_id: gid, goal_mode: modeOf(goal) })}
            className="hover:text-ink"
          >
            목표
          </Link>{' '}
          › {goal.goalCategoryName}
        </p>
      )}

      {del.isOpen ? (
        <div className="mt-2">
          <InlineConfirm
            message={`'${goal.goalCategoryName}' 목표를 삭제할까요?`}
            detail="지금 이후로 예정된 Task와 루틴은 모두 지워지고, 지난 Task·완료한 기록은 남아요. 되돌릴 수 없어요."
            loading={remove.isPending}
            onCancel={(method) => {
              track('goal_detail.delete_cancel', { goal_category_id: gid, close_method: method })
              del.close()
            }}
            onConfirm={() => {
              track('goal_detail.delete_confirm', {
                goal_category_id: gid,
                goal_mode: modeOf(goal),
                goal_status: goal.goalCategoryStatus,
                routine_count: routineCountOf(goal),
              })
              remove
                .mutateAsync(undefined)
                .then(() => {
                  track('goal_detail.delete_result', { goal_category_id: gid, result: 'success' })
                  del.close()
                  router.replace('/goal')
                })
                .catch((e: unknown) =>
                  track('goal_detail.delete_result', {
                    goal_category_id: gid,
                    result: 'fail',
                    error_status: errorStatusOf(e),
                  })
                )
            }}
          />
        </div>
      ) : edit.isOpen ? (
        <div className="mt-2">
          <GoalEditForm goal={goal} onDone={edit.close} onDelete={() => del.open()} />
        </div>
      ) : embedded ? (
        // 업무형 요약 카드 안 (명세 13.8) — 이름 H3 18 (두 줄까지) · 태그(작게) · 시작일 글
        <>
          <div className="flex min-w-0 items-start gap-1">
            <h1 className="text-h3 text-text-primary line-clamp-2 min-w-0">
              {goal.emoji ? `${goal.emoji} ` : ''}
              {goal.goalCategoryName}
            </h1>
            <button
              type="button"
              onClick={() => {
                trackEditOpen(goal, 'header')
                edit.open()
              }}
              aria-label="목표 수정"
              className="text-text-secondary hover:bg-bg-surface/70 grid size-7 shrink-0 place-items-center rounded-[6px]"
            >
              <Pencil className="size-4" />
            </button>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            <Badge
              tone={goal.goalCategoryStatus === 'COMPLETED' ? 'success' : 'neutral'}
              className="bg-bg-surface/70 h-5 px-2"
            >
              {statusLabel(goal.goalCategoryStatus)}
            </Badge>
            {goal.temporary && (
              <Badge tone="warning" className="h-5 px-2">
                임시 목표
              </Badge>
            )}
            {goal.goalMode === 'RECORD' && (
              <Badge tone="neutral" className="bg-bg-surface/70 h-5 px-2">
                업무형
              </Badge>
            )}
            {goal.goalCategoryStartDate && (
              <span className="text-caption-md text-text-secondary">
                {formatDot(goal.goalCategoryStartDate)} 시작
                {goal.goalMode === 'RECORD'
                  ? ' · 마감은 세부 목표마다'
                  : goal.goalCategoryEndDate
                    ? ` · ${formatDot(goal.goalCategoryEndDate)} 마감`
                    : ''}
              </span>
            )}
          </div>
        </>
      ) : (
        <>
          <div className="group mt-1 flex flex-wrap items-center gap-3">
            <span className="size-3 rounded-full" style={{ backgroundColor: goalColor(goal.colorCode) }} />
            <h1 className="text-h2 sm:text-h1">
              {goal.emoji ? `${goal.emoji} ` : ''}
              {goal.goalCategoryName}
            </h1>
            <Badge tone={goal.goalCategoryStatus === 'COMPLETED' ? 'success' : 'neutral'}>
              {statusLabel(goal.goalCategoryStatus)}
            </Badge>
            {goal.temporary && <Badge tone="warning">임시 목표</Badge>}
            {goal.goalMode === 'RECORD' && <Badge tone="neutral">업무형</Badge>}
            {goal.goalKindName && goal.goalType !== 'WORK' && <Badge tone="brand">{goal.goalKindName}</Badge>}
            <button
              type="button"
              onClick={() => {
                trackEditOpen(goal, 'header')
                edit.open()
              }}
              className="text-ink-3 hover:bg-subtle hover:text-ink flex items-center gap-1 rounded-lg px-2 py-1 text-[13px] font-semibold"
            >
              <Pencil className="size-3.5" />
              수정
            </button>
            {/* AI 없이 만든 목표형 목표 → 처음엔 모달로 한 번, 그 뒤로는 이 버튼으로 (2026-10) */}
            {isLegacyGoal(goal) && <LegacyGoalSetup goal={goal} />}
          </div>
          {goal.goalCategoryStartDate && (
            <p className="text-ink-3 mt-1.5 text-sm">
              {formatDot(goal.goalCategoryStartDate)} –{' '}
              {goal.goalMode === 'RECORD'
                ? '시작 · 마감은 세부 목표마다'
                : goal.goalCategoryEndDate
                  ? formatDot(goal.goalCategoryEndDate)
                  : '종료일 미정'}
              {period && ` · ${period.weeks}주`}
            </p>
          )}
        </>
      )}
    </div>
  )
}

/** motive 칸의 제목 — 업무형은 "시작한 이유" 대신 만들 때 받은 한 줄 설명 */
export function motiveLabel(goal: Pick<GoalCategory, 'goalMode'>) {
  if (goal.goalMode !== 'RECORD') return '이 목표를 시작한 이유'
  return '무엇을 하나요'
}

/** 시작한 이유 · AI 노트 카드 (머리말 수정에서 함께 바뀌어요) */
export function GoalNotes({ goal }: { goal: GoalCategory }) {
  const edit = useEditing('goal')
  if (!goal.motive && !goal.aiNote) return null
  return (
    <Card className="space-y-3 p-5 sm:p-6">
      {goal.motive && (
        <button
          type="button"
          onClick={() => {
            trackEditOpen(goal, 'notes')
            edit.open()
          }}
          className="hover:bg-bg-sunken -m-2 block w-[calc(100%+16px)] rounded-xl p-2 text-left"
        >
          <p className="text-ink-3 text-[13px] font-medium">{motiveLabel(goal)}</p>
          <p className="mt-1 text-[15px] leading-relaxed whitespace-pre-wrap">{goal.motive}</p>
        </button>
      )}
      {goal.aiNote && (
        <p className="ai-border-flat rounded-card flex gap-2.5 px-4 py-3 text-[14px] leading-relaxed">
          <AIBadge size="sm" className="mt-px" />
          {goal.aiNote}
        </p>
      )}
    </Card>
  )
}
