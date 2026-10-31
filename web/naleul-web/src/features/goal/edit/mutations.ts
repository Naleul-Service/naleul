import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { timetableKeys } from '@/features/timetable/api'
import type { TimeBlockTask } from '@/features/timetable/types'
import { goalKeys, type GoalCategory, type JavaDayOfWeek, type MilestoneInfo } from '../api'
import type { GoalSubType, GoalType } from '../kind'

/**
 * 목표 상세에서 쓰는 추가·수정·삭제 API.
 * 어떤 걸 바꿔도 목표(목록·상세·관련 Task)와 캘린더를 다시 불러와요.
 * (세부 목표·루틴을 바꾸면 Task 가 새로 생기거나 지워지므로 부분 갱신보다 다시 조회가 안전)
 */

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

function useGoalMutation<V, R = unknown>(fn: (v: V) => Promise<R>, success?: string | ((v: V) => string)) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (_, v) => {
      const msg = typeof success === 'function' ? success(v) : success
      if (msg) toast.success(msg)
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.all })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
    },
  })
}

// ─── 전체 목표 ───────────────────────────────────────────────

export interface GoalCreateInput {
  goalCategoryName: string
  goalCategoryStartDate: string
  goalCategoryEndDate: string
  colorId: number
  motive?: string
  emoji?: string
  metricName?: string
  metricUnit?: string
  startValue?: number | null
  targetValue?: number | null
  goalType?: GoalType
  goalSubType?: GoalSubType
  goalKindLabel?: string
}

/**
 * 직접 목표 만들기 — 목표를 만든 뒤 적어 둔 세부 목표들을 같은 기간·색으로 이어서 만들어요.
 * 세부 목표 하나가 실패해도 목표는 이미 만들어졌으니, 나머지는 상세 화면에서 추가하도록 안내해요.
 */
export function useCreateGoal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ subGoals, ...goal }: GoalCreateInput & { subGoals: string[] }) => {
      const created = await api.post<GoalCategory>('/v1/goal-categories', goal)
      let failed = 0
      for (const name of subGoals) {
        try {
          await api.post('/v1/general-categories', {
            generalCategoryName: name,
            goalCategoryId: created.goalCategoryId,
            generalCategoryStartDate: goal.goalCategoryStartDate,
            generalCategoryEndDate: goal.goalCategoryEndDate,
            colorId: goal.colorId,
          })
        } catch {
          failed++
        }
      }
      return { goal: created, failed }
    },
    onSuccess: ({ failed }) =>
      failed
        ? toast.error(`목표는 만들었지만 세부 목표 ${failed}개를 만들지 못했어요. 상세 화면에서 다시 추가해 주세요.`)
        : toast.success('목표를 만들었어요. 루틴·마일스톤·Task도 바로 추가해 보세요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => qc.invalidateQueries({ queryKey: goalKeys.all }),
  })
}

export interface GoalUpdateInput {
  goalCategoryName?: string
  motive?: string
  goalCategoryStartDate?: string
  goalCategoryEndDate?: string
  colorId?: number
  /** 문자열은 "" 를 보내면 지워져요 */
  emoji?: string
  description?: string
  metricName?: string
  metricUnit?: string
  startValue?: number | null
  currentValue?: number | null
  targetValue?: number | null
  /** 수치 목표 마감일 (목표 종료일과 따로 움직여요). 없으면 목표 종료일을 따라요 */
  metricTargetDate?: string
  /** true 면 수치 마감일을 지워 다시 목표 종료일을 따라요 */
  clearMetricTargetDate?: boolean
  /** true 면 수치 목표를 지워요 ("수치 없이 진행하기") — 점검 시점 수치도 함께 */
  clearMetric?: boolean
  /** 목표 카테고리 — "기타"면 goalKindLabel 1~10자 */
  goalType?: GoalType
  goalSubType?: GoalSubType
  goalKindLabel?: string
}

/** 목표 상세 "지금 어디쯤?"에서 수치만 고치기 (목표 전체 수정과 따로) */
export const useUpdateGoalMetric = (goalId: number) =>
  useGoalMutation(
    (v: GoalUpdateInput) => api.patch<GoalCategory>(`/v1/goal-categories/${goalId}`, v),
    '수치를 수정했어요.'
  )

export const useUpdateGoal = (goalId: number) =>
  useGoalMutation(
    (v: GoalUpdateInput) => api.patch<GoalCategory>(`/v1/goal-categories/${goalId}`, v),
    '목표를 수정했어요.'
  )

