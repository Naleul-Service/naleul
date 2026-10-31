'use client'

import { Switch } from '@/components/ui/Switch'
import { cn } from '@/lib/cn'
import { track } from '@/lib/client/analytics'
import { usePlannerSetting, useUpdatePlannerSetting } from '../api'

/**
 * 자동 이월 켜기/끄기 (2026-10) — 캘린더 머리글(compact) · 설정(card) 두 곳에 둬요.
 *  - 켜짐: 못 한 일을 다음 날 아침 기상 시간에 오늘로 옮겨요 (새벽까지 하던 일은 그대로)
 *  - 꺼짐: 못 한 일은 그날에 '놓침'으로 남아요 → 직접 '다른 날로' 옮겨요
 */
export function AutoCarryToggle({ variant }: { variant: 'compact' | 'card' }) {
  const setting = usePlannerSetting()
  const update = useUpdatePlannerSetting()
  const on = setting.data?.autoCarryEnabled ?? true
  const toggle = (next: boolean) => {
    track(variant === 'card' ? 'settings.auto_carry_toggle' : 'calendar.auto_carry_toggle', { enabled: next })
    update.mutate({ autoCarryEnabled: next })
  }
  const help = on
    ? '못 한 일을 다음 날 아침(기상 시간)에 오늘로 옮겨요. 새벽까지 하던 일은 그대로 둬요.'
    : "못 한 일은 그날에 '놓침'으로 남아요. 필요하면 직접 '다른 날로' 옮겨요."

  if (variant === 'compact') {
    return (
      <label
        title={help}
        className={cn(
          'border-border-subtle text-text-secondary flex h-8 items-center gap-2 rounded-full border px-2.5 text-xs font-medium',
          setting.isPending && 'opacity-60'
        )}
      >
        자동 이월
        <Switch
          checked={on}
          onChange={toggle}
          label="못 한 일 자동 이월"
          disabled={setting.isPending || update.isPending}
          className="h-5 w-9 [&>span]:size-4 [&>span]:translate-x-[2px] aria-checked:[&>span]:translate-x-[18px]"
        />
      </label>
    )
  }
  return (
    <div className="flex items-center gap-3 px-5 py-4">
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-bold">못 한 일 자동 이월</p>
        <p className="text-ink-3 mt-0.5 text-[13px]">{help}</p>
      </div>
      <Switch
        checked={on}
        onChange={toggle}
        label="못 한 일 자동 이월"
        disabled={setting.isPending || update.isPending}
      />
    </div>
  )
}
