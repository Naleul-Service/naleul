import type { Metadata } from 'next'
import { GoalAddView } from '@/features/goal/ui/GoalAddView'

export const metadata: Metadata = { title: '목표 추가' }

/** /goal/add — 업무형 / 생활형 고르기 (?mode=record 면 업무형 폼, ?mode=manual 이면 생활형 직접 입력 폼을 바로 열어요) */
export default async function GoalAddPage({ searchParams }: { searchParams: Promise<{ mode?: string }> }) {
  const { mode } = await searchParams
  return <GoalAddView initialMode={mode === 'manual' || mode === 'record' ? mode : null} />
}
