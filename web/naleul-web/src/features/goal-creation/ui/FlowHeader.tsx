'use client'

import type { ReactNode } from 'react'
import { X } from '@/components/icons'
import { cn } from '@/lib/cn'

/** 목표 만들기 플로우 공통 상단 바: 닫기 · 제목(+진행 표시) · 오른쪽 액션 */
export function FlowHeader({
  title,
  center,
  action,
  onClose,
  wide = false,
}: {
  title: string
  center?: ReactNode
  action?: ReactNode
  onClose: () => void
  /** 넓은 화면(초안 검토)용 */
  wide?: boolean
}) {
  return (
    <header className="border-line bg-surface/90 sticky top-0 z-20 border-b backdrop-blur">
      <div
        className={cn(
          'mx-auto grid h-16 max-w-3xl grid-cols-[1fr_auto_1fr] items-center gap-2 px-3 sm:px-4',
          wide && 'max-w-[1280px] sm:px-6 lg:px-8'
        )}
      >
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-ink-2 hover:bg-subtle hover:text-ink rounded-xl p-2"
          >
            <X className="size-5" />
          </button>
          <span className="text-[15px] font-bold">{title}</span>
        </div>
        <div className="justify-self-center">{center}</div>
        <div className="justify-self-end">{action}</div>
      </div>
    </header>
  )
}
