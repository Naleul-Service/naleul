/**
 * 운영 대시보드 (GET /api/v1/admin/dashboard) 응답 타입.
 * 백엔드 AdminDashboardResponse 와 1:1. 비율(%)이 null 이면 "분모가 0" — 0% 와 구분해서 '–' 로 보여줘요.
 */

export interface AdminDashboard {
  period: { from: string; to: string; days: number; bucket: 'DAY' | 'WEEK'; generatedAt: string }
  finance: Finance
  users: DashUsers
  goals: Goals
  tasks: Execution
  routines: Execution
  /** Task 실천률 쪼개 보기 (2026-10-25 이전 서버면 없음) */
  practice?: Practice
  aiGoalFunnel: AiGoalFunnel
  /** AI 품질 — 기능마다 ① 채택 ② 수정 ③ 결과 + 자동 판정 신뢰도 */
  aiQuality?: AiQuality
  series: DailyPoint[]
  notes: string[]
}

export interface Finance {
  revenue: Revenue
  aiCost: AiCost
  serverCost: ServerCost
  profit: Profit
}

export interface Revenue {
  grossKrw: number
  refundKrw: number
  appleFeeKrw: number
  netKrw: number
  purchaseCount: number
  renewalCount: number
  refundCount: number
  activeSubscriptions: number
  estimatedMrrKrw: number
  appleFeeRate: number
  products: ProductRow[]
  trackingSince: string | null
}

export interface ProductRow {
  productId: string
  periodPayments: number
  periodGrossKrw: number
  activeSubscriptions: number
  /** null 이면 가격을 몰라 MRR 에서 빠졌어요 */
  monthlyPriceKrw: number | null
}

export interface AiCost {
  usd: number
  krw: number
  usdKrw: number
  calls: number
  failedCalls: number
  inputTokens: number
  outputTokens: number
  cacheWriteTokens: number
  cacheReadTokens: number
  byFeature: AiFeatureRow[]
  byModel: AiModelRow[]
  unpricedModels: string[]
  trackingSince: string | null
}

export interface AiFeatureRow {
  feature: string
  label: string
  calls: number
  failedCalls: number
  inputTokens: number
  outputTokens: number
  usd: number
  krw: number
  avgUsdPerCall: number | null
}

export interface AiModelRow {
  model: string
  calls: number
  inputTokens: number
  outputTokens: number
  usd: number
  krw: number
}

export interface ServerCost {
  monthlyKrw: number
  periodKrw: number
  items: { name: string; monthlyKrw: number; periodKrw: number }[]
}

export interface Profit {
  netRevenueKrw: number
  totalCostKrw: number
  profitKrw: number
  aiCostPerActiveUserKrw: number | null
  costPerActiveUserKrw: number | null
}

export interface DashUsers {
  total: number
  pro: number
  proRatePercent: number | null
  active7d: number
  active30d: number
  activeInPeriod: number
  withActiveGoal: number
}

export interface Goals {
  ended: number
  achieved: number
  partial: number
  notAchieved: number
  /** 달성 / 끝난 목표 — 핵심 지표 */
  achievementRate: number | null
  successRate: number | null
  inProgress: number
  avgExecutionRate: number | null
  onTrackRate: number | null
  /** 실천률 0–19 · 20–39 · 40–59 · 60–79 · 80–100 구간별 목표 수 */
  executionHistogram: number[]
  byType: GoalGroupRow[]
  bySource: GoalGroupRow[]
  /** 끝난 목표를 만든 방법 · AI 초안 경로별로 (V68, 2026-10-25) */
  byPath?: GoalPathRow[]
  /** 진행 중 목표 실천률을 AI 전체 · 경로별 · 직접으로 */
  inProgressByPath?: InProgressGroup[]
}

/** AS_IS 그대로 확정 · MANUAL 직접 고쳐 확정 · QUICK_FIX 말로 고쳐 확정 · REGEN 다시 생성 뒤 확정 · AI_LEGACY · MANUAL_GOAL */
export interface GoalPathRow {
  key: string
  label: string
  ended: number
  achieved: number
  partial: number
  achievementRate: number | null
  successRate: number | null
  avgRoutineRate: number | null
  avgTaskRate: number | null
  /** 초안 보존율 평균 (경로 기록이 있는 AI 목표만) */
  avgKeptRate: number | null
  /** 사용자가 직접 결과를 고른 목표 수 (나머지는 자동 판정) */
  userPicked: number
}

