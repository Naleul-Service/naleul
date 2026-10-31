'use client'

import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'

/** 목표 상세의 카드 섹션 (제목 + 오른쪽 보조 정보 + 내용) */
export function Section({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <Card className="p-5 sm:p-6">
      <div className="mb-4 flex items-center justify-between gap-2">
        <h2 className="text-h4">{title}</h2>
        {aside && <span className="text-ink-3 text-[13px]">{aside}</span>}
      </div>
      {children}
    </Card>
  )
}
