'use client'

import { useMemo, useState } from 'react'
import { X } from 'lucide-react'
import { Chip } from '@/components/ui/Chip'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useDraftReview, useDraftReviewItems, type DraftCaseFilter, type DraftReviewFilter } from '../api'
import type { DraftItemKind, DraftPath, DraftReview, EditDirection } from '../types'
import { dateTimeLabel, num, pct } from '../format'
import { GOAL_TYPES, selectClass } from './ChatReviewSection'
import { Empty, Panel, Stat } from './Panel'

const PATH_META: Record<DraftPath, { label: string; meaning: string; color: string }> = {
  AS_IS: { label: '④ 그대로 확정', meaning: '일단 받아들임 — 확정 뒤 수정을 꼭 같이 봐요', color: 'bg-[#0F7A9C]' },
  MANUAL: { label: '③ 직접 수정만', meaning: '고칠 게 적거나 직접 고르고 싶음', color: 'bg-brand' },
  QUICK_FIX: { label: '② 말로 고치기', meaning: '손으로 고치기 귀찮을 때 AI 에게 부분 수정', color: 'bg-[#A894FA]' },
  REGEN: { label: '① 다시 생성', meaning: '진짜 마음에 안 듦 — 유일한 실패 신호', color: 'bg-danger' },
}

const BUCKET_META = {
  NEAR: { label: '거의 그대로', sub: '보존율 90% 이상' },
  SOME: { label: '조금 수정', sub: '보존율 70~90%' },
  MANY: { label: '많이 수정', sub: '보존율 70% 미만' },
} as const

const INTENSITY_LABEL: Record<string, string> = { STEADY: '안정형', CHALLENGE: '도전형' }

const CASE_FILTERS: { value: DraftCaseFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'EDITED', label: '확정 전 고침' },
  { value: 'DELETED', label: '확정 전 삭제' },
  { value: 'ADDED', label: '사용자 추가' },
  { value: 'AFTER', label: '확정 뒤 수정 · 삭제' },
]

/** 개선 후보: 고친 쪽 실천률이 그대로 둔 쪽보다 이만큼(%p) 높으면 */
const CANDIDATE_GAP = 5

const DATE_FIELDS = new Set(['START_DATE', 'END_DATE', 'DUE_DATE', 'SCHEDULED_DATE'])

/** "늦춤 (평균 +1.8시간)" 처럼 방향 + 크기 */
function directionText(field: string, dir: EditDirection, avg: number | null, single = false) {
  const p = single ? '' : '평균 '
  const d = avg ?? 0
  if (field === 'START_TIME') {
    const size = d >= 60 ? `${(d / 60).toFixed(1).replace(/\.0$/, '')}시간` : `${Math.round(d)}분`
    if (dir === 'LATER') return `늦춤 (${p}+${size})`
    if (dir === 'EARLIER') return `당김 (${p}−${size})`
  }
  if (DATE_FIELDS.has(field)) {
    if (dir === 'LATER') return `미룸 (${p}+${Math.round(d)}일)`
    if (dir === 'EARLIER') return `당김 (${p}−${Math.round(d)}일)`
  }
  if (field === 'DURATION') {
    if (dir === 'MORE') return `늘림 (${p}+${Math.round(d)}분)`
    if (dir === 'LESS') return `줄임 (${p}−${Math.round(d)}분)`
  }
  if (field === 'DAYS') {
    if (dir === 'MORE') return `횟수 늘림 (주 +${d.toFixed(1).replace(/\.0$/, '')}회)`
    if (dir === 'LESS') return `횟수 줄임 (주 −${d.toFixed(1).replace(/\.0$/, '')}회)`
    return '요일만 바꿈'
  }
  if (dir === 'MORE') return '올림'
  if (dir === 'LESS') return '내림'
  return '바꿈'
}

const hours = (min: number) => `${(min / 60).toFixed(1).replace(/\.0$/, '')}시간`

/**
 * AI 초안 정확도 (운영진 전용, V68) — 사용자에게는 보이지 않아요.
 * 확정 전: AI 최신 초안 ↔ 확정한 계획 (칸별, 자동으로 다시 맞춰진 칸 제외)
 * 확정 뒤: 첫 행동(루틴 체크 · Task 완료 · 실제 한 일 추가 · 수치 기록)부터 14일 · 30일.
 * "그대로 확정"만 성공으로 보지 않아요 — 수정은 개인화로 보고, 실패 신호는 다시 생성 · 확정 뒤 이탈 · 삭제로 봐요.
 */
