'use client'

import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type RefObject } from 'react'
import { SNAP_MINUTES, snap } from '../time'
import type { FixedBlock, TimeBlockTask } from '../types'

/**
 * TimeTable 드래그 앤 드롭 (명세 3-4)
 *
 * 라이브러리(@dnd-kit) 대신 Pointer Events 로 직접 구현했어요.
 *  - 이 화면의 드래그는 "어느 칸에 놓았나"가 아니라 "몇 시 몇 분에 놓았나"를 계산해야 해서
 *    어차피 좌표 → 분 변환을 직접 해야 해요. 라이브러리를 써도 이 부분은 그대로 남아요.
 *  - Pointer Events 는 마우스·펜·터치를 한 가지 코드로 처리해요.
 *
 * 동작
 *  - 마우스: 4px 이상 움직이면 드래그 시작 (그보다 적으면 클릭 → 상세 팝오버)
 *  - 터치: 0.35초 길게 누르면 드래그 시작 (그 전에 움직이면 평소처럼 스크롤)
 *  - 10분 단위로 맞춰져요. ESC 로 취소. 위·아래 끝에 가까이 가면 자동 스크롤.
 *  - 크기 조절: 블록 아래 끝(끝 시각) · 위 끝(시작 시각) 둘 다 잡을 수 있어요.
 *  - 묶음: 여러 Task 를 묶어 두면(group) 하나를 끌 때 나머지도 같은 만큼 같이 움직여요.
 */

/** 묶음으로 같이 옮길 Task (끄는 Task 와 같은 날, 시간이 있는 것) */
export interface GroupMember {
  task: TimeBlockTask
  date: string
  start: number
  end: number
}

export type DragSource =
  | {
      kind: 'task'
      task: TimeBlockTask
      /** 원래 날짜. start/end 가 null 이면 "시간 미정" 칸에서 끌어온 것 */
      date: string
      start: number | null
      end: number | null
      /** 같이 옮길 다른 Task 들 (묶음 드래그). 비어 있으면 혼자 움직여요 */
      group?: GroupMember[]
    }
  | { kind: 'fixed'; block: FixedBlock; date: string; start: number; end: number }
  /** 완료한 Task 의 "실제로 한 시간" 블록 — 끌어서 옮기거나 위·아래 끝을 늘이고 줄여요 (part = 나눠서 한 구간 번호) */
  | { kind: 'actual'; task: TimeBlockTask; date: string; start: number; end: number; part: number }

/** resize = 아래 끝(끝 시각) 조절, resize-top = 위 끝(시작 시각) 조절 */
export type DragMode = 'move' | 'resize' | 'resize-top'

export const isResize = (m: DragMode) => m !== 'move'

export type DropTarget =
  { type: 'grid'; date: string; start: number; end: number } | { type: 'unscheduled'; date: string }

export interface DragState {
  source: DragSource
  mode: DragMode
  target: DropTarget | null
  /** 놓을 수 없는 이유 (있으면 빨간 미리보기, 놓아도 저장 안 함) */
  invalid: string | null
}

interface Options {
  scrollRef: RefObject<HTMLDivElement | null>
  from: number
  to: number
  /** 1분당 px */
  ppm: number
  /** 머리글 등 위에 붙어 있는 영역 높이 — 자동 스크롤 기준 */
  stickyTop: number
  validate: (source: DragSource, target: DropTarget) => string | null
  onDrop: (source: DragSource, target: DropTarget, mode: DragMode) => void
  onActivate?: () => void
  /** 끌기 시작 후 저장 없이 끝났을 때 (분석용) — invalid 면 놓을 수 없던 이유 문구 */
  onCancel?: (source: DragSource, mode: DragMode, reason: DragCancelReason, invalid?: string) => void
  /** 터치로 Task 를 2초 꾹 눌렀을 때 (드래그는 접혀요) — 묶음 고르기 */
  onHold?: (source: DragSource) => void
}

/** esc · 터치 취소(pointer_cancel) · 자리 밖(no_target) · 놓을 수 없는 자리(invalid) */
export type DragCancelReason = 'esc' | 'pointer_cancel' | 'no_target' | 'invalid'

const MOUSE_THRESHOLD = 4
/** 터치로 이만큼 꾹 눌러야 끌기 시작 — 짧으면 "대고 잠깐 멈췄다 내리는" 스크롤이 끌기로 잡혀요 (2026-10: 350 → 500) */
const TOUCH_HOLD_MS = 500
const TOUCH_SLOP = 8
/** 터치로 이만큼 꾹 누르고 있으면 묶음 고르기 (명세 10.5 · 13.1 — 2초) */
const GROUP_HOLD_MS = 2000
const EDGE = 48
const MAX_SCROLL_SPEED = 14
const DEFAULT_MINUTES = 60

