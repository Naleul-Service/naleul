import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { TermsScrollDepth } from '@/features/terms/analytics'
import { MARKETING_INFO_ROWS, TERMS_BODY, TERMS_META, isTermsType } from '@/features/terms/content'

type Props = { params: Promise<{ type: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { type } = await params
  return { title: isTermsType(type) ? TERMS_META[type].detailTitle : '약관' }
}

/** 약관 상세 (iOS TermsDetailView) — /settings/terms/service | privacy | marketing */
export default async function TermsDetailPage({ params }: Props) {
  const { type } = await params
  if (!isTermsType(type)) notFound()

  return (
    <>
      <TermsScrollDepth type={type} />
      <PageHeader
        breadcrumb={
          <>
            <Link href="/settings">설정</Link> / <Link href="/settings/terms">약관 동의</Link>
          </>
        }
        title={TERMS_META[type].detailTitle}
      />
      <Card className="mt-6 max-w-3xl p-5 sm:p-7">
        {/* 본문은 줄바꿈이 들어간 평문이라 whitespace-pre-line 으로 그대로 보여줘요 */}
        <p className="text-ink-2 text-[14px] leading-7 whitespace-pre-line">{TERMS_BODY[type]}</p>

        {type === 'marketing' && (
          <table className="border-line mt-4 w-full overflow-hidden rounded-xl border text-[14px]">
            <thead className="bg-subtle">
              <tr>
                <th className="w-28 px-4 py-3 text-left font-semibold">항목</th>
                <th className="border-line border-l px-4 py-3 text-left font-semibold">설명</th>
              </tr>
            </thead>
            <tbody>
              {MARKETING_INFO_ROWS.map((row) => (
                <tr key={row.label} className="border-line border-t">
                  <td className="text-ink-2 px-4 py-3 align-top">{row.label}</td>
                  <td className="text-ink-2 border-line border-l px-4 py-3">{row.content}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  )
}
