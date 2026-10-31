'use client'

import type { CSSProperties, MouseEvent, PointerEvent } from 'react'
import { GripHorizontal } from 'lucide-react'
import { Check, Clock, Repeat, Sparkles, X } from '@/components/icons'
import { cn } from '@/lib/cn'
import { goalVars } from '@/lib/goalTone'
import { shownTime, type Placed, type PlacedSleep } from '../layout'
import { formatMinutes, minutesToTime, todayKst } from '../time'
import type { ActualActivity, FixedBlock, TimeBlockTask } from '../types'
import { isTopPriority, PriorityBadge } from './Priority'

/** 고정 시간 바탕 (명세 9.5) — bg/sunken + 45° 빗금 1px · 6px 간격 (수면 포함 모두 같은 회색) */
export const HATCH: CSSProperties = {
  backgroundColor: 'var(--color-hatch)',
  backgroundImage: 'repeating-linear-gradient(45deg, var(--color-hatch-line) 0 1px, transparent 1px 6px)',
}

interface Geometry {
  /** 화면 시작 시각(분) */
  from: number
  /** 1분당 px */
  ppm: number
}

/** 드래그 가능한 블록에 붙는 핸들러. 없으면 드래그 불가 */
export type BlockDrag = (e: PointerEvent<HTMLElement>, mode: 'move' | 'resize' | 'resize-top') => void

/** 이보다 낮은 블록은 위 끝 핸들을 숨겨요 (위·아래 핸들이 블록을 다 덮으면 옮길 곳이 없어서) */
const TOP_HANDLE_MIN_PX = 22

// 길게 눌렀을 때 iOS 의 텍스트 선택·메뉴가 뜨지 않게
const NO_CALLOUT: CSSProperties = { WebkitTouchCallout: 'none', WebkitUserSelect: 'none', userSelect: 'none' }

/** 블록 위·아래 가장자리를 잡고 늘리기/줄이기 (위 = 시작 시각, 아래 = 끝 시각) */
function ResizeHandle({
  onDrag,
  edge = 'bottom',
}: {
  onDrag: BlockDrag
  edge?: 'top' | 'bottom'
}) {
  const top = edge === 'top'
  return (
    <span
      aria-hidden
      data-resize-handle={edge}
      onPointerDown={(e) => onDrag(e, top ? 'resize-top' : 'resize')}
      className={cn(
        'group/handle absolute inset-x-0 flex h-2 cursor-ns-resize justify-center',
        top ? 'top-0 items-start' : 'bottom-0 items-end'
      )}
    >
      <span
        className={cn(
          'h-1 w-6 rounded-full opacity-0 transition-opacity group-hover/handle:opacity-100',
          top ? 'mt-0.5' : 'mb-0.5',
          'bg-ink-4'
        )}
      />
    </span>
  )
}

const pos = (p: Placed<unknown>, g: Geometry): CSSProperties => ({
  top: (p.top - g.from) * g.ppm,
  height: Math.max((p.bottom - p.top) * g.ppm - 2, 16),
})

/** 왼쪽 4px 목표 색 선 (모서리를 따라 잘려요) */
const BAR = 'inset 4px 0 0 var(--goal-accent)'
/** 연한 목표 바탕을 55% 로 (미완료 · 제안) */
const SOFT_BG = 'color-mix(in srgb, var(--goal-bg) 55%, var(--color-surface))'

/**
 * 블록 스타일 (디자인 시스템 Event — 연한 목표 바탕 + 왼쪽 색 선 + 목표 글자색)
 *  - 루틴 · 일반 Task 같은 모양 (목표 색은 lib/goalTone 이 팔레트로 맞춰요)
 *  - 완료: 목표 색 원 ✓ / 놓침: 바탕 55% + 1px 목표 색 테두리 / 직접 정한 시간: 📌
 */
export function taskColors(t: TimeBlockTask): CSSProperties {
  return {
    ...goalVars(t.goalColorCode),
    backgroundColor: 'var(--goal-bg)',
    boxShadow: BAR,
    color: 'var(--goal-fg)',
  }
}

