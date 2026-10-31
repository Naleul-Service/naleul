import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { goalKeys, type GoalCategory, type JavaDayOfWeek } from '@/features/goal/api'
import { activityKeys } from '@/features/timetable/api'
import type { ActualActivity } from '@/features/timetable/types'
import type { GoalSubType, GoalType } from '@/features/goal/kind'

/**
 * 기록형 목표 (백엔드 V39 goal_mode = RECORD)
 *
 * 회사 업무처럼 "무엇을 이룰지"보다 "오늘 무엇을 했는지"를 쌓는 목표예요.
 * 종료일·수치·마일스톤 없이 이름만으로 만들고, 기록은 "실제로 한 일"(ActualActivity)로 모아요.
 */

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

export const recordKeys = {
  all: ['record'] as const,
  goal: (goalId: number) => [...recordKeys.all, 'goal', goalId] as const,
  pattern: (goalId: number) => [...recordKeys.all, 'pattern', goalId] as const,
  journal: (goalId: number) => [...recordKeys.all, 'journal', goalId] as const,
}

// ── 기록형 목표 만들기 ──────────────────────────────────────────

export interface RecordGoalInput {
  name: string
  emoji?: string | null
  /** 비우면 서버가 안 쓰는 색을 골라요 */
  colorId?: number | null
  goalType?: GoalType | null
  goalSubType?: GoalSubType | null
  goalKindLabel?: string | null
  /** 한 줄 설명 — "내가 맡은 일" / "무엇을 만드는지" (100자, motive 에 저장) */
  description?: string | null
}

export function useCreateRecordGoal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: RecordGoalInput) => api.post<GoalCategory>('/v1/goal-categories/record', body),
    onSuccess: () => toast.success('업무형 목표를 만들었어요. 첫 Task를 추가해 보세요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: goalKeys.all }),
  })
}

/** 임시 목표 → 기록형 목표로 그대로 두기 (구체화 없이) */
export function useKeepAsRecord(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.patch<GoalCategory>(`/v1/goal-categories/${goalId}/keep-as-record`),
    onSuccess: () => toast.success('업무형 목표로 바꿨어요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: goalKeys.all }),
  })
}

/** 예전에 직접 만든 목표 → 업무형 (PATCH /goal-categories/{id}/convert-to-record, 2026-10) */
export function useConvertToRecord(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.patch<GoalCategory>(`/v1/goal-categories/${goalId}/convert-to-record`),
    onSuccess: () => toast.success('업무형 목표로 바꿨어요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: goalKeys.all }),
  })
}

// ── 한 일 빠르게 기록하기 (POST /activities/logs) ───────────────

export interface ActivityLogItem {
  clientKey: string
  /** 사용자가 적은 문장 그대로 — 서버가 시각·걸린 시간을 읽어요 */
  text: string
  /** 아래는 AI 해석 결과를 쓸 때만 (보낸 값이 문장보다 우선) */
  title?: string | null
  date?: string | null
  startTime?: string | null
  endTime?: string | null
  minutes?: number | null
  goalCategoryId?: number | null
  tempGoalKey?: string | null
}

export interface ActivityLogRequest {
  items: ActivityLogItem[]
  tempGoals?: {
    tempKey: string
    name: string
    emoji?: string | null
    colorId?: number | null
  }[]
}

export interface ActivityLogResponse {
  activities: ActualActivity[]
  tempGoals: {
    tempKey: string
    goalCategoryId: number
    name: string
    emoji: string | null
    colorCode: string | null
  }[]
}

export function useLogActivities() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ActivityLogRequest) => api.post<ActivityLogResponse>('/v1/activities/logs', body),
    onError: (e) => toast.error(errorMessage(e)),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: activityKeys.all })
      qc.invalidateQueries({ queryKey: recordKeys.all })
      if (res.tempGoals.length) qc.invalidateQueries({ queryKey: goalKeys.all })
    },
  })
}

export function useDeleteRecord() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (activityId: number) => api.delete(`/v1/activities/${activityId}`),
    onSuccess: () => toast.success('기록을 지웠어요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: activityKeys.all })
      qc.invalidateQueries({ queryKey: recordKeys.all })
    },
  })
}

let seq = 0
export const newClientKey = () => `r${Date.now().toString(36)}${(seq++).toString(36)}`

// ── 이 목표에 대한 나의 패턴 (GET /activities/goals/{id}/pattern) ──

export type TimeBand = 'DAWN' | 'MORNING' | 'AFTERNOON' | 'EVENING'

