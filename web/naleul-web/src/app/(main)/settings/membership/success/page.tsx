import { Suspense } from 'react'
import type { Metadata } from 'next'
import { PageHeader } from '@/components/layout/PageHeader'
import { BillingSuccessView } from '@/features/billing/ui/BillingResultView'

export const metadata: Metadata = { title: '구독 결제' }

/** 토스 카드 등록 성공 후 돌아오는 곳 (successUrl) */
export default function BillingSuccessPage() {
  return (
    <>
      <PageHeader title="멤버십" />
      {/* useSearchParams 를 쓰는 화면은 Suspense 안에 둬요 */}
      <Suspense>
        <BillingSuccessView />
      </Suspense>
    </>
  )
}
