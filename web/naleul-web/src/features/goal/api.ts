import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import { timetableKeys } from '@/features/timetable/api'
import type { TimeBlockTask } from '@/features/timetable/types'
import type { GoalSubType, GoalType } from './kind'

/**
 * 백엔드 GoalCategoryResponse (목표 = goal_category)
 *
 * AI 목표 생성 필드(emoji, aiNote, metric*, milestones 등)는 사용자가 직접 만든 목표에선 null 이에요.
 */
export type GoalStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'DELETED'
export type JavaDayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface RoutineSummary {
  routineId: number
  routineName: string
  routineStatus: string
  repeatStartDate: string | null
  repeatEndDate: string | null
  /** "19:30:00" (LocalTime) */
  repeatStartTime: string | null
  repeatEndTime: string | null
  repeatDays: JavaDayOfWeek[]
  notificationEnabled: boolean
  /** 하는 방법 (AI 루틴: "스쿼트 4x10, 런지 3x12…") */
  description?: string | null
  durationMinutes?: number | null
  /** 기본 시간과 다른 요일만 (예: 수요일만 07:00~07:25). 비어 있으면 모든 요일이 repeatStartTime~repeatEndTime */
  dayTimes?: RoutineDayTime[]
}

/** 요일별 시간 — "HH:mm" 또는 "HH:mm:ss" */
export interface RoutineDayTime {
  dayOfWeek: JavaDayOfWeek
  startTime: string
  endTime: string
}

export interface SubGoalInfo {
  generalCategoryId: number
  generalCategoryName: string
  colorCode: string | null
  generalCategoryStartDate: string | null
  generalCategoryEndDate: string | null
  generalCategoryStatus: GoalStatus
  routines: RoutineSummary[]
  /** 세부 목표의 실천 원칙 (AI: "하루 1,600kcal, 단백질 90g…") */
  description?: string | null
  emoji?: string | null
  /** 목표의 "기타 할 일" 그릇 — 영역을 안 고른 루틴·Task 가 들어가요. 화면에선 영역으로 보여주지 않아요 */
  defaultBucket?: boolean
}

export interface MilestoneInfo {
  milestoneId: number
  title: string
  description: string | null
  dueDate: string
  targetValue: number | null
  /** 사용자가 이 시점 수치를 직접 정했는지 (false = 시작값 → 목표값 직선에서 자동 계산) */
  targetValueManual?: boolean
  status: 'PENDING' | 'ACHIEVED' | 'MISSED'
}

export interface GoalCategory {
  goalCategoryId: number
  goalCategoryName: string
  goalCategoryStatus: GoalStatus
  goalCategoryStartDate: string | null
  goalCategoryEndDate: string | null
  achievement: string | null
  motive: string | null
  colorCode: string | null
  generalCategories: SubGoalInfo[]
  // ── AI 목표 생성 필드 ──
  emoji?: string | null
  goalTopic?: string | null
  description?: string | null
  aiGenerated?: boolean
  /** Brain dump 에서 자동으로 만든 임시 목표 (종료일·마일스톤 없음) → "AI로 구체화하기"를 권해요 */
  temporary?: boolean
  /**
   * 목표 형태 (V52)
   *  - ACHIEVEMENT 목표형(기본): 수치 · 기간이 있는 목표. 진행 그래프(여러 개)로 "오늘 어디까지"를 보여줘요
   *  - RECORD 업무형: 회사 업무 · 사이드 프로젝트. 마감(선택) · 주제별 Task 보드 · 업무 일지
   *  - HABIT 기본 생활 습관: 사용자마다 1개씩 자동으로 생기는 체크 전용 습관 그릇
   */
  goalMode?: GoalMode
  /** 목표 카테고리 2단계 (명세 6.A.1.2). 예전 목표는 null */
  goalType?: GoalType | null
  goalSubType?: GoalSubType | null
  goalKindLabel?: string | null
  /** "건강 · 다이어트" */
  goalKindName?: string | null
  planningStyle?: 'PLANNER' | 'SPONTANEOUS' | null
  aiNote?: string | null
  metricName?: string | null
  metricUnit?: string | null
  startValue?: number | null
  currentValue?: number | null
  targetValue?: number | null
  /** 수치 목표 마감일 (V58). null 이면 목표 종료일을 따라요 */
  metricTargetDate?: string | null
  milestones?: MilestoneInfo[] | null
  /** 목록 조회에서만 — 마지막 활동일 (Task 완료 · 수치 기록) */
  lastActivityDate?: string | null
  /** 목록 조회에서만 — 진행 중인데 오래 활동이 없으면 멈춘 지 며칠째 (생활형 14일 · 업무형 28일부터), 아니면 null */
  pausedDays?: number | null
}