export const durationOf = (s: DragSource) =>
  s.start !== null && s.end !== null
    ? s.end - s.start
    : s.kind === 'task'
      ? (s.task.plannedDurationMinutes ?? DEFAULT_MINUTES)
      : DEFAULT_MINUTES

/**
 * 시간 미정 칸에 놓을 수 있는 Task (백엔드: 루틴·미션은 400).
 * 시간 미정 Task 도 다른 날의 시간 미정 칸으로 옮길 수 있어요 (2026-10 · PATCH /tasks/{id}/date)
 */
export const canUnschedule = (s: DragSource) =>
  s.kind === 'task' && !s.group?.length && s.task.sourceType === 'MANUAL' && s.task.taskStatus !== 'COMPLETED'

/**
 * 묶음 드래그 결과: 끈 Task 를 포함해 묶음 전체가 놓일 자리.
 * 끈 Task 가 움직인 만큼(분) 나머지도 똑같이 움직이고, 날짜는 끈 Task 가 놓인 날로 같이 가요.
 */
export function groupMoves(
  source: Extract<DragSource, { kind: 'task' }>,
  target: Extract<DropTarget, { type: 'grid' }>
): GroupMember[] {
  const self: GroupMember = { task: source.task, date: target.date, start: target.start, end: target.end }
  if (!source.group?.length || source.start === null) return [self]
  const delta = target.start - source.start
  return [
    self,
    ...source.group.map((m) => ({ task: m.task, date: target.date, start: m.start + delta, end: m.end + delta })),
  ]
}

