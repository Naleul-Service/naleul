'use client'

import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { useAhaMoment } from '../api'
import type { AhaMoment } from '../types'
import { dotDate, num, pct } from '../format'
import { BarRow, Empty, Panel, Stat } from './Panel'

/** 도달률이 이 범위면 기준이 너무 쉽거나 어렵지 않아요 (노션 「Aha-moment · 기준 설명」) */
const TARGET_MIN = 10
const TARGET_MAX = 30
/** 도달자 − 미도달자 D14 리텐션 차이가 이만큼(%p) 이상이면 "의미 있는 기준" */
const GAP_GOOD = 10

const DIST_TITLE = { goals: '7일 안에 만든 목표', tasks: '7일 안에 완료한 Task', visitDays: '7일 중 온 날' } as const

/**
 * Aha-moment (운영진 전용, V69) — 사용자에게는 보이지 않아요.
 * 가입 후 7일 안에 ① 목표 2개 생성 ② Task 3개 이상 완료(루틴 · 일반) ③ 서로 다른 3일 이상 방문 → 셋 다 = 도달.
 * 기간은 가입일 기준이고, 가입 후 7일이 지난 사람만 판정해요.
 */
export function AhaPanel({ days }: { days: number }) {
  const { data, isPending, isError, isFetching } = useAhaMoment(days)
  return (
    <Panel
      title="Aha-moment"
      aside={
        data
          ? `가입일 기준 · 가입 ${num(data.signups)}명 · 판정 ${num(data.eligible)}명 (7일 안 지난 ${num(data.pending)}명 제외)`
          : '가입일 기준'
      }
    >
      <div className="bg-brand-soft text-text-brand mb-4 rounded-xl px-4 py-3 text-[13px] font-semibold">
        가입 후 7일 안에 ① 목표 2개 생성 + ② Task 3개 이상 완료(루틴 · 일반) + ③ 서로 다른 3일 이상 방문
        {isFetching && <Spinner className="ml-2 inline size-3.5" />}
      </div>
      {isPending ? (
        <div className="grid min-h-[160px] place-items-center">
          <Spinner className="text-ink-3 size-5" />
        </div>
      ) : isError || !data ? (
        <Empty>Aha-moment 를 불러오지 못했어요</Empty>
      ) : data.signups === 0 ? (
        <Empty>기간 안에 가입한 사람이 없어요 (가입 시각은 V69 배포 이후 가입자부터 쌓여요)</Empty>
      ) : (
        <div className="space-y-4">
          <Headline data={data} />
          <div className="grid gap-4 lg:grid-cols-2">
            <Funnel data={data} />
            <Validation data={data} />
          </div>
          <Distributions data={data} />
          <Cohorts data={data} />
          <p className="text-ink-3 text-[11px] leading-relaxed">
            온 날 = 오늘의 TimeBlock · Task 화면(홈 오늘 · 타임테이블)을 본 날 ∪ Task 를 완료한 날 ∪ 실제 한 일을 추가한
            날 (앱 실행만으로는 안 셈) · 목표는 임시 목표를 빼고, 나중에 지운 목표도 &ldquo;만든 것&rdquo;으로 셈 ·{' '}
            {data.visitTrackingSince
              ? `화면 방문 기록은 ${dotDate(data.visitTrackingSince)}부터 (그 전 날은 Task 완료 · 실제 한 일로만 셈)`
              : '화면 방문 기록은 배포 이후부터 쌓여요'}{' '}
            · 운영진 계정 제외 · 표본 {data.minSample}명 미만은 흐리게
          </p>
        </div>
      )}
    </Panel>
  )
}

function Headline({ data }: { data: AhaMoment }) {
  const few = data.eligible < data.minSample
  const inRange = data.aha.rate != null && data.aha.rate >= TARGET_MIN && data.aha.rate <= TARGET_MAX
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div className="border-brand bg-brand-soft rounded-xl border p-4">
        <Stat
          label={<span className="text-text-brand font-semibold">Aha 도달률 (셋 다)</span>}
          value={<span className={cn('text-text-brand', few && 'opacity-50')}>{pct(data.aha.rate)}</span>}
          sub={`${num(data.aha.users)} / ${num(data.eligible)}명 · 목표 범위 ${TARGET_MIN}~${TARGET_MAX}% ${
            data.aha.rate == null ? '' : inRange ? '✓' : '벗어남'
          }`}
        />
      </div>
      {data.conditions.map((c, i) => (
        <div key={c.key} className="border-line rounded-xl border p-4">
          <Stat
            label={`${'①②③'[i]} ${c.label}`}
            value={<span className={cn(few && 'opacity-50')}>{pct(c.rate)}</span>}
            sub={`${num(c.users)} / ${num(data.eligible)}명`}
          />
        </div>
      ))}
    </div>
  )
}

