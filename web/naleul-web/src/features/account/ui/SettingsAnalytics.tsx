'use client'

import { useEffect, useRef, type ReactNode } from 'react'
import Link from 'next/link'
import { track } from '@/lib/client/analytics'
import { readThemePref } from '@/features/theme/theme'

import type { SettingsDestination } from '@/features/account/settingsRole'

/** settings.pageview — /settings 를 그린 직후 한 번 */
export function SettingsPageview({ isPremium }: { isPremium: boolean }) {
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current) return
    sent.current = true
    track('settings.pageview', { is_premium: isPremium, theme_pref: readThemePref() })
  }, [isPremium])
  return null
}

/** 설정 목록의 한 줄 — 누르면 settings.menu_click 을 보내고 이동해요 */
export function SettingsMenuLink({
  href,
  destination,
  isPremium,
  className,
  children,
}: {
  href: string
  destination: SettingsDestination
  isPremium: boolean
  className?: string
  children: ReactNode
}) {
  return (
    <Link
      href={href}
      className={className}
      onClick={() => track('settings.menu_click', { destination, is_premium: isPremium })}
    >
      {children}
    </Link>
  )
}
