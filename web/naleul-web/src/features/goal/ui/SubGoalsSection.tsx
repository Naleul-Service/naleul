'use client'

import { useState } from 'react'
import { useUserColors } from '@/features/color/api'
import { addDays, todayKst } from '@/features/timetable/time'
import { isRecordGoal, type GoalCategory, type SubGoalInfo } from '../api'
import { useCreateSubGoal, useDeleteSubGoal, useUpdateSubGoal } from '../edit/mutations'
import {
  AddButton,
  ColorSwatches,
  Field,
  InlineConfirm,
  InlineForm,
  RowActions,
  colorIdOf,
  inlineInput,
  useEditing,
} from '../edit/inline'
import { formatDot, goalColor } from '../format'
import { Section } from './sections'

/** 화면에 보여줄 세부 목표 (삭제된 것 제외 — 백엔드가 소프트 삭제된 것도 내려줘요) */
export const activeSubGoals = (goal: GoalCategory) =>
  goal.generalCategories.filter((sg) => sg.generalCategoryStatus !== 'DELETED')

/** 사용자가 만든 영역만 ("기타 할 일" 그릇 제외) */
export const areasOf = (goal: GoalCategory) => activeSubGoals(goal).filter((sg) => !sg.defaultBucket)

/** 목표의 "기타 할 일" 그릇 (영역을 안 고른 루틴·Task 가 들어가요) */
export const bucketOf = (goal: GoalCategory) => activeSubGoals(goal).find((sg) => sg.defaultBucket)

/** 새로 만들 때 기본 기간: 목표 기간 (기록형은 종료일이 없어서 오늘부터 3개월) */
export function defaultPeriod(goal: GoalCategory) {
  const today = todayKst()
  const start = goal.goalCategoryStartDate ?? today
  const end = goal.goalCategoryEndDate ?? (isRecordGoal(goal) ? addDays(today, 90) : start)
  return { start, end: end < start ? start : end }
}

function SubGoalForm({
  goal,
  sub,
  onDone,
}: {
  goal: GoalCategory
  /** 없으면 새로 추가 */
  sub?: SubGoalInfo
  onDone: () => void
}) {
  const colors = useUserColors()
  const create = useCreateSubGoal(goal.goalCategoryId)
  const update = useUpdateSubGoal()
  const period = defaultPeriod(goal)
  const [name, setName] = useState(sub?.generalCategoryName ?? '')
  const [start, setStart] = useState(sub?.generalCategoryStartDate ?? period.start)
  const [end, setEnd] = useState(sub?.generalCategoryEndDate ?? period.end)
  const [colorId, setColorId] = useState<number | null>(null)
  const effectiveColor = colorId ?? colorIdOf(colors.data, sub?.colorCode ?? goal.colorCode)

  const error = !name.trim()
    ? null
    : !start || !end
      ? '기간을 입력해 주세요.'
      : start > end
        ? '종료일이 시작일보다 빨라요.'
        : null
  const valid = !!name.trim() && !error && effectiveColor !== null

  const submit = () => {
    const body = {
      generalCategoryName: name.trim(),
      generalCategoryStartDate: start,
      generalCategoryEndDate: end,
    }
    if (sub) {
      update.mutate(
        { id: sub.generalCategoryId, ...body, ...(colorId !== null ? { colorId } : {}) },
        { onSuccess: onDone }
      )
    } else {
      create.mutate({ ...body, colorId: effectiveColor! }, { onSuccess: onDone })
    }
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={onDone}
      saving={create.isPending || update.isPending}
      valid={valid}
      error={error}
      submitLabel={sub ? '저장' : '추가'}
    >
      <Field label="세부 목표 이름">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={30}
          placeholder="예: 식단 관리, 단어 외우기"
          className={inlineInput}
        />
      </Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label="시작일">
          <input type="date" value={start} onChange={(e) => setStart(e.target.value)} className={inlineInput} />
        </Field>
        <Field label="종료일">
          <input type="date" value={end} onChange={(e) => setEnd(e.target.value)} className={inlineInput} />
        </Field>
      </div>
      <Field label="색상">
        <ColorSwatches colors={colors.data} value={effectiveColor} onChange={setColorId} />
      </Field>
    </InlineForm>
  )
}

