/**
 * TimeTable API 응답 타입 (백엔드 BE-2/4/6 DTO 와 1:1)
 *
 * ⚠️ 날짜/시간 문자열은 모두 "한국 시간 벽시계" 값이에요. (Z, +09:00 없음)
 *   - LocalDate      → "2026-11-18"
 *   - LocalDateTime  → "2026-11-18T09:30:00"
 *   - LocalTime      → "09:30:00"
 *   브라우저 Date 로 바꾸면 사용자 PC 시간대에 따라 어긋나므로, 문자열 그대로 계산해요. (time.ts)
 */
import type { JavaDayOfWeek } from '@/features/goal/api'

export type LifePatternType = 'SLEEP' | 'LUNCH' | 'DINNER' | 'CUSTOM'
export type TaskStatus = 'TODO' | 'COMPLETED' | 'SKIPPED' | 'DELETED'
export type TaskPriority = 'A' | 'B' | 'C' | 'D' | 'E'
export type TaskKind = 'NORMAL' | 'APPOINTMENT'
export type TaskSourceType = 'MANUAL' | 'ROUTINE' | 'MISSION'
export type PlacedBy = 'USER' | 'ENGINE'
export type DailyPlanStatus = 'GENERATING' | 'GENERATED' | 'REVIEWED' | 'FAILED'
export type DailyPlanSource = 'NIGHTLY' | 'BRAIN_DUMP' | 'REPLAN' | 'GOAL_CREATED'

export interface TimeBlockTask {
  taskId: number
  taskName: string
  emoji?: string | null
  taskStatus: TaskStatus
  taskPriority?: TaskPriority | null
  taskKind?: TaskKind | null
  sourceType: TaskSourceType
  source?: string | null
  goalCategoryId?: number | null
  goalCategoryName?: string | null
  goalEmoji?: string | null
  goalColorCode?: string | null
  goalTemporary: boolean
  generalCategoryId?: number | null
  generalCategoryName?: string | null
  milestoneId?: number | null
  milestoneTitle?: string | null
  /** 업무형 목표의 세부 목표 (로드맵 · WorkTopic) — 없으면 "세부 목표 없음" */
  workTopicId?: number | null
  workTopicName?: string | null
  routineId?: number | null
  date?: string | null
  plannedStartAt?: string | null
  plannedEndAt?: string | null
  plannedDurationMinutes?: number | null
  scheduledDate?: string | null
  dueDate?: string | null
  locked: boolean
  placedBy?: PlacedBy | null
  placementReason?: string | null
  placementReasonCodes?: string[]
  carryOverCount: number
  completedAt?: string | null
  /** 완료한 Task 의 실제 수행 시각 (완료 전이거나 실제 시각 기록이 없으면 null) */
  actualStartAt?: string | null
  actualEndAt?: string | null
  /** 여러 번 나눠서 했을 때만 각 구간 (예: 13~15시, 16~18시). 한 번에 했으면 비어 있어요 */
  actualSegments?: { start: string; end: string }[]
  /**
   * 자동 배치가 연속된 빈 시간이 없어 나눠 넣었을 때만 계획 구간 (예: 09~12시, 13~14시 — 점심 앞뒤).
   * plannedStartAt ~ plannedEndAt 은 첫 시작 ~ 마지막 끝, plannedDurationMinutes 는 구간 합계예요
   */
  plannedSegments?: { start: string; end: string }[]
  missed: boolean
  /** 22시 이월로 놓였지만 아직 확인 안 함 → 점선 블록 + "옮길까요?" */
  carryPending?: boolean
  /** 이월되기 전 날짜 */
  carriedFromDate?: string | null
  /** (화면 전용) "AI로 배치하기" 미리보기 제안 — 아직 저장 안 된 점선 블록 */
  proposed?: boolean
}

export interface Covered {
  taskId: number
  start: string
  end: string
}

export interface FixedBlock {
  lifePatternId: number
  patternType: LifePatternType
  title: string
  emoji?: string | null
  /** 그날만 변경 API 에 넘길 날짜 (자정을 넘긴 수면의 다음 날 부분이어도 전날) */
  targetDate: string
  start: string
  end: string
  overridden: boolean
  clippedFromPreviousDay: boolean
  coveredBy?: Covered[]
}

export interface DayPlan {
  status: DailyPlanStatus
  source: DailyPlanSource
  summary?: string | null
  needsReview: boolean
  generatedAt?: string | null
}

export interface DayStats {
  total: number
  completed: number
  missed: number
  /** 완료/전체 × 100. Task 가 없으면 null */
  rate?: number | null
  /** 이날 못 해서 다음 날로 넘어간 Task 수 (total·missed 에 이미 포함 — 넘어가도 실천률이 바뀌지 않게) */
  carriedOver?: number
}

export interface TimetableDay {
  date: string
  dayRange: { start: string; end: string }
  fixedBlocks: FixedBlock[]
  tasks: TimeBlockTask[]
  unscheduledTasks: TimeBlockTask[]
  plan?: DayPlan | null
  stats: DayStats
  /**
   * 하루 범위를 정한 수면 블록 (자르지 않은 원본 구간). wake 의 끝 = 기상, bed 의 시작 = 취침.
   * 화면은 기상 전·취침 후 2시간을 같이 보여주고, 이 두 가장자리를 끌어서 바꿔요. 수면 패턴이 없으면 null.
   */
  sleep?: { wake: FixedBlock | null; bed: FixedBlock | null } | null
  /** 시간대를 정한 기본 생활 습관 (예: 샤워 20:00~20:30) — 체크할 수 있는 블록 */
  habits?: HabitBlock[]
  /**
   * 이날 못 해서 다른 날로 옮겨진 Task 의 흔적 (2026-10) — 원래 자리에 흐린 점선 "* 이름 → 11/19로 옮김".
   * 끌 수 없고 실행률 · 개수에도 넣지 않아요. 누르면 옮겨 간 날로 가요.
   */
  carriedOut?: CarriedOutBlock[]
}

