import type { ReactNode } from 'react'

export function PageHeader({
  breadcrumb,
  title,
  description,
  actions,
}: {
  breadcrumb?: ReactNode
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        {breadcrumb && <p className="text-body-sm text-text-tertiary mb-1">{breadcrumb}</p>}
        <h1 className="text-h2 sm:text-h1 text-text-primary">{title}</h1>
        {description && <p className="text-body-md text-text-secondary mt-1.5">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  )
}