export interface InProgressGroup {
  key: string
  label: string
  goals: number
  avgExecutionRate: number | null
  onTrackRate: number | null
}

/** Sub 핵심 지표 · Task 실천률 쪼개 보기 (2026-10-25) */
export interface Practice {
  /** 실천률 B — 그날 앱을 쓴 사용자의 그날 Task · 루틴만 */
  activeDay: {
    taskDue: number
    taskDone: number
    taskRate: number | null
    routineDue: number
    routineDone: number
    routineRate: number | null
  }
  /** AI_KEPT · AI_EDITED · AI_ADDED · AI_OTHER · MANUAL · NO_GOAL */
  origins: {
    key: string
    label: string
    routineDue: number
    routineDone: number
    routineRate: number | null
    taskDue: number
    taskDone: number
    taskRate: number | null
    users: number
  }[]
  visitTrackingSince: string | null
}

export interface GoalGroupRow {
  key: string
  label: string
  ended: number
  achieved: number
  partial: number
  achievementRate: number | null
  avgRoutineRate: number | null
  avgTaskRate: number | null
}

export interface Execution {
  due: number
  completed: number
  skipped: number
  rate: number | null
  users: number
  activeItems: number
}

export interface AiGoalFunnel {
  sessions: number
  drafts: number
  confirmed: number
  abandoned: number
  conversionRate: number | null
}

export interface DailyPoint {
  date: string
  taskDue: number
  taskDone: number
  taskRate: number | null
  routineDue: number
  routineDone: number
  routineRate: number | null
  aiUsd: number
  activeUsers: number
}

/** AI 품질 (채택 · 수정 · 결과) — 백엔드 AdminDashboardResponse.AiQuality */
export interface AiQuality {
  features: AiQualityRow[]
  outcomeCheck: OutcomeCheck
}

/**
 * key: GOAL_PLAN · TIMETABLE · GOAL_EDIT · SCHEDULE_EDIT · BRAIN_DUMP · PATTERN
 * 비율은 분모(…Base)가 0 이면 null. editLabel 이 null 이면 해당 없음.
 */
export interface AiQualityRow {
  key: string
  label: string
  adoptLabel: string
  adoptRate: number | null
  adoptBase: number
  editLabel: string | null
  editRate: number | null
  editBase: number
  resultLabel: string
  resultRate: number | null
  resultBase: number
  /** ③과 비교할 기준 (적용 전 같은 기간 실천률) */
  beforeRate: number | null
  extraLabel: string | null
  extraRate: number | null
  extraBase: number
}

/** 자동 판정 신뢰도 — 결과 확인 모달 응답률 · 직접 고른 결과와의 일치율 */
export interface OutcomeCheck {
  autoJudged: number
  answered: number
  dismissed: number
  pending: number
  responseRate: number | null
  compared: number
  matched: number
  matchRate: number | null
  /** 수치 80~99% 라 자동 "달성" 처리된 목표 수 */
  metric80: number
  byReason: { reason: string; label: string; compared: number; matched: number; matchRate: number | null }[]
}

// ───────────── AI 채팅 정리 정확도 (V67, GET /v1/admin/dashboard/chat-review) ─────────────

export type ChatReviewField =
  'GOAL_STATEMENT' | 'METRIC' | 'DEADLINE' | 'APPROACH' | 'MOTIVATION' | 'PRACTICE_PREFERENCE' | 'PLANNING_STYLE'

export interface ChatReviewFieldRow {
  key: ChatReviewField
  label: string
  /** AI 가 채운 세션 (수정률 · 진행률 분모) */
  aiFilled: number
  edited: number
  editRate: number | null
  chipAnswers: number
  typedAnswers: number
  /** 이 항목을 물은 질문에 칩을 눌러 답한 비율 (실행 성향은 null) */
  chipRate: number | null
  typedRate: number | null
  /** AI 가 비워 둔 세션 (누락률 분모) */
  emptyBase: number
  added: number
  /** 이유 · 실행 성향은 null */
  missRate: number | null
  progressRate: number | null
}

