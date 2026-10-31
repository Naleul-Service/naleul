import type { Metadata } from 'next'
import { LegalPage } from '@/features/public/LegalPage'

export const metadata: Metadata = {
  title: '개인정보처리방침',
  description: '나를(Naleul)이 수집하는 개인정보와 구글 캘린더 데이터를 다루는 방식',
}

export default function PrivacyPage() {
  return <LegalPage type="privacy" />
}
