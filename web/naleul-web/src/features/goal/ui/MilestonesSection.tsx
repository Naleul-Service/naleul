'use client'

import { useState } from 'react'
import { Check } from '@/components/icons'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { addDays, todayKst } from '@/features/timetable/time'
import type { GoalCategory, MilestoneInfo } from '../api'
import { useCreateMilestone, useDeleteMilestone, useUpdateMilestone } from '../edit/mutations'
import { AddButton, Field, InlineConfirm, InlineForm, RowActions, inlineInput, toNum, useEditing } from '../edit/inline'
import { formatDot } from '../format'
import { Section } from './sections'

/** 수치 목표가 있는 목표인지 (점검 시점 수치를 자동 계산할 수 있는지) */
const hasMetric = (goal: GoalCategory) => goal.startValue != null && goal.targetValue != null

/**
 * 점검 시점 추가·수정.
 * 수치는 기본으로 "처음 값 → 목표 값" 직선에서 날짜에 맞춰 서버가 계산해요. 사람이 맞출 필요가 없어요.
 * 꼭 필요할 때만 "이 시점만 직접 정하기"를 펼쳐서 값을 넣고, 언제든 자동으로 되돌릴 수 있어요.
 */
function MilestoneForm({
  goal,
  milestone,
  isLast = false,
  onDone,
}: {
  goal: GoalCategory
  milestone?: MilestoneInfo
  /** 수치 목표의 마지막 시점은 항상 목표 종료일 · 목표 값 (자동) */
  isLast?: boolean
  onDone: () => void
}) {
  const create = useCreateMilestone(goal.goalCategoryId)
  const update = useUpdateMilestone()
  const unit = goal.metricUnit ?? ''
  const metric = hasMetric(goal)
  const lockedLast = metric && isLast && !!goal.goalCategoryEndDate
  // 새 점검 시점 기본 날짜: 마지막 시점 + 4주 (없으면 오늘 + 4주), 목표 종료일을 넘지 않게
  const last = [...(goal.milestones ?? [])].sort((a, b) => b.dueDate.localeCompare(a.dueDate))[0]
  let suggested = addDays(last?.dueDate ?? todayKst(), 28)
  if (goal.goalCategoryEndDate && suggested > goal.goalCategoryEndDate) suggested = goal.goalCategoryEndDate

  const [title, setTitle] = useState(milestone?.title ?? '')
  const [dueDate, setDueDate] = useState(milestone?.dueDate ?? suggested)
  const [description, setDescription] = useState(milestone?.description ?? '')
  // 직접 정하기: 이미 직접 정한 값이면 펼친 채로
  const [manualOpen, setManualOpen] = useState(!!milestone?.targetValueManual)
  const [target, setTarget] = useState(milestone?.targetValueManual ? String(milestone.targetValue ?? '') : '')
  const [revertToAuto, setRevertToAuto] = useState(false)

  const targetNum = manualOpen ? toNum(target) : null
  const error = !title.trim()
    ? null
    : !dueDate
      ? '날짜를 입력해 주세요.'
      : targetNum !== null && Number.isNaN(targetNum)
        ? '수치는 숫자로 입력해 주세요.'
        : null

  const submit = () => {
    const base = { title: title.trim(), dueDate, description: description.trim() }
    if (milestone) {
      update.mutate(
        {
          id: milestone.milestoneId,
          ...base,
          ...(manualOpen && targetNum !== null ? { targetValue: targetNum } : {}),
          ...(revertToAuto || (milestone.targetValueManual && !manualOpen) ? { autoTargetValue: true } : {}),
        },
        { onSuccess: onDone }
      )
    } else {
      create.mutate({ ...base, targetValue: manualOpen ? targetNum : null }, { onSuccess: onDone })
    }
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={onDone}
      saving={create.isPending || update.isPending}
      valid={!!title.trim() && !error}
      error={error}
      submitLabel={milestone ? '저장' : '추가'}
    >
      <div className="grid grid-cols-[minmax(0,1fr)_160px] gap-2">
        <Field label="이름">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={40}
            placeholder="예: 1개월 차 점검"
            className={inlineInput}
          />
        </Field>
        <Field label={lockedLast ? '날짜 (목표 종료일)' : '날짜'}>
          <input
            type="date"
            value={lockedLast ? goal.goalCategoryEndDate! : dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            disabled={lockedLast}
            className={`${inlineInput} disabled:bg-subtle disabled:text-ink-3`}
          />
        </Field>
      </div>

      {/* 수치: 기본은 자동, 필요할 때만 직접 */}
      {metric && (
        <div className="bg-subtle/60 rounded-xl px-3 py-2.5 text-[13px]">
          {lockedLast ? (
            <p className="text-ink-2">
              마지막 마일스톤은 항상{' '}
              <b>
                목표 값 {goal.targetValue}
                {unit}
              </b>
              이에요. 바꾸려면 목표 수정에서 목표 값을 고쳐 주세요.
            </p>
          ) : manualOpen ? (
            <div className="space-y-2">
              <div className="flex items-end gap-2">
                <Field label={`이 시점 목표 (${unit || '값'})`} className="w-36">
                  <input
                    inputMode="decimal"
                    value={target}
                    onChange={(e) => setTarget(e.target.value)}
                    placeholder={milestone?.targetValue != null ? String(milestone.targetValue) : '예: 76'}
                    className={inlineInput}
                    autoFocus
                  />
                </Field>
                <button
                  type="button"
                  onClick={() => {
                    setManualOpen(false)
                    setTarget('')
                    setRevertToAuto(!!milestone?.targetValueManual)
                  }}
                  className="text-ink-2 h-10 px-2 text-[13px] font-semibold underline"
                >
                  자동으로 되돌리기
                </button>
              </div>
              <p className="text-ink-3 text-xs leading-relaxed">
                직접 정한 값은 자동 계산에서 빠져요. 목표의 처음 값·목표 값·종료일을 바꾸면 다시 자동으로 돌아가요. 처음
                값과 목표 값 사이를 벗어난 값은 자동으로 바뀌어요.
              </p>
            </div>
          ) : (
            <p className="text-ink-2 flex flex-wrap items-center gap-x-2 gap-y-1">
              <span>
                이 시점 수치는 날짜에 맞춰 <b>자동으로 계산</b>돼요
                {milestone?.targetValue != null && !revertToAuto ? ` (지금 ${milestone.targetValue}${unit})` : ''}.
              </span>
              <button
                type="button"
                onClick={() => setManualOpen(true)}
                className="text-ink-3 hover:text-ink text-[12px] font-semibold underline"
              >
                이 시점만 직접 정하기
              </button>
            </p>
          )}
        </div>
      )}

      <Field label="설명 (선택)">
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={300}
          className={inlineInput}
        />
      </Field>
    </InlineForm>
  )
}

function MilestoneItem({
  goal,
  m,
  current,
  isLast,
}: {
  goal: GoalCategory
  m: MilestoneInfo
  current: boolean
  isLast: boolean
}) {
  const key = `milestone:${m.milestoneId}`
  const edit = useEditing(key)
  const del = useEditing(`del:${key}`)
  const update = useUpdateMilestone()
  const remove = useDeleteMilestone()
  const done = m.status === 'ACHIEVED'
  const unit = goal.metricUnit ?? ''

  if (edit.isOpen || del.isOpen) {
    return (
      <li className="relative -ml-7">
        {edit.isOpen ? (
          <MilestoneForm goal={goal} milestone={m} isLast={isLast} onDone={edit.close} />
        ) : (
          <InlineConfirm
            message={`'${m.title}'을 삭제할까요?`}
            detail="이 마일스톤에 연결된 Task는 남고 연결만 풀려요."
            loading={remove.isPending}
            onCancel={del.close}
            onConfirm={() => remove.mutate(m.milestoneId, { onSuccess: del.close })}
          />
        )}
      </li>
    )
  }

  return (
    <li className="group relative">
      {/* 동그라미를 누르면 달성 / 달성 취소 */}
      <button
        type="button"
        onClick={() => update.mutate({ id: m.milestoneId, status: done ? 'PENDING' : 'ACHIEVED' })}
        disabled={update.isPending}
        aria-label={done ? `${m.title} 달성 취소` : `${m.title} 달성`}
        title={done ? '달성 취소' : '달성으로 표시'}
        className={cn(
          'absolute top-0.5 -left-7 grid size-5 place-items-center rounded-full border-[3px] transition-colors',
          done
            ? 'border-success bg-success text-on-ink'
            : current
              ? 'border-brand bg-surface hover:bg-brand-soft'
              : 'border-border-subtle bg-surface hover:border-success'
        )}
      >
        {done && <Check className="size-2.5" />}
      </button>
      <div className="flex items-start gap-2">
        <button type="button" onClick={edit.open} className="min-w-0 flex-1 text-left">
          <p className={cn('text-xs', m.status === 'MISSED' ? 'text-danger' : 'text-ink-3')}>
            ~{formatDot(m.dueDate)}
            {m.status === 'MISSED' && ' · 기한 지남'}
          </p>
          <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[15px] font-bold">
            {m.title}
            {m.targetValue != null && (
              <Badge tone={done ? 'success' : 'brand'} className="h-5 px-1.5 text-[11px]">
                {m.targetValue}
                {unit}
              </Badge>
            )}
            {m.targetValueManual && <span className="text-ink-3 text-[11px] font-medium">직접 정함</span>}
          </p>
          {m.description && <p className="text-ink-3 mt-0.5 text-[13px]">{m.description}</p>}
        </button>
        <RowActions label={m.title} onEdit={edit.open} onDelete={del.open} />
      </div>
    </li>
  )
}

/** 마일스톤 — 동그라미로 달성 체크, 글자를 누르면 그 자리에서 수정 */
export function MilestonesSection({ goal }: { goal: GoalCategory }) {
  const add = useEditing('milestone:new')
  const milestones = [...(goal.milestones ?? [])].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  const today = todayKst()
  const achieved = milestones.filter((m) => m.status === 'ACHIEVED').length
  // 지금 향하고 있는 마일스톤: 아직 달성 안 했고 기한이 남은 것 중 가장 가까운 것
  const currentId = milestones.find((m) => m.status !== 'ACHIEVED' && m.dueDate >= today)?.milestoneId

  return (
    <Section title="마일스톤" aside={milestones.length ? `${achieved} / ${milestones.length} 달성` : undefined}>
      <p className="text-ink-3 -mt-2 mb-4 text-[13px] leading-relaxed">
        목표까지 가는 길의 중간 점검 지점이에요. 그날까지 잘 가고 있는지 확인하고, 동그라미를 눌러 달성을 체크해요.
        {hasMetric(goal) && ' 수치는 처음 값 → 목표 값 사이에서 날짜에 맞춰 자동으로 정해져요.'}
      </p>
      {milestones.length > 0 && (
        <ol className="relative mb-4 space-y-5 pl-7">
          <span className="bg-line-strong absolute top-2 bottom-2 left-[9px] w-0.5" aria-hidden />
          {milestones.map((m, i) => (
            <MilestoneItem
              key={m.milestoneId}
              goal={goal}
              m={m}
              current={m.milestoneId === currentId}
              isLast={i === milestones.length - 1}
            />
          ))}
        </ol>
      )}
      {add.isOpen ? (
        <MilestoneForm goal={goal} onDone={add.close} />
      ) : (
        <AddButton onClick={add.open}>마일스톤 추가</AddButton>
      )}
    </Section>
  )
}
