import { Suspense } from 'react'
import type { Metadata } from 'next'
import { Spinner } from '@/components/ui/Spinner'
import { ReflectionsView } from '@/features/reflection/ui/ReflectionsView'

export const metadata: Metadata = { title: '회고 모아보기' }

/**
 * /reflections?goal=12
 * 목표형 "오늘 기록"과 업무형 회고 글을 날짜별로 한곳에서. ReflectionsView 가 useSearchParams 를 써서 Suspense 로 감싸요.
 */
export default function ReflectionsPage() {
  return (
    <Suspense
      fallback={
        <div className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      }
    >
      <ReflectionsView />
    </Suspense>
  )
}