export interface RecordPeriodStat {
  start: string
  end: string
  /** 완료한 Task 수 */
  count: number
  /** 시간을 알 수 있는 Task 의 시간 합 */
  minutes: number
  activeDays: number
}

/**
 * 이 목표의 완료한 Task(일반 + 루틴) 기준 패턴.
 * Task 날짜: 실제 시각 → 계획 시각 → 루틴 날짜 → 권장일 → 완료 버튼 누른 시각.
 * 직접 남긴 "한 일" 기록은 섞지 않아요 (업무 일지에서 함께 보여줘요).
 */
export interface RecordPattern {
  summary: {
    totalCount: number
    totalMinutes: number
    activeDays: number
    firstDoneDate: string | null
    /** 최근 4주 (오늘 포함 28일) */
    recent: RecordPeriodStat
    /** 그 전 4주 */
    previous: RecordPeriodStat
    /** 완료한 Task 가 있는 주가 몇 주째 이어지는지 (이번 주가 비었으면 지난주까지) */
    weekStreak: number
    thisWeekDone: boolean
  }
  rhythm: {
    /** 잔디 시작일 (월요일) */
    heatmapStart: string
    /** 완료가 있는 날만 */
    days: { date: string; count: number; minutes: number }[]
    /** 최근 12주, 오래된 것부터 (빈 주 포함) */
    weeks: { weekStart: string; count: number; minutes: number; cumulativeCount: number; cumulativeMinutes: number }[]
  }
  when: {
    cells: { dayOfWeek: JavaDayOfWeek; band: TimeBand; count: number; minutes: number }[]
    bands: { band: TimeBand; count: number; minutes: number }[]
    peakDay: JavaDayOfWeek | null
    peakBand: TimeBand | null
    /** 시각을 몰라서 표에서 뺀 Task 수 (시간 미정으로 완료) */
    untimedCount: number
  }
  /** 목표에 루틴이 없으면 items 가 비어 있어요 → 카드를 숨겨요 */
  routines: {
    /** 쌓인 시간이 많은 순 (완료가 없는 루틴도 포함) */
    items: RecordRoutineStat[]
    /** 최근 4주에 그 전 4주보다 눈에 띄게 더 한 루틴 */
    risingRoutineId: number | null
  }
}

/** 완료한 루틴 Task 기준. 시간은 실제 시각 → 계획 시간 → 루틴 기본 시간 순 */
export interface RecordRoutineStat {
  routineId: number
  routineName: string
  emoji: string | null
  minutes: number
  completedCount: number
  /** 최근 4주 */
  recentMinutes: number
  recentCount: number
  /** 그 전 4주 */
  previousMinutes: number
  previousCount: number
}

/** 기록을 남기거나 지우면 recordKeys.all 이 무효화돼서 같이 다시 불러와요 (Task 완료 토글도 같이) */
export function useRecordPattern(goalId: number) {
  return useQuery({
    queryKey: recordKeys.pattern(goalId),
    queryFn: () => api.get<RecordPattern>(`/v1/activities/goals/${goalId}/pattern`),
  })
}

// ── 업무 일지 (GET /activities/goals/{id}/journal) ──

/** TASK: 완료한 Task · RECORD: 직접 남긴 기록 */
export interface JournalItem {
  type: 'TASK' | 'RECORD'
  id: number
  title: string
  emoji: string | null
  /** 모르면 null (시간 미정으로 완료한 Task) */
  startAt: string | null
  endAt: string | null
  minutes: number | null
  /** 루틴 Task 인지 */
  routine: boolean
  memo: string | null
}

export interface JournalDay {
  date: string
  taskCount: number
  recordCount: number
  /** 시간을 알 수 있는 항목의 합 */
  minutes: number
  /** 시각순 (시각 없는 Task 는 뒤로) */
  items: JournalItem[]
}

export interface RecordJournal {
  /** 최근 날짜부터 */
  days: JournalDay[]
  /** 더 오래된 일지가 있으면 다음 쪽을 부를 날짜 (없으면 null) */
  nextBefore: string | null
}

/** 업무 일지 — 2주씩, "더 보기"로 과거를 이어 불러요 */
export function useRecordJournal(goalId: number) {
  return useInfiniteQuery({
    queryKey: recordKeys.journal(goalId),
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      api.get<RecordJournal>(
        `/v1/activities/goals/${goalId}/journal?days=14${pageParam ? `&before=${pageParam}` : ''}`
      ),
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  })
}
