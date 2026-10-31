import type { GoalSubType } from '@/features/goal/kind'
/**
 * AI 목표 생성 — 백엔드 응답 타입 (인수인계 문서 3장 기준)
 */

export type SessionStatus =
  'INTERVIEWING' | 'READY_TO_GENERATE' | 'GENERATING' | 'DRAFT_READY' | 'CONFIRMED' | 'ABANDONED'

/** V52: 이유(motivation)는 더 묻지 않고, 어떻게 이룰지(approach)를 물어요. motivation 은 예전 세션 호환용 */
export type SlotKey = 'goalStatement' | 'metric' | 'motivation' | 'deadline' | 'approach' | 'practicePreference'
export type SlotStatus = 'EMPTY' | 'FILLED' | 'SKIPPED'
export type PlanningStyle = 'PLANNER' | 'SPONTANEOUS'
/** 계획 강도 — 안정형(기본)은 지금 실력 기준, 도전형은 조금 더 높게 */
export type PlanIntensity = 'STEADY' | 'CHALLENGE'
export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export interface Slot<T> {
  status: SlotStatus
  value: T | null
}

export interface MetricValue {
  name: string
  currentValue: number | null
  targetValue: number | null
  unit: string | null
}

export interface DeadlineValue {
  endDate: string // yyyy-MM-dd
  isFlexible: boolean
  /** 사용자가 말한 시작일 (없으면 오늘부터) */
  startDate?: string | null
}

export interface PracticePreferenceValue {
  preferredDays: DayOfWeek[] | null
  preferredTimeRanges: { start: string; end: string }[] | null
  unavailableNote: string | null
  workStyleAnswer: string | null
}

export interface GoalSlots {
  goalStatement: Slot<string>
  metric: Slot<MetricValue>
  motivation: Slot<string>
  deadline: Slot<DeadlineValue>
  practicePreference: Slot<PracticePreferenceValue>
  /** 어떻게 이루고 싶은지 · 정해 둔 계획 (V52, 예전 세션엔 없어요) */
  approach?: Slot<string>
}

export interface GoalSummary {
  goalStatement: string | null
  metricText: string | null
  motivation: string | null
  deadlineText: string | null
  preferenceText: string | null
  planningStyle: PlanningStyle | null
  /** 어떻게 이루고 싶은지 · 정해 둔 계획 (V52) */
  approach?: string | null
}

export interface GoalMessage {
  messageId: number
  role: 'USER' | 'ASSISTANT'
  content: string | null
  /** 'extraNote' = 인터뷰 마지막 "이밖에 알아둬야 할 정보" 질문 (슬롯 아님, 2026-10) */
  targetSlot?: SlotKey | 'extraNote' | null
  quickReplies?: string[] | null
  skippable?: boolean
  skip?: boolean
}

/** 세션 시작 / 답변 / 조기 종료 응답 */
export interface TurnResponse {
  sessionId: number
  status: SessionStatus
  questionCount: number
  maxQuestions: number
  canFinishEarly: boolean
  message: GoalMessage | null
  slots: GoalSlots
  summary: GoalSummary | null
  /** 이 목표를 위해 꼭 하고 싶은 일 (예: 헬스, 식단 기록) — 계획에 반드시 들어가요 */
  mustDoItems?: string[]
}

export interface DraftRef {
  draftId: number
  version: number
  status: 'GENERATING' | 'READY' | 'FAILED'
}

/** 세션 조회 (이어하기) 응답 */
export interface SessionDetail {
  sessionId: number
  status: SessionStatus
  questionCount: number
  maxQuestions: number
  canFinishEarly: boolean
  messages: GoalMessage[]
  slots: GoalSlots
  summary: GoalSummary | null
  latestDraft: DraftRef | null
  mustDoItems?: string[]
}

// ─── 슬롯 수정 (PATCH /slots) — 바꾼 것만 보냄 ───────────────────
export interface SlotsPatch {
  goalStatement?: string
  metric?: { name: string; currentValue: number | null; targetValue: number | null; unit: string | null }
  motivation?: string
  approach?: string
  deadline?: DeadlineValue
  practicePreference?: PracticePreferenceValue
  /** 빈 목록이면 지워요 */
  mustDoItems?: string[]
}