function SubGoalCard({ goal, sub }: { goal: GoalCategory; sub: SubGoalInfo }) {
  const key = `sub:${sub.generalCategoryId}`
  const edit = useEditing(key)
  const del = useEditing(`del:${key}`)
  const remove = useDeleteSubGoal()

  if (edit.isOpen) {
    return (
      <div>
        <SubGoalForm goal={goal} sub={sub} onDone={edit.close} />
      </div>
    )
  }
  if (del.isOpen) {
    return (
      <div>
        <InlineConfirm
          message={`'${sub.generalCategoryName}'을 삭제할까요?`}
          detail={
            sub.routines.length
              ? `루틴 ${sub.routines.length}개와 그 루틴의 Task도 함께 삭제돼요.`
              : '이 세부 목표에 연결된 일회성 Task는 남아요.'
          }
          loading={remove.isPending}
          onCancel={del.close}
          onConfirm={() => remove.mutate(sub.generalCategoryId, { onSuccess: del.close })}
        />
      </div>
    )
  }
  return (
    <div className="group bg-bg-sunken relative rounded-2xl p-4">
      <div className="flex items-start justify-between gap-2">
        <span
          className="mt-1.5 block h-1 w-8 rounded-full"
          style={{ backgroundColor: goalColor(sub.colorCode ?? goal.colorCode) }}
        />
        <RowActions label={sub.generalCategoryName} onEdit={edit.open} onDelete={del.open} className="-mt-1 -mr-1" />
      </div>
      <button type="button" onClick={edit.open} className="mt-2 block w-full text-left">
        <p className="text-[15px] font-bold">
          {sub.emoji ? `${sub.emoji} ` : ''}
          {sub.generalCategoryName}
        </p>
        {/* AI 가 정한 실천 원칙 (예: 식단 — 하루 1,600kcal, 단백질 90g …) */}
        {sub.description && (
          <p className="text-ink-2 mt-1.5 text-[13px] leading-relaxed whitespace-pre-wrap">{sub.description}</p>
        )}
        <p className="text-ink-3 mt-1 text-[13px]">
          루틴 {sub.routines.length}개
          {sub.generalCategoryStartDate && (
            <span className="text-ink-4">
              {' '}
              · {formatDot(sub.generalCategoryStartDate).slice(5)}–{formatDot(sub.generalCategoryEndDate).slice(5)}
            </span>
          )}
        </p>
      </button>
    </div>
  )
}

/**
 * 영역 (예전 이름: 세부 목표) — 루틴·할 일을 묶어 보는 라벨.
 * 없어도 돼요. 안 고른 루틴·할 일은 "기타 할 일" 그릇에 들어가고, 그 그릇은 여기 보여주지 않아요.
 */
export function SubGoalsSection({ goal }: { goal: GoalCategory }) {
  const subs = areasOf(goal)
  const add = useEditing('sub:new')

  return (
    <Section title="세부 목표" aside={`${subs.length}개`}>
      <p className="text-ink-3 -mt-2 mb-3 text-[13px] leading-relaxed">
        목표를 이루려고 나눈 갈래예요 (예: 필기 대비 · 실기 대비). 루틴·Task에 붙는 이름표 역할이라, 없어도 괜찮아요.
      </p>
      {/* 세로로 쌓아요 — 좁은 오른쪽 칸에서 가로로 나누면 글자가 한두 글자씩 끊겨 읽기 어려워요 */}
      {subs.length > 0 && (
        <div className="space-y-2.5">
          {subs.map((sg) => (
            <SubGoalCard key={sg.generalCategoryId} goal={goal} sub={sg} />
          ))}
        </div>
      )}
      <div className={subs.length ? 'mt-3' : ''}>
        {add.isOpen ? (
          <SubGoalForm goal={goal} onDone={add.close} />
        ) : (
          <AddButton onClick={add.open}>세부 목표 추가</AddButton>
        )}
      </div>
    </Section>
  )
}
