'use client'

import { isValidRoutineRange, minutesToTime, timeToMinutes } from '@/features/timetable/time'
import { useEffect, useRef, useState } from 'react'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { RoutineWeekMap, routineConflicts, useAllRoutines } from './RoutineWeekMap'
import { BellOff, CalendarPlus } from 'lucide-react'
import { Bell } from '@/components/icons'
import { cn } from '@/lib/cn'
import { addDays, todayKst, weekdayIndex } from '@/features/timetable/time'
import { useRoutineHeatmap, type GoalCategory, type JavaDayOfWeek, type RoutineSummary } from '../api'
import { useAddRoutineExtraDay, useCreateRoutine, useDeleteRoutine, useUpdateRoutine } from '../edit/mutations'
import {
  AddButton,
  DaysPicker,
  Field,
  InlineConfirm,
  InlineForm,
  RowActions,
  inlineInput,
  useEditing,
  type InlineCloseMethod,
  type InlineSubmitMethod,
} from '../edit/inline'
import { JAVA_DAYS, JAVA_DAY_LABEL, formatDot, hhmm } from '../format'
import { Section } from './sections'
import { byDayLine } from './RoutineHeatmapSection'
import { activeSubGoals, areasOf, bucketOf, defaultPeriod } from './SubGoalsSection'

type DayTimeMap = Partial<Record<JavaDayOfWeek, { start: string; end: string }>>

/** 오늘부터 며칠 뒤인지 (YYYY-MM-DD) */
const daysBetween = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000)

/** 목표 루틴 수 (분석용 routine_count) */
const routineCountOf = (goal: GoalCategory) => activeSubGoals(goal).reduce((n, sg) => n + sg.routines.length, 0)

type RoutineAction = 'create' | 'update' | 'delete' | 'extra_day'
const trackRoutineResult = (goalId: number, action: RoutineAction, err?: unknown) =>
  track('goal_detail.routine_result', {
    goal_category_id: goalId,
    routine_action: action,
    result: err === undefined ? 'success' : 'fail',
    error_status: err === undefined ? undefined : errorStatusOf(err),
  })

/**
 * 루틴 입력창이 다른 항목 입력창을 열어서 닫혔을 때 (close_method=other_open).
 * 취소·저장으로 닫히면 current 가 null 이 돼서 여기선 안 보내요.
 */
function useTrackOtherOpen(isOpen: boolean, current: string | null, goalId: number, isNew: boolean) {
  const wasOpen = useRef(false)
  useEffect(() => {
    if (wasOpen.current && !isOpen && current != null) {
      track('goal_detail.routine_edit_cancel', { goal_category_id: goalId, is_new: isNew, close_method: 'other_open' })
    }
    wasOpen.current = isOpen
  }, [isOpen, current, goalId, isNew])
}

const toDayTimeMap = (routine?: RoutineSummary): DayTimeMap =>
  Object.fromEntries(
    (routine?.dayTimes ?? []).map((t) => [t.dayOfWeek, { start: hhmm(t.startTime) ?? '', end: hhmm(t.endTime) ?? '' }])
  )

/**
 * 루틴 시간 한 줄 — 요일별 시간이 있으면 시간이 같은 요일끼리 묶어서
 * "월·금 20:00~20:25 · 수 07:00~07:30"
 */
function routineTimeText(routine: RoutineSummary): string {
  const base = hhmm(routine.repeatStartTime)
  const baseEnd = hhmm(routine.repeatEndTime)
  const fmt = (s: string | null, e: string | null) => (s ? `${s}${e ? `~${e}` : ''}` : '시간 자동')
  if (!routine.dayTimes?.length) return fmt(base, baseEnd)
  const groups = new Map<string, JavaDayOfWeek[]>()
  for (const d of JAVA_DAYS.filter((x) => routine.repeatDays.includes(x))) {
    const own = routine.dayTimes.find((t) => t.dayOfWeek === d)
    const key = own ? fmt(hhmm(own.startTime), hhmm(own.endTime)) : fmt(base, baseEnd)
    groups.set(key, [...(groups.get(key) ?? []), d])
  }
  return [...groups.entries()].map(([time, ds]) => `${ds.map((d) => JAVA_DAY_LABEL[d]).join('·')} ${time}`).join(' · ')
}