export function DraftReviewPanel({ days }: { days: number }) {
  const [filter, setFilter] = useState<DraftReviewFilter>({ window: 14 })
  const [open, setOpen] = useState<DraftItemKind | null>(null)
  const { data, isPending, isError, isFetching } = useDraftReview(days, filter)

  return (
    <Panel
      title="AI 초안 정확도"
      aside={
        data
          ? `초안 화면 · 확정 기준 · ${num(data.sessions)}세션 · 유지율은 첫 행동부터 ${data.window}일`
          : '초안 화면 · 확정 기준'
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
        <select
          aria-label="계획 강도"
          className={selectClass}
          value={filter.intensity ?? ''}
          onChange={(e) =>
            setFilter((f) => ({ ...f, intensity: (e.target.value || undefined) as DraftReviewFilter['intensity'] }))
          }
        >
          <option value="">강도 전체</option>
          <option value="STEADY">안정형</option>
          <option value="CHALLENGE">도전형</option>
        </select>
        <select
          aria-label="실행 성향"
          className={selectClass}
          value={filter.planningStyle ?? ''}
          onChange={(e) =>
            setFilter((f) => ({
              ...f,
              planningStyle: (e.target.value || undefined) as DraftReviewFilter['planningStyle'],
            }))
          }
        >
          <option value="">성향 전체</option>
          <option value="PLANNER">계획형</option>
          <option value="SPONTANEOUS">즉흥형</option>
        </select>
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
        {([14, 30] as const).map((w) => (
          <Chip
            key={w}
            size="sm"
            selected={filter.window === w}
            onClick={() => setFilter((f) => ({ ...f, window: w }))}
          >
            유지 {w}일
          </Chip>
        ))}
        {isFetching && <Spinner className="text-ink-3 size-3.5" />}
      </div>

      {isPending ? (
        <div className="grid min-h-[160px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError || !data ? (
        <Empty>초안 정확도를 불러오지 못했어요</Empty>
      ) : data.sessions === 0 && data.funnel.entered === 0 ? (
        <Empty>기간 안에 초안 화면에 들어온 세션이 없어요 (배포 이후 초안부터 쌓여요)</Empty>
      ) : (
        <div className="space-y-4">
          <Headline data={data} />
          {data.sessions > 0 && (
            <>
              <Paths data={data} />
              <Buckets data={data} />
              <ItemTable data={data} open={open} onOpen={(k) => setOpen((cur) => (cur === k ? null : k))} />
              <Directions data={data} />
            </>
          )}
          <Extras data={data} />
          {open && data.sessions > 0 && (
            <ItemDetail
              key={`${open}|${days}|${JSON.stringify(filter)}`}
              kind={open}
              days={days}
              filter={filter}
              onClose={() => setOpen(null)}
            />
          )}
        </div>
      )}
    </Panel>
  )
}

function Headline({ data }: { data: DraftReview }) {
  const h = data.headline
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="border-brand bg-brand-soft rounded-xl border p-4">
        <Stat
          label={<span className="text-text-brand font-semibold">초안 보존율 (대표)</span>}
          value={<span className="text-text-brand">{pct(h.preservationRate)}</span>}
          sub={`AI 값 그대로 남은 칸 ${num(h.keptFields)} / ${num(h.aiFields)} · 자동 조정 칸 제외`}
        />
      </div>
      <div className="border-line rounded-xl border p-4">
        <Stat
          label="다시 생성률 (실패 신호)"
          value={pct(h.regenRate)}
          sub={`다시 생성 ${num(h.regenSessions)} / 초안 화면 ${num(h.enteredSessions)}세션 · 성향·강도 변경 제외`}
        />
      </div>
      <div className="border-line rounded-xl border p-4">
        <Stat
          label={`유지율 (첫 행동 후 ${data.window}일)`}
          value={pct(h.retentionRate)}
          sub={`안 고치고 안 지운 루틴·Task ${num(h.retainedItems)} / ${num(h.evaluatedItems)}`}
        />
      </div>
    </div>
  )
}

