import type { Metadata } from 'next'
import { PageHeader } from '@/components/layout/PageHeader'
import { CalendarImportView } from '@/features/external-calendar/ui/CalendarImportView'

export const metadata: Metadata = { title: '외부 캘린더 가져오기' }

/** 구글 · 노션 · 아이폰 캘린더 일정을 골라서 Task 로 가져오기 (버튼 눌러 한 번) */
export default function CalendarImportPage() {
  return (
    <>
      <PageHeader title="외부 캘린더 가져오기" />
      <CalendarImportView />
    </>
  )
}