function DayChips({ days }: { days: JavaDayOfWeek[] }) {
  return (
    <span className="flex gap-1">
      {JAVA_DAYS.map((d) => (
        <span
          key={d}
          className={cn(
            'grid size-6 place-items-center rounded-md text-[11px] font-semibold',
            days.includes(d) ? 'bg-brand text-white' : 'bg-subtle text-ink-4'
          )}
        >
          {JAVA_DAY_LABEL[d]}
        </span>
      ))}
    </span>
  )
}

function RoutineForm({
  goal,
  routine,
  subGoalId,
  onDone,
}: {
  goal: GoalCategory
  /** 없으면 새로 추가 */
  routine?: RoutineSummary
  subGoalId?: number
  onDone: () => void
}) {
  // 영역은 선택 — 안 고르면 서버가 목표의 "기타 할 일"에 넣어요 (트리 구조를 몰라도 바로 추가)
  const areas = areasOf(goal)
  const bucket = bucketOf(goal)
  const create = useCreateRoutine(goal.goalCategoryId)
  const update = useUpdateRoutine()
  const period = defaultPeriod(goal)
  const today = todayKst()
  const goalId = goal.goalCategoryId
  const isNew = !routine
  const allRoutines = useAllRoutines()

  const [name, setName] = useState(routine?.routineName ?? '')
  const [sub, setSub] = useState<number | null>(
    subGoalId != null && areas.some((a) => a.generalCategoryId === subGoalId) ? subGoalId : null
  )
  // 반복 기간은 보통 목표 기간 그대로라 접어 둬요
  const [showPeriod, setShowPeriod] = useState(false)
  const [days, setDays] = useState<JavaDayOfWeek[]>(routine?.repeatDays?.length ? routine.repeatDays : JAVA_DAYS)
  const [startTime, setStartTime] = useState(hhmm(routine?.repeatStartTime ?? null) ?? '')
  const [endTime, setEndTime] = useState(hhmm(routine?.repeatEndTime ?? null) ?? '')
  const [start, setStart] = useState(routine?.repeatStartDate ?? (period.start < today ? today : period.start))
  const [end, setEnd] = useState(routine?.repeatEndDate ?? (period.end < today ? today : period.end))
  const [notify, setNotify] = useState(routine?.notificationEnabled ?? true)
  const [howTo, setHowTo] = useState(routine?.description ?? '')
  // 요일별 시간: 기본은 모든 요일 같은 시간, 원하면 요일마다 다르게 (기본과 다른 요일만 저장돼요)
  const [perDay, setPerDay] = useState(!!routine?.dayTimes?.length)
  const [dayTimes, setDayTimes] = useState<DayTimeMap>(() => toDayTimeMap(routine))

  /** 요일별 칸에 보여줄 값 — 따로 정한 게 없으면 기본 시간 */
  const timeOf = (d: JavaDayOfWeek) => dayTimes[d] ?? { start: startTime, end: endTime }
  const setDayTime = (d: JavaDayOfWeek, key: 'start' | 'end', v: string) =>
    setDayTimes((m) => ({ ...m, [d]: { ...timeOf(d), [key]: v } }))
  const resetDay = (d: JavaDayOfWeek) =>
    setDayTimes((m) => {
      const next = { ...m }
      delete next[d]
      return next
    })
  const shownDays = JAVA_DAYS.filter((d) => days.includes(d))
  // 기본 시간과 실제로 다른 요일만 보내요
  const overrides = perDay
    ? shownDays
        .map((d) => ({ dayOfWeek: d, startTime: timeOf(d).start, endTime: timeOf(d).end }))
        .filter((t) => t.startTime && t.endTime && (t.startTime !== startTime || t.endTime !== endTime))
    : []
  const badDay = perDay
    ? shownDays.find((d) => {
        const t = timeOf(d)
        return !!t.start !== !!t.end || (t.start && t.end && !isValidRoutineRange(t.start, t.end))
      })
    : undefined

  const hadTime = !!routine?.repeatStartTime
  const oneTimeOnly = !!startTime !== !!endTime
  const error = !name.trim()
    ? null
    : !days.length
      ? '요일을 하나 이상 골라 주세요.'
      : oneTimeOnly
        ? '시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요.'
        : startTime && endTime && !isValidRoutineRange(startTime, endTime)
          ? '시작과 종료 시간을 확인해 주세요. 자정을 넘기는 루틴(예: 23:00 ~ 00:00)은 12시간까지 돼요.'
          : hadTime && !startTime
            ? '이미 정한 시간은 비울 수 없어요. 시간 없이 하려면 루틴을 다시 만들어 주세요.'
            : !start || !end || start > end
              ? '반복 기간을 확인해 주세요.'
              : badDay
                ? `${JAVA_DAY_LABEL[badDay]}요일 시간을 확인해 주세요. (시작·종료를 함께, 자정을 넘기면 12시간까지)`
                : null
  const valid = !!name.trim() && !error

  // [UX] 빨간 문구가 뜰 때 (문구가 바뀔 때마다 한 번)
  const lastError = useRef<string | null>(null)
  useEffect(() => {
    if (error && error !== lastError.current) {
      track('goal_detail.routine_edit_error', { goal_category_id: goalId, is_new: isNew, validation_error: error })
    }
    lastError.current = error
  }, [error, goalId, isNew])

  const cancel = (method: InlineCloseMethod) => {
    track('goal_detail.routine_edit_cancel', { goal_category_id: goalId, is_new: isNew, close_method: method })
    onDone()
  }

  const submit = (method: InlineSubmitMethod) => {
    const defStart = period.start < today ? today : period.start
    const defEnd = period.end < today ? today : period.end
    const conflictCount = routineConflicts(allRoutines.data ?? [], {
      routineId: routine?.routineId,
      name: name.trim(),
      days,
      timeOf,
    }).length
    track('goal_detail.routine_save_click', {
      goal_category_id: goalId,
      is_new: isNew,
      repeat_day_count: days.length,
      has_time: !!startTime && !!endTime,
      per_day_time: overrides.length > 0,
      notification_enabled: notify,
      has_description: !!howTo.trim(),
      has_sub_goal: sub != null,
      period_changed: (start !== period.start && start !== defStart) || (end !== period.end && end !== defEnd),
      has_conflict: conflictCount > 0,
      submit_method: method,
    })
    const body = {
      // 영역 없음 → 새로 만들 땐 null(서버가 "기타 할 일"로), 수정할 땐 "기타 할 일"로 옮기기 (그릇이 없으면 그대로)
      generalCategoryId: sub ?? (routine ? (bucket?.generalCategoryId ?? null) : null),
      routineName: name.trim(),
      repeatStartDate: start,
      repeatEndDate: end,
      repeatDays: days,
      repeatStartTime: startTime || null,
      repeatEndTime: endTime || null,
      notificationEnabled: notify,
      description: howTo.trim(),
      // 수정: 요일별을 끄면 [] → 모든 요일이 기본 시간으로 돌아가요
      dayTimes: overrides,
    }
    const action: RoutineAction = routine ? 'update' : 'create'
    const callbacks = {
      onSuccess: () => {
        trackRoutineResult(goalId, action)
        onDone()
      },
      onError: (e: unknown) => trackRoutineResult(goalId, action, e),
    }
    if (routine) update.mutate({ id: routine.routineId, ...body }, callbacks)
    else create.mutate(body, callbacks)
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={cancel}
      saving={create.isPending || update.isPending}
      valid={valid}
      error={error}
      submitLabel={routine ? '저장' : '추가'}
      extra={
        <button
          type="button"
          onClick={() => {
            track('goal_detail.routine_notify_toggle', {
              goal_category_id: goalId,
              is_new: isNew,
              notification_enabled: !notify,
            })
            setNotify(!notify)
          }}
          aria-pressed={notify}
          className={cn(
            'flex items-center gap-1 rounded-lg px-2 py-1.5 text-[13px] font-semibold',
            notify ? 'text-text-brand hover:bg-brand-soft' : 'text-ink-3 hover:bg-subtle'
          )}
        >
          {notify ? <Bell className="size-3.5" /> : <BellOff className="size-3.5" />}
          알림 {notify ? '켜짐' : '꺼짐'}
        </button>
      }
    >
      <div className={cn('grid gap-2', areas.length > 0 && 'sm:grid-cols-[minmax(0,1fr)_180px]')}>
        <Field label="루틴 이름">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={50}
            placeholder="예: 아침 스트레칭"
            className={inlineInput}
          />
        </Field>
        {/* 영역이 있을 때만 (선택) — 없으면 아예 안 보여요 */}
        {areas.length > 0 && (
          <Field label="세부 목표 (선택)">
            <select
              value={sub ?? ''}
              onChange={(e) => setSub(e.target.value ? Number(e.target.value) : null)}
              className={inlineInput}
            >
              <option value="">세부 목표 없음</option>
              {areas.map((s) => (
                <option key={s.generalCategoryId} value={s.generalCategoryId}>
                  {s.generalCategoryName}
                </option>
              ))}
            </select>
          </Field>
        )}
      </div>
      <Field label="반복 요일">
        <DaysPicker value={days} onChange={setDays} />
      </Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label={perDay ? '기본 시작 시간' : '시작 시간 (선택)'}>
          <input
            type="time"
            step={600}
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            className={inlineInput}
          />
        </Field>
        <Field label={perDay ? '기본 종료 시간' : '종료 시간 (선택)'}>
          <input
            type="time"
            step={600}
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            className={inlineInput}
          />
        </Field>
        {showPeriod && (
          <>
            <Field label="반복 시작일">
              <input type="date" value={start} onChange={(e) => setStart(e.target.value)} className={inlineInput} />
            </Field>
            <Field label="반복 종료일">
              <input type="date" value={end} onChange={(e) => setEnd(e.target.value)} className={inlineInput} />
            </Field>
          </>
        )}
      </div>
      {/* 다른 루틴과 겹치는지 · 내 루틴 주간표 (캘린더에 다녀오지 않아도 돼요) */}
      <RoutineWeekMap
        draft={{ routineId: routine?.routineId, name: name.trim(), days, timeOf }}
        analytics={{ goalId, isNew, routineCount: routineCountOf(goal) }}
        onPick={(d, m) => {
          track('goal_detail.routine_weekmap_pick', {
            goal_category_id: goalId,
            day_of_week: d,
            start_minute: m,
            per_day_time: perDay && days.length > 1,
          })
          // 지금 길이(없으면 1시간)를 그대로 두고 시작 시각만 옮겨요
          const len =
            startTime && endTime ? (timeToMinutes(endTime) - timeToMinutes(startTime) + 1440) % 1440 || 60 : 60
          if (!days.includes(d)) setDays((v) => JAVA_DAYS.filter((x) => x === d || v.includes(x)))
          const st = minutesToTime(m)
          const et = minutesToTime((m + len) % 1440)
          if (perDay && days.length > 1) {
            setDayTimes((map) => ({ ...map, [d]: { start: st, end: et } }))
          } else {
            setStartTime(st)
            setEndTime(et)
          }
        }}
      />
      {/* 요일별 시간 — 기본은 같은 시간, 필요할 때만 펼쳐요 */}
      {days.length > 1 &&
        (perDay ? (
          <div className="bg-subtle/60 space-y-2 rounded-xl p-3">
            <div className="flex items-center justify-between gap-2">
              <p className="text-[13px] font-semibold">요일별 시간</p>
              <button
                type="button"
                onClick={() => {
                  track('goal_detail.routine_per_day_toggle', {
                    goal_category_id: goalId,
                    is_new: isNew,
                    per_day_time: false,
                  })
                  setPerDay(false)
                  setDayTimes({})
                }}
                className="text-ink-3 hover:text-ink text-[12px] font-semibold underline"
              >
                모든 요일 같은 시간으로
              </button>
            </div>
            <ul className="space-y-1.5">
              {shownDays.map((d) => {
                const t = timeOf(d)
                const custom = !!dayTimes[d] && (t.start !== startTime || t.end !== endTime)
                return (
                  <li key={d} className="flex items-center gap-2">
                    <span
                      className={cn(
                        'grid size-8 shrink-0 place-items-center rounded-lg text-[13px] font-bold',
                        custom ? 'bg-brand text-white' : 'bg-surface text-ink-2'
                      )}
                    >
                      {JAVA_DAY_LABEL[d]}
                    </span>
                    <input
                      type="time"
                      step={600}
                      value={t.start}
                      onChange={(e) => setDayTime(d, 'start', e.target.value)}
                      aria-label={`${JAVA_DAY_LABEL[d]}요일 시작`}
                      className={cn(inlineInput, 'h-9 min-w-0 flex-1')}
                    />
                    <span className="text-ink-4">~</span>
                    <input
                      type="time"
                      step={600}
                      value={t.end}
                      onChange={(e) => setDayTime(d, 'end', e.target.value)}
                      aria-label={`${JAVA_DAY_LABEL[d]}요일 종료`}
                      className={cn(inlineInput, 'h-9 min-w-0 flex-1')}
                    />
                    <button
                      type="button"
                      onClick={() => resetDay(d)}
                      disabled={!custom}
                      className="text-ink-3 hover:text-ink w-12 shrink-0 text-[12px] font-semibold disabled:invisible"
                    >
                      기본으로
                    </button>
                  </li>
                )
              })}
            </ul>
            <p className="text-ink-3 text-xs">
              파란 요일만 기본 시간과 달라요. 비워 둔 요일은{' '}
              {startTime && endTime ? `기본 시간(${startTime}~${endTime})` : '자동 배치'}을 따라요.
            </p>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => {
              track('goal_detail.routine_per_day_toggle', {
                goal_category_id: goalId,
                is_new: isNew,
                per_day_time: true,
              })
              setPerDay(true)
            }}
            className="text-text-brand self-start text-[13px] font-semibold"
          >
            + 요일마다 시간을 다르게 하기
          </button>
        ))}
      {!showPeriod && (
        <p className="text-ink-3 text-xs">
          {formatDot(start)} – {formatDot(end)} 동안 반복해요.{' '}
          <button
            type="button"
            onClick={() => {
              track('goal_detail.routine_period_open', { goal_category_id: goalId, is_new: isNew })
              setShowPeriod(true)
            }}
            className="text-ink-2 font-semibold underline"
          >
            기간 바꾸기
          </button>
        </p>
      )}
      <Field label="하는 방법 (선택)">
        <textarea
          value={howTo}
          onChange={(e) => setHowTo(e.target.value)}
          rows={2}
          maxLength={300}
          placeholder="예) 스쿼트 4x10 → 런지 3x12(각), 세트 사이 60초 휴식 · Shift+Enter 로 줄바꿈"
          className={`${inlineInput} h-auto resize-none py-2 leading-relaxed`}
        />
      </Field>
      <p className="text-ink-3 text-xs">
        {startTime ? '정한 시간에 매번 고정돼요.' : '시간을 비워 두면 자동 배치가 빈 시간에 넣어 줘요.'}
        {routine && ' 저장하면 오늘 이후의 루틴 Task가 새 설정으로 바뀌어요 (지난 기록은 그대로).'}
      </p>
    </InlineForm>
  )
}