export function TaskBlock({
  placed,
  geometry,
  selected,
  onSelect,
  drag,
  dimmed,
  isNew,
  grouped,
  planned,
  faded,
  focused,
  dense,
  noResize,
}: {
  placed: Placed<TimeBlockTask>
  geometry: Geometry
  selected: boolean
  onSelect: (t: TimeBlockTask, e: MouseEvent<HTMLButtonElement>) => void
  drag?: BlockDrag
  /** 드래그 중인 원래 자리 */
  dimmed?: boolean
  /** 방금 만든 Task (NEW 표시) */
  isNew?: boolean
  /** 묶음 드래그로 같이 옮길 Task (점선 테두리) */
  grouped?: boolean
  /** 일간 "계획" 칸: 완료했어도 계획한 시각으로 그렸으니 "실제" 표시를 하지 않아요 */
  planned?: boolean
  /** 확인을 기다리는 블록에 눈이 가도록 흐리게 */
  faded?: boolean
  /** 확인을 기다리는 블록 (테두리 강조) */
  focused?: boolean
  /** 주간처럼 좁은 칸 (명세 9.8): Caption/SM 11 Medium · 두 줄까지 · 안쪽 3/4 · 40px 미만이면 시간 숨김 */
  dense?: boolean
  /** 나눠 넣은 조각: 길이 조절 손잡이를 숨겨요 (옮기면 한 덩어리가 돼요) */
  noResize?: boolean
}) {
  const t = placed.item
  const heightPx = (placed.bottom - placed.top) * geometry.ppm
  const short = heightPx < 26 // 10~20분 블록: 한 줄로 가운데 정렬
  const done = t.taskStatus === 'COMPLETED'
  // 완료 후 실제 시각에 그려진 블록 ("실제" 표시)
  const actual = !planned && (shownTime(t)?.actual ?? false)
  // 22시 이월 제안(아직 확인 안 함): 점선 + 연한 바탕 — "확정된 일정이 아니라 제안"으로 보이게
  const proposed = (!!t.carryPending || !!t.proposed) && !done
  // 중요도 A: 제목 앞 'A' 배지
  const top = isTopPriority(t.taskPriority)
  const routine = t.sourceType === 'ROUTINE'
  const width = 100 / placed.cols
  const style: CSSProperties = {
    ...pos(placed, geometry),
    left: `calc(${placed.col * width}% + 2px)`,
    width: `calc(${width}% - 4px)`,
    ...goalVars(t.goalColorCode),
    color: 'var(--goal-fg)',
    // 2026-10: 완료 = 테두리 + 목표 색 바탕 / 아직 안 한 일(계획 · 놓침) = 바탕 없이 테두리만
    //  왼쪽 막대는 모서리를 따라 휘지 않는 일직선 (아래 span) — Task · 루틴 같은 모양, 루틴은 오른쪽 반복 아이콘
    ...(proposed
      ? {
          // 제안 (Proposal) — 바탕 55% + 1.5px 목표 색 테두리, 확인 대기 중(focused)이면 바탕 100% (Proposal Active)
          backgroundColor: focused ? 'var(--goal-bg)' : SOFT_BG,
          border: '1.5px solid var(--goal-accent)',
        }
      : done
        ? { backgroundColor: 'var(--goal-bg)', border: '1px solid var(--goal-accent)' }
        : { backgroundColor: 'var(--color-surface)', border: '1px solid var(--goal-accent)' }),
    ...NO_CALLOUT,
  }

  return (
    <button
      type="button"
      data-task-id={t.taskId}
      // 분석용: 실제로 한 시각에 그린 블록인지 (timetable.task_block_click drag_source)
      data-actual={actual || undefined}
      onClick={(e) => onSelect(t, e)}
      onPointerDown={drag ? (e) => drag(e, 'move') : undefined}
      style={style}
      aria-label={`${top ? '중요도 A ' : ''}${t.taskName} ${actual ? '실제 ' : ''}${formatMinutes(placed.start)}~${formatMinutes(placed.end)}`}
      className={cn(
        // 왼쪽은 각지게 (막대가 위아래 끝까지 곧게), 오른쪽만 둥글게
        'rounded-r-event text-label-sm absolute z-10 flex flex-col overflow-hidden pr-2 pl-3 text-left transition-[filter,box-shadow]',
        dense
          ? cn('pr-1 pl-2 text-[11px] leading-[1.25] font-medium', short ? 'justify-center py-0' : 'py-[3px]')
          : short
            ? 'justify-center py-0 text-[11px]'
            : 'py-1.5',
        'hover:brightness-[.97]',
        drag && 'cursor-grab active:cursor-grabbing',
        dimmed && 'opacity-30',
        // 미리보기 중 제안 블록을 돋보이게 — 나머지는 흐리게(faded)
        faded && 'opacity-50 saturate-[.8]',
        focused && 'shadow-primary z-[12]',
        selected && 'ring-ink ring-2 ring-offset-1',
        grouped && 'outline-action-primary-bg z-[11] outline-[1.5px] outline-offset-1 outline-dashed',
        placed.clippedTop && 'rounded-t-none',
        placed.clippedBottom && 'rounded-b-none'
      )}
    >
      {/* 왼쪽 목표 색 막대 — 위아래 끝까지 일직선 (블록 왼쪽 모서리는 각져 있어요). 모바일 2px · 데스크톱 4px */}
      <span aria-hidden className="pointer-events-none absolute inset-y-0 left-0 w-[2px] bg-(--goal-accent) sm:w-[4px]" />
      <span className={cn('flex min-w-0 gap-1', dense && !short ? 'items-start' : 'items-center')}>
        {done && (
          <span className="bg-success text-on-ink grid size-3.5 shrink-0 place-items-center rounded-full">
            <Check className="size-2.5" />
          </span>
        )}
        {t.missed && (
          <span className="bg-action-danger-bg text-action-danger-fg grid size-3.5 shrink-0 place-items-center rounded-full">
            <X className="size-2.5" />
          </span>
        )}
        {proposed && t.proposed && <Sparkles className="size-3.5 shrink-0 text-(--goal-accent)" />}
        {top && <PriorityBadge priority="A" className={cn(dense && !short && 'mt-px')} />}
        <span className={cn('min-w-0 flex-1', dense && !short ? 'line-clamp-2 break-all' : 'truncate')}>
          {t.emoji ? `${t.emoji} ` : ''}
          {t.taskName}
        </span>
        {routine && (
          <Repeat
            className={cn('shrink-0 opacity-80', dense ? 'size-3' : 'size-3.5', dense && !short && 'mt-px')}
            aria-label="루틴"
          />
        )}
        {isNew && (
          <span className="bg-action-danger-bg text-action-danger-fg shrink-0 rounded px-1 text-[9px] leading-[14px] font-bold">
            NEW
          </span>
        )}
        {proposed && (
          <span
            className={cn(
              'shrink-0 rounded px-1 text-[9px] leading-[14px] font-bold',
              t.proposed ? 'bg-ai-gradient text-white' : 'bg-bg-surface text-text-secondary'
            )}
          >
            {t.proposed ? 'AI 제안' : '옮길까요?'}
          </span>
        )}
      </span>
      {heightPx >= (dense ? 40 : 34) && (
        <span className={cn('text-caption-sm text-text-secondary truncate', dense ? 'mt-px text-[10px]' : 'mt-0.5')}>
          {actual && '실제 '}
          {formatMinutes(placed.start)} – {formatMinutes(placed.end)}
        </span>
      )}
      {drag && !noResize && !placed.clippedTop && heightPx >= TOP_HANDLE_MIN_PX && (
        <ResizeHandle onDrag={drag} edge="top" />
      )}
      {drag && !noResize && !placed.clippedBottom && <ResizeHandle onDrag={drag} />}
    </button>
  )
}

