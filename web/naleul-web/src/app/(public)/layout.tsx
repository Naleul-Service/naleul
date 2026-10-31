import { PublicFooter, PublicHeader } from '@/features/public/PublicChrome'
import { PublicPageview } from '@/features/public/analytics'

/**
 * 로그인 없이 보는 공개 페이지 — /about(서비스 소개) · /privacy · /terms
 * 구글 OAuth 앱 인증에 필요한 "공개 홈페이지 · 개인정보처리방침 · 이용약관" 주소예요. (proxy.ts PUBLIC_PATHS)
 * 앱 틀(사이드바 · 둘러보기 토큰) 없이 그려요.
 */
export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-canvas text-ink min-h-dvh">
      <PublicPageview />
      <PublicHeader />
      <main>{children}</main>
      <PublicFooter />
    </div>
  )
}
