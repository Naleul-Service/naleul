'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
import { Check, Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'
import { goalCreationApi, goalCreationKeys } from '../../api'
import { POLL_INTERVAL_MS, POLL_TIMEOUT_MS, PROGRESS_STEPS } from '../../constants'
import { wasStyleSelectedByUser } from '../../planningStyleMemory'
import { goalFlowPath, type GenerateSource } from '../../routes'
import { FlowShell } from '../SessionGate'

const FAIL_MESSAGE = '계획을 만드는 데 실패했어요. 다시 시도해 주세요.'

/**
 * G-3 생성 중
 *
 * 초안은 서버가 비동기로 만들어요(약 30초). 화면은 2초마다 상태를 물어봐요(폴링).
 *  - READY   → 초안 검토 화면으로
 *  - FAILED  → 에러 화면 (다시 시도 / 정보 수정하기)
 *  - 90초 초과 → 에러 화면 (서버는 계속 만들 수도 있어요)
 * 화면을 벗어나면 컴포넌트가 사라지면서 폴링도 자동으로 멈춰요.
 */
export function GeneratingView({
  sessionId,
  draftId,
  source = 'resume',
}: {
  sessionId: number
  draftId: number
  source?: GenerateSource
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [timedOut, setTimedOut] = useState(false)
  const [retrying, setRetrying] = useState(false)

  // ── 분석 (노션 「목표 만들기」 goal_generating.*) ──
  const startedAt = useRef(0)
  const pollCount = useRef(0)
  const elapsed = () => Math.round((Date.now() - startedAt.current) / 1000)
  const ids = { session_id: sessionId, draft_id: draftId }
  useEffect(() => {
    startedAt.current = Date.now()
    track('goal_generating.view', { session_id: sessionId, draft_id: draftId, generate_source: source })
    // 화면이 그려질 때 한 번
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => setTimedOut(true), POLL_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [])

  const draft = useQuery({
    queryKey: goalCreationKeys.draft(sessionId, draftId),
    queryFn: () => {
      pollCount.current++
      return goalCreationApi.getDraft(sessionId, draftId)
    },
    staleTime: 0,
    gcTime: 0,
    retry: 2, // 폴링 중 네트워크가 잠깐 끊겨도 바로 실패로 보지 않게
    refetchInterval: (query) => {
      const data = query.state.data
      if (timedOut) return false
      if (data && data.status !== 'GENERATING') return false
      return data?.pollAfterMs ?? POLL_INTERVAL_MS
    },
  })

  const status = draft.data?.status

  // 생성 단계가 바뀔 때마다 (분석 → 세부 목표 → 마일스톤 → 루틴 → 점검)
  const progressStep = draft.data?.progressStep
  useEffect(() => {
    if (!progressStep || status !== 'GENERATING') return
    track('goal_generating.step_view', { ...ids, progress_step: progressStep, seconds_elapsed: elapsed() })
    // 단계가 바뀔 때만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [progressStep])

  useEffect(() => {
    if (status !== 'READY') return
    track('goal_generating.draft_ready', {
      session_id: sessionId,
      draft_id: draftId,
      seconds_elapsed: elapsed(),
      poll_count: pollCount.current,
    })
    queryClient.invalidateQueries({ queryKey: goalCreationKeys.active() })
    router.replace(goalFlowPath.draft(sessionId))
  }, [status, sessionId, draftId, router, queryClient])

  // 실패 화면 (서버 FAILED · 90초 초과 · 상태 확인 요청 실패)
  const failed = status === 'FAILED' || (timedOut && status !== 'READY') || (draft.isError && !draft.data)
  const failReason = status === 'FAILED' ? 'server_failed' : draft.isError && !draft.data ? 'poll_error' : 'timeout'
  useEffect(() => {
    if (!failed) return
    track('goal_generating.fail_view', {
      ...ids,
      fail_reason: failReason,
      seconds_elapsed: elapsed(),
      error_status: isApiError(draft.error) ? draft.error.httpStatus : undefined,
    })
    // 실패 화면이 뜨는 순간 한 번
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [failed])

  const retry = async () => {
    track('goal_generating.retry_click', { ...ids, fail_reason: failReason })
    setRetrying(true)
    try {
      const style = draft.data?.planningStyle
      const body = wasStyleSelectedByUser(sessionId) && style ? { planningStyle: style } : {}
      const res = await goalCreationApi.requestDraft(sessionId, body)
      track('goal_generating.retry_result', { session_id: sessionId, result: 'success', draft_id: res.draftId })
      router.replace(goalFlowPath.generating(sessionId, res.draftId, 'retry'))
    } catch (error) {
      track('goal_generating.retry_result', {
        session_id: sessionId,
        result: 'fail',
        error_status: isApiError(error) ? error.httpStatus : undefined,
      })
      setRetrying(false)
      if (isApiError(error) && error.httpStatus === 409) {
        // 아직 서버가 이전 요청을 만들고 있음 → 그 초안을 이어서 기다려요
        const session = await goalCreationApi.getSession(sessionId).catch(() => null)
        const latest = session?.latestDraft
        if (latest?.status === 'GENERATING') {
          router.replace(goalFlowPath.generating(sessionId, latest.draftId, 'retry'))
          return
        }
        if (latest?.status === 'READY') {
          router.replace(goalFlowPath.draft(sessionId))
          return
        }
      }
      toast.error(isApiError(error) ? error.message : FAIL_MESSAGE)
    }
  }

  const close = () => {
    track('goal_generating.close_click', {
      session_id: sessionId,
      draft_status: failed ? 'FAILED' : (status ?? 'unknown'),
      seconds_elapsed: elapsed(),
    })
    if (status === 'GENERATING' || !status) toast.show('계획은 계속 만들어지고 있어요. 나중에 이어서 볼 수 있어요.')
    router.push('/goal')
  }

  // ── 실패 / 시간 초과 ──
  if (failed) {
    const message =
      status === 'FAILED'
        ? (draft.data?.message ?? FAIL_MESSAGE)
        : draft.isError
          ? draft.error.message
          : '생각보다 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요.'
    return (
      <FlowShell onClose={close}>
        <div className="grid flex-1 place-items-center px-4 text-center">
          <div className="max-w-sm">
            <span className="bg-danger-soft text-danger mx-auto grid size-14 place-items-center rounded-full">
              <AlertCircle className="size-7" />
            </span>
            <p className="mt-5 text-lg font-bold">계획을 만들지 못했어요</p>
            <p className="text-ink-3 mt-2 text-sm leading-relaxed">{message}</p>
            <div className="mt-6 flex justify-center gap-2">
              <Button
                variant="secondary"
                onClick={() => {
                  track('goal_generating.edit_info_click', { session_id: sessionId, fail_reason: failReason })
                  router.replace(goalFlowPath.review(sessionId))
                }}
              >
                정보 수정하기
              </Button>
              <Button onClick={retry} loading={retrying}>
                다시 시도
              </Button>
            </div>
          </div>
        </div>
      </FlowShell>
    )
  }

  // ── 생성 중 ──
  const step = draft.data?.progressStep ?? 'ANALYZING'
  const currentIndex = Math.max(
    PROGRESS_STEPS.findIndex((s) => s.step === step),
    0
  )
  const done = status === 'READY'

  return (
    <FlowShell onClose={close}>
      <div className="grid flex-1 place-items-center px-4">
        <div className="w-full max-w-sm">
          <div className="relative mx-auto size-20">
            <span className="bg-ai-gradient absolute inset-0 animate-ping rounded-full opacity-20" />
            <span className="bg-ai-gradient relative grid size-20 place-items-center rounded-full text-white">
              <Sparkles className="size-8" />
            </span>
          </div>

          <p className="mt-8 text-center text-xl font-bold" aria-live="polite">
            {done ? '계획이 완성됐어요' : PROGRESS_STEPS[currentIndex].label}
          </p>
          <p className="text-ink-3 mt-2 text-center text-sm">보통 30초 정도 걸려요. 화면을 나가도 계속 만들어요.</p>

          <ol className="mt-10 space-y-3">
            {PROGRESS_STEPS.map((s, i) => {
              const state = done || i < currentIndex ? 'done' : i === currentIndex ? 'current' : 'todo'
              return (
                <li key={s.step} className="flex items-center gap-3">
                  <span
                    className={cn(
                      'grid size-6 shrink-0 place-items-center rounded-full',
                      state === 'done' && 'bg-action-primary-bg text-action-primary-fg',
                      state === 'current' && 'bg-brand-soft text-text-brand',
                      state === 'todo' && 'bg-subtle'
                    )}
                  >
                    {state === 'done' && <Check className="size-3.5" />}
                    {state === 'current' && <Spinner className="size-3" />}
                  </span>
                  <span
                    className={cn(
                      'text-sm',
                      state === 'todo' ? 'text-ink-4' : 'text-ink',
                      state === 'current' && 'font-semibold'
                    )}
                  >
                    {s.label}
                  </span>
                </li>
              )
            })}
          </ol>
        </div>
      </div>
    </FlowShell>
  )
}
