import type { Metadata } from 'next'
import { StartGoalView } from '@/features/goal-creation/ui/StartGoalView'

export const metadata: Metadata = { title: '새 목표' }

/** /goal/new?sourceGoalId=12 → 임시 목표를 AI 목표로 구체화 */
export default async function NewGoalPage({ searchParams }: { searchParams: Promise<{ sourceGoalId?: string }> }) {
  const raw = Number((await searchParams).sourceGoalId)
  return <StartGoalView sourceGoalId={Number.isInteger(raw) && raw > 0 ? raw : undefined} />
}
