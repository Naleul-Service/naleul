import { DAY_LABEL } from '../../constants'
import type { DayOfWeek, GoalPlan, PlanIssue, PlanTask } from '../../types'

// ─── 날짜 ───────────────────────────────────────────────────────

/** "2026-10-02" → 로컬 Date (new Date("2026-10-02") 는 UTC 로 해석돼 하루 밀릴 수 있어요) */
export function parseYmd(value: string): Date {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function toYmd(date: Date): string {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']

/** "2026-10-02" → "10.02 (금)" */
export function formatMd(value: string | null, withWeekday = true): string {
  if (!value) return ''
  const date = parseYmd(value)
  const md = `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
  return withWeekday ? `${md} (${WEEKDAY[date.getDay()]})` : md
}

/** "2026-10-02" → "2026.10.02" */
export const formatYmdDot = (value: string) => value.replaceAll('-', '.')

export function daysBetween(from: string, to: string): number {
  return Math.round((parseYmd(to).getTime() - parseYmd(from).getTime()) / 86_400_000)
}

/** 오늘 기준 D-day 숫자 (남은 일수) */
export function dDay(to: string): number {
  return daysBetween(toYmd(new Date()), to)
}

export function formatDays(days: DayOfWeek[] | null): string {
  if (!days?.length) return ''
  if (days.length === 7) return '매일'
  return days.map((d) => DAY_LABEL[d]).join('·')
}

// ─── 편집 헬퍼 ──────────────────────────────────────────────────

let seq = 0
/** 새로 추가하는 항목의 tempId (중복만 안 되면 돼요) */
export const newTempId = (prefix: 'sg' | 'ms' | 't') => `${prefix}-new-${Date.now().toString(36)}-${++seq}`

export const isRoutine = (t: PlanTask) => t.type === 'ROUTINE'

/** 예전 목표를 AI 로 다시 설계할 때 서버가 넣어 준 "지금 하고 있는 루틴" (확정하면 새로 만들지 않고 이어서 써요) */
export const isExistingTask = (t: PlanTask) => t.tempId.startsWith('existing-routine-')

/** 루틴 주간 총 시간(분) */
export const weeklyRoutineMinutes = (tasks: PlanTask[]) =>
  tasks.filter(isRoutine).reduce((sum, t) => sum + t.durationMinutes * (t.routineDays?.length ?? 0), 0)

// ─── 자동 보정 ───────────────────────────────────────────────────

const addDaysYmd = (ymd: string, n: number) => {
  const d = parseYmd(ymd)
  d.setDate(d.getDate() + n)
  return toYmd(d)
}

/** 시작값·목표값 자리수에 맞춰 반올림 (80 → 72 면 정수, 80.5 → 72 면 소수 첫째 자리) */
function roundLike(value: number, ...refs: number[]) {
  const decimals = Math.max(...refs.map((r) => (String(r).split('.')[1] ?? '').length), 0)
  const f = 10 ** Math.min(Math.max(decimals, 0), 2)
  return Math.round(value * f) / f
}

/**
 * 사용자가 무엇을 고치든 계획이 "깨지지 않게" 자동으로 맞춰요.
 * 예전에는 마일스톤 날짜·수치를 사람이 직접 맞춰야 해서(마지막 = 종료일, 우상향/우하향 유지, 할 일 마감 ≤ 마일스톤)
 * 하나를 고치면 다른 곳에 빨간 오류가 생겼어요. 이제 사용자는 "시작값·목표값·종료일"만 고치면 돼요.
 *  - 마일스톤: 날짜순 정렬 · 목표 기간 안으로 · 겹치는 날짜는 하루씩 밀기 · 마지막은 종료일
 *  - 마일스톤 수치: 시작값 → 목표값 직선 위의 값 (날짜 비율대로) — 그래프가 항상 한 방향
 *  - 일회성 할 일: 마감이 연결된 마일스톤보다 늦으면 마일스톤 날짜로, 실행일이 마감보다 늦으면 마감으로
 *  - 시작일을 미뤘을 때: 루틴 시작일이 그보다 앞이면 비워서(= 목표 시작일부터) 시작일 전에 루틴이 생기지 않게,
 *    일회성 할 일 실행일·마감도 시작일 이후로
 */
export function normalizePlan(plan: GoalPlan): GoalPlan {
  const { goal } = plan
  const start = goal.startDate
  const end = goal.endDate
  if (!start || !end || end <= start) return plan

  // 1) 날짜
  const sorted = [...plan.milestones].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  const milestones = sorted.map((m) => ({ ...m }))
  milestones.forEach((m, i) => {
    let due = m.dueDate < start ? addDaysYmd(start, 1) : m.dueDate > end ? end : m.dueDate
    const prev = milestones[i - 1]
    if (prev && due <= prev.dueDate) due = addDaysYmd(prev.dueDate, 1)
    m.dueDate = due > end ? end : due
  })
  const last = milestones[milestones.length - 1]
  if (last) last.dueDate = end

  // 2) 수치 — 직선 보간
  const metric = goal.metric
  const total = Math.max(daysBetween(start, end), 1)
  milestones.forEach((m) => {
    if (!metric) {
      m.targetValue = null
      return
    }
    const ratio = Math.min(Math.max(daysBetween(start, m.dueDate) / total, 0), 1)
    const raw = metric.startValue + (metric.targetValue - metric.startValue) * ratio
    m.targetValue = roundLike(raw, metric.startValue, metric.targetValue)
  })
  if (last && metric) last.targetValue = metric.targetValue

  // 3) 일회성 할 일 날짜
  const dueOf = new Map(milestones.map((m) => [m.tempId, m.dueDate]))
  const tasks = plan.tasks.map((t) => {
    if (isRoutine(t)) {
      const routineStart = t.startDate && t.startDate < start ? null : t.startDate
      const routineEnd = t.endDate && (t.endDate > end || t.endDate < start) ? null : t.endDate
      return routineStart === t.startDate && routineEnd === t.endDate
        ? t
        : { ...t, startDate: routineStart, endDate: routineEnd }
    }
    const msDue = t.milestoneTempId ? dueOf.get(t.milestoneTempId) : undefined
    let due = t.dueDate
    if (due && msDue && due > msDue) due = msDue
    if (due && due > end) due = end
    if (due && due < start) due = msDue ?? start
    let scheduled = t.scheduledDate && due && t.scheduledDate > due ? due : t.scheduledDate
    if (scheduled && scheduled < start) scheduled = null
    return due === t.dueDate && scheduled === t.scheduledDate ? t : { ...t, dueDate: due, scheduledDate: scheduled }
  })

  const same =
    milestones.every((m, i) => {
      const o = plan.milestones[i]
      return o && o.tempId === m.tempId && o.dueDate === m.dueDate && o.targetValue === m.targetValue
    }) && tasks.every((t, i) => t === plan.tasks[i])
  return same ? plan : { ...plan, milestones, tasks }
}

// ─── 검증 ───────────────────────────────────────────────────────
// 서버 HARD 규칙 중 프론트에서 바로 확인 가능한 것들 (인수인계 3-4).
// path 형식을 서버 violations 와 같게 맞춰서, 화면 표시 로직을 하나로 써요.

/**
 * 사용자가 초안을 고칠 때의 한도 = 서버 GoalPlanValidator.USER_LIMITS.
 * AI 는 더 엄격하게(루틴 5~30분, 일회성 0~5개) 만들지만, 사람이 직접 늘리거나 길게 잡는 건 막지 않아요.
 * 주간 루틴 합계는 막지 않고 서버가 경고만 해요.
 */
export const LIMITS = {
  title: 40,
  subGoals: [1, 5],
  // V52: AI 초안은 마일스톤 없이 와요 (서버 USER_LIMITS 0~8)
  milestones: [0, 8],
  routines: [0, 10],
  oneTimes: [0, 30],
  routineMinutes: [1, 1440],
  oneTimeMinutes: [1, 1440],
  routineDescription: 300,
} as const

export function validatePlan(plan: GoalPlan): PlanIssue[] {
  const issues: PlanIssue[] = []
  const add = (path: string, message: string) => issues.push({ ruleId: 'CLIENT', path, message })
  const titleOk = (t: string) => t.trim().length >= 1 && t.trim().length <= LIMITS.title

  if (!titleOk(plan.goal.title)) add('goal.title', `목표 이름은 1~${LIMITS.title}자로 적어 주세요.`)

  const [sgMin, sgMax] = LIMITS.subGoals
  if (plan.subGoals.length < sgMin || plan.subGoals.length > sgMax)
    add('subGoals', `세부 목표는 ${sgMin}~${sgMax}개여야 해요.`)
  plan.subGoals.forEach((sg, i) => {
    if (!titleOk(sg.title)) add(`subGoals[${i}].title`, `제목은 1~${LIMITS.title}자로 적어 주세요.`)
  })

  const [msMin, msMax] = LIMITS.milestones
  if (plan.milestones.length < msMin || plan.milestones.length > msMax)
    add('milestones', `마일스톤은 ${msMin}~${msMax}개여야 해요.`)
  plan.milestones.forEach((ms, i) => {
    if (!titleOk(ms.title)) add(`milestones[${i}].title`, `제목은 1~${LIMITS.title}자로 적어 주세요.`)
    const prev = plan.milestones[i - 1]
    if (prev && ms.dueDate <= prev.dueDate) add(`milestones[${i}].dueDate`, '마일스톤 날짜는 앞 단계보다 뒤여야 해요.')
  })
  const lastMs = plan.milestones[plan.milestones.length - 1]
  if (lastMs && lastMs.dueDate !== plan.goal.endDate)
    add(
      `milestones[${plan.milestones.length - 1}].dueDate`,
      `마지막 마일스톤은 목표 종료일(${formatYmdDot(plan.goal.endDate)})이어야 해요.`
    )

  const routines = plan.tasks.filter(isRoutine)
  const [rMin, rMax] = LIMITS.routines
  if (plan.tasks.length === 0) add('tasks', '할 일을 하나 이상 남겨 주세요.')
  if (routines.length < rMin || routines.length > rMax) add('tasks', `루틴은 ${rMax}개까지 만들 수 있어요.`)
  // 일회성 Task 개수는 검사하지 않아요 — AI 목표는 루틴 중심이라 0개가 기본이에요 (추가 버튼만 30개에서 막혀요)

  plan.tasks.forEach((t, i) => {
    const p = `tasks[${i}]`
    if (!titleOk(t.title)) add(`${p}.title`, `제목은 1~${LIMITS.title}자로 적어 주세요.`)
    if (isRoutine(t)) {
      const [min, max] = LIMITS.routineMinutes
      if (t.durationMinutes < min || t.durationMinutes > max)
        add(`${p}.durationMinutes`, `루틴은 ${min}~${max}분으로 설정해 주세요.`)
      if (!t.routineDays?.length) add(`${p}.routineDays`, '요일을 하나 이상 골라 주세요.')
    } else {
      const [min, max] = LIMITS.oneTimeMinutes
      if (t.durationMinutes < min || t.durationMinutes > max)
        add(`${p}.durationMinutes`, `할 일은 ${min}~${max}분으로 설정해 주세요.`)
      if (!t.dueDate) add(`${p}.dueDate`, '마감일을 정해 주세요.')
      if (t.scheduledDate && t.dueDate && t.scheduledDate > t.dueDate)
        add(`${p}.scheduledDate`, '실행일은 마감일보다 늦을 수 없어요.')
      const ms = plan.milestones.find((m) => m.tempId === t.milestoneTempId)
      if (ms && t.dueDate && t.dueDate > ms.dueDate)
        add(`${p}.dueDate`, `마감일은 연결된 마일스톤(${formatMd(ms.dueDate, false)})보다 늦을 수 없어요.`)
    }
  })

  return issues
}

/**
 * violation path → 화면 항목 키
 *  "tasks[1].durationMinutes" → "tasks[1]"
 *  "goal.title"               → "goal"
 *  "milestones"               → "milestones" (섹션 전체)
 */
export function itemKeyOf(path: string): string {
  const m = path.match(/^([a-zA-Z]+)(\[\d+\])?/)
  return m ? m[0] : path
}

/** 항목 키별 메시지 묶기 */
export function groupIssues(issues: PlanIssue[]): Map<string, string[]> {
  const map = new Map<string, string[]>()
  for (const issue of issues) {
    const key = itemKeyOf(issue.path)
    const list = map.get(key) ?? []
    if (!list.includes(issue.message)) list.push(issue.message)
    map.set(key, list)
  }
  return map
}