/** 그 날의 루틴 시간 — 요일별 시간이 있으면 그 시간, 없으면 기본 시간 */
function routineTimeOn(routine: RoutineSummary, ymd: string) {
  const day = JAVA_DAYS[weekdayIndex(ymd)]
  const own = routine.dayTimes?.find((t) => t.dayOfWeek === day)
  return {
    start: hhmm(own?.startTime ?? routine.repeatStartTime) ?? '',
    end: hhmm(own?.endTime ?? routine.repeatEndTime) ?? '',
  }
}

/**
 * 다른 날 추가 — 평일 루틴을 주말에도 했을 때처럼, 반복 요일이 아닌 날에 그날 하루만 루틴 블록을 만들어요.
 * "이미 했어요"면 그 시간을 실제로 한 시간으로 바로 남겨요 (앞날은 예정으로만).
 */
function ExtraDayForm({ goalId, routine, onDone }: { goalId: number; routine: RoutineSummary; onDone: () => void }) {
  const today = todayKst()
  const add = useAddRoutineExtraDay()
  const [date, setDate] = useState(today)
  const [startTime, setStartTime] = useState(() => routineTimeOn(routine, today).start)
  const [endTime, setEndTime] = useState(() => routineTimeOn(routine, today).end)
  const [done, setDone] = useState(true)
  const future = date > today
  const day = date ? JAVA_DAYS[weekdayIndex(date)] : null
  const repeatDay = !!day && routine.repeatDays.includes(day)

  const changeDate = (v: string) => {
    setDate(v)
    if (!v) return
    const t = routineTimeOn(routine, v)
    setStartTime(t.start)
    setEndTime(t.end)
  }

  const error = !date
    ? '날짜를 골라 주세요.'
    : date > addDays(today, 60)
      ? '60일 뒤까지만 추가할 수 있어요.'
      : !!startTime !== !!endTime
        ? '시작·종료 시간을 함께 입력하거나 둘 다 비워 주세요.'
        : startTime && endTime && !isValidRoutineRange(startTime, endTime)
          ? '시작과 종료 시간을 확인해 주세요.'
          : null

  const submit = () => {
    track('goal_detail.routine_extra_day_save', {
      goal_category_id: goalId,
      routine_id: routine.routineId,
      days_from_today: daysBetween(today, date),
      marked_done: done && !future,
      is_repeat_day: repeatDay,
      has_time: !!startTime && !!endTime,
    })
    add.mutate(
      {
        routineId: routine.routineId,
        date,
        startTime: startTime || null,
        endTime: endTime || null,
        done: done && !future,
      },
      {
        onSuccess: () => {
          trackRoutineResult(goalId, 'extra_day')
          onDone()
        },
        onError: (e) => trackRoutineResult(goalId, 'extra_day', e),
      }
    )
  }

  const cancel = (method: InlineCloseMethod) => {
    track('goal_detail.routine_extra_day_cancel', {
      goal_category_id: goalId,
      routine_id: routine.routineId,
      close_method: method,
    })
    onDone()
  }

  return (
    <InlineForm
      onSubmit={submit}
      onCancel={cancel}
      saving={add.isPending}
      valid={!error}
      error={date ? error : null}
      submitLabel={done && !future ? '한 걸로 남기기' : '그날 추가'}
    >
      <p className="text-[14px] font-semibold">&lsquo;{routine.routineName}&rsquo;를 다른 날에도 했어요</p>
      <p className="text-ink-3 -mt-1 text-xs">
        반복 요일은 그대로 두고, 고른 날 하루만 루틴 블록이 생겨요. 실천 기록에도 함께 들어가요.
      </p>
      <div className="grid grid-cols-2 gap-3">
        <Field label="날짜" className="col-span-2">
          <input
            type="date"
            value={date}
            max={addDays(today, 60)}
            onChange={(e) => changeDate(e.target.value)}
            className={inlineInput}
            data-autofocus
          />
        </Field>
        <Field label="시작">
          <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className={inlineInput} />
        </Field>
        <Field label="종료">
          <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className={inlineInput} />
        </Field>
      </div>
      {repeatDay && (
        <p className="text-warning text-xs">
          {JAVA_DAY_LABEL[day!]}요일은 원래 하는 날이에요. 이미 블록이 있으면 캘린더에서 그 블록을 완료해 주세요.
        </p>
      )}
      <label className={cn('flex items-center gap-2 text-[14px]', future && 'opacity-50')}>
        <input
          type="checkbox"
          checked={done && !future}
          disabled={future}
          onChange={(e) => setDone(e.target.checked)}
          className="size-4"
        />
        이미 했어요 {future ? '(앞날은 예정으로만 넣어요)' : startTime ? `— ${startTime}~${endTime} 에 한 걸로` : ''}
      </label>
    </InlineForm>
  )
}

