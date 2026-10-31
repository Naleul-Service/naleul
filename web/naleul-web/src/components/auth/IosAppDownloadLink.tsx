'use client'

import { EVENTS, trackBeforeLeave } from '@/lib/client/analytics'

const IOS_APP_URL = 'https://apps.apple.com/kr/app/%EB%82%98%EB%A5%BC-naleul/id6780154075'

/** 로그인 페이지의 'iOS 앱 다운로드' 링크 (새 탭으로 App Store) */
export function IosAppDownloadLink({ className }: { className?: string }) {
  return (
    <a
      href={IOS_APP_URL}
      target="_blank"
      rel="noreferrer"
      onClick={() => void trackBeforeLeave(EVENTS.loginIosAppDownloadClick)}
      className={className}
    >
      iOS 앱 다운로드
    </a>
  )
}
