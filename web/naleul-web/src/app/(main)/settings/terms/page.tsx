import type { Metadata } from 'next'
import Link from 'next/link'
import { ChevronRight } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { TermsItemLink, TermsPageview } from '@/features/terms/analytics'
import { TERMS_META, TERMS_TYPES } from '@/features/terms/content'

export const metadata: Metadata = { title: '약관 동의' }

/** 약관 목록 (iOS TermsAgreementView) */
export default function TermsListPage() {
  return (
    <>
      <TermsPageview />
      <PageHeader breadcrumb={<Link href="/settings">설정</Link>} title="약관 동의" />
      <Card className="divide-line mt-6 max-w-3xl divide-y">
        {TERMS_TYPES.map((type) => (
          <TermsItemLink
            key={type}
            type={type}
            href={`/settings/terms/${type}`}
            className="hover:bg-subtle/60 first:rounded-t-card last:rounded-b-card flex items-center gap-3 px-5 py-4"
          >
            <span className="flex-1 text-[15px] font-semibold">{TERMS_META[type].listTitle}</span>
            <ChevronRight className="text-ink-4 size-4" />
          </TermsItemLink>
        ))}
      </Card>
    </>
  )
}
