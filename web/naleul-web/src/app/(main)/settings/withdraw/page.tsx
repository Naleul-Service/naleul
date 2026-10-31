import type { Metadata } from 'next'
import { WithdrawView } from '@/features/account/ui/WithdrawView'

export const metadata: Metadata = { title: '회원 탈퇴' }

export default function WithdrawPage() {
  return <WithdrawView />
}
