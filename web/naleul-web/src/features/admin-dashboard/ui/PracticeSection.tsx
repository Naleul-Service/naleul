import { cn } from '@/lib/cn'
import type { GoalPathRow, InProgressGroup, OutcomeCheck, Practice } from '../types'
import { dotDate, num, pct } from '../format'
import { Empty, Panel, Stat } from './Panel'

/** 이 실천률 이상이면 "실천 높음" — 목표 결과에서 "유지된 루틴"을 세는 기준과 같아요 */
const PRACTICE_HIGH = 60
/** 표본이 이보다 적으면 흐리게 (다른 탭과 같은 기준) */
const MIN_SAMPLE = 30
/** 판정 일치율이 이보다 낮으면 달성률을 믿기 어려워요 (AI 품질 탭 › 자동 판정 신뢰도) */
const MATCH_OK = 70

/**
 * 목표 달성률 옆에 붙이는 "이 숫자를 믿어도 되나" 한 줄.
 * 달성 여부는 서버 자동 판정이라, 사용자가 직접 고른 결과와 얼마나 같은지(일치율)를 같이 보여줘요.
 */
export function TrustLine({ check }: { check?: OutcomeCheck }) {
  if (!check) return null
  const ok = check.matchRate != null && check.matchRate >= MATCH_OK
  return (
    <p className={cn('mt-2 text-[12px]', check.matchRate == null ? 'text-ink-3' : ok ? 'text-success' : 'text-danger')}>
      자동 판정 일치율 <b className="tabular-nums">{pct(check.matchRate)}</b>
      <span className="text-ink-3">
        {' '}
        · 직접 고른 {num(check.compared)}개 중 {num(check.matched)}개
        {check.matchRate == null
          ? ' — 아직 비교할 목표가 없어요'
          : ok
            ? ''
            : ` — ${MATCH_OK}% 아래라 달성률 해석에 주의`}
        {check.compared > 0 && check.compared < MIN_SAMPLE && ' · 표본이 적어 참고만'}
      </span>
    </p>
  )
}

/** 판단 기준 (노션 「리텐션 향상 › 판단 기준 · AI 수정률 × 실천률」) */
function reading(r: GoalPathRow): { text: string; tone: 'good' | 'bad' | 'mid' } | null {
  const rate = r.avgRoutineRate ?? r.avgTaskRate
  if (rate == null || r.key === 'AI_LEGACY' || r.key === 'MANUAL_GOAL') return null
  const high = rate >= PRACTICE_HIGH
  switch (r.key) {
    case 'AS_IS':
      return high
        ? { text: '수정 적음 + 실천 높음 → AI가 잘 맞음', tone: 'good' }
        : { text: '수정 적음 + 실천 낮음 → 일단 수락 · "내 목표" 느낌 없음', tone: 'bad' }
    case 'REGEN':
      return high
        ? { text: '다시 만들면 맞음 → 첫 초안 개선 여지', tone: 'mid' }
        : { text: '다시 만들어도 실천 낮음 → AI 품질 문제 + 이탈 위험', tone: 'bad' }
    default:
      return high
        ? { text: '수정 많음 + 실천 높음 → 다듬으며 몰입 (AI 개선 여지)', tone: 'mid' }
        : { text: '수정 많음 + 실천 낮음 → AI 품질 문제 + 이탈 위험', tone: 'bad' }
  }
}

/**
 * Main 핵심 지표 · 목표 달성률을 AI 초안을 어떻게 받아들였는지(경로)로 쪼개요 (V68, 2026-10-25).
 * "AI로 만든 목표가 잘 달성되나"를 그대로 확정 · 고쳐서 확정 · 다시 생성 · 직접 만든 목표와 나란히 비교.
 */
