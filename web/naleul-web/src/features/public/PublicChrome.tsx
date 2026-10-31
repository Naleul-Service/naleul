import { Logo } from '@/components/brand/Logo'
import { BUSINESS, businessLines } from '@/lib/business'
import { PublicLink, PublicLoginLink, PublicStartLink } from './analytics'

export const CONTACT_EMAIL = BUSINESS.email

/** 공개 페이지 위쪽 바 — 로고(소개로) · 로그인 · 시작하기 */
export function PublicHeader() {
  return (
    <header className="border-line bg-canvas/90 sticky top-0 z-10 border-b backdrop-blur">
      <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-4 sm:px-6">
        <PublicLink href="/about" type="about" position="header" className="flex items-center">
          <Logo />
        </PublicLink>
        <nav className="flex items-center gap-2">
          <PublicLoginLink
            position="header"
            className="text-ink-2 hover:text-ink rounded-xl px-3 py-2 text-[14px] font-medium transition"
          >
            로그인
          </PublicLoginLink>
          <PublicStartLink
            position="header"
            className="bg-brand hover:bg-brand-strong text-on-ink rounded-xl px-4 py-2 text-[14px] font-semibold transition"
          >
            시작하기
          </PublicStartLink>
        </nav>
      </div>
    </header>
  )
}

/** 공개 페이지 바닥 — 소개 · 이용약관 · 개인정보처리방침 · 문의 */
export function PublicFooter() {
  const link = 'text-ink-2 hover:text-ink transition'
  return (
    <footer className="border-line mt-20 border-t">
      <div className="text-ink-3 mx-auto flex max-w-5xl flex-col gap-4 px-4 py-10 text-[13px] sm:px-6">
        <nav className="flex flex-wrap gap-x-5 gap-y-2">
          <PublicLink href="/about" type="about" position="footer" className={link}>
            서비스 소개
          </PublicLink>
          <PublicLink href="/terms" type="terms" position="footer" className={link}>
            이용약관
          </PublicLink>
          <PublicLink href="/refund" type="refund" position="footer" className={link}>
            환불 정책
          </PublicLink>
          <PublicLink href="/privacy" type="privacy" position="footer" className={`${link} font-semibold`}>
            개인정보처리방침
          </PublicLink>
          <PublicLink href={`mailto:${CONTACT_EMAIL}`} type="contact" position="footer" className={link}>
            문의하기
          </PublicLink>
        </nav>
        {/* 사업자 정보 (전자상거래법 · PG 심사) */}
        <p className="leading-6">
          {businessLines().map((l) => (
            <span key={l} className="block">
              {l}
            </span>
          ))}
          <span className="mt-1 block">© 2026 나를(Naleul)</span>
        </p>
      </div>
    </footer>
  )
}
