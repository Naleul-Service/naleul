'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useMutation, useQuery } from '@tanstack/react-query'
import { MessageCircle } from 'lucide-react'
import { ArrowRight, NotebookPen, Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Badge, Chip } from '@/components/ui/Chip'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'
import { useGoalCategories } from '@/features/goal/api'
import { MUST_DO_SUGGESTIONS, isKindComplete, kindOfGoal, subOption, type GoalKindValue } from '@/features/goal/kind'
import { GoalKindPicker } from '@/features/goal/ui/GoalKindPicker'
import { tightestUsage, useAiUsage, useRefreshUsage, usageItem } from '@/features/usage/api'
import { UsageLine } from '@/features/usage/ui/UsageLine'
import { goalCreationApi, goalCreationKeys } from '../api'
import { EXAMPLE_GOALS } from '../constants'
import { MustDoPicker, withDraft } from './MustDoPicker'
import { FlowHeader } from './FlowHeader'
import { ResumeCard } from './ResumeCard'
import { AIBadge } from '@/components/ui/AIBadge'

/** /goal/new — 첫 문장 입력 + 이어서 만들기 */
export function StartGoalView({ sourceGoalId }: { sourceGoalId?: number }) {
  const router = useRouter()
  const [text, setText] = useState('')
  const [kind, setKind] = useState<GoalKindValue | null>(null)
  // 이 목표를 위해 꼭 하고 싶은 일 (예: 다이어트 → 헬스 · 식단 기록) — 계획에 반드시 루틴으로 들어가요
  const [mustDo, setMustDo] = useState<string[]>([])
  // 입력창에 적어 두기만 한 일도 함께 보내요 (하나만 할 거면 "+ 추가"를 안 눌러도 돼요)
  const [mustDoDraft, setMustDoDraft] = useState('')
  const mustDoInput = useRef<HTMLInputElement>(null)
  const kindReady = isKindComplete(kind)
  const goals = useGoalCategories()
  const source = sourceGoalId ? goals.data?.find((g) => g.goalCategoryId === sourceGoalId) : undefined
  // 예전에 AI 없이 만든 목표를 AI 로 다시 설계 (2026-10) — 확정하면 새 목표가 아니라 그 목표에 계획이 채워져요
  const isLegacySource = !!source && !source.temporary
  const refineSource = !sourceGoalId ? undefined : isLegacySource ? 'legacy' : 'temporary'

  // 예전 목표 · 임시 목표에서 들어오면 이름 · 카테고리를 미리 채워 둬요 (꼭 해야 할 일만 비워 둠) — 바로 시작하거나 고쳐서 시작
  //  카테고리: 목표에 저장된 값 → 없으면 이름 키워드로 짐작 (AI 를 부르지 않아요)
  const prefilled = useRef(false)
  useEffect(() => {
    if (prefilled.current || !source) return
    prefilled.current = true
    setText(source.goalCategoryName.slice(0, 100))
    setKind(kindOfGoal(source))
    track('goal_new.prefill', { refine_source: refineSource, has_saved_kind: !!source.goalSubType })
  }, [source, refineSource])

  // 오늘 남은 횟수 (AI 목표 만들기 · 계획 초안) — 다 쓰면 시작 버튼을 막고 미리 알려줘요
  const usage = useAiUsage()
  const refreshUsage = useRefreshUsage()
  // 2026-10: AI 목표 만들기 · 계획 초안 모두 하루 · 주 · 월 한도 — 화면엔 가장 먼저 바닥나는 것만
  const sessionLeft = usageItem(usage.data, 'GOAL_SESSION')
  const weekLeft = usageItem(usage.data, 'GOAL_SESSION_WEEK')
  const monthLeft = usageItem(usage.data, 'GOAL_SESSION_MONTH')
  const draftLeft = tightestUsage(usage.data, 'GOAL_DRAFT')
  const sessionTight = tightestUsage(usage.data, 'GOAL_SESSION') ?? sessionLeft
  const outOfMonth = monthLeft?.remaining === 0
  const outOfWeek = !outOfMonth && weekLeft?.remaining === 0
  const outOfQuota = sessionLeft?.remaining === 0 || draftLeft?.remaining === 0 || outOfWeek || outOfMonth

  // ── 분석 (노션 Tracking Plan 「목표 만들기」) ──
  const quotaState = outOfMonth
    ? 'out_monthly'
    : outOfWeek
      ? 'out_weekly'
      : sessionLeft?.remaining === 0
        ? 'out_daily'
        : draftLeft?.remaining === 0
          ? 'out_draft'
          : 'ok'
  // ResumeCard 와 같은 요청 (캐시를 같이 써서 한 번만 불러요)
  const active = useQuery({ queryKey: goalCreationKeys.active(), queryFn: goalCreationApi.getActive, staleTime: 0 })
  const sent = useRef({ pageview: false, quota: false })
  useEffect(() => {
    if (sent.current.pageview || usage.isPending || active.isPending) return
    sent.current.pageview = true
    track('goal_new.pageview', {
      is_refine: !!sourceGoalId,
      refine_source: refineSource,
      has_resume: !!active.data,
      quota_state: quotaState,
      remaining_sessions: sessionLeft?.remaining ?? undefined,
    })
  }, [usage.isPending, active.isPending, active.data, sourceGoalId, refineSource, quotaState, sessionLeft?.remaining])
  useEffect(() => {
    if (!outOfQuota || sent.current.quota) return
    sent.current.quota = true
    track('goal_new.quota_block_view', { quota_state: quotaState })
  }, [outOfQuota, quotaState])
  const hasInput = !!kind || !!text.trim() || mustDo.length > 0 || !!mustDoDraft.trim()

  const start = useMutation({
    mutationFn: ({ initialMessage }: { initialMessage?: string; mode: 'typed' | 'ask_ai' | 'refine_name' }) =>
      goalCreationApi.start(initialMessage, sourceGoalId, kind, withDraft(mustDo, mustDoDraft)),
    onSuccess: (turn, { mode }) => {
      track('goal_new.start_result', { result: 'success', session_id: turn.sessionId, start_mode: mode })
      refreshUsage()
      router.push(`/goal/new/${turn.sessionId}`)
    },
    onError: (error, { mode }) => {
      track('goal_new.start_result', {
        result: 'fail',
        error_status: isApiError(error) ? error.httpStatus : undefined,
        start_mode: mode,
      })
      toast.error(isApiError(error) ? error.message : '시작하지 못했어요. 다시 시도해 주세요.')
    },
  })

  const goalText = text.trim()
  const begin = (mode: 'typed' | 'ask_ai' | 'refine_name') => {
    track('goal_new.start_click', {
      start_mode: mode,
      goal_type: kind?.goalType,
      goal_sub_type: kind?.goalSubType,
      goal_text_length: mode === 'typed' ? goalText.length : 0,
      must_do_count: withDraft(mustDo, mustDoDraft).length,
      has_unadded_draft: !!mustDoDraft.trim(),
      is_refine: !!sourceGoalId,
      refine_source: refineSource,
    })
    start.mutate({ initialMessage: mode === 'typed' ? goalText : undefined, mode })
  }
  const submit = () => {
    if (goalText && kindReady && !start.isPending && !outOfQuota) begin('typed')
  }
  // 막힌 시작 버튼을 누를 때 — 왜 막혔는지
  const disabledReason = !kind
    ? 'no_kind'
    : !kindReady
      ? 'no_label'
      : !goalText
        ? 'no_text'
        : outOfQuota
          ? 'quota'
          : null

  // 이동 중에도 버튼이 다시 눌리지 않게 성공 후에도 막아둬요
  const busy = start.isPending || start.isSuccess
  // 고른 2단계에 맞는 예시 (기타·미선택이면 기본 예시)
  // 고른 2단계에 맞는 예시 (미선택이면 기본 예시, "기타"는 사용자가 적은 이름으로)
  const sub = kind ? subOption(kind.goalSubType) : null
  const examples = !sub ? EXAMPLE_GOALS : sub.custom ? [] : sub.examples
  const placeholder = !kindReady
    ? '먼저 카테고리를 골라 주세요'
    : sub?.custom
      ? `예) ${kind!.goalKindLabel?.trim()} 주 3회 꾸준히 하기`
      : `예) ${examples[0]}`

  return (
    <div className="bg-surface flex min-h-dvh flex-col">
      <FlowHeader
        title={isLegacySource ? '목표 다시 설계' : '새 목표'}
        onClose={() => {
          track('goal_new.close_click', { has_input: hasInput })
          router.push('/goal')
        }}
      />

      <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 pt-12 pb-10 sm:px-6 sm:pt-20">
        <Badge tone="brand" className="self-start">
          <AIBadge size="sm" className="-ml-2 rounded-full" />
          AI 목표 설계
        </Badge>
        <h1 className="text-h2 sm:text-h1 mt-4">어떤 목표를 이루고 싶으세요?</h1>
        <p className="text-ink-3 mt-2 text-[15px] leading-relaxed">
          카테고리를 고르면 AI가 그 분야에 맞는 루틴 위주로, 꼭 필요한 것만 담아 계획을 설계해 드려요.
        </p>

        {/* 회사 업무처럼 수치·마감이 없는 일은 AI 설계가 오히려 뜬구름이 돼요 → 업무형으로 안내 */}
        {!sourceGoalId && (
          <Link
            href="/goal/add?mode=record"
            onClick={() => track('goal_new.record_mode_click', { has_input: hasInput })}
            className="border-line bg-surface hover:border-line-strong group mt-6 flex items-center gap-3 rounded-2xl border px-4 py-3"
          >
            <span className="bg-action-secondary-bg text-text-brand grid size-9 shrink-0 place-items-center rounded-xl">
              <NotebookPen className="size-4" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[14px] font-semibold">회사 업무 · 취업 준비 · 동아리 활동인가요?</span>
              <span className="text-ink-3 block text-[13px]">
                AI 설계 없이 바로 만들고, 완료한 Task로 업무 일지를 쌓는 업무형 목표가 더 잘 맞아요.
              </span>
            </span>
            <ArrowRight className="text-ink-3 size-4 shrink-0 transition-transform group-hover:translate-x-0.5" />
          </Link>
        )}

        {sourceGoalId && (
          <div className="bg-warning-soft text-warning-ink-strong mt-6 rounded-2xl px-4 py-3.5 text-sm">
            {isLegacySource ? (
              <p>
                예전에 만든 목표{' '}
                <b>
                  {source?.emoji ? `${source.emoji} ` : ''}
                  {source?.goalCategoryName ?? ''}
                </b>
                을(를) AI와 다시 설계해요. 확정하면 이 목표에 수치 목표 · 마일스톤 · 루틴이 더해지고, 지금까지의 Task ·
                루틴 · 기록은 그대로 남아요.
              </p>
            ) : (
              <p>
                임시 목표{' '}
                <b>
                  {source?.emoji ? `${source.emoji} ` : ''}
                  {source?.goalCategoryName ?? ''}
                </b>
                을(를) 구체화해요. 목표가 확정되면 거기에 있던 Task와 기록이 새 목표로 옮겨져요.
              </p>
            )}
            {/* 이름 · 카테고리는 아래에 미리 채워 뒀어요 — 확인하고 시작만 누르면 돼요 (고쳐도 돼요) */}
            <p className="mt-1.5 text-[13px] opacity-80">
              이름과 카테고리를 미리 채워 뒀어요. 확인하고 시작만 누르세요.
            </p>
          </div>
        )}

        <section className="mt-8">
          <p className="mb-3 text-[14px] font-semibold">
            <span className="text-text-brand mr-1.5">1</span>카테고리
          </p>
          <GoalKindPicker
            value={kind}
            onChange={setKind}
            disabled={busy}
            achievementOnly
            onPickType={(t) => track('goal_new.kind_type_select', { goal_type: t })}
            onPickSub={(t, s) => track('goal_new.kind_sub_select', { goal_type: t, goal_sub_type: s })}
          />
        </section>

        <section className={kindReady ? 'mt-8' : 'pointer-events-none mt-8 opacity-40'}>
          <p className="mb-3 text-[14px] font-semibold">
            <span className="text-text-brand mr-1.5">2</span>이루고 싶은 목표를 작성해 주세요
          </p>
          {/* Enter 는 바로 시작하지 않고 다음 질문(꼭 해야 할 일)으로 넘어가요 */}
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
                e.preventDefault()
                mustDoInput.current?.focus()
              }
            }}
            maxLength={100}
            disabled={busy || !kindReady}
            placeholder={placeholder}
            aria-label="이루고 싶은 목표"
            className="border-border-subtle bg-surface placeholder:text-text-placeholder focus:border-border-focus disabled:bg-subtle h-12 w-full rounded-2xl border px-4 text-[15px] outline-none"
          />
          <div className="mt-3 flex flex-wrap gap-2">
            {examples.map((goal, i) => (
              <Chip
                key={goal}
                size="sm"
                onClick={() => {
                  track('goal_new.example_click', { goal_sub_type: kind?.goalSubType, example_index: i })
                  setText(goal)
                }}
                disabled={busy || !kindReady}
              >
                {goal}
              </Chip>
            ))}
          </div>
        </section>

        <section className={kindReady ? 'mt-8' : 'pointer-events-none mt-8 opacity-40'}>
          <p className="text-[14px] font-semibold">
            <span className="text-text-brand mr-1.5">3</span>
            {goalText ? (
              <>
                <span className="text-text-brand">
                  &ldquo;
                  {goalText.length > 24 ? `${goalText.slice(0, 24)}…` : goalText}
                  &rdquo;
                </span>{' '}
                목표를 이루기 위해 꼭 해야 할 일들이 있나요?
              </>
            ) : (
              '이 목표를 이루기 위해 꼭 해야 할 일들이 있나요?'
            )}{' '}
            <span className="text-ink-3 font-normal">(선택)</span>
          </p>
          <p className="text-ink-3 mt-1 mb-3 text-[13px]">
            하나면 적기만 하면 되고, 여러 개면 &lsquo;+ 추가&rsquo;로 더 적어 주세요. 고른 일은 계획에 꼭 넣어 드려요.
          </p>
          <MustDoPicker
            value={mustDo}
            onChange={setMustDo}
            draft={mustDoDraft}
            onDraftChange={setMustDoDraft}
            inputRef={mustDoInput}
            suggestions={kind ? (MUST_DO_SUGGESTIONS[kind.goalSubType] ?? []) : []}
            disabled={busy || !kindReady}
            surface="goal_new"
          />
        </section>

        {/* disabled 버튼은 click 이 안 나서 감싼 요소의 pointerdown 으로 막힌 클릭을 잡아요 */}
        <div
          className="mt-8"
          onPointerDown={() => {
            if (!busy && disabledReason) track('goal_new.disabled_click', { disabled_reason: disabledReason })
          }}
        >
          <Button
            size="lg"
            variant="ai"
            className="w-full"
            onClick={submit}
            loading={start.isPending}
            disabled={busy || !kindReady || !goalText || outOfQuota}
          >
            <Sparkles className="size-4" />
            AI와 목표 설계 시작하기
          </Button>
        </div>
        <UsageLine className="mt-2 justify-center" items={[sessionTight, draftLeft]} />
        {outOfQuota && (
          <p className="text-danger mt-1 text-center text-[13px] font-semibold">
            {outOfMonth
              ? '이번 달 AI 목표 만들기를 모두 썼어요. 다음 달 1일 0시에 다시 채워져요'
              : outOfWeek
                ? '이번 주 AI 목표 만들기를 모두 썼어요. 월요일 0시에 다시 채워져요'
                : draftLeft?.remaining === 0 && draftLeft.key !== 'GOAL_DRAFT'
                  ? `${draftLeft.key === 'GOAL_DRAFT_MONTH' ? '이번 달' : '이번 주'} AI 계획 초안 횟수를 모두 썼어요`
                  : '오늘 AI 목표 만들기 횟수를 모두 썼어요. 내일 0시에 다시 채워져요'}{' '}
            — 지금은 직접 만들기를 쓸 수 있어요.{' '}
            <Link
              href="/settings/membership"
              onClick={() => track('goal_new.membership_click', { quota_state: quotaState })}
              className="text-text-brand underline"
            >
              Pro로 더 많이
            </Link>
          </p>
        )}

        <button
          type="button"
          onClick={() => begin('ask_ai')}
          disabled={busy || !kindReady || outOfQuota}
          className="text-ink-3 hover:text-ink mt-6 inline-flex items-center gap-1.5 self-start text-sm font-medium disabled:opacity-40"
        >
          <MessageCircle className="size-4" />
          아직 정하지 못했어요. AI가 먼저 물어봐 주세요
        </button>

        <ResumeCard className="mt-auto sm:mt-16" />
      </main>
    </div>
  )
}
