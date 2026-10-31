'use client'

import { useEffect, useRef } from 'react'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { track } from '@/lib/client/analytics'
import { cn } from '@/lib/cn'
import { useAiUsage } from '../api'

/** 설정 · "오늘 사용량" — 하루 횟수가 정해진 기능을 한눈에 */
export function UsageCard() {
  const { data, isPending, isError } = useAiUsage()

  // settings.usage_view — 불러오기가 끝난 직후 한 번 (실패하면 빈 카드). 이 카드는 설정에서만 써요
  const viewSent = useRef(false)
  useEffect(() => {
    if (viewSent.current || isPending) return
    viewSent.current = true
    if (isError || !data) {
      track('settings.usage_view', { result: 'fail' })
      return
    }
    track('settings.usage_view', {
      result: 'success',
      is_premium: data.pro,
      exhausted_keys: data.items
        .filter((i) => i.limit != null && i.remaining === 0)
        .map((i) => i.key)
        .join(','),
    })
  }, [isPending, isError, data])
  return (
    <Card className="p-5">
      <div className="flex items-center gap-2">
        <h2 className="text-[16px] font-bold">오늘 사용량</h2>
        {data && (
          <span className="bg-subtle text-ink-2 rounded-full px-2 py-0.5 text-[11px] font-semibold">
            {data.pro ? 'Pro 한도' : '무료 한도'}
          </span>
        )}
        <span className="text-ink-3 ml-auto text-[12px]">매일 0시(한국 시간)에 다시 채워져요</span>
      </div>
      {isPending ? (
        <div className="grid h-24 place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : (
        <ul className="mt-4 space-y-4">
          {(data?.items ?? []).map((i) => {
            const ratio = i.limit ? Math.min(i.used / i.limit, 1) : 0
            return (
              <li key={i.key}>
                <div className="flex items-baseline gap-2">
                  <p className="text-[14px] font-semibold">{i.label}</p>
                  <p className="text-ink-3 min-w-0 flex-1 truncate text-[12px]">{i.description}</p>
                  <p className="shrink-0 text-[13px] tabular-nums">
                    {i.limit == null ? (
                      <span className="text-ink-3">제한 없음</span>
                    ) : (
                      <>
                        <b className={i.remaining === 0 ? 'text-danger' : ''}>{i.remaining}</b>
                        <span className="text-ink-3"> / {i.limit}회 남음</span>
                      </>
                    )}
                  </p>
                </div>
                {i.limit != null && (
                  <div className="bg-progress-track mt-1.5 h-1.5 overflow-hidden rounded-full">
                    <div
                      className={cn(
                        'h-full rounded-full',
                        ratio >= 1 ? 'bg-danger' : ratio >= 0.7 ? 'bg-warning' : 'bg-brand'
                      )}
                      style={{ width: `${ratio * 100}%` }}
                    />
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}