export interface CarriedOutBlock {
  taskId: number
  taskName: string
  emoji: string | null
  goalColorCode: string | null
  /** 원래 계획 시각 (시간 미정이었으면 null) */
  start: string | null
  end: string | null
  /** 옮겨 간 날짜 */
  toDate: string
}

export interface HabitBlock {
  habitId: number
  name: string
  emoji: string | null
  start: string
  end: string
  checked: boolean
}

export interface TimetableResponse {
  days: TimetableDay[]
}

export interface TaskScheduleResponse {
  task: TimeBlockTask
  moved: { taskId: number; fromStart: string; toStart: string; toEnd: string; reason?: string | null }[]
  unscheduled: number[]
  coveredFixedBlocks: { lifePatternId: number; title: string }[]
}

/** 여러 Task 를 한 번에 옮긴 결과 (묶음 드래그) */
export interface TaskBatchScheduleResponse {
  tasks: TimeBlockTask[]
  moved: TaskScheduleResponse['moved']
  unscheduled: number[]
  coveredFixedBlocks: TaskScheduleResponse['coveredFixedBlocks']
}

export interface LifePattern {
  lifePatternId: number
  patternType: LifePatternType
  title: string
  emoji?: string | null
  startTime: string
  endTime: string
  days: JavaDayOfWeek[]
  everyDay: boolean
  crossesMidnight: boolean
  sortOrder: number
  /** 요일별로 다른 시간 (기본 시간과 다른 요일만) */
  dayTimes?: LifePatternDayTime[]
  /** 이날만 패턴이면 그 날짜 "YYYY-MM-DD" (매주 반복이면 null) */
  onlyDate?: string | null
}

/**
 * 고정 시간 요일별 시간. day 요일에 "속한" 블록만 이 시간이에요.
 * startDayOffset: 블록이 그 요일 하루 앞(-1)·뒤(+1)에 시작 (예: 토요일 수면을 금요일 밤 23:30 에 시작 → -1)
 */
export interface LifePatternDayTime {
  day: JavaDayOfWeek
  startTime: string
  endTime: string
  startDayOffset: number
}

/** GET /timetable/monthly (명세 5-2) */
export interface MonthlyChip {
  taskId: number
  title: string
  colorCode?: string | null
  completed: boolean
  temporaryGoal: boolean
}

export interface MonthlyMilestoneChip {
  milestoneId: number
  title: string
  colorCode?: string | null
  goalCategoryId: number
}

export interface MonthlyDay {
  date: string
  /** 실행률 (Task 없으면 null) */
  rate?: number | null
  taskCount: number
  completedCount: number
  /** 루틴이 아닌 Task 최대 3개 */
  chips: MonthlyChip[]
  moreCount: number
  milestones: MonthlyMilestoneChip[]
}

export interface MonthlyDeadline {
  type: 'TASK' | 'MILESTONE'
  id: number
  title: string
  date: string
  colorCode?: string | null
}

export interface MonthlyTimetableResponse {
  year: number
  month: number
  days: MonthlyDay[]
  /** 이번 주(월~일) 마감인 미완료 Task + 마일스톤 */
  deadlinesThisWeek: MonthlyDeadline[]
}

/** GET /daily-plans/{date} (BE-6) */
export interface DailyPlanResponse {
  date: string
  exists: boolean
  status?: DailyPlanStatus | null
  source?: DailyPlanSource | null
  summary?: string | null
  needsReview: boolean
  placedCount: number
  carriedCount: number
  unplacedCount: number
  generatedAt?: string | null
  reviewedAt?: string | null
}

/** POST /daily-plans/{date}/replan */
export interface ReplanResponse {
  plan: DailyPlanResponse
  placed: { taskId: number; date: string; start: string; end: string; reason?: string | null }[]
  movedToOtherDays: { taskId: number; date: string; start: string; end: string; reason?: string | null }[]
  unscheduled: number[]
}

/**
 * 실제로 한 일 (GET /activities) — 계획(Task)과 상관없이 그 시간에 실제로 한 활동.
 * TimeTable 에서 계획 블록 옆 "실제" 칸에 그려요. 시각은 한국 시간 "YYYY-MM-DDTHH:mm:ss".
 */
export interface ActualActivity {
  activityId: number
  title: string
  emoji: string | null
  startAt: string
  endAt: string
  durationMinutes: number
  goalCategoryId: number | null
  goalCategoryName: string | null
  goalColorCode: string | null
  /** 원래 이 시간에 계획했던 Task (선택) */
  replacedTaskId: number | null
  replacedTaskName: string | null
  memo: string | null
}

export interface ActivityInput {
  title: string
  emoji?: string | null
  startAt: string
  endAt: string
  goalCategoryId?: number | null
  replacedTaskId?: number | null
  memo?: string | null
}

/** GET /timetable/goal-time — 기간 동안 완료한 Task 의 실천 시간, 목표별 (2026-10) */
export interface GoalTimeEntry {
  /** 목표 없이 완료한 Task 는 null 한 줄 */
  goalCategoryId: number | null
  goalCategoryName: string | null
  goalEmoji: string | null
  goalColorCode: string | null
  minutes: number
  taskCount: number
}

export interface GoalTimeResponse {
  startDate: string
  endDate: string
  totalMinutes: number
  /** 많이 쓴 순 */
  goals: GoalTimeEntry[]
}
