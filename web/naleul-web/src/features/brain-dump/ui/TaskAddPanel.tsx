'use client'

import { useEffect, useRef } from 'react'
import { X } from '@/components/icons'
import { iconButtonClass } from '@/components/ui/IconButton'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { TaskAddView, type TaskAddedInfo, type TaskAddViewHandle } from './TaskAddView'

/** 패널 폭 — 캘린더는 이만큼 비켜서 나란히 보여요 (xl 이상) */
export const TASK_PANEL_WIDTH = 420

/** 패널 제목 = 캘린더 + 메뉴의 항목 이름 (명세 11.2 · 11.3) */
export const ADD_MODE_LABEL = { ai: 'AI로 자동 Task 추가', manual: '계획 할 일 추가', log: '실제 한 일 추가' } as const

/**
 * 캘린더 오른쪽에 여는 Task 추가 패널.
 *  - xl(1280px) 이상: 캘린더 옆에 나란히 (캘린더를 보면서 빈 시간을 골라 추가)
 *  - 그보다 좁으면: 오른쪽에서 덮는 시트, 모바일은 화면 전체
 * 추가하면 옆 캘린더가 그 날짜로 이동하고 새 블록에 NEW 가 붙어요 (onAdded).
 */
export function TaskAddPanel({
  mode,
  onClose,
  onAdded,
  prefill,
  floating = false,
}: {
  mode: 'ai' | 'manual' | 'log'
  /** 옆에 나란히 둘 캘린더가 없는 화면(홈) — 넓은 화면에서도 그림자를 두고 위에 떠요 */
  floating?: boolean
  /** 계획 할 일 추가: 캘린더 빈 곳을 눌러 연 날짜 · 시각 */
  prefill?: { date: string; startTime?: string | null; endTime?: string | null }
  onClose: () => void
  onAdded: (info: TaskAddedInfo) => void
}) {
  const viewRef = useRef<TaskAddViewHandle>(null)
  /** 닫기 — 적던 내용은 확인 없이 사라져서 has_input 으로 남겨요 */
  const closeWith = (method: 'x_button' | 'esc') => {
    track('task_add.panel_close', { ...viewRef.current?.closeInfo(), close_method: method })
    onClose()
  }

  // Esc 로 닫기 — 입력 중이거나 다른 창(모달·팝오버)이 떠 있으면 그쪽이 먼저
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || e.defaultPrevented) return
      const el = e.target instanceof HTMLElement ? e.target : null
      if (el?.closest('input, textarea, select, [role="dialog"]')) return
      track('task_add.panel_close', { ...viewRef.current?.closeInfo(), close_method: 'esc' })
      onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <aside
      aria-label={ADD_MODE_LABEL[mode]}
      className={cn(
        'bg-bg-surface border-border-subtle shadow-pop fixed inset-0 z-40 flex animate-[panel-in_320ms_cubic-bezier(0.22,1,0.36,1)] flex-col sm:inset-y-0 sm:right-0 sm:left-auto sm:w-[420px] sm:border-l',
        !floating && 'xl:shadow-none'
      )}
    >
      {/* 명세 13.2 — 머리: H3 제목 왼쪽 · 닫기 IconButton / 내용: 좌우 24 · 위 20 · 안쪽 카드 테두리 · 그림자 없이 */}
      <div className="flex shrink-0 items-center gap-2 px-6 pt-6 pb-1">
        <h2 className="text-h3 text-text-primary min-w-0 flex-1 truncate">{ADD_MODE_LABEL[mode]}</h2>
        <button
          type="button"
          onClick={() => closeWith('x_button')}
          aria-label={`${ADD_MODE_LABEL[mode]} 닫기`}
          className={iconButtonClass('md')}
        >
          <X className="size-[18px]" />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-6 pt-5 pb-[max(24px,env(safe-area-inset-bottom))] [&_.shadow-card]:border-0 [&_.shadow-card]:bg-transparent [&_.shadow-card]:p-0 [&_.shadow-card]:shadow-none">
        <TaskAddView
          ref={viewRef}
          key={`${mode}-${prefill?.date ?? ''}-${prefill?.startTime ?? ''}`}
          variant="panel"
          initialMode={mode}
          singleMode
          prefill={prefill}
          onAdded={onAdded}
        />
      </div>
    </aside>
  )
}
