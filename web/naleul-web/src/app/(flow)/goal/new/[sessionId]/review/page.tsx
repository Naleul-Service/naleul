import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { ReviewScreen } from '@/features/goal-creation/ui/review/ReviewScreen'
import { parseSessionId } from '@/features/goal-creation/parseSessionId'

export const metadata: Metadata = { title: '정리 확인' }

export default async function ReviewPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const id = parseSessionId((await params).sessionId)
  if (!id) notFound()
  return <ReviewScreen sessionId={id} />
}
