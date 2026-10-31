import type { Metadata } from 'next'
import { cookies } from 'next/headers'
import { isPremiumRole } from '@/features/account/settingsRole'
import { NotificationSettingsView } from '@/features/notification-settings/ui/NotificationSettingsView'
import { COOKIE } from '@/lib/server/session'

export const metadata: Metadata = { title: '알림 설정' }

/**
 * Task·미션 푸시 알림 on/off (iOS PushNotificationSettingView)
 * BM(2026-10): 알림은 Free 도 쓸 수 있어요 (AI 만 횟수 제한) → 잠금 없음
 */
export default async function NotificationSettingsPage() {
  const store = await cookies()
  const role = store.get(COOKIE.userRole)?.value
  return (
    <NotificationSettingsView locked={false} isPremium={isPremiumRole(role, store.get(COOKIE.adminViewPlan)?.value)} />
  )
}