export function FixedBlockView({
  placed,
  geometry,
  selected,
  onSelect,
  drag,
  dimmed,
}: {
  placed: Placed<FixedBlock>
  geometry: Geometry
  selected: boolean
  onSelect: (b: FixedBlock, e: MouseEvent<HTMLButtonElement>) => void
  drag?: BlockDrag
  dimmed?: boolean
}) {
  const b = placed.item
  const heightPx = (placed.bottom - placed.top) * geometry.ppm
  return (
    <button
      type="button"
      data-fixed-id={`${b.lifePatternId}-${b.targetDate}`}
      onClick={(e) => onSelect(b, e)}
      onPointerDown={drag ? (e) => drag(e, 'move') : undefined}
      style={{ ...pos(placed, geometry), ...HATCH, ...NO_CALLOUT }}
      aria-label={`고정 시간 ${b.title} ${formatMinutes(placed.start)}~${formatMinutes(placed.end)}`}
      className={cn(
        'border-border-subtle text-text-secondary rounded-event-fixed text-caption-md absolute inset-x-0.5 z-0 flex flex-col overflow-hidden border px-2 py-1 text-left',
        'hover:border-line-strong',
        drag && 'cursor-grab active:cursor-grabbing',
        dimmed && 'opacity-40',
        selected && 'ring-ink-3 ring-2',
        placed.clippedTop && 'rounded-t-none border-t-0',
        placed.clippedBottom && 'rounded-b-none border-b-0'
      )}
    >
      <span className="flex min-w-0 items-center gap-1 truncate">
        <Clock className="text-text-tertiary size-3.5 shrink-0" aria-hidden />
        {b.emoji ? `${b.emoji} ` : ''}
        {b.title}
        {/* 기본 시간과 다르게 바꾼 날 (이날만·이번 주로 바꾼 경우). 지난 날짜는 "앞으로" 변경 때 예전 시간으로 고정해 둔 것이라 표시하지 않아요 */}
        {b.overridden && b.targetDate >= todayKst() && <span className="text-text-brand ml-1 font-medium">· 변경</span>}
      </span>
      {heightPx >= 30 && (
        <span className="truncate">
          {formatMinutes(placed.start)} – {formatMinutes(placed.end)}
        </span>
      )}
      {drag && !placed.clippedTop && heightPx >= TOP_HANDLE_MIN_PX && <ResizeHandle onDrag={drag} edge="top" />}
      {drag && !placed.clippedBottom && <ResizeHandle onDrag={drag} />}
    </button>
  )
}

