'use client'

import { useEffect, useRef, type ReactNode } from 'react'
import Link from 'next/link'
import { track } from '@/lib/client/analytics'
import type { TermsType } from './content'

/** terms.pageview — /settings/terms 를 그린 직후 한 번 */
export function TermsPageview() {
  const sent = useRef(false)
  useEffect(() => {
    if (sent.current) return
    sent.current = true
    track('terms.pageview')
  }, [])
  return null
}

/** 약관 목록의 한 줄 → 상세 (terms.item_click) */
export function TermsItemLink({
  type,
  href,
  className,
  children,
}: {
  type: TermsType
  href: string
  className?: string
  children: ReactNode
}) {
  return (
    <Link href={href} className={className} onClick={() => track('terms.item_click', { terms_type: type })}>
      {children}
    </Link>
  )
}

const MARKS = [25, 50, 75, 100] as const

/** terms.detail_scroll_depth — 약관 본문을 25 / 50 / 75 / 100% 까지 내렸을 때 구간마다 한 번 */
export function TermsScrollDepth({ type }: { type: TermsType }) {
  useEffect(() => {
    const sent = new Set<number>()
    const onScroll = () => {
      const max = document.documentElement.scrollHeight - window.innerHeight
      if (max <= 0) return
      const pct = (window.scrollY / max) * 100
      for (const mark of MARKS) {
        if (pct >= mark - 1 && !sent.has(mark)) {
          sent.add(mark)
          track('terms.detail_scroll_depth', { terms_type: type, scroll_percent: mark })
        }
      }
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [type])
  return null
}
