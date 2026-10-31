import type { Metadata } from 'next'
import { LegalPage } from '@/features/public/LegalPage'

export const metadata: Metadata = {
  title: '환불 정책',
  description: '나를(Naleul) Pro 구독의 결제 · 해지 · 환불 기준',
}

/** /refund — 로그인 없이 보는 환불 정책 (토스페이먼츠 심사의 환불 정책 URL) */
export default function RefundPage() {
  return <LegalPage type="refund" />
}
