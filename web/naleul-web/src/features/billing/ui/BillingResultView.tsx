'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { buttonClass } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { errorStatusOf } from '@/features/home/analytics'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { firstTimeFor, rememberedPayMethod, rememberedPlan } from '../analytics'
import { billingKeys, confirmBilling, confirmTossPay } from '../api'
import { PLAN_LABEL, longDate } from '../format'
import type { BillingStatus } from '../types'

type ConfirmState = { kind: 'loading' } | { kind: 'done'; data: BillingStatus } | { kind: 'error'; message: string }

/**
 * 결제 성공 후 돌아오는 곳 (successUrl)
 *  - 토스페이: ?method=toss_pay (토스 앱 인증 후) → 서버가 인증 상태 확인 + 첫 결제
 *  - 카드: ?customerKey=…&authKey=… → billingKey 발급 + 첫 결제
 */
export function BillingSuccessView() {
  const params = useSearchParams()
  return params.get('method') === 'toss_pay' ? <TossPaySuccess /> : <CardSuccess />
}

function TossPaySuccess() {
  const router = useRouter()
  const qc = useQueryClient()
  const [state, setState] = useState<ConfirmState>({ kind: 'loading' })
  const started = useRef(false) // 개발 모드에서 effect 가 두 번 돌아도 결제 요청은 한 번만

  useEffect(() => {
    if (started.current) return
    started.current = true
    // membership.confirm_result — 같은 돌아온 주소에서는 한 번만 (새로고침 · 뒤로 가기로 다시 요청해도 다시 안 보냄)
    const sendOnce = (props: Parameters<typeof track>[1]) => {
      if (firstTimeFor('confirm', window.location.pathname + window.location.search + rememberedPlan()))
        track('membership.confirm_result', props)
    }
    confirmTossPay()
      .then((data) => {
        sendOnce({ plan: data.subscription?.plan ?? rememberedPlan(), pay_method: 'toss_pay', result: 'success' })
        qc.setQueryData(billingKeys.status, data)
        setState({ kind: 'done', data })
        router.refresh() // 사이드바 · 설정의 FREE → PRO 표시
      })
      .catch((e: Error) => {
        sendOnce({
          plan: rememberedPlan(),
          pay_method: 'toss_pay',
          result: 'fail',
          error_status: errorStatusOf(e),
          fail_code: isApiError(e) ? e.status : undefined,
        })
        setState({ kind: 'error', message: e.message })
      })
  }, [qc, router])

  if (state.kind === 'loading') return <Processing />
  if (state.kind === 'error') return <ResultCard ok={false} title="결제하지 못했어요" message={state.message} />
  const sub = state.data.subscription
  return (
    <ResultCard
      ok
      title="Pro 구독을 시작했어요"
      message={sub ? `Pro ${PLAN_LABEL[sub.plan]} · 토스페이 · ${longDate(sub.currentPeriodEnd)}까지 이용할 수 있어요.` : ''}
    />
  )
}

