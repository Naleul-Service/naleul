'use client'

import { useEffect, useRef, useState } from 'react'
import { CornerDownLeft } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Chip'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { cn } from '@/lib/cn'
import type { GoalCategory } from '../api'
import { useGoalEditApply, useGoalEditPreview, type GoalEditPreview } from '../edit/mutations'
import { AI_EDIT_PREFILL_EVENT } from './progress/DailyCheckInCard'
import { AIBadge } from '@/components/ui/AIBadge'

const MAX = 200
const EXAMPLES = [
  '최근 기록 보고 계획 조정해줘',
  '운동은 주 2회로 줄여줘',
  '종료일 2주 늦춰줘',
  '아침 7시에 스트레칭 10분 추가해줘',
]
const ACTION_TONE: Record<string, 'brand' | 'neutral' | 'danger'> = { 추가: 'brand', 변경: 'neutral', 삭제: 'danger' }
/** 분석용 change_action */
const ACTION_KEY: Record<string, string> = { 추가: 'add', 변경: 'update', 삭제: 'delete' }

/**
 * 목표 상세 "말로 고치기".
 * 트리 구조(영역 → 루틴 → 점검 시점)나 수치 계산 방식을 몰라도 한 문장으로 고칠 수 있게 해요.
 *  1) 문장을 보내면 AI 가 "바뀔 항목 목록"을 만들어요 — 이때는 아무것도 안 바뀌어요
 *  2) 바뀌기 전/후를 보고 원하는 것만 골라 [적용]
 * 실행 중인 목표라 바로 바꾸지 않고 꼭 확인을 거쳐요.
 *
 * asSheet (2026-10): 목표 상세 오른쪽 아래 떠 있는 AI 버튼으로 여는 창. 늘 그려 두고(open 만 바뀜)
 *   "오늘 기록"의 "최근 기록 보고 계획 조정받기"가 문장을 채우면 창을 스스로 열어요.
 */
