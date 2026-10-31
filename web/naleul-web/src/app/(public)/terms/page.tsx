import type { Metadata } from 'next'
import { LegalPage } from '@/features/public/LegalPage'

export const metadata: Metadata = { title: '서비스 이용약관' }

export default function TermsPage() {
  return <LegalPage type="terms" />
}
