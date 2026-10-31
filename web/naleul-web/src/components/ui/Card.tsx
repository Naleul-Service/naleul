import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

/** 디자인 시스템 Card — bg/surface · 1px border/subtle · 모서리 16 (안쪽 여백은 쓰는 곳에서, 기본 20) */
export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-card border-border-subtle bg-bg-surface shadow-card border', className)} {...rest} />
}

export function CardHeader({
  title,
  aside,
  className,
}: {
  title: React.ReactNode
  aside?: React.ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex items-center justify-between gap-3', className)}>
      <h2 className="text-h4 text-text-primary">{title}</h2>
      {aside && <div className="text-caption-md text-text-secondary">{aside}</div>}
    </div>
  )
}