function Paths({ data }: { data: DraftReview }) {
  const q = data.quickFix
  const total = data.paths.reduce((s, p) => s + p.sessions, 0)
  return (
    <section className="border-line rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 className="text-[14px] font-bold">세션 경로 4가지</h3>
        <p className="text-ink-3 text-[12px]">
          한 세션에서 여러 개를 썼으면 가장 강한 신호 1개 (① &gt; ② &gt; ③ &gt; ④) · 성향 · 강도 변경은 ‘조건 변경’이라
          제외
        </p>
      </div>
      {total > 0 && (
        <div className="mt-3 flex h-3 gap-0.5 overflow-hidden rounded-full" aria-hidden>
          {data.paths
            .filter((p) => p.sessions > 0)
            .map((p) => (
              <div
                key={p.path}
                className={PATH_META[p.path].color}
                style={{ width: `${(p.sessions / total) * 100}%` }}
              />
            ))}
        </div>
      )}
      <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {data.paths.map((p) => (
          <div key={p.path} className="bg-subtle rounded-lg px-3 py-2.5">
            <p className="flex items-center gap-1.5 text-[12px] font-bold">
              <span className={cn('size-2 rounded-full', PATH_META[p.path].color)} />
              {PATH_META[p.path].label}
              <span className="ml-auto text-[16px] tabular-nums">{pct(p.share)}</span>
            </p>
            <p className="mt-1 text-[12px] tabular-nums">
              {num(p.sessions)}세션 · 유지율 {pct(p.retentionRate)} · 실천률 {pct(p.practiceRate)}
            </p>
            {p.path === 'QUICK_FIX' && (
              <p className="text-ink-2 mt-0.5 text-[11px] tabular-nums">
                1회 {num(q.once)} · 2회 {num(q.twice)} · 3회+ {num(q.threePlus)} · 성공률 {pct(q.successRate)}
              </p>
            )}
            <p className="text-ink-3 mt-0.5 text-[11px]">{PATH_META[p.path].meaning}</p>
          </div>
        ))}
      </div>
      <p className="text-ink-3 mt-2 text-[11px] tabular-nums">
        말로 고치다가 답답해서 다시 생성 {num(q.thenRegen)}세션 · 말로 고치기 직전 손으로 고친 칸 {num(q.baseEditCount)}
        개 · 말로 고치기 성공률 = 말로 고친 뒤 다시 생성 없이 확정 {num(q.succeeded)} ÷ 말로 고치기를 쓰고 끝난 세션{' '}
        {num(q.finished)}
      </p>
    </section>
  )
}

function Buckets({ data }: { data: DraftReview }) {
  return (
    <section className="border-line rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 className="text-[14px] font-bold">수정 정도별 실천률</h3>
        <p className="text-ink-3 text-[12px]">
          초안 보존율로 세션을 3묶음 → 첫 행동부터 {data.window}일 루틴 실천률 · 기한이 끝난 목표의 달성률
        </p>
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        {data.buckets.map((b) => (
          <div key={b.bucket} className="bg-subtle rounded-lg px-3 py-2.5">
            <p className="text-[12px] font-bold">
              {BUCKET_META[b.bucket].label}{' '}
              <span className="text-ink-3 font-normal">
                {BUCKET_META[b.bucket].sub} · 세션 {pct(b.share)}
              </span>
            </p>
            <div className="mt-1.5 grid grid-cols-2 gap-2">
              <Stat label="루틴 실천률" value={pct(b.practiceRate)} sub={`해야 했던 루틴 ${num(b.routinePlanned)}`} />
              <Stat label="목표 달성률" value={pct(b.achievementRate)} sub={`끝난 목표 ${num(b.endedGoals)}`} />
            </div>
          </div>
        ))}
      </div>
      <p className="text-ink-3 mt-2 text-[11px]">
        많이 고친 쪽이 더 잘 지키면 → 사람들이 고친 방향이 맞고 AI 초안이 덜 맞아요 (아래 ‘자주 고치는 방향’을 초안
        규칙에 반영) · 거의 그대로인데 실천률이 낮으면 → ‘일단 수락’ — 분량 · 시간대가 비현실적
      </p>
    </section>
  )
}