export function GoalAiEditBar({
  goal,
  asSheet = false,
  open = false,
  onOpenChange,
}: {
  goal: GoalCategory
  asSheet?: boolean
  open?: boolean
  onOpenChange?: (open: boolean, how: 'prefill' | 'applied' | ModalCloseMethod) => void
}) {
  const [text, setText] = useState('')
  const [preview, setPreview] = useState<GoalEditPreview | null>(null)
  const [picked, setPicked] = useState<boolean[]>([])
  const ask = useGoalEditPreview(goal.goalCategoryId)
  const apply = useGoalEditApply(goal.goalCategoryId)
  const inputRef = useRef<HTMLInputElement>(null)
  const gid = goal.goalCategoryId
  /** 예시 칩 · "오늘 기록" 카드가 채워 준 문장 (is_prefilled 비교용) */
  const prefilled = useRef<string | null>(null)
  const onOpenChangeRef = useRef(onOpenChange)
  useEffect(() => {
    onOpenChangeRef.current = onOpenChange
  }, [onOpenChange])

  // "오늘 기록" 카드의 "최근 기록 보고 계획 조정받기" → 문장을 채우고 이 칸으로 이동
  useEffect(() => {
    const onPrefill = (e: Event) => {
      const v = (e as CustomEvent<string>).detail
      if (!v) return
      setText(v.slice(0, MAX))
      prefilled.current = v.slice(0, MAX)
      if (asSheet) {
        onOpenChangeRef.current?.(true, 'prefill')
        return
      }
      inputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      inputRef.current?.focus()
    }
    window.addEventListener(AI_EDIT_PREFILL_EVENT, onPrefill)
    return () => window.removeEventListener(AI_EDIT_PREFILL_EVENT, onPrefill)
  }, [asSheet])

  const submit = (method: 'button' | 'enter') => {
    const value = text.trim()
    if (!value || ask.isPending) return
    track('goal_detail.ai_edit_submit', {
      goal_category_id: gid,
      text_length: value.length,
      is_prefilled: prefilled.current != null && text === prefilled.current,
      submit_method: method,
    })
    ask
      .mutateAsync(value)
      .then((res) => {
        track('goal_detail.ai_edit_preview_result', {
          goal_category_id: gid,
          result: 'success',
          change_count: res.changes.length,
        })
        setPreview(res)
        setPicked(res.changes.map(() => true))
      })
      .catch((e: unknown) =>
        track('goal_detail.ai_edit_preview_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
        })
      )
  }

  const close = (method: ModalCloseMethod | 'cancel_button') => {
    if (apply.isPending) return
    track('goal_detail.ai_edit_cancel', {
      goal_category_id: gid,
      close_method: method,
      change_count: preview?.changes.length ?? 0,
    })
    setPreview(null)
  }

  const chosen = preview ? preview.changes.filter((_, i) => picked[i]).map((c) => c.op) : []
  const doApply = () => {
    const selected = chosen.length
    track('goal_detail.ai_edit_apply_click', {
      goal_category_id: gid,
      selected_count: selected,
      change_count: preview?.changes.length ?? 0,
    })
    apply
      .mutateAsync(chosen)
      .then(() => {
        track('goal_detail.ai_edit_apply_result', {
          goal_category_id: gid,
          result: 'success',
          selected_count: selected,
        })
        setPreview(null)
        setText('')
        prefilled.current = null
        if (asSheet) onOpenChange?.(false, 'applied')
      })
      .catch((e: unknown) =>
        track('goal_detail.ai_edit_apply_result', {
          goal_category_id: gid,
          result: 'fail',
          error_status: errorStatusOf(e),
          selected_count: selected,
        })
      )
  }

  const inputUi = (
    <>
      <div className="flex items-center gap-2">
        <AIBadge size="sm" />
        <input
          ref={inputRef}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX))}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit('enter')
            }
          }}
          disabled={ask.isPending}
          placeholder="바꾸고 싶은 걸 말로 적어 보세요. 예) 운동은 주 2회로 줄여줘"
          aria-label="말로 고치기"
          autoFocus={asSheet}
          className="placeholder:text-text-placeholder h-10 min-w-0 flex-1 bg-transparent text-[15px] outline-none"
        />
        <Button
          size="sm"
          variant="ai"
          onClick={() => submit('button')}
          loading={ask.isPending}
          disabled={!text.trim()}
        >
          <CornerDownLeft className="size-3.5" />
          고치기
        </Button>
      </div>
      {!text && (
        <div className="mt-1.5 flex flex-wrap gap-1.5 pl-7">
          {EXAMPLES.map((ex, i) => (
            <button
              key={ex}
              type="button"
              onClick={() => {
                track('goal_detail.ai_edit_example_click', { goal_category_id: gid, example_index: i })
                prefilled.current = ex
                setText(ex)
              }}
              className="border-line text-ink-3 hover:border-line-strong hover:text-ink-2 h-6 rounded-full border px-2 text-[12px]"
            >
              {ex}
            </button>
          ))}
        </div>
      )}

    </>
  )

  const previewModal = (
      <Modal
        open={!!preview}
        onClose={close}
        title="이렇게 바꿀까요?"
        description={preview?.summary}
        size="lg"
        dismissible={!apply.isPending}
        footer={
          preview?.changes.length ? (
            <>
              <Button variant="secondary" onClick={() => close('cancel_button')} disabled={apply.isPending}>
                취소
              </Button>
              <Button onClick={doApply} loading={apply.isPending} disabled={!chosen.length}>
                {chosen.length}개 적용
              </Button>
            </>
          ) : (
            <Button variant="secondary" onClick={() => close('cancel_button')}>
              다시 적기
            </Button>
          )
        }
      >
        {preview && preview.changes.length > 0 && (
          <>
            <p className="text-ink-3 mb-3 text-[13px]">아직 아무것도 바뀌지 않았어요. 적용할 항목만 체크해 주세요.</p>
            <ul className="space-y-2">
              {preview.changes.map((c, i) => (
                <li key={i}>
                  <label
                    className={cn(
                      'flex cursor-pointer items-start gap-3 rounded-2xl border p-3 transition-colors',
                      picked[i] ? 'border-ink/30 bg-surface' : 'border-line bg-bg-sunken opacity-60'
                    )}
                  >
                    <input
                      type="checkbox"
                      checked={!!picked[i]}
                      onChange={(e) => {
                        const checked = e.target.checked
                        track('goal_detail.ai_edit_item_toggle', {
                          goal_category_id: gid,
                          change_action: ACTION_KEY[c.action] ?? c.action,
                          checked,
                        })
                        setPicked((p) => p.map((v, j) => (j === i ? checked : v)))
                      }}
                      className="mt-1 size-4 shrink-0"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-1.5">
                        <Badge tone={ACTION_TONE[c.action] ?? 'neutral'} className="h-5 px-2 text-[11px]">
                          {c.action}
                        </Badge>
                        <span className="text-[14px] font-semibold">{c.target}</span>
                      </span>
                      {c.before && (
                        <span className="text-ink-3 mt-1 block text-[13px] line-through decoration-1">{c.before}</span>
                      )}
                      {c.after && <span className="text-ink mt-0.5 block text-[14px]">{c.after}</span>}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </>
        )}
      </Modal>
  )

  if (asSheet) {
    return (
      <>
        {/* 떠 있는 AI 버튼으로 여는 창 — 바뀔 항목 확인 창이 뜨면 잠깐 숨겨요 */}
        <Modal
          open={open && !preview}
          onClose={(m) => onOpenChange?.(false, m)}
          title="AI로 계획 고치기"
          description="루틴 · 할 일 · 기간을 한 문장으로 고쳐요. 적용 전에 바뀔 항목을 먼저 보여줘요."
          size="md"
          dismissible={!ask.isPending}
        >
          <div className="ai-border-flat rounded-card px-4 py-3">{inputUi}</div>
        </Modal>
        {previewModal}
      </>
    )
  }

  return (
    // AI 요청 바 (명세 6B)
    <section className="ai-border-flat rounded-card px-4 py-3 sm:px-5">
      {inputUi}
      {previewModal}
    </section>
  )
}
