import type { Metadata, Viewport } from 'next'
import { THEME_SCRIPT } from '@/features/theme/theme'
import { ADSENSE_CLIENT } from '@/lib/ads'
import { Providers } from './providers'
import './globals.css'

export const metadata: Metadata = {
  title: { default: '나를', template: '%s · 나를' },
  description: '목표를 세우고, 하루를 계획하고, 나를 알아가는 자기관리 앱',
  // AdSense 사이트 소유권 확인
  other: { 'google-adsense-account': ADSENSE_CLIENT },
}

export const viewport: Viewport = {
  // 브라우저 주소창 색 — 라이트/다크 (globals.css 의 canvas 와 같은 색)
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#121820' },
  ],
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // data-theme 은 THEME_SCRIPT 가 React 보다 먼저 붙여서 서버 HTML 과 달라요 → 경고만 끕니다
    <html lang="ko" suppressHydrationWarning>
      <head>
        {/* 다크 모드 깜빡임 방지: 첫 화면을 그리기 전에 data-theme 을 정해요 */}
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
        {/* Pretendard (iOS 앱과 같은 폰트). 사용하는 글자만 내려받는 dynamic-subset 버전 */}
        <link
          rel="stylesheet"
          crossOrigin="anonymous"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css"
        />
        {/* Google AdSense — 사이트 소유권 확인 · 승인 심사를 위해 모든 페이지 <head> 에 (광고는 AdSlot 자리에만 나와요) */}
        <script
          async
          src={`https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`}
          crossOrigin="anonymous"
        />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
