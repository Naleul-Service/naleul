'use client'

import { useEffect, useRef } from 'react'
import {
  onInteraction,
  track,
  trackBeforeLeave,
  type AnalyticsEventName,
  type EventProps,
} from '@/lib/client/analytics'

/**
 * 화면 첫 동작 · 머문 시간 — 노션 규칙 "화면마다 같은 방식으로 (공통 훅 하나로)".
 *   usePageEngagement('home', { view_date: today })
 *
 *  - {page}.first_interaction : 진입 후 처음 누른 버튼·체크·링크 (pageview 마다 1회)
 *      first_action(그 이벤트 이름) · seconds_to_first_action
 *  - {page}.page_exit : 떠날 때 dwell_seconds(숨긴 시간 제외) · interaction_count · exit_type · next_path
 *      exit_type = navigate(다른 메뉴로) / hidden(다른 탭·앱으로) / unload(창 닫기·새로고침)
 *      숨김에서 돌아오면 이어서 계산해요. 떠나는 순간 보내는 거라 beacon 으로 보내요.
 *
 * "동작"은 '{page}.' 로 시작하는 이벤트 중 보기 · 결과 · 실패가 아닌 것이에요 (analytics.ts 의 PASSIVE 참고).
 * enabled=false 면 아무것도 안 해요 (예: 데이터 로딩 중 — pageview 와 같은 시점에 시작하려고).
 */
export function usePageEngagement(page: string, props: EventProps = {}, enabled = true) {
  const propsRef = useRef(props)
  useEffect(() => {
    propsRef.current = props
  })

  useEffect(() => {
    if (!enabled) return
    const startedAt = Date.now()
    const myPath = window.location.pathname
    let visibleSince: number | null = document.visibilityState === 'visible' ? startedAt : null
    let dwellMs = 0
    let count = 0
    let firstSent = false
    let ended = false

    const dwellSeconds = () => Math.round((dwellMs + (visibleSince !== null ? Date.now() - visibleSince : 0)) / 1000)
    const ev = (name: string) => `${page}.${name}` as AnalyticsEventName

    const offInteraction = onInteraction((event) => {
      if (!event.startsWith(`${page}.`)) return
      count++
      if (firstSent) return
      firstSent = true
      track(ev('first_interaction'), {
        ...propsRef.current,
        first_action: event,
        seconds_to_first_action: Math.round((Date.now() - startedAt) / 1000),
      })
    })

    const sendExit = (exitType: 'navigate' | 'hidden' | 'unload', nextPath?: string) =>
      void trackBeforeLeave(ev('page_exit'), {
        ...propsRef.current,
        dwell_seconds: dwellSeconds(),
        interaction_count: count,
        exit_type: exitType,
        next_path: nextPath,
      })

    const onVisibility = () => {
      if (ended) return
      if (document.visibilityState === 'hidden') {
        if (visibleSince !== null) dwellMs += Date.now() - visibleSince
        visibleSince = null
        sendExit('hidden')
      } else {
        visibleSince = Date.now()
      }
    }
    const onPageHide = () => {
      if (ended) return
      ended = true
      // 이미 숨김으로 보냈으면 (탭 전환 후 닫기) 또 보내지 않아요
      if (visibleSince !== null) sendExit('unload')
    }
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('pagehide', onPageHide)

    return () => {
      offInteraction()
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('pagehide', onPageHide)
      if (ended) return
      ended = true
      // 다른 메뉴로 이동하며 화면이 사라질 때 (주소는 이미 바뀌어 있어요)
      const next = window.location.pathname
      sendExit('navigate', next !== myPath ? next : undefined)
    }
  }, [page, enabled])
}
