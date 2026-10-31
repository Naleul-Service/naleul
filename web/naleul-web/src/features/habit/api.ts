import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import type { JavaDayOfWeek } from '@/features/goal/api'

/**
 * 기본 생활 습관 (백엔드 V52 · /api/v1/habits)
 *
 * 사용자마다 "기본 생활 습관" 목표(goalMode = HABIT)가 하나씩 자동으로 생기고, 그 안에
 * 영양제 먹기 · 샤워하기 같은 "했는지만 체크하는" 습관을 담아요. 마감 · 수치가 없고, 쉬었다 다시 시작할 수 있어요.
 */

export type HabitStatus = 'ACTIVE' | 'PAUSED' | 'DELETED'
/** 잔디 한 칸 — DONE 함 · MISSED 놓침 · TODO 오늘 아직 · OFF 반복 요일 아님 · PAUSED 쉬는 중 · BEFORE_START · FUTURE */
export type HabitDayState = 'DONE' | 'MISSED' | 'TODO' | 'OFF' | 'PAUSED' | 'BEFORE_START' | 'FUTURE'

export interface Habit {
  habitId: number
  name: string
  emoji: string | null
  /** 비어 있으면 매일 (daily = true) */
  repeatDays: JavaDayOfWeek[]
  daily: boolean
  /** "08:30" */
  remindTime: string | null
  status: HabitStatus
  startDate: string
  sortOrder: number
  /** 기준 날짜(date)에 해야 하는 날인지 */
  scheduled: boolean
  /** 기준 날짜에 체크했는지 */
  checked: boolean
  state: HabitDayState
  /** 연속으로 지킨 "해야 하는 날" 수 (오늘 아직 안 했으면 어제까지) */
  streak: number
  /** 최근 30일 실천률(%) — 해야 하는 날이 없었으면 null */
  rate30: number | null
  /** 최근 7일 (오래된 날 → 기준 날짜) */
  recent7: { date: string; state: HabitDayState }[]
  /** 하는 시간대 "20:00" ~ "20:30" (선택) — 있으면 캘린더에 블록으로 보여요 */
  startTime?: string | null
  endTime?: string | null
}

export interface HabitHome {
  goalCategoryId: number
  goalName: string
  emoji: string | null
  date: string
  today: string
  scheduledCount: number
  doneCount: number
  habits: Habit[]
}

export interface HabitHeatmap {
  from: string
  to: string
  today: string
  /** 모든 습관을 합친 하루 — level 0 ~ 4 */
  days: { date: string; done: number; scheduled: number; level: number }[]
  /** cells[i] = from + i 일 */
  habits: {
    habitId: number
    name: string
    emoji: string | null
    status: HabitStatus
    streak: number
    rate: number | null
    doneDays: number
    cells: HabitDayState[]
  }[]
}

export interface HabitInput {
  name?: string
  emoji?: string
  /** [] = 매일 */
  repeatDays?: JavaDayOfWeek[]
  /** "HH:mm" */
  remindTime?: string | null
  clearRemindTime?: boolean
  /** 하는 시간대 "HH:mm" — 둘 다 보내거나 clearTimeRange */
  startTime?: string
  endTime?: string
  clearTimeRange?: boolean
}

export const habitKeys = {
  all: ['habits'] as const,
  home: (date?: string) => [...habitKeys.all, 'home', date ?? 'today'] as const,
  /** from 을 안 넣으면 접두사 — 체크하면 모든 기간의 잔디를 다시 불러와요 */
  heatmap: (from?: string) =>
    from ? ([...habitKeys.all, 'heatmap', from] as const) : ([...habitKeys.all, 'heatmap'] as const),
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

/** 습관 목록 — date 를 안 주면 오늘. 처음 부르면 서버가 기본 생활 습관 그릇을 만들어요 */
export function useHabitHome(date?: string) {
  return useQuery({
    queryKey: habitKeys.home(date),
    queryFn: () => api.get<HabitHome>(`/v1/habits${date ? `?date=${date}` : ''}`),
  })
}

/** 잔디맵 — from(포함) ~ 오늘. 서버는 최대 400일까지 받아요 */
export function useHabitHeatmap(from?: string, enabled = true) {
  return useQuery({
    queryKey: habitKeys.heatmap(from),
    queryFn: () => api.get<HabitHeatmap>(`/v1/habits/heatmap${from ? `?from=${from}` : ''}`),
    enabled,
  })
}

function useHabitMutation<V, R>(fn: (v: V) => Promise<R>, success?: string | ((v: V) => string | null)) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (_, v) => {
      const msg = typeof success === 'function' ? success(v) : success
      if (msg) toast.success(msg)
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: habitKeys.all }),
  })
}

