import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** 디자인 시스템 EmptyState — 가운데 정렬 · 48 원 아이콘 + 제목 + 설명 + (선택) 버튼 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: ReactNode
  title: ReactNode
  description?: ReactNode
  /** 보통 <Button variant="outline" size="sm"> */
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center gap-3 p-8 text-center', className)}>
      {icon && (
        <span className="bg-bg-subtle text-text-secondary grid size-12 place-items-center rounded-full [&_svg]:size-6">
          {icon}
        </span>
      )}
      <div>
        <p className="text-body-lg text-text-primary font-medium">{title}</p>
        {description && <p className="text-body-sm text-text-secondary mt-1">{description}</p>}
      </div>
      {action}
    </div>
  )
}
