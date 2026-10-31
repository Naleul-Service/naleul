import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { goalKeys, type GoalMode, type ReflectionMood } from '@/features/goal/api'
import { workKeys } from '@/features/work/api'

/**
 * 홈 "오늘" (백엔드 GET /api/v1/home/today)
 *  - 다가오는 마감: Task 마감 · 업무형 주제 마감(V53) · 목표형 종료일
 *  - 진행 중인 목표별 오늘: 끝낸 일 · 못 한 일 · 오늘 쓴 회고
 * 오늘 일정 · 루틴 Task 는 /timetable, 생활 습관은 /habits 를 그대로 써요.
 */

export type DeadlineType = 'TASK' | 'TOPIC' | 'GOAL'

export interface HomeDeadline {
  type: DeadlineType
  /** taskId · workTopicId · goalCategoryId */
  id: number
  title: string
  emoji: string | null
  date: string
  /** 오늘 0, 지났으면 음수 */
  dDay: number
  overdue: boolean
  goalCategoryId: number | null
  goalName: string | null
  goalEmoji: string | null
  colorCode: string | null
  goalMode: GoalMode | null
  /** TOPIC: 그 주제의 남은 Task 수 */
  remaining: number | null
}

export interface HomeRetro {
  retrospectiveId: number
  reviewType: 'DAILY' | 'WEEKLY' | 'MONTHLY' | null
  content: string
}

/**
 * 목표형(ACHIEVEMENT) 그날 기록 — 목표 상세 "오늘 기록"과 같은 데이터 (PUT /goal-categories/{id}/check-in)
 *  - hasMetric: 수치 목표인지 (아니면 기분 · 한 줄 회고만)
 *  - value: 그날 기록한 수치
 */
export interface HomeCheckIn {
  hasMetric: boolean
  metricName: string | null
  unit: string | null
  currentValue: number | null
  value: number | null
  mood: ReflectionMood | null
  note: string | null
}

export const checkInWritten = (c: HomeCheckIn | null | undefined) =>
  !!c && (c.value != null || c.mood != null || !!c.note)

export interface HomeGoalToday {
  goalCategoryId: number
  goalName: string
  emoji: string | null
  colorCode: string | null
  goalMode: GoalMode | null
  /** 오늘 할 일 · 끝낸 일 · 회고가 하나라도 있으면 true */
  activeToday: boolean
  planned: number
  done: number
  doneTasks: string[]
  openTasks: string[]
  retros: HomeRetro[]
  /** 목표형만 (업무형은 null — 회고 글로 남겨요). 아직 배포 전 서버면 없을 수 있어요 */
  checkIn?: HomeCheckIn | null
}

export interface HomeToday {
  today: string
  deadlines: HomeDeadline[]
  goals: HomeGoalToday[]
}

export const homeKeys = {
  all: ['home'] as const,
  today: (date?: string) => [...homeKeys.all, 'today', date ?? 'today'] as const,
  reflections: (goalCategoryId?: number) => [...homeKeys.all, 'reflections', goalCategoryId ?? 'all'] as const,
}

/** date 를 주면 그날 (홈에서 날짜 ‹ › 로 지난날 · 앞날 보기). 안 주면 오늘 */
export function useHomeToday(date?: string) {
  return useQuery({
    queryKey: homeKeys.today(date),
    queryFn: () => api.get<HomeToday>(`/v1/home/today${date ? `?date=${date}` : ''}`),
    // 아직 배포 전인 서버(404)에서도 홈의 나머지는 보여야 해서 재시도하지 않아요
    retry: false,
  })
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

function useRetroMutation<V>(fn: (v: V) => Promise<unknown>, success: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => toast.success(success),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: homeKeys.all })
      // 업무형 보드의 회고 목록도 같이 바뀌어요
      qc.invalidateQueries({ queryKey: workKeys.all })
    },
  })
}

/** 오늘 회고 남기기 (POST /retrospectives · DAILY) */
export const useCreateTodayRetro = () =>
  useRetroMutation(
    (v: { goalCategoryId: number; content: string; date: string }) =>
      api.post('/v1/retrospectives', {
        reviewType: 'DAILY',
        reviewDate: v.date,
        content: v.content,
        goalCategoryId: v.goalCategoryId,
      }),
    '회고를 남겼어요.'
  )

