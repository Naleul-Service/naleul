'use client'

import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link2 } from 'lucide-react'
import { Trash2, X } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { isHabitGoal, useGoalCategories } from '@/features/goal/api'
import { isOngoing } from '@/features/goal/format'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TimetableSurface } from '../analytics'
import { useDeleteActivity, useSaveActivity } from '../api'
import { formatMinutes, minutesFrom, nowKst, timeToMinutes, toDateTime } from '../time'
import type { ActualActivity } from '../types'

/** 폼을 열 때의 초기값 */
export interface ActivityDraft {
  /** 있으면 수정, 없으면 새로 기록 */
  activity?: ActualActivity
  date: string
  /** 그날 0시 기준 분 (끝이 1440 이상이면 다음 날) */
  start: number
  end: number
  /** "이 시간에 다른 일을 했어요"로 열었을 때 원래 계획했던 Task */
  replaced?: { id: number; name: string } | null
}

/** 수정할 기록 → 폼 초기값 */
export function draftOf(a: ActualActivity): ActivityDraft {
  const date = a.startAt.slice(0, 10)
  return {
    activity: a,
    date,
    start: minutesFrom(date, a.startAt),
    end: minutesFrom(date, a.endAt),
    replaced: a.replacedTaskId ? { id: a.replacedTaskId, name: a.replacedTaskName ?? '계획했던 일' } : null,
  }
}

const input =
  'border-border-subtle bg-surface placeholder:text-text-placeholder focus:border-border-focus h-10 w-full min-w-0 rounded-input border px-3 text-[14px] outline-none'
const label = 'text-ink-3 mb-1 block text-[12px] font-medium'

/** 오늘(KST)부터 며칠 전인지 (오늘 0) */
const daysAgo = (ymd: string) => Math.round((Date.parse(nowKst().date) - Date.parse(ymd)) / 86_400_000)

/**
 * 실제로 한 일 기록 · 수정 (팝오버 안에서 바로).
 * Enter 저장(메모 칸은 Shift+Enter 줄바꿈), Esc 닫기. 삭제는 같은 자리에서 한 번 더 확인.
 * 끝 시각이 시작보다 이르면 다음 날로 봐요 (예: 23:30 ~ 00:30).
 */
