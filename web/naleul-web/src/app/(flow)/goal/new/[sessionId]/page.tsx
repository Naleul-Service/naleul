import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { SessionScreen } from '@/features/goal-creation/ui/SessionScreen'
import { parseSessionId } from '@/features/goal-creation/parseSessionId'

export const metadata: Metadata = { title: '새 목표' }

export default async function GoalSessionPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const id = parseSessionId((await params).sessionId)
  if (!id) notFound()
  return <SessionScreen sessionId={id} />
}