export const useCreateHabit = () =>
  useHabitMutation(
    (v: HabitInput & { name: string }) => api.post<Habit>('/v1/habits', v),
    (v) => `'${v.name}' 습관을 추가했어요.`
  )

export const useUpdateHabit = () =>
  useHabitMutation(
    ({ habitId, ...v }: HabitInput & { habitId: number }) => api.patch<Habit>(`/v1/habits/${habitId}`, v),
    '습관을 수정했어요.'
  )

export const usePauseHabit = () =>
  useHabitMutation(
    (habitId: number) => api.patch<Habit>(`/v1/habits/${habitId}/pause`),
    '잠시 쉬어요. 쉬는 동안은 안 한 날로 세지 않아요.'
  )

export const useResumeHabit = () =>
  useHabitMutation((habitId: number) => api.patch<Habit>(`/v1/habits/${habitId}/resume`), '다시 시작했어요.')

export const useDeleteHabit = () =>
  useHabitMutation((habitId: number) => api.delete(`/v1/habits/${habitId}`), '습관을 지웠어요.')

export const useReorderHabits = () =>
  useHabitMutation((habitIds: number[]) => api.put<HabitHome>('/v1/habits/order', { habitIds }))

/**
 * 체크 · 체크 풀기 — 누르자마자 화면에 반영하고(낙관적 업데이트), 실패하면 되돌려요.
 * 매일 여러 번 누르는 버튼이라 기다리는 느낌이 없어야 해요.
 */
export function useToggleHabitCheck(date?: string) {
  const qc = useQueryClient()
  const key = habitKeys.home(date)
  return useMutation({
    mutationFn: ({ habit, day }: { habit: Habit; day: string }) =>
      habit.checked
        ? api.delete<Habit>(`/v1/habits/${habit.habitId}/checks/${day}`)
        : api.put<Habit>(`/v1/habits/${habit.habitId}/checks/${day}`),
    onMutate: async ({ habit }) => {
      await qc.cancelQueries({ queryKey: key })
      const prev = qc.getQueryData<HabitHome>(key)
      if (prev) {
        const habits = prev.habits.map((h) =>
          h.habitId === habit.habitId
            ? { ...h, checked: !h.checked, state: (!h.checked ? 'DONE' : h.state) as HabitDayState }
            : h
        )
        qc.setQueryData<HabitHome>(key, {
          ...prev,
          habits,
          doneCount: habits.filter((h) => h.scheduled && h.checked).length,
        })
      }
      return { prev }
    },
    onError: (e, _, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev)
      toast.error(errorMessage(e))
    },
    onSuccess: (updated) => {
      // 서버가 계산한 연속 기록 · 실천률 · 최근 7일로 맞춰요
      qc.setQueryData<HabitHome>(key, (prev) =>
        prev
          ? (() => {
              const habits = prev.habits.map((h) => (h.habitId === updated.habitId ? updated : h))
              return { ...prev, habits, doneCount: habits.filter((h) => h.scheduled && h.checked).length }
            })()
          : prev
      )
    },
    onSettled: () => {
      // 다른 날짜 화면 · 잔디맵은 다시 불러와요 (지금 보고 있는 목록은 위에서 맞췄어요)
      qc.invalidateQueries({ queryKey: habitKeys.heatmap() })
      qc.invalidateQueries({
        queryKey: habitKeys.all,
        predicate: (q) => JSON.stringify(q.queryKey) !== JSON.stringify(key),
      })
    },
  })
}
