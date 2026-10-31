'use client'

import { useEffect, useRef } from 'react'
import { usePathname } from 'next/navigation'
import { cn } from '@/lib/cn'
import { COMMON_EVENTS as CE, track, trackBeforeLeave } from '@/lib/client/analytics'
import { currentPath } from '@/lib/client/commonEvents'
import { ADSENSE_CLIENT } from '@/lib/ads'

/** 광고를 요청한 뒤 결과(채워짐/빈 광고)를 확인하기까지 기다리는 시간 */
const AD_RESULT_WAIT_MS = 3000

/**
 * 배너 광고 자리 (Google AdSense) — Free · 둘러보기 사용자에게만 보여요. Pro 는 광고 없음.
 *
 * 아직 광고 계정이 없어서 "자리"만 먼저 만들어 뒀어요. 환경변수만 넣으면 실제 광고가 나와요.
 *   NEXT_PUBLIC_ADSENSE_CLIENT = ca-pub-xxxxxxxxxxxxxxxx   (AdSense 게시자 ID)
 *   NEXT_PUBLIC_ADSENSE_SLOT_SIDEBAR = 1234567890            (광고 단위 ID)
 *   NEXT_PUBLIC_ADSENSE_TEST = true                         (테스트 광고 — 승인 전 · 개발 중)
 * 값이 없으면: 개발 환경에서는 점선 "광고 자리"를, 운영에서는 아무것도 안 보여줘요.
 */
/** 스크립트는 루트 layout 이 <head> 에 넣어요 — 여기서는 광고 단위만 */
const CLIENT = ADSENSE_CLIENT
const TEST = process.env.NEXT_PUBLIC_ADSENSE_TEST === 'true'
const SLOTS = {
  sidebar: process.env.NEXT_PUBLIC_ADSENSE_SLOT_SIDEBAR,
} as const

declare global {
  interface Window {
    adsbygoogle?: unknown[]
  }
}

export function AdSlot({
  placement,
  role,
  className,
}: {
  placement: keyof typeof SLOTS
  role?: string
  className?: string
}) {
  const slot = SLOTS[placement]
  const pushed = useRef(false)
  const show = role !== 'PRO' && role !== 'ADMIN'
  const configured = !!CLIENT && !!slot
  const boxRef = useRef<HTMLDivElement>(null)
  const insRef = useRef<HTMLModElement>(null)
  // push 가 실패했는지 (광고 차단기 등) — 화면엔 안 쓰고 분석에만 써서 state 가 아니라 ref
  const blocked = useRef(false)
  const pathname = usePathname()

  useEffect(() => {
    if (!show || !CLIENT || !slot || pushed.current) return
    pushed.current = true
    try {
      ;(window.adsbygoogle = window.adsbygoogle || []).push({})
    } catch {
      /* 광고 차단기 등 — 조용히 무시 */
      blocked.current = true
    }
  }, [show, slot])

  // ad.view — 광고 자리가 그려질 때 경로마다 1회 (좁은 화면에서 숨겨진 고정 사이드바 쪽은 빼요)
  // ad_state: rendered 광고 요청함 / not_configured 환경변수 없음(개발 점선 자리) / blocked 광고 차단·스크립트 실패 / unfilled 빈 광고
  useEffect(() => {
    if (!show || (!configured && process.env.NODE_ENV === 'production')) return
    const t = window.setTimeout(
      () => {
        if (!boxRef.current || boxRef.current.offsetParent === null) return
        const status = insRef.current?.getAttribute('data-ad-status')
        const scriptLoaded = !!(window.adsbygoogle as { loaded?: boolean } | undefined)?.loaded
        const state = !configured
          ? 'not_configured'
          : blocked.current || !scriptLoaded
            ? 'blocked'
            : status === 'unfilled'
              ? 'unfilled'
              : 'rendered'
        track(CE.adView, { ad_placement: placement, ad_state: state, current_path: currentPath() })
      },
      configured ? AD_RESULT_WAIT_MS : 0
    )
    return () => window.clearTimeout(t)
  }, [show, configured, placement, pathname])

  if (!show) return null

  if (!CLIENT || !slot) {
    if (process.env.NODE_ENV === 'production') return null
    return (
      <div
        ref={boxRef}
        className={cn(
          'border-border-subtle text-ink-4 grid h-[90px] place-items-center rounded-xl border border-dashed text-[12px]',
          className
        )}
      >
        광고 자리 (AdSense 설정 전)
      </div>
    )
  }

  return (
    <div ref={boxRef} className={cn('overflow-hidden rounded-xl', className)} aria-label="광고">
      <ins
        ref={insRef}
        className="adsbygoogle block"
        style={{ display: 'block', width: '100%', height: 90 }}
        data-ad-client={CLIENT}
        data-ad-slot={slot}
        data-ad-format="horizontal"
        data-full-width-responsive="false"
        {...(TEST ? { 'data-adtest': 'on' } : {})}
      />
      <p className="text-ink-4 mt-1 text-right text-[10px]">
        광고 · {/* Link 가 아니라 a 태그라 앱 전체를 새로 불러와요 → beacon 으로 보내요 */}
        <a
          href="/settings/membership"
          onClick={() =>
            void trackBeforeLeave(CE.adUpgradeClick, { ad_placement: placement, current_path: currentPath() })
          }
          className="hover:underline"
        >
          Pro는 광고 없이
        </a>
      </p>
    </div>
  )
}
