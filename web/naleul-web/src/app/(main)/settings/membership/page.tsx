import type { Metadata } from 'next'
import { PageHeader } from '@/components/layout/PageHeader'
import { MembershipView } from '@/features/billing/ui/MembershipView'

export const metadata: Metadata = { title: '멤버십' }

/** 웹 Pro 구독 (토스페이먼츠 자동결제) */
export default function MembershipPage() {
  return (
    <>
      <PageHeader title="멤버십" />
      <MembershipView />
    </>
  )
}
