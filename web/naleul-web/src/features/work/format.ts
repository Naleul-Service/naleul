import { addDays, weekdayIndex, WEEKDAY_LABEL } from '@/features/timetable/time'
import type { WorkColumn, WorkTask } from './api'

export const COLUMN_LABEL: Record<WorkColumn, string> = {
  NOT_STARTED: '시작 전',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
}

/** "오늘" · "내일" · "어제" · "10.14 (화)" (해가 다르면 "2027.01.03 (일)") */
export function dayLabel(ymd: string, today: string) {
  if (ymd === today) return '오늘'
  if (ymd === addDays(today, 1)) return '내일'
  if (ymd === addDays(today, -1)) return '어제'
  const md = `${ymd.slice(5, 7)}.${ymd.slice(8, 10)}`
  const date = ymd.slice(0, 4) === today.slice(0, 4) ? md : `${ymd.slice(0, 4)}.${md}`
  return `${date} (${WEEKDAY_LABEL[weekdayIndex(ymd)]})`
}

/** 마감 표시 — "D-3" · "오늘 마감" · "2일 지남" */
export function dueLabel(t: Pick<WorkTask, 'dueInDays' | 'status'>) {
  if (t.dueInDays == null) return null
  if (t.status === 'DONE') return null
  if (t.dueInDays === 0) return '오늘 마감'
  if (t.dueInDays > 0) return `D-${t.dueInDays}`
  return `${-t.dueInDays}일 지남`
}

/** 목표 마감 D-day — "D-12" · "D-DAY" · "D+3" */
export function goalDDay(dDay: number | null) {
  if (dDay == null) return null
  if (dDay === 0) return 'D-DAY'
  return dDay > 0 ? `D-${dDay}` : `D+${-dDay}`
}

/** 오늘부터 며칠 전 날짜인지 (오늘 0) — 분석용 days_ago */
export const daysAgo = (ymd: string, today: string) =>
  Math.round((Date.parse(`${today.slice(0, 10)}T00:00:00Z`) - Date.parse(`${ymd.slice(0, 10)}T00:00:00Z`)) / 86_400_000)

/** "2026.11.14 (금)" */
export const fullDate = (ymd: string) => `${ymd.replaceAll('-', '.')} (${WEEKDAY_LABEL[weekdayIndex(ymd)]})`
