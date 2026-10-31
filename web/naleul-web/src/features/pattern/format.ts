import type { JavaDayOfWeek } from '@/features/goal/api'
import type { DayGroup, PatternReport, TimeWindow } from './types'

export const DAY_SHORT: Record<JavaDayOfWeek, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
}

const GROUP_LABEL: Record<DayGroup, string> = { WEEKDAY: '평일', WEEKEND: '주말', ALL: '매일' }

const pad = (n: number) => String(n).padStart(2, '0')

/** "2026-10-01" → "10.01" (Date 로 파싱하지 않아요 — UTC 로 해석돼 하루 밀리는 문제 방지) */
export const dotDate = (ymd: string) => `${ymd.slice(5, 7)}.${ymd.slice(8, 10)}`

/** 헤더 문구: "10.01 – 11.18 · 49일, TimeBlock 412개 기록 기반" */
export function rangeLabel(r: PatternReport) {
  return `${dotDate(r.range.start)} – ${dotDate(r.range.end)} · ${r.range.days}일, TimeBlock ${r.timeBlockCount.toLocaleString()}개 기록 기반`
}

/** "10월 9일 13:42" — 서버가 준 한국 시간 문자열 그대로 */
export function updatedLabel(iso: string) {
  const [d, t] = iso.split('T')
  if (!d || !t) return iso
  return `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일 ${t.slice(0, 5)}`
}

/** 골든/취약 시간: "평일 19–22시" */
export const windowLabel = (w: TimeWindow) => `${GROUP_LABEL[w.dayGroup]} ${pad(w.startHour)}–${pad(w.endHour)}시`

/** +12분 / −3분 / 0분 */
export const signedMinutes = (m: number) => (m > 0 ? `+${m}분` : m < 0 ? `−${Math.abs(m)}분` : '0분')

/** 0.0 → "0", 7.5 → "7.5" */
export const hours1 = (h: number) => (Number.isInteger(h) ? String(h) : h.toFixed(1))

/** 인사이트 아이콘 키 → 이모지 */
export const INSIGHT_ICON: Record<string, string> = {
  moon: '🌙',
  sun: '☀️',
  sunrise: '🌅',
  calendar: '📅',
  alarm: '⏰',
  'chart-down': '📉',
  fire: '🔥',
  sparkle: '✨',
}

/** 히트맵 단계 색 (1 낮음 ~ 5 높음) */
export const HEAT_BG: Record<number, string> = {
  1: 'var(--color-heat-1)',
  2: 'var(--color-heat-2)',
  3: 'var(--color-heat-3)',
  4: 'var(--color-heat-4)',
  5: 'var(--color-heat-5)',
}

export const heatText = (level: number) => (level >= 3 ? 'text-white' : 'text-ink-2')

/** 빗금 배경 (고정 시간) */
export const HATCH = 'repeating-linear-gradient(135deg, var(--color-line) 0 3px, var(--color-subtle) 3px 7px)'
