import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { goalKeys } from '@/features/goal/api'
import { timetableKeys } from '@/features/timetable/api'

/**
 * 업무형 목표 상세 · 보드 (백엔드 V52 · /api/v1/goal-categories/{id}/work-*)
 *
 * 오늘 뭘 해야 하는지 · 다음 마감이 뭔지 · 언제까지 뭘 해야 하는지를 한 화면에 보여줘요.
 * 업무형은 목표 전체 마감이 없고 주제마다 마감이 있어요 (V53) → 주제를 마감 순서대로 잇는 세로 로드맵.
 * Task 는 주제로 묶고, 시작 전 · 진행 중 · 완료 보드로 옮겨요. 날짜를 안 정한 Task 는 "할 일 목록"에 쌓여요.
 */

export type WorkColumn = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE'

export interface WorkTask {
  taskId: number
  taskName: string
  emoji: string | null
  topicId: number | null
  topicName: string | null
  status: WorkColumn
  /** 하기로 한 날 (null = 할 일 목록) */
  date: string | null
  /** "HH:mm" — 시간이 정해졌을 때만 */
  startTime: string | null
  endTime: string | null
  dueDate: string | null
  /** 마감까지 남은 날 (오늘 0, 지났으면 음수) */
  dueInDays: number | null
  overdue: boolean
  memo: string | null
  /** 반복 루틴의 그날 Task — 보드 칸에는 안 나오고 "오늘 할 일"에만 */
  routine: boolean
  completedAt: string | null
}

export interface WorkSummary {
  startDate: string | null
  total: number
  notStarted: number
  inProgress: number
  done: number
  remaining: number
  overdue: number
  dueThisWeek: number
  backlog: number
  progressPercent: number | null
  /** 끝난 주제 수 / 전체 주제 수 */
  topicsDone: number
  topicsTotal: number
  /** 아직 안 끝난 주제 중 마감이 가장 가까운 것 (지난 것 포함). 없으면 null */
  nextTopic: WorkTopic | null
}

/** DONE 끝남 · OVERDUE 마감 지남 · NEXT 다음 마감 · UPCOMING 그 뒤 · NO_DUE 마감 없음 */
export type TopicStatus = 'DONE' | 'OVERDUE' | 'NEXT' | 'UPCOMING' | 'NO_DUE'

/** 주제 — 업무형은 목표 전체 마감 대신 주제마다 마감이 있어요 (V53) */
export interface WorkTopic {
  /** null = "주제 없음" (보드 필터에만) */
  topicId: number | null
  name: string
  emoji: string | null
  sortOrder: number
  dueDate: string | null
  /** 마감까지 남은 날 (오늘 0, 지났으면 음수) */
  dDay: number | null
  status: TopicStatus
  /** 사용자가 "끝냈어요"로 표시했는지 */
  completed: boolean
  total: number
  done: number
  remaining: number
  progressPercent: number | null
  /** 마감까지 하루에 끝내야 하는 Task 수 */
  remainingPerDay: number | null
}

export interface WorkRetro {
  retrospectiveId: number
  reviewDate: string
  reviewType: 'DAILY' | 'WEEKLY' | 'MONTHLY' | null
  content: string
}

export interface WorkBoard {
  goalCategoryId: number
  goalName: string
  emoji: string | null
  description: string | null
  today: string
  summary: WorkSummary
  /** 주제를 마감 순서대로 (마감 없는 주제는 맨 뒤) — 세로 로드맵 */
  timeline: WorkTopic[]
  todayTasks: WorkTask[]
  doneToday: WorkTask[]
  upcoming: WorkTask[]
  topics: WorkTopic[]
  board: { notStarted: WorkTask[]; inProgress: WorkTask[]; done: WorkTask[]; doneTotal: number }
  backlog: WorkTask[]
  archive: { date: string; count: number; tasks: WorkTask[] }[]
  retrospectives: WorkRetro[]
}

export type ShareScope = 'TODAY' | 'REMAINING' | 'ALL'

