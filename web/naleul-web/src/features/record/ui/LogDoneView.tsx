'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Timer } from 'lucide-react'
import { ArrowLeft, Check, Clock, Sparkles, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import type { TaskAddSurface } from '@/features/brain-dump/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { toast } from '@/stores/toastStore'
import { useParseBrainDump } from '@/features/brain-dump/api'
import { parseLine } from '@/features/brain-dump/parseLine'
import type { GoalCategory } from '@/features/goal/api'
import { formatDuration, formatMonthDay, todayKst } from '@/features/timetable/time'
import { newClientKey, useLogActivities, type ActivityLogResponse } from '../api'

const MAX_LINES = 20
const PLACEHOLDER = '오전 10시 주간 회의 1시간\n기획서 초안 작성 2시간\n어제 코드 리뷰 30분\n운동 30분'

/** 검토 화면의 한 줄 */
interface LogRow {
  clientKey: string
  text: string
  title: string
  date: string | null
  startTime: string | null
  endTime: string | null
  minutes: number | null
  /** "goal:12" · "temp:t1" · "" (목표 없이) */
  link: string
}

/** 연결 값 → goal_match */
const goalMatchOf = (link: string) =>
  link.startsWith('goal:') ? 'existing' : link.startsWith('temp:') ? 'temp' : 'none'

const toHm = (t?: string | null) => (t ? t.slice(0, 5) : null)
const PAST_WORD = /(어제|그제|그저께)/

/**
 * Task 추가 › "한 일 기록" — 이미 한 일을 Brain dump 처럼 한 번에 적어요.
 *   1. 한 줄에 하나씩 적기 → AI 가 제목·시간을 정리하고 목표를 연결 (있는 목표, 없으면 임시 목표 제안)
 *   2. 연결만 확인하고 저장 → TimeTable 의 "실제" 칸과 각 목표(기록형이면 타임라인)에 쌓여요
 */
export function LogDoneView({ goals, surface = 'page' }: { goals: GoalCategory[]; surface?: TaskAddSurface }) {
  const today = todayKst()
  const [text, setText] = useState('')
  const [rows, setRows] = useState<LogRow[] | null>(null)
  const [temps, setTemps] = useState<{ tempKey: string; name: string; emoji?: string | null }[]>([])
  const [done, setDone] = useState<ActivityLogResponse | null>(null)
  const parse = useParseBrainDump()
  const save = useLogActivities()

  const allLines = text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  const lines = allLines.slice(0, MAX_LINES)

  const goParse = (method: 'button' | 'enter') => {
    if (!lines.length) return
    const req = lines.map((line) => {
      const p = parseLine(line, today)
      return {
        clientKey: newClientKey(),
        text: line,
        startTime: p.startTime,
        endTime: p.endTime,
        estimatedMinutes: p.minutes,
      }
    })
    track('task_add_log.parse_click', {
      line_count: lines.length,
      dropped_count: allLines.length - lines.length,
      has_parsed_time: req.some((r) => !!r.startTime),
      submit_method: method,
    })
    parse.mutate(req, {
      onSuccess: (res) => {
        const nextRows: LogRow[] = res.items.map((i) => {
          const original = req.find((r) => r.clientKey === i.clientKey)!
          return {
            clientKey: i.clientKey,
            text: original.text,
            title: i.title,
            // "어제"는 Task 기준 AI 가 오늘로 당겨 오니 서버가 문장으로 다시 읽게 비워요
            date: PAST_WORD.test(original.text) ? null : (i.scheduledDate ?? null),
            startTime: toHm(i.startTime),
            endTime: toHm(i.endTime),
            minutes: i.estimatedMinutes ?? null,
            link:
              i.goal.type === 'EXISTING' && i.goal.goalCategoryId
                ? `goal:${i.goal.goalCategoryId}`
                : i.goal.type === 'TEMP' && i.goal.tempKey
                  ? `temp:${i.goal.tempKey}`
                  : '',
          }
        })
        setRows(nextRows)
        setTemps(res.tempGoals)
        track('task_add_log.parse_result', {
          result: 'success',
          item_count: res.items.length,
          ai_failed: res.aiFailed,
          temp_count: res.tempGoals.length,
          missing_goal_count: nextRows.filter((r) => !r.link).length,
        })
      },
      onError: (e) => {
        track('task_add_log.parse_result', { result: 'fail', error_status: errorStatusOf(e) })
        toast.error(isApiError(e) ? e.message : '정리하지 못했어요. 다시 시도해 주세요.')
      },
    })
  }

  const update = (key: string, patch: Partial<LogRow>) =>
    setRows((rs) => rs && rs.map((r) => (r.clientKey === key ? { ...r, ...patch } : r)))

  const goSave = () => {
    if (!rows?.length) return
    const usedTemps = new Set(rows.filter((r) => r.link.startsWith('temp:')).map((r) => r.link.slice(5)))
    track('task_add_log.save_click', {
      item_count: rows.length,
      missing_goal_count: rows.filter((r) => !r.link).length,
      temp_count: usedTemps.size,
    })
    save.mutate(
      {
        items: rows.map((r) => ({
          clientKey: r.clientKey,
          text: r.text,
          title: r.title.trim() || r.text,
          date: r.date,
          startTime: r.startTime,
          endTime: r.startTime ? r.endTime : null,
          minutes: r.minutes,
          goalCategoryId: r.link.startsWith('goal:') ? Number(r.link.slice(5)) : null,
          tempGoalKey: r.link.startsWith('temp:') ? r.link.slice(5) : null,
        })),
        tempGoals: temps.filter((t) => usedTemps.has(t.tempKey)),
      },
      {
        onSuccess: (res) => {
          track('task_add_log.save_result', {
            result: 'success',
            created_count: res.activities.length,
            temp_count: res.tempGoals.length,
          })
          setDone(res)
          toast.success(`${res.activities.length}개를 기록했어요.`)
        },
        onError: (e) => track('task_add_log.save_result', { result: 'fail', error_status: errorStatusOf(e) }),
      }
    )
  }

  const reset = () => {
    setText('')
    setRows(null)
    setTemps([])
    setDone(null)
  }

  // ── 3. 완료 ──
  if (done) {
    return (
      <Card className="p-5 sm:p-6">
        <p className="flex items-center gap-2 text-[17px] font-bold">
          <span className="bg-success grid size-6 place-items-center rounded-full text-white">
            <Check className="size-3.5" />
          </span>
          {done.activities.length}개를 기록했어요
        </p>
        <ul className="border-line divide-line mt-4 divide-y rounded-2xl border">
          {done.activities.map((a) => (
            <li key={a.activityId} className="flex items-center gap-3 px-4 py-3 text-[14px]">
              <span className="text-ink-3 w-[150px] shrink-0 tabular-nums">
                {a.startAt.slice(0, 10) === today ? '오늘' : formatMonthDay(a.startAt.slice(0, 10))}{' '}
                {a.startAt.slice(11, 16)}–{a.endAt.slice(11, 16)}
              </span>
              <span className="min-w-0 flex-1 truncate font-medium">{a.title}</span>
              {a.goalCategoryId && (
                <Link
                  href={`/goal/${a.goalCategoryId}`}
                  onClick={() => track('task_add_log.done_link_click', { destination: 'goal', surface })}
                  className="text-ink-3 hover:text-ink truncate text-xs"
                >
                  {a.goalCategoryName}
                </Link>
              )}
            </li>
          ))}
        </ul>
        <div className="mt-4 flex gap-2">
          <Button
            onClick={() => {
              track('task_add_log.more_click', { created_count: done.activities.length })
              reset()
            }}
          >
            더 기록하기
          </Button>
          <Link
            href="/"
            onClick={() => track('task_add_log.done_link_click', { destination: 'home', surface })}
            className="text-ink-2 px-3 py-2 text-sm font-semibold"
          >
            TimeTable에서 보기
          </Link>
        </div>
      </Card>
    )
  }

  // ── 2. 목표 연결 확인 ──
  if (rows) {
    return (
      <Card className="p-5 sm:p-6">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-h4">이렇게 기록할게요</h2>
          <button
            type="button"
            onClick={() => {
              track('task_add_log.rewrite_click', { item_count: rows.length })
              setRows(null)
            }}
            className="text-ink-3 hover:text-ink flex items-center gap-1 text-sm"
          >
            <ArrowLeft className="size-4" />
            다시 적기
          </button>
        </div>
        <p className="text-ink-3 mt-1 text-[13px]">
          목표 연결만 확인하세요. 시각이 없는 일은 방금 끝낸 일로, 다른 기록과 겹치지 않게 채워요.
        </p>
        <ul className="mt-4 space-y-2">
          {rows.map((r) => (
            <li key={r.clientKey} className="border-line rounded-2xl border p-3">
              <div className="flex items-center gap-2">
                <input
                  value={r.title}
                  onChange={(e) => update(r.clientKey, { title: e.target.value.slice(0, 60) })}
                  aria-label="제목"
                  className="focus:bg-bg-sunken h-9 min-w-0 flex-1 rounded-lg bg-transparent px-1 text-[15px] font-semibold outline-none"
                />
                <button
                  type="button"
                  onClick={() => {
                    track('task_add_log.row_remove', { line_count: rows.length - 1 })
                    setRows((rs) => rs && rs.filter((x) => x.clientKey !== r.clientKey))
                  }}
                  aria-label="빼기"
                  className="text-ink-4 hover:text-ink rounded-lg p-1"
                >
                  <X className="size-4" />
                </button>
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[12px]">
                <span className="bg-subtle text-ink-2 inline-flex h-6 items-center gap-1 rounded-full px-2 font-semibold">
                  <Clock className="size-3" />
                  {r.date && r.date !== today ? `${formatMonthDay(r.date)} ` : PAST_WORD.test(r.text) ? '지난 날 ' : ''}
                  {r.startTime ? `${r.startTime}${r.endTime ? `–${r.endTime}` : ''}` : '방금'}
                </span>
                {r.minutes && !r.endTime && (
                  <span className="bg-subtle text-ink-2 inline-flex h-6 items-center gap-1 rounded-full px-2 font-semibold">
                    <Timer className="size-3" />
                    {formatDuration(r.minutes)}
                  </span>
                )}
                <select
                  value={r.link}
                  onChange={(e) => {
                    track('task_add_log.link_change', { goal_match: goalMatchOf(e.target.value) })
                    update(r.clientKey, { link: e.target.value })
                  }}
                  aria-label="연결할 목표"
                  className={cn(
                    'border-border-subtle bg-surface ml-auto h-8 max-w-[220px] rounded-lg border px-2 text-[13px]',
                    !r.link && 'text-ink-3'
                  )}
                >
                  <option value="">목표 없이</option>
                  {goals.map((g) => (
                    <option key={g.goalCategoryId} value={`goal:${g.goalCategoryId}`}>
                      {g.emoji ? `${g.emoji} ` : ''}
                      {g.goalCategoryName}
                    </option>
                  ))}
                  {temps.map((t) => (
                    <option key={t.tempKey} value={`temp:${t.tempKey}`}>
                      ＋ 새 임시 목표: {t.name}
                    </option>
                  ))}
                </select>
              </div>
            </li>
          ))}
        </ul>
        <div className="mt-4 flex justify-end">
          <Button onClick={goSave} loading={save.isPending} disabled={!rows.length}>
            {rows.length}개 기록하기
          </Button>
        </div>
      </Card>
    )
  }

  // ── 1. 적기 ──
  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-h4">오늘 한 일을 한 줄씩 적어 주세요</h2>
      <p className="text-ink-3 mt-1 text-[13px]">
        시각(오후 2시)·걸린 시간(30분)·날짜(어제)를 같이 적으면 그대로 기록해요. 목표는 AI가 알아서 연결해요.
      </p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={6}
        placeholder={PLACEHOLDER}
        className="border-border-subtle bg-surface placeholder:text-text-placeholder focus:border-border-focus mt-4 w-full resize-none rounded-2xl border px-4 py-3 text-[15px] leading-relaxed outline-none"
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
            e.preventDefault()
            goParse('enter')
          }
        }}
      />
      <div className="mt-3 flex items-center gap-2">
        <span className="text-ink-3 text-xs tabular-nums">
          {lines.length}/{MAX_LINES}줄 · ⌘+Enter로 정리
        </span>
        <Button
          className="ml-auto"
          variant="ai"
          onClick={() => goParse('button')}
          loading={parse.isPending}
          disabled={!lines.length}
        >
          <Sparkles className="size-4" />
          정리하기
        </Button>
      </div>
    </Card>
  )
}