export type GoalMode = 'ACHIEVEMENT' | 'RECORD' | 'HABIT'

/** 기록형 목표인지 (예전 응답엔 goalMode 가 없어서 undefined = 달성형) */
export const isRecordGoal = (g: Pick<GoalCategory, 'goalMode'> | null | undefined) => g?.goalMode === 'RECORD'
/** 기본 생활 습관 그릇인지 (V52) */
export const isHabitGoal = (g: Pick<GoalCategory, 'goalMode'> | null | undefined) => g?.goalMode === 'HABIT'
/** 업무형 목표인지 — 업무형은 기록형(RECORD)으로 만들어져요. 세부 목표 = 업무 보드의 주제(WorkTopic) */
export const isWorkGoal = (g: Pick<GoalCategory, 'goalMode' | 'goalType'> | null | undefined) =>
  g?.goalMode === 'RECORD' || g?.goalType === 'WORK'

export const goalKeys = {
  all: ['goals'] as const,
  list: () => [...goalKeys.all, 'list'] as const,
  /** list() 로 무효화하면 같이 무효화돼요 (앞부분이 같아서) */
  listWithHabit: () => [...goalKeys.all, 'list', 'with-habit'] as const,
  detail: (id: number) => [...goalKeys.all, 'detail', id] as const,
  tasks: (id: number) => [...goalKeys.all, 'tasks', id] as const,
  progress: (id: number) => [...goalKeys.all, 'progress', id] as const,
  heatmap: (id: number) => [...goalKeys.all, 'heatmap', id] as const,
  outcomePrompts: () => [...goalKeys.all, 'outcome-prompts'] as const,
}

/**
 * 내 목표 목록.
 * @param includeHabit 기본 생활 습관 그릇(HABIT)도 같이 (맨 뒤) — 실제로 한 일 · 할 일의 목표를 직접 고르는 곳에서
 *   "기본 생활 습관"으로도 분류할 수 있게 (2026-10). 목표 목록 화면 · 통계에는 넣지 않아요
 */
export function useGoalCategories({ includeHabit = false }: { includeHabit?: boolean } = {}) {
  return useQuery({
    queryKey: includeHabit ? goalKeys.listWithHabit() : goalKeys.list(),
    // 삭제된 목표는 혹시 내려와도 화면에서 제외
    queryFn: async () => {
      const list = (
        (await api.get<GoalCategory[]>(`/v1/goal-categories${includeHabit ? '?includeHabit=true' : ''}`)) ?? []
      ).filter((g) => g.goalCategoryStatus !== 'DELETED')
      // 서버는 습관 그릇을 맨 앞에 줘요 → 고르는 목록에선 맨 뒤로 (첫 목표가 기본 선택이라)
      return [...list.filter((g) => !isHabitGoal(g)), ...list.filter((g) => isHabitGoal(g))]
    },
  })
}

export function useGoalCategory(id: number, enabled = true) {
  return useQuery({
    queryKey: goalKeys.detail(id),
    queryFn: () => api.get<GoalCategory>(`/v1/goal-categories/${id}`),
    enabled,
  })
}

/**
 * GET /goal-categories/{id}/tasks — 목표에 속한 Task 전체 (AI 생성·직접 추가·루틴)
 * 서버가 정렬해서 내려줘요: 미완료 먼저 · 오늘과 가까운 날짜 먼저 · 완료는 아래 (최근 것 먼저)
 */
export interface GoalTasksResponse {
  totalCount: number
  todoCount: number
  completedCount: number
  tasks: TimeBlockTask[]
}

export function useGoalTasks(goalId: number) {
  return useQuery({
    queryKey: goalKeys.tasks(goalId),
    queryFn: () => api.get<GoalTasksResponse>(`/v1/goal-categories/${goalId}/tasks`),
  })
}

