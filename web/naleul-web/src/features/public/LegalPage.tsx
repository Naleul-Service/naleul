import { REFUND_BODY, TERMS_BODY } from '@/features/terms/content'
import type { LegalType } from '@/lib/client/publicEvents'
import { LegalBody, LegalScrollDepth, PublicLink } from './analytics'

const TITLE: Record<LegalType, string> = { privacy: '개인정보처리방침', terms: '서비스 이용약관', refund: '환불 정책' }
const BODY: Record<LegalType, string> = { privacy: TERMS_BODY.privacy, terms: TERMS_BODY.service, refund: REFUND_BODY }

/** /privacy · /terms 본문 — 앱 설정의 약관 본문(features/terms/content.ts)과 같은 글을 보여줘요 */
export function LegalPage({ type }: { type: LegalType }) {
  // 제목 아래 '○○ 보기' — 방침 ↔ 약관, 환불 정책은 약관으로
  const other: LegalType = type === 'privacy' ? 'terms' : type === 'terms' ? 'privacy' : 'terms'
  return (
    <article className="mx-auto max-w-3xl px-4 pt-12 sm:px-6">
      <LegalScrollDepth legalType={type} />
      <h1 className="text-[28px] font-bold tracking-tight">{TITLE[type]}</h1>
      <p className="text-ink-3 mt-2 text-[14px]">
        나를(Naleul) ·{' '}
        <PublicLink href={`/${other}`} type={other} position="body" className="text-text-brand underline underline-offset-2">
          {TITLE[other]} 보기
        </PublicLink>
      </p>
      <div className="border-line mt-8 rounded-2xl border p-5 sm:p-7">
        <LegalBody text={BODY[type]} />
      </div>
    </article>
  )
}