/** 목표 삭제 — 세부 목표·루틴(과 루틴 Task)도 함께 지워져요 */
export const useDeleteGoal = (goalId: number) =>
  useGoalMutation(() => api.delete(`/v1/goal-categories/${goalId}`), '목표를 삭제했어요.')

// ─── 영역 (세부 목표 · general category) ─────────────────────

export interface SubGoalInput {
  generalCategoryName: string
  generalCategoryStartDate: string
  generalCategoryEndDate: string
  colorId?: number
}

export const useCreateSubGoal = (goalId: number) =>
  useGoalMutation(
    (v: SubGoalInput & { colorId: number }) => api.post('/v1/general-categories', { ...v, goalCategoryId: goalId }),
    '세부 목표를 추가했어요.'
  )

export const useUpdateSubGoal = () =>
  useGoalMutation(
    ({ id, ...v }: SubGoalInput & { id: number }) => api.put(`/v1/general-categories/${id}`, v),
    '세부 목표를 수정했어요.'
  )

export const useDeleteSubGoal = () =>
  useGoalMutation((id: number) => api.delete(`/v1/general-categories/${id}`), '세부 목표를 삭제했어요.')

// ─── 루틴 ───────────────────────────────────────────────────

export interface RoutineInput {
  /** 영역 — null 이면 서버가 목표의 "기타 할 일"에 넣어요 */
  generalCategoryId: number | null
  routineName: string
  repeatStartDate: string
  repeatEndDate: string
  repeatDays: JavaDayOfWeek[]
  /** "HH:mm" — 없으면 null (자동 배치가 시간을 정해요) */
  repeatStartTime: string | null
  repeatEndTime: string | null
  notificationEnabled: boolean
  /** 하는 방법 — 수정 때 "" 를 보내면 지워져요 */
  description?: string
  /** 요일별 시간 — 기본 시간과 다른 요일만. 수정 때 [] 를 보내면 모든 요일이 기본 시간으로 돌아가요 */
  dayTimes?: { dayOfWeek: JavaDayOfWeek; startTime: string; endTime: string }[]
}

export const useCreateRoutine = (goalId: number) =>
  useGoalMutation((v: RoutineInput) => api.post('/v1/routines', { ...v, goalCategoryId: goalId }), '루틴을 추가했어요.')

/** 루틴 수정 — 오늘 이후의 루틴 Task 가 새 설정으로 다시 만들어져요 (지난 기록은 그대로) */
export const useUpdateRoutine = () =>
  useGoalMutation(
    ({ id, ...v }: Partial<RoutineInput> & { id: number }) => api.patch(`/v1/routines/${id}`, v),
    '루틴을 수정했어요.'
  )

export interface RoutineExtraDayInput {
  routineId: number
  /** yyyy-MM-dd — 반복 요일이 아닌 날도 돼요 (예: 평일 루틴을 토요일에도) */
  date: string
  /** "HH:mm" — 비우면 루틴 시간 */
  startTime: string | null
  endTime: string | null
  /** true 면 "이미 했어요" — 그 시간을 실제로 한 시간으로 남겨요 */
  done: boolean
}

/** 루틴을 다른 날에도 했을 때 — 그날 하루만 루틴 블록을 하나 더 만들어요 */
export function useAddRoutineExtraDay() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ routineId, ...body }: RoutineExtraDayInput) =>
      api.post<{ taskId: number }>(`/v1/routines/${routineId}/extra-days`, body),
    onSuccess: (_, v) => toast.success(v.done ? '그날 한 걸로 남겼어요.' : '그날 캘린더에 넣었어요.'),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.all })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      qc.invalidateQueries({ queryKey: ['home'] })
      qc.invalidateQueries({ queryKey: ['pattern'] })
    },
  })
}

export const useDeleteRoutine = () =>
  useGoalMutation((id: number) => api.delete(`/v1/routines/${id}`), '루틴을 삭제했어요.')

// ─── 점검 시점 (마일스톤) ─────────────────────────────────────

export interface MilestoneInput {
  title: string
  description: string
  dueDate: string
  /** 보내면 "직접 정한 값", null/생략이면 시작값 → 목표값 직선에서 자동 계산 */
  targetValue: number | null
}

