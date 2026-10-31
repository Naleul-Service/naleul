'use client'

import { useMemo, useState } from 'react'
import { X } from 'lucide-react'
import { Chip } from '@/components/ui/Chip'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useChatReview, useChatReviewItems, type ChatReviewFilter } from '../api'
import type { ChatReview, ChatReviewField, ChatReviewFieldRow } from '../types'
import { dateTimeLabel, num, pct } from '../format'
import { Empty, Panel, Stat } from './Panel'

export const GOAL_TYPES = [
  { value: '', label: '카테고리 전체' },
  { value: 'HEALTH', label: '건강' },
  { value: 'LEARNING', label: '학습·자기개발' },
  { value: 'HABIT', label: '생활습관' },
  { value: 'ETC', label: '기타' },
  { value: 'WORK', label: '업무' },
]

const SOURCE_LABEL = { CHIP: '칩', TYPED: '직접', SKIP: '건너뜀' } as const

export const selectClass =
  'border-border-subtle bg-surface h-8 rounded-full border px-3 text-[12px] font-semibold outline-none focus:border-border-focus'

/**
 * AI 채팅 정리 정확도 (운영진 전용, V67) — 사용자에게는 보이지 않아요.
 * 기준: 대화가 끝난 순간의 AI 정리값 vs 처음 "계획 만들기"를 누른 순간의 값. 정리 화면에서 바뀐 것만 세요.
 * 수정률 · 누락률 · 진행률을 전체(세션)와 항목별 두 단계로. 행을 누르면 그 항목의 고침 · 직접 채움 세션을 전부 봐요.
 */
