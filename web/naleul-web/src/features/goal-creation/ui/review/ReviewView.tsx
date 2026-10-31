'use client'

import { useEffect, useState, type ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'
import { goalCreationApi, goalCreationKeys } from '../../api'
import { PLAN_INTENSITY_DESCRIPTION, PLAN_INTENSITY_LABEL, PLANNING_STYLE_LABEL } from '../../constants'
import { markStyleSelectedByUser, wasStyleSelectedByUser } from '../../planningStyleMemory'
import { goalFlowPath } from '../../routes'
import type { PlanIntensity, PlanningStyle, SessionDetail, SlotKey, SlotsPatch } from '../../types'
import { MustDoPicker, withDraft } from '../MustDoPicker'
import { FlowShell } from '../SessionGate'
import { SlotEditModal } from './SlotEditModal'
import { AIBadge } from '@/components/ui/AIBadge'

const STYLE_DESCRIPTION: Record<PlanningStyle, string> = {
  PLANNER: '요일·시간을 정해 매일 조금씩',
  SPONTANEOUS: '여유 있게, 몰아서 해도 괜찮게',
}

function SummaryRow({ label, value, onClick }: { label: string; value: string | null; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="hover:bg-bg-sunken group flex w-full items-center gap-4 px-5 py-4 text-left transition-colors"
    >
      <span className="text-ink-3 w-16 shrink-0 text-[13px]">{label}</span>
      <span className={cn('min-w-0 flex-1 text-[15px] leading-relaxed', value ? 'font-semibold' : 'text-ink-4')}>
        {value ?? 'AI가 알맞게 정해줄게요'}
      </span>
      <ChevronRight className="text-ink-4 group-hover:text-ink-2 size-4 shrink-0" />
    </button>
  )
}

function Section({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section className="mt-8">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-[15px] font-bold">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

/** G-2 정리 확인 */
export function ReviewView({ session }: { session: SessionDetail }) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const sessionId = session.sessionId
  const summary = session.summary

  const [editing, setEditing] = useState<SlotKey | null>(null)
  // 꼭 할 일 수정 (슬롯이 아니라 따로 저장돼요)
  const [mustDoOpen, setMustDoOpen] = useState(false)
  const [mustDoDraft, setMustDoDraft] = useState<string[]>(session.mustDoItems ?? [])
  const [mustDoText, setMustDoText] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const aiStyle: PlanningStyle = summary?.planningStyle ?? 'PLANNER'
  const [style, setStyle] = useState<PlanningStyle>(aiStyle)
  const [styleByUser, setStyleByUser] = useState(() => wasStyleSelectedByUser(sessionId))
  const [generating, setGenerating] = useState(false)
  // 계획 강도 — 처음엔 아무것도 고르지 않은 상태 (2026-10-25: "추천" 표시 · 기본 선택 제거 — AI 가 정한 것처럼 보이지 않게).
  // 고르지 않고 계획 만들기를 누르면 안정형으로 만들어요. 초안 화면에서 바꾸면 한 번 다시 만들어요
  const [intensity, setIntensity] = useState<PlanIntensity | null>(null)

  // ── 분석 (노션 「목표 만들기」 goal_review.*) ──
  const regenerate = session.status === 'DRAFT_READY'
  useEffect(() => {
    track('goal_review.pageview', {
      session_id: sessionId,
      is_regenerate: regenerate,
      filled_slot_count: Object.values(session.slots).filter((s) => s?.status === 'FILLED').length,
      ai_style: aiStyle,
    })
    // 화면이 그려질 때 한 번
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  // 수정 창이 열려 있는 항목 (꼭 할 일 창은 mustDo)
  const editingKey = mustDoOpen ? 'mustDo' : editing
  const slotResult = (props: Record<string, string | number | undefined>) =>
    track('goal_review.slot_edit_result', { session_id: sessionId, slot_key: editingKey ?? undefined, ...props })
  const cancelEdit = (key: string, method: ModalCloseMethod | 'cancel_button') =>
    track('goal_review.slot_edit_cancel', { session_id: sessionId, slot_key: key, close_method: method })

  const openEdit = (slot: SlotKey, value: string | null) => {
    track('goal_review.slot_edit_open', { session_id: sessionId, slot_key: slot, was_empty: !value })
    setSaveError(null)
    setEditing(slot)
  }

  const save = async (patch: SlotsPatch) => {
    setSaving(true)
    setSaveError(null)
    try {
      const turn = await goalCreationApi.updateSlots(sessionId, patch)
      // 화면 데이터(캐시)를 응답으로 바로 갱신 → 다시 불러오지 않아도 요약이 바뀌어요
      queryClient.setQueryData<SessionDetail>(goalCreationKeys.session(sessionId), (prev) =>
        prev
          ? { ...prev, status: turn.status, slots: turn.slots, summary: turn.summary, mustDoItems: turn.mustDoItems }
          : prev
      )
      slotResult({ result: 'success' })
      setEditing(null)
      setMustDoOpen(false)
      toast.success('수정했어요.')
    } catch (error) {
      slotResult({ result: 'fail', error_status: isApiError(error) ? error.httpStatus : undefined })
      if (isApiError(error) && error.httpStatus === 409) {
        // 이미 생성이 시작됐거나 끝난 세션 → 서버 상태로 화면 이동
        toast.show(error.message)
        setEditing(null)
        queryClient.invalidateQueries({ queryKey: goalCreationKeys.session(sessionId) })
        return
      }
      setSaveError(isApiError(error) ? error.message : '저장하지 못했어요. 다시 시도해 주세요.')
    } finally {
      setSaving(false)
    }
  }

  const chooseStyle = (next: PlanningStyle) => {
    track('goal_review.style_select', { session_id: sessionId, planning_style: next, ai_style: aiStyle })
    setStyle(next)
    setStyleByUser(true)
    markStyleSelectedByUser(sessionId)
  }

  const generate = async () => {
    if (generating) return
    track('goal_review.generate_click', {
      session_id: sessionId,
      planning_style: style,
      style_by_user: styleByUser,
      plan_intensity: intensity ?? 'STEADY',
      intensity_by_user: intensity != null,
      is_regenerate: regenerate,
    })
    setGenerating(true)
    try {
      // 성향은 사용자가 직접 골랐을 때만 보내요 (안 보내면 서버가 대화에서 추정한 값 사용)
      const res = await goalCreationApi.requestDraft(sessionId, {
        intensity: intensity ?? 'STEADY',
        ...(styleByUser ? { planningStyle: style } : {}),
      })
      track('goal_review.generate_result', { session_id: sessionId, result: 'success', draft_id: res.draftId })
      queryClient.removeQueries({ queryKey: goalCreationKeys.session(sessionId) })
      router.replace(goalFlowPath.generating(sessionId, res.draftId, 'review'))
    } catch (error) {
      track('goal_review.generate_result', {
        session_id: sessionId,
        result: 'fail',
        error_status: isApiError(error) ? error.httpStatus : undefined,
      })
      setGenerating(false)
      if (isApiError(error) && error.httpStatus === 409) {
        // 이미 생성 중 (다른 탭에서 눌렀거나 더블클릭) → 생성 중 화면으로
        router.replace(goalFlowPath.generating(sessionId, undefined, 'review'))
        return
      }
      toast.error(isApiError(error) ? error.message : '계획을 만들지 못했어요. 다시 시도해 주세요.')
    }
  }

  return (
    <FlowShell
      onClose={() => {
        // 확인창 없이 나가요 (세션은 남아서 시작 화면에서 이어서 만들기 가능)
        track('goal_review.close_click', { session_id: sessionId })
        router.push('/goal')
      }}
    >
      <div className="flex-1 overflow-y-auto">
        <main className="mx-auto w-full max-w-2xl px-4 pt-8 pb-10 sm:px-6 sm:pt-12">
          <Badge tone="brand">
            <AIBadge size="sm" className="-ml-2 rounded-full" />
            정리 확인
          </Badge>
          <h1 className="mt-3 text-h2 sm:text-h1">이렇게 정리했어요</h1>
          <p className="text-ink-3 mt-1.5 text-[15px]">항목을 눌러 고칠 수 있어요. 확인되면 계획을 만들어 드릴게요.</p>

          <Section title="목표 정보">
            <div className="border-line divide-line divide-y overflow-hidden rounded-card border">
              <SummaryRow
                label="목표"
                value={summary?.goalStatement ?? null}
                onClick={() => openEdit('goalStatement', summary?.goalStatement ?? null)}
              />
              <SummaryRow
                label="수치"
                value={summary?.metricText ?? null}
                onClick={() => openEdit('metric', summary?.metricText ?? null)}
              />
              <SummaryRow
                label="기한"
                value={summary?.deadlineText ?? null}
                onClick={() => openEdit('deadline', summary?.deadlineText ?? null)}
              />
              <SummaryRow
                label="계획"
                value={summary?.approach ?? null}
                onClick={() => openEdit('approach', summary?.approach ?? null)}
              />
              {summary?.motivation && (
                <SummaryRow
                  label="이유"
                  value={summary.motivation}
                  onClick={() => openEdit('motivation', summary.motivation)}
                />
              )}
              <SummaryRow
                label="실천 시간"
                value={summary?.preferenceText ?? null}
                onClick={() => openEdit('practicePreference', summary?.preferenceText ?? null)}
              />
              <SummaryRow
                label="꼭 할 일"
                value={session.mustDoItems?.length ? session.mustDoItems.join(' · ') : null}
                onClick={() => {
                  track('goal_review.slot_edit_open', {
                    session_id: sessionId,
                    slot_key: 'mustDo',
                    was_empty: !session.mustDoItems?.length,
                  })
                  setSaveError(null)
                  setMustDoDraft(session.mustDoItems ?? [])
                  setMustDoText('')
                  setMustDoOpen(true)
                }}
              />
            </div>
          </Section>

          <Section
            title="실행 성향"
            aside={!styleByUser && <span className="text-ink-3 text-xs">대화를 바탕으로 AI가 골랐어요</span>}
          >
            <div className="grid grid-cols-2 gap-3" role="radiogroup" aria-label="실행 성향">
              {(['PLANNER', 'SPONTANEOUS'] as const).map((value) => {
                const selected = style === value
                return (
                  <button
                    key={value}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => chooseStyle(value)}
                    className={cn(
                      'rounded-2xl border p-4 text-left transition-colors',
                      selected ? 'border-brand bg-brand-soft' : 'border-border-subtle hover:border-line-strong'
                    )}
                  >
                    <span className={cn('block text-[15px] font-bold', selected && 'text-text-brand')}>
                      {PLANNING_STYLE_LABEL[value]}
                    </span>
                    <span className="text-ink-3 mt-1 block text-[13px]">{STYLE_DESCRIPTION[value]}</span>
                  </button>
                )
              })}
            </div>
          </Section>

          <Section
            title="계획 강도"
            aside={<span className="text-ink-3 text-xs">고르지 않으면 안정형으로 만들어요</span>}
          >
            <div className="grid grid-cols-2 gap-3" role="radiogroup" aria-label="계획 강도">
              {(['STEADY', 'CHALLENGE'] as const).map((value) => {
                const selected = intensity === value
                return (
                  <button
                    key={value}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => {
                      track('goal_review.intensity_select', { session_id: sessionId, plan_intensity: value })
                      setIntensity(value)
                    }}
                    className={cn(
                      'rounded-2xl border p-4 text-left transition-colors',
                      selected ? 'border-brand bg-brand-soft' : 'border-border-subtle hover:border-line-strong'
                    )}
                  >
                    <span className={cn('block text-[15px] font-bold', selected && 'text-text-brand')}>
                      {PLAN_INTENSITY_LABEL[value]}
                    </span>
                    <span className="text-ink-3 mt-1 block text-[13px]">{PLAN_INTENSITY_DESCRIPTION[value]}</span>
                  </button>
                )
              })}
            </div>
          </Section>
        </main>
      </div>

      <div className="border-line bg-surface border-t pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto max-w-2xl px-4 py-3 sm:px-6 sm:py-4">
          <Button variant="ai" size="lg" fullWidth onClick={generate} loading={generating}>
            {!generating && <Sparkles className="size-4" />}
            {regenerate ? '이 정보로 다시 만들기' : '계획 만들기'}
          </Button>
        </div>
      </div>

      <Modal
        open={mustDoOpen}
        onClose={(method) => {
          if (saving) return
          cancelEdit('mustDo', method)
          setMustDoOpen(false)
        }}
        title="꼭 하고 싶은 일"
        description="고른 일은 계획에 꼭 루틴으로 넣어 드려요."
        dismissible={!saving}
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => {
                cancelEdit('mustDo', 'cancel_button')
                setMustDoOpen(false)
              }}
              disabled={saving}
            >
              취소
            </Button>
            <Button
              onClick={() => {
                track('goal_review.slot_edit_save_click', {
                  session_id: sessionId,
                  slot_key: 'mustDo',
                  submit_method: 'button',
                })
                save({ mustDoItems: withDraft(mustDoDraft, mustDoText) })
              }}
              loading={saving}
            >
              저장
            </Button>
          </>
        }
      >
        <MustDoPicker
          value={mustDoDraft}
          onChange={setMustDoDraft}
          draft={mustDoText}
          onDraftChange={setMustDoText}
          disabled={saving}
          surface="goal_review"
          sessionId={sessionId}
        />
        {saveError && <p className="text-danger mt-2 text-[13px]">{saveError}</p>}
      </Modal>
      <SlotEditModal
        slot={editing}
        slots={session.slots}
        saving={saving}
        serverError={saveError}
        onClose={(method) => {
          if (editing) cancelEdit(editing, method)
          setEditing(null)
        }}
        onSave={save}
        onSaveClick={(method) =>
          track('goal_review.slot_edit_save_click', {
            session_id: sessionId,
            slot_key: editing ?? undefined,
            submit_method: method,
          })
        }
        onValidationError={(message) => slotResult({ result: 'fail', validation_error: message })}
      />
    </FlowShell>
  )
}
