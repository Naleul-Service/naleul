import type { PlanIntensity, PlanningStyle } from './types'

export const PLANNING_STYLE_LABEL: Record<PlanningStyle, string> = {
  PLANNER: '차근차근 계획형',
  SPONTANEOUS: '몰아서 하는 즉흥형',
}

export const PLAN_INTENSITY_LABEL: Record<PlanIntensity, string> = {
  STEADY: '안정형',
  CHALLENGE: '도전형',
}

export const PLAN_INTENSITY_DESCRIPTION: Record<PlanIntensity, string> = {
  STEADY: '지금 실천량에 맞춰 작게 시작해 꾸준히 이어가요',
  CHALLENGE: '조금 더 빡빡하게, 평소보다 한 단계 높여 잡아요',
}

/** 시작 화면의 예시 목표 칩 */
export const EXAMPLE_GOALS = [
  '몸무게 10kg 감량하기',
  '토익 900점 받기',
  '올해 책 12권 읽기',
  '매일 아침 운동하는 습관 만들기',
]

/** 닫기 확인창 문구 (명세 9-1) */
export const LEAVE_NOTICE = '진행 중인 목표는 7일 동안 이어서 만들 수 있어요.'

export const DAY_LABEL: Record<import('./types').DayOfWeek, string> = {
  MON: '월',
  TUE: '화',
  WED: '수',
  THU: '목',
  FRI: '금',
  SAT: '토',
  SUN: '일',
}
export const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const

/** G-3 생성 단계 문구 (명세 9-3) */
export const PROGRESS_STEPS = [
  { step: 'ANALYZING', label: '목표를 분석하고 있어요' },
  { step: 'SUB_GOALS', label: '세부 목표를 나누고 있어요' },
  { step: 'MILESTONES', label: '마일스톤을 정하고 있어요' },
  { step: 'TASKS', label: '꼭 필요한 루틴만 짜고 있어요' },
  { step: 'VALIDATING', label: '계획을 점검하고 있어요' },
] as const

/** 폴링 간격 / 최대 대기 (명세: 2초, 90초) */
export const POLL_INTERVAL_MS = 2000
export const POLL_TIMEOUT_MS = 90_000
