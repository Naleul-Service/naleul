'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2, Copy, PenLine } from 'lucide-react'
import { Repeat, Trash2 } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Spinner } from '@/components/ui/Spinner'
import { toast } from '@/stores/toastStore'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { addDays, formatDuration, formatMonthDay, startOfWeek, todayKst } from '@/features/timetable/time'
import { useDeleteRecord, useRecordJournal, type JournalDay, type JournalItem } from '../api'
import { journalText } from '../journalText'

function dayLabel(ymd: string, today: string) {
  if (ymd === today) return `오늘 · ${formatMonthDay(ymd)}`
  if (ymd === addDays(today, -1)) return `어제 · ${formatMonthDay(ymd)}`
  return formatMonthDay(ymd)
}

/** 오늘부터 며칠 전 날짜인지 (오늘 0) */
const daysAgoOf = (ymd: string, today: string) => Math.round((Date.parse(today) - Date.parse(ymd)) / 86_400_000)

/** 복사하고 성공 여부를 돌려줘요 */
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('복사했어요. 원하는 곳에 붙여 넣으세요.')
    return true
  } catch {
    toast.error('복사하지 못했어요. 다시 시도해 주세요.')
    return false
  }
}

/**
 * 업무 일지 — 날짜별로 "그날 완료한 Task"와 "직접 남긴 기록"을 한 목록으로.
 *  - 하루 / 이번 주 단위로 글로 복사 (업무 보고·회고에 바로 붙여 넣기)
 *  - 직접 남긴 기록만 여기서 지울 수 있어요 (Task 완료 취소는 Task 목록에서)
 */
export function RecordJournal({ goalId, goalName }: { goalId: number; goalName: string }) {
  const today = todayKst()
  const { data, isPending, isError, error, refetch, hasNextPage, fetchNextPage, isFetchingNextPage } =
    useRecordJournal(goalId)
  const days = useMemo(() => data?.pages.flatMap((p) => p.days) ?? [], [data])
  const del = useDeleteRecord()
  const [pending, setPending] = useState<{ item: JournalItem; daysAgo: number } | null>(null)

  // '일지를 불러오지 못했어요'가 보일 때 (실패할 때마다 1회)
  const failSent = useRef(false)
  useEffect(() => {
    if (!isError) {
      failSent.current = false
      return
    }
    if (failSent.current) return
    failSent.current = true
    track('work_journal.load_fail', { goal_category_id: goalId, error_status: errorStatusOf(error) })
  }, [isError, error, goalId])

  // '아직 일지가 없어요' / '최근 2주에는 일지가 없어요'가 보일 때 (보일 때마다 1회)
  const isEmpty = !isPending && !isError && days.length === 0
  const hasOlder = !!hasNextPage
  const emptySent = useRef(false)
  useEffect(() => {
    if (!isEmpty) {
      emptySent.current = false
      return
    }
    if (emptySent.current) return
    emptySent.current = true
    track('work_journal.empty_view', { goal_category_id: goalId, has_older: hasOlder })
  }, [isEmpty, hasOlder, goalId])

  const copyAndTrack = async (target: 'week' | 'day', text: string) => {
    const ok = await copy(text)
    track('work_journal.copy_result', {
      goal_category_id: goalId,
      copy_target: target,
      result: ok ? 'success' : 'fail',
    })
  }

  const copyWeek = () => {
    const weekStart = startOfWeek(today)
    const week = days.filter((d) => d.date >= weekStart)
    track('work_journal.copy_week_click', {
      goal_category_id: goalId,
      day_count: week.length,
      item_count: week.reduce((n, d) => n + d.items.length, 0),
      was_empty: week.length === 0,
    })
    if (!week.length) {
      toast.show('이번 주 일지가 아직 없어요.')
      return
    }
    copyAndTrack(
      'week',
      journalText(`${goalName} · 업무 일지 · ${formatMonthDay(weekStart)} – ${formatMonthDay(today)}`, week)
    )
  }

  const copyDay = (day: JournalDay) => {
    track('work_journal.copy_day_click', {
      goal_category_id: goalId,
      days_ago: daysAgoOf(day.date, today),
      item_count: day.items.length,
    })
    copyAndTrack('day', journalText(`${goalName} · 업무 일지`, [day]))
  }

  const askDelete = (item: JournalItem, date: string) => {
    const daysAgo = daysAgoOf(date, today)
    track('work_journal.record_delete_click', { goal_category_id: goalId, days_ago: daysAgo })
    setPending({ item, daysAgo })
  }

  const confirmDelete = () => {
    if (!pending) return
    track('work_journal.record_delete_confirm', { goal_category_id: goalId, days_ago: pending.daysAgo })
    del.mutate(pending.item.id, {
      onSuccess: () => track('work_journal.record_delete_result', { goal_category_id: goalId, result: 'success' }),
      onError: (e) =>
        track('work_journal.record_delete_result', {
          goal_category_id: goalId,
          result: 'fail',
          error_status: errorStatusOf(e),
        }),
      onSettled: () => setPending(null),
    })
  }

  return (
    <Card className="p-5 sm:p-6">
      <div className="mb-1 flex items-center justify-between gap-3">
        <h2 className="text-h4">업무 일지</h2>
        <Button variant="secondary" size="sm" onClick={copyWeek} disabled={!days.length}>
          <Copy className="size-3.5" />
          이번 주 복사
        </Button>
      </div>
      <p className="text-ink-3 mb-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px]">
        <span className="flex items-center gap-1">
          <CheckCircle2 className="text-text-brand size-3.5" /> 완료한 Task
        </span>
        <span className="flex items-center gap-1">
          <PenLine className="size-3.5" /> 직접 남긴 기록
        </span>
      </p>

      {isPending ? (
        <div className="grid h-24 place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError ? (
        <div className="bg-bg-sunken flex items-center justify-between gap-3 rounded-2xl px-4 py-4 text-sm">
          <span className="text-ink-2">일지를 불러오지 못했어요.</span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              track('work_journal.retry_click', { goal_category_id: goalId })
              refetch()
            }}
          >
            다시 시도
          </Button>
        </div>
      ) : days.length === 0 ? (
        <p className="bg-bg-sunken text-ink-3 rounded-2xl px-4 py-6 text-center text-sm leading-relaxed">
          {hasNextPage
            ? '최근 2주에는 일지가 없어요. 아래에서 이전 일지를 볼 수 있어요.'
            : '아직 일지가 없어요. 이 목표의 Task를 완료하거나 위에서 한 일을 남기면 날짜별로 쌓여요.'}
        </p>
      ) : (
        <div className="space-y-5">
          {days.map((d) => (
            <JournalDaySection
              key={d.date}
              day={d}
              today={today}
              onCopy={() => copyDay(d)}
              onDelete={(item) => askDelete(item, d.date)}
            />
          ))}
        </div>
      )}

      {hasNextPage && (
        <Button
          variant="ghost"
          fullWidth
          className="mt-4"
          loading={isFetchingNextPage}
          onClick={() => {
            track('work_journal.load_more_click', { goal_category_id: goalId, day_count: days.length })
            fetchNextPage()
          }}
        >
          이전 2주 더 보기
        </Button>
      )}

      <ConfirmDialog
        open={!!pending}
        tone="danger"
        title="이 기록을 지울까요?"
        description={pending ? `"${pending.item.title}" 기록이 사라져요.` : undefined}
        confirmLabel="지우기"
        loading={del.isPending}
        onConfirm={confirmDelete}
        onCancel={(method) => {
          track('work_journal.record_delete_cancel', { goal_category_id: goalId, close_method: method })
          setPending(null)
        }}
      />
    </Card>
  )
}