export interface ChatReview {
  period: { from: string; to: string; days: number; generatedAt: string }
  filters: { goalType: string | null; promptVersion: string | null; answerSource: 'CHIP' | 'TYPED' | null }
  /** 정리 화면에서 계획 만들기 누른 세션 */
  sessions: number
  overall: {
    progressRate: number | null
    editRate: number | null
    missRate: number | null
    untouchedSessions: number
    editedSessions: number
    addedSessions: number
    avgEditedItems: number | null
  }
  fields: ChatReviewFieldRow[]
  exit: { entered: number; reviewed: number; exited: number; pending: number; exitRate: number | null }
  promptVersions: string[]
  /** 이보다 표본이 적은 항목은 흐리게 */
  minSample: number
}

export interface ChatReviewItems {
  stats: ChatReviewFieldRow
  page: number
  size: number
  total: number
  items: {
    at: string
    result: 'EDITED' | 'ADDED'
    question: string | null
    chips: string[]
    answer: string | null
    answerSource: 'CHIP' | 'TYPED' | 'SKIP' | null
    aiValue: string | null
    finalValue: string | null
    /** false 면 6개월 보관 기간이 지나 원문이 지워짐 */
    retained: boolean
  }[]
}

// ───────────── AI 초안 정확도 (V68, GET /v1/admin/dashboard/draft-review) ─────────────

export type DraftItemKind = 'GOAL' | 'ROUTINE' | 'ONE_TIME' | 'SUB_GOAL' | 'MILESTONE'
/** ① 다시 생성 > ② 말로 고치기 > ③ 직접 수정 > ④ 그대로 확정 (가장 강한 신호 하나) */
export type DraftPath = 'REGEN' | 'QUICK_FIX' | 'MANUAL' | 'AS_IS'
export type EditDirection = 'EARLIER' | 'LATER' | 'MORE' | 'LESS' | 'CHANGED'

export interface DraftReview {
  period: { from: string; to: string; days: number; generatedAt: string }
  filters: {
    goalType: string | null
    intensity: string | null
    planningStyle: string | null
    promptVersion: string | null
  }
  /** 확정 뒤 유지율 · 실천률을 센 기간 (첫 행동부터 14 · 30일) */
  window: 14 | 30
  /** 확정한 세션 */
  sessions: number
  headline: {
    /** 대표: AI 값이 그대로 남은 칸 ÷ AI 가 만든 칸 (자동 조정 제외) */
    preservationRate: number | null
    keptFields: number
    aiFields: number
    /** 실패 신호: 다시 생성을 쓴 세션 ÷ 초안 화면에 들어온 세션 */
    regenRate: number | null
    regenSessions: number
    enteredSessions: number
    /** 첫 행동부터 window 일 동안 안 고치고 안 지운 루틴 · Task 비율 */
    retentionRate: number | null
    retainedItems: number
    evaluatedItems: number
  }
  funnel: { entered: number; confirmed: number; exited: number; pending: number; exitRate: number | null }
  paths: {
    path: DraftPath
    sessions: number
    share: number | null
    practiceRate: number | null
    routinePlanned: number
    retentionRate: number | null
    evaluatedItems: number
  }[]
  quickFix: {
    sessions: number
    once: number
    twice: number
    threePlus: number
    /** 말로 고친 뒤 다시 생성 없이 확정 ÷ 말로 고치기를 쓰고 끝난 세션 */
    successRate: number | null
    succeeded: number
    finished: number
    /** 말로 고치다가 다시 생성으로 넘어간 세션 */
    thenRegen: number
    /** 말로 고치기 직전 손으로 고친 칸 수 */
    baseEditCount: number
  }
  buckets: {
    bucket: 'NEAR' | 'SOME' | 'MANY'
    sessions: number
    share: number | null
    practiceRate: number | null
    routinePlanned: number
    achievementRate: number | null
    endedGoals: number
  }[]
  items: {
    kind: DraftItemKind
    label: string
    aiItems: number
    edited: number
    deleted: number
    added: number
    editRate: number | null
    deleteRate: number | null
    addRate: number | null
    keptRate: number | null
    topField: string | null
    topFieldLabel: string | null
    topFieldRate: number | null
    retentionRate: number | null
    evaluatedItems: number
  }[]
  directions: {
    kind: DraftItemKind
    field: string
    fieldLabel: string
    direction: EditDirection
    count: number
    rate: number | null
    avgDelta: number | null
    editedPractice: number | null
    editedPlanned: number
    keptPractice: number | null
    keptPlanned: number
  }[]
  /** 루틴 시간 변화: 확정한 루틴 일주일 총 시간 ÷ AI 초안 루틴 일주일 총 시간 */
  weeklyTime: {
    intensity: string
    sessions: number
    draftMinutes: number
    confirmedMinutes: number
    rate: number | null
  }[]
  /** 확정 뒤 14일 안에 첫 행동이 없는 목표 */
  notUsed: { count: number; base: number; rate: number | null }
  promptVersions: string[]
  minSample: number
}

