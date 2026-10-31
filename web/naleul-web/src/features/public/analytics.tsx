'use client'

import { useEffect, useRef, type ReactNode } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { usePageEngagement } from '@/components/analytics/usePageEngagement'
import { track, trackBeforeLeave } from '@/lib/client/analytics'
import type { LegalType, PublicLinkType } from '@/lib/client/publicEvents'

/**
 * 공개 페이지(/about · /privacy · /terms) 이벤트 — 노션 Tracking Plan 「공개 페이지」
 * 머리 · 바닥 부품은 세 페이지가 같이 써서, 지금 주소로 어느 페이지 이벤트인지 정해요.
 */
export type PublicPage = { page: 'about' } | { page: 'legal'; legalType: LegalType }

export function publicPageOf(pathname: string): PublicPage {
  if (pathname.startsWith('/privacy')) return { page: 'legal', legalType: 'privacy' }
  if (pathname.startsWith('/terms')) return { page: 'legal', legalType: 'terms' }
  if (pathname.startsWith('/refund')) return { page: 'legal', legalType: 'refund' }
  return { page: 'about' }
}

const baseProps = (p: PublicPage) => (p.page === 'legal' ? { legal_type: p.legalType } : {})

export function usePublicPage() {
  return publicPageOf(usePathname() ?? '/about')
}

/**
 * {page}.pageview + 첫 동작 · 머문 시간 (공통 훅)
 * /privacy ↔ /terms 처럼 같은 틀 안에서 주소만 바뀌어도 화면이 바뀐 거라, 주소마다 새로 그려서
 * 앞 화면의 page_exit 을 보내고 새 화면의 pageview · 첫 동작을 다시 재요.
 */
export function PublicPageview() {
  const p = usePublicPage()
  const key = p.page === 'legal' ? `legal:${p.legalType}` : 'about'
  return <PublicPageviewOnce key={key} p={p} />
}

function PublicPageviewOnce({ p }: { p: PublicPage }) {
  const props = baseProps(p)
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current) return
    sent.current = true
    track(p.page === 'about' ? 'about.pageview' : 'legal.pageview', props)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  usePageEngagement(p.page, props)
  return null
}

type CtaPosition = 'header' | 'hero' | 'bottom'

/** 시작하기 (둘러보기로 앱 열기) — {page}.start_click */
export function PublicStartLink({
  position,
  className,
  children,
}: {
  position: CtaPosition
  className?: string
  children: ReactNode
}) {
  const p = usePublicPage()
  return (
    <Link
      href="/"
      className={className}
      onClick={() =>
        track(p.page === 'about' ? 'about.start_click' : 'legal.start_click', {
          ...baseProps(p),
          cta_position: position,
        })
      }
    >
      {children}
    </Link>
  )
}

/** 로그인 — {page}.login_click */
export function PublicLoginLink({
  position,
  className,
  children,
}: {
  position: CtaPosition
  className?: string
  children: ReactNode
}) {
  const p = usePublicPage()
  return (
    <Link
      href="/login"
      className={className}
      onClick={() =>
        track(p.page === 'about' ? 'about.login_click' : 'legal.login_click', {
          ...baseProps(p),
          cta_position: position,
        })
      }
    >
      {children}
    </Link>
  )
}

/**
 * 페이지 안 링크 — {page}.link_click (link_type · link_position)
 * 바깥 주소(구글 · 메일)는 떠나기 직전이라 beacon 으로 보내요.
 */
export function PublicLink({
  href,
  type,
  position,
  className,
  children,
}: {
  href: string
  type: PublicLinkType
  position: 'header' | 'section' | 'body' | 'footer'
  className?: string
  children: ReactNode
}) {
  const p = usePublicPage()
  const external = /^(https?:|mailto:)/.test(href)
  const onClick = () => {
    const event = p.page === 'about' ? 'about.link_click' : 'legal.link_click'
    const props = { ...baseProps(p), link_type: type, link_position: position }
    if (external) void trackBeforeLeave(event, props)
    else track(event, props)
  }
  if (external)
    return (
      <a
        href={href}
        className={className}
        onClick={onClick}
        {...(href.startsWith('http') ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
      >
        {children}
      </a>
    )
  return (
    <Link href={href} className={className} onClick={onClick}>
      {children}
    </Link>
  )
}

const MARKS = [25, 50, 75, 100] as const

/** legal.scroll_depth — 본문을 25 / 50 / 75 / 100% 까지 내렸을 때 구간마다 한 번 */
export function LegalScrollDepth({ legalType }: { legalType: LegalType }) {
  useEffect(() => {
    const sent = new Set<number>()
    const onScroll = () => {
      const max = document.documentElement.scrollHeight - window.innerHeight
      if (max <= 0) return
      const pct = (window.scrollY / max) * 100
      for (const mark of MARKS) {
        if (pct >= mark - 1 && !sent.has(mark)) {
          sent.add(mark)
          track('legal.scroll_depth', { legal_type: legalType, scroll_percent: mark })
        }
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [legalType])
  return null
}

const URL_RE = /(https?:\/\/[^\s)]+)/g

function linkTypeOfUrl(url: string): PublicLinkType {
  if (url.includes('myaccount.google.com')) return 'google_permissions'
  if (url.includes('adssettings.google.com') || url.includes('aboutads.info')) return 'ad_settings'
  return 'google_policy'
}

/** 약관 평문 — 줄바꿈 그대로, 본문 속 주소는 눌리는 링크로 (legal.link_click, link_position=body) */
export function LegalBody({ text }: { text: string }) {
  const parts = text.split(URL_RE)
  return (
    <p className="text-ink-2 text-[14px] leading-7 break-words whitespace-pre-line">
      {parts.map((part, i) =>
        i % 2 === 1 && !part.includes('googleapis.com/auth') ? (
          <PublicLink
            key={i}
            href={part}
            type={linkTypeOfUrl(part)}
            position="body"
            className="text-text-brand underline underline-offset-2"
          >
            {part}
          </PublicLink>
        ) : (
          part
        )
      )}
    </p>
  )
}
