import { minutesFrom } from '@/features/timetable/time'
import type { FixedBlock, TimeBlockTask, TimetableDay } from '@/features/timetable/types'

/**
 * 그날 "하기로 한" Task — 계획 시작일 또는 시간 미정 날짜가 그날이거나, 그날 끝낸 것.
 * (전날 밤부터 이어진 블록은 TimeTable 에선 이날 칸에도 보이지만 여기선 빼요 · DayTaskList 와 같은 규칙)
 */
export function tasksOfDay(day: TimetableDay | undefined): TimeBlockTask[] {
  if (!day) return []
  const seen = new Set<number>()
  return [...day.tasks, ...day.unscheduledTasks].filter((t) => {
    if (seen.has(t.taskId) || t.taskStatus === 'SKIPPED' || t.taskStatus === 'DELETED') return false
    seen.add(t.taskId)
    const planDay = t.plannedStartAt?.slice(0, 10) ?? t.date ?? t.scheduledDate
    return !planDay || planDay === day.date || t.completedAt?.slice(0, 10) === day.date
  })
}

export const isRoutineTask = (t: TimeBlockTask) => t.sourceType === 'ROUTINE'
export const isDone = (t: TimeBlockTask) => t.taskStatus === 'COMPLETED'

/** 그날 0시부터 시작 분 (시간 미정은 맨 뒤) */
export const startMinutes = (day: string, t: TimeBlockTask) =>
  t.plannedStartAt ? minutesFrom(day, t.plannedStartAt) : Infinity

export function endMinutes(day: string, t: TimeBlockTask) {
  if (!t.plannedStartAt) return Infinity
  if (t.plannedEndAt) return minutesFrom(day, t.plannedEndAt)
  return minutesFrom(day, t.plannedStartAt) + (t.plannedDurationMinutes ?? 30)
}

/** 일정표 한 줄 — Task 또는 고정 시간(점심 · 저녁 · 직접 만든 고정 시간) */
export type AgendaItem =
  | { kind: 'task'; key: string; start: number; end: number; task: TimeBlockTask }
  | { kind: 'fixed'; key: string; start: number; end: number; block: FixedBlock }

/** 오늘 일정표 — 시간이 정해진 Task + 고정 시간 (수면은 기상 · 취침으로 따로) */
export function agendaOf(day: TimetableDay | undefined): AgendaItem[] {
  if (!day) return []
  const items: AgendaItem[] = []
  for (const t of tasksOfDay(day)) {
    if (!t.plannedStartAt || t.plannedStartAt.slice(0, 10) !== day.date) continue
    items.push({
      kind: 'task',
      key: `t${t.taskId}`,
      start: startMinutes(day.date, t),
      end: endMinutes(day.date, t),
      task: t,
    })
  }
  for (const b of day.fixedBlocks) {
    if (b.patternType === 'SLEEP' || b.clippedFromPreviousDay) continue
    // 그 시간을 Task 가 통째로 덮었으면 (예: 점심 약속) 고정 시간은 숨겨요
    if (b.coveredBy?.length) continue
    items.push({
      kind: 'fixed',
      key: `f${b.lifePatternId}-${b.start}`,
      start: minutesFrom(day.date, b.start),
      end: minutesFrom(day.date, b.end),
      block: b,
    })
  }
  return items.sort((a, b) => a.start - b.start || (a.kind === 'fixed' ? 1 : -1))
}

/** "40분 뒤" · "1시간 20분 뒤" */
export function inLabel(minutes: number) {
  if (minutes <= 0) return '지금'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h && m) return `${h}시간 ${m}분 뒤`
  if (h) return `${h}시간 뒤`
  return `${m}분 뒤`
}