/** 수면 배경 — 다른 고정 시간과 같은 회색 빗금 (명세 9.5) */
const SLEEP_BG: CSSProperties = HATCH

/**
 * 수면 블록 — 기상 전(아침)·취침 후(밤) 2시간만큼 보여요.
 * 아침 블록은 아래 가장자리(= 기상), 밤 블록은 위 가장자리(= 취침)를 끌어서 시간을 바꿔요.
 * 블록 전체를 옮기는 드래그는 없어요 (기상·취침 중 하나만 바꾸는 게 보통이라서).
 */
export function SleepBlockView({
  placed,
  geometry,
  selected,
  onSelect,
  drag,
  dimmed,
}: {
  placed: PlacedSleep
  geometry: Geometry
  selected: boolean
  onSelect: (b: FixedBlock, e: MouseEvent<HTMLButtonElement>) => void
  drag?: BlockDrag
  dimmed?: boolean
}) {
  const b = placed.item
  const wake = placed.edge === 'wake'
  const time = minutesToTime(wake ? placed.end : placed.start)
  const label = wake ? `기상 ${time}` : `취침 ${time}`
  return (
    <button
      type="button"
      data-fixed-id={`${b.lifePatternId}-${b.targetDate}`}
      data-sleep-edge={placed.edge}
      onClick={(e) => onSelect(b, e)}
      style={{ ...pos(placed, geometry), ...SLEEP_BG, ...NO_CALLOUT }}
      aria-label={`수면 · ${label}`}
      className={cn(
        'text-text-secondary border-border-subtle absolute inset-x-0 z-0 flex flex-col overflow-hidden border text-left text-[11px] leading-tight',
        wake ? 'justify-end' : 'justify-start',
        dimmed && 'opacity-40',
        selected && 'ring-ink-3 ring-2'
      )}
    >
      {/* 끌 수 있는 가장자리 — 늘 보이는 손잡이 (마우스를 올려야 보이는 다른 블록과 달리 처음 보는 사람도 알 수 있게) */}
      <span
        aria-hidden
        onPointerDown={drag ? (e) => drag(e, wake ? 'resize' : 'resize-top') : undefined}
        className={cn(
          'border-border-subtle text-text-secondary flex h-6 shrink-0 items-center gap-1 px-2 font-semibold',
          wake ? 'border-b-2' : 'border-t-2',
          drag && 'hover:bg-bg-subtle cursor-ns-resize'
        )}
      >
        <span className="truncate">
          {wake ? '☀️' : '🌙'} {label}
        </span>
        {drag && <GripHorizontal className="ml-auto size-3.5 shrink-0 opacity-70" />}
      </span>
    </button>
  )
}

