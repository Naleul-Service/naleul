'use client'

import { useEffect } from 'react'
import Link from 'next/link'
import { COMMON_EVENTS as CE, track } from '@/lib/client/analytics'
import { currentPath, referrerType } from '@/lib/client/commonEvents'

/**
 * 404 화면 이벤트 (not-found.tsx 는 서버 컴포넌트라 브라우저 쪽 부품으로 나눴어요).
 * 삭제된 목표 상세 링크 · 오래된 북마크가 주 원인 → requested_path · referrer_type 으로 확인해요.
 */
export function NotFoundTracker() {
  useEffect(() => {
    track(CE.notFoundView, { requested_path: currentPath(), referrer_type: referrerType() })
  }, [])
  return null
}

export function NotFoundHomeLink({ className }: { className?: string }) {
  return (
    <Link href="/" onClick={() => track(CE.notFoundHomeClick, { requested_path: currentPath() })} className={className}>
      홈으로
    </Link>
  )
}
