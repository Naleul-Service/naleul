import { cn } from '@/lib/cn'
import type { AiQuality, AiQualityRow, OutcomeCheck } from '../types'
import { num, pct } from '../format'
import { Empty, Panel, Stat } from './Panel'

/** ①은 높은데 ③이 낮으면 "받아들였지만 현실적이지 않은 계획" — 가장 위험한 경우라 표시해요 */
const RISK_ADOPT = 60
const RISK_RESULT = 50
const isRisky = (r: AiQualityRow) =>
  r.adoptRate != null && r.resultRate != null && r.adoptRate >= RISK_ADOPT && r.resultRate < RISK_RESULT

/**
 * AI 품질 — 모든 AI 기능을 같은 세 단계로 재요.
 *  ① 채택: AI 가 낸 결과를 받아들였나  ② 수정: 받아들이기 전에 얼마나 고쳤나  ③ 결과: 받아들인 뒤 실제로 실천됐나
 */
export function AiQualityPanel({ quality, onOpenDraft }: { quality: AiQuality; onOpenDraft?: () => void }) {
  const rows = quality.features
  return (
    <Panel title="AI 품질 · 채택 → 수정 → 결과" aside="기간 중 제안 · 적용 기준 (결과는 어제까지)">
      {rows.every((r) => !r.adoptBase && !r.resultBase) ? (
        <Empty>기간 안에 AI 제안 기록이 없어요</Empty>
      ) : (
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[720px] text-[13px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left text-[12px]">
                <th className="px-1 pb-2 font-medium">AI 기능</th>
                <th className="px-1 pb-2 font-medium">① 채택</th>
                <th className="px-1 pb-2 font-medium">② 수정</th>
                <th className="px-1 pb-2 font-medium">③ 결과</th>
                <th className="px-1 pb-2 font-medium">보조 지표</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y align-top">
              {rows.map((r) => (
                <tr key={r.key} className={cn(isRisky(r) && 'bg-danger/5')}>
                  <td className="px-1 py-3 font-semibold">
                    {r.label}
                    {isRisky(r) && (
                      <span className="text-danger mt-1 block text-[11px] font-semibold">
                        받아들였지만 실천이 안 돼요
                      </span>
                    )}
                    {r.key === 'GOAL_PLAN' && onOpenDraft && (
                      <button
                        type="button"
                        onClick={onOpenDraft}
                        className="text-text-brand mt-1 block text-[11px] font-semibold hover:underline"
                      >
                        AI 초안 탭에서 자세히 →
                      </button>
                    )}
                  </td>
                  <Cell label={r.adoptLabel} rate={r.adoptRate} base={r.adoptBase} />
                  <Cell label={r.editLabel} rate={r.editRate} base={r.editBase} />
                  <Cell label={r.resultLabel} rate={r.resultRate} base={r.resultBase} strong before={r.beforeRate} />
                  <Cell label={r.extraLabel} rate={r.extraRate} base={r.extraBase} muted />
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-ink-3 mt-3 text-[11px] leading-relaxed">
        n 은 분모예요. 말로 고치기 · AI 일정 조정 · 브레인 덤프는 V64 배포 이후부터 쌓여요. AI 목표 생성은
        V68(2026-10-25) 이후 확정한 목표부터 AI 초안 탭과 같은 정의로 보여주고, 그 전 기간은 예전 방식(첫 초안 · Task
        수정 · 달성률)이에요. 정의는 노션 Tracking Plan 「AI 품질 지표」를 따라요. AI 로 만든 목표가 끝까지 달성 ·
        실천되는지(초안 경로별 목표 달성률, AI 초안을 그대로 둔 루틴 · Task 의 실천률)는 서비스 지표 탭에서 봐요.
      </p>
    </Panel>
  )
}

function Cell({
  label,
  rate,
  base,
  strong,
  muted,
  before,
}: {
  label: string | null
  rate: number | null
  base: number
  strong?: boolean
  muted?: boolean
  before?: number | null
}) {
  if (!label) return <td className="text-ink-3 px-1 py-3">–</td>
  const delta = before != null && rate != null ? rate - before : null
  return (
    <td className="px-1 py-3">
      <p
        className={cn(
          'text-[18px] leading-none font-extrabold',
          muted && 'text-ink-2 text-[15px]',
          strong && 'text-text-brand'
        )}
      >
        {pct(rate)}
        {delta != null && (
          <span className={cn('ml-1.5 text-[12px] font-bold', delta >= 0 ? 'text-success' : 'text-danger')}>
            {delta >= 0 ? '+' : ''}
            {delta}%p
          </span>
        )}
      </p>
      <p className="text-ink-3 mt-1 text-[11px] leading-snug">
        {label} · n {num(base)}
        {before != null && <> · 전 {pct(before)}</>}
      </p>
    </td>
  )
}

/**
 * 자동 판정 신뢰도 — 기한이 지나 서버가 낸 판정이 사용자가 직접 고른 결과와 얼마나 맞는지.
 * 이게 낮으면 "목표 달성률" 숫자를 믿기 어려워요 (자동 판정 기준을 고쳐야 해요).
 */
export function OutcomeCheckPanel({ check }: { check: OutcomeCheck }) {
  const c = check
  return (
    <Panel title="자동 판정 신뢰도" aside="기간 중 끝난 목표">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat
          label="판정 일치율"
          value={pct(c.matchRate)}
          sub={`직접 고른 ${num(c.compared)}개 중 ${num(c.matched)}개`}
          tone={c.matchRate == null ? undefined : c.matchRate >= 70 ? 'good' : 'bad'}
        />
        <Stat label="결과 확인 모달 응답률" value={pct(c.responseRate)} sub={`띄운 ${num(c.autoJudged)}개`} />
        <Stat label="나중에 · 대기" value={`${num(c.dismissed)} · ${num(c.pending)}`} sub="자동 판정 그대로" />
        <Stat label="80% 이상이라 자동 달성" value={num(c.metric80)} sub="수치 80~99% 목표" />
      </div>
      {c.byReason.length > 0 && (
        <ul className="divide-line mt-4 divide-y text-[13px] tabular-nums">
          {c.byReason.map((r) => (
            <li key={r.reason} className="flex items-center justify-between py-2">
              <span className="text-ink-2">{r.label}</span>
              <span>
                <b>{pct(r.matchRate)}</b>
                <span className="text-ink-3 ml-2 text-[12px]">
                  {num(r.matched)}/{num(r.compared)}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
      <p className="text-ink-3 mt-3 text-[11px] leading-relaxed">
        일치율 = 자동 판정과 사용자가 직접 고른 결과(모달 · 목표 마무리 · 목표 상세)가 같은 비율. 70% 아래면 판정
        기준(수치 80% · 실천률 80%/50%)을 다시 봐야 해요.
      </p>
    </Panel>
  )
}