export function GoalPathPanel({ rows, inProgress }: { rows?: GoalPathRow[]; inProgress?: InProgressGroup[] }) {
  return (
    <Panel title="목표 달성률 · AI 초안을 어떻게 받아들였나" aside="끝난 목표 (결과) · 진행 중 목표 (과정)">
      {!rows || rows.length === 0 ? (
        <Empty>기간 안에 끝난 목표가 없어요</Empty>
      ) : (
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[860px] text-[13px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left text-[12px]">
                <th className="px-1 pb-2 font-medium">만든 방법 · 경로</th>
                <th className="px-1 pb-2 text-right font-medium">끝난 목표</th>
                <th className="px-1 pb-2 text-right font-medium">달성률</th>
                <th className="px-1 pb-2 text-right font-medium">성공률</th>
                <th className="px-1 pb-2 text-right font-medium">루틴 실천률</th>
                <th className="px-1 pb-2 text-right font-medium">Task 실천률</th>
                <th className="px-1 pb-2 text-right font-medium">초안 보존율</th>
                <th className="px-1 pb-2 pl-4 font-medium">읽는 법</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {rows.map((r) => {
                const hint = reading(r)
                return (
                  <tr key={r.key} className={cn(r.ended < MIN_SAMPLE && 'opacity-60')}>
                    <td className="px-1 py-2">
                      <p className={cn('font-semibold', r.key === 'MANUAL_GOAL' && 'text-ink-2')}>{r.label}</p>
                      <p className="text-ink-3 text-[11px]">직접 고른 결과 {num(r.userPicked)}개</p>
                    </td>
                    <td className="px-1 py-2 text-right">{num(r.ended)}</td>
                    <td className="px-1 py-2 text-right font-bold">{pct(r.achievementRate)}</td>
                    <td className="text-ink-2 px-1 py-2 text-right">{pct(r.successRate)}</td>
                    <td className="text-ink-2 px-1 py-2 text-right">{pct(r.avgRoutineRate)}</td>
                    <td className="text-ink-2 px-1 py-2 text-right">{pct(r.avgTaskRate)}</td>
                    <td className="text-ink-2 px-1 py-2 text-right">{pct(r.avgKeptRate)}</td>
                    <td
                      className={cn(
                        'px-1 py-2 pl-4 text-[12px]',
                        hint?.tone === 'good' && 'text-success',
                        hint?.tone === 'bad' && 'text-danger',
                        (!hint || hint.tone === 'mid') && 'text-ink-2'
                      )}
                    >
                      {hint?.text ?? '–'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {inProgress && inProgress.length > 0 && (
        <div className="border-line mt-4 border-t pt-4">
          <p className="text-ink-3 text-[12px] font-semibold">진행 중 목표 실천률 — 끝나기 전에 미리 보는 신호</p>
          <ul className="mt-2 grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {inProgress.map((g) => (
              <li
                key={g.key}
                className={cn('rounded-xl border p-3', g.key === 'AI' ? 'border-brand bg-brand-soft' : 'border-line')}
              >
                <Stat
                  label={g.label}
                  value={pct(g.avgExecutionRate)}
                  sub={`목표 ${num(g.goals)}개 · 순항 ${pct(g.onTrackRate)}`}
                />
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-ink-3 mt-3 text-[11px] leading-relaxed">
        경로 = AI 초안 화면에서 가장 강한 신호 1개 (다시 생성 &gt; 말로 고치기 &gt; 직접 수정 &gt; 그대로 확정) · 성공률
        = 달성 + 부분 달성 · 초안 보존율 = 확정 계획에 AI 값이 그대로 남은 칸 ÷ AI 가 만든 칸 · 읽는 법은 루틴
        실천률(없으면 Task 실천률) {PRACTICE_HIGH}% 기준 · 순항 = 실천률 80% 이상 · V68(2026-10-25) 전에 확정한 AI
        목표는 경로 기록이 없어 따로 묶어요 · 끝난 목표 {MIN_SAMPLE}개 미만은 흐리게
      </p>
    </Panel>
  )
}

/**
 * Sub 핵심 지표 · Task 실천률을 "잘 실천하고 있는지" 쪼개요 (2026-10-25).
 * A(전체) vs B(그날 앱을 쓴 사람만) — B 가 높고 A 가 낮으면 계획이 아니라 이탈이 문제.
 * 출처별 — AI 초안 그대로 둔 것 · 고친 것 · 사용자가 더한 것 · 직접 목표 · 목표 없음.
 */
export function PracticePanel({
  practice,
  taskRate,
  routineRate,
}: {
  practice?: Practice
  taskRate: number | null
  routineRate: number | null
}) {
  if (!practice) return null
  const b = practice.activeDay
  const gap = (x: number | null, y: number | null) => (x == null || y == null ? null : x - y)
  const taskGap = gap(b.taskRate, taskRate)
  const routineGap = gap(b.routineRate, routineRate)
  return (
    <Panel title="Task 실천률 · 잘 실천하고 있나" aside="하기로 한 날이 기간 안인 Task · 루틴 회차">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="border-line rounded-xl border p-3">
          <Stat label="A · Task 실천률 (전체)" value={pct(taskRate)} sub="해야 했던 Task 전체" />
        </div>
        <div className="border-brand bg-brand-soft rounded-xl border p-3">
          <Stat
            label={<span className="text-text-brand font-semibold">B · Task 실천률 (쓴 날만)</span>}
            value={pct(b.taskRate)}
            sub={`${num(b.taskDone)} / ${num(b.taskDue)}개 · A 와 ${taskGap == null ? '–' : `${taskGap > 0 ? '+' : ''}${taskGap}%p`}`}
          />
        </div>
        <div className="border-line rounded-xl border p-3">
          <Stat label="A · 루틴 실천률 (전체)" value={pct(routineRate)} sub="해야 했던 루틴 회차 전체" />
        </div>
        <div className="border-brand bg-brand-soft rounded-xl border p-3">
          <Stat
            label={<span className="text-text-brand font-semibold">B · 루틴 실천률 (쓴 날만)</span>}
            value={pct(b.routineRate)}
            sub={`${num(b.routineDone)} / ${num(b.routineDue)}회 · A 와 ${routineGap == null ? '–' : `${routineGap > 0 ? '+' : ''}${routineGap}%p`}`}
          />
        </div>
      </div>
      <p className="text-ink-2 mt-2 text-[12px]">
        B 가 높고 A 가 낮으면 → 계획이 아니라 <b>앱에 안 들어온 날</b>이 문제 (리텐션 · 알림) · B 도 낮으면 → 계획 분량
        · 시간이 현실적이지 않음 (AI 초안 · 배치)
      </p>

      <h3 className="mt-5 text-[14px] font-bold">어디서 온 Task 인가 — AI 초안을 그대로 둔 것이 잘 지켜지나</h3>
      {practice.origins.length === 0 ? (
        <Empty>기간 안에 해야 했던 Task 가 없어요</Empty>
      ) : (
        <div className="-mx-1 mt-2 overflow-x-auto">
          <table className="w-full min-w-[640px] text-[13px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left text-[12px]">
                <th className="px-1 pb-2 font-medium">출처</th>
                <th className="px-1 pb-2 text-right font-medium">루틴 실천률</th>
                <th className="px-1 pb-2 text-right font-medium">루틴 회차</th>
                <th className="px-1 pb-2 text-right font-medium">Task 실천률</th>
                <th className="px-1 pb-2 text-right font-medium">Task</th>
                <th className="px-1 pb-2 text-right font-medium">사람</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {practice.origins.map((o) => (
                <tr key={o.key} className={cn(o.routineDue + o.taskDue < MIN_SAMPLE && 'opacity-60')}>
                  <td className={cn('px-1 py-2 font-semibold', !o.key.startsWith('AI_') && 'text-ink-2')}>{o.label}</td>
                  <td className="px-1 py-2 text-right font-bold">{pct(o.routineRate)}</td>
                  <td className="text-ink-3 px-1 py-2 text-right">
                    {num(o.routineDone)} / {num(o.routineDue)}
                  </td>
                  <td className="px-1 py-2 text-right font-bold">{pct(o.taskRate)}</td>
                  <td className="text-ink-3 px-1 py-2 text-right">
                    {num(o.taskDone)} / {num(o.taskDue)}
                  </td>
                  <td className="text-ink-3 px-1 py-2 text-right">{num(o.users)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-ink-3 mt-3 text-[11px] leading-relaxed">
        쓴 날 = 오늘 화면(홈 오늘 · 타임테이블)을 본 날 ∪ Task 를 완료한 날 ∪ 실제 한 일을 추가한 날 ·{' '}
        {practice.visitTrackingSince
          ? `화면 방문 기록은 ${dotDate(practice.visitTrackingSince)}부터 (그 전 날은 완료 · 실제 한 일로만 셈 → B 가 조금 높게 나와요)`
          : '화면 방문 기록은 배포 이후부터 쌓여요 (그 전엔 완료 · 실제 한 일로만 셈 → B 가 조금 높게 나와요)'}{' '}
        · 출처의 AI 초안 구분은 V68(2026-10-25) 이후 확정한 목표부터 · 그대로 둔 것 vs 고친 것의 실천률 차이는 AI 초안
        탭 「자주 고치는 방향」에서 칸 단위로 · 합이 {MIN_SAMPLE} 미만은 흐리게
      </p>
    </Panel>
  )
}
