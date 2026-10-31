import type { WebPayProvider, WebPlan, WebSubscriptionStatus } from './types'

export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

/** "2026-11-09T18:00:00" → "2026년 11월 9일" */
export function longDate(iso: string | null) {
  if (!iso) return ''
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return `${y}년 ${m}월 ${d}일`
}

export const PLAN_LABEL: Record<WebPlan, string> = { PRO_MONTHLY: '월간', PRO_YEARLY: '연간' }

export const STATUS_LABEL: Record<WebSubscriptionStatus, string> = {
  PENDING: '결제 전',
  ACTIVE: '이용 중',
  PAST_DUE: '결제 실패 · 다시 시도 중',
  CANCELED: '해지 예정',
  EXPIRED: '종료',
}

export const PROVIDER_LABEL: Record<WebPayProvider, string> = {
  TOSS_PAYMENTS_CARD: '카드 정기구독',
  TOSS_PAY: '토스페이 정기구독',
}