function Funnel({ data }: { data: AhaMoment }) {
  return (
    <section className="border-line rounded-xl border p-4">
      <h3 className="text-[14px] font-bold">어디서 멈추나</h3>
      <p className="text-ink-3 mt-0.5 text-[12px]">
        조건을 순서대로 쌓았을 때 남는 사람 (판정 대상 {num(data.eligible)}명 기준)
      </p>
      <ul className="mt-3 space-y-3">
        {data.funnel.map((s) => (
          <BarRow
            key={s.label}
            label={s.label}
            value={pct(s.rate)}
            sub={`${num(s.users)}명`}
            ratio={(s.rate ?? 0) / 100}
          />
        ))}
      </ul>
    </section>
  )
}

function Validation({ data }: { data: AhaMoment }) {
  const r = data.retention
  const good = r.gapPp != null && r.gapPp >= GAP_GOOD
  const few = r.reachedBase < data.minSample || r.notReachedBase < data.minSample
  return (
    <section className="border-line rounded-xl border p-4">
      <h3 className="text-[14px] font-bold">기준 검증 · D14 리텐션</h3>
      <p className="text-ink-3 mt-0.5 text-[12px]">
        가입 14~20일째에 다시 온 비율 (가입 후 21일 지난 {num(r.overallBase)}명) — 도달자와 미도달자 차이가 커야 좋은
        기준
      </p>
      <div className={cn('mt-3 grid grid-cols-3 gap-3', few && 'opacity-60')}>
        <Stat label="도달자" value={pct(r.reachedRate)} sub={`${num(r.reachedRetained)} / ${num(r.reachedBase)}명`} />
        <Stat
          label="미도달자"
          value={pct(r.notReachedRate)}
          sub={`${num(r.notReachedRetained)} / ${num(r.notReachedBase)}명`}
        />
        <Stat
          label="차이"
          value={r.gapPp == null ? '–' : `${r.gapPp > 0 ? '+' : ''}${r.gapPp}%p`}
          sub={`전체 ${pct(r.overallRate)}`}
          tone={r.gapPp == null ? undefined : good ? 'good' : 'bad'}
        />
      </div>
      <p className={cn('mt-3 text-[12px] font-semibold', good ? 'text-success' : 'text-ink-3')}>
        {r.gapPp == null
          ? '아직 비교할 사람이 없어요'
          : good
            ? `도달하면 D14 리텐션이 ${r.gapPp}%p 높아요 → 의미 있는 기준`
            : `차이가 ${GAP_GOOD}%p 미만 → 조건(목표 수 · Task 수 · 방문일)을 바꿔 비교해 보세요`}
        {few && ' · 표본이 적어 아직 참고만'}
      </p>
    </section>
  )
}

function Distributions({ data }: { data: AhaMoment }) {
  return (
    <section className="border-line rounded-xl border p-4">
      <h3 className="text-[14px] font-bold">분포 — 기준(2개 · 3개 · 3일)이 적당한지</h3>
      <div className="mt-3 grid gap-4 sm:grid-cols-3">
        {(Object.keys(DIST_TITLE) as (keyof typeof DIST_TITLE)[]).map((k) => (
          <div key={k}>
            <p className="text-ink-2 text-[12px] font-semibold">{DIST_TITLE[k]}</p>
            <ul className="mt-2 space-y-2">
              {data.distributions[k].map((b) => (
                <BarRow
                  key={b.label}
                  label={b.label}
                  value={pct(b.share)}
                  sub={`${num(b.users)}명`}
                  ratio={(b.share ?? 0) / 100}
                />
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

function Cohorts({ data }: { data: AhaMoment }) {
  if (data.cohorts.length === 0) return null
  return (
    <section>
      <h3 className="text-[14px] font-bold">가입 주별</h3>
      <div className="-mx-1 mt-2 overflow-x-auto">
        <table className="w-full min-w-[760px] text-[13px] tabular-nums">
          <thead>
            <tr className="text-ink-3 text-left text-[12px]">
              <th className="px-2 pb-2 font-medium">가입 주</th>
              <th className="px-2 pb-2 font-medium">가입 · 판정</th>
              <th className="px-2 pb-2 font-medium">① 목표 2개</th>
              <th className="px-2 pb-2 font-medium">② Task 3개</th>
              <th className="px-2 pb-2 font-medium">③ 3일 방문</th>
              <th className="px-2 pb-2 font-medium">Aha 도달</th>
              <th className="px-2 pb-2 font-medium">D14 (도달 / 미도달)</th>
            </tr>
          </thead>
          <tbody className="divide-line divide-y">
            {data.cohorts.map((c) => (
              <tr key={c.weekStart} className={cn(c.eligible < data.minSample && 'text-ink-3')}>
                <td className="px-2 py-2.5 font-semibold">{dotDate(c.weekStart)} 주</td>
                <td className="px-2 py-2.5">
                  {num(c.signups)} · {num(c.eligible)}
                </td>
                <td className="px-2 py-2.5">{pct(c.goalsRate)}</td>
                <td className="px-2 py-2.5">{pct(c.tasksRate)}</td>
                <td className="px-2 py-2.5">{pct(c.visitsRate)}</td>
                <td className="text-text-brand px-2 py-2.5 font-bold">{pct(c.ahaRate)}</td>
                <td className="px-2 py-2.5">
                  {pct(c.d14Reached)} / {pct(c.d14NotReached)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
