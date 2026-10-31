import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/client/api'
import type { BillingPrepare, BillingStatus, TossPayPrepare, WebPlan } from './types'

export const billingKeys = { status: ['billing', 'status'] as const }

export function useBillingStatus() {
  return useQuery({ queryKey: billingKeys.status, queryFn: () => api.get<BillingStatus>('/billing') })
}

export const prepareBilling = (plan: WebPlan) => api.post<BillingPrepare>('/billing/prepare', { plan })

export const confirmBilling = (authKey: string, customerKey: string) =>
  api.post<BillingStatus>('/billing/confirm', { authKey, customerKey })

// ── 토스페이 정기구독 ──
export const prepareTossPay = (plan: WebPlan) =>
  api.post<TossPayPrepare>('/billing/toss-pay/prepare', { plan, returnOrigin: window.location.origin })

/** 토스 앱 인증을 마치고 돌아온 뒤 — 서버가 인증 상태를 확인하고 첫 결제 */
export const confirmTossPay = () => api.post<BillingStatus>('/billing/toss-pay/confirm')

function useBillingAction(action: 'cancel' | 'resume') {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<BillingStatus>(`/billing/${action}`),
    onSuccess: (data) => qc.setQueryData(billingKeys.status, data),
  })
}

export const useCancelBilling = () => useBillingAction('cancel')
export const useResumeBilling = () => useBillingAction('resume')
