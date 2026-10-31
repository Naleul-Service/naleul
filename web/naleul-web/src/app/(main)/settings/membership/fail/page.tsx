import { Suspense } from 'react'
import type { Metadata } from 'next'
import { PageHeader } from '@/components/layout/PageHeader'
import { BillingFailView } from '@/features/billing/ui/BillingResultView'

export const metadata: Metadata = { title: '구독 결제' }

/** 토스 카드 등록 실패 · 취소 후 돌아오는 곳 (failUrl) */
export default function BillingFailPage() {
  return (
    <>
      <PageHeader title="멤버십" />
      <Suspense>
        <BillingFailView />
      </Suspense>
    </>
  )
}
