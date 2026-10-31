'use client'

import { useEffect, useRef } from 'react'
import Link from 'next/link'
import { useQueryClient } from '@tanstack/react-query'
import { CalendarOff, RotateCcw } from 'lucide-react'
import { Clock, Settings } from '@/components/icons'
import { Button, buttonClass } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Chip'
import { cn } from '@/lib/cn'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TimetableSurface } from '../analytics'
import { formatMonthDay, hm, todayKst } from '../time'
import type { FixedBlock } from '../types'

export interface FixedActions {
  onEditTime: (b: FixedBlock) => void
  onSkip: (b: FixedBlock) => void
  onReset: (b: FixedBlock) => void
}

const TYPE_LABEL = { SLEEP: '수면', LUNCH: '점심', DINNER: '저녁', CUSTOM: '고정 일정' } as const

/** 고정 시간 요청 변수 (usePatternOverride · useDeletePatternOverride) */
type FixedVars = { lifePatternId?: number; targetDate?: string; skipped?: boolean; startTime?: string; scope?: string }

export function FixedDetail({
  block: b,
  busy,
  actions,
  surface,
}: {
  block: FixedBlock
  busy?: boolean
  actions: FixedActions
  /** Amplitude surface — 이 창을 띄운 화면 */
  surface?: TimetableSurface
}) {
  const covered = b.coveredBy ?? []
  const pattern_type = b.patternType
  const qc = useQueryClient()
  /** 이 창에서 누른 비우기 · 되돌리기 요청 (끝나면 fixed_detail.result) */
  const pendingRef = useRef<'skip' | 'reset' | null>(null)
  const { lifePatternId, targetDate } = b

  // 요청은 훅이 보내요 — 성공하면 창이 닫히므로 mutation cache 에서 결과를 바로 받아요
  useEffect(
    () =>
      qc.getMutationCache().subscribe((ev) => {
        const action = pendingRef.current
        if (ev.type !== 'updated' || !action) return
        const v = ev.mutation.state.variables as FixedVars | undefined
        if (v?.lifePatternId !== lifePatternId || v.targetDate !== targetDate || v.startTime || v.scope) return
        if (action === 'skip' ? !v.skipped : v.skipped !== undefined) return
        if (ev.action.type === 'success') {
          pendingRef.current = null
          track('fixed_detail.result', { surface, fixed_action: action, result: 'success' })
        } else if (ev.action.type === 'error') {
          pendingRef.current = null
          track('fixed_detail.result', {
            surface,
            fixed_action: action,
            result: 'fail',
            error_status: errorStatusOf(ev.action.error),
          })
        }
      }),
    [qc, lifePatternId, targetDate, surface]
  )
  // 자정을 넘긴 수면의 다음 날 조각: 시작 시각을 몰라 시간 변경은 시작한 날에서 해요
  const canEditTime = !b.clippedFromPreviousDay

  return (
    <div className="p-5">
      <div className="flex flex-wrap items-center gap-1.5 pr-8">
        <Badge>고정 시간 · {TYPE_LABEL[b.patternType]}</Badge>
        {b.overridden && <Badge tone="brand">기본과 다른 시간</Badge>}
      </div>

      <h3 className="mt-2.5 text-lg font-bold">
        {b.emoji ? `${b.emoji} ` : ''}
        {b.title}
      </h3>
      <p className="text-ink-2 mt-2 flex items-center gap-2 text-sm">
        <Clock className="text-ink-3 size-4" />
        {formatMonthDay(b.targetDate)} · {hm(b.start)} – {hm(b.end) === '00:00' ? '24:00' : hm(b.end)}
      </p>

      {b.clippedFromPreviousDay && (
        <p className="bg-subtle text-ink-3 mt-3 rounded-xl px-3.5 py-2.5 text-xs leading-relaxed">
          전날 밤에 시작한 {b.title} 시간이에요. 시간을 바꾸려면 {formatMonthDay(b.targetDate)} 블록을 눌러 주세요.
        </p>
      )}
      {covered.length > 0 && (
        <p className="bg-warning-soft mt-3 rounded-xl px-3.5 py-2.5 text-xs leading-relaxed text-warning-ink">
          직접 정한 Task가 이 시간의 일부를 덮고 있어요.
        </p>
      )}

      <div className="mt-5 grid grid-cols-2 gap-2">
        {canEditTime && (
          <Button
            variant="secondary"
            onClick={() => {
              track('fixed_detail.time_edit_click', { surface, pattern_type })
              actions.onEditTime(b)
            }}
            disabled={busy}
          >
            <Clock className="size-4" />
            시간 변경
          </Button>
        )}
        <Button
          variant="secondary"
          onClick={() => {
            track('fixed_detail.skip_click', {
              surface,
              pattern_type,
              days_from_today: Math.round((Date.parse(b.targetDate) - Date.parse(todayKst())) / 86_400_000),
            })
            pendingRef.current = 'skip'
            actions.onSkip(b)
          }}
          loading={busy}
          className={cn(!canEditTime && 'col-span-2')}
        >
          <CalendarOff className="size-4" />
          이날만 비우기
        </Button>
        {b.overridden && (
          <Button
            variant="ghost"
            className="col-span-2"
            onClick={() => {
              track('fixed_detail.reset_click', { surface, pattern_type })
              pendingRef.current = 'reset'
              actions.onReset(b)
            }}
            disabled={busy}
          >
            <RotateCcw className="size-4" />
            기본 시간으로 되돌리기
          </Button>
        )}
        <Link
          href="/settings/life-pattern"
          onClick={() => track('fixed_detail.settings_click', { surface, pattern_type })}
          className={cn(buttonClass('ghost'), 'col-span-2')}
        >
          <Settings className="size-4" />
          기본 패턴 수정
        </Link>
      </div>
      <p className="text-ink-4 mt-3 text-center text-[11px]">
        시간을 옮기면 이날만 · 이번 주 · 앞으로 중에서 고를 수 있어요.
      </p>
    </div>
  )
}