export function ChatReviewPanel({ days }: { days: number }) {
  const [filter, setFilter] = useState<ChatReviewFilter>({})
  const [open, setOpen] = useState<ChatReviewField | null>(null)
  const { data, isPending, isError, isFetching } = useChatReview(days, filter)

  return (
    <Panel
      title="AI 채팅 정리 정확도"
      aside={
        data ? `정리 화면 · 첫 "계획 만들기" 기준 · ${num(data.sessions)}세션` : '정리 화면 · 첫 "계획 만들기" 기준'
      }
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <select
          aria-label="카테고리"
          className={selectClass}
          value={filter.goalType ?? ''}
          onChange={(e) => setFilter((f) => ({ ...f, goalType: e.target.value || undefined }))}
        >
          {GOAL_TYPES.map((g) => (
            <option key={g.value} value={g.value}>
              {g.label}
            </option>
          ))}
        </select>
        {([undefined, 'CHIP', 'TYPED'] as const).map((s) => (
          <Chip
            key={s ?? 'all'}
            size="sm"
            selected={filter.answerSource === s}
            onClick={() => setFilter((f) => ({ ...f, answerSource: s }))}
          >
            {s ? `${SOURCE_LABEL[s]}으로 답한 항목` : '답 방식 전체'}
          </Chip>
        ))}
        <select
          aria-label="프롬프트 버전"
          className={selectClass}
          value={filter.promptVersion ?? ''}
          onChange={(e) => setFilter((f) => ({ ...f, promptVersion: e.target.value || undefined }))}
        >
          <option value="">프롬프트 버전 전체</option>
          {(data?.promptVersions ?? []).map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
        {isFetching && <Spinner className="text-ink-3 size-3.5" />}
      </div>

      {isPending ? (
        <div className="grid min-h-[160px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError || !data ? (
        <Empty>정리 정확도를 불러오지 못했어요</Empty>
      ) : data.sessions === 0 && data.exit.entered === 0 ? (
        <Empty>기간 안에 정리 화면에 들어온 세션이 없어요 (배포 이후 대화부터 쌓여요)</Empty>
      ) : (
        <>
          {data.sessions === 0 ? (
            <Empty>정리 화면에서 계획 만들기를 누른 세션이 없어요 — 아래 이탈률을 확인해 주세요</Empty>
          ) : (
            <>
              <Overall data={data} />
              <FieldTable data={data} open={open} onOpen={(f) => setOpen((cur) => (cur === f ? null : f))} />
              <p className="text-ink-3 mt-2 text-[11px] leading-relaxed">
                수정률 = 고친 세션 ÷ AI 가 채운 세션 · 진행률 = 그대로 둔 세션 ÷ AI 가 채운 세션 · 칩/직접으로 답한 비율
                = 그 항목을 물은 질문에 어떻게 답했는지 · 누락률 = 직접 채운 세션 ÷ AI 가 비운 세션 (이유는 빈 줄이 안
                보이고 실행 성향은 항상 골라져 있어 제외). 빨간 숫자는 상위 2개, 표본 {data.minSample}세션 미만은
                흐리게.
              </p>
            </>
          )}
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <div className="border-line rounded-xl border p-4">
              <Stat
                label="세션당 평균 수정 항목 수"
                value={data.overall.avgEditedItems != null ? `${data.overall.avgEditedItems}개` : '–'}
                sub="고친 항목 수 합계 ÷ 1개 이상 고친 세션"
              />
            </div>
            <div className="border-line rounded-xl border p-4">
              <Stat
                label="정리 화면 이탈률"
                value={pct(data.exit.exitRate)}
                sub={`계획 만들기 없이 끝남 ${num(data.exit.exited)} ÷ 진입 ${num(data.exit.entered - data.exit.pending)} (아직 머무는 ${num(data.exit.pending)} 제외)`}
              />
            </div>
          </div>
          {open && data.sessions > 0 && (
            <ItemDetail
              key={`${open}|${days}|${JSON.stringify(filter)}`}
              field={open}
              days={days}
              filter={filter}
              onClose={() => setOpen(null)}
            />
          )}
        </>
      )}
    </Panel>
  )
}

function Overall({ data }: { data: ChatReview }) {
  const o = data.overall
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="border-brand bg-brand-soft rounded-xl border p-4">
        <Stat
          label={<span className="text-text-brand font-semibold">전체 진행률 (대표)</span>}
          value={<span className="text-text-brand">{pct(o.progressRate)}</span>}
          sub={`안 고치고 · 직접 채우지도 않음 ${num(o.untouchedSessions)}세션`}
        />
      </div>
      <div className="border-line rounded-xl border p-4">
        <Stat label="전체 수정률" value={pct(o.editRate)} sub={`7개 중 1개 이상 고침 ${num(o.editedSessions)}세션`} />
      </div>
      <div className="border-line rounded-xl border p-4">
        <Stat
          label="전체 누락률"
          value={pct(o.missRate)}
          sub={`5개 중 1개 이상 직접 채움 ${num(o.addedSessions)}세션`}
        />
      </div>
    </div>
  )
}

/** 상위 2개 (표본이 충분한 항목만) */
function topKeys(
  rows: ChatReviewFieldRow[],
  rate: (r: ChatReviewFieldRow) => number | null,
  base: (r: ChatReviewFieldRow) => number,
  min: number
) {
  return new Set(
    rows
      .filter((r) => rate(r) != null && (rate(r) ?? 0) > 0 && base(r) >= min)
      .sort((a, b) => (rate(b) ?? 0) - (rate(a) ?? 0))
      .slice(0, 2)
      .map((r) => r.key)
  )
}

