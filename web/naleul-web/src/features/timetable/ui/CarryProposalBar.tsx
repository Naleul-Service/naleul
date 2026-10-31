'use client'

import { Undo2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Check, ChevronLeft, ChevronRight, Info, X } from '@/components/icons'
import { AIBadge } from '@/components/ui/AIBadge'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Chip'
import { IconButton } from '@/components/ui/IconButton'
import { cn } from '@/lib/cn'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TimetableSurface } from '../analytics'
import { useAcceptAllCarry, useAcceptCarry, useDeclineCarry, useRevertCarry } from '../api'
import { addDays, formatMonthDay, hm } from '../time'
import type { TimeBlockTask } from '../types'

/** 기준 날짜(base)에서 본 상대 날짜: 오늘 · 어제 · 그제 · 내일 · 10월 8일 */
function relDay(date: string, base: string) {
  if (date === base) return '오늘'
  if (date === addDays(base, -1)) return '어제'
  if (date === addDays(base, -2)) return '그제'
  if (date === addDays(base, 1)) return '내일'
  return formatMonthDay(date)
}

/** "을/를" — 받침에 맞게 */
const eulReul = (word: string) => {
  const c = word.charCodeAt(word.length - 1)
  if (c < 0xac00 || c > 0xd7a3) return `${word}을(를)`
  return `${word}${(c - 0xac00) % 28 ? '을' : '를'}`
}

/**
 * 22시 이월 제안 — 못 한 일을 말없이 옮기면 "내가 잘못 넣었나?" 하고 헷갈려서, 옮긴 자리를 점선으로 보여주고 물어봐요.
 *   "어제 못한 '보고서 쓰기'를 오늘 15:00–16:00로 옮길까요?"  [옮기기] [안 옮기기]
 *  - 옮기기: 점선 자리 그대로 확정
 *  - 안 옮기기: 시간을 비워 그날 "시간 미정"으로 (원하는 시간에 끌어다 놓거나 지우면 돼요)
 *  - 여러 개면 하나씩 묻고, "모두 옮기기"로 한 번에
 * 블록을 직접 끌어 옮기거나 완료하면 답한 것으로 봐요.
 */
