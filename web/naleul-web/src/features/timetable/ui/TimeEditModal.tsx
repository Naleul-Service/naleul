'use client'

import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import type { TimetableSurface } from '../analytics'
import type { ChangeScope } from '../api'
import type { FixedBlock } from '../types'
import { WEEKDAY_LABEL, formatDuration, isYmd, minutesToTime, timeToMinutes, weekdayIndex } from '../time'
import { ScopeChips, scopeOptions } from './ScopeChooser'

export interface TimeValue {
  date: string
  /** 그 날짜 0시 기준 분. end 가 1440 이상이면 다음 날 */
  start: number
  end: number
}

interface Props {
  title: string
  description?: string
  initial: TimeValue
  /** false 면 날짜는 고정 (고정 시간 "이날만 변경") */
  dateEditable: boolean
  loading?: boolean
  /** 루틴·고정 시간이면 적용 범위(이날만 / 이번 주 / 앞으로)를 함께 골라요 */
  scope?: { kind: 'routine' | 'fixed'; today: string }
  onSubmit: (v: TimeValue, scope: ChangeScope) => void
  onClose: () => void
  /** Amplitude surface — 이 창을 띄운 화면 */
  surface?: TimetableSurface
  /** 고정 시간을 고칠 때 그 종류 (fixed_detail.time_save · result 에 실어요) */
  patternType?: FixedBlock['patternType']
}

const inputClass =
  'border-border-subtle focus:border-border-focus h-11 w-full rounded-input border bg-surface px-3 text-[15px] outline-none'

/**
 * 시간 직접 입력 (드래그 대신 쓰는 방법).
 * 종료가 시작보다 이르면 다음 날로 넘어가는 것으로 봐요 (예: 23:00 ~ 01:00).
 * 부모에서 key 를 바꿔 열 때마다 초기값으로 다시 그려요.
 */
export function TimeEditModal({
  title,
  description,
  initial,
  dateEditable,
  loading,
  scope,
  onSubmit,
  onClose,
  surface,
  patternType,
}: Props) {
  const qc = useQueryClient()
  /** 고정 시간 저장을 눌러 요청을 기다리는 중인지 (끝나면 fixed_detail.result) */
  const submittedRef = useRef(false)
  const isFixed = scope?.kind === 'fixed'
  // 요청은 훅(usePatternScopedChange)이 보내요 — 성공하면 창이 닫히므로 mutation cache 에서 결과를 바로 받아요
  useEffect(() => {
    if (!isFixed) return
    return qc.getMutationCache().subscribe((ev) => {
      if (ev.type !== 'updated' || !submittedRef.current) return
      const v = ev.mutation.state.variables as { lifePatternId?: number; scope?: string } | undefined
      if (v?.lifePatternId === undefined || !v.scope) return
      if (ev.action.type === 'success') {
        submittedRef.current = false
        track('fixed_detail.result', { surface, fixed_action: 'time', result: 'success' })
      } else if (ev.action.type === 'error') {
        submittedRef.current = false
        track('fixed_detail.result', {
          surface,
          fixed_action: 'time',
          result: 'fail',
          error_status: errorStatusOf(ev.action.error),
        })
      }
    })
  }, [qc, isFixed, surface])

  const [date, setDate] = useState(initial.date)
  const [chosen, setChosen] = useState<ChangeScope>('DAY')
  const [start, setStart] = useState(minutesToTime(initial.start))
  const [end, setEnd] = useState(minutesToTime(initial.end))

  const s = start ? timeToMinutes(start) : NaN
  let e = end ? timeToMinutes(end) : NaN
  if (e <= s) e += 1440
  const valid = isYmd(date) && !Number.isNaN(s) && !Number.isNaN(e) && e - s > 0 && e - s <= 1440 && e !== s + 1440
  const crosses = valid && e >= 1440
  const options = scope
    ? scopeOptions({
        kind: scope.kind,
        isToday: date === scope.today,
        sameDay: date === initial.date,
        crossesMidnight: crosses,
        weekdayLabel: scope.kind === 'fixed' && isYmd(date) ? `${WEEKDAY_LABEL[weekdayIndex(date)]}요일` : undefined,
      })
    : null
  // 고를 수 없게 된 범위를 골라 둔 상태면 이날만으로
  const effectiveScope = options?.find((o) => o.scope === chosen && !o.disabledReason) ? chosen : 'DAY'
  const submit = () => {
    if (isFixed) {
      track('fixed_detail.time_save', {
        surface,
        pattern_type: patternType,
        change_scope: effectiveScope,
        crosses_midnight: e > 1440,
      })
      submittedRef.current = true
    }
    onSubmit({ date, start: s, end: e }, effectiveScope)
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={title}
      description={description}
      size="sm"
      dismissible={!loading}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            취소
          </Button>
          <Button onClick={submit} disabled={!valid} loading={loading}>
            저장
          </Button>
        </>
      }
    >
      <form
        className="space-y-3"
        onSubmit={(ev) => {
          ev.preventDefault()
          if (valid && !loading) submit()
        }}
      >
        {dateEditable && (
          <label className="block">
            <span className="text-ink-3 mb-1 block text-xs font-medium">날짜</span>
            <input
              type="date"
              value={date}
              onChange={(ev) => setDate(ev.target.value)}
              className={inputClass}
              required
            />
          </label>
        )}
        <div className="grid grid-cols-2 gap-2">
          <label className="block">
            <span className="text-ink-3 mb-1 block text-xs font-medium">시작</span>
            <input
              type="time"
              step={600}
              value={start}
              onChange={(ev) => setStart(ev.target.value)}
              className={inputClass}
              required
              aria-label="시작 시각"
            />
          </label>
          <label className="block">
            <span className="text-ink-3 mb-1 block text-xs font-medium">종료</span>
            <input
              type="time"
              step={600}
              value={end}
              onChange={(ev) => setEnd(ev.target.value)}
              className={inputClass}
              required
              aria-label="종료 시각"
            />
          </label>
        </div>
        <p className="text-ink-3 min-h-5 text-xs">
          {!valid
            ? '시작과 종료 시각을 다르게 입력해 주세요.'
            : `${formatDuration(e - s)}${crosses ? ' · 다음 날까지 이어져요' : ''}`}
        </p>
        {options && <ScopeChips options={options} value={effectiveScope} onChange={setChosen} />}
        {/* Enter 로 저장 */}
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}
