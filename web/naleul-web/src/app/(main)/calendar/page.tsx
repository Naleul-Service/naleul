import { Suspense } from 'react'
import type { Metadata } from 'next'
import { Spinner } from '@/components/ui/Spinner'
import { CalendarView } from '@/features/timetable/ui/CalendarView'

export const metadata: Metadata = { title: '캘린더' }

/**
 * /calendar?view=day|week&date=YYYY-MM-DD
 * CalendarView 가 useSearchParams 를 쓰므로 Suspense 로 감싸요 (Next 16 규칙).
 */
export default function CalendarPage() {
  return (
    <Suspense
      fallback={
        <div className="grid min-h-[420px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      }
    >
      <CalendarView />
    </Suspense>
  )
}
