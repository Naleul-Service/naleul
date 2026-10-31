import type { BillingStatus, PayMethod, WebPlan } from './types'

/**
 * 멤버십 이벤트 공통 도구 (노션 Tracking Plan 「설정 > 멤버십」, membership.*).
 * 결제 키 · 카드 정보 · customerKey 같은 민감 값은 절대 속성에 넣지 않아요.
 */

export type MembershipState = 'free' | 'active' | 'canceled' | 'past_due' | 'other_premium'

/** 화면에 보이는 웹 구독 (해지 · 결제 실패 포함, 결제 전 · 종료는 없음으로) */
export function webSubscription(data: BillingStatus) {
  const sub = data.subscription
  return sub && sub.status !== 'EXPIRED' && sub.status !== 'PENDING' ? sub : null
}

export function membershipStateOf(data: BillingStatus): MembershipState {
  const sub = webSubscription(data)
  if (sub) return sub.status === 'CANCELED' ? 'canceled' : sub.status === 'PAST_DUE' ? 'past_due' : 'active'
  return data.otherPremium ? 'other_premium' : 'free'
}

/** Pro 이용 기간이 남은 날 수 (Pro 가 아니면 undefined). 렌더 중이 아니라 이벤트를 보낼 때 불러요 */
export function daysLeftOf(data: BillingStatus): number | undefined {
  const end = webSubscription(data)?.currentPeriodEnd ?? (data.premium ? data.premiumUntil : null)
  if (!end) return undefined
  const ms = new Date(end).getTime() - Date.now()
  if (Number.isNaN(ms)) return undefined
  return Math.max(0, Math.ceil(ms / 86_400_000))
}

// ── 카드 등록 창 → 성공 · 실패 페이지 사이에 고른 상품을 기억 (상품 이름만, 민감 값 아님) ──
const PLAN_KEY = 'naleul.billing.plan'

export function rememberPlan(plan: WebPlan) {
  try {
    sessionStorage.setItem(PLAN_KEY, plan)
  } catch {
    /* 저장소를 못 쓰면 plan 없이 보내요 */
  }
}

export function rememberedPlan(): WebPlan | undefined {
  try {
    const v = sessionStorage.getItem(PLAN_KEY)
    return v === 'PRO_MONTHLY' || v === 'PRO_YEARLY' ? v : undefined
  } catch {
    return undefined
  }
}

const METHOD_KEY = 'naleul.billing.pay_method'

/** 결제 창 → 성공 · 실패 페이지 사이에 고른 결제 방식을 기억 (pay_method 속성용) */
export function rememberPayMethod(method: PayMethod) {
  try {
    sessionStorage.setItem(METHOD_KEY, method)
  } catch {
    /* 저장소를 못 쓰면 주소의 method 로만 알아요 */
  }
}

export function rememberedPayMethod(): PayMethod | undefined {
  try {
    const v = sessionStorage.getItem(METHOD_KEY)
    return v === 'toss_pay' || v === 'card_subscription' ? v : undefined
  } catch {
    return undefined
  }
}

/** 구독의 결제하는 곳 → pay_method 값 */
export const payMethodOf = (provider: 'TOSS_PAYMENTS_CARD' | 'TOSS_PAY' | undefined): PayMethod | undefined =>
  provider === 'TOSS_PAY' ? 'toss_pay' : provider === 'TOSS_PAYMENTS_CARD' ? 'card_subscription' : undefined

/** 문자열 → 짧은 해시 (돌아온 주소를 그대로 저장하지 않으려고) */
function hash(s: string) {
  let h = 5381
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0
  return (h >>> 0).toString(36)
}

/**
 * 같은 결제 결과 주소에서 이벤트를 한 번만 (새로고침 · 뒤로 가기로 다시 들어와도 다시 안 보냄).
 * key 에는 주소(쿼리)를 넘기고, 저장은 해시만 해요. 처음이면 true.
 */
export function firstTimeFor(kind: string, key: string): boolean {
  const k = `naleul.billing.sent.${kind}.${hash(key)}`
  try {
    if (sessionStorage.getItem(k)) return false
    sessionStorage.setItem(k, '1')
  } catch {
    /* 저장소를 못 쓰면 컴포넌트의 ref 로만 막아요 */
  }
  return true
}
