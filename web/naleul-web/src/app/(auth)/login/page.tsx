import type { Metadata } from 'next'
import { Logo } from '@/components/brand/Logo'
import { safeRedirectPath } from '@/lib/redirectPath'
import { KakaoLoginButton } from '@/components/auth/KakaoLoginButton'
import { AppleLoginButton } from '@/components/auth/AppleLoginButton'
import { IosAppDownloadLink } from '@/components/auth/IosAppDownloadLink'
import { loginErrorMessage } from '@/components/auth/kakaoAuth'
import { loginErrorCodeOf } from '@/lib/loginAnalytics'
import { TrackOnMount } from '@/components/analytics/TrackOnMount'
import { LoginLegalLinks } from '@/components/auth/LoginLegalLinks'

export const metadata: Metadata = { title: '로그인' }

// 평소엔 로그인 모달(LoginRequiredDialog)로 로그인해요.
// 이 페이지는 주소로 직접 들어오거나, 둘러보기 토큰을 못 받았을 때(proxy.ts)만 보여요.

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ redirect?: string; error?: string; from?: string }>
}) {
  const { redirect, error, from } = await searchParams
  const next = safeRedirectPath(redirect)
  const errorMessage = loginErrorMessage(error)

  return (
    <main className="grid min-h-dvh place-items-center px-4 py-12">
      {/* 서버 컴포넌트라 analytics.ts(브라우저 SDK)를 import 하지 않고 이벤트 이름을 문자열로 넘겨요 (타입은 그대로 검사돼요) */}
      <TrackOnMount
        event="login.pageview"
        props={{ redirect_path: next, entry_source: from ?? 'direct', login_error_code: loginErrorCodeOf(error) }}
      />
      <div className="w-full max-w-[400px]">
        <Logo size="lg" />

        <h1 className="mt-10 text-[32px] leading-[1.25] font-bold tracking-tight">
          목표를 세우고,
          <br />
          <span className="text-text-brand">나를</span> 알아가요
        </h1>
        <p className="text-ink-3 mt-3 text-[15px] leading-relaxed">
          AI와 대화하며 목표를 설계하고
          <br />
          매일의 실천을 기록해요.
        </p>

        {errorMessage && (
          <p role="alert" className="bg-danger-soft text-danger mt-8 rounded-xl px-4 py-3 text-sm font-medium">
            {errorMessage}
          </p>
        )}

        <div className="mt-8 flex flex-col gap-3">
          <KakaoLoginButton next={next} surface="login_page" />
          <AppleLoginButton next={next} surface="login_page" />
          <IosAppDownloadLink className="border-border-subtle bg-surface hover:bg-subtle flex h-14 items-center justify-center rounded-2xl border text-[15px] font-semibold transition" />
        </div>

        <LoginLegalLinks />
        <p className="text-ink-4 mt-3 text-center text-xs">© 2026 나를(Naleul)</p>
      </div>
    </main>
  )
}
