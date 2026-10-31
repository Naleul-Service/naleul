'use client'

import { useEffect, useRef, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { CornerDownLeft } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/Button'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { goalKeys } from '@/features/goal/api'
import { timetableKeys } from '../api'
import { AIBadge } from '@/components/ui/AIBadge'

// ── API (POST /timetable/ai-edit/preview · /apply) ─────────────────────

export interface ScheduleEditOp {
  type: 'MOVE_ROUTINE' | 'MOVE_TASK'
  targetId: number
  days: string[] | null
  startTime: string | null
  durationMinutes: number | null
  date: string | null
  /** AI 가 이렇게 옮기는 이유 (2026-10, 오른쪽 패널에 보여줘요) */
  reason?: string | null
}

export interface ScheduleEditChange {
  op: ScheduleEditOp
  kind: 'ROUTINE' | 'TASK'
  target: string
  goal: string | null
  colorCode: string | null
  before: string
  after: string
}

export interface ScheduleEditPreview {
  summary: string
  changes: ScheduleEditChange[]
  /** 지금 겹쳐 있는 루틴 — "화 16:30–17:00 '기출 풀기' · 'SQL 문제'" */
  overlaps: string[]
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

function useScheduleEditPreview() {
  return useMutation({
    mutationFn: (v: { instruction: string; startDate: string; endDate: string }) =>
      api.post<ScheduleEditPreview>('/v1/timetable/ai-edit/preview', v),
    onError: (e) => toast.error(errorMessage(e)),
  })
}

export function useScheduleEditApply() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { ops: ScheduleEditOp[]; startDate: string; endDate: string }) =>
      api.post<{ applied: number; skipped: string[] }>('/v1/timetable/ai-edit/apply', v),
    onSuccess: (r) => {
      const applied = r?.applied ?? 0
      const skipped = r?.skipped ?? []
      if (applied)
        toast.success(`${applied}개를 옮겼어요.${skipped.length ? ` ${skipped.length}개는 못 옮겼어요.` : ''}`)
      else toast.error(skipped[0] ?? '옮긴 일정이 없어요.')
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      // 루틴 시간이 바뀌면 목표 상세 · 홈 · 업무형 패턴도
      qc.invalidateQueries({ queryKey: goalKeys.all })
      qc.invalidateQueries({ queryKey: ['home'] })
      qc.invalidateQueries({ queryKey: ['record'] })
    },
  })
}

// ── 화면 ────────────────────────────────────────────────────

const MAX = 300
const EXAMPLES = ['겹치는 루틴 정리해줘', '이번 주 운동 루틴은 저녁 8시로 옮겨줘', '내일 할 일을 오후로 몰아줘']

/**
 * 캘린더 "AI로 일정 조정" — 여러 목표의 루틴 · 일정을 한 문장으로 한꺼번에 옮겨요.
 *  1) 문장을 보내면 AI 가 "옮길 항목 목록"을 만들어요 (보고 있는 주/날의 일정 + 모든 루틴 기준) — 이때는 아무것도 안 바뀌어요
 *  2) 캘린더에 점선 "AI 제안"으로 보여주고, 오른쪽 패널에서 이유를 보고 고치거나 빼고 [적용] (2026-10, 예전 모달)
 * 루틴 변경은 "앞으로 계속", Task 변경은 그날만 바뀌어요.
 * ?ai=문장 으로 들어오면(루틴 폼의 "AI로 한 번에 정리") 입력칸에 채워 둬요 (보내기는 사용자가).
 */
/** 오른쪽 패널이 다시 묻거나(겹치는 루틴 정리) 적용 뒤 입력칸을 비울 때 쓰는 손잡이 */
export interface ScheduleAiBarHandle {
  send: (text: string) => void
  clear: () => void
}