function FieldTable({
  data,
  open,
  onOpen,
}: {
  data: ChatReview
  open: ChatReviewField | null
  onOpen: (f: ChatReviewField) => void
}) {
  const min = data.minSample
  const hotEdit = useMemo(
    () =>
      topKeys(
        data.fields,
        (r) => r.editRate,
        (r) => r.aiFilled,
        min
      ),
    [data.fields, min]
  )
  const hotMiss = useMemo(
    () =>
      topKeys(
        data.fields,
        (r) => r.missRate,
        (r) => r.emptyBase,
        min
      ),
    [data.fields, min]
  )
  return (
    <div className="-mx-1 mt-4 overflow-x-auto">
      <table className="w-full min-w-[720px] text-[13px] tabular-nums">
        <thead>
          <tr className="text-ink-3 text-left text-[12px]">
            <th className="px-2 pb-2 font-medium">항목</th>
            <th className="px-2 pb-2 font-medium">AI가 채운 세션</th>
            <th className="px-2 pb-2 font-medium">수정률</th>
            <th className="px-2 pb-2 font-medium">칩으로 답한 비율</th>
            <th className="px-2 pb-2 font-medium">직접 답한 비율</th>
            <th className="px-2 pb-2 font-medium">누락률</th>
            <th className="px-2 pb-2 font-medium">진행률</th>
            <th className="w-6" />
          </tr>
        </thead>
        <tbody className="divide-line divide-y">
          {data.fields.map((r) => {
            const fewFilled = r.aiFilled < min
            const fewEmpty = r.emptyBase < min
            return (
              <tr
                key={r.key}
                onClick={() => onOpen(r.key)}
                className={cn('hover:bg-subtle/60 cursor-pointer', open === r.key && 'bg-brand-soft/60')}
                aria-selected={open === r.key}
              >
                <td className="px-2 py-3 font-semibold">{r.label}</td>
                <td className="px-2 py-3">{num(r.aiFilled)}</td>
                <td
                  className={cn('px-2 py-3', fewFilled && 'text-ink-4', hotEdit.has(r.key) && 'text-danger font-bold')}
                >
                  {pct(r.editRate)}
                </td>
                <td className="px-2 py-3">{pct(r.chipRate)}</td>
                <td className="px-2 py-3">{pct(r.typedRate)}</td>
                <td
                  className={cn('px-2 py-3', fewEmpty && 'text-ink-4', hotMiss.has(r.key) && 'text-danger font-bold')}
                >
                  {pct(r.missRate)}
                </td>
                <td className={cn('px-2 py-3', fewFilled && 'text-ink-4')}>{pct(r.progressRate)}</td>
                <td className="text-ink-4 px-1 py-3 text-right">›</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/** 항목 상세 — 그 항목을 고치거나 직접 채운 세션 전부 (최근 몇 개만 보여주지 않아요) */
function ItemDetail({
  field,
  days,
  filter,
  onClose,
}: {
  field: ChatReviewField
  days: number
  filter: ChatReviewFilter
  onClose: () => void
}) {
  const [result, setResult] = useState<'CHANGED' | 'EDITED' | 'ADDED'>('CHANGED')
  const [page, setPage] = useState(0)
  const { data, isPending, isFetching } = useChatReviewItems(field, days, filter, result, page)
  const s = data?.stats
  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1
  const pick = (r: typeof result) => {
    setResult(r)
    setPage(0)
  }

  return (
    <section className="border-brand mt-5 rounded-2xl border-[1.5px] p-4 sm:p-5" aria-label="항목 상세">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-bold">항목 상세 · {s?.label ?? ''}</h3>
          <p className="text-ink-3 mt-0.5 text-[12px]">
            기간 안에서 이 항목을 고치거나 직접 채운 세션 전부 · 개인 식별 정보 제외 · 답 원문은 6개월 보관
          </p>
        </div>
        <button type="button" onClick={onClose} className="text-ink-3 hover:text-ink" aria-label="항목 상세 닫기">
          <X className="size-4" />
        </button>
      </div>

      {s && (
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {[
            ['수정률', `${pct(s.editRate)} (${num(s.edited)}/${num(s.aiFilled)})`],
            ['누락률', s.missRate == null ? '–' : `${pct(s.missRate)} (${num(s.added)}/${num(s.emptyBase)})`],
            ['칩으로 답한 비율', pct(s.chipRate)],
            ['직접 답한 비율', pct(s.typedRate)],
            ['진행률', pct(s.progressRate)],
          ].map(([l, v]) => (
            <div key={l} className="bg-subtle rounded-lg px-3 py-2">
              <p className="text-ink-3 text-[11px]">{l}</p>
              <p className="text-[14px] font-bold tabular-nums">{v}</p>
            </div>
          ))}
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {(
          [
            ['CHANGED', '전체'],
            ['EDITED', '고침'],
            ['ADDED', '직접 채움'],
          ] as const
        ).map(([v, l]) => (
          <Chip key={v} size="sm" selected={result === v} onClick={() => pick(v)}>
            {l}
          </Chip>
        ))}
        {data && <span className="text-ink-3 text-[12px]">{num(data.total)}건</span>}
        {isFetching && <Spinner className="text-ink-3 size-3.5" />}
      </div>

      {isPending ? (
        <div className="grid min-h-[120px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : !data || data.items.length === 0 ? (
        <Empty>이 조건으로 고치거나 직접 채운 세션이 없어요</Empty>
      ) : (
        <div className="-mx-1 mt-3 overflow-x-auto">
          <table className="w-full min-w-[880px] text-[12px]">
            <thead>
              <tr className="text-ink-3 text-left text-[11px]">
                <th className="px-2 pb-2 font-medium">날짜</th>
                <th className="px-2 pb-2 font-medium">결과</th>
                <th className="px-2 pb-2 font-medium">질문</th>
                <th className="px-2 pb-2 font-medium">AI 칩</th>
                <th className="px-2 pb-2 font-medium">답 (방식)</th>
                <th className="px-2 pb-2 font-medium">AI가 뽑은 값</th>
                <th className="px-2 pb-2 font-medium">최종 값</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y align-top">
              {data.items.map((it, i) => (
                <tr key={`${it.at}-${i}`}>
                  <td className="px-2 py-2.5 whitespace-nowrap tabular-nums">{dateTimeLabel(it.at)}</td>
                  <td className="px-2 py-2.5 font-semibold whitespace-nowrap">
                    {it.result === 'EDITED' ? '고침' : '직접 채움'}
                  </td>
                  <td className="text-ink-2 px-2 py-2.5">
                    {it.question ?? <span className="text-ink-4">(질문 안 함 · 질문 수 소진 등)</span>}
                  </td>
                  <td className="text-ink-3 px-2 py-2.5">{it.chips.length ? it.chips.join(' · ') : '–'}</td>
                  <td className="px-2 py-2.5">
                    {it.answerSource == null ? (
                      '–'
                    ) : (
                      <>
                        {it.answer != null ? `"${it.answer}"` : it.answerSource === 'SKIP' ? '' : '(보관 기간 지남)'}{' '}
                        <span className="text-ink-3">({SOURCE_LABEL[it.answerSource]})</span>
                      </>
                    )}
                  </td>
                  <td className="text-danger px-2 py-2.5 font-semibold">
                    {it.retained ? (it.aiValue ?? '(비어 있음)') : '(보관 기간 지남)'}
                  </td>
                  <td className="text-text-brand px-2 py-2.5 font-semibold">
                    {it.retained ? (it.finalValue ?? '(비어 있음)') : '(보관 기간 지남)'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.total > data.size && (
        <div className="mt-3 flex items-center justify-end gap-2 text-[12px]">
          <span className="text-ink-3 tabular-nums">
            {page * data.size + 1}–{Math.min(data.total, (page + 1) * data.size)} / {num(data.total)}
          </span>
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="rounded-md px-2 py-1 font-semibold disabled:opacity-30"
          >
            ‹ 이전
          </button>
          <button
            type="button"
            disabled={page + 1 >= pages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md px-2 py-1 font-semibold disabled:opacity-30"
          >
            다음 ›
          </button>
        </div>
      )}
    </section>
  )
}
