import { Fragment } from 'react'
import { cn } from '@/lib/cn'

/**
 * 백엔드 문장의 **…** 부분을 강조해서 보여줘요.
 * (dangerouslySetInnerHTML 을 쓰지 않아요 — 문장에 사용자가 입력한 루틴 이름이 들어가므로)
 */
export function Emphasis({ text, className }: { text: string; className?: string }) {
  const parts = text.split(/\*\*(.+?)\*\*/g)
  return (
    <>
      {parts.map((p, i) =>
        i % 2 === 1 ? (
          <strong key={i} className={cn('font-bold', className)}>
            {p}
          </strong>
        ) : (
          <Fragment key={i}>{p}</Fragment>
        )
      )}
    </>
  )
}
