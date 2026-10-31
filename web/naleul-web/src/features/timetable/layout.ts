/**
 * TimeTable 격자에 블록을 놓기 위한 순수 계산 (화면과 분리해서 테스트하기 쉽게).
 */
import { minutesFrom, toDateTime } from './time'
import type { ActualActivity, FixedBlock, TimeBlockTask, TimetableDay } from './types'

export const DEFAULT_START_HOUR = 7
export const DEFAULT_END_HOUR = 24
/** 취침이 자정을 넘기면(예: 01:00) 격자를 다음 날 새벽까지 늘려요. 최대 다음 날 06:00 */
export const MAX_END_HOUR = 30
const MIN_TASK_MINUTES = 15 // 너무 짧은 블록도 글자가 보이도록

export interface ShownTime {
  startAt: string
  endAt: string | null
  /** true 면 실제로 한 시각, false 면 계획 시각 */
  actual: boolean
}

/**
 * 블록을 그릴 시각 (백엔드 TimetableQueryService.blockRange 와 같은 규칙)
 *  - 완료 전: 계획 시각 (plannedStartAt ~ plannedEndAt)
 *  - 완료 후: 실제 시각이 기록돼 있으면 실제로 한 시각, 없으면 계획 시각 ("계획대로 했다"로 봐요)
 * 시간이 없으면 null (시간 미정)
 */
export function shownTime(t: TimeBlockTask): ShownTime | null {
  if (t.taskStatus === 'COMPLETED' && t.actualStartAt && t.actualEndAt) {
    return { startAt: t.actualStartAt, endAt: t.actualEndAt, actual: true }
  }
  if (!t.plannedStartAt) return null
  return { startAt: t.plannedStartAt, endAt: t.plannedEndAt ?? null, actual: false }
}

/** 계획 시각 기준 구간 (완료했어도 원래 계획한 자리) */
function plannedMinutes(dayYmd: string, t: TimeBlockTask) {
  if (!t.plannedStartAt) return null
  const start = minutesFrom(dayYmd, t.plannedStartAt)
  const end = t.plannedEndAt ? minutesFrom(dayYmd, t.plannedEndAt) : start + (t.plannedDurationMinutes ?? 30)
  return { start, end }
}

/** 그날(dayYmd) 0시 기준 분으로 바꾼 블록 구간. 끝이 없으면 소요 시간(없으면 30분)으로 */
function shownMinutes(dayYmd: string, t: TimeBlockTask, fallbackMinutes = t.plannedDurationMinutes ?? 30) {
  const st = shownTime(t)
  if (!st) return null
  const start = minutesFrom(dayYmd, st.startAt)
  const end = st.endAt ? minutesFrom(dayYmd, st.endAt) : start + fallbackMinutes
  return { start, end }
}

/** 기상 전 · 취침 후로 더 보여줄 시간 — 기상·취침을 끌어서 바꿀 수 있게 (분) */
export const SLEEP_MARGIN_MINUTES = 120

/**
 * 격자에 보여줄 시간 범위 (시 단위).
 * 기본은 하루 범위(기상 ~ 취침) + 앞뒤 2시간이고, 그 밖에 Task 가 있으면 넓혀요.
 * 수면 패턴이 없으면 백엔드가 00:00~24:00 을 주므로 그대로 0~24시.
 */
export function visibleHours(days: TimetableDay[], activities: ActualActivity[] = []) {
  if (!days.length) return { startHour: DEFAULT_START_HOUR, endHour: DEFAULT_END_HOUR }
  let start = Infinity
  let end = -Infinity
  for (const d of days) {
    // 수면 블록이 있으면 기상 2시간 전부터 취침 2시간 뒤까지 (수면 블록 일부가 보여서 가장자리를 끌 수 있어요)
    start = Math.min(start, minutesFrom(d.date, d.dayRange.start) - (d.sleep?.wake ? SLEEP_MARGIN_MINUTES : 0))
    end = Math.max(end, minutesFrom(d.date, d.dayRange.end) + (d.sleep?.bed ? SLEEP_MARGIN_MINUTES : 0))
    for (const t of d.tasks) {
      const m = shownMinutes(d.date, t, 30)
      if (!m) continue
      start = Math.min(start, m.start)
      end = Math.max(end, m.end)
    }
    // 실제로 한 일도 보이게 (그날 범위 안 조각만)
    for (const a of activities) {
      const as = minutesFrom(d.date, a.startAt)
      const ae = minutesFrom(d.date, a.endAt)
      if (ae <= 0 || as >= MAX_END_HOUR * 60) continue
      start = Math.min(start, Math.max(as, 0))
      end = Math.max(end, Math.min(ae, MAX_END_HOUR * 60))
    }
  }
  const startHour = Math.max(0, Math.min(DEFAULT_END_HOUR - 1, Math.floor(start / 60)))
  // 끝은 최소 24시 — 취침 직전·늦은 밤으로도 끌어다 놓을 수 있게.
  // 취침이 자정 뒤(예: 01:00)면 그 시각까지 (그날 하루는 다음 날 01:00 까지라서)
  const endHour = Math.min(MAX_END_HOUR, Math.max(startHour + 1, Math.ceil(end / 60), DEFAULT_END_HOUR))
  return { startHour, endHour }
}