export const workKeys = {
  all: ['work'] as const,
  board: (goalId: number) => [...workKeys.all, 'board', goalId] as const,
  share: (goalId: number, scope: ShareScope) => [...workKeys.all, 'share', goalId, scope] as const,
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

export function useWorkBoard(goalId: number, archiveDays = 30, enabled = true) {
  return useQuery({
    enabled: enabled && goalId > 0,
    queryKey: workKeys.board(goalId),
    queryFn: () => api.get<WorkBoard>(`/v1/goal-categories/${goalId}/work-board?archiveDays=${archiveDays}`),
  })
}

/** 공유 텍스트 — 열 때마다 새로 받아요 (보드가 바뀌었을 수 있어서) */
export function useWorkShareText(goalId: number, scope: ShareScope, enabled: boolean) {
  return useQuery({
    queryKey: workKeys.share(goalId, scope),
    queryFn: () =>
      api.get<{ scope: string; text: string }>(`/v1/goal-categories/${goalId}/work-board/share-text?scope=${scope}`),
    enabled,
    staleTime: 0,
  })
}

/** 보드를 바꾸면 목표 상세 · 업무 일지 · 통계 · 캘린더도 달라질 수 있어요 */
function useWorkMutation<V, R>(
  goalId: number,
  fn: (v: V) => Promise<R>,
  success?: string | ((v: V, r: R) => string | null)
) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (r, v) => {
      const msg = typeof success === 'function' ? success(v, r) : success
      if (msg) toast.success(msg)
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: workKeys.all })
      qc.invalidateQueries({ queryKey: goalKeys.tasks(goalId) })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      qc.invalidateQueries({ queryKey: ['record'] })
      // 홈 회고 · 회고 모아보기
      qc.invalidateQueries({ queryKey: ['home'] })
    },
  })
}

// ── Task ───────────────────────────────────────────────────

export interface WorkTaskInput {
  taskName: string
  topicId?: number | null
  date?: string | null
  dueDate?: string | null
  memo?: string | null
  status?: WorkColumn
}

export const useCreateWorkTask = (goalId: number) =>
  useWorkMutation(
    goalId,
    (v: WorkTaskInput) => api.post<WorkTask>(`/v1/goal-categories/${goalId}/work-tasks`, v),
    'Task를 추가했어요.'
  )

export const useCreateWorkTasks = (goalId: number) =>
  useWorkMutation(
    goalId,
    (v: { text: string; topicId?: number | null; date?: string | null; dueDate?: string | null }) =>
      api.post<WorkTask[]>(`/v1/goal-categories/${goalId}/work-tasks/bulk`, v),
    (_, r) => `Task ${r.length}개를 추가했어요.`
  )

export interface WorkTaskPatch {
  taskName?: string
  topicId?: number
  clearTopic?: boolean
  date?: string
  clearDate?: boolean
  dueDate?: string
  clearDueDate?: boolean
  memo?: string
  status?: WorkColumn
}

export const useUpdateWorkTask = (goalId: number) =>
  useWorkMutation(
    goalId,
    ({ taskId, ...v }: WorkTaskPatch & { taskId: number }) =>
      api.patch<WorkTask>(`/v1/goal-categories/${goalId}/work-tasks/${taskId}`, v),
    'Task를 수정했어요.'
  )

/**
 * 보드에서 칸 옮기기 (드래그 · 체크). 누르자마자 칸을 옮겨 보여주고, 실패하면 되돌려요.
 */
export function useMoveWorkTask(goalId: number) {
  const qc = useQueryClient()
  const key = workKeys.board(goalId)
  return useMutation({
    mutationFn: ({ task, to }: { task: WorkTask; to: WorkColumn }) =>
      api.patch<WorkTask>(`/v1/goal-categories/${goalId}/work-tasks/${task.taskId}/status`, { status: to }),
    onMutate: async ({ task, to }) => {
      await qc.cancelQueries({ queryKey: key })
      const prev = qc.getQueryData<WorkBoard>(key)
      if (prev) qc.setQueryData<WorkBoard>(key, moveLocally(prev, task, to))
      return { prev }
    },
    onError: (e, _, ctx) => {
      if (ctx?.prev) qc.setQueryData(key, ctx.prev)
      toast.error(errorMessage(e))
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: workKeys.all })
      qc.invalidateQueries({ queryKey: goalKeys.tasks(goalId) })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      qc.invalidateQueries({ queryKey: ['record'] })
    },
  })
}