/** 목표 상세에서 완료 체크 / 취소. 캘린더(TimeTable)도 같이 다시 불러와요 */
export function useToggleGoalTask(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (t: Pick<TimeBlockTask, 'taskId' | 'taskStatus'>) =>
      api.patch(`/v1/tasks/${t.taskId}/${t.taskStatus === 'COMPLETED' ? 'cancel-complete' : 'complete'}`),
    onSuccess: (_, t) => toast.success(t.taskStatus === 'COMPLETED' ? '완료를 취소했어요.' : '완료했어요.'),
    onError: (e) => toast.error(isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.'),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.tasks(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.progress(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.heatmap(goalId) })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      // 기록형 목표의 "나의 패턴" · "업무 일지"도 완료한 Task 로 계산해요 (record/api 의 recordKeys.all)
      qc.invalidateQueries({ queryKey: ['record'] })
    },
  })
}

// ─── 루틴 실천 히트맵 ──────────────────────────────────────────

/** GET /routines/{goalId}/heatmap — 루틴마다 반복 요일 · 기간 · 완료한 날짜 */
export interface RoutineHeatmap {
  routineId: number
  routineName: string
  repeatStartDate: string
  repeatEndDate: string | null
  /** "MONDAY" … */
  repeatDays: JavaDayOfWeek[]
  /** 루틴 Task 를 완료한 날짜 "YYYY-MM-DD" */
  completedDates: string[]
  currentStreak: number
  longestStreak: number
  /** 주로 몇 시부터 몇 시에 했는지. 시각 기록이 없으면 null */
  timeStats?: RoutineTimeStats | null
}

export interface RoutineTimeStats {
  /** "HH:mm" */
  bestStart: string
  bestEnd: string
  /** 시각을 알 수 있는 실천 횟수 */
  practiceCount: number
  /** 그중 bestStart~bestEnd 에 걸친 횟수 */
  inWindowCount: number
  /** 0~23시 각 시간에 걸친 실천 횟수 */
  hourly: number[]
  /** 요일별로 가장 많이 한 시간대 (많이 한 요일 순, 최대 3개 · 2026-10) */
  byDay?: RoutineDayTimeStat[]
}

export interface RoutineDayTimeStat {
  /** MONDAY ~ SUNDAY */
  day: string
  start: string
  end: string
  /** 그 요일 실천 중 이 시간대에 걸친 횟수 */
  count: number
  /** 그 요일에 실천한 횟수 */
  total: number
}

export function useRoutineHeatmap(goalId: number) {
  return useQuery({
    queryKey: goalKeys.heatmap(goalId),
    queryFn: async () => (await api.get<RoutineHeatmap[]>(`/v1/routines/${goalId}/heatmap`)) ?? [],
  })
}

// ─── 진행 도표 ("지금 어디쯤?") ───────────────────────────────────

export type ProgressStatus = 'NOT_STARTED' | 'AHEAD' | 'ON_TRACK' | 'BEHIND' | 'ACHIEVED' | 'NO_PLAN'

export interface ProgressPoint {
  date: string
  value: number
  /** "시작" · 마일스톤 제목 · "목표" */
  label: string | null
}

export interface MetricLog {
  date: string
  value: number
  memo: string | null
}

/** 수치 목표(체중 72 → 69kg 등)의 계획선 · 기록 · 오늘 위치 */
export interface MetricProgress {
  name: string | null
  unit: string | null
  startValue: number
  targetValue: number
  currentValue: number
  /** 계획대로라면 오늘 값 (종료일 없으면 null) */
  expectedValue: number | null
  /** (현재-시작)/(목표-시작)×100 — 음수·100 초과 가능 */
  progressPercent: number | null
  status: ProgressStatus
  /** 최근 기록 추세대로면 목표 도달 예상일 */
  projectedDate: string | null
  lastLoggedDate: string | null
  loggedToday: boolean
  planLine: ProgressPoint[]
  logs: MetricLog[]
  /** 수치 목표 마감일 (V58) — 따로 정하지 않았으면 목표 종료일. 추가 그래프는 목표 종료일 */
  targetDate?: string | null
}

export interface TaskProgressPoint {
  date: string
  /** 그날까지 하기로 한 Task 누적 */
  planned: number
  /** 그날까지 완료한 Task 누적 (오늘 이후는 null) */
  completed: number | null
}

