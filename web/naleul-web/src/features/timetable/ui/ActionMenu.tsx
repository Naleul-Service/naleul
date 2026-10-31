'use client'

import type { ReactNode } from 'react'
import { Popover, type AnchorRect } from '@/components/ui/Popover'
import { cn } from '@/lib/cn'

export interface ActionMenuItem {
  key: string
  label: string
  icon: ReactNode
  onSelect: () => void
  disabled?: boolean
  /** 눌 수 없을 때 이유 (항목 아래 작은 글씨) */
  hint?: string
}

/**
 * 캘린더 + 메뉴 · AI 메뉴 (명세 11.1 · 11.2) — 넓은 화면은 누른 자리 옆 팝오버, 좁은 화면은 아래 시트.
 * 항목 사이 구분선 없어요.
 */
export function ActionMenu({
  anchor,
  onClose,
  label,
  items,
}: {
  anchor: AnchorRect
  onClose: () => void
  label: string
  items: ActionMenuItem[]
}) {
  return (
    <Popover anchor={anchor} onClose={onClose} width={248} label={label} hideClose>
      <ul role="menu" aria-label={label} className="flex flex-col p-1.5 max-sm:pb-3">
        {items.map((i, n) => (
          // 항목이 위에서부터 차례로 (25ms 간격)
          <li
            key={i.key}
            role="none"
            className="animate-[menu-item-in_240ms_var(--ease-out-expo)_both]"
            style={{ animationDelay: `${40 + n * 25}ms` }}
          >
            <button
              type="button"
              role="menuitem"
              disabled={i.disabled}
              onClick={() => {
                onClose()
                i.onSelect()
              }}
              className={cn(
                'rounded-nav text-body-md text-text-primary hover:bg-bg-subtle flex min-h-11 w-full items-center gap-3 px-3 py-2 text-left font-medium transition-colors',
                'disabled:text-disabled-fg disabled:hover:bg-transparent',
                '[&_svg]:size-5 [&_svg]:shrink-0'
              )}
            >
              <span className={cn('grid place-items-center', i.disabled ? 'text-disabled-fg' : 'text-text-secondary')}>
                {i.icon}
              </span>
              <span className="min-w-0">
                <span className="block">{i.label}</span>
                {i.hint && <span className="text-caption-sm text-text-tertiary block">{i.hint}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Popover>
  )
}