function RoutineRow({
  goal,
  routine,
  subGoal,
  subGoalId,
}: {
  goal: GoalCategory
  routine: RoutineSummary
  subGoal: string
  subGoalId: number
}) {
  const key = `routine:${routine.routineId}`
  const edit = useEditing(key)
  const del = useEditing(`del:${key}`)
  const extra = useEditing(`extra:${key}`)
  const remove = useDeleteRoutine()
  const start = hhmm(routine.repeatStartTime)
  const active = routine.routineStatus !== 'STOPPED' && routine.routineStatus !== 'DELETED'
  const goalId = goal.goalCategoryId
  // 실제로 가장 많이 한 시간 (히트맵 API · 같은 쿼리 캐시를 써요)
  const heat = useRoutineHeatmap(goalId).data?.find((h) => h.routineId === routine.routineId)
  const practiceLine = (() => {
    const t = heat?.timeStats
    if (!t || t.practiceCount === 0) return null
    const days = byDayLine(t)
    return days ? `${days}에 가장 많이 실천했어요` : `주로 ${t.bestStart}~${t.bestEnd}에 실천했어요`
  })()
  const ids = { goal_category_id: goalId, routine_id: routine.routineId }
  useTrackOtherOpen(edit.isOpen, edit.current, goalId, false)
  const openEdit = (entryPoint: 'row' | 'icon') => {
    track('goal_detail.routine_edit_open', { ...ids, entry_point: entryPoint })
    edit.open()
  }

  if (extra.isOpen) {
    return (
      <li className="py-2">
        <ExtraDayForm goalId={goalId} routine={routine} onDone={extra.close} />
      </li>
    )
  }

  if (edit.isOpen) {
    return (
      <li className="py-2">
        <RoutineForm goal={goal} routine={routine} subGoalId={subGoalId} onDone={edit.close} />
      </li>
    )
  }
  if (del.isOpen) {
    return (
      <li className="py-2">
        <InlineConfirm
          message={`'${routine.routineName}' 루틴을 삭제할까요?`}
          detail="이 루틴으로 만들어진 Task도 함께 삭제돼요."
          loading={remove.isPending}
          onCancel={() => {
            track('goal_detail.routine_delete_cancel', ids)
            del.close()
          }}
          onConfirm={() => {
            track('goal_detail.routine_delete_confirm', ids)
            remove.mutate(routine.routineId, {
              onSuccess: () => {
                trackRoutineResult(goalId, 'delete')
                del.close()
              },
              onError: (e) => trackRoutineResult(goalId, 'delete', e),
            })
          }}
        />
      </li>
    )
  }
  return (
    <li className="group flex flex-wrap items-center gap-x-4 gap-y-2 py-3.5">
      <button type="button" onClick={() => openEdit('row')} className="min-w-0 flex-1 basis-[55%] text-left">
        <p className="flex items-center gap-1.5 text-[15px] font-semibold">
          {routine.routineName}
          {routine.notificationEnabled && <Bell className="text-ink-3 size-3.5" aria-label="알림 켜짐" />}
        </p>
        {/* 영역은 부모가 아니라 라벨 — "기타 할 일"이면 안 보여줘요 */}
        {subGoal && (
          <p className="mt-1">
            <span className="bg-subtle text-ink-2 rounded-full px-2 py-0.5 text-[12px] font-medium">{subGoal}</span>
          </p>
        )}
        {/* 계획한 시간 — 실천 통계와 헷갈리지 않게 "계획"을 붙여요 */}
        <p className="text-ink-3 mt-1 text-[13px]">
          <span className="text-ink-2 font-medium">계획</span> · {routineTimeText(routine)}
          {!start && !routine.dayTimes?.length && routine.durationMinutes ? ` · ${routine.durationMinutes}분` : ''}
        </p>
        {practiceLine && <p className="text-text-brand mt-0.5 text-[12px] font-medium">{practiceLine}</p>}
        {routine.description && (
          <p className="bg-subtle text-ink-2 mt-2 rounded-lg px-2.5 py-1.5 text-[13px] leading-relaxed whitespace-pre-wrap">
            {routine.description}
          </p>
        )}
      </button>
      {/* 좁은 화면에선 요일을 아랫줄로 */}
      <div className="order-3 w-full sm:order-none sm:w-auto">
        <DayChips days={routine.repeatDays} />
      </div>
      {active && (
        <button
          type="button"
          onClick={() => {
            track('goal_detail.routine_extra_day_click', ids)
            extra.open()
          }}
          title="반복 요일이 아닌 날에도 했다면 그날을 추가해요"
          className="text-ink-3 hover:text-ink border-line hover:border-line-strong order-4 flex h-7 items-center gap-1 rounded-full border px-2.5 text-[12px] font-medium sm:order-none"
        >
          <CalendarPlus className="size-3.5" />
          다른 날 추가
        </button>
      )}
      <RowActions
        label={routine.routineName}
        onEdit={() => openEdit('icon')}
        onDelete={() => {
          track('goal_detail.routine_delete_click', ids)
          del.open()
        }}
      />
    </li>
  )
}