export interface TaskProgress {
  total: number
  completed: number
  /** 오늘까지 하기로 한 Task (이미 끝낸 Task 포함) */
  dueByToday: number
  completionPercent: number | null
  series: TaskProgressPoint[]
}

export interface GoalProgress {
  goalCategoryId: number
  today: string
  startDate: string | null
  endDate: string | null
  elapsedDays: number
  totalDays: number | null
  metric: MetricProgress | null
  tasks: TaskProgress
  /** 최근 하루 회고 (최신순) */
  reflections?: GoalReflection[]
  /** 끝난 목표의 결과 (진행 중이면 null) */
  outcome?: GoalOutcome | null
  /** 주간 점검 제안 — 2주 연속 실천률이 낮거나 높을 때만 내려와요 */
  adjustment?: GoalAdjustment | null
  /** 사용자가 더 만든 그래프 (V52) — 목표 기간 위 직선 계획선. expectedValue 가 "오늘은 여기까지" */
  extraMetrics?: ExtraMetric[]
}

/** 추가 그래프 하나 (V52) */
export interface ExtraMetric {
  metricId: number
  sortOrder: number
  progress: MetricProgress
}

/** 목표 결과: 달성 · 일부 달성 · 못 함 */
export type OutcomeResult = 'ACHIEVED' | 'PARTIAL' | 'NOT_ACHIEVED'

export interface GoalOutcome {
  result: OutcomeResult
  /** USER 직접 고름 · AUTO 기간이 끝나 자동 판정 */
  source: 'USER' | 'AUTO'
  metricPercent: number | null
  routineRate: number | null
  taskRate: number | null
  /** 수치 목표인데 수치 기록이 없어 실천률로 판정했어요 (이때 metricPercent 는 null) */
  metricUnrecorded?: boolean
  /** 서버 자동 판정 (사용자가 직접 골라도 남아 있어요 — 판정 일치율 비교용) */
  autoResult?: OutcomeResult | null
  autoReason?: OutcomeAutoReason | null
  /** 자동 판정 안내 — 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요." */
  autoMessage?: string | null
}

/** 자동 판정 근거: 수치 · 루틴 실천률 · 할 일 실천률 · 기록 없음 */
export type OutcomeAutoReason = 'METRIC' | 'ROUTINE_RATE' | 'TASK_RATE' | 'NO_RECORD'

/** 결과 확인 모달 한 건 — 기한이 지나 자동 판정된 목표 (GET /v1/goal-outcomes/prompts) */
export interface OutcomePrompt {
  goalCategoryId: number
  goalName: string
  emoji: string | null
  colorCode: string | null
  startDate: string | null
  endDate: string | null
  autoResult: OutcomeResult
  autoReason: OutcomeAutoReason | null
  autoMessage: string | null
  metricPercent: number | null
  routineRate: number | null
  taskRate: number | null
  aiGenerated: boolean
}

export interface GoalAdjustment {
  key: string
  /** DOWN 하루 줄이기 · UP 하루 늘리기 */
  direction: 'DOWN' | 'UP'
  routineId: number
  routineName: string
  fromDays: number
  toDays: number
  day: JavaDayOfWeek
  previousWeekRate: number
  lastWeekRate: number
  message: string
}

/** 그날 목표를 돌아본 느낌 */
export type ReflectionMood = 'GOOD' | 'OK' | 'BAD'

/** 하루 회고: 무엇을 했는지 · 어땠는지 (예: "점심에 마라탕 먹음" · 아쉬워요) */
export interface GoalReflection {
  date: string
  mood: ReflectionMood | null
  note: string | null
}

export function useGoalProgress(goalId: number, enabled = true) {
  return useQuery({
    queryKey: goalKeys.progress(goalId),
    queryFn: () => api.get<GoalProgress>(`/v1/goal-categories/${goalId}/progress`),
    enabled,
  })
}

/**
 * 수치 기록 (같은 날짜는 덮어써요). 응답이 진행 데이터 전체라 도표는 바로 다시 그려요.
 * 목표의 현재 값도 바뀌니 목표 상세 · 목록도 다시 불러와요.
 */
