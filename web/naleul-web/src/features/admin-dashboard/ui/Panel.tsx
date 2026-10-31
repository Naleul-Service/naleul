import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'

/** 대시보드 카드 틀 (제목 + 오른쪽 보조 문구) */
export function Panel({
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
      <div className="mb-4 flex items-baseline justify-between gap-3">
        <h2 className="text-[16px] font-bold">{title}</h2>
        {aside && <span className="text-ink-3 text-right text-[12px]">{aside}</span>}
      </div>
      {children}
    </Card>
  )
}

/** 숫자 하나 + 설명 (카드 없이 — 여러 개를 한 카드 안에 나란히 놓을 때) */
export function Stat({
  label,
  value,
  sub,
  tone,
}: {
  label: ReactNode
  value: ReactNode
  sub?: ReactNode
  tone?: 'good' | 'bad'
}) {
  return (
    <div className="min-w-0">
      <p className="text-ink-3 text-[12px]">{label}</p>
      <p
        className={cn(
          'mt-1 truncate text-[22px] leading-tight font-extrabold tracking-tight tabular-nums',
          tone === 'good' && 'text-success',
          tone === 'bad' && 'text-danger'
        )}
      >
        {value}
      </p>
      {sub && <p className="text-ink-3 mt-0.5 truncate text-[12px] tabular-nums">{sub}</p>}
    </div>
  )
}

/** 큰 숫자 타일 (핵심 지표 줄) */
export function Tile({ label, value, sub }: { label: ReactNode; value: ReactNode; sub?: ReactNode }) {
  return (
    <Card className="flex min-h-[116px] flex-col justify-between p-5">
      <p className="text-ink-3 text-[13px]">{label}</p>
      <div>
        <p className="text-[28px] leading-none font-extrabold tracking-tight tabular-nums">{value}</p>
        {sub && <p className="text-ink-3 mt-2 text-[12px] tabular-nums">{sub}</p>}
      </div>
    </Card>
  )
}

/** 가로 막대 한 줄 (기능별 AI 비용 등) */
export function BarRow({
  label,
  value,
  ratio,
  sub,
  color = 'bg-brand',
}: {
  label: ReactNode
  value: ReactNode
  ratio: number
  sub?: ReactNode
  color?: string
}) {
  return (
    <li>
      <div className="flex items-baseline gap-2 text-[13px]">
        <span className="min-w-0 flex-1 truncate font-semibold">{label}</span>
        {sub && <span className="text-ink-3 shrink-0 text-[12px] tabular-nums">{sub}</span>}
        <span className="shrink-0 font-bold tabular-nums">{value}</span>
      </div>
      <div className="bg-progress-track mt-1.5 h-1.5 overflow-hidden rounded-full">
        <div className={cn('h-full rounded-full', color)} style={{ width: `${Math.max(0, Math.min(1, ratio)) * 100}%` }} />
      </div>
    </li>
  )
}

export function Empty({ children = '아직 데이터가 없어요' }: { children?: ReactNode }) {
  return <p className="text-ink-3 grid min-h-[96px] place-items-center text-center text-[13px]">{children}</p>
}
