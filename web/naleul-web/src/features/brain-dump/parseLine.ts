/**
 * Brain dump 한 줄을 Enter 하는 순간 바로 읽어서 칩을 채워요 (서버 KoreanTaskTextParser 와 같은 규칙의 일부).
 *  - 시각: "저녁 8시", "오후 3시 반", "15:00", "3시~4시", "14:00-15:30"
 *  - 소요 시간: "3시간", "1시간 반", "90분" (시각과 상관없이 "이만큼 걸리는 일")
 *  - 날짜: "오늘", "내일", "모레" (요일·"~까지" 같은 나머지는 다음 단계에서 AI 가 해석)
 * 문장은 그대로 두고 칩만 채워요 — 서버가 제목에서 시간 표현을 정리해요.
 */
import { addDays } from '@/features/timetable/time'

export interface ParsedLine {
  date: string | null
  /** "HH:mm" */
  startTime: string | null
  endTime: string | null
  minutes: number | null
}

// "1시간"의 "1시"를 시각으로 읽지 않도록 '시' 뒤에 '간'이 오면 제외
const HM = '(오전|오후|저녁|밤|아침)?\\s*(\\d{1,2})(?::(\\d{2})|\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?)'
const TIME_RANGE = new RegExp(`${HM}\\s*[~\\-]\\s*${HM}`)
const TIME_ONE = new RegExp(HM)
// "7시 30분"의 "30분"은 시각이라 소요 시간으로 보지 않아요 ('시' 바로 뒤의 분은 제외)
const DURATION = /(\d{1,2})\s*시간(\s*반)?|(?<!시\s*)(\d{2,3})\s*분/
const RELATIVE = /(오늘|내일|모레)/

const pad = (n: number) => String(n).padStart(2, '0')

function toTime(
  ampm: string | undefined,
  h: string,
  colonMin: string | undefined,
  min: string | undefined,
  half: string | undefined,
  after: number | null,
  evening: boolean
): number | null {
  let hour = Number(h)
  const minute = colonMin ? Number(colonMin) : min ? Number(min) : half ? 30 : 0
  if (hour > 24 || minute > 59) return null
  if (ampm && ['오후', '저녁', '밤'].includes(ampm) && hour < 12) hour += 12
  else if (!ampm && !colonMin && hour >= 1 && hour <= 6)
    hour += 12 // "3시 미팅" → 15시
  else if (!ampm && evening && hour >= 7 && hour < 12) hour += 12 // "저녁 약속 7시" → 19시
  if (after !== null && hour * 60 < after && hour + 12 < 24) hour += 12 // "3시~4시" 뒤 시간 보정
  if (hour === 24) hour = 0
  return hour * 60 + minute
}

const fmt = (m: number | null) => (m === null ? null : `${pad(Math.floor(m / 60))}:${pad(m % 60)}`)

export function parseLine(text: string, today: string): ParsedLine {
  let rest = text
  const out: ParsedLine = { date: null, startTime: null, endTime: null, minutes: null }

  const rel = RELATIVE.exec(rest)
  if (rel) out.date = rel[1] === '내일' ? addDays(today, 1) : rel[1] === '모레' ? addDays(today, 2) : today

  // 소요 시간 먼저 (그래야 "2시간"을 2시로 읽지 않아요)
  const d = DURATION.exec(rest)
  if (d) {
    out.minutes = d[1] ? Number(d[1]) * 60 + (d[2] ? 30 : 0) : Number(d[3])
    rest = rest.slice(0, d.index) + ' ' + rest.slice(d.index + d[0].length)
  }
  if (out.minutes !== null && (out.minutes < 5 || out.minutes > 480)) out.minutes = null

  const evening = /저녁|밤/.test(text)
  const r = TIME_RANGE.exec(rest)
  if (r) {
    const s = toTime(r[1], r[2], r[3], r[4], r[5], null, evening)
    const e = s === null ? null : toTime(r[6] ?? r[1], r[7], r[8], r[9], r[10], s, evening)
    out.startTime = fmt(s)
    out.endTime = s !== null && e !== null && e > s ? fmt(e) : null
  } else {
    const one = TIME_ONE.exec(rest)
    // 숫자만 있는 건 시각이 아니에요 ("3곳") — '시'나 ':' 가 있어야 해요
    if (one && (one[3] !== undefined || one[0].includes('시'))) {
      out.startTime = fmt(toTime(one[1], one[2], one[3], one[4], one[5], null, evening))
    }
  }
  return out
}
