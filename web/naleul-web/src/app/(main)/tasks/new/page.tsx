import type { Metadata } from 'next'
import { TaskAddView } from '@/features/brain-dump/ui/TaskAddView'

export const metadata: Metadata = { title: 'Task 추가' }

/** /tasks/new?mode=manual — 바로 "직접 추가하기" · ?mode=log — 바로 "한 일 기록" */
export default async function TaskAddPage({ searchParams }: { searchParams: Promise<{ mode?: string }> }) {
  const { mode } = await searchParams
  return <TaskAddView initialMode={mode === 'manual' || mode === 'log' ? mode : 'ai'} />
}
