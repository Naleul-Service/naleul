import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'

/** 패턴 화면 카드 틀 */
export function Section({
  title,
  aside,
  className,
  children,
}: {
  title: ReactNode
  aside?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <Card className={cn('p-5 sm:p-6', className)}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-h4">{title}</h2>
        {aside && <span className="text-ink-3 text-[13px] whitespace-nowrap">{aside}</span>}
      </div>
      {children}
    </Card>
  )
}

/** 표본이 부족한 카드 안내 */
export function NotEnough({ children = '기록이 쌓이면 보여드려요' }: { children?: ReactNode }) {
  return <p className="text-ink-3 grid min-h-[120px] place-items-center text-center text-sm">{children}</p>
}