/** 토스 카드 등록 성공 → 서버에서 billingKey 발급 + 첫 결제 */
function CardSuccess() {
  const params = useSearchParams()
  const router = useRouter()
  const qc = useQueryClient()
  const [state, setState] = useState<ConfirmState>({ kind: 'loading' })
  const started = useRef(false) // 개발 모드에서 effect 가 두 번 돌아도 결제 요청은 한 번만

  const authKey = params.get('authKey')
  const customerKey = params.get('customerKey')

  useEffect(() => {
    if (started.current) return
    started.current = true
    // membership.confirm_result — 같은 돌아온 주소에서는 한 번만 (새로고침 · 뒤로 가기로 다시 요청해도 다시 안 보냄).
    // 주소에는 authKey 가 있어서 저장은 해시만, 속성에는 절대 넣지 않아요.
    const sendOnce = (props: Parameters<typeof track>[1]) => {
      if (firstTimeFor('confirm', window.location.search)) track('membership.confirm_result', props)
    }
    if (!authKey || !customerKey) {
      sendOnce({ plan: rememberedPlan(), pay_method: 'card_subscription', result: 'fail', fail_code: 'missing_params' })
      return
    }
    confirmBilling(authKey, customerKey)
      .then((data) => {
        sendOnce({ plan: data.subscription?.plan ?? rememberedPlan(), pay_method: 'card_subscription', result: 'success' })
        qc.setQueryData(billingKeys.status, data)
        setState({ kind: 'done', data })
        router.refresh() // 사이드바 · 설정의 FREE → PRO 표시
      })
      .catch((e: Error) => {
        sendOnce({
          plan: rememberedPlan(),
          pay_method: 'card_subscription',
          result: 'fail',
          error_status: errorStatusOf(e),
          fail_code: isApiError(e) ? e.status : undefined,
        })
        setState({ kind: 'error', message: e.message })
      })
  }, [authKey, customerKey, qc, router])

  if (!authKey || !customerKey) {
    return (
      <ResultCard
        ok={false}
        title="결제하지 못했어요"
        message="카드 등록 정보가 없어요. 처음부터 다시 시도해 주세요."
      />
    )
  }
  if (state.kind === 'loading') return <Processing />
  if (state.kind === 'error') return <ResultCard ok={false} title="결제하지 못했어요" message={state.message} />
  const sub = state.data.subscription
  return (
    <ResultCard
      ok
      title="Pro 구독을 시작했어요"
      message={sub ? `Pro ${PLAN_LABEL[sub.plan]} · ${longDate(sub.currentPeriodEnd)}까지 이용할 수 있어요.` : ''}
    />
  )
}

function Processing() {
  return (
    <Card className="mt-6 grid max-w-xl place-items-center gap-3 p-10 text-center">
      <Spinner className="size-6" />
      <p className="font-bold">결제하고 있어요</p>
      <p className="text-ink-3 text-sm">창을 닫지 말고 잠시만 기다려 주세요.</p>
    </Card>
  )
}

/** 결제 실패 · 취소 — 카드: failUrl?code=…&message=… / 토스페이: ?method=toss_pay (토스 인증 실패 · 취소) */
export function BillingFailView() {
  const params = useSearchParams()
  const tossPay = params.get('method') === 'toss_pay' || (!params.get('code') && rememberedPayMethod() === 'toss_pay')
  const code = params.get('code') ?? (tossPay ? 'toss_pay_auth_failed' : null)

  // membership.fail_view — '구독하지 않았어요'가 보일 때 한 번 (같은 주소로 새로고침하면 다시 안 보냄)
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current) return
    sent.current = true
    if (firstTimeFor('fail', window.location.search))
      track('membership.fail_view', {
        fail_code: code ?? 'missing_params',
        pay_method: tossPay ? 'toss_pay' : 'card_subscription',
      })
  }, [code, tossPay])

  const message = tossPay
    ? '토스 앱에서 인증하지 않았거나 실패했어요. 다시 시도해 주세요.'
    : code === 'PAY_PROCESS_CANCELED'
      ? '카드 등록을 취소했어요.'
      : (params.get('message') ?? '카드를 등록하지 못했어요.')
  return <ResultCard ok={false} title="구독하지 않았어요" message={message} />
}

function ResultCard({ ok, title, message }: { ok: boolean; title: string; message: string }) {
  return (
    <Card className="mt-6 max-w-xl p-8 text-center">
      {ok ? (
        <CheckCircle2 className="text-success mx-auto size-10" />
      ) : (
        <XCircle className="text-danger mx-auto size-10" />
      )}
      <p className="mt-3 text-[18px] font-bold">{title}</p>
      {message && <p className="text-ink-3 mt-1.5 text-sm">{message}</p>}
      <div className="mt-6 flex justify-center gap-2">
        <Link
          href="/settings/membership"
          className={buttonClass(ok ? 'secondary' : 'primary')}
          onClick={() =>
            track('membership.result_cta_click', { destination: 'membership', result: ok ? 'success' : 'fail' })
          }
        >
          {ok ? '멤버십 보기' : '다시 시도하기'}
        </Link>
        {ok && (
          <Link
            href="/"
            className={buttonClass('primary')}
            onClick={() => track('membership.result_cta_click', { destination: 'home', result: 'success' })}
          >
            홈으로
          </Link>
        )}
      </div>
    </Card>
  )
}