export function ScheduleAiBar({
  startDate,
  endDate,
  viewMode,
  onPreview,
  handleRef,
}: {
  startDate: string
  endDate: string
  /** 캘린더 보기 (calendar_ai.submit 의 view_mode) */
  viewMode?: 'day' | 'week' | 'month'
  /** 2026-10: 미리보기는 모달 대신 캘린더 점선 + 오른쪽 패널로 (ScheduleAiPanel) */
  onPreview: (preview: ScheduleEditPreview) => void
  handleRef?: React.MutableRefObject<ScheduleAiBarHandle | null>
}) {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const [text, setText] = useState('')
  const ask = useScheduleEditPreview()
  const inputRef = useRef<HTMLInputElement>(null)
  // ?ai= 로 채워 둔 문장 (calendar_ai.submit 의 is_prefilled)
  const prefilledRef = useRef<string | null>(null)

  const send = (value: string) => {
    const v = value.trim()
    if (!v || ask.isPending) return
    ask.mutate(
      { instruction: v, startDate, endDate },
      {
        onSuccess: (res) => {
          track('calendar_ai.preview_result', {
            result: 'success',
            change_count: res?.changes.length ?? 0,
            overlap_count: res?.overlaps.length ?? 0,
          })
          if (!res) return
          onPreview(res)
        },
        onError: (e) => track('calendar_ai.preview_result', { result: 'fail', error_status: errorStatusOf(e) }),
      }
    )
  }
  // 입력칸 Enter · '조정하기' 버튼
  const submit = (method: 'button' | 'enter') => {
    const v = text.trim()
    if (!v || ask.isPending) return
    track('calendar_ai.submit', {
      view_mode: viewMode,
      text_length: v.length,
      is_prefilled: prefilledRef.current != null && text === prefilledRef.current,
      submit_method: method,
    })
    send(text)
  }

  // 루틴 폼에서 "캘린더에서 AI로 한 번에 정리"로 왔을 때 — 문장을 채우고 주소에서는 지워요
  const prefill = params.get('ai')
  useEffect(() => {
    if (!prefill) return
    const v = prefill.slice(0, MAX)
    const q = new URLSearchParams(params.toString())
    q.delete('ai')
    router.replace(`${pathname}?${q.toString()}`, { scroll: false })
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 주소로 받은 문장을 한 번만 옮겨 담아요
    setText(v)
    prefilledRef.current = v
    inputRef.current?.focus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prefill])

  useEffect(() => {
    if (!handleRef) return
    handleRef.current = { send, clear: () => setText('') }
  })

  return (
    // AI 요청 바 (명세 6B) — 그라데이션 테두리
    <section className="ai-border-flat rounded-card mb-3 px-4 py-2.5 sm:px-5">
      <div className="flex items-center gap-2">
        <AIBadge size="sm" />
        <input
          id="schedule-ai-input"
          ref={inputRef}
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX))}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit('enter')
            }
          }}
          disabled={ask.isPending}
          placeholder="AI로 일정 옮기기 — 이미 있는 일정을 말로 바꿔요. 예) 겹치는 루틴 정리해줘 · 운동 루틴은 전부 저녁 8시로"
          aria-label="AI로 일정 옮기기"
          className="placeholder:text-text-placeholder h-10 min-w-0 flex-1 bg-transparent text-[15px] outline-none"
        />
        <Button
          size="sm"
          variant="ai"
          onClick={() => submit('button')}
          loading={ask.isPending}
          disabled={!text.trim()}
        >
          <CornerDownLeft className="size-3.5" />
          조정하기
        </Button>
      </div>
      {!text && (
        <div className="mt-1 mb-0.5 flex flex-wrap gap-1.5 pl-7">
          {EXAMPLES.map((ex, i) => (
            <button
              key={ex}
              type="button"
              onClick={() => {
                track('calendar_ai.example_click', { example_index: i })
                setText(ex)
              }}
              className="border-line text-ink-3 hover:border-line-strong hover:text-ink-2 h-6 rounded-full border px-2 text-[12px]"
            >
              {ex}
            </button>
          ))}
        </div>
      )}

    </section>
  )
}