export function CarryProposalBar({
  tasks,
  today,
  surface,
}: {
  tasks: TimeBlockTask[]
  today: string
  /** 분석: 이 바가 쓰인 화면 */
  surface?: TimetableSurface
}) {
  const accept = useAcceptCarry()
  const decline = useDeclineCarry()
  const acceptAll = useAcceptAllCarry()
  const revert = useRevertCarry()
  const busy = accept.isPending || decline.isPending || acceptAll.isPending || revert.isPending
  // 넘겨 보기 (명세 6D 페이저) — 화면에서만 고르는 것, 답하면 목록이 줄어 자연히 다음 제안이 보여요
  const [page, setPage] = useState(0)
  const idx = Math.min(page, Math.max(tasks.length - 1, 0))
  const t = tasks[idx]
  const surf = surface ?? 'calendar'

  // 분석: 제안이 하나씩 뜰 때마다 한 번 (같은 제안을 다시 그려도 한 번만) — 맨 앞 제안 기준 (넘겨 보기는 세지 않아요)
  const first = tasks[0]
  const shownId = first?.taskId
  const fromDate = first?.carriedFromDate ?? null
  const remaining = tasks.length
  const viewedId = useRef<number | null>(null)
  useEffect(() => {
    if (shownId === undefined || viewedId.current === shownId) return
    viewedId.current = shownId
    track('carry_proposal.view', {
      surface: surf,
      item_count: remaining,
      days_ago: fromDate ? Math.round((Date.parse(today) - Date.parse(fromDate)) / 86_400_000) : undefined,
    })
  }, [shownId, fromDate, remaining, today, surf])

  if (!t) return null

  const result = (action: 'accept' | 'decline' | 'accept_all' | 'revert') => ({
    onSuccess: () => track('carry_proposal.result', { surface: surf, carry_action: action, result: 'success' }),
    onError: (e: unknown) =>
      track('carry_proposal.result', {
        surface: surf,
        carry_action: action,
        result: 'fail',
        error_status: errorStatusOf(e),
      }),
  })

  const date = t.plannedStartAt?.slice(0, 10) ?? t.date ?? today
  const from = t.carriedFromDate ? relDay(t.carriedFromDate, today) : '전날'
  const to = relDay(date, today)
  const name = `'${t.emoji ? `${t.emoji} ` : ''}${t.taskName}'`
  const when = t.plannedStartAt ? `${to} ${hm(t.plannedStartAt)}–${hm(t.plannedEndAt)}` : `${to}(시간 미정)`
  const sameDay = tasks.filter((x) => (x.plannedStartAt?.slice(0, 10) ?? x.date) === date)

  const sentence = `${from} 못한 ${eulReul(name)} ${when}로 옮길까요?`
  const hint = '점선으로 표시한 자리예요. 블록을 직접 끌어 옮겨도 돼요.'

  const acceptAllBtn = (compact: boolean) =>
    sameDay.length > 1 && (
      <Button
        size={compact ? 'sm' : 'md'}
        variant={compact ? 'ghost' : 'secondary'}
        disabled={busy}
        loading={acceptAll.isPending}
        onClick={() => {
          track('carry_proposal.accept_all_click', { surface: surf, item_count: sameDay.length })
          acceptAll.mutate(date, result('accept_all'))
        }}
      >
        {compact ? `${sameDay.length}개 모두 적용` : '모두 적용'}
      </Button>
    )
  const pager = tasks.length > 1 && (
    <div className="flex shrink-0 items-center gap-0.5">
      <IconButton label="이전 제안" disabled={idx === 0} onClick={() => setPage(idx - 1)}>
        <ChevronLeft />
      </IconButton>
      <span className="text-body-sm text-text-secondary min-w-9 text-center font-medium tabular-nums">
        {idx + 1} / {tasks.length}
      </span>
      <IconButton label="다음 제안" disabled={idx >= tasks.length - 1} onClick={() => setPage(idx + 1)}>
        <ChevronRight />
      </IconButton>
    </div>
  )
  const skipBtn = (
    <Button
      variant="secondary"
      disabled={busy}
      loading={decline.isPending}
      leftIcon={<X className="size-4" />}
      onClick={() => {
        track('carry_proposal.decline_click', { surface: surf, task_id: t.taskId, item_count: tasks.length })
        decline.mutate(t.taskId, result('decline'))
      }}
    >
      건너뛰기
    </Button>
  )
  // 원래 날로 되돌리기 (2026-10) — 원래 날짜 · 시각으로, 다시 자동으로 옮기지 않아요
  const revertBtn = t.carriedFromDate && (
    <Button
      variant="secondary"
      disabled={busy}
      loading={revert.isPending}
      leftIcon={<Undo2 className="size-4" />}
      onClick={() => {
        track('carry_proposal.revert_click', { surface: surf, task_id: t.taskId, item_count: tasks.length })
        revert.mutate({ taskId: t.taskId }, result('revert'))
      }}
    >
      원래 날로
    </Button>
  )
  const applyBtn = (
    <Button
      variant="primary"
      disabled={busy}
      loading={accept.isPending}
      leftIcon={<Check className="size-4" />}
      onClick={() => {
        track('carry_proposal.accept_click', { surface: surf, task_id: t.taskId, item_count: tasks.length })
        accept.mutate(t.taskId, result('accept'))
      }}
    >
      적용
    </Button>
  )
  const divider = <span aria-hidden className="bg-border-subtle h-7 w-px shrink-0" />
  const info = (
    <span title={`${sentence} ${hint}`} className="text-text-tertiary shrink-0">
      <Info className="size-4" />
    </span>
  )
  const title = (
    <span className="text-label-lg text-text-primary min-w-0 truncate">
      {t.emoji ? `${t.emoji} ` : ''}
      {t.taskName}
    </span>
  )
  const time = (
    <span className="text-body-sm text-text-secondary shrink-0 tabular-nums">
      {t.carriedFromDate ? `${formatMonthDay(t.carriedFromDate)} → ` : ''}
      {when}
    </span>
  )

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--tabbar-h)+16px)] z-40 flex justify-center px-4">
      {/* 제안 바 (명세 6D) — AI 그라데이션 테두리 · 넓은 화면 한 줄 / 좁은 화면 3줄 */}
      <div
        role="dialog"
        aria-label="못 한 일 옮기기"
        className="ai-border rounded-card pointer-events-auto w-full max-w-[920px] animate-[modal-in_160ms_ease-out]"
      >
        <p className="sr-only">
          {sentence} {hint}
          {tasks.length > 1 && ` 남은 제안 ${tasks.length}개`}
        </p>

        {/* 넓은 화면: h56 · 안쪽 9 · 간격 12 */}
        <div className="hidden h-14 items-center gap-3 p-[9px] md:flex">
          {sameDay.length > 1 && (
            <>
              {acceptAllBtn(false)}
              {divider}
            </>
          )}
          {pager && (
            <>
              {pager}
              {divider}
            </>
          )}
          <div className="flex min-w-0 flex-1 items-center gap-2">
            <AIBadge />
            {title}
            <Badge tone="neutral">새로 배치</Badge>
            {time}
            {info}
          </div>
          {divider}
          {revertBtn}
          {skipBtn}
          {applyBtn}
        </div>

        {/* 좁은 화면: 12/14 · 간격 8 · 3줄 */}
        <div className={cn('flex flex-col gap-2 px-3.5 py-3 md:hidden')}>
          <div className="flex min-w-0 items-center gap-2">
            <AIBadge />
            {title}
            <Badge tone="neutral">새로 배치</Badge>
          </div>
          <div className="flex min-w-0 items-center gap-2">
            {time}
            {info}
            <span className="ml-auto">{pager}</span>
          </div>
          <div className="flex items-center gap-2">
            {acceptAllBtn(true)}
            <span className="ml-auto flex gap-2">
              {revertBtn}
          {skipBtn}
              {applyBtn}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
