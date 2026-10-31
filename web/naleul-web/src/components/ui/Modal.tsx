'use client'

import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from '@/components/icons'
import { cn } from '@/lib/cn'

/** 모달을 닫은 방법 (Amplitude close_method) */
export type ModalCloseMethod = 'x_button' | 'backdrop' | 'esc'

interface ModalProps {
  open: boolean
  /** method: 어떻게 닫혔는지 (필요한 곳만 받아서 써요) */
  onClose: (method: ModalCloseMethod) => void
  title?: ReactNode
  description?: ReactNode
  children?: ReactNode
  /** 하단 버튼 영역 */
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** 바깥 클릭으로 닫기 (기본 true). 저장 중엔 false 로 */
  dismissible?: boolean
}

// 명세 13.2 — 기본 480 · 넓게 640 · 확인창 400
const WIDTH = { sm: 'sm:max-w-[400px]', md: 'sm:max-w-[480px]', lg: 'sm:max-w-[640px]' }

export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
  dismissible = true,
}: ModalProps) {
  const titleId = useId()
  // 닫힐 때도 잠깐(150ms) 그려 두고 나가는 애니메이션을 보여줘요
  const [shown, setShown] = useState(open)
  if (open && !shown) setShown(true)
  const leaving = shown && !open
  useEffect(() => {
    if (!leaving) return
    const id = window.setTimeout(() => setShown(false), 150)
    return () => window.clearTimeout(id)
  }, [leaving])
  const panelRef = useRef<HTMLDivElement>(null)
  // 내용이 스크롤될 때만 아래 버튼 줄 위에 1px border/soft
  const contentRef = useRef<HTMLDivElement>(null)
  const [scrolls, setScrolls] = useState(false)
  useEffect(() => {
    const el = contentRef.current
    if (!open || !el) return
    const check = () => setScrolls(el.scrollHeight > el.clientHeight + 1)
    check()
    const ro = new ResizeObserver(check)
    ro.observe(el)
    if (el.firstElementChild) ro.observe(el.firstElementChild)
    return () => ro.disconnect()
  }, [open])
  // onClose 가 렌더링마다 새 함수여도 아래 effect 가 다시 돌지 않도록 ref 에 보관
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  // ESC 로 닫기 + 뒤 화면 스크롤 막기 + 열릴 때 포커스 이동
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && dismissible) onCloseRef.current('esc')
    }
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', onKey)
    panelRef.current?.focus()
    return () => {
      document.body.style.overflow = prevOverflow
      document.removeEventListener('keydown', onKey)
    }
  }, [open, dismissible])

  if (!shown || typeof document === 'undefined') return null

  return createPortal(
    <div
      className={cn(
        'fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4',
        leaving && 'pointer-events-none'
      )}
    >
      <div
        className={cn(
          'bg-bg-overlay absolute inset-0',
          leaving ? 'animate-[fade-out_150ms_ease-in_both]' : 'animate-[fade-in_200ms_ease-out]'
        )}
        onClick={dismissible ? () => onClose('backdrop') : undefined}
        aria-hidden
      />
      {/*
        명세 13.2 — 하나의 시트/모달 디자인
         · 모바일: 아래 시트 (위 모서리 24 · 손잡이 36×4 위 16 · 아래 12) · 넓은 화면: 가운데 모달 (모서리 20)
         · 머리: 제목 H3 왼쪽 · 부제목 Body/SM · 닫기 IconButton MD — 좌우 24
         · 내용: 좌우 24 · 위 20 · 아래 버튼 줄: 16/24 + safe area, 내용이 스크롤될 때만 위 1px border/soft
      */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
        className={cn(
          'bg-bg-surface shadow-pop relative flex max-h-[90dvh] w-full flex-col outline-none',
          'rounded-t-sheet sm:rounded-[20px]',
          // 모바일: 아래에서 슝 올라오는 시트 · 넓은 화면: 가운데에서 살짝 튕기며 (나갈 땐 빠르게)
          leaving
            ? 'max-sm:animate-[sheet-out_150ms_ease-in_both] sm:animate-[modal-out_150ms_ease-in_both]'
            : 'max-sm:animate-[sheet-in_320ms_var(--ease-out-expo)] sm:animate-[modal-in_260ms_var(--ease-spring)]',
          WIDTH[size]
        )}
      >
        {/* 모바일 손잡이 */}
        <div className="flex shrink-0 justify-center pt-4 pb-3 sm:hidden" aria-hidden>
          <span className="bg-border-subtle h-1 w-9 rounded-[2px]" />
        </div>
        {(title || dismissible) && (
          <div className="flex shrink-0 items-start justify-between gap-4 px-6 sm:pt-6">
            <div className="min-w-0">
              {title && (
                <h2 id={titleId} className="text-h3 text-text-primary">
                  {title}
                </h2>
              )}
              {description && <p className="text-body-sm text-text-secondary mt-1">{description}</p>}
            </div>
            {dismissible && (
              <button
                type="button"
                onClick={() => onClose('x_button')}
                aria-label="닫기"
                className="rounded-icon-btn text-text-secondary hover:bg-bg-subtle hover:text-text-primary -mt-1 -mr-1.5 grid size-8 shrink-0 place-items-center"
              >
                <X className="size-[18px]" />
              </button>
            )}
          </div>
        )}
        {children && (
          <div ref={contentRef} className={cn('min-h-0 overflow-y-auto px-6 pt-5', footer ? 'pb-5' : 'pb-1')}>
            <div>{children}</div>
          </div>
        )}
        {footer && (
          <div
            className={cn(
              'flex shrink-0 gap-2 px-6 pt-4 pb-[max(16px,env(safe-area-inset-bottom))] sm:justify-end sm:pb-6',
              // 모바일: 버튼은 LG 높이로 줄을 꽉 채워요 (보조 + 주 버튼이면 반씩)
              'max-sm:[&>button]:h-12 max-sm:[&>button]:flex-1',
              scrolls && 'border-border-soft border-t'
            )}
          >
            {footer}
          </div>
        )}
        {!footer && <div className="pb-[max(24px,env(safe-area-inset-bottom))] sm:pb-6" />}
      </div>
    </div>,
    document.body
  )
}