function ItemTable({
  data,
  open,
  onOpen,
}: {
  data: DraftReview
  open: DraftItemKind | null
  onOpen: (k: DraftItemKind) => void
}) {
  const min = data.minSample
  const hot = useMemo(() => {
    // 수정률 · 삭제율 중 상위 2개 (표본이 충분한 항목만)
    const cells = data.items
      .filter((r) => r.aiItems >= min)
      .flatMap((r) => [
        { key: `${r.kind}|edit`, rate: r.editRate ?? 0 },
        { key: `${r.kind}|delete`, rate: r.deleteRate ?? 0 },
      ])
      .filter((c) => c.rate > 0)
      .sort((a, b) => b.rate - a.rate)
      .slice(0, 2)
    return new Set(cells.map((c) => c.key))
  }, [data.items, min])
  return (
    <section>
      <div className="-mx-1 overflow-x-auto">
        <table className="w-full min-w-[760px] text-[13px] tabular-nums">
          <thead>
            <tr className="text-ink-3 text-left text-[12px]">
              <th className="px-2 pb-2 font-medium">항목</th>
              <th className="px-2 pb-2 font-medium">AI가 만든 수</th>
              <th className="px-2 pb-2 font-medium">수정률</th>
              <th className="px-2 pb-2 font-medium">삭제율</th>
              <th className="px-2 pb-2 font-medium">사용자 추가율</th>
              <th className="px-2 pb-2 font-medium">가장 많이 고친 칸</th>
              <th className="px-2 pb-2 font-medium">유지율 ({data.window}일)</th>
              <th className="w-6" />
            </tr>
          </thead>
          <tbody className="divide-line divide-y">
            {data.items.map((r) => {
              const few = r.aiItems < min
              return (
                <tr
                  key={r.kind}
                  onClick={() => onOpen(r.kind)}
                  className={cn('hover:bg-subtle/60 cursor-pointer', open === r.kind && 'bg-brand-soft/60')}
                  aria-selected={open === r.kind}
                >
                  <td className="px-2 py-3 font-semibold">{r.label}</td>
                  <td className="px-2 py-3">{num(r.aiItems)}</td>
                  <td
                    className={cn(
                      'px-2 py-3',
                      few && 'text-ink-4',
                      hot.has(`${r.kind}|edit`) && 'text-danger font-bold'
                    )}
                  >
                    {pct(r.editRate)}
                  </td>
                  <td
                    className={cn(
                      'px-2 py-3',
                      few && 'text-ink-4',
                      hot.has(`${r.kind}|delete`) && 'text-danger font-bold'
                    )}
                  >
                    {pct(r.deleteRate)}
                  </td>
                  <td className={cn('px-2 py-3', few && 'text-ink-4')}>{pct(r.addRate)}</td>
                  <td className={cn('px-2 py-3', few && 'text-ink-4')}>
                    {r.topFieldLabel ? `${r.topFieldLabel} ${pct(r.topFieldRate)}` : '–'}
                  </td>
                  <td className={cn('px-2 py-3', r.evaluatedItems < min && 'text-ink-4')}>{pct(r.retentionRate)}</td>
                  <td className="text-ink-4 px-1 py-3 text-right">›</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="text-ink-3 mt-2 text-[11px] leading-relaxed">
        수정률 · 삭제율 · 사용자 추가율 = 고친 / 지운 / 더한 항목 ÷ AI가 만든 항목 (확정 시점) · 가장 많이 고친 칸 = 그
        항목에서 가장 많이 고친 칸 (목표 기간을 바꿔 자동으로 다시 맞춰진 마일스톤 날짜 · 단계 수치 · 할 일 마감은 빼고
        셈) · 유지율 = 첫 행동부터 {data.window}일 동안 안 고치고 안 지운 비율 · 빨간 숫자는 상위 2개, 표본 {min} 미만은
        흐리게 · 행을 누르면 칸별 상세
      </p>
    </section>
  )
}

function Directions({ data }: { data: DraftReview }) {
  const min = data.minSample
  if (data.directions.length === 0) return null
  return (
    <section className="border-line rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 className="text-[14px] font-bold">자주 고치는 방향 · 개선 후보</h3>
        <p className="text-ink-3 text-[12px]">
          칸별로 어느 쪽으로 고쳤는지 → 고친 쪽이 더 잘 지켜지면 초안 규칙 · 프롬프트에 반영
        </p>
      </div>
      <div className="-mx-1 mt-3 overflow-x-auto">
        <table className="w-full min-w-[760px] text-[12px] tabular-nums">
          <thead>
            <tr className="text-ink-3 text-left text-[11px]">
              <th className="px-2 pb-2 font-medium">항목 · 칸</th>
              <th className="px-2 pb-2 font-medium">고친 방향</th>
              <th className="px-2 pb-2 font-medium">고친 비율</th>
              <th className="px-2 pb-2 font-medium">실천률 (고친 쪽 vs 그대로)</th>
              <th className="px-2 pb-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-line divide-y">
            {data.directions.map((d) => {
              const gap = d.editedPractice != null && d.keptPractice != null ? d.editedPractice - d.keptPractice : null
              const candidate = gap != null && gap >= CANDIDATE_GAP && d.count >= min
              return (
                <tr key={`${d.kind}|${d.field}|${d.direction}`} className={cn(d.count < min && 'text-ink-4')}>
                  <td className="px-2 py-2.5 font-semibold">{d.fieldLabel}</td>
                  <td className="px-2 py-2.5">{directionText(d.field, d.direction, d.avgDelta)}</td>
                  <td className="px-2 py-2.5">
                    {pct(d.rate)} <span className="text-ink-3">({num(d.count)})</span>
                  </td>
                  <td className={cn('px-2 py-2.5', candidate && 'text-text-brand font-bold')}>
                    {pct(d.editedPractice)} vs {pct(d.keptPractice)}
                  </td>
                  <td className="px-2 py-2.5">
                    {candidate && (
                      <span className="bg-brand-soft text-text-brand rounded-full px-2 py-0.5 text-[11px] font-bold">
                        개선 후보
                      </span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="text-ink-3 mt-2 text-[11px]">
        고친 비율 = 이 방향으로 고친 칸 ÷ AI가 만든 그 칸 · 실천률 = 첫 행동부터 {data.window}일 · 표본 {min} 이상이고
        고친 쪽이 {CANDIDATE_GAP}%p 이상 높으면 개선 후보 → 프롬프트 버전을 바꾼 뒤 같은 표로 전후 비교
      </p>
    </section>
  )
}

function Extras({ data }: { data: DraftReview }) {
  const f = data.funnel
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="border-line rounded-xl border p-4">
        <Stat
          label="초안 화면 이탈률"
          value={pct(f.exitRate)}
          sub={`그만둠 ${num(f.exited)} ÷ 진입 ${num(f.entered - f.pending)} (아직 머무는 ${num(f.pending)} 제외)`}
        />
      </div>
      <div className="border-line rounded-xl border p-4">
        <p className="text-ink-3 text-[12px]">루틴 시간 변화 (확정 ÷ 초안)</p>
        {data.weeklyTime.length === 0 ? (
          <p className="mt-1 text-[22px] font-extrabold">–</p>
        ) : (
          <ul className="mt-1 space-y-0.5">
            {data.weeklyTime.map((w) => (
              <li key={w.intensity} className="flex items-baseline gap-2 tabular-nums">
                <span className="text-[13px] font-semibold">{INTENSITY_LABEL[w.intensity] ?? w.intensity}</span>
                <span className="text-[20px] font-extrabold">{pct(w.rate)}</span>
                <span className="text-ink-3 truncate text-[11px]">
                  AI {hours(w.draftMinutes / Math.max(1, w.sessions))} → 확정{' '}
                  {hours(w.confirmedMinutes / Math.max(1, w.sessions))} / 주 평균
                </span>
              </li>
            ))}
          </ul>
        )}
        <p className="text-ink-3 mt-0.5 text-[11px]">
          AI가 짠 루틴 일주일 총 시간 대비 사용자가 확정한 시간 (100% 아래 = 줄여서 시작)
        </p>
      </div>
      <div className="border-line rounded-xl border p-4">
        <Stat
          label="확정 뒤 아직 안 씀"
          value={pct(data.notUsed.rate)}
          sub={`14일 안에 첫 행동 없는 목표 ${num(data.notUsed.count)} / ${num(data.notUsed.base)} · 유지율에서 빠짐`}
        />
      </div>
    </div>
  )
}

/** 항목 상세 — 칸별 수정률 + 고치거나 지우거나 더한 사례 전부 (확정 전 · 확정 뒤) */
function ItemDetail({
  kind,
  days,
  filter,
  onClose,
}: {
  kind: DraftItemKind
  days: number
  filter: DraftReviewFilter
  onClose: () => void
}) {
  const [result, setResult] = useState<DraftCaseFilter>('ALL')
  const [page, setPage] = useState(0)
  const { data, isPending, isFetching } = useDraftReviewItems(kind, days, filter, result, page)
  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1
  const pick = (r: DraftCaseFilter) => {
    setResult(r)
    setPage(0)
  }
  return (
    <section className="border-brand rounded-2xl border-[1.5px] p-4 sm:p-5" aria-label="항목 상세">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-bold">항목 상세 · {data?.label ?? ''}</h3>
          <p className="text-ink-3 mt-0.5 text-[12px]">
            칸별 수정률 + 이 항목을 고치거나 지우거나 더한 사례 전부 (확정 전 · 확정 뒤) · 개인 식별 정보 제외 · 요청
            원문은 6개월 보관
          </p>
        </div>
        <button type="button" onClick={onClose} className="text-ink-3 hover:text-ink" aria-label="항목 상세 닫기">
          <X className="size-4" />
        </button>
      </div>

      {data && data.fields.length > 0 && (
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
          {data.fields.map((f) => (
            <div key={f.field} className="bg-subtle rounded-lg px-3 py-2">
              <p className="text-ink-3 truncate text-[11px]">{f.label}</p>
              <p className="text-[14px] font-bold tabular-nums">
                {pct(f.editRate)}{' '}
                <span className="text-ink-3 text-[11px] font-normal">
                  ({num(f.edited)}/{num(f.base)})
                </span>
              </p>
              <p className="text-ink-3 text-[11px] tabular-nums">확정 뒤 {pct(f.afterRate)}</p>
            </div>
          ))}
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {CASE_FILTERS.map((c) => (
          <Chip key={c.value} size="sm" selected={result === c.value} onClick={() => pick(c.value)}>
            {c.label}
          </Chip>
        ))}
        {data && <span className="text-ink-3 text-[12px]">{num(data.total)}건</span>}
        {isFetching && <Spinner className="text-ink-3 size-3.5" />}
      </div>

      {isPending ? (
        <div className="grid min-h-[120px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : !data || data.cases.length === 0 ? (
        <Empty>이 조건으로 고치거나 지우거나 더한 사례가 없어요</Empty>
      ) : (
        <div className="-mx-1 mt-3 overflow-x-auto">
          <table className="w-full min-w-[920px] text-[12px]">
            <thead>
              <tr className="text-ink-3 text-left text-[11px]">
                <th className="px-2 pb-2 font-medium">날짜</th>
                <th className="px-2 pb-2 font-medium">결과</th>
                <th className="px-2 pb-2 font-medium">항목</th>
                <th className="px-2 pb-2 font-medium">고친 칸</th>
                <th className="px-2 pb-2 font-medium">경로 · 요청</th>
                <th className="px-2 pb-2 font-medium">AI 초안 값</th>
                <th className="px-2 pb-2 font-medium">최종 값</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y align-top">
              {data.cases.map((c, i) => (
                <tr key={`${c.at}-${c.field}-${i}`}>
                  <td className="px-2 py-2.5 whitespace-nowrap tabular-nums">{dateTimeLabel(c.at)}</td>
                  <td className="px-2 py-2.5 font-semibold whitespace-nowrap">{resultLabel(c.stage, c.result)}</td>
                  <td className="px-2 py-2.5">{c.itemLabel ?? <span className="text-ink-4">(보관 기간 지남)</span>}</td>
                  <td className="text-ink-2 px-2 py-2.5">
                    {c.result === 'DELETED' || c.result === 'ADDED' ? '(항목 통째로)' : c.fieldLabel}
                    {c.direction && c.direction !== 'CHANGED' && c.stage === 'BEFORE_CONFIRM' && (
                      <span className="text-ink-3"> · {directionText(c.field, c.direction, c.delta, true)}</span>
                    )}
                  </td>
                  <td className="text-ink-2 px-2 py-2.5">
                    {PATH_META[c.path]?.label ?? c.path}
                    {c.request && <span className="text-ink-3 block">“{c.request}”</span>}
                  </td>
                  <td className="text-danger px-2 py-2.5 font-semibold">{c.aiValue ?? '(없음)'}</td>
                  <td className="text-text-brand px-2 py-2.5 font-semibold">
                    {c.stage === 'AFTER_CONFIRM'
                      ? `${c.finalValue ?? '(없음)'} → 확정 뒤 ${c.result === 'DELETED' ? '삭제' : '수정'}`
                      : c.result === 'DELETED'
                        ? '(삭제)'
                        : (c.finalValue ?? '(비어 있음)')}
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

function resultLabel(stage: 'BEFORE_CONFIRM' | 'AFTER_CONFIRM', result: string) {
  if (stage === 'AFTER_CONFIRM') return result === 'DELETED' ? '확정 뒤 삭제' : '확정 뒤 수정'
  if (result === 'DELETED') return '삭제'
  if (result === 'ADDED') return '추가'
  return '고침'
}