export const useCreateMilestone = (goalId: number) =>
  useGoalMutation(
    (v: MilestoneInput) => api.post<MilestoneInfo>(`/v1/goal-categories/${goalId}/milestones`, v),
    '마일스톤을 추가했어요.'
  )

export const useUpdateMilestone = () =>
  useGoalMutation(
    ({
      id,
      ...v
    }: Partial<MilestoneInput> & {
      id: number
      clearTargetValue?: boolean
      /** true 면 직접 정한 값을 버리고 자동 계산으로 되돌려요 */
      autoTargetValue?: boolean
      status?: 'ACHIEVED' | 'PENDING'
    }) => api.patch<MilestoneInfo>(`/v1/milestones/${id}`, v),
    (v) =>
      v.status === 'ACHIEVED'
        ? '마일스톤을 달성했어요. 🎉'
        : v.status === 'PENDING'
          ? '달성을 취소했어요.'
          : v.autoTargetValue
            ? '자동 계산으로 되돌렸어요.'
            : '마일스톤을 수정했어요.'
  )

export const useDeleteMilestone = () =>
  useGoalMutation((id: number) => api.delete(`/v1/milestones/${id}`), '마일스톤을 삭제했어요.')

// ─── Task ───────────────────────────────────────────────────

export interface GoalTaskInput {
  taskName: string
  emoji: string | null
  /** 영역 — null 이면 서버가 목표의 "기타 할 일"에 넣어요 */
  generalCategoryId: number | null
  milestoneId: number | null
  /** "YYYY-MM-DD" */
  date: string
  /** "HH:mm" — 둘 다 있거나 둘 다 null. 없으면 그날 "시간 미정"으로 두고 자동 배치가 시간을 정해요 */
  startTime: string | null
  endTime: string | null
  durationMinutes: number | null
  dueDate: string | null
}

export const useCreateGoalTask = (goalId: number) =>
  useGoalMutation(
    (v: GoalTaskInput) => api.post<TimeBlockTask>(`/v1/goal-categories/${goalId}/tasks`, v),
    'Task를 추가했어요.'
  )

export const useUpdateGoalTask = (goalId: number) =>
  useGoalMutation(
    ({ taskId, ...v }: GoalTaskInput & { taskId: number }) =>
      api.put<TimeBlockTask>(`/v1/goal-categories/${goalId}/tasks/${taskId}`, v),
    'Task를 수정했어요.'
  )

export const useDeleteGoalTask = () =>
  useGoalMutation((taskId: number) => api.delete(`/v1/tasks/${taskId}`), 'Task를 삭제했어요.')

// ─── 말로 고치기 (AI 변경안 미리보기 → 확인 → 적용) ─────────────

export type GoalEditOpType =
  | 'UPDATE_GOAL'
  | 'ADD_ROUTINE'
  | 'UPDATE_ROUTINE'
  | 'DELETE_ROUTINE'
  | 'ADD_MILESTONE'
  | 'UPDATE_MILESTONE'
  | 'DELETE_MILESTONE'
  | 'ADD_TASK'

/** 변경 하나 — 미리보기에서 받은 그대로 적용할 때 다시 보내요 */
export interface GoalEditOp {
  type: GoalEditOpType
  targetId?: number | null
  title?: string | null
  durationMinutes?: number | null
  days?: string[] | null
  startTime?: string | null
  description?: string | null
  date?: string | null
  startValue?: number | null
  targetValue?: number | null
  areaId?: number | null
}

export interface GoalEditChange {
  op: GoalEditOp
  /** "추가" · "변경" · "삭제" */
  action: string
  target: string
  before: string | null
  after: string | null
}

export interface GoalEditPreview {
  summary: string
  changes: GoalEditChange[]
}

/** 미리보기 — 아직 아무것도 바뀌지 않아요 */
export function useGoalEditPreview(goalId: number) {
  return useMutation({
    mutationFn: (instruction: string) =>
      api.post<GoalEditPreview>(`/v1/goal-categories/${goalId}/ai-edit/preview`, { instruction }),
    onError: (e) => toast.error(errorMessage(e)),
  })
}

/** 고른 변경만 적용 */
export const useGoalEditApply = (goalId: number) =>
  useGoalMutation(
    (ops: GoalEditOp[]) => api.post<{ applied: number }>(`/v1/goal-categories/${goalId}/ai-edit/apply`, { ops }),
    (ops) => `${ops.length}개 변경을 적용했어요.`
  )
