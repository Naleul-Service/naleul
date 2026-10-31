'use client'

import { useEffect } from 'react'
import { EVENTS, track } from '@/lib/client/analytics'
import { takeGuestStartedCookie } from '@/lib/loginAnalytics'

const TRACKED_KEY = 'naleul.guest_session_tracked' // sessionStorage: 이 탭에서 보냈는지
const VISITED_KEY = 'naleul.has_visited' // localStorage: 이 브라우저에 전에 온 적 있는지

/**
 * guest.session_start — 로그인 안 한 상태로 둘러보기를 시작할 때. 화면에는 아무것도 그리지 않아요.
 *
 *  - 브라우저 탭마다 한 번 보내요 (sessionStorage 표시) → 재방문한 둘러보기 사용자도 잡혀요
 *  - is_new_guest: 둘러보기 토큰을 "방금" 발급받았으면 true (proxy.ts 의 1회용 쿠키), 다시 온 거면 false
 *    → 처음 온 사람만 보려면 is_new_guest = true 로 필터해요
 *
 * 토큰을 새로 받는다고 항상 새 사람은 아니에요 (로그아웃 직후, 둘러보기 토큰 약 30일 만료 후).
 * 그래서 이 브라우저에 전에 온 적이 있으면(localStorage) is_new_guest = false 로 봐요.
 */
export function GuestSessionTracker({ isGuest }: { isGuest: boolean }) {
  useEffect(() => {
    if (!isGuest) {
      // 로그인 사용자도 "온 적 있음"으로 남겨요 → 나중에 로그아웃하고 둘러봐도 새 사람으로 안 세요
      try {
        localStorage.setItem(VISITED_KEY, '1')
      } catch {
        // 저장이 막혀도 무시해요
      }
      return
    }
    const tokenJustIssued = takeGuestStartedCookie()
    let alreadyTracked = false
    let visitedBefore = false
    try {
      alreadyTracked = sessionStorage.getItem(TRACKED_KEY) === '1'
      sessionStorage.setItem(TRACKED_KEY, '1')
      visitedBefore = localStorage.getItem(VISITED_KEY) === '1'
      localStorage.setItem(VISITED_KEY, '1')
    } catch {
      // 시크릿 모드 등에서 막히면 표시 없이 진행 (중복될 수는 있어요)
    }
    const isNewGuest = tokenJustIssued && !visitedBefore
    // 방금 토큰을 받은 경우는 같은 탭이라도 꼭 보내요 (로그아웃 후 둘러보기로 돌아온 경우 등)
    if (alreadyTracked && !tokenJustIssued) return
    track(EVENTS.guestSessionStart, { is_new_guest: isNewGuest })
  }, [isGuest])
  return null
}
