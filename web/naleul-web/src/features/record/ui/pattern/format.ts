import type { TimeBand } from '../../api'

export const BAND_LABEL: Record<TimeBand, string> = {
  DAWN: '새벽',
  MORNING: '오전',
  AFTERNOON: '오후',
  EVENING: '저녁',
}
export const BAND_RANGE: Record<TimeBand, string> = {
  DAWN: '0–6시',
  MORNING: '6–12시',
  AFTERNOON: '12–18시',
  EVENING: '18–24시',
}
export const BANDS: TimeBand[] = ['DAWN', 'MORNING', 'AFTERNOON', 'EVENING']

/** 칸 안에 들어갈 짧은 표기: 45 → "45분", 150 → "2.5시간" */
export function shortDuration(minutes: number) {
  if (minutes < 60) return `${minutes}분`
  const h = Math.round((minutes / 60) * 10) / 10
  return `${Number.isInteger(h) ? h : h.toFixed(1)}시간`
}

/** 그래프 축용: 150 → "2.5h" */
export function axisHours(minutes: number) {
  const h = Math.round((minutes / 60) * 10) / 10
  return `${Number.isInteger(h) ? h : h.toFixed(1)}h`
}

/**
 * 1~4 단계 (0 은 기록 없음). 가장 많이 한 날을 기준으로 나눠요.
 * 기록형은 "목표 시간"이 없어서 절대 기준 대신 내 기록끼리 비교해요.
 */
export function levelOf(minutes: number, max: number) {
  if (minutes <= 0 || max <= 0) return 0
  const r = minutes / max
  return r > 0.75 ? 4 : r > 0.5 ? 3 : r > 0.25 ? 2 : 1
}

/** 단계별 색 (나의 패턴 히트맵과 같은 파란 계열) */
export const LEVEL_BG = [
  '',
  'var(--color-heat-1)',
  'var(--color-heat-2)',
  'var(--color-heat-4)',
  'var(--color-heat-5)',
]