export interface Placed<T> {
  item: T
  /** 그날 0시 기준 분 (화면 범위로 잘림) */
  top: number
  bottom: number
  /** 잘리기 전 원래 시각(분) — 글자로 보여줄 때 */
  start: number
  end: number
  /** 한 Task 를 여러 구간으로 나눠 그릴 때 몇 번째 조각인지 (나눠서 한 실제 시간) */
  part?: number
  /** 원래 시간이 화면 범위 밖까지 이어지는지 */
  clippedTop: boolean
  clippedBottom: boolean
  /** 겹칠 때 나란히 놓기 위한 열 정보 */
  col: number
  cols: number
}

function clip(start: number, end: number, from: number, to: number) {
  const top = Math.max(start, from)
  const bottom = Math.min(end, to)
  if (bottom <= top) return null
  return { top, bottom, start, end, clippedTop: start < from, clippedBottom: end > to }
}

export function placeFixed(day: TimetableDay, from: number, to: number): Placed<FixedBlock>[] {
  const out: Placed<FixedBlock>[] = []
  // 기상 · 취침 수면은 placeSleep 이 자르지 않은 원본으로 따로 그려요 (기상·취침 가장자리를 끌 수 있게).
  // 그 둘이 아닌 수면(예전 서버가 늦잠 기상을 못 알아본 경우 등)은 여기서 그대로 그려서 사라지지 않게 해요
  const sameSleep = (a: FixedBlock, b: FixedBlock | null | undefined) =>
    !!b && a.lifePatternId === b.lifePatternId && a.targetDate === b.targetDate
  for (const b of day.fixedBlocks) {
    if (b.patternType === 'SLEEP' && (sameSleep(b, day.sleep?.wake) || sameSleep(b, day.sleep?.bed))) continue
    const c = clip(minutesFrom(day.date, b.start), minutesFrom(day.date, b.end), from, to)
    if (c) out.push({ item: b, ...c, col: 0, cols: 1 })
  }
  return out
}

export interface PlacedSleep extends Placed<FixedBlock> {
  /** wake = 아침에 끝나는 수면 (아래 가장자리 = 기상), bed = 밤에 시작하는 수면 (위 가장자리 = 취침) */
  edge: 'wake' | 'bed'
}

/** 그날 칸에 그릴 수면 블록 (기상 전 · 취침 후). start/end 는 자르기 전 원래 시각(그날 0시 기준 분, 음수·1440 이상 가능) */
export function placeSleep(day: TimetableDay, from: number, to: number): PlacedSleep[] {
  const out: PlacedSleep[] = []
  for (const edge of ['wake', 'bed'] as const) {
    const b = day.sleep?.[edge]
    if (!b) continue
    const c = clip(minutesFrom(day.date, b.start), minutesFrom(day.date, b.end), from, to)
    if (c) out.push({ item: b, ...c, col: 0, cols: 1, edge })
  }
  return out
}

/**
 * Task 블록 배치. 백엔드 규칙상 Task 끼리는 겹치지 않지만,
 * 예전 데이터(iOS 에서 만든 겹친 일정)가 있을 수 있어 겹치면 나란히 그려요.
 */
/**
 * @param basis shown   = 화면 기본 (완료 전엔 계획 시각, 완료 후엔 실제 시각)
 *              planned = 모두 계획 시각 (일간 "계획" 칸 — 완료한 일도 원래 계획한 자리에)
 *              done    = 완료한 Task 만, 실제 시각(없으면 계획 시각) (일간 "실제" 칸)
 */
