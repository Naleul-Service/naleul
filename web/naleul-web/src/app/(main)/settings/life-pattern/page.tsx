import type { Metadata } from 'next'
import { LifePatternView } from '@/features/life-pattern/ui/LifePatternView'

export const metadata: Metadata = { title: '기본 생활 패턴' }

/** 고정 시간(수면·점심·저녁·직접 추가한 시간) 추가·수정·삭제 */
export default function LifePatternSettingsPage() {
  return <LifePatternView />
}
