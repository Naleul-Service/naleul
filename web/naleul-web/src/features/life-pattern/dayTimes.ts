import type { JavaDayOfWeek } from '@/features/goal/api'
import { minutesToTime, timeToMinutes } from '@/features/timetable/time'
import type { LifePattern, LifePatternDayTime } from '@/features/timetable/types'
import { ALL_DAYS, toHm } from './presets'

/**
 * 고정 시간 요일별 시간 계산 (백엔드 LifePattern.occurrenceOn 과 같은 규칙).
 *
 * 블록은 "소속 요일"이 있고, 요일별 시간은 그 요일 블록만 바꿔요.
 * startDayOffset 이 -1 이면 소속 요일 전날, +1 이면 다음 날에 시작해요.
 *   예) 수면 00:00~07:00 의 토요일 블록 = 금요일 밤 → 토요일 아침.
 *       금요일 밤 23:30 에 자게 바꾸면 { day: SATURDAY, startTime: 23:30, endTime: 07:00, startDayOffset: -1 }
 */

type PatternTimes = Pick<LifePattern, 'startTime' | 'endTime' | 'days'> & { dayTimes?: LifePatternDayTime[] }

const applies = (p: PatternTimes, d: JavaDayOfWeek) => !p.days.length || p.days.includes(d)

/** 소속 요일 day 의 블록 구간 — 그 요일 0시 기준 분 (start 는 음수·1440 이상일 수 있고, end > start) */
export function occurrenceOf(p: PatternTimes, day: JavaDayOfWeek) {
  const dt = p.dayTimes?.find((x) => x.day === day)
  const start = timeToMinutes(toHm(dt?.startTime ?? p.startTime)) + (dt?.startDayOffset ?? 0) * 1440
  let end = Math.floor(start / 1440) * 1440 + timeToMinutes(toHm(dt?.endTime ?? p.endTime))
  if (end <= start) end += 1440
  return { start, end }
}

// ── 수면: "밤" 단위로 보기 ───────────────────────────────

/** 하룻밤: bed = 그날 밤 취침, wake = 다음 날 아침 기상 ("HH:mm") */
export interface SleepNight {
  bed: string
  wake: string
}

const NOON = 720

/**
 * 밤 W(월~일)가 몇 요일 블록인지: 기본 취침이 정오 전(00:00 등)이면 다음 날 소속, 정오 뒤(23:00 등)면 그날 소속.
 * (블록은 "시작한 날" 소속이라서 00:00 에 자면 다음 날 블록이에요)
 */
const nightShift = (defaultStart: string) => (timeToMinutes(toHm(defaultStart)) < NOON ? 1 : 0)

/** 패턴 → 요일별 밤 (월요일 밤 ~ 일요일 밤). 그 밤에 수면이 없으면 null */
export function sleepNights(p: PatternTimes): (SleepNight | null)[] {
  const shift = nightShift(p.startTime)
  return ALL_DAYS.map((_, w) => {
    const key = ALL_DAYS[(w + shift) % 7]
    if (!applies(p, key)) return null
    const occ = occurrenceOf(p, key)
    return { bed: minutesToTime(occ.start), wake: minutesToTime(occ.end) }
  })
}

/** 밤 W 의 취침 시각을 밤 W 날짜 0시 기준 분으로 (정오 전이면 다음 날 새벽) */
const bedFromNight = (bed: string) => {
  const m = timeToMinutes(bed)
  return m >= NOON ? m : m + 1440
}

/** 요일별 밤 → 요일별 시간 (기본 시간과 같은 밤은 빼요) */
export function nightsToDayTimes(
  defaultStart: string,
  defaultEnd: string,
  nights: (SleepNight | null)[]
): LifePatternDayTime[] {
  const shift = nightShift(defaultStart)
  const out: LifePatternDayTime[] = []
  nights.forEach((n, w) => {
    if (!n) return
    const relKey = bedFromNight(n.bed) - shift * 1440 // 소속 요일 0시 기준
    const startDayOffset = Math.floor(relKey / 1440)
    const startTime = minutesToTime(relKey)
    if (startDayOffset === 0 && startTime === toHm(defaultStart) && n.wake === toHm(defaultEnd)) return
    out.push({ day: ALL_DAYS[(w + shift) % 7], startTime, endTime: n.wake, startDayOffset })
  })
  return out
}

/**
 * 밤끼리 겹치는지 — W 밤에 일어나는 시각(다음 날 아침)이 그다음 밤 취침보다 늦으면 안 돼요.
 * @returns 문제가 있는 요일(기상하는 날) 인덱스, 없으면 -1
 */
export function sleepConflictDay(nights: (SleepNight | null)[]) {
  for (let w = 0; w < 7; w++) {
    const n = nights[w]
    const next = nights[(w + 1) % 7]
    if (!n || !next) continue
    const bed = bedFromNight(n.bed)
    let wake = Math.floor(bed / 1440) * 1440 + timeToMinutes(n.wake)
    if (wake <= bed) wake += 1440
    const nextBed = 1440 + bedFromNight(next.bed)
    if (wake >= nextBed) return (w + 1) % 7
  }
  return -1
}