export interface DraftReviewItems {
  kind: DraftItemKind
  label: string
  fields: {
    field: string
    label: string
    base: number
    edited: number
    editRate: number | null
    afterBase: number
    afterEdited: number
    afterRate: number | null
  }[]
  page: number
  size: number
  total: number
  cases: {
    at: string | null
    stage: 'BEFORE_CONFIRM' | 'AFTER_CONFIRM'
    result: 'EDITED' | 'DELETED' | 'ADDED'
    itemLabel: string | null
    field: string
    fieldLabel: string
    path: DraftPath
    /** 그 세션의 말로 고치기 · 다시 생성 요청 문장 (6개월 보관) */
    request: string | null
    aiValue: string | null
    finalValue: string | null
    direction: EditDirection | null
    delta: number | null
  }[]
}

// ───────────── Aha-moment (V69, GET /v1/admin/dashboard/aha) ─────────────

export interface AhaMoment {
  period: { from: string; to: string; days: number; generatedAt: string }
  /** 기간 안에 가입한 사람 (운영진 제외) */
  signups: number
  /** 가입 후 7일이 지나 판정할 수 있는 사람 */
  eligible: number
  /** 아직 7일이 안 지난 사람 */
  pending: number
  aha: { users: number; rate: number | null }
  conditions: { key: 'GOALS' | 'TASKS' | 'VISITS'; label: string; users: number; rate: number | null }[]
  /** ① → ①+② → ①+②+③ */
  funnel: { label: string; users: number; rate: number | null }[]
  /** 가입 14~20일째에 다시 온 비율 (가입 후 21일 지난 사람만) */
  retention: {
    reachedBase: number
    reachedRetained: number
    reachedRate: number | null
    notReachedBase: number
    notReachedRetained: number
    notReachedRate: number | null
    gapPp: number | null
    overallBase: number
    overallRate: number | null
  }
  distributions: Record<'goals' | 'tasks' | 'visitDays', { label: string; users: number; share: number | null }[]>
  cohorts: {
    weekStart: string
    signups: number
    eligible: number
    goalsRate: number | null
    tasksRate: number | null
    visitsRate: number | null
    ahaRate: number | null
    d14Reached: number | null
    d14NotReached: number | null
  }[]
  /** 방문 기록을 시작한 날 (그 전은 Task 완료 · 실제 한 일로만 "온 날"을 셈) */
  visitTrackingSince: string | null
  minSample: number
}

// ───────────── AI 관심도 (V70, GET /v1/admin/dashboard/ai-interest) ─────────────

export type AiPlan = 'FREE' | 'PRO'

export interface AiInterestPlanStat {
  /** 써 본 사람 (시도 당시 플랜) */
  users: number
  /** 써 본 사람 ÷ 같은 플랜 활동 사용자 */
  triedRate: number | null
  attempts: number
  completed: number
  completedRate: number | null
  dropped: number
  dropRate: number | null
  inProgress: number
  completedUsers: number
  /** 2번 이상 끝까지 쓴 사람 */
  repeatUsers: number
}

export interface AiInterestDropStat {
  count: number
  /** 이 기능 중단 중 % */
  ofDrops: number | null
  /** 이 기능 시도 중 % */
  ofAttempts: number | null
}

export interface AiInterest {
  period: { from: string; to: string; days: number; generatedAt: string }
  plans: {
    plan: AiPlan
    activeUsers: number
    triedUsers: number
    triedRate: number | null
    completedUsers: number
    completedRate: number | null
    avgFeatures: number | null
  }[]
  features: {
    key: string
    label: string
    page: string
    completeLabel: string
    free: AiInterestPlanStat
    pro: AiInterestPlanStat
    note: string
  }[]
  drops: {
    feature: string
    featureLabel: string
    page: string
    step: string
    detail: string
    free: AiInterestDropStat
    pro: AiInterestDropStat
  }[]
  limits: {
    feature: string
    featureLabel: string
    reason: string
    reasonLabel: string
    free: number
    freeUsers: number
    pro: number
    proUsers: number
  }[]
  /** 한도에 막힌 기록을 시작한 시각 (V70 배포) */
  limitTrackingSince: string | null
}
