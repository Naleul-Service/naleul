/**
 * 날짜·시간 계산 도우미.
 *
 * 백엔드는 "한국 시간 벽시계" 문자열("2026-11-18T09:30:00")을 주고받아요.
 * new Date("2026-11-18T09:30:00") 은 브라우저 시간대로 해석되므로(해외 PC면 어긋남)
 * 날짜는 "YYYY-MM-DD" 문자열, 시간은 "그날 0시부터 몇 분" 숫자로만 다뤄요.
 */

export const KST = 'Asia/Seoul'
export const SNAP_MINUTES = 10

const pad = (n: number) => String(n).padStart(2, '0')

/** Date(UTC 기준 자정) ↔ "YYYY-MM-DD" — 날짜 산술은 UTC 로 해서 서머타임 영향 없음 */
const toUtc = (ymd: string) => {
  const [y, m, d] = ymd.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}
const fromUtc = (dt: Date) => `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())}`

export const isYmd = (v: unknown): v is string =>
  typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(toUtc(v).getTime()) && fromUtc(toUtc(v)) === v

export const addDays = (ymd: string, n: number) => {
  const dt = toUtc(ymd)
  dt.setUTCDate(dt.getUTCDate() + n)
  return fromUtc(dt)
}

/** 0=월 … 6=일 */
export const weekdayIndex = (ymd: string) => (toUtc(ymd).getUTCDay() + 6) % 7

/** 그 주 월요일 */
export const startOfWeek = (ymd: string) => addDays(ymd, -weekdayIndex(ymd))

export const WEEKDAY_LABEL = ['월', '화', '수', '목', '금', '토', '일']

export const dayOfMonth = (ymd: string) => Number(ymd.slice(8, 10))
export const monthOf = (ymd: string) => Number(ymd.slice(5, 7))
export const yearOf = (ymd: string) => Number(ymd.slice(0, 4))

/** 지금 한국 시간 { date: "YYYY-MM-DD", minutes: 0~1439 } */
export function nowKst(at: Date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: KST,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(at)
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '00'
  return {
    date: `${get('year')}-${get('month')}-${get('day')}`,
    minutes: Number(get('hour')) * 60 + Number(get('minute')),
  }
}

export const todayKst = () => nowKst().date

/** "2026-11-18T09:30:00" → { date: "2026-11-18", minutes: 570 } */
export function splitDateTime(v: string) {
  const [date, time = '00:00'] = v.split('T')
  const [h, m] = time.split(':').map(Number)
  return { date, minutes: h * 60 + (m || 0) }
}

/**
 * 어떤 날짜(dayYmd) 기준 0시부터 몇 분인지. 다음 날 01:00 이면 1500.
 * (자정을 넘기는 블록을 한 칸 안에서 계산하기 위해)
 */
export function minutesFrom(dayYmd: string, v: string) {
  const { date, minutes } = splitDateTime(v)
  const diffDays = Math.round((toUtc(date).getTime() - toUtc(dayYmd).getTime()) / 86_400_000)
  return diffDays * 1440 + minutes
}

/** 날짜 + 분 → "YYYY-MM-DDTHH:mm:00" (분이 1440 이상이면 다음 날로) */
export function toDateTime(dayYmd: string, minutes: number) {
  const dayShift = Math.floor(minutes / 1440)
  const m = minutes - dayShift * 1440
  return `${addDays(dayYmd, dayShift)}T${pad(Math.floor(m / 60))}:${pad(m % 60)}:00`
}

/** 570 → "09:30", 1440 → "24:00" */
export const formatMinutes = (minutes: number) => {
  const m = ((minutes % 1440) + 1440) % 1440
  const h = minutes > 0 && m === 0 && minutes % 1440 === 0 ? 24 : Math.floor(m / 60)
  return `${pad(h)}:${pad(m % 60)}`
}

/** "2026-11-18T09:30:00" → "09:30" */
export const hm = (v?: string | null) => (v ? v.slice(11, 16) : '')

/** "09:30:00" | "09:30" → 570 */
export const timeToMinutes = (t: string) => {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + (m || 0)
}

/** 570 → "09:30" (LocalTime 요청용, 24:00 은 00:00) */
export const minutesToTime = (minutes: number) => {
  const m = ((minutes % 1440) + 1440) % 1440
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`
}

export const snap = (minutes: number, step = SNAP_MINUTES) => Math.round(minutes / step) * step

/** "11월 18일 (수)" */
export const formatMonthDay = (ymd: string) =>
  `${monthOf(ymd)}월 ${dayOfMonth(ymd)}일 (${WEEKDAY_LABEL[weekdayIndex(ymd)]})`

/** 주간 제목: "2026년 11월 16일 – 22일" / 달이 바뀌면 "11월 30일 – 12월 6일" */
export function formatRange(start: string, end: string) {
  if (start === end) return `${yearOf(start)}년 ${formatMonthDay(start)}`
  const head = `${yearOf(start)}년 ${monthOf(start)}월 ${dayOfMonth(start)}일`
  if (yearOf(start) !== yearOf(end)) return `${head} – ${yearOf(end)}년 ${monthOf(end)}월 ${dayOfMonth(end)}일`
  if (monthOf(start) !== monthOf(end)) return `${head} – ${monthOf(end)}월 ${dayOfMonth(end)}일`
  return `${head} – ${dayOfMonth(end)}일`
}

/** 75 → "1시간 15분" */
export function formatDuration(minutes: number) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h && m) return `${h}시간 ${m}분`
  if (h) return `${h}시간`
  return `${m}분`
}

/** 달 이동. 31일 → 다음 달에 31일이 없으면 그 달 마지막 날 */
export function addMonths(ymd: string, n: number) {
  const y = yearOf(ymd)
  const m = monthOf(ymd) - 1 + n
  const ny = y + Math.floor(m / 12)
  const nm = ((m % 12) + 12) % 12
  const last = new Date(Date.UTC(ny, nm + 1, 0)).getUTCDate()
  return `${ny}-${pad(nm + 1)}-${pad(Math.min(dayOfMonth(ymd), last))}`
}

/** 월간 달력 칸: 그 달 1일이 있는 주 월요일부터, 마지막 날이 있는 주 일요일까지 (주 단위 배열) */
export function monthWeeks(year: number, month: number) {
  const first = `${year}-${pad(month)}-01`
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const last = `${year}-${pad(month)}-${pad(lastDay)}`
  const weeks: string[][] = []
  for (let d = startOfWeek(first); d <= last; d = addDays(d, 7)) {
    weeks.push(Array.from({ length: 7 }, (_, i) => addDays(d, i)))
  }
  return weeks
}

/** 오늘 기준 D-day 문구: 오늘 / 내일 / D-3 / 지남 */
export function dDay(ymd: string, today: string) {
  const diff = Math.round((toUtc(ymd).getTime() - toUtc(today).getTime()) / 86_400_000)
  if (diff === 0) return '오늘'
  if (diff === 1) return '내일'
  if (diff < 0) return `${-diff}일 지남`
  return `D-${diff}`
}

/**
 * 루틴 시간 범위가 괜찮은지 ("HH:mm"). 끝이 시작보다 이르면 자정을 넘기는 루틴이에요 (23:00 ~ 00:00 = 오후 11시 ~ 오전 12시).
 * 자정을 넘기면 12시간까지 (백엔드 RoutineValidator 와 같은 규칙). 같은 시각은 안 돼요.
 */
export function isValidRoutineRange(start: string, end: string) {
  const s = timeToMinutes(start)
  const e = timeToMinutes(end)
  if (s === e) return false
  if (e > s) return true
  return 1440 - (s - e) <= 12 * 60
}
