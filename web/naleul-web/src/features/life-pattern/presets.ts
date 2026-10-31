import type { JavaDayOfWeek } from '@/features/goal/api'
import type { LifePattern, LifePatternType } from '@/features/timetable/types'

export const ALL_DAYS: JavaDayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
export const WEEKDAYS: JavaDayOfWeek[] = ALL_DAYS.slice(0, 5)
export const WEEKEND: JavaDayOfWeek[] = ALL_DAYS.slice(5)

export const TYPE_LABEL: Record<LifePatternType, string> = {
  SLEEP: '수면',
  LUNCH: '점심',
  DINNER: '저녁',
  CUSTOM: '고정 일정',
}

/** 종류별 색 (주간 미리보기 막대) */
export const TYPE_COLOR: Record<LifePatternType, string> = {
  SLEEP: 'var(--color-goal-slate-accent)', // 명세 9.5 — 수면도 보라색 없이
  LUNCH: 'var(--color-goal-amber-accent)',
  DINNER: 'var(--color-goal-coral-accent)',
  CUSTOM: 'var(--color-goal-mint-accent)',
}

export interface LifePatternPreset {
  key: string
  /** 추천 칩에 보이는 이름 */
  label: string
  patternType: LifePatternType
  title: string
  emoji: string
  startTime: string
  endTime: string
  days: JavaDayOfWeek[]
  /** 제목을 입력할 때 이 단어가 들어가면 추천해요 */
  keywords: string[]
  /** 사용자당 하나만 두는 종류 (수면·점심·저녁) */
  single: boolean
}

/**
 * 제목 추천 (+이동시간, +수면시간, +외출준비, +쉬는시간 …).
 * 누르면 제목·이모지·시간·요일이 한 번에 채워지고, 사용자가 시간만 고쳐서 저장하면 돼요.
 */
export const PRESETS: LifePatternPreset[] = [
  {
    key: 'sleep',
    label: '수면시간',
    patternType: 'SLEEP',
    title: '수면',
    emoji: '🌙',
    startTime: '00:00',
    endTime: '07:00',
    days: ALL_DAYS,
    keywords: ['수면', '잠', '취침', '기상'],
    single: true,
  },
  {
    key: 'getReady',
    label: '외출준비',
    patternType: 'CUSTOM',
    title: '외출준비',
    emoji: '🪞',
    startTime: '07:30',
    endTime: '08:10',
    days: WEEKDAYS,
    keywords: ['외출', '준비', '씻', '샤워'],
    single: false,
  },
  {
    key: 'commute',
    label: '이동시간',
    patternType: 'CUSTOM',
    title: '이동시간',
    emoji: '🚇',
    startTime: '08:10',
    endTime: '09:00',
    days: WEEKDAYS,
    keywords: ['이동', '출근', '퇴근', '통학', '등교', '하교'],
    single: false,
  },
  {
    key: 'break',
    label: '쉬는시간',
    patternType: 'CUSTOM',
    title: '쉬는시간',
    emoji: '☕',
    startTime: '15:00',
    endTime: '15:30',
    days: ALL_DAYS,
    keywords: ['쉬는', '휴식', '쉼', '브레이크', '낮잠'],
    single: false,
  },
  {
    key: 'lunch',
    label: '점심',
    patternType: 'LUNCH',
    title: '점심',
    emoji: '🍚',
    startTime: '12:00',
    endTime: '13:00',
    days: ALL_DAYS,
    keywords: ['점심'],
    single: true,
  },
  {
    key: 'dinner',
    label: '저녁',
    patternType: 'DINNER',
    title: '저녁',
    emoji: '🍽️',
    startTime: '18:00',
    endTime: '19:00',
    days: ALL_DAYS,
    keywords: ['저녁'],
    single: true,
  },
]

/** 수면·점심·저녁이 이미 있으면 그 패턴 (추천을 누르면 새로 만들지 않고 그걸 수정해요) */
export const existingOfType = (patterns: LifePattern[], preset: LifePatternPreset) =>
  preset.single ? patterns.find((p) => p.patternType === preset.patternType) : undefined

/** 입력한 제목과 맞는 추천. 제목이 비어 있으면 전부 */
export function matchPresets(title: string) {
  const q = title.trim()
  if (!q) return PRESETS
  return PRESETS.filter((p) => p.title.includes(q) || p.label.includes(q) || p.keywords.some((k) => q.includes(k)))
}

/** "HH:mm:ss" → "HH:mm" */
export const toHm = (t: string) => t.slice(0, 5)

const DAY_SHORT: Record<JavaDayOfWeek, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
}
export const dayShort = (d: JavaDayOfWeek) => DAY_SHORT[d]

const sameSet = (a: JavaDayOfWeek[], b: JavaDayOfWeek[]) => a.length === b.length && b.every((d) => a.includes(d))

/** ["MONDAY",…,"FRIDAY"] → "평일", 7개 → "매일", 그 외 → "월·수·금" */
export function formatDays(days: JavaDayOfWeek[]) {
  if (days.length === 0 || days.length === 7) return '매일'
  if (sameSet(days, WEEKDAYS)) return '평일'
  if (sameSet(days, WEEKEND)) return '주말'
  return ALL_DAYS.filter((d) => days.includes(d))
    .map(dayShort)
    .join('·')
}