export function useRecordMetric(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { date?: string; value: number; memo?: string | null }) =>
      api.put<GoalProgress>(`/v1/goal-categories/${goalId}/metric-logs`, body),
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      toast.success('기록했어요.')
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '기록하지 못했어요. 다시 시도해 주세요.'),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.detail(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.list() })
    },
  })
}

/**
 * 오늘 기록 + 회고 한 번에 (수치는 수치 목표일 때만 저장돼요).
 * mood·note 를 모두 비우면 그날 회고를 지워요.
 */
export function useCheckIn(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { date?: string; value?: number | null; mood?: ReflectionMood | null; note?: string | null }) =>
      api.put<GoalProgress>(`/v1/goal-categories/${goalId}/check-in`, body),
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      toast.success('오늘 기록을 남겼어요.')
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '기록하지 못했어요. 다시 시도해 주세요.'),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.detail(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.list() })
      // 홈 "오늘 기록" · 회고 모아보기에도 같은 기록이 보여요
      qc.invalidateQueries({ queryKey: ['home'] })
    },
  })
}

export function useDeleteMetricLog(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (date: string) => api.delete<GoalProgress>(`/v1/goal-categories/${goalId}/metric-logs/${date}`),
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      toast.success('기록을 지웠어요.')
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '지우지 못했어요. 다시 시도해 주세요.'),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: goalKeys.detail(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.list() })
    },
  })
}

/** 루틴 요일이 바뀌면 Task · 시간표 · 진행 화면이 모두 달라져요 */
function invalidateGoalPlan(qc: ReturnType<typeof useQueryClient>, goalId: number) {
  qc.invalidateQueries({ queryKey: goalKeys.tasks(goalId) })
  qc.invalidateQueries({ queryKey: goalKeys.heatmap(goalId) })
  qc.invalidateQueries({ queryKey: goalKeys.detail(goalId) })
  qc.invalidateQueries({ queryKey: timetableKeys.all })
}

/** 주간 점검 제안 적용 — 내일부터 루틴 요일이 바뀌어요 */
export function useApplyAdjustment(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (key: string) => api.post<GoalProgress>(`/v1/goal-categories/${goalId}/adjustments/apply`, { key }),
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      invalidateGoalPlan(qc, goalId)
      toast.success('내일부터 바뀐 요일로 진행해요.')
    },
    onError: (e) => {
      toast.error(isApiError(e) ? e.message : '바꾸지 못했어요. 다시 시도해 주세요.')
      // 이미 루틴이 바뀌었으면(STALE) 새 진행 정보로 제안을 다시 받아요
      qc.invalidateQueries({ queryKey: goalKeys.progress(goalId) })
    },
  })
}

/** 주간 점검 제안 넘기기 — 같은 제안은 7일 동안 다시 안 보여요 */
export function useDismissAdjustment(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (key: string) => api.post<GoalProgress>(`/v1/goal-categories/${goalId}/adjustments/dismiss`, { key }),
    onSuccess: (data) => qc.setQueryData(goalKeys.progress(goalId), data),
    onError: (e) => toast.error(isApiError(e) ? e.message : '처리하지 못했어요. 다시 시도해 주세요.'),
  })
}

/** 끝난 목표의 결과를 직접 고르기 (자동 판정을 덮어써요) */
export function useUpdateOutcome(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (result: OutcomeResult) => api.patch<GoalProgress>(`/v1/goal-categories/${goalId}/outcome`, { result }),
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      toast.success('결과를 남겼어요.')
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '저장하지 못했어요. 다시 시도해 주세요.'),
  })
}

/** 기한이 지나 자동 판정된 목표 — 앱을 열 때 "목표는 만족했나요?" 로 물어요 (오래된 순) */
export function useOutcomePrompts(enabled = true) {
  return useQuery({
    queryKey: goalKeys.outcomePrompts(),
    queryFn: async () => (await api.get<OutcomePrompt[]>('/v1/goal-outcomes/prompts')) ?? [],
    enabled,
    staleTime: 5 * 60 * 1000,
    // 모달은 부가 기능 — 실패해도 조용히 넘어가요
    retry: false,
  })
}

