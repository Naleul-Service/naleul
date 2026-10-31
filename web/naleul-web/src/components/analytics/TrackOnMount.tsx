'use client'

import { useEffect, useRef } from 'react'
import { track, type AnalyticsEventName, type EventProps } from '@/lib/client/analytics'

/**
 * 서버 컴포넌트 화면에서 "화면이 뜬 직후" 이벤트를 보낼 때 써요. 화면에는 아무것도 그리지 않아요.
 *   <TrackOnMount event={EVENTS.loginPageview} props={{ entry_source: 'direct' }} />
 *
 * 개발 모드(StrictMode)에서는 effect 가 두 번 돌 수 있어서 ref 로 한 번만 보내요.
 */
export function TrackOnMount({ event, props }: { event: AnalyticsEventName; props?: EventProps }) {
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current) return
    sent.current = true
    track(event, props)
    // 첫 렌더 한 번만 보내요 (props 가 바뀌어도 다시 보내지 않아요)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return null
}