/**
 * 실제로 한 일 블록 — 그날 칸 오른쪽 "실제" 칸에 그려요.
 * 계획(Task) 블록과 헷갈리지 않게 흰 바탕 + 초록 왼쪽 선 + "실제" 표시, 목표를 연결했으면 그 색 선.
 */
export function ActivityBlock({
  placed,
  geometry,
  selected,
  onSelect,
}: {
  placed: Placed<ActualActivity>
  geometry: Geometry
  selected: boolean
  onSelect: (a: ActualActivity, e: MouseEvent<HTMLButtonElement>) => void
}) {
  const a = placed.item
  const heightPx = (placed.bottom - placed.top) * geometry.ppm
  const short = heightPx < 34
  // 목표가 있으면 목표 색, 없으면 초록 (실제로 한 일)
  const tone: CSSProperties = a.goalColorCode
    ? goalVars(a.goalColorCode)
    : ({
        '--goal-bg': 'var(--color-success-soft)',
        '--goal-accent': 'var(--color-success)',
        '--goal-fg': 'var(--color-ink)',
      } as CSSProperties)
  return (
    <button
      type="button"
      data-activity-id={a.activityId}
      onClick={(e) => {
        e.stopPropagation()
        onSelect(a, e)
      }}
      style={{
        ...pos(placed, geometry),
        // 주간처럼 Task 와 한 열에 섞어 그릴 때 겹치면 나란히 (cols 가 1 이면 칸 전체)
        ...(placed.cols > 1
          ? { left: `calc(${(placed.col * 100) / placed.cols}% + 2px)`, width: `calc(${100 / placed.cols}% - 4px)` }
          : {}),
        ...NO_CALLOUT,
        ...tone,
        // 완료한 Task 와 같은 틀 (왼쪽 곧은 막대 · 오른쪽만 둥글게) + 차이: 연한 바탕 · 흐린 실선 테두리 · ✓ 글자
        //  (점선은 묶어 옮기기 표시와 헷갈려서 쓰지 않아요)
        backgroundColor: 'color-mix(in srgb, var(--goal-bg) 55%, var(--color-surface))',
        border: '1px solid color-mix(in srgb, var(--goal-accent) 45%, transparent)',
        color: 'var(--goal-fg)',
      }}
      aria-label={`실제로 한 일 ${a.title} ${formatMinutes(placed.start)}~${formatMinutes(placed.end)}`}
      className={cn(
        'rounded-r-event absolute inset-x-0.5 z-10 flex flex-col overflow-hidden pr-1.5 pl-2.5 text-left text-[11px] leading-tight sm:pl-3',
        'hover:brightness-[.97]',
        short ? 'justify-center py-0' : 'py-1',
        selected && 'ring-ink ring-2 ring-offset-1',
        placed.clippedTop && 'rounded-t-none',
        placed.clippedBottom && 'rounded-b-none'
      )}
    >
      <span aria-hidden className="pointer-events-none absolute inset-y-0 left-0 w-[2px] bg-(--goal-accent) sm:w-[4px]" />
      <span className="flex min-w-0 items-center gap-1">
        <span className="text-success shrink-0 text-[11px] font-bold">✓</span>
        <span className="truncate font-semibold">
          {a.emoji ? `${a.emoji} ` : ''}
          {a.title}
        </span>
      </span>
      {!short && (
        <span className="truncate tabular-nums opacity-70">
          실제 {formatMinutes(placed.start)}–{formatMinutes(placed.end)}
        </span>
      )}
      {heightPx >= 56 && a.replacedTaskName && <span className="text-ink-4 truncate">대신: {a.replacedTaskName}</span>}
    </button>
  )
}
