export type WebPlan = 'PRO_MONTHLY' | 'PRO_YEARLY'
export type WebSubscriptionStatus = 'PENDING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'EXPIRED'
/** 구독을 결제하는 곳 — 둘 다 매달 · 매년 자동결제, 해지하면 기간 끝까지 Pro */
export type WebPayProvider = 'TOSS_PAYMENTS_CARD' | 'TOSS_PAY'
/** 화면에서 고르는 결제 방식 (분석 pay_method 값과 같아요) */
export type PayMethod = 'toss_pay' | 'card_subscription'

/** GET /api/billing (백엔드 GET /api/v1/web-billing) */
export interface BillingStatus {
  /** 서버에 토스페이먼츠 키가 있는지 — 없으면 카드 정기구독 "준비 중" */
  configured: boolean
  /** 서버에 토스페이 키가 있는지 — 없으면 토스페이 정기구독 "준비 중" */
  tossPayConfigured: boolean
  /** 테스트 키 — 실제 돈이 나가지 않아요 */
  testMode: boolean
  premium: boolean
  premiumUntil: string | null
  /** 웹이 아닌 경로(iOS 앱 구독 등)로 Pro */
  otherPremium: boolean
  plans: { plan: WebPlan; name: string; amount: number; months: number }[]
  subscription: {
    plan: WebPlan
    provider: WebPayProvider
    status: WebSubscriptionStatus
    currentPeriodEnd: string | null
    nextChargeAt: string | null
    /** 카드사 (토스페이 토스머니면 은행 이름 또는 '토스머니') */
    cardCompany: string | null
    cardNumber: string | null
    failedAttempts: number
    testMode: boolean
  } | null
  payments: {
    plan: WebPlan
    amount: number
    status: 'DONE' | 'FAILED'
    at: string
    failureMessage: string | null
    testMode: boolean
  }[]
}

export interface BillingPrepare {
  customerKey: string
  orderName: string
  amount: number
  customerName: string | null
  testMode: boolean
}

/** 토스페이 정기구독 — 웹은 checkoutUri 로 이동해요 (PC 는 QR, 모바일은 토스 앱) */
export interface TossPayPrepare {
  checkoutUri: string
  orderName: string
  amount: number
  testMode: boolean
}
