'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { cn } from '@/lib/cn'
import { api, isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { toast } from '@/stores/toastStore'
import { goalAccent } from '@/lib/goalTone'
import { goalKeys, type ReflectionMood } from '@/features/goal/api'
import { homeKeys, type ReflectionEntry } from '@/features/home/api'
import { workKeys } from '@/features/work/api'
import { MOODS, moodOf } from '@/features/goal/ui/progress/DailyCheckInCard'
import type { ReflectionTimelineSurface } from './ReflectionsView'

const RETRO_MAX = 1000
const CHECK_IN_MAX = 500
const fmt = (v: number) => String(Number(v.toFixed(2)))
const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']
const longDate = (iso: string) => {
  const d = new Date(`${iso}T00:00:00`)
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})`
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

/**
 * 회고 자세히 보기 (2026-10) — 회고 줄을 누르면 떠요.
 *  - 업무형 회고(RETRO): 내용 고치기 (PATCH /retrospectives/{id}) · 지우기 (DELETE)
 *  - 목표형 오늘 기록(CHECK_IN): 기분 · 한 줄 회고 고치기 (PUT check-in), 지우기 = 그날 회고 + 수치 기록 삭제
 */
export function ReflectionDetailModal({
  entry: e,
  open,
  onClose,
  surface,
  daysAgo,
}: {
  entry: ReflectionEntry
  open: boolean
  onClose: () => void
  surface: ReflectionTimelineSurface
  daysAgo: number
}) {
  const qc = useQueryClient()
  const isRetro = e.kind === 'RETRO'
  const [editing, setEditing] = useState(false)
  const [note, setNote] = useState(e.note ?? '')
  const [mood, setMood] = useState<ReflectionMood | null>(e.mood)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const props = {
    surface,
    entry_kind: e.kind,
    days_ago: daysAgo,
    goal_mode: e.goalMode ?? undefined,
  }

  // 열 때마다 새로 그려요 (부모가 open 일 때만 붙여서 입력 상태가 처음 값으로 시작)
  useEffect(() => {
    track('reflection_detail.view', props)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: homeKeys.all })
    qc.invalidateQueries({ queryKey: workKeys.all })
    qc.invalidateQueries({ queryKey: goalKeys.progress(e.goalCategoryId) })
    qc.invalidateQueries({ queryKey: goalKeys.detail(e.goalCategoryId) })
  }

  const save = useMutation({
    mutationFn: async () => {
      if (isRetro && e.retrospectiveId) {
        await api.patch(`/v1/retrospectives/${e.retrospectiveId}`, {
          content: note.trim(),
          goalCategoryId: e.goalCategoryId,
        })
      } else {
        await api.put(`/v1/goal-categories/${e.goalCategoryId}/check-in`, {
          date: e.date,
          mood,
          note: note.trim() || null,
        })
      }
    },
    onSuccess: () => {
      toast.success('회고를 고쳤어요.')
      track('reflection_detail.edit_save', {
        ...props,
        content_length: note.trim().length,
      })
      setEditing(false)
      onClose()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      track('reflection_detail.edit_fail', { ...props, http_status: errorStatusOf(err) })
    },
    onSettled: invalidate,
  })

  const remove = useMutation({
    mutationFn: async () => {
      if (isRetro && e.retrospectiveId) {
        await api.delete(`/v1/retrospectives/${e.retrospectiveId}`)
        return
      }
      if (e.mood || e.note) {
        // mood · note 를 둘 다 비우면 그날 회고를 지워요 (수치가 있으면 같이 보내야 400 이 안 나요)
        await api.put(`/v1/goal-categories/${e.goalCategoryId}/check-in`, {
          date: e.date,
          value: e.value ?? undefined,
          mood: null,
          note: null,
        })
      }
      if (e.value != null) await api.delete(`/v1/goal-categories/${e.goalCategoryId}/metric-logs/${e.date}`)
    },
    onSuccess: () => {
      toast.success('회고를 지웠어요.')
      track('reflection_detail.delete_confirm', props)
      setConfirmDelete(false)
      onClose()
    },
    onError: (err) => {
      toast.error(errorMessage(err))
      track('reflection_detail.delete_fail', { ...props, http_status: errorStatusOf(err) })
    },
    onSettled: invalidate,
  })

  const max = isRetro ? RETRO_MAX : CHECK_IN_MAX
  const canSave = isRetro ? note.trim().length > 0 : !!(note.trim() || mood || e.value != null)
  const currentMood = moodOf(e.mood)

  return (
    <>
      <Modal
        open={open && !confirmDelete}
        onClose={(m) => {
          track('reflection_detail.close', {
            ...props,
            close_method: m,
            editing,
          })
          onClose()
        }}
        title={
          isRetro
            ? e.reviewType === 'WEEKLY'
              ? '주간 회고'
              : e.reviewType === 'MONTHLY'
                ? '월간 회고'
                : '회고'
            : '오늘 기록'
        }
        description={longDate(e.date)}
        dismissible={!save.isPending}
        footer={
          editing ? (
            <>
              <Button
                variant="outline"
                onClick={() => {
                  setEditing(false)
                  setNote(e.note ?? '')
                  setMood(e.mood)
                  track('reflection_detail.edit_cancel', props)
                }}
                disabled={save.isPending}
              >
                취소
              </Button>
              <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!canSave}>
                저장
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                className="text-danger mr-auto"
                onClick={() => {
                  setConfirmDelete(true)
                  track('reflection_detail.delete_click', props)
                }}
              >
                삭제
              </Button>
              <Button
                onClick={() => {
                  setEditing(true)
                  track('reflection_detail.edit_click', props)
                }}
              >
                수정
              </Button>
            </>
          )
        }
      >
        <div className="flex flex-col gap-3">
          <Link
            href={`/goal/${e.goalCategoryId}`}
            onClick={() => track('reflection_detail.goal_link_click', props)}
            className="text-label-sm text-text-secondary flex w-fit max-w-full min-w-0 items-center gap-1.5 hover:underline"
          >
            <span
              className="size-2 shrink-0 rounded-full"
              style={{ backgroundColor: goalAccent(e.colorCode) }}
              aria-hidden
            />
            <span className="truncate">
              {e.goalEmoji ? `${e.goalEmoji} ` : ''}
              {e.goalName}
            </span>
          </Link>

          {e.value != null && (
            <span className="bg-badge-bg flex w-fit items-center gap-1 rounded-full px-2.5 py-1 text-[13px]">
              <span className="text-text-secondary font-medium">{e.metricName ?? '수치'}</span>
              <span className="text-text-primary font-semibold">
                {fmt(e.value)}
                {e.unit ?? ''}
              </span>
            </span>
          )}

          {!isRetro &&
            (editing ? (
              <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="기분">
                {MOODS.map((m) => (
                  <button
                    key={m.value}
                    type="button"
                    role="radio"
                    aria-checked={mood === m.value}
                    onClick={() => setMood(mood === m.value ? null : m.value)}
                    className={cn(
                      'h-8 rounded-full border px-3 text-[13px] font-semibold transition-colors',
                      mood === m.value
                        ? 'border-text-brand bg-ai-tint-soft text-text-brand'
                        : 'border-border-subtle bg-bg-surface text-text-primary hover:bg-bg-sunken'
                    )}
                  >
                    {m.emoji} {m.label}
                  </button>
                ))}
              </div>
            ) : (
              currentMood && (
                <span className="bg-badge-bg w-fit rounded-full px-2.5 py-1 text-[13px] font-semibold">
                  {currentMood.emoji} {currentMood.label}
                </span>
              )
            ))}

          {editing ? (
            <label className="block">
              <textarea
                value={note}
                autoFocus
                onChange={(ev) => setNote(ev.target.value.slice(0, max))}
                rows={6}
                placeholder={isRetro ? '오늘을 돌아보며 적어 보세요' : '무엇을 했나요? (한 줄 회고)'}
                className="border-border-subtle bg-bg-surface text-body-md focus:border-text-brand w-full resize-y rounded-xl border px-3 py-2.5 leading-relaxed outline-none"
              />
              <span className="text-text-tertiary mt-1 block text-right text-[11px] tabular-nums">
                {note.length} / {max}
              </span>
            </label>
          ) : e.note ? (
            <p className="text-ink-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap">{e.note}</p>
          ) : (
            <p className="text-ink-4 text-[13px]">{isRetro ? '내용이 없어요' : '수치만 남겼어요'}</p>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={open && confirmDelete}
        title="이 회고를 지울까요?"
        description={
          isRetro ? '지운 회고는 되돌릴 수 없어요.' : '그날 남긴 기분 · 한 줄 회고와 수치 기록이 함께 지워져요.'
        }
        confirmLabel="삭제"
        tone="danger"
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={(m) => {
          setConfirmDelete(false)
          track('reflection_detail.delete_cancel', {
            ...props,
            cancel_method: m,
          })
        }}
      />
    </>
  )
}
