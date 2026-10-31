'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/**
 * 목표 상세 떠 있는 버튼 묶음 (명세 12.9) — 56×56 원 · 같은 그림자 · 사이 12 · 아이콘 22 (모바일 캘린더 FAB 와 같게).
 * 모바일은 탭바 위, 넓은 화면은 오른쪽 아래.
 */
export function GoalFabs({ children }: { children: ReactNode }) {
  return (
    <div className="fixed right-5 bottom-[calc(var(--tabbar-h)+20px)] z-30 flex flex-col items-center gap-3 lg:right-10 lg:bottom-10">
      {children}
    </div>
  )
}

export function GoalFab({
  label,
  icon,
  onClick,
  tone = 'primary',
}: {
  label: string
  icon: ReactNode
  onClick: () => void
  tone?: 'primary' | 'ai'
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        'shadow-primary grid size-14 place-items-center rounded-full text-white transition-colors active:shadow-none [&_svg]:size-[22px]',
        tone === 'ai'
          ? 'bg-ai-gradient hover:bg-ai-gradient-hover active:bg-ai-gradient-pressed'
          : 'bg-action-primary-bg hover:bg-action-primary-bg-hover active:bg-action-primary-bg-pressed'
      )}
    >
      {icon}
    </button>
  )
}
