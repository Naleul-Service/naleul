'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { Pencil, Plus, Sparkles, Trash2 } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { Badge, Chip } from '@/components/ui/Chip'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Spinner } from '@/components/ui/Spinner'
import { errorStatusOf } from '@/features/home/analytics'
import { useLifePatterns } from '@/features/timetable/api'
import { formatDuration, timeToMinutes } from '@/features/timetable/time'
import type { LifePattern } from '@/features/timetable/types'
import { track } from '@/lib/client/analytics'
import { referrerType } from '@/lib/client/commonEvents'
import { useCreateLifePattern, useDeleteLifePattern, useUpdateLifePattern } from '../api'
import { PRESETS, TYPE_LABEL, existingOfType, formatDays, toHm, type LifePatternPreset } from '../presets'
import { LifePatternFormModal, presetToForm, type LifePatternFormValue } from './LifePatternFormModal'
import { WeeklyOverview } from './WeeklyOverview'

/** 백엔드 LifePatternService.MAX_PATTERNS */
const MAX_PATTERNS = 10
const EMPTY_PATTERNS: LifePattern[] = []

type FormState = { mode: 'create'; initial: LifePatternFormValue | null } | { mode: 'edit'; pattern: LifePattern }

/** life_pattern.edit_click 의 entry_point — 연필 아이콘 · 한 주 미리보기 막대 · 페이지 추천 칩 · 모달 안 추천 칩 */
type EditEntryPoint = 'icon' | 'weekly_overview' | 'preset' | 'suggestion'

const durationOf = (p: LifePattern) => {
  const s = timeToMinutes(p.startTime)
  let e = timeToMinutes(p.endTime)
  if (e <= s) e += 1440
  return formatDuration(e - s)
}

/**
 * /settings/life-pattern — 기본 생활 패턴(고정 시간) 한 화면에서 추가·수정·삭제.
 * 여기서 바꾸면 "오늘부터 모든 날짜"의 기본값이 바뀌어요. (그날만 바꾸기는 캘린더에서)
 */
