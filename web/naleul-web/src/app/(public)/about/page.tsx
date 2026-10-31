import type { Metadata } from 'next'
import { CalendarArrowDown, Clock3, ShieldCheck, Sparkles, TrendingUp } from 'lucide-react'
import { LogoSymbol } from '@/components/brand/Logo'
import { PublicLink, PublicLoginLink, PublicStartLink } from '@/features/public/analytics'

export const metadata: Metadata = {
  title: { absolute: '나를(Naleul) — 목표를 세우고, 하루를 계획하는 자기관리 앱' },
  description: 'AI와 대화하며 목표를 설계하고, 하루 시간표를 자동으로 짜고, 나의 실천 패턴을 알아가는 자기관리 앱',
}

const FEATURES = [
  {
    icon: Sparkles,
    title: 'AI와 대화하며 목표 만들기',
    body: '지금 상황 · 목표 수치 · 기한을 몇 가지 질문으로 정리하면, 실천할 루틴과 할 일을 목표 구조로 만들어 드려요.',
  },
  {
    icon: Clock3,
    title: '하루 시간표 자동 배치',
    body: '기상 · 식사 · 수면 같은 고정 시간을 피해 할 일을 하루 시간표에 놓아 드려요. 놓은 이유도 함께 보여드려요.',
  },
  {
    icon: CalendarArrowDown,
    title: '외부 캘린더 일정 가져오기',
    body: '구글 캘린더나 아이폰 캘린더의 약속을 골라 할 일로 가져와, 약속과 할 일을 한 시간표에서 봐요.',
  },
  {
    icon: TrendingUp,
    title: '나의 패턴',
    body: '언제 잘 실천하고 언제 미루는지 기록을 바탕으로 보여드려요. 다음 계획을 고칠 때 참고할 수 있어요.',
  },
] as const

/** /about — 로그인 없이 보는 서비스 소개 (구글 OAuth 앱 인증의 홈페이지 주소) */
export default function AboutPage() {
  return (
    <>
      <section className="mx-auto max-w-5xl px-4 pt-16 pb-12 sm:px-6 sm:pt-24">
        {/* 구글 OAuth 브랜딩 심사: 동의 화면의 앱 이름 · 로고와 같은 이름 · 심볼을 홈페이지 첫 화면에 보여줘요 */}
        <p className="mb-6 flex items-center gap-3">
          <LogoSymbol className="h-8" title="나를(Naleul)" />
          <span className="text-[18px] font-bold">나를(Naleul)</span>
        </p>
        <h1 className="text-[34px] leading-[1.25] font-bold tracking-tight sm:text-[46px]">
          목표를 세우고, 하루를 계획하고,
          <br />
          <span className="text-text-brand">나를</span> 알아가요
        </h1>
        <p className="text-ink-2 mt-5 max-w-xl text-[16px] leading-relaxed sm:text-[17px]">
          나를은 목표와 매일의 할 일을 한곳에서 관리하는 자기관리 캘린더예요. 목표를 실천 가능한 루틴으로 나누고,
          하루 시간표에 놓고, 실제로 한 일을 돌아보며 나에게 맞는 방식을 찾아가요.
        </p>
        <p lang="en" className="text-ink-3 mt-3 max-w-xl text-[14px] leading-relaxed">
          Naleul is a self-management calendar app. It helps you turn goals into routines and tasks, plan them on a
          daily timetable, and optionally import events you choose from Google Calendar (read-only) as tasks.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <PublicStartLink
            position="hero"
            className="bg-brand hover:bg-brand-strong text-on-ink flex h-12 items-center rounded-2xl px-6 text-[15px] font-semibold transition"
          >
            로그인 없이 둘러보기
          </PublicStartLink>
          <PublicLoginLink
            position="hero"
            className="border-line-strong hover:bg-subtle flex h-12 items-center rounded-2xl border px-6 text-[15px] font-semibold transition"
          >
            로그인
          </PublicLoginLink>
        </div>
      </section>

      <section className="mx-auto max-w-5xl px-4 sm:px-6">
        <ul className="grid gap-4 sm:grid-cols-2">
          {FEATURES.map(({ icon: Icon, title, body }) => (
            <li key={title} className="border-line rounded-2xl border p-6">
              <span className="bg-brand-soft text-text-brand inline-flex size-10 items-center justify-center rounded-xl">
                <Icon className="size-5" aria-hidden />
              </span>
              <h2 className="mt-4 text-[17px] font-semibold">{title}</h2>
              <p className="text-ink-2 mt-2 text-[14px] leading-6">{body}</p>
            </li>
          ))}
        </ul>
      </section>

      <section id="google-calendar" className="mx-auto mt-16 max-w-5xl px-4 sm:px-6">
        <div className="bg-subtle rounded-2xl p-6 sm:p-8">
          <div className="flex items-center gap-2">
            <ShieldCheck className="text-text-brand size-5" aria-hidden />
            <h2 className="text-[19px] font-semibold">구글 캘린더 연동과 내 정보</h2>
          </div>
          <ul className="text-ink-2 mt-4 flex list-disc flex-col gap-2 pl-5 text-[14px] leading-6">
            <li>
              구글 캘린더 연동은 선택 기능이에요. 설정의 &ldquo;외부 캘린더 가져오기&rdquo;에서 직접 구글 계정을 연결할 때만
              쓰여요.
            </li>
            <li>
              나를은 <b className="text-ink font-semibold">캘린더 보기 권한(읽기 전용)</b>만 요청해요. 구글 캘린더에 일정을
              만들거나 고치거나 지우지 않아요.
            </li>
            <li>
              캘린더 목록과 고른 기간의 일정을 불러와 보여드리고, 그중 사용자가 고른 일정의 제목 · 시간만 나를의 할 일로
              저장해요. 고르지 않은 일정은 저장하지 않아요.
            </li>
            <li>
              구글 접근 토큰은 가져오는 동안 사용자의 브라우저 · 기기에서만 쓰고 서버에 저장하지 않아요. 구글 계정 설정에서
              언제든 연결을 해제할 수 있어요.
            </li>
            <li>
              구글에서 받은 정보는 판매하거나 광고에 쓰지 않으며, Limited Use 요건을 포함한 Google API 서비스 사용자 데이터
              정책을 따라요.
            </li>
          </ul>
          <p className="mt-5 flex flex-wrap gap-x-4 gap-y-2 text-[14px] font-medium">
            <PublicLink href="/privacy" type="privacy" position="section" className="text-text-brand underline underline-offset-2">
              개인정보처리방침 자세히 보기
            </PublicLink>
            <PublicLink
              href="https://developers.google.com/terms/api-services-user-data-policy"
              type="google_policy"
              position="section"
              className="text-text-brand underline underline-offset-2"
            >
              Google API 서비스 사용자 데이터 정책
            </PublicLink>
          </p>
        </div>
      </section>

      <section className="mx-auto mt-16 max-w-5xl px-4 text-center sm:px-6">
        <h2 className="text-[24px] font-bold tracking-tight">오늘 할 일부터 가볍게 시작해 보세요</h2>
        <p className="text-ink-2 mt-2 text-[15px]">로그인 없이 먼저 둘러볼 수 있어요.</p>
        <PublicStartLink
          position="bottom"
          className="bg-brand hover:bg-brand-strong text-on-ink mt-6 inline-flex h-12 items-center rounded-2xl px-6 text-[15px] font-semibold transition"
        >
          시작하기
        </PublicStartLink>
      </section>
    </>
  )
}
