import { Suspense } from 'react'
import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { Spinner } from '@/components/ui/Spinner'
import { GoalDetailView } from '@/features/goal/ui/GoalDetailView'

export const metadata: Metadata = { title: '목표' }

/**
 * /goal/12?created=1 — AI 목표를 막 만든 직후 ("이어서 TimeTable 에 배치할까요?" 카드)
 * /goal/12?tab=stats — 업무형 목표의 통계 탭
 * 업무형 탭이 useSearchParams 를 쓰므로 Suspense 로 감싸요 (Next 16 규칙).
 */
export default async function GoalDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ goalId: string }>
  searchParams: Promise<{ created?: string }>
}) {
  const id = Number((await params).goalId)
  if (!Number.isInteger(id) || id <= 0) notFound()
  const { created } = await searchParams
  return (
    <Suspense
      fallback={
        <div className="grid min-h-[60vh] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      }
    >
      <GoalDetailView key={id} goalId={id} justCreated={created === '1'} />
    </Suspense>
  )
}