/** 결과 확인 모달에서 결과 고르기 — 목표 상세의 "다시 고르기"와 같은 API (자동 판정을 덮어써요) */
export function useAnswerOutcomePrompt() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ goalId, result }: { goalId: number; result: OutcomeResult }) =>
      api.patch<GoalProgress>(`/v1/goal-categories/${goalId}/outcome`, { result }),
    onSuccess: (data, { goalId }) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      qc.setQueryData<OutcomePrompt[]>(goalKeys.outcomePrompts(), (list) =>
        (list ?? []).filter((p) => p.goalCategoryId !== goalId)
      )
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '저장하지 못했어요. 다시 시도해 주세요.'),
  })
}

/** 결과 확인 모달 "나중에" — 자동 판정을 그대로 두고 다시 묻지 않아요 (목표 상세에서는 언제든 고를 수 있어요) */
export function useDismissOutcomePrompt() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (goalId: number) => api.post<void>(`/v1/goal-outcomes/prompts/${goalId}/dismiss`),
    onSettled: (_d, _e, goalId) =>
      qc.setQueryData<OutcomePrompt[]>(goalKeys.outcomePrompts(), (list) =>
        (list ?? []).filter((p) => p.goalCategoryId !== goalId)
      ),
  })
}

/** 목표 마무리 — 결과 · 소감과 함께 끝내요. 남은 루틴은 끝내고 앞으로 예정된 Task 는 서버가 지워요 */
export function useCompleteGoal(goalId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { goalCategoryEndDate: string; achievement?: string | null; result: OutcomeResult }) =>
      api.patch<GoalCategory>(`/v1/goal-categories/${goalId}/complete`, body),
    onSuccess: () => {
      toast.success('목표를 마무리했어요. 수고했어요!')
      qc.invalidateQueries({ queryKey: goalKeys.progress(goalId) })
      qc.invalidateQueries({ queryKey: goalKeys.list() })
      invalidateGoalPlan(qc, goalId)
      // 캘린더(invalidateGoalPlan) · 홈에 남아 있던 앞날의 루틴 블록이 빠져요
      qc.invalidateQueries({ queryKey: ['home'] })
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '마무리하지 못했어요. 다시 시도해 주세요.'),
  })
}

// ─── 추가 그래프 (V52) ──────────────────────────────────────────

export interface ExtraMetricInput {
  name?: string
  unit?: string
  startValue?: number
  targetValue?: number
}

/** 추가 그래프 만들기 · 고치기 · 지우기 · 기록 — 응답이 진행 데이터 전체라 도표를 바로 다시 그려요 */
function useExtraMetricMutation<V>(goalId: number, fn: (v: V) => Promise<GoalProgress>, success: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (data) => {
      qc.setQueryData(goalKeys.progress(goalId), data)
      toast.success(success)
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.'),
  })
}

export const useCreateExtraMetric = (goalId: number) =>
  useExtraMetricMutation(
    goalId,
    (v: Required<Pick<ExtraMetricInput, 'name' | 'startValue' | 'targetValue'>> & { unit?: string }) =>
      api.post<GoalProgress>(`/v1/goal-categories/${goalId}/metrics`, v),
    '그래프를 추가했어요.'
  )

export const useUpdateExtraMetric = (goalId: number) =>
  useExtraMetricMutation(
    goalId,
    ({ metricId, ...v }: ExtraMetricInput & { metricId: number }) =>
      api.patch<GoalProgress>(`/v1/goal-categories/${goalId}/metrics/${metricId}`, v),
    '그래프를 고쳤어요.'
  )

export const useDeleteExtraMetric = (goalId: number) =>
  useExtraMetricMutation(
    goalId,
    (metricId: number) => api.delete<GoalProgress>(`/v1/goal-categories/${goalId}/metrics/${metricId}`),
    '그래프를 지웠어요.'
  )

export const useRecordExtraMetric = (goalId: number) =>
  useExtraMetricMutation(
    goalId,
    ({ metricId, ...v }: { metricId: number; date?: string; value: number; memo?: string | null }) =>
      api.put<GoalProgress>(`/v1/goal-categories/${goalId}/metrics/${metricId}/logs`, v),
    '기록했어요.'
  )

export const useDeleteExtraMetricLog = (goalId: number) =>
  useExtraMetricMutation(
    goalId,
    ({ metricId, date }: { metricId: number; date: string }) =>
      api.delete<GoalProgress>(`/v1/goal-categories/${goalId}/metrics/${metricId}/logs/${date}`),
    '기록을 지웠어요.'
  )