/** 루틴 — 줄을 누르면 그 자리에서 수정, 아래 점선 버튼으로 추가 */
export function RoutinesSection({ goal }: { goal: GoalCategory }) {
  const add = useEditing('routine:new')
  useTrackOtherOpen(add.isOpen, add.current, goal.goalCategoryId, true)
  const rows = activeSubGoals(goal).flatMap((sg) =>
    sg.routines.map((r) => ({
      routine: r,
      subGoal: sg.defaultBucket ? '' : sg.generalCategoryName,
      subGoalId: sg.generalCategoryId,
    }))
  )

  return (
    <Section title="루틴" aside={`${rows.length}개`}>
      {rows.length > 0 && (
        <ul className="divide-line divide-y">
          {rows.map(({ routine, subGoal, subGoalId }) => (
            <RoutineRow key={routine.routineId} goal={goal} routine={routine} subGoal={subGoal} subGoalId={subGoalId} />
          ))}
        </ul>
      )}
      <div className={rows.length ? 'mt-3' : ''}>
        {add.isOpen ? (
          <RoutineForm goal={goal} onDone={add.close} />
        ) : (
          <AddButton
            onClick={() => {
              track('goal_detail.routine_add_click', {
                goal_category_id: goal.goalCategoryId,
                routine_count: rows.length,
              })
              add.open()
            }}
          >
            루틴 추가
          </AddButton>
        )}
      </div>
    </Section>
  )
}
