import type { Metadata } from 'next'
import { GoalListView } from '@/features/goal/ui/GoalListView'

export const metadata: Metadata = { title: '목표' }

export default function GoalPage() {
  return <GoalListView />
}
