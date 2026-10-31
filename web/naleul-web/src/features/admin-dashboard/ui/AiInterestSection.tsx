'use client'

import { useState } from 'react'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useAiInterest } from '../api'
import type { AiInterest, AiInterestDropStat, AiInterestPlanStat, AiPlan } from '../types'
import { dateTimeLabel, num, pct } from '../format'
import { selectClass } from './ChatReviewSection'
import { Empty, Panel, Stat } from './Panel'

const PLAN_LABEL: Record<AiPlan, string> = { FREE: 'Free', PRO: 'Pro' }

/**
 * AI 관심도 (운영진 전용, V70) — 사용자에게는 보이지 않아요.
 * AI 기능마다 시도 · 완료 · 중단을 Free / Pro 로 나눠 보고, 중단은 어느 화면 · 단계에서 멈췄는지 개수와 비율로 봐요.
 * 시도 = 완료 + 중단 + 진행 중 (한도에 막힌 것도 시도).
 */
export function AiInterestPanel({ days }: { days: number }) {
  const { data, isPending, isError, isFetching } = useAiInterest(days)
  return (
    <Panel title="AI 관심도" aside="기간 안 활동 사용자 기준 · Free / Pro">
      <div className="bg-brand-soft text-text-brand mb-4 rounded-xl px-4 py-3 text-[13px] font-semibold">
        AI 기능을 써 보려 한 사람(시도) → 끝까지 쓴 사람(완료) → 멈춘 곳(중단)
        {isFetching && <Spinner className="ml-2 inline size-3.5" />}
      </div>
      {isPending ? (
        <div className="grid min-h-[160px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError || !data ? (
        <Empty>AI 관심도를 불러오지 못했어요</Empty>
      ) : (
        <div className="space-y-4">
          <Headline data={data} />
          <FeatureTable data={data} />
          <Drops data={data} />
          <Limits data={data} />
          <p className="text-ink-3 text-[11px] leading-relaxed">
            활동 사용자 = 기간 안에 오늘 화면 방문 · Task 완료 · 실제 한 일 추가 · AI 시도 중 하나라도 한 사람 · 맨 위
            요약은 지금 플랜, 기능 표는 시도 당시 플랜(V70 이전 기록은 지금 플랜) · 제안형 기능은 미리보기 뒤 2시간
            안이면 진행 중 · 화면을 열기만 하고 요청하지 않은 건 서버에 남지 않아 Amplitude 에서 봐요 · AI 호출이 오류로
            끝난 요청은 시도에 안 잡혀요 · 운영진 계정 제외
          </p>
        </div>
      )}
    </Panel>
  )
}

