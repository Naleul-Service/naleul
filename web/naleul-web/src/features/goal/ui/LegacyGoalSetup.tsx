'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { BriefcaseBusiness } from 'lucide-react'
import { Check, Sparkles, Target } from '@/components/icons'
import { AIBadge } from '@/components/ui/AIBadge'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { useConvertToRecord } from '@/features/record/api'
import { isHabitGoal, isRecordGoal, type GoalCategory } from '../api'
import { MetricEditForm } from './progress/MetricEditForm'
import { refineHref } from './RefineWithAiCard'

const SEEN_KEY = (id: number) => `naleul.legacyGoalSetup.seen.${id}`

/**
 * AI 없이 만든 목표형 목표인지 (2026-10) — 예전 앱에서 만든 목표 · 지금 "직접 만들기"로 만든 목표 모두.
 * 수치 목표 · 마일스톤 · 루틴 설계가 없어서 AI 로 설계하거나 업무형으로 바꾸게 권해요.
 * 수치를 정하거나(목표형으로 쓰기로 함) AI 로 설계하면 더 이상 해당하지 않아요.
 */
export function isLegacyGoal(goal: GoalCategory) {
  return (
    !goal.temporary &&
    !goal.aiGenerated &&
    !isRecordGoal(goal) &&
    !isHabitGoal(goal) &&
    !goal.metricName &&
    goal.targetValue == null &&
    goal.goalCategoryStatus !== 'COMPLETED' &&
    goal.goalCategoryStatus !== 'DELETED'
  )
}

function seenBefore(id: number) {
  try {
    return window.localStorage.getItem(SEEN_KEY(id)) === '1'
  } catch {
    return true // 저장이 안 되는 환경이면 매번 뜨지 않게 자동으로 열지 않아요
  }
}
function markSeen(id: number) {
  try {
    window.localStorage.setItem(SEEN_KEY(id), '1')
  } catch {
    // 무시
  }
}

type Step = 'choose' | 'manual' | 'record'
/** 고르기 단계에서 고른 것 — 확인을 눌러야 진행돼요 */
type Choice = 'achievement_ai' | 'record' | 'achievement_manual'

/**
 * 목표 상세 — 예전 목표를 목표형(AI 설계) / 업무형 중에 골라 이어가게 (2026-10).
 *  - 처음 들어오면 모달로 한 번 떠요 (목표마다 · 이 브라우저에서 한 번). 그 뒤로는 머리말의 "목표 형태 정하기" 버튼으로 다시 열어요
 *  - 목표형 → AI 설계(/goal/new?sourceGoalId=). 확정하면 새 목표가 아니라 이 목표에 계획이 채워지고,
 *    지금 하는 루틴 · 세부 목표는 초안에 그대로 들어가 이어서 써요 (백엔드 LegacyPlanMerger)
 *  - AI 없이 수치만 → 모달 안 입력창 (정하면 버튼까지 사라져요)
 *  - 업무형 → 모달 안에서 한 번 더 확인 후 전환
 */
