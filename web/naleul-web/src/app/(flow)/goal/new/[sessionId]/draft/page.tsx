import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { DraftScreen } from '@/features/goal-creation/ui/draft/DraftScreen'
import { parseSessionId } from '@/features/goal-creation/parseSessionId'

export const metadata: Metadata = { title: '초안 검토' }

export default async function DraftPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const id = parseSessionId((await params).sessionId)
  if (!id) notFound()
  return <DraftScreen sessionId={id} />
}