function Headline({ data }: { data: AiInterest }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {data.plans.map((p) => (
        <div
          key={p.plan}
          className={cn('rounded-xl border p-4', p.plan === 'PRO' ? 'border-brand bg-brand-soft' : 'border-line')}
        >
          <p className={cn('text-[13px] font-bold', p.plan === 'PRO' && 'text-text-brand')}>{PLAN_LABEL[p.plan]}</p>
          <div className="mt-2 grid grid-cols-3 gap-3">
            <Stat
              label="AI 써 본 사람"
              value={pct(p.triedRate)}
              sub={`${num(p.triedUsers)} / ${num(p.activeUsers)}명`}
            />
            <Stat
              label="끝까지 쓴 사람"
              value={pct(p.completedRate)}
              sub={`${num(p.completedUsers)} / ${num(p.activeUsers)}명`}
            />
            <Stat
              label="1명당 써 본 기능"
              value={p.avgFeatures == null ? '–' : `${p.avgFeatures}개`}
              sub={`기능 ${data.features.length}개 중`}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

/** 완료 · 중단 · 진행 중 비율 막대 */
function OutcomeBar({ s }: { s: AiInterestPlanStat }) {
  if (s.attempts === 0) return <div className="bg-progress-track h-1.5 rounded-full" />
  const w = (n: number) => `${(100 * n) / s.attempts}%`
  return (
    <div className="bg-progress-track flex h-1.5 overflow-hidden rounded-full">
      <div className="bg-success h-full" style={{ width: w(s.completed) }} />
      <div className="bg-danger h-full" style={{ width: w(s.dropped) }} />
      <div className="bg-line-strong h-full" style={{ width: w(s.inProgress) }} />
    </div>
  )
}

function FeatureTable({ data }: { data: AiInterest }) {
  return (
    <section>
      <h3 className="text-[14px] font-bold">기능별 시도 · 완료 · 중단</h3>
      <p className="text-ink-3 mt-0.5 text-[12px]">
        비율은 시도 수 기준 · 써 본 사람 비율은 같은 플랜 활동 사용자 기준 · 막대 = 완료 / 중단 / 진행 중
      </p>
      <div className="-mx-1 mt-2 overflow-x-auto">
        <table className="w-full min-w-[860px] text-[13px] tabular-nums">
          <thead>
            <tr className="text-ink-3 text-left text-[12px]">
              <th className="px-2 pb-2 font-medium">기능 · 화면</th>
              <th className="px-2 pb-2 font-medium">플랜</th>
              <th className="px-2 pb-2 font-medium">써 본 사람</th>
              <th className="px-2 pb-2 font-medium">시도</th>
              <th className="px-2 pb-2 font-medium">완료</th>
              <th className="px-2 pb-2 font-medium">중단</th>
              <th className="px-2 pb-2 font-medium">진행 중</th>
              <th className="px-2 pb-2 font-medium">다시 쓴 사람</th>
              <th className="w-[120px] px-2 pb-2 font-medium" />
            </tr>
          </thead>
          <tbody>
            {data.features.map((f) =>
              (['FREE', 'PRO'] as const).map((plan, i) => {
                const s = plan === 'FREE' ? f.free : f.pro
                return (
                  <tr key={f.key + plan} className={cn(i === 0 && 'border-line border-t')}>
                    {i === 0 && (
                      <td rowSpan={2} className="px-2 py-2 align-top">
                        <p className="font-semibold">{f.label}</p>
                        <p className="text-ink-3 text-[12px]">{f.page}</p>
                        <p className="text-ink-3 mt-1 max-w-[220px] text-[11px] leading-snug">{f.note}</p>
                      </td>
                    )}
                    <td className={cn('px-2 py-2 font-semibold', plan === 'PRO' && 'text-text-brand')}>
                      {PLAN_LABEL[plan]}
                    </td>
                    <td className="px-2 py-2">
                      {pct(s.triedRate)} <span className="text-ink-3 text-[12px]">{num(s.users)}명</span>
                    </td>
                    <td className="px-2 py-2">{num(s.attempts)}</td>
                    <td className="px-2 py-2">
                      <span className="text-success font-semibold">{pct(s.completedRate)}</span>{' '}
                      <span className="text-ink-3 text-[12px]">
                        {f.completeLabel} {num(s.completed)}
                      </span>
                    </td>
                    <td className="px-2 py-2">
                      <span className="text-danger font-semibold">{pct(s.dropRate)}</span>{' '}
                      <span className="text-ink-3 text-[12px]">{num(s.dropped)}</span>
                    </td>
                    <td className="px-2 py-2">{num(s.inProgress)}</td>
                    <td className="px-2 py-2">
                      {num(s.repeatUsers)} <span className="text-ink-3 text-[12px]">/ {num(s.completedUsers)}명</span>
                    </td>
                    <td className="px-2 py-2">
                      <OutcomeBar s={s} />
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function DropCell({ s }: { s: AiInterestDropStat }) {
  if (s.count === 0) return <span className="text-ink-3">–</span>
  return (
    <>
      <span className="font-semibold">{num(s.count)}</span>{' '}
      <span className="text-ink-3 text-[12px]">
        중단의 {pct(s.ofDrops)} · 시도의 {pct(s.ofAttempts)}
      </span>
    </>
  )
}

function Drops({ data }: { data: AiInterest }) {
  const [feature, setFeature] = useState('ALL')
  const rows = feature === 'ALL' ? data.drops : data.drops.filter((d) => d.feature === feature)
  return (
    <section className="border-line rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h3 className="text-[14px] font-bold">어디서 멈추나 — 화면 · 단계별 중단</h3>
          <p className="text-ink-3 mt-0.5 text-[12px]">
            중단의 % = 그 기능 중단 중 이 단계 비율 · 시도의 % = 그 기능 시도 중 이 단계에서 멈춘 비율
          </p>
        </div>
        <select
          className={selectClass}
          value={feature}
          onChange={(e) => setFeature(e.target.value)}
          aria-label="기능 고르기"
        >
          <option value="ALL">전체 기능</option>
          {data.features.map((f) => (
            <option key={f.key} value={f.key}>
              {f.label}
            </option>
          ))}
        </select>
      </div>
      {rows.length === 0 ? (
        <Empty>멈춘 기록이 없어요</Empty>
      ) : (
        <div className="-mx-1 mt-3 overflow-x-auto">
          <table className="w-full min-w-[760px] text-[13px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left text-[12px]">
                <th className="px-2 pb-2 font-medium">기능 · 화면</th>
                <th className="px-2 pb-2 font-medium">멈춘 단계</th>
                <th className="px-2 pb-2 font-medium">어떻게</th>
                <th className="px-2 pb-2 font-medium">Free</th>
                <th className="text-text-brand px-2 pb-2 font-medium">Pro</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {rows.map((d) => (
                <tr key={d.feature + d.step + d.detail}>
                  <td className="px-2 py-2">
                    <p className="font-semibold">{d.featureLabel}</p>
                    <p className="text-ink-3 text-[12px]">{d.page}</p>
                  </td>
                  <td className="px-2 py-2 font-semibold">{d.step}</td>
                  <td className="text-ink-2 px-2 py-2">{d.detail}</td>
                  <td className="px-2 py-2">
                    <DropCell s={d.free} />
                  </td>
                  <td className="px-2 py-2">
                    <DropCell s={d.pro} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Limits({ data }: { data: AiInterest }) {
  return (
    <section className="border-line rounded-xl border p-4">
      <h3 className="text-[14px] font-bold">한도에 막힘</h3>
      <p className="text-ink-3 mt-0.5 text-[12px]">
        써 보려 했는데 횟수 한도로 못 쓴 횟수 (사람 수) · Free 가 많이 막히면 Pro 전환 지점 후보
        {data.limitTrackingSince && ` · ${dateTimeLabel(data.limitTrackingSince)}부터 기록`}
      </p>
      {data.limits.length === 0 ? (
        <Empty>한도에 막힌 기록이 없어요</Empty>
      ) : (
        <ul className="divide-line mt-3 divide-y text-[13px] tabular-nums">
          {data.limits.map((l) => (
            <li key={l.feature + l.reason} className="flex flex-wrap items-baseline gap-x-4 gap-y-1 py-2">
              <span className="min-w-0 flex-1 font-semibold">
                {l.featureLabel} <span className="text-ink-3 font-normal">· {l.reasonLabel}</span>
              </span>
              <span>
                Free <b>{num(l.free)}</b>번 <span className="text-ink-3">({num(l.freeUsers)}명)</span>
              </span>
              <span className="text-text-brand">
                Pro <b>{num(l.pro)}</b>번 <span className="text-ink-3">({num(l.proUsers)}명)</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
