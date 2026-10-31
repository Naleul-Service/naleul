'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronDown } from '@/components/icons'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useUserColors } from '@/features/color/api'
import {
  ColorSwatches,
  Field,
  InlineForm,
  inlineInput,
  type InlineCloseMethod,
  type InlineSubmitMethod,
} from '@/features/goal/edit/inline'
import { useCreateRecordGoal } from '../api'

/** 이름 예시 — 누르면 이름과 이모지가 같이 채워져요. 업무형은 종류를 나누지 않아요 (회사 · 취업 · 동아리 …) */
const EXAMPLES: { emoji: string; name: string }[] = [
  { emoji: '💼', name: '회사 업무' },
  { emoji: '🎯', name: '취업 준비' },
  { emoji: '🚀', name: '사이드 프로젝트' },
  { emoji: '🎸', name: '동아리 활동' },
  { emoji: '🌍', name: '대외활동' },
  { emoji: '📚', name: '스터디 운영' },
  { emoji: '🎓', name: '졸업 프로젝트' },
]

const EMOJIS = ['💼', '🎯', '🚀', '🎸', '🌍', '📚', '🎓', '🧑‍💻', '📊', '✍️']

/**
 * 업무형 목표 만들기 (goal_mode = RECORD).
 * 회사 업무 · 취업 준비 · 동아리처럼 종류가 다양해서 종류를 고르지 않고 이름만 받아요.
 * 기한 · 수치 · 세부 목표도 묻지 않아요 — 만든 뒤 주제마다 마감을 정하면 로드맵이 그려져요.
 */
export function RecordGoalForm({ onCancel, className }: { onCancel: () => void; className?: string }) {
  const router = useRouter()
  const colors = useUserColors()
  const create = useCreateRecordGoal()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [emoji, setEmoji] = useState(EXAMPLES[0].emoji)
  const [colorId, setColorId] = useState<number | null>(null)
  const [styleOpen, setStyleOpen] = useState(false)
  /** 마지막으로 누른 예시 칩의 이름 (분석용 name_from_example) */
  const [exampleName, setExampleName] = useState<string | null>(null)
  const value = name.trim()

  const cancel = (method: InlineCloseMethod) => {
    track('goal_add.record_cancel', {
      close_method: method,
      has_input: !!name.trim() || !!description.trim() || emoji !== EXAMPLES[0].emoji || colorId !== null,
    })
    onCancel()
  }

  const submit = (method: InlineSubmitMethod) => {
    track('goal_add.record_submit', {
      name_from_example: exampleName !== null && name === exampleName,
      has_description: !!description.trim(),
      emoji_changed: emoji !== EXAMPLES[0].emoji,
      color_changed: colorId !== null,
      submit_method: method,
    })
    create.mutate(
      { name: value, emoji, colorId, description: description.trim() || null },
      {
        onSuccess: (goal) => {
          track('goal_add.create_result', {
            goal_mode: 'RECORD',
            result: 'success',
            goal_category_id: goal.goalCategoryId,
          })
          router.push(`/goal/${goal.goalCategoryId}`)
        },
        onError: (e) =>
          track('goal_add.create_result', { goal_mode: 'RECORD', result: 'fail', error_status: errorStatusOf(e) }),
      }
    )
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={cancel}
      saving={create.isPending}
      valid={!!value}
      error={null}
      submitLabel="업무형 목표 만들기"
      className={className}
    >
      {/* ① 이름 */}
      <Field label="이름">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={40}
          placeholder="예: 취업 준비 · 동아리 활동 · OO팀 업무"
          className={inlineInput}
          data-autofocus
        />
      </Field>
      <div className="-mt-1 flex flex-wrap gap-1.5">
        {EXAMPLES.map((ex, i) => (
          <button
            key={ex.name}
            type="button"
            onClick={() => {
              track('goal_add.record_example_click', { example_index: i })
              setExampleName(ex.name)
              setName(ex.name)
              setEmoji(ex.emoji)
            }}
            className="border-line text-ink-3 hover:border-line-strong hover:text-ink-2 h-7 rounded-full border px-2.5 text-[12px]"
          >
            {ex.emoji} {ex.name}
          </button>
        ))}
      </div>

      {/* ② 한 줄 설명 */}
      <Field label="무엇을 하나요? (선택)">
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={100}
          placeholder="예: 하반기 백엔드 개발자 공채 지원"
          className={inlineInput}
        />
      </Field>

      {/* ③ 꾸미기 — 기본값이 있어서 접어 둬요 */}
      <div>
        <button
          type="button"
          onClick={() => {
            track('goal_add.record_style_toggle', { expanded: !styleOpen })
            setStyleOpen(!styleOpen)
          }}
          aria-expanded={styleOpen}
          className="text-ink-2 hover:text-ink flex items-center gap-1.5 text-[13px] font-semibold"
        >
          <span className="text-base" aria-hidden>
            {emoji}
          </span>
          이모지·색상 바꾸기
          <ChevronDown className={cn('size-4 transition-transform', styleOpen && 'rotate-180')} />
        </button>
        {styleOpen && (
          <div className="mt-2 space-y-3">
            <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="이모지">
              {EMOJIS.map((e) => (
                <button
                  key={e}
                  type="button"
                  role="radio"
                  aria-checked={emoji === e}
                  onClick={() => setEmoji(e)}
                  className={cn(
                    'grid size-9 place-items-center rounded-xl border text-lg',
                    emoji === e ? 'border-action-outline-border bg-action-secondary-bg' : 'border-line hover:border-line-strong'
                  )}
                >
                  {e}
                </button>
              ))}
            </div>
            <Field label="색상 (선택 안 하면 자동)">
              <ColorSwatches colors={colors.data} value={colorId} onChange={setColorId} />
            </Field>
          </div>
        )}
      </div>

      {/* 예시 — 만든 뒤 어떻게 쓰는지 */}
      <p className="bg-subtle text-ink-2 rounded-xl px-3 py-2.5 text-[13px] leading-relaxed">
        전체 마감 없이 만들어요. 만든 뒤 <b>포트폴리오 → 이력서 제출 → 면접</b>처럼 세부 목표마다 마감을 정하면 마감 순서대로
        로드맵이 그려지고, 세부 목표마다 남은 할 일을 체크해요.
      </p>
    </InlineForm>
  )
}
