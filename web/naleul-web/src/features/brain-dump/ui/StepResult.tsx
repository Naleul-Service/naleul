'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { CalendarX2, RotateCcw } from 'lucide-react'
import { ArrowRight, CalendarClock, Sparkles } from '@/components/icons'
import { Button, buttonClass } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useReplan, useTimetable } from '@/features/timetable/api'
import { visibleHours, type Proposal } from '@/features/timetable/layout'
import { addDays, formatMonthDay, hm } from '@/features/timetable/time'
import { TimeGrid } from '@/features/timetable/ui/TimeGrid'
import { useTimetableInteractions } from '@/features/timetable/ui/useTimetableInteractions'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TaskAddSurface } from '../analytics'
import type { ConfirmResponse } from '../types'

const MAX_DAYS = 7

/** a → b 가 며칠 뒤인지 (YYYY-MM-DD) */
const daysBetween = (a: string, b: string) =>
  Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000)

/**
 * 3단계 · TimeBlock 확인 (명세 3-2)
 *  - 시간을 정하지 않은 Task 는 바로 놓지 않고 점선으로 먼저 보여 줘요 → 끌어서 고친 뒤 "이대로 진행할까요?"
 *    예 = 그 자리에 저장 · 아니요 = Task 는 저장된 채 시간 미정
 *  - 날짜별 TimeTable 미리보기 (새로 만든 블록은 NEW) — 캘린더와 같은 격자라 끌어서 옮길 수 있어요
 *  - 블록마다 배치 이유 · 다른 날로 배치된 Task · 시간 미정 Task
 *  - 임시 목표 안내 → AI 목표 만들기로 구체화
 */