export function LegacyGoalSetup({ goal }: { goal: GoalCategory }) {
  const gid = goal.goalCategoryId
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<Step>('choose')
  const [choice, setChoice] = useState<Choice | null>(null)
  const router = useRouter()
  const convert = useConvertToRecord(gid)

  const opened = useRef(false)
  const show = (trigger: 'auto' | 'button') => {
    track('goal_detail.legacy_setup_view', {
      goal_category_id: gid,
      goal_status: goal.goalCategoryStatus,
      trigger,
    })
    setStep('choose')
    setChoice(null)
    setOpen(true)
  }
  // 처음 들어왔을 때 한 번만 자동으로 (localStorage 는 마운트 뒤에만 읽을 수 있어요)
  useEffect(() => {
    if (opened.current || seenBefore(gid)) return
    opened.current = true
    markSeen(gid)
    show('auto')
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 목표마다 처음 한 번만
  }, [gid])

  const close = (method: ModalCloseMethod | 'later') => {
    if (convert.isPending) return
    if (step === 'record')
      track('goal_detail.legacy_setup_record_cancel', { goal_category_id: gid, close_method: method })
    track('goal_detail.legacy_setup_dismiss', { goal_category_id: gid, close_method: method, step })
    setOpen(false)
  }

  const toRecord = () => {
    track('goal_detail.legacy_setup_record_confirm', { goal_category_id: gid })
    convert
      .mutateAsync()
      .then(() => {
        track('goal_detail.legacy_setup_record_result', { goal_category_id: gid, result: 'success' })
        setOpen(false)
      })
      .catch((e: unknown) =>
        track('goal_detail.legacy_setup_record_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
        })
      )
  }

  const selectAi = () => {
    track('goal_detail.legacy_setup_select', { goal_category_id: gid, choice: 'achievement_ai' })
    setOpen(false)
  }

  /** 고른 뒤 "확인" — 그때 진행해요 (고르기만 해선 아무 일도 안 일어나요) */
  const confirmChoice = () => {
    if (!choice) return
    track('goal_detail.legacy_setup_select', { goal_category_id: gid, choice })
    if (choice === 'achievement_ai') {
      setOpen(false)
      router.push(refineHref(goal))
    } else if (choice === 'record') {
      setStep('record')
    } else {
      track('goal_detail.metric_edit_open', { goal_category_id: gid, entry_point: 'legacy_setup', had_metric: false })
      setStep('manual')
    }
  }

  const option = (on: boolean) =>
    cn(
      'relative flex w-full flex-col gap-1.5 rounded-2xl border p-4 pr-10 text-left transition-colors',
      on ? 'border-brand bg-brand-soft/40 ring-brand ring-1' : 'border-line bg-surface hover:border-brand/60'
    )
  const mark = (on: boolean) => (
    <span
      aria-hidden
      className={cn(
        'absolute top-4 right-4 grid size-5 place-items-center rounded-full border',
        on ? 'border-brand bg-brand text-white' : 'border-line-strong'
      )}
    >
      {on && <Check className="size-3.5" />}
    </span>
  )

  return (
    <>
      <button
        type="button"
        onClick={() => {
          track('goal_detail.legacy_setup_button_click', { goal_category_id: gid })
          show('button')
        }}
        className="text-text-brand bg-ai-tint-soft hover:bg-ai-tint flex items-center gap-1 rounded-lg px-2 py-1 text-[13px] font-semibold"
      >
        <Sparkles className="size-3.5" />
        목표 형태 정하기
      </button>

      <Modal
        open={open}
        onClose={close}
        dismissible={!convert.isPending}
        title={
          step === 'record'
            ? '업무형 목표로 바꿀까요?'
            : step === 'manual'
              ? '이룰 수치를 정해요'
              : '이 목표, 어떤 형태로 이어갈까요?'
        }
        description={
          step === 'choose'
            ? '아직 목표 수치와 계획이 없어요. 목표 성격에 맞게 골라 주시면 화면을 그에 맞게 바꿔 드려요. 지금까지의 Task · 루틴 · 기록은 어느 쪽이든 그대로 남아요.'
            : undefined
        }
        footer={
          step === 'record' ? (
            <>
              <Button
                variant="secondary"
                onClick={() => {
                  track('goal_detail.legacy_setup_record_cancel', { goal_category_id: gid, close_method: 'back' })
                  setStep('choose')
                }}
                disabled={convert.isPending}
              >
                돌아가기
              </Button>
              <Button onClick={toRecord} loading={convert.isPending}>
                업무형으로 바꾸기
              </Button>
            </>
          ) : step === 'choose' ? (
            <>
              {/* 건너뛰기는 왼쪽 · 확인은 오른쪽 (고른 뒤에만 눌러져요) */}
              <Button variant="secondary" className="sm:mr-auto" onClick={() => close('later')}>
                건너뛰기
              </Button>
              <Button onClick={confirmChoice} disabled={!choice}>
                확인
              </Button>
            </>
          ) : undefined
        }
      >
        {step === 'choose' && (
          <div role="radiogroup" aria-label="목표 형태" className="flex flex-col gap-3">
            <button
              type="button"
              role="radio"
              aria-checked={choice === 'achievement_ai'}
              onClick={() => setChoice('achievement_ai')}
              className={option(choice === 'achievement_ai')}
            >
              {mark(choice === 'achievement_ai')}
              <span className="flex items-center gap-2 font-bold">
                <AIBadge size="sm" />
                목표형으로 AI와 설계할게요
              </span>
              <span className="text-ink-3 text-[13px] leading-relaxed">
                몸무게 · 점수 · 권수처럼 이룰 수치가 있어요. 몇 가지 질문에 답하면 수치 목표와 루틴을 설계해 이 목표에
                채워 드려요. 지금 하는 루틴 · 세부 목표는 그대로 이어서 써요.
              </span>
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={choice === 'record'}
              onClick={() => setChoice('record')}
              className={option(choice === 'record')}
            >
              {mark(choice === 'record')}
              <span className="flex items-center gap-2 font-bold">
                <BriefcaseBusiness className="text-brand size-5" />
                업무형으로 바꿀게요
              </span>
              <span className="text-ink-3 text-[13px] leading-relaxed">
                회사 업무 · 사이드 프로젝트처럼 정해진 수치 없이 한 일을 쌓아 가요. 세부 목표별 보드로 관리해요.
              </span>
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={choice === 'achievement_manual'}
              onClick={() => setChoice('achievement_manual')}
              className={option(choice === 'achievement_manual')}
            >
              {mark(choice === 'achievement_manual')}
              <span className="flex items-center gap-2 text-[14px] font-semibold">
                <Target className="text-ink-3 size-4" />
                AI 없이 목표 수치만 직접 정할게요
              </span>
            </button>
          </div>
        )}

        {step === 'manual' && (
          <div>
            {/* 수치를 저장하면 목표가 다시 불러와지고 이 버튼 · 모달은 저절로 사라져요 */}
            <MetricEditForm goal={goal} entry="legacy_setup" onDone={() => setStep('choose')} />
            <Link
              href={refineHref(goal)}
              onClick={selectAi}
              className="text-text-brand mt-3 inline-flex items-center gap-1.5 text-[13px] font-semibold hover:underline"
            >
              <Sparkles className="size-4" />
              루틴까지 AI와 설계하기
            </Link>
          </div>
        )}

        {step === 'record' && (
          <p className="text-ink-2 text-sm leading-relaxed">
            세부 목표별 보드로 보여 드려요. Task · 루틴 · 기록은 그대로 남고, 목표 종료일은 비워져요 (업무형은 세부 목표마다
            마감을 정해요).
          </p>
        )}
      </Modal>
    </>
  )
}
