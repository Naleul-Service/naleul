'use client'

import Link from 'next/link'
import { track } from '@/lib/client/analytics'

const LINKS = [
  { href: '/about', label: '서비스 소개', type: 'about' },
  { href: '/terms', label: '이용약관', type: 'terms' },
  { href: '/privacy', label: '개인정보처리방침', type: 'privacy' },
] as const

/** 로그인 화면 아래 공개 페이지 링크 — login.legal_link_click (link_type) */
export function LoginLegalLinks() {
  return (
    <nav className="text-ink-3 mt-10 flex justify-center gap-4 text-xs">
      {LINKS.map((l) => (
        <Link
          key={l.type}
          href={l.href}
          className={`hover:text-ink-2 transition ${l.type === 'privacy' ? 'font-semibold' : ''}`}
          onClick={() => track('login.legal_link_click', { link_type: l.type })}
        >
          {l.label}
        </Link>
      ))}
    </nav>
  )
}