export function StepResult({
  result,
  today,
  onAddMore,
  surface = 'page',
}: {
  result: ConfirmResponse
  today: string
  onAddMore: () => void
  /** 분석용 — task_add_result.* 의 surface */
  surface?: TaskAddSurface
}) {
  const pending = useMemo(() => result.pending ?? [], [result])
  const titleOf = useMemo(() => new Map(pending.map((p) => [p.taskId, p.title])), [pending])
  // 점선 확인 단계: proposing(확인 중) → applied(이대로 진행) / declined(아니요)
  const [phase, setPhase] = useState<'proposing' | 'applied' | 'declined' | null>(pending.length ? 'proposing' : null)
  const [finalProposals, setFinalProposals] = useState<Proposal[]>([])
  const [previewUnscheduled, setPreviewUnscheduled] = useState<number[]>([])
  // 다른 날로 넘어간 제안도 격자에 보이도록 기간을 넓혀요
  const [proposalDates, setProposalDates] = useState<string[]>([])

  const newIds = useMemo(
    () =>
      new Set([
        ...result.placements.map((p) => p.taskId),
        ...result.movedToOtherDays.map((p) => p.taskId),
        ...result.unscheduled.map((u) => u.taskId),
        ...pending.map((u) => u.taskId),
      ]),
    [result, pending]
  )

  // 보여줄 기간: 결과가 있는 첫날부터 마지막 날까지 (최대 7일)
  const dates = useMemo(() => {
    const all = [
      ...result.placements.map((p) => p.date),
      ...result.movedToOtherDays.map((p) => p.date),
      ...[...result.unscheduled, ...pending].map((u) => u.date).filter((d): d is string => !!d),
      ...proposalDates,
    ].sort()
    return all.length ? all : [today]
  }, [result, pending, proposalDates, today])
  const start = dates[0]
  const lastWanted = dates[dates.length - 1]
  const end = lastWanted > addDays(start, MAX_DAYS - 1) ? addDays(start, MAX_DAYS - 1) : lastWanted

  const { data, isPending } = useTimetable(start, end)
  const rawDays = useMemo(() => (data?.days ?? []).filter((d) => d.date >= start && d.date <= end), [data, start, end])
  const {
    days,
    selection,
    onSelectTask,
    onSelectFixed,
    onDrop,
    close,
    overlays,
    groupIds,
    previewFill,
    proposals,
    focusIds,
    fadeOthers,
    gridEvents,
  } = useTimetableInteractions(rawDays, start, { surface: 'task_add_result' })

  // 확인 바에서 Esc 로 닫으면 final 이 비어 와요 → 마지막 점선 자리를 기억해 둬요 (dragged_count 용)
  const proposalsRef = useRef<Proposal[] | null>(null)
  useEffect(() => {
    if (proposals) proposalsRef.current = proposals
  }, [proposals])
  const previewUnscheduledRef = useRef<number[]>([])

  // 3단계 노출 — 결과마다 한 번
  const viewSent = useRef(false)
  useEffect(() => {
    if (viewSent.current) return
    viewSent.current = true
    track('task_add_result.view', {
      surface,
      placed_count: result.placements.length,
      moved_count: result.movedToOtherDays.length,
      unscheduled_count: result.unscheduled.length,
      pending_count: (result.pending ?? []).length,
      temp_count: result.tempGoals.length,
    })
  }, [result, surface])

  // 7일 밖 결과 안내가 보일 때 한 번
  const rangeCut = lastWanted > end
  const rangeCutSent = useRef(false)
  useEffect(() => {
    if (!rangeCut || rangeCutSent.current) return
    rangeCutSent.current = true
    track('task_add_result.range_cut_view', { day_count: daysBetween(start, lastWanted) + 1 })
  }, [rangeCut, start, lastWanted])

  // 들어오자마자 한 번만 점선 제안을 받아요 (개발 모드에서 effect 가 두 번 돌아도 한 번만)
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current || !pending.length) return
    asked.current = true
    previewFill(
      { taskIds: pending.map((p) => p.taskId) },
      {
        kind: 'new',
        onPreview: (r) => {
          setProposalDates(r.proposals.map((p) => p.date))
          setPreviewUnscheduled(r.unscheduled)
          previewUnscheduledRef.current = r.unscheduled
          track('task_add_result.proposal_ready', {
            surface,
            result: 'success',
            placed_count: r.proposals.length,
            unscheduled_count: r.unscheduled.length,
          })
        },
        onResolve: (applied, final) => {
          setPhase(applied ? 'applied' : 'declined')
          setFinalProposals(applied ? final : [])
          const shown = final.length ? final : (proposalsRef.current ?? [])
          track('task_add_result.proposal_resolve', {
            surface,
            proposal_applied: applied,
            dragged_count: shown.filter((p) => p.start !== p.proposedStart || p.end !== p.proposedEnd).length,
            placed_count: applied ? final.length : 0,
            unscheduled_count: applied ? previewUnscheduledRef.current.length : pending.length,
          })
        },
        // 자리를 못 받아 왔으면 Task 는 저장된 채 시간 미정으로 둬요
        onError: () => {
          setPhase('declined')
          track('task_add_result.proposal_ready', {
            surface,
            result: 'fail',
            placed_count: 0,
            unscheduled_count: pending.length,
          })
        },
      }
    )
  }, [pending, previewFill, surface])
  const { startHour, endHour } = useMemo(() => visibleHours(days), [days])

  const replan = useReplan()
  const replanDates = [...new Set(dates)].filter((d) => d >= today)

  const placedNow = result.placements.filter((p) => !p.locked)
  const lockedNow = result.placements.filter((p) => p.locked)
  const proposing = phase === 'proposing'
  // 점선 제안(확인 중이면 지금 자리, 진행했으면 최종 자리)
  const shownProposals = proposing ? (proposals ?? []) : finalProposals
  const leftUnscheduled =
    phase === 'declined'
      ? pending
      : pending.filter(
          (p) => previewUnscheduled.includes(p.taskId) && !shownProposals.some((s) => s.taskId === p.taskId)
        )
  const proposalRow = (p: Proposal) => (
    <ReasonRow
      key={p.taskId}
      title={titleOf.get(p.taskId) ?? '새 Task'}
      when={`${formatMonthDay(p.start.slice(0, 10))} ${hm(p.start)}–${hm(p.end)}`}
      reason={p.start !== p.proposedStart ? '직접 옮긴 시간이에요' : p.reason}
      dashed={proposing}
    />
  )

  return (
    <div className="space-y-4">
      <Card className="p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            {proposing ? (
              <>
                <h2 className="text-h4">
                  {proposals ? '점선 자리에 놓을까요?' : '놓을 자리를 찾고 있어요…'}
                </h2>
                <p className="text-ink-3 mt-1 text-sm">
                  Task {pending.length + result.placements.length}개는 저장됐어요. 시간을 정하지 않은 {pending.length}
                  개는 아직 TimeBlock이 아니에요 — 점선을 끌어서 고친 뒤 아래에서 &quot;이대로 진행&quot;을 눌러 주세요.
                </p>
              </>
            ) : phase === 'declined' ? (
              <>
                <h2 className="text-h4">Task를 추가했어요</h2>
                <p className="text-ink-3 mt-1 text-sm">
                  시간은 정하지 않았어요. 격자의 &quot;시간 미정&quot;에서 원하는 시간으로 끌어다 놓을 수 있어요.
                </p>
              </>
            ) : (
              <>
                <h2 className="text-h4">TimeBlock을 만들었어요</h2>
                <p className="text-ink-3 mt-1 text-sm">
                  {result.placements.length + finalProposals.length}개를 배치했어요
                  {result.movedToOtherDays.length > 0 && ` · 다른 날로 ${result.movedToOtherDays.length}개`}
                  {result.unscheduled.length + leftUnscheduled.length > 0 &&
                    ` · 시간 미정 ${result.unscheduled.length + leftUnscheduled.length}개`}
                  . 블록을 끌어서 옮기면 그 시간에 고정돼요.
                </p>
              </>
            )}
          </div>
          <div className="flex gap-2">
            {replanDates.length > 0 && !proposing && (
              <Button
                variant="secondary"
                onClick={() => {
                  track('task_add_result.replan_click', { surface, day_count: replanDates.length })
                  replan.mutate(replanDates, {
                    onSuccess: () => track('task_add_result.replan_result', { surface, result: 'success' }),
                    onError: (e) =>
                      track('task_add_result.replan_result', {
                        surface,
                        result: 'fail',
                        error_status: errorStatusOf(e),
                      }),
                  })
                }}
                loading={replan.isPending}
              >
                <RotateCcw className="size-4" />
                다시 배치
              </Button>
            )}
          </div>
        </div>

        {/* 임시 목표 안내 */}
        {result.tempGoals.map((t) => (
          <div
            key={t.goalCategoryId}
            className="bg-warning-soft mt-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl px-4 py-3"
          >
            <p className="text-sm text-warning-ink-strong">
              <b>
                {t.emoji ? `${t.emoji} ` : ''}
                {t.name}
              </b>
              은(는) 임시 목표로 만들어졌어요. 목표를 구체화해볼까요?
            </p>
            <Link
              href={`/goal/new?sourceGoalId=${t.goalCategoryId}`}
              className={buttonClass('ai', 'sm')}
              onClick={() =>
                track('task_add_result.temp_refine_click', { surface, goal_category_id: t.goalCategoryId })
              }
            >
              <Sparkles className="size-3.5" />
              AI로 구체화하기
            </Link>
          </div>
        ))}
      </Card>

      {/* 미리보기 격자 */}
      {isPending ? (
        <Card className="grid min-h-[320px] place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </Card>
      ) : (
        <TimeGrid
          {...gridEvents}
          days={days}
          startHour={startHour}
          endHour={endHour}
          selection={selection}
          onSelectTask={onSelectTask}
          onSelectFixed={onSelectFixed}
          onDrop={onDrop}
          onDragStart={close}
          highlightIds={newIds}
          groupIds={groupIds}
          focusIds={focusIds}
          fadeOthers={fadeOthers}
          maxHeightClass="max-h-[560px]"
        />
      )}
      {rangeCut && (
        <p className="text-ink-3 text-xs">
          {formatMonthDay(addDays(end, 1))} 이후에 놓인 Task는 캘린더에서 확인할 수 있어요.
        </p>
      )}

      {/* 배치 이유 */}
      <Card className="p-5 sm:p-6">
        <h3 className="text-[15px] font-bold">{proposing ? '이렇게 놓을게요 (아직 저장 전)' : '이렇게 배치했어요'}</h3>
        <ul className="mt-3 space-y-2.5">
          {shownProposals.map(proposalRow)}
          {placedNow.map((p) => (
            <ReasonRow
              key={p.taskId}
              title={p.title}
              when={`${formatMonthDay(p.date)} ${hm(p.start)}–${hm(p.end)}`}
              reason={p.reason}
            />
          ))}
          {lockedNow.map((p) => (
            <ReasonRow
              key={p.taskId}
              title={p.title}
              when={`${formatMonthDay(p.date)} ${hm(p.start)}–${hm(p.end)}`}
              reason="직접 정한 시간이에요"
            />
          ))}
          {result.placements.length === 0 && shownProposals.length === 0 && (
            <li className="text-ink-3 text-sm">
              {proposing && !proposals ? '자리를 찾는 중이에요…' : '그날 안에 놓인 Task가 없어요.'}
            </li>
          )}
        </ul>

        {result.movedToOtherDays.length > 0 && (
          <>
            <h3 className="mt-6 flex items-center gap-1.5 text-[15px] font-bold">
              <CalendarClock className="size-4" />
              다른 날로 배치된 Task
            </h3>
            <ul className="mt-3 space-y-2.5">
              {result.movedToOtherDays.map((p) => (
                <ReasonRow
                  key={p.taskId}
                  title={p.title}
                  when={`${formatMonthDay(p.date)} ${hm(p.start)}–${hm(p.end)}`}
                  reason={p.reason}
                />
              ))}
            </ul>
          </>
        )}

        {result.unscheduled.length + leftUnscheduled.length > 0 && (
          <>
            <h3 className="mt-6 flex items-center gap-1.5 text-[15px] font-bold">
              <CalendarX2 className="size-4" />
              시간 미정
            </h3>
            <p className="text-ink-3 mt-1 text-xs">
              {phase === 'declined' ? '시간을 정하지 않고 날짜만 정해 뒀어요.' : '빈 시간이 없어서 날짜만 정했어요.'}{' '}
              격자의 &quot;시간 미정&quot;에서 끌어다 놓을 수 있어요.
            </p>
            <ul className="mt-2 space-y-1.5">
              {[...result.unscheduled, ...leftUnscheduled].map((u) => (
                <li key={u.taskId} className="text-sm">
                  {u.title}
                  {u.date && <span className="text-ink-3"> · {formatMonthDay(u.date)}</span>}
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      {/* 점선 확인 중에는 화면 아래 "이대로 진행할까요?" 바가 대신해요 */}
      {!proposing && (
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              track('task_add_result.add_more_click', {
                surface,
                placed_count: result.placements.length + finalProposals.length,
              })
              onAddMore()
            }}
          >
            Task 더 추가하기
          </Button>
          <Link
            href={`/calendar?view=week&date=${start}`}
            className={cn(buttonClass('primary'))}
            onClick={() => track('task_add_result.calendar_click', { days_from_today: daysBetween(today, start) })}
          >
            캘린더에서 보기
            <ArrowRight className="size-4" />
          </Link>
        </div>
      )}

      {overlays}
    </div>
  )
}

function ReasonRow({
  title,
  when,
  reason,
  dashed,
}: {
  title: string
  when: string
  reason?: string | null
  /** 아직 저장 전인 점선 제안 */
  dashed?: boolean
}) {
  return (
    <li className="flex items-start gap-3">
      {dashed ? (
        <span className="border-brand text-text-brand mt-1 shrink-0 rounded border border-dashed px-1 text-[9px] leading-[12px] font-bold">
          제안
        </span>
      ) : (
        <span className="bg-danger mt-1 shrink-0 rounded px-1 text-[9px] leading-[14px] font-bold text-white">NEW</span>
      )}
      <div className="min-w-0">
        <p className="text-sm font-semibold">
          {title}
          <span className="text-ink-3 ml-2 text-xs font-normal tabular-nums">{when}</span>
        </p>
        {reason && <p className="text-ink-3 mt-0.5 text-xs leading-relaxed">{reason}</p>}
      </div>
    </li>
  )
}