/** 서버 응답 전에 보드 칸만 먼저 옮겨 보여주기 */
function moveLocally(b: WorkBoard, task: WorkTask, to: WorkColumn): WorkBoard {
  const moved: WorkTask = { ...task, status: to }
  const strip = (list: WorkTask[]) => list.filter((t) => t.taskId !== task.taskId)
  const board = {
    notStarted: strip(b.board.notStarted),
    inProgress: strip(b.board.inProgress),
    done: strip(b.board.done),
    doneTotal: b.board.doneTotal + (to === 'DONE' ? 1 : 0) - (task.status === 'DONE' ? 1 : 0),
  }
  if (to === 'DONE') board.done = [moved, ...board.done]
  else if (to === 'IN_PROGRESS') board.inProgress = [...board.inProgress, moved]
  else board.notStarted = [...board.notStarted, moved]
  const swap = (list: WorkTask[]) => list.map((t) => (t.taskId === task.taskId ? moved : t))
  return { ...b, board, todayTasks: swap(b.todayTasks), upcoming: swap(b.upcoming), backlog: swap(b.backlog) }
}

/** Task 삭제는 기존 API (DELETE /v1/tasks/{id}) */
export const useDeleteWorkTask = (goalId: number) =>
  useWorkMutation(goalId, (taskId: number) => api.delete(`/v1/tasks/${taskId}`), 'Task를 지웠어요.')

// ── 주제 ───────────────────────────────────────────────────

export const useCreateWorkTopic = (goalId: number) =>
  useWorkMutation(
    goalId,
    (v: { name: string; emoji?: string | null; dueDate?: string | null }) =>
      api.post<{ topicId: number; name: string }>(`/v1/goal-categories/${goalId}/work-topics`, v),
    (v) => `'${v.name}' 세부 목표를 만들었어요.`
  )

export const useUpdateWorkTopic = (goalId: number) =>
  useWorkMutation(
    goalId,
    ({ topicId, ...v }: WorkTopicPatch & { topicId: number }) =>
      api.patch(`/v1/goal-categories/${goalId}/work-topics/${topicId}`, v),
    (v) =>
      v.completed === true
        ? '세부 목표를 끝냈어요. 수고했어요!'
        : v.completed === false
          ? '세부 목표를 다시 열었어요.'
          : '세부 목표를 고쳤어요.'
  )

export interface WorkTopicPatch {
  name?: string
  emoji?: string
  dueDate?: string
  clearDueDate?: boolean
  /** true 끝냈어요 · false 다시 열기 */
  completed?: boolean
}

export const useDeleteWorkTopic = (goalId: number) =>
  useWorkMutation(
    goalId,
    (topicId: number) => api.delete(`/v1/goal-categories/${goalId}/work-topics/${topicId}`),
    "세부 목표를 지웠어요. 들어 있던 Task는 '세부 목표 없음'으로 옮겼어요."
  )

// ── 회고 (기존 /v1/retrospectives) ──────────────────────────────

export const useCreateWorkRetro = (goalId: number) =>
  useWorkMutation(
    goalId,
    (content: string) => api.post('/v1/retrospectives', { reviewType: 'DAILY', content, goalCategoryId: goalId }),
    '회고를 남겼어요.'
  )

export const useDeleteWorkRetro = (goalId: number) =>
  useWorkMutation(
    goalId,
    (retrospectiveId: number) => api.delete(`/v1/retrospectives/${retrospectiveId}`),
    '회고를 지웠어요.'
  )