export function useGridDrag({
  scrollRef,
  from,
  to,
  ppm,
  stickyTop,
  validate,
  onDrop,
  onActivate,
  onCancel,
  onHold,
}: Options) {
  const [drag, setDrag] = useState<DragState | null>(null)

  // 이벤트 리스너 안에서 최신 값을 쓰기 위해 ref 에 보관
  const opts = useRef({ from, to, ppm, stickyTop, validate, onDrop, onActivate, onCancel, onHold })
  useEffect(() => {
    opts.current = { from, to, ppm, stickyTop, validate, onDrop, onActivate, onCancel, onHold }
  })

  const session = useRef<{
    source: DragSource
    mode: DragMode
    pointerId: number
    touch: boolean
    startX: number
    startY: number
    lastX: number
    lastY: number
    grab: number | null // 블록 위쪽에서 잡은 지점까지의 분
    active: boolean
    holdTimer: number | null
    raf: number | null
    /** 터치로 3초 꾹 누르면 묶음 고르기 (명세 10.5) */
    groupTimer: number | null
    state: DragState | null
    cleanup: () => void
  } | null>(null)
  const suppressClick = useRef(false)

  /** 화면 좌표 → 놓을 위치 */
  const compute = useCallback(
    (x: number, y: number): DropTarget | null => {
      const s = session.current
      const root = scrollRef.current
      if (!s || !root) return null
      const { from, to, ppm } = opts.current

      // ① "시간 미정" 칸 위
      if (s.mode === 'move' && canUnschedule(s.source)) {
        for (const el of root.querySelectorAll<HTMLElement>('[data-unscheduled-date]')) {
          const r = el.getBoundingClientRect()
          if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
            return { type: 'unscheduled', date: el.dataset.unscheduledDate! }
          }
        }
      }

      // ② 날짜 칸 (고정 시간·크기 조절은 원래 날짜에서만)
      const cols = [...root.querySelectorAll<HTMLElement>('[data-date]')]
      if (!cols.length) return null
      // 고정 시간 · 실제로 한 시간 · 크기 조절은 원래 날짜 칸에서만
      const sameDay = s.source.kind === 'fixed' || s.source.kind === 'actual' || isResize(s.mode)
      let col = sameDay ? cols.find((c) => c.dataset.date === s.source.date) : undefined
      if (!col) {
        // 칸 밖이면 가장 가까운 칸
        let best = Infinity
        for (const c of cols) {
          const r = c.getBoundingClientRect()
          const d = x < r.left ? r.left - x : x > r.right ? x - r.right : 0
          if (d < best) {
            best = d
            col = c
          }
        }
      }
      if (!col) return null
      const rect = col.getBoundingClientRect()
      const minute = from + (y - rect.top) / ppm
      const date = col.dataset.date!

      if (s.mode === 'resize') {
        const start = s.source.start!
        const end = Math.min(to, Math.max(start + SNAP_MINUTES, snap(minute)))
        return { type: 'grid', date, start, end }
      }
      if (s.mode === 'resize-top') {
        // 끝은 그대로 두고 시작 시각만 움직여요 (최소 10분은 남김)
        const end = s.source.end!
        const start = Math.max(from, Math.min(end - SNAP_MINUTES, snap(minute)))
        return { type: 'grid', date, start, end }
      }
      const dur = durationOf(s.source)
      if (s.grab === null) s.grab = Math.min(dur / 2, 30) // 시간 미정 칩: 블록 위쪽 조금 아래를 잡은 것으로
      // 묶음이면 묶음 전체가 격자 안에 들어오게 (가장 이른 Task 가 위로, 가장 늦은 Task 가 아래로 넘치지 않게)
      let before = 0
      let after = dur
      if (s.source.kind === 'task' && s.source.group?.length && s.source.start !== null) {
        for (const m of s.source.group) {
          before = Math.max(before, s.source.start - m.start)
          after = Math.max(after, m.end - s.source.start)
        }
      }
      const lo = from + before
      const start = Math.min(Math.max(snap(minute - s.grab), lo), Math.max(lo, to - after))
      return { type: 'grid', date, start, end: start + dur }
    },
    [scrollRef]
  )

  const update = useCallback(() => {
    const s = session.current
    if (!s || !s.active) return
    const target = compute(s.lastX, s.lastY)
    const invalid = target ? opts.current.validate(s.source, target) : null
    const prev = s.state
    const same = prev && prev.invalid === invalid && JSON.stringify(prev.target) === JSON.stringify(target)
    if (same) return
    s.state = { source: s.source, mode: s.mode, target, invalid }
    setDrag(s.state)
  }, [compute])

  /** 위·아래 끝 근처면 한 프레임만큼 스크롤 (activate 의 loop 가 매 프레임 호출) */
  const autoScrollStep = useCallback(() => {
    const s = session.current
    const root = scrollRef.current
    if (!s || !s.active || !root) return
    const r = root.getBoundingClientRect()
    const top = r.top + opts.current.stickyTop
    let dy = 0
    if (s.lastY < top + EDGE) dy = -Math.ceil(((top + EDGE - s.lastY) / EDGE) * MAX_SCROLL_SPEED)
    else if (s.lastY > r.bottom - EDGE) dy = Math.ceil(((s.lastY - (r.bottom - EDGE)) / EDGE) * MAX_SCROLL_SPEED)
    if (dy) {
      const before = root.scrollTop
      root.scrollTop += Math.max(-MAX_SCROLL_SPEED, Math.min(MAX_SCROLL_SPEED, dy))
      if (root.scrollTop !== before) update()
    }
  }, [scrollRef, update])

  const finish = useCallback((commit: boolean, cancelReason: DragCancelReason = 'pointer_cancel') => {
    const s = session.current
    if (!s) return
    s.cleanup()
    session.current = null
    setDrag(null)
    if (!s.active) return
    // 드래그가 끝난 직후 같은 요소에 click 이 발생해요 → 상세 팝오버가 열리지 않게 한 번 막아요
    suppressClick.current = true
    window.setTimeout(() => (suppressClick.current = false), 0)
    const st = s.state
    if (commit && st?.target && !st.invalid) opts.current.onDrop(s.source, st.target, s.mode)
    else if (!commit) opts.current.onCancel?.(s.source, s.mode, cancelReason)
    else if (st?.invalid) opts.current.onCancel?.(s.source, s.mode, 'invalid', st.invalid)
    else opts.current.onCancel?.(s.source, s.mode, 'no_target')
  }, [])

  const activate = useCallback(() => {
    const s = session.current
    if (!s || s.active) return
    s.active = true
    document.body.style.userSelect = 'none'
    document.body.style.cursor = isResize(s.mode) ? 'ns-resize' : 'grabbing'
    opts.current.onActivate?.()
    if (s.touch && navigator.vibrate) navigator.vibrate(10)
    update()
    const loop = () => {
      const cur = session.current
      if (!cur?.active) return
      autoScrollStep()
      cur.raf = window.requestAnimationFrame(loop)
    }
    s.raf = window.requestAnimationFrame(loop)
  }, [update, autoScrollStep])

  const startDrag = useCallback(
    (e: ReactPointerEvent<HTMLElement>, source: DragSource, mode: DragMode = 'move') => {
      if (e.button !== 0 || session.current) return
      if (isResize(mode)) e.stopPropagation()
      const touch = e.pointerType === 'touch'

      // 잡은 지점: 블록 위쪽에서 몇 분 아래를 잡았는지 (놓을 때 같은 지점이 커서 아래에 오도록)
      let grab: number | null = null
      if (source.start !== null) {
        const col = scrollRef.current?.querySelector<HTMLElement>(`[data-date="${source.date}"]`)
        if (col) {
          const minute = opts.current.from + (e.clientY - col.getBoundingClientRect().top) / opts.current.ppm
          grab = Math.max(0, minute - source.start)
        }
      }

      const onMove = (ev: PointerEvent) => {
        const s = session.current
        if (!s || ev.pointerId !== s.pointerId) return
        s.lastX = ev.clientX
        s.lastY = ev.clientY
        const dist = Math.hypot(ev.clientX - s.startX, ev.clientY - s.startY)
        // 손가락이 움직이면 "3초 꾹 = 묶음 고르기" 는 취소 (보통 드래그로 계속)
        if (s.groupTimer && dist > TOUCH_SLOP) {
          window.clearTimeout(s.groupTimer)
          s.groupTimer = null
        }
        if (!s.active) {
          if (s.touch) {
            if (dist > TOUCH_SLOP) finish(false) // 길게 누르기 전에 움직임 → 스크롤로 보고 포기
          } else if (dist > MOUSE_THRESHOLD) activate()
          return
        }
        update()
      }
      const onUp = (ev: PointerEvent) => {
        if (session.current && ev.pointerId === session.current.pointerId) finish(true)
      }
      const onCancel = (ev: PointerEvent) => {
        if (session.current && ev.pointerId === session.current.pointerId) finish(false)
      }
      const onKey = (ev: KeyboardEvent) => {
        if (ev.key === 'Escape' && session.current?.active) {
          ev.preventDefault()
          ev.stopPropagation()
          finish(false, 'esc')
        }
      }
      // 터치로 드래그 중에는 화면이 같이 스크롤되지 않게
      const onTouchMove = (ev: TouchEvent) => {
        if (session.current?.active && ev.cancelable) ev.preventDefault()
      }
      const onContextMenu = (ev: Event) => {
        if (session.current) ev.preventDefault()
      }

      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
      window.addEventListener('pointercancel', onCancel)
      window.addEventListener('keydown', onKey, true)
      window.addEventListener('touchmove', onTouchMove, { passive: false })
      window.addEventListener('contextmenu', onContextMenu)

      session.current = {
        source,
        mode,
        pointerId: e.pointerId,
        touch,
        startX: e.clientX,
        startY: e.clientY,
        lastX: e.clientX,
        lastY: e.clientY,
        grab,
        active: false,
        holdTimer: touch ? window.setTimeout(activate, TOUCH_HOLD_MS) : null,
        // 터치로 Task 를 움직이지 않고 2초 꾹 누르면 → 드래그를 접고 묶음 고르기로 (Shift+클릭과 같은 묶음)
        groupTimer:
          touch && source.kind === 'task' && mode === 'move' && opts.current.onHold
            ? window.setTimeout(() => {
                const s = session.current
                if (!s) return
                s.cleanup()
                session.current = null
                setDrag(null)
                // 손을 뗄 때 생기는 click 이 상세 창 · 묶음 토글로 이어지지 않게 한 번 막아요
                suppressClick.current = true
                window.setTimeout(() => (suppressClick.current = false), 400)
                if (navigator.vibrate) navigator.vibrate(20)
                opts.current.onHold?.(source)
              }, GROUP_HOLD_MS)
            : null,
        raf: null,
        state: null,
        cleanup: () => {
          const s = session.current
          if (s?.holdTimer) window.clearTimeout(s.holdTimer)
          if (s?.groupTimer) window.clearTimeout(s.groupTimer)
          if (s?.raf) window.cancelAnimationFrame(s.raf)
          window.removeEventListener('pointermove', onMove)
          window.removeEventListener('pointerup', onUp)
          window.removeEventListener('pointercancel', onCancel)
          window.removeEventListener('keydown', onKey, true)
          window.removeEventListener('touchmove', onTouchMove)
          window.removeEventListener('contextmenu', onContextMenu)
          document.body.style.userSelect = ''
          document.body.style.cursor = ''
        },
      }
    },
    [scrollRef, activate, finish, update]
  )

  // 화면을 벗어나면 정리
  useEffect(() => () => session.current?.cleanup(), [])

  /** 드래그 직후의 click 이면 true (그 click 은 무시) */
  const consumeClick = useCallback(() => suppressClick.current, [])

  return { drag, startDrag, consumeClick }
}
