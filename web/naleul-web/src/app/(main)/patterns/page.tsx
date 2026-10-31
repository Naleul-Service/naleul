import { Suspense } from 'react'
import type { Metadata } from 'next'
import { Spinner } from '@/components/ui/Spinner'
import { PatternView } from '@/features/pattern/ui/PatternView'

export const metadata: Metadata = { title: '나의 패턴' }

/**
 * /patterns?period=RECENT_4W|ALL
 * PatternView 가 useSearchParams 를 쓰므로 Suspense 로 감싸요 (Next 16 규칙).
 */
export default function PatternsPage() {
  return (
    <Suspense
      fallback={
        <div className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      }
    >
      <PatternView />
    </Suspense>
  )
}