export function placeTasks(
  day: TimetableDay,
  from: number,
  to: number,
  basis: 'shown' | 'planned' | 'done' = 'shown'
): Placed<TimeBlockTask>[] {
  const items = day.tasks
    .filter((t) => basis !== 'done' || t.taskStatus === 'COMPLETED')
    .flatMap((t) => {
      // 여러 번 나눠서 한 완료 Task 는 실제로 한 구간마다 블록 하나씩 (계획 기준일 땐 계획 자리 하나)
      const actualSegs = basis !== 'planned' && t.taskStatus === 'COMPLETED' ? (t.actualSegments ?? []) : []
      // 자동 배치가 점심 앞뒤로 나눠 넣은 Task 는 계획 구간마다 블록 하나씩 (실제 시각으로 그릴 땐 제외)
      const drawnAtActual = basis !== 'planned' && t.taskStatus === 'COMPLETED' && !!t.actualStartAt
      const segs = actualSegs.length > 1 ? actualSegs : drawnAtActual ? [] : (t.plannedSegments ?? [])
      const ranges =
        segs.length > 1
          ? segs.map((g) => ({ start: minutesFrom(day.date, g.start), end: minutesFrom(day.date, g.end) }))
          : [basis === 'planned' ? plannedMinutes(day.date, t) : shownMinutes(day.date, t)]
      return ranges.map((m, part) => {
        if (!m) return { t, part, c: null }
        const c = clip(m.start, Math.max(m.end, m.start + MIN_TASK_MINUTES), from, to)
        return { t, part, c: c && { ...c, end: m.end } }
      })
    })
    .filter((x): x is { t: TimeBlockTask; part: number; c: NonNullable<ReturnType<typeof clip>> } => !!x.c)

  return layoutColumns(items.map(({ t, part, c }) => ({ item: t, part, ...c, col: 0, cols: 1 })))
}

/**
 * 겹치는 블록을 나란히 놓도록 열(col / cols)을 정해요 (넘겨준 객체를 고쳐서 위→아래 순으로 돌려줘요).
 * 서로 다른 종류(Task · 실제로 한 일)를 섞어 넘기면 한 칸 안에서 같이 나눠 그려요.
 */
export function layoutColumns<P extends { top: number; bottom: number; col: number; cols: number }>(list: P[]): P[] {
  const items = [...list].sort((a, b) => a.top - b.top || b.bottom - a.bottom)
  const out: P[] = []
  // 겹치는 덩어리(cluster)마다 열을 나눠요
  let cluster: P[] = []
  let clusterEnd = -Infinity
  const colEnds: number[] = []
  const flush = () => {
    const cols = Math.max(1, colEnds.length)
    cluster.forEach((p) => (p.cols = cols))
    out.push(...cluster)
    cluster = []
    colEnds.length = 0
  }
  for (const p of items) {
    if (p.top >= clusterEnd) flush()
    let col = colEnds.findIndex((end) => end <= p.top)
    if (col === -1) {
      col = colEnds.length
      colEnds.push(p.bottom)
    } else colEnds[col] = p.bottom
    p.col = col
    p.cols = 1
    cluster.push(p)
    clusterEnd = Math.max(clusterEnd, p.bottom)
  }
  flush()
  return out
}

/** "#3D5AFE" / "3D5AFE" / null → "#3D5AFE" (없으면 회색) */
export const hexOf = (code?: string | null) => (code ? (code.startsWith('#') ? code : `#${code}`) : '#8B9099')

