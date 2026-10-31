import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { GeneratingScreen } from '@/features/goal-creation/ui/generating/GeneratingScreen'
import { parseSessionId } from '@/features/goal-creation/parseSessionId'
import type { GenerateSource } from '@/features/goal-creation/routes'

const SOURCES: GenerateSource[] = [
  'review',
  'regen',
  'quick_fix',
  'style_change',
  'intensity_change',
  'retry',
  'resume',
]

export const metadata: Metadata = { title: '계획 만드는 중' }

export default async function GeneratingPage({
  params,
  searchParams,
}: {
  params: Promise<{ sessionId: string }>
  searchParams: Promise<{ draftId?: string; source?: string }>
}) {
  const id = parseSessionId((await params).sessionId)
  if (!id) notFound()
  const query = await searchParams
  const draftId = parseSessionId(query.draftId)
  const source = SOURCES.find((s) => s === query.source) ?? 'resume'
  return <GeneratingScreen sessionId={id} draftId={draftId} source={source} />
}
