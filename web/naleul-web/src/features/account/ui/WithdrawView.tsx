'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { Check } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useBillingStatus } from '@/features/billing/api'
import type { BillingStatus } from '@/features/billing/types'
import { errorStatusOf } from '@/features/home/analytics'
import { track, trackBeforeLeave } from '@/lib/client/analytics'
import { isApiError } from '@/lib/client/api'
import { cn } from '@/lib/cn'
import { toast } from '@/stores/toastStore'
import { WITHDRAWAL_REASONS, useWithdraw, type WithdrawalReason } from '../api'

/** 멤버십 화면 상태 (membership_state) — MembershipView 와 같은 기준 */
function membershipStateOf(b: BillingStatus) {
  const sub = b.subscription
  if (sub && sub.status !== 'EXPIRED' && sub.status !== 'PENDING') {
    return sub.status === 'CANCELED' ? 'canceled' : sub.status === 'PAST_DUE' ? 'past_due' : 'active'
  }
  return b.otherPremium ? 'other_premium' : 'free'
}

const field = 'border-border-subtle focus:border-border-focus w-full rounded-xl border bg-surface px-3 text-[15px] outline-none'

/** /settings/withdraw — iOS WithdrawView(회원 탈퇴)를 옮긴 것 */
export function WithdrawView() {
  const [reason, setReason] = useState<WithdrawalReason | ''>('')
  const [detail, setDetail] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const withdraw = useWithdraw()
  const billing = useBillingStatus()
  const isPremium = billing.data?.premium
  const membershipState = billing.data ? membershipStateOf(billing.data) : undefined
  const reasonProp = reason || undefined

  // withdraw.pageview — 멤버십 상태를 불러온 직후 한 번 (못 불러오면 속성 없이)
  const pageviewSent = useRef(false)
  const billingSettled = !billing.isPending
  useEffect(() => {
    if (!billingSettled || pageviewSent.current) return
    pageviewSent.current = true
    track('withdraw.pageview', { is_premium: isPremium, membership_state: membershipState })
  }, [billingSettled, isPremium, membershipState])

  // withdraw.abandon — 탈퇴하지 않고 화면을 떠날 때 (다른 화면으로 이동 · 탭 닫기)
  const latest = useRef({ reason: reasonProp as string | undefined, agreed, hasInput: false })
  useEffect(() => {
    latest.current = { reason: reasonProp, agreed, hasInput: detail.trim().length > 0 }
  }, [reasonProp, agreed, detail])
  const leaving = useRef(false) // 탈퇴 확인을 눌러 요청 중 · 성공 → abandon 아님
  const abandonSent = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    const mountedRef = mounted
    const sentRef = abandonSent
    const leavingRef = leaving
    mountedRef.current = true
    const props = () => ({
      withdraw_reason: latest.current.reason,
      checked: latest.current.agreed,
      has_input: latest.current.hasInput,
    })
    const onPageHide = () => {
      if (leavingRef.current || sentRef.current) return
      sentRef.current = true
      void trackBeforeLeave('withdraw.abandon', props())
    }
    window.addEventListener('pagehide', onPageHide)
    return () => {
      window.removeEventListener('pagehide', onPageHide)
      mountedRef.current = false
      // 개발 모드(StrictMode)의 effect 재실행과 구분하려고 한 틱 미뤄요 — 바로 다시 붙으면 보내지 않아요
      setTimeout(() => {
        if (mountedRef.current || leavingRef.current || sentRef.current) return
        sentRef.current = true
        track('withdraw.abandon', props())
      }, 0)
    }
  }, [])

  // "기타"일 때만 상세 사유가 필수 (백엔드 서비스 단 검증과 같은 규칙)
  const reasonOk = reason !== '' && (reason !== 'OTHER' || detail.trim().length > 0)
  const canSubmit = reasonOk && agreed

  const submit = () => {
    if (!reason) return
    leaving.current = true
    void trackBeforeLeave('withdraw.confirm_click', {
      withdraw_reason: reason,
      is_premium: isPremium,
      membership_state: membershipState,
    })
    withdraw.mutate(
      { reason, detail: reason === 'OTHER' ? detail.trim() : '' },
      {
        onError: (e) => {
          leaving.current = false
          track('withdraw.result', { result: 'fail', error_status: errorStatusOf(e), withdraw_reason: reason })
          setConfirmOpen(false)
          toast.error(isApiError(e) ? e.message : '탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요.')
        },
      }
    )
  }

  return (
    <>
      <PageHeader breadcrumb={<Link href="/settings">설정</Link>} title="회원 탈퇴" />

      <Card className="mt-6 max-w-2xl p-5 sm:p-6">
        <h2 className="text-h4">정말 탈퇴하실건가요?</h2>
        <p className="text-ink-3 mt-3 text-[13px] font-medium">탈퇴 시 유의사항</p>
        <ul className="text-ink-3 mt-1 list-disc space-y-1 pl-5 text-[13px] leading-relaxed">
          <li>
            수집된 개인정보 및 나를 계정에 저장된 모든 정보(목표, 루틴, 일정, 미션과 관련된 모든 컨텐츠)는 삭제되어
            복구할 수 없고 연결된 기기의 연결이 해제됩니다.
          </li>
        </ul>

        <label className="mt-6 block">
          <span className="text-ink-2 mb-1.5 block text-[14px] font-semibold">
            떠나시는 이유를 알려주세요 <span className="text-danger">*</span>
          </span>
          <select
            value={reason}
            onChange={(e) => {
              const next = e.target.value as WithdrawalReason
              setReason(next)
              if (next !== 'OTHER') setDetail('')
              track('withdraw.reason_select', { withdraw_reason: next })
            }}
            className={cn(field, 'h-11')}
          >
            <option value="" disabled>
              탈퇴 사유 선택
            </option>
            {WITHDRAWAL_REASONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </label>

        {reason === 'OTHER' && (
          <textarea
            value={detail}
            onChange={(e) => setDetail(e.target.value)}
            rows={4}
            maxLength={500}
            placeholder="탈퇴 사유를 자유롭게 적어주세요"
            aria-label="탈퇴 상세 사유"
            className={cn(field, 'placeholder:text-text-placeholder mt-3 resize-none py-3 leading-relaxed')}
          />
        )}

        <label className="mt-6 flex cursor-pointer items-center gap-2.5">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => {
              setAgreed(e.target.checked)
              track('withdraw.agree_toggle', { checked: e.target.checked, withdraw_reason: reasonProp })
            }}
            className="peer sr-only"
          />
          <span
            className={cn(
              'grid size-5 shrink-0 place-items-center rounded-md border transition-colors',
              'peer-focus-visible:outline-brand peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2',
              agreed ? 'border-brand bg-brand text-white' : 'border-border-subtle bg-surface'
            )}
            aria-hidden
          >
            {agreed && <Check className="size-3.5" />}
          </span>
          <span className="text-ink-2 text-[14px]">회원 탈퇴 유의 사항을 확인했으며 동의합니다.</span>
        </label>

        <Button
          variant="danger"
          size="lg"
          fullWidth
          className="mt-5"
          disabled={!canSubmit}
          onClick={() => {
            setConfirmOpen(true)
            track('withdraw.submit_click', {
              withdraw_reason: reasonProp,
              text_length: reason === 'OTHER' ? detail.trim().length : undefined,
            })
          }}
        >
          탈퇴하기
        </Button>
      </Card>

      <ConfirmDialog
        open={confirmOpen}
        title="정말 탈퇴하시겠어요?"
        description="이 작업은 되돌릴 수 없어요."
        confirmLabel="탈퇴하기"
        tone="danger"
        loading={withdraw.isPending || withdraw.isSuccess}
        onCancel={() => {
          track('withdraw.confirm_cancel', { withdraw_reason: reasonProp })
          setConfirmOpen(false)
        }}
        onConfirm={submit}
      />
    </>
  )
}