/** "#3D5AFE" + 0.15 → "rgba(61, 90, 254, 0.15)" */
export function withAlpha(hex: string, alpha: number) {
  const h = hex.replace('#', '')
  const full =
    h.length === 3
      ? h
          .split('')
          .map((c) => c + c)
          .join('')
      : h.slice(0, 6)
  const n = parseInt(full, 16)
  if (Number.isNaN(n)) return `rgba(139, 144, 153, ${alpha})`
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

// ─── 드래그 결과를 서버 응답 전에 먼저 보여주기 (낙관적 표시) ─────────────────

export type Pending =
  /** Task 를 date 의 start~end 로 옮김. date 가 null 이면 "시간 미정"으로 보냄 (toDate 가 있으면 그날 시간 미정) */
  | { kind: 'task'; taskId: number; date: string | null; start: number; end: number; toDate?: string }
  /** 고정 블록(fixedKey)을 그날 start~end 로 바꿈 */
  | { kind: 'fixed'; key: string; date: string; start: number; end: number }

/**
 * 서버 응답이 오기 전까지 옮긴 위치에 블록을 그려요.
 * 응답이 오면(성공·실패 모두) TimeTable 을 다시 불러오고 pending 을 지워요.
 */
export function applyPending(
  days: TimetableDay[],
  pending: Pending[],
  keyOf: (b: FixedBlock) => string
): TimetableDay[] {
  if (!pending.length) return days
  const taskMoves = new Map<number, Extract<Pending, { kind: 'task' }>>()
  const fixedMoves = new Map<string, Extract<Pending, { kind: 'fixed' }>>()
  for (const p of pending) {
    if (p.kind === 'task') taskMoves.set(p.taskId, p)
    else fixedMoves.set(p.key, p)
  }

  const all = days.flatMap((d) => [...d.tasks, ...d.unscheduledTasks])
  return days.map((d) => {
    const keep = (t: TimeBlockTask) => !taskMoves.has(t.taskId)
    const tasks = d.tasks.filter(keep)
    const unscheduledTasks = d.unscheduledTasks.filter(keep)
    for (const [id, p] of taskMoves) {
      const t = all.find((x) => x.taskId === id)
      if (!t) continue
      if (p.date === d.date) {
        tasks.push({
          ...t,
          date: d.date,
          plannedStartAt: toDateTime(d.date, p.start),
          plannedEndAt: toDateTime(d.date, p.end),
          plannedSegments: undefined, // 옮기면 한 덩어리 (서버 응답 전까지 옛 조각 자리에 그리지 않게)
          locked: true,
          placedBy: 'USER',
          missed: false,
        })
      } else if (p.date === null && (p.toDate ?? t.date ?? t.plannedStartAt?.slice(0, 10)) === d.date) {
        unscheduledTasks.push({ ...t, plannedStartAt: null, plannedEndAt: null, plannedSegments: undefined, locked: false })
      }
    }
    const fixedBlocks = d.fixedBlocks.map((b) => {
      const p = fixedMoves.get(keyOf(b))
      return p && p.date === d.date
        ? { ...b, start: toDateTime(d.date, p.start), end: toDateTime(d.date, p.end), overridden: true }
        : b
    })
    // 수면 블록은 이틀(전날 밤 → 이날 아침) 칸에 걸쳐 보여서, 끌어놓은 칸의 날짜 기준으로 바꾼 시각을 양쪽에 반영해요
    const moveSleep = (b: FixedBlock | null) => {
      const p = b && fixedMoves.get(keyOf(b))
      return b && p ? { ...b, start: toDateTime(p.date, p.start), end: toDateTime(p.date, p.end), overridden: true } : b
    }
    const sleep = d.sleep ? { wake: moveSleep(d.sleep.wake), bed: moveSleep(d.sleep.bed) } : d.sleep
    return { ...d, tasks, unscheduledTasks, fixedBlocks, sleep }
  })
}

/** "AI로 배치하기" 미리보기 제안 하나 (화면 상태) */
export interface Proposal {
  taskId: number
  reason?: string | null
  /** AI 가 처음 제안한 자리 — 확정할 때 "고쳤는지" 비교용 */
  proposedStart: string
  proposedEnd: string
  /** 지금 자리 (끌어서 고치면 바뀜) */
  start: string
  end: string
}

/** 제안을 점선 블록으로 덧입혀요: 시간 미정 칸에서 빼서 제안 자리에 그려요 (저장 전) */
export function applyProposals(days: TimetableDay[], proposals: Proposal[] | null): TimetableDay[] {
  if (!proposals?.length) return days
  const byId = new Map(proposals.map((p) => [p.taskId, p]))
  const all = days.flatMap((d) => [...d.tasks, ...d.unscheduledTasks])
  return days.map((d) => {
    const tasks = d.tasks.filter((t) => !byId.has(t.taskId))
    const unscheduledTasks = d.unscheduledTasks.filter((t) => !byId.has(t.taskId))
    for (const p of proposals) {
      if (p.start.slice(0, 10) !== d.date) continue
      const t = all.find((x) => x.taskId === p.taskId)
      if (!t) continue
      tasks.push({
        ...t,
        date: d.date,
        plannedStartAt: p.start,
        plannedEndAt: p.end,
        plannedSegments: undefined,
        placedBy: 'ENGINE',
        locked: false,
        missed: false,
        placementReason: p.reason ?? t.placementReason,
        proposed: true,
      })
    }
    return { ...d, tasks, unscheduledTasks }
  })
}

/** 자동 배치가 밀어낼 수 없는 블록 — 백엔드 isMovableEngineBlock 의 반대 (건너뛴 Task 는 자리를 차지하지 않아요) */
export const isBlockingTask = (t: TimeBlockTask) =>
  t.taskStatus !== 'SKIPPED' &&
  (t.locked || t.placedBy !== 'ENGINE' || t.taskStatus !== 'TODO' || t.sourceType === 'MISSION')

/**
 * 한 시각에 같이 둘 수 있는 (밀어낼 수 없는) Task 수 — 백엔드 Concurrency.MAX_TASKS_AT_ONCE 와 같아야 해요.
 * 새 자리에 먼저 놓고 원래 있던 Task 를 나중에 옮기는 식으로 정리할 수 있게 2개까지 허용해요.
 */
export const MAX_TASKS_AT_ONCE = 2

export interface MinuteRange {
  start: number
  end: number
}

/** window 안에서 ranges 가 가장 많이 겹치는 순간의 개수 (끝과 시작이 같으면 겹치지 않음) */
export function maxOverlap(ranges: MinuteRange[], window: MinuteRange) {
  const events: [number, number][] = []
  for (const r of ranges) {
    if (!(r.start < window.end && r.end > window.start)) continue
    events.push([Math.max(r.start, window.start), 1], [Math.min(r.end, window.end), -1])
  }
  // 같은 시각이면 끝(-1)을 먼저 → 10~11 과 11~12 는 겹치지 않음
  events.sort((a, b) => a[0] - b[0] || a[1] - b[1])
  let cur = 0
  let max = 0
  for (const [, d] of events) {
    cur += d
    max = Math.max(max, cur)
  }
  return max
}

/**
 * start~end 에 놓으면 한 시각에 밀어낼 수 없는 Task 가 3개 이상이 되는지.
 * 완료한 Task 는 "실제로 한 시간"이 자리를 차지해요 (화면에 그려진 자리 = 막힌 자리. 계획 자리는 비어 있어요).
 *
 * @param exclude 같이 옮기는 Task 들 (원래 자리는 빼고 봐요)
 * @param extra   같이 옮기는 다른 Task 들의 새 자리
 * @returns 놓을 수 없으면 그 자리에 이미 있는 Task 들, 놓을 수 있으면 null
 */
export function crowdedTasks(
  day: TimetableDay,
  start: number,
  end: number,
  exclude: ReadonlySet<number>,
  extra: MinuteRange[] = []
): TimeBlockTask[] | null {
  const hits: { task: TimeBlockTask; range: MinuteRange }[] = []
  for (const t of day.tasks) {
    if (exclude.has(t.taskId) || !isBlockingTask(t)) continue
    const range = shownMinutes(day.date, t, 30)
    if (range && range.start < end && range.end > start) hits.push({ task: t, range })
  }
  const ranges = [...hits.map((h) => h.range), ...extra]
  if (maxOverlap(ranges, { start, end }) < MAX_TASKS_AT_ONCE) return null
  return hits.map((h) => h.task)
}

/**
 * 그날 칸에 그릴 "실제로 한 일" 블록.
 * 기록끼리는 겹치지 않아서(서버가 막음) 열 나누기 없이 위치만 계산해요.
 * 자정을 넘긴 기록은 날짜마다 잘린 조각으로 그려요.
 */
export function placeActivities(dayYmd: string, activities: ActualActivity[], from: number, to: number) {
  const out: Placed<ActualActivity>[] = []
  for (const a of activities) {
    const start = minutesFrom(dayYmd, a.startAt)
    const end = minutesFrom(dayYmd, a.endAt)
    if (end <= 0 || start >= to) continue // 이 날 칸과 안 겹침
    const top = Math.max(start, 0, from)
    const bottom = Math.min(Math.max(end, start + MIN_TASK_MINUTES), to)
    if (bottom <= top) continue
    out.push({
      item: a,
      top,
      bottom,
      start,
      end,
      clippedTop: start < top,
      clippedBottom: end > bottom,
      col: 0,
      cols: 1,
    })
  }
  return out
}