export const useUpdateRetro = () =>
  useRetroMutation(
    (v: { retrospectiveId: number; content: string }) =>
      api.patch(`/v1/retrospectives/${v.retrospectiveId}`, { content: v.content }),
    '회고를 고쳤어요.'
  )

export const useDeleteRetro = () =>
  useRetroMutation((retrospectiveId: number) => api.delete(`/v1/retrospectives/${retrospectiveId}`), '회고를 지웠어요.')

/** 홈에서 목표형 "오늘 기록" 남기기 — 목표 상세의 오늘 기록과 같은 API (수치 + 기분 + 한 줄 회고) */
export function useHomeCheckIn() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: {
      goalCategoryId: number
      date: string
      value: number | null
      mood: ReflectionMood | null
      note: string | null
    }) =>
      api.put(`/v1/goal-categories/${v.goalCategoryId}/check-in`, {
        date: v.date,
        value: v.value,
        mood: v.mood,
        note: v.note,
      }),
    onSuccess: () => toast.success('오늘 기록을 남겼어요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: (_d, _e, v) => {
      qc.invalidateQueries({ queryKey: homeKeys.all })
      // 목표 상세 그래프 · 오늘 기록 · 목록의 현재 값도 같이 바뀌어요
      qc.invalidateQueries({ queryKey: goalKeys.progress(v.goalCategoryId) })
      qc.invalidateQueries({ queryKey: goalKeys.detail(v.goalCategoryId) })
      qc.invalidateQueries({ queryKey: goalKeys.list() })
    },
  })
}

// ── 회고 모아보기 (GET /home/reflections) ──────────────────────────

export type ReflectionKind = 'CHECK_IN' | 'RETRO'

export interface ReflectionEntry {
  kind: ReflectionKind
  date: string
  goalCategoryId: number
  goalName: string
  goalEmoji: string | null
  colorCode: string | null
  goalMode: GoalMode | null
  /** RETRO 만 */
  retrospectiveId: number | null
  reviewType: 'DAILY' | 'WEEKLY' | 'MONTHLY' | null
  /** CHECK_IN 만 */
  mood: ReflectionMood | null
  /** CHECK_IN: 한 줄 회고 · RETRO: 회고 내용 */
  note: string | null
  value: number | null
  metricName: string | null
  unit: string | null
}

export interface ReflectionFeedPage {
  from: string
  to: string
  entries: ReflectionEntry[]
  hasMore: boolean
}

/** 회고 모아보기 기간 — 일간(하루) · 주간(월~일) · 월간(1일~말일) */
export type ReflectionPeriod = 'DAY' | 'WEEK' | 'MONTH'

/** [from, to] 기간의 회고 (양 끝 포함, 최대 92일) */
export function useReflectionRange(from: string, to: string, goalCategoryId?: number, enabled = true) {
  return useQuery({
    enabled,
    queryKey: [...homeKeys.reflections(goalCategoryId), from, to] as const,
    queryFn: () => {
      const days = Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1
      const before = new Date(Date.parse(to) + 86_400_000).toISOString().slice(0, 10)
      const q = new URLSearchParams({ before, days: String(days) })
      if (goalCategoryId) q.set('goalCategoryId', String(goalCategoryId))
      return api.get<ReflectionFeedPage>(`/v1/home/reflections?${q}`)
    },
    placeholderData: (prev) => prev,
  })
}

/** "전체" — 기간 없이 최근 회고부터, 30일씩 "더 보기"로 그 전 (같은 GET /home/reflections · before 커서) */
export function useReflectionFeed(goalCategoryId?: number, enabled = true) {
  return useInfiniteQuery({
    enabled,
    queryKey: [...homeKeys.reflections(goalCategoryId), 'feed'] as const,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => {
      const q = new URLSearchParams({ days: '30' })
      if (pageParam) q.set('before', pageParam)
      if (goalCategoryId) q.set('goalCategoryId', String(goalCategoryId))
      return api.get<ReflectionFeedPage>(`/v1/home/reflections?${q}`)
    },
    getNextPageParam: (last) => (last.hasMore ? last.from : null),
  })
}