export function LifePatternView() {
  const { data, isLoading, isError, error, errorUpdatedAt, refetch } = useLifePatterns()
  const patterns = data ?? EMPTY_PATTERNS
  const create = useCreateLifePattern()
  const update = useUpdateLifePattern()
  const remove = useDeleteLifePattern()

  const [form, setForm] = useState<FormState | null>(null)
  // 모달을 열 때마다 새로 그리기 위한 key
  const [formKey, setFormKey] = useState(0)
  const [deleting, setDeleting] = useState<LifePattern | null>(null)

  const full = patterns.length >= MAX_PATTERNS

  // ── 분석 ──
  // life_pattern.pageview — 목록을 불러온 직후 한 번 (실패 후 '다시 시도'로 불러와도 그때 한 번)
  const pageviewSent = useRef(false)
  useEffect(() => {
    if (!data || pageviewSent.current) return
    pageviewSent.current = true
    track('life_pattern.pageview', {
      pattern_count: data.length,
      has_sleep: data.some((p) => p.patternType === 'SLEEP'),
      referrer_type: referrerType(),
    })
  }, [data])

  // life_pattern.load_fail — 실패 카드가 보일 때 (다시 시도 후 또 실패하면 다시)
  const loadFailSentAt = useRef(0)
  useEffect(() => {
    if (!isError || loadFailSentAt.current === errorUpdatedAt) return
    loadFailSentAt.current = errorUpdatedAt
    track('life_pattern.load_fail', { error_status: errorStatusOf(error) })
  }, [isError, error, errorUpdatedAt])

  // life_pattern.limit_view — 10개라 추가 버튼·추천 칩이 꺼질 때 (꺼지는 순간마다 한 번)
  const limitShown = useRef(false)
  const limited = !isLoading && !isError && full
  useEffect(() => {
    if (!limited) {
      limitShown.current = false
      return
    }
    if (limitShown.current) return
    limitShown.current = true
    track('life_pattern.limit_view', { pattern_count: patterns.length })
  }, [limited, patterns.length])

  const openForm = (next: FormState) => {
    setFormKey((k) => k + 1)
    setForm(next)
  }

  const openEdit = (pattern: LifePattern, entry: EditEntryPoint) => {
    track('life_pattern.edit_click', { pattern_type: pattern.patternType, entry_point: entry })
    openForm({ mode: 'edit', pattern })
  }

  const openPreset = (preset: LifePatternPreset) => {
    const existing = existingOfType(patterns, preset)
    track('life_pattern.preset_click', {
      pattern_type: preset.patternType,
      is_new: !existing,
      pattern_count: patterns.length,
    })
    if (existing) openEdit(existing, 'preset')
    else openForm({ mode: 'create', initial: presetToForm(preset) })
  }

  if (isLoading) {
    return (
      <div className="grid min-h-[420px] place-items-center">
        <Spinner className="text-ink-3 size-6" />
      </div>
    )
  }

  return (
    <>
      <PageHeader
        breadcrumb={<Link href="/settings">설정</Link>}
        title="기본 생활 패턴"
        description="매주 반복되는 고정 시간이에요. 이 시간은 비워두고 Task를 배치해요."
        actions={
          <Button
            onClick={() => {
              track('life_pattern.add_click', { pattern_count: patterns.length })
              openForm({ mode: 'create', initial: null })
            }}
            disabled={full || isError}
            title={full ? `고정 시간은 최대 ${MAX_PATTERNS}개까지 만들 수 있어요` : undefined}
          >
            <Plus className="size-4" />
            고정 시간 추가
          </Button>
        }
      />

      {isError ? (
        <Card className="mt-6 grid min-h-[240px] place-items-center p-10 text-center">
          <div>
            <p className="font-bold">고정 시간을 불러오지 못했어요</p>
            <Button
              variant="secondary"
              className="mt-4"
              onClick={() => {
                track('life_pattern.retry_click', { error_status: errorStatusOf(error) })
                refetch()
              }}
            >
              다시 시도
            </Button>
          </div>
        </Card>
      ) : (
        <div className="mt-6 space-y-4">
          {/* 추천 */}
          <Card className="p-5">
            <p className="flex items-center gap-1.5 text-sm font-bold">
              <Sparkles className="text-text-brand size-4" />
              추천으로 빠르게 추가
            </p>
            <div className="mt-3 flex flex-wrap gap-1.5">
              {PRESETS.map((p) => {
                const existing = existingOfType(patterns, p)
                return (
                  <Chip
                    key={p.key}
                    size="sm"
                    onClick={() => openPreset(p)}
                    disabled={!existing && full}
                    title={existing ? `이미 있는 ${p.label}을 수정해요` : `${p.startTime}–${p.endTime}로 채워요`}
                  >
                    {existing ? `${p.emoji} ${p.label} 수정` : `+ ${p.label}`}
                  </Chip>
                )
              })}
            </div>
          </Card>

          {/* 목록 */}
          <Card className="p-5">
            <CardHeader title="고정 시간" aside={`${patterns.length} / ${MAX_PATTERNS}`} />
            {patterns.length === 0 ? (
              <div className="text-ink-3 py-10 text-center text-sm">
                등록된 고정 시간이 없어요. 위 추천이나 <b className="text-ink-2">고정 시간 추가</b>로 만들어 보세요.
              </div>
            ) : (
              <ul className="divide-line mt-3 divide-y">
                {patterns.map((p) => (
                  <li key={p.lifePatternId} className="flex items-center gap-3 py-3">
                    <span className="bg-subtle grid size-10 shrink-0 place-items-center rounded-xl text-lg">
                      {p.emoji || '📌'}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <p className="truncate text-[15px] font-bold">{p.title}</p>
                        {p.patternType !== 'CUSTOM' && <Badge>{TYPE_LABEL[p.patternType]}</Badge>}
                      </div>
                      <p className="text-ink-3 mt-0.5 text-[13px]">
                        {toHm(p.startTime)} – {toHm(p.endTime)}
                        {p.crossesMidnight && ' (다음 날)'} · {durationOf(p)} ·{' '}
                        <span className="text-ink-2 font-medium">
                          {p.onlyDate
                            ? `${Number(p.onlyDate.slice(5, 7))}/${Number(p.onlyDate.slice(8, 10))} 하루만`
                            : formatDays(p.days)}
                        </span>
                        {!!p.dayTimes?.length && (
                          <span className="text-text-brand font-medium"> · 요일별 시간 {p.dayTimes.length}일</span>
                        )}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => openEdit(p, 'icon')}
                      aria-label={`${p.title} 수정`}
                      className="text-ink-3 hover:bg-subtle hover:text-ink rounded-lg p-2"
                    >
                      <Pencil className="size-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        track('life_pattern.delete_click', { pattern_type: p.patternType })
                        setDeleting(p)
                      }}
                      aria-label={`${p.title} 삭제`}
                      className="text-ink-3 hover:bg-danger-soft hover:text-danger rounded-lg p-2"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {/* 한 주 미리보기 */}
          {patterns.length > 0 && (
            <WeeklyOverview patterns={patterns} onSelect={(p) => openEdit(p, 'weekly_overview')} />
          )}

          <p className="text-ink-4 text-center text-xs">
            특정 날짜만 바꾸거나 비우려면 캘린더에서 고정 시간 블록을 눌러 주세요.
          </p>
        </div>
      )}

      {form && (
        <LifePatternFormModal
          key={formKey}
          editing={form.mode === 'edit' ? form.pattern : null}
          initial={form.mode === 'create' ? form.initial : null}
          patterns={patterns}
          loading={create.isPending || update.isPending}
          onEditExisting={(p) => openEdit(p, 'suggestion')}
          onClose={() => setForm(null)}
          onSubmit={(v) => {
            const isNew = form.mode === 'create'
            const patternType = form.mode === 'edit' ? form.pattern.patternType : v.patternType
            const done = {
              onSuccess: () => {
                track('life_pattern.save_result', { is_new: isNew, result: 'success', pattern_type: patternType })
                setForm(null)
              },
              onError: (err: unknown) =>
                track('life_pattern.save_result', {
                  is_new: isNew,
                  result: 'fail',
                  error_status: errorStatusOf(err),
                  pattern_type: patternType,
                }),
            }
            if (form.mode === 'edit') {
              const { patternType, ...body } = v
              void patternType // 종류는 수정 API 에 보내지 않아요
              update.mutate({ lifePatternId: form.pattern.lifePatternId, ...body }, done)
            } else {
              create.mutate(v, done)
            }
          }}
        />
      )}

      <ConfirmDialog
        open={!!deleting}
        title={`'${deleting?.title ?? ''}'을 삭제할까요?`}
        description={
          deleting?.patternType === 'SLEEP'
            ? '수면 시간이 없으면 하루 범위를 00:00~24:00으로 보고 Task를 배치해요.'
            : '오늘부터 이 시간에도 Task가 배치될 수 있어요.'
        }
        confirmLabel="삭제"
        tone="danger"
        loading={remove.isPending}
        onCancel={(method) => {
          if (deleting)
            track('life_pattern.delete_cancel', { pattern_type: deleting.patternType, close_method: method })
          setDeleting(null)
        }}
        onConfirm={() => {
          if (!deleting) return
          const patternType = deleting.patternType
          track('life_pattern.delete_confirm', { pattern_type: patternType })
          remove.mutate(deleting.lifePatternId, {
            onSuccess: () => {
              track('life_pattern.delete_result', { result: 'success', pattern_type: patternType })
              setDeleting(null)
            },
            onError: (err) =>
              track('life_pattern.delete_result', {
                result: 'fail',
                error_status: errorStatusOf(err),
                pattern_type: patternType,
              }),
          })
        }}
      />
    </>
  )
}