// ─── 초안 (GoalPlan) — 초안 응답 = 확정 요청 공통 구조 ─────────────
export type GoalCategoryType = 'HEALTH' | 'STUDY' | 'CAREER' | 'FINANCE' | 'HOBBY' | 'RELATIONSHIP' | 'LIFE' | 'ETC'
export type Comparator = 'GTE' | 'LTE' | 'EQ'
export type MetricPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'TOTAL'

export interface PlanGoal {
  title: string
  emoji: string | null
  category: GoalCategoryType
  description: string | null
  metric: { name: string; unit: string; startValue: number; targetValue: number } | null
  startDate: string
  endDate: string
  planningStyle: PlanningStyle
  aiNote: string | null
  /** 목표 카테고리 2단계 (AI 가 분류하거나 사용자가 고른 값) */
  subType?: GoalSubType | null
}

export interface PlanSubGoal {
  tempId: string
  title: string
  emoji: string | null
  description: string | null
  metric: {
    name: string
    unit: string
    comparator: Comparator
    targetValue: number
    period: MetricPeriod
  } | null
}

export interface PlanMilestone {
  tempId: string
  title: string
  description: string | null
  dueDate: string
  targetValue: number | null
}

export interface PlanTask {
  tempId: string
  type: 'ROUTINE' | 'ONE_TIME'
  subGoalTempId: string | null
  milestoneTempId: string | null
  title: string
  emoji: string | null
  durationMinutes: number
  routineDays: DayOfWeek[] | null
  preferredStartTime: string | null
  startDate: string | null
  endDate: string | null
  scheduledDate: string | null
  dueDate: string | null
  /** 루틴의 구체적인 방법 (예: "스쿼트 4x10, 런지 3x12…") */
  description?: string | null
  /** AI 가 이렇게 정한 이유 (예: "초보라 주 3회로 시작해요") — 검토 화면에서만 보여요 */
  reason?: string | null
}

export interface PlanIssue {
  ruleId: string
  path: string
  message: string
}

export interface GoalPlan {
  goal: PlanGoal
  subGoals: PlanSubGoal[]
  milestones: PlanMilestone[]
  tasks: PlanTask[]
  warnings: PlanIssue[]
  /** 이 계획이 나에게 어떻게 맞춰졌는지 (기존 초안엔 없을 수 있어요) */
  fit?: PlanFit | null
}

export interface PlanFit {
  intensity: PlanIntensity
  /** 이 사용자 기준 루틴 주간 상한 (분) */
  weeklyCapMinutes: number
  /** 이 계획의 루틴 주간 합계 (분) */
  planWeeklyMinutes: number
  /** 최근 4주 실행 기록을 바탕으로 했는지 */
  basedOnHistory: boolean
  notes: string[]
}

// ─── 초안 생성 / 폴링 ─────────────────────────────────────────────
export type DraftStatus = 'GENERATING' | 'READY' | 'FAILED'
export type ProgressStep = 'ANALYZING' | 'SUB_GOALS' | 'MILESTONES' | 'TASKS' | 'VALIDATING'
export type DraftErrorCode = 'AI_PLAN_INVALID' | 'AI_TIMEOUT' | 'AI_UNAVAILABLE' | 'AI_INVALID_OUTPUT' | 'AI_BUSY'

/** POST /drafts → 202 */
export interface DraftRequested {
  draftId: number
  version: number
  status: DraftStatus
  pollAfterMs: number
}

/** GET /drafts/{draftId} */
export interface DraftResponse {
  draftId: number
  version: number
  status: DraftStatus
  planningStyle: PlanningStyle | null
  progressStep: ProgressStep | null
  pollAfterMs: number | null
  plan: GoalPlan | null
  errorCode: DraftErrorCode | null
  message: string | null
}

// ─── 확정 ─────────────────────────────────────────────────────────
export interface ConfirmRequest {
  draftId: number
  plan: GoalPlan
  planningStyleSelectedByUser: boolean
  /** 선택한 UserColor id. 생략하면 서버가 자동 배정 */
  colorId?: number
}
