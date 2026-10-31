import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import type { Goals, GoalGroupRow, OutcomeCheck } from '../types'
import { pct } from '../format'
import { Empty, Panel } from './Panel'
import { TrustLine } from './PracticeSection'

const HIST_LABELS = ['0–19', '20–39', '40–59', '60–79', '80–100']

/**
 * 핵심 지표 — 전체 사용자의 목표 달성률.
 *  왼쪽: 기간 중 끝난 목표의 결과 (달성 · 부분 · 미달성)
 *  오른쪽: 지금 진행 중인 목표들이 얼마나 계획대로 가고 있는지 (실천률 분포)
 */
export function GoalHero({ goals, check }: { goals: Goals; check?: OutcomeCheck }) {
  const g = goals
  const seg = (n: number) => (g.ended ? (n / g.ended) * 100 : 0)
  const histMax = Math.max(...g.executionHistogram, 1)

  return (
    <Card className="grid gap-6 p-5 sm:p-6 lg:grid-cols-2">
      <div>
        <p className="text-text-brand text-[13px] font-bold">핵심 지표 · 목표 달성률</p>
        <div className="mt-2 flex items-end gap-3">
          <p className="text-[52px] leading-none font-extrabold tracking-tight tabular-nums">
            {pct(g.achievementRate)}
          </p>
          <p className="text-ink-3 pb-1.5 text-[13px]">
            끝난 목표 {g.ended.toLocaleString()}개 중 달성 {g.achieved.toLocaleString()}개
          </p>
        </div>
        <p className="text-ink-2 mt-2 text-[13px]">
          부분 달성까지 포함하면 <b className="tabular-nums">{pct(g.successRate)}</b>
        </p>
        <TrustLine check={check} />

        {g.ended > 0 ? (
          <>
            <div className="bg-subtle mt-4 flex h-3 overflow-hidden rounded-full" aria-hidden>
              <div className="bg-brand" style={{ width: `${seg(g.achieved)}%` }} />
              <div className="bg-brand/40" style={{ width: `${seg(g.partial)}%` }} />
              <div className="bg-line-strong" style={{ width: `${seg(g.notAchieved)}%` }} />
            </div>
            <ul className="text-ink-2 mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[12px] tabular-nums">
              <Legend className="bg-brand" label="달성" n={g.achieved} />
              <Legend className="bg-brand/40" label="부분 달성" n={g.partial} />
              <Legend className="bg-line-strong" label="미달성" n={g.notAchieved} />
            </ul>
          </>
        ) : (
          <p className="text-ink-3 mt-4 text-[13px]">기간 안에 끝난 목표가 없어요</p>
        )}
      </div>

      <div className="border-line lg:border-l lg:pl-6">
        <p className="text-ink-3 text-[13px] font-semibold">진행 중 목표 {g.inProgress.toLocaleString()}개의 실천률</p>
        <div className="mt-2 flex items-end gap-5">
          <div>
            <p className="text-[32px] leading-none font-extrabold tabular-nums">{pct(g.avgExecutionRate)}</p>
            <p className="text-ink-3 mt-1 text-[12px]">평균 실천률</p>
          </div>
          <div>
            <p className="text-success text-[32px] leading-none font-extrabold tabular-nums">{pct(g.onTrackRate)}</p>
            <p className="text-ink-3 mt-1 text-[12px]">순항 중 (실천률 80% 이상)</p>
          </div>
        </div>
        <div className="mt-4 flex h-24 items-end gap-2" role="img" aria-label="진행 중 목표의 실천률 분포">
          {g.executionHistogram.map((n, i) => (
            <div key={HIST_LABELS[i]} className="flex flex-1 flex-col items-center gap-1">
              <span className="text-ink-2 text-[11px] font-semibold tabular-nums">{n}</span>
              <div
                className={cn('w-full rounded-t-md', i === 4 ? 'bg-success' : 'bg-brand/30')}
                style={{ height: `${(n / histMax) * 64}px`, minHeight: n ? 3 : 0 }}
              />
              <span className="text-ink-3 text-[10px] tabular-nums">{HIST_LABELS[i]}%</span>
            </div>
          ))}
        </div>
        <p className="text-ink-3 mt-3 text-[11px] leading-relaxed">
          실천률 = 목표 시작부터 오늘까지 해야 했던 Task·루틴 중 완료한 비율 (임시 목표 · 기록형 목표 제외). 80% 는
          기간이 끝났을 때 &quot;달성&quot;으로 자동 판정되는 기준이에요.
        </p>
      </div>
    </Card>
  )
}

function Legend({ className, label, n }: { className: string; label: string; n: number }) {
  return (
    <li className="inline-flex items-center gap-1.5">
      <span className={cn('inline-block size-2.5 rounded-sm', className)} />
      {label} {n.toLocaleString()}
    </li>
  )
}

/** 목표 종류별 · 만든 방법별(AI / 직접) 달성률 표 */
export function GoalGroups({ title, rows, aside }: { title: string; rows: GoalGroupRow[]; aside?: string }) {
  return (
    <Panel title={title} aside={aside}>
      {rows.length === 0 ? (
        <Empty>기간 안에 끝난 목표가 없어요</Empty>
      ) : (
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[420px] text-[13px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left text-[12px]">
                <th className="px-1 pb-2 font-medium">구분</th>
                <th className="px-1 pb-2 text-right font-medium">끝난 목표</th>
                <th className="px-1 pb-2 text-right font-medium">달성률</th>
                <th className="px-1 pb-2 text-right font-medium">루틴 실천률</th>
                <th className="px-1 pb-2 text-right font-medium">Task 실천률</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {rows.map((r) => (
                <tr key={r.key}>
                  <td className="px-1 py-2 font-semibold">{r.label}</td>
                  <td className="px-1 py-2 text-right">{r.ended.toLocaleString()}</td>
                  <td className="px-1 py-2 text-right font-bold">{pct(r.achievementRate)}</td>
                  <td className="text-ink-2 px-1 py-2 text-right">{pct(r.avgRoutineRate)}</td>
                  <td className="text-ink-2 px-1 py-2 text-right">{pct(r.avgTaskRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}