function JournalDaySection({
  day,
  today,
  onCopy,
  onDelete,
}: {
  day: JournalDay
  today: string
  onCopy: () => void
  onDelete: (item: JournalItem) => void
}) {
  const summary = [
    day.taskCount && `Task ${day.taskCount}`,
    day.recordCount && `기록 ${day.recordCount}`,
    day.minutes && formatDuration(day.minutes),
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <section>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="text-[14px] font-bold">{dayLabel(day.date, today)}</h3>
        <div className="flex items-center gap-1">
          <span className="text-ink-3 text-xs tabular-nums">{summary}</span>
          <button
            type="button"
            onClick={onCopy}
            aria-label={`${formatMonthDay(day.date)} 일지 복사`}
            title="이 날 일지 복사"
            className="text-ink-4 hover:bg-subtle hover:text-ink rounded-lg p-1.5"
          >
            <Copy className="size-3.5" />
          </button>
        </div>
      </div>
      <ul className="border-line divide-line divide-y rounded-2xl border">
        {day.items.map((i) => (
          <li key={`${i.type}-${i.id}`} className="group flex items-start gap-3 px-4 py-3">
            <span className="text-ink-3 w-[92px] shrink-0 pt-0.5 text-[13px] tabular-nums">
              {i.startAt && i.endAt ? `${i.startAt.slice(11, 16)}–${i.endAt.slice(11, 16)}` : '시간 미정'}
            </span>
            {i.type === 'TASK' ? (
              <CheckCircle2 className="text-text-brand mt-0.5 size-4 shrink-0" aria-label="완료한 Task" />
            ) : (
              <PenLine className="text-ink-3 mt-0.5 size-4 shrink-0" aria-label="직접 남긴 기록" />
            )}
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-1.5 text-[15px] font-medium">
                <span className="truncate">
                  {i.emoji && <span className="mr-1">{i.emoji}</span>}
                  {i.title}
                </span>
                {i.routine && (
                  <span className="bg-brand-soft text-text-brand inline-flex shrink-0 items-center gap-0.5 rounded-full px-1.5 py-px text-[10px] font-bold">
                    <Repeat className="size-2.5" />
                    루틴
                  </span>
                )}
              </p>
              {i.memo && <p className="text-ink-3 mt-0.5 text-[13px]">{i.memo}</p>}
            </div>
            {i.minutes ? <span className="text-ink-3 shrink-0 pt-0.5 text-xs">{formatDuration(i.minutes)}</span> : null}
            {i.type === 'RECORD' ? (
              <button
                type="button"
                onClick={() => onDelete(i)}
                aria-label={`${i.title} 기록 지우기`}
                className="text-ink-4 hover:text-danger rounded-lg p-1 opacity-60 group-hover:opacity-100"
              >
                <Trash2 className="size-4" />
              </button>
            ) : (
              <span className="w-6 shrink-0" aria-hidden />
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