export function ActivityForm({
  draft,
  onDone,
  surface,
}: {
  draft: ActivityDraft
  onDone: () => void
  /** Amplitude surface — 이 폼을 띄운 화면 */
  surface?: TimetableSurface
}) {
  const save = useSaveActivity()
  const remove = useDeleteActivity()
  // 기본 생활 습관으로도 분류할 수 있게 (2026-10)
  const goals = useGoalCategories({ includeHabit: true })
  const a = draft.activity

  const [title, setTitle] = useState(a?.title ?? '')
  const [emoji, setEmoji] = useState(a?.emoji ?? '')
  const [date, setDate] = useState(draft.date)
  const [start, setStart] = useState(formatMinutes(draft.start))
  const [end, setEnd] = useState(formatMinutes(draft.end))
  const [goalId, setGoalId] = useState<number | null>(a?.goalCategoryId ?? null)
  const [replaced, setReplaced] = useState(draft.replaced ?? null)
  const [memo, setMemo] = useState(a?.memo ?? '')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  /** 닫기(activity_form.close) 판단용 — 마지막 닫는 동작 · 저장/삭제로 닫혔는지 · 고친 내용 */
  const closeRef = useRef<{ method: 'x_button' | 'backdrop' | 'esc' | null; done: boolean; dirty: boolean }>({
    method: null,
    done: false,
    dirty: false,
  })
  const lastErrorRef = useRef<string | null>(null)

  const now = nowKst()
  const s = timeToMinutes(start)
  let e = timeToMinutes(end)
  if (e <= s) e += 1440 // 자정을 넘긴 기록
  const startAt = toDateTime(date, s)
  const endAt = toDateTime(date, e)
  const nowAt = toDateTime(now.date, now.minutes)
  const minutes = e - s

  const error = !title.trim()
    ? null
    : endAt > nowAt
      ? '아직 오지 않은 시간은 기록할 수 없어요.'
      : minutes > 1440
        ? '24시간까지만 기록할 수 있어요.'
        : null
  const valid = !!title.trim() && !error && !!date && !!start && !!end
  const isDirty =
    title !== (a?.title ?? '') ||
    emoji !== (a?.emoji ?? '') ||
    date !== draft.date ||
    start !== formatMinutes(draft.start) ||
    end !== formatMinutes(draft.end) ||
    goalId !== (a?.goalCategoryId ?? null) ||
    (replaced?.id ?? null) !== (draft.replaced?.id ?? null) ||
    memo !== (a?.memo ?? '')

  useEffect(() => {
    closeRef.current.dirty = isDirty
  }, [isDirty])

  // 입력 오류 문구가 뜰 때마다 한 번
  useEffect(() => {
    if (error && error !== lastErrorRef.current) track('activity_form.error_view', { surface, validation_error: error })
    lastErrorRef.current = error
  }, [error, surface])

  // 팝오버(X · 바깥 · Esc)로 닫히면 activity_form.close — 닫는 동작을 여기서 보고, 사라질 때 보내요
  useEffect(() => {
    const st = closeRef.current
    const dialog = () => formRef.current?.closest('[role="dialog"]') ?? formRef.current
    const onDown = (ev: PointerEvent) => {
      const box = dialog()
      if (!(ev.target instanceof Node) || !box) return
      if (!box.contains(ev.target)) st.method = 'backdrop'
      else if (ev.target instanceof Element && ev.target.closest('button[aria-label="닫기"]')?.parentElement === box)
        st.method = 'x_button'
    }
    const onKey = (ev: globalThis.KeyboardEvent) => {
      if (ev.key === 'Escape' && !ev.defaultPrevented) st.method = 'esc'
    }
    document.addEventListener('pointerdown', onDown, true)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown, true)
      document.removeEventListener('keydown', onKey)
      if (st.method && !st.done)
        track('activity_form.close', { surface, is_new: !a, close_method: st.method, is_dirty: st.dirty })
    }
  }, [surface, a])

  const finish = () => {
    closeRef.current.done = true
    onDone()
  }

  const submit = (ev?: FormEvent) => {
    ev?.preventDefault()
    if (!valid || save.isPending) return
    track('activity_form.save_click', {
      surface,
      is_new: !a,
      duration_minutes: minutes,
      crosses_midnight: e > 1440,
      has_goal: goalId != null,
      is_habit_goal: isHabitGoal((goals.data ?? []).find((g) => g.goalCategoryId === goalId)),
      memo_length: memo.trim().length,
      is_replacement: !!replaced,
      days_ago: daysAgo(date),
    })
    const activity_action = a ? 'update' : 'create'
    save.mutate(
      {
        activityId: a?.activityId,
        title: title.trim(),
        emoji: emoji.trim() || null,
        startAt,
        endAt,
        goalCategoryId: goalId,
        replacedTaskId: replaced?.id ?? null,
        memo: memo.trim() || null,
      },
      {
        onSuccess: () => {
          track('activity_form.result', { surface, activity_action, result: 'success' })
          finish()
        },
        onError: (err) =>
          track('activity_form.result', {
            surface,
            activity_action,
            result: 'fail',
            error_status: errorStatusOf(err),
          }),
      }
    )
  }

  const confirmRemove = () => {
    if (!a) return
    track('activity_form.delete_confirm', { surface, days_ago: daysAgo(draft.date) })
    remove.mutate(a.activityId, {
      onSuccess: () => {
        track('activity_form.result', { surface, activity_action: 'delete', result: 'success' })
        finish()
      },
      onError: (err) =>
        track('activity_form.result', {
          surface,
          activity_action: 'delete',
          result: 'fail',
          error_status: errorStatusOf(err),
        }),
    })
  }

  const onKeyDown = (ev: KeyboardEvent<HTMLFormElement>) => {
    if (ev.nativeEvent.isComposing) return
    if (ev.key === 'Enter' && !ev.shiftKey && ev.target instanceof HTMLTextAreaElement) {
      ev.preventDefault()
      submit()
    }
  }

  const durationText =
    minutes >= 60 ? `${Math.floor(minutes / 60)}시간${minutes % 60 ? ` ${minutes % 60}분` : ''}` : `${minutes}분`
  const activeGoals = (goals.data ?? []).filter((g) => isOngoing(g.goalCategoryStatus) || g.goalCategoryId === goalId)

  return (
    <form ref={formRef} onSubmit={submit} onKeyDown={onKeyDown} className="space-y-3 p-5">
      {/* 오른쪽 위 닫기(X) 버튼 자리 비워 두기 */}
      <div className="pr-8">
        <p className="text-success text-[12px] font-bold">✓ {a ? '실제로 한 일' : '실제 한 일 추가'}</p>
        <p className="text-ink-3 mt-0.5 text-[12px]">계획과 상관없이 그 시간에 실제로 한 일을 남겨요.</p>
      </div>

      {replaced && (
        <p className="bg-subtle flex items-center gap-1.5 rounded-xl px-3 py-2 text-[13px]">
          <Link2 className="text-ink-3 size-3.5 shrink-0" />
          <span className="text-ink-3 shrink-0">계획</span>
          <b className="min-w-0 flex-1 truncate">{replaced.name}</b>
          <span className="text-ink-3 shrink-0">대신</span>
          <button
            type="button"
            onClick={() => {
              track('activity_form.unlink_click', { surface })
              setReplaced(null)
            }}
            aria-label="계획 연결 끊기"
            className="text-ink-3 hover:text-ink rounded p-0.5"
          >
            <X className="size-3.5" />
          </button>
        </p>
      )}

      <div className="grid grid-cols-[52px_minmax(0,1fr)] gap-2">
        <label>
          <span className={label}>이모지</span>
          <input
            value={emoji}
            onChange={(ev) => setEmoji(ev.target.value)}
            maxLength={16}
            placeholder="🙂"
            className={cn(input, 'px-1 text-center placeholder:opacity-40')}
          />
        </label>
        <label>
          <span className={label}>무엇을 했나요?</span>
          <input
            autoFocus
            value={title}
            onChange={(ev) => setTitle(ev.target.value)}
            maxLength={60}
            placeholder="예) 친구 만남, 유튜브, 낮잠"
            className={input}
          />
        </label>
      </div>

      <label className="block">
        <span className={label}>날짜</span>
        <input
          type="date"
          value={date}
          max={now.date}
          onChange={(ev) => setDate(ev.target.value || draft.date)}
          className={input}
        />
      </label>
      <div className="grid grid-cols-2 gap-2">
        <label>
          <span className={label}>시작</span>
          <input type="time" value={start} onChange={(ev) => setStart(ev.target.value)} className={input} />
        </label>
        <label>
          <span className={label}>끝 {e >= 1440 && <span className="text-ink-4">(다음 날)</span>}</span>
          <input type="time" value={end} onChange={(ev) => setEnd(ev.target.value)} className={input} />
        </label>
      </div>

      <label className="block">
        <span className={label}>목표 (선택)</span>
        <select
          value={goalId ?? ''}
          onChange={(ev) => setGoalId(ev.target.value ? Number(ev.target.value) : null)}
          className={input}
        >
          <option value="">연결 안 함</option>
          {activeGoals.map((g) => (
            <option key={g.goalCategoryId} value={g.goalCategoryId}>
              {g.emoji ? `${g.emoji} ` : ''}
              {g.goalCategoryName}
            </option>
          ))}
        </select>
      </label>

      <label className="block">
        <span className={label}>메모 (선택)</span>
        <textarea
          value={memo}
          onChange={(ev) => setMemo(ev.target.value)}
          rows={2}
          maxLength={200}
          placeholder="왜 계획과 달라졌는지 적어 두면 패턴을 보기 좋아요"
          className={cn(input, 'h-auto resize-none py-2 leading-relaxed')}
        />
      </label>

      {error && (
        <p role="alert" className="text-danger text-[13px] font-medium">
          {error}
        </p>
      )}

      {confirmDelete ? (
        <div className="border-border-subtle bg-danger-soft/60 flex flex-wrap items-center gap-2 rounded-xl border px-3 py-2.5">
          <p className="min-w-0 flex-1 text-[14px] font-semibold">이 기록을 지울까요?</p>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => {
              track('activity_form.delete_cancel', { surface })
              setConfirmDelete(false)
            }}
          >
            취소
          </Button>
          <Button type="button" variant="danger" size="sm" autoFocus loading={remove.isPending} onClick={confirmRemove}>
            삭제
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-2 pt-1">
          {a && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-danger hover:bg-danger-soft"
              onClick={() => {
                track('activity_form.delete_click', { surface, days_ago: daysAgo(draft.date) })
                setConfirmDelete(true)
              }}
            >
              <Trash2 className="size-4" />
              삭제
            </Button>
          )}
          <span className="text-ink-3 ml-auto text-[12px] tabular-nums">{valid ? durationText : ''}</span>
          <Button type="submit" size="sm" loading={save.isPending} disabled={!valid}>
            {a ? '저장' : '기록'}
          </Button>
        </div>
      )}
    </form>
  )
}
