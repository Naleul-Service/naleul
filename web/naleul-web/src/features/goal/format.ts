import { goalAccent } from '@/lib/goalTone'
import type { GoalStatus, JavaDayOfWeek } from './api'

export const STATUS_LABEL: Record<GoalStatus, string> = {
  NOT_STARTED: '시작 전',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  DELETED: '삭제됨',
}
export const statusLabel = (s: GoalStatus) => STATUS_LABEL[s] ?? s

/** 아직 끝나지 않은 목표 (사이드바 · 진행 중 목록) */
export const isOngoing = (s: GoalStatus) => s === 'IN_PROGRESS' || s === 'NOT_STARTED'

export const JAVA_DAYS: JavaDayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
export const JAVA_DAY_LABEL: Record<JavaDayOfWeek, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
}

/** "19:30:00" → "19:30" */
export const hhmm = (t: string | null) => (t ? t.slice(0, 5) : null)

/** "#3D5AFE" / "3D5AFE" / null → 목표 팔레트 accent (CSS 값 · 다크 모드 따라감 — lib/goalTone) */
export const goalColor = (code: string | null | undefined) => goalAccent(code)

function parse(value: string) {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}
const today = () => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}
const DAY = 86_400_000

export const formatDot = (v: string | null) => (v ? v.replaceAll('-', '.') : '')

/**
 * 기간 진행 정보 (오늘 기준)
 * - total: 전체 일수, elapsed: 지난 일수(0~total), remain: 남은 일수(음수면 종료), ratio: 0~1
 */
export function periodProgress(start: string | null, end: string | null) {
  if (!start || !end) return null
  const s = parse(start).getTime()
  const e = parse(end).getTime()
  const t = today().getTime()
  const total = Math.max(Math.round((e - s) / DAY), 1)
  const elapsed = Math.min(Math.max(Math.round((t - s) / DAY), 0), total)
  return { total, elapsed, remain: Math.round((e - t) / DAY), ratio: elapsed / total, weeks: Math.ceil(total / 7) }
}

export const dDayLabel = (remain: number) => (remain > 0 ? `D-${remain}` : remain === 0 ? 'D-DAY' : '종료')
