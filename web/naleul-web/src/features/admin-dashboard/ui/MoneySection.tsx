import type { Finance } from '../types'
import { compact, dateTimeLabel, krw, num, usd } from '../format'
import { BarRow, Empty, Panel, Stat } from './Panel'

/** 돈 한 줄 요약: 들어온 돈 − (AI + 서버) = 남은 돈 */
export function ProfitPanel({ finance, activeUsers }: { finance: Finance; activeUsers: number }) {
  const { revenue: r, aiCost: ai, serverCost: s, profit: p } = finance
  return (
    <Panel title="수익 · 비용" aside={`Apple 수수료 ${Math.round(r.appleFeeRate * 100)}% · $1 = ${num(ai.usdKrw)}원`}>
      <div className="grid grid-cols-2 gap-x-4 gap-y-5 sm:grid-cols-4">
        <Stat label="순매출" value={krw(p.netRevenueKrw)} sub={`결제 ${krw(r.grossKrw, true)} · 환불 ${krw(r.refundKrw, true)}`} />
        <Stat label="AI 비용" value={krw(ai.krw)} sub={`${usd(ai.usd)} · ${num(ai.calls)}회 호출`} />
        <Stat label="서버 비용" value={krw(s.periodKrw)} sub={`월 ${krw(s.monthlyKrw, true)} 기준`} />
        <Stat
          label="이익"
          value={krw(p.profitKrw)}
          tone={p.profitKrw >= 0 ? 'good' : 'bad'}
          sub={`비용 합계 ${krw(p.totalCostKrw, true)}`}
        />
      </div>
      <div className="border-line mt-5 grid grid-cols-2 gap-x-4 gap-y-5 border-t pt-5 sm:grid-cols-4">
        <Stat label="월 반복 매출 (MRR 추정)" value={krw(r.estimatedMrrKrw)} sub={`활성 구독 ${num(r.activeSubscriptions)}개`} />
        <Stat
          label="결제 건수"
          value={num(r.purchaseCount + r.renewalCount)}
          sub={`신규 ${num(r.purchaseCount)} · 갱신 ${num(r.renewalCount)} · 환불 ${num(r.refundCount)}`}
        />
        <Stat
          label="활동 사용자 1명당 AI 비용"
          value={krw(p.aiCostPerActiveUserKrw)}
          sub={`활동 사용자 ${num(activeUsers)}명`}
        />
        <Stat label="활동 사용자 1명당 전체 비용" value={krw(p.costPerActiveUserKrw)} />
      </div>
    </Panel>
  )
}

/** AI 비용: 기능별 막대 + 모델별 표 */
export function AiCostPanel({ finance }: { finance: Finance }) {
  const ai = finance.aiCost
  const max = Math.max(...ai.byFeature.map((f) => f.usd), 0)
  return (
    <Panel title="AI 토큰 비용" aside={ai.trackingSince ? `${dateTimeLabel(ai.trackingSince)}부터 기록` : '기록 전'}>
      {ai.byFeature.length === 0 ? (
        <Empty>기간 안에 AI 호출이 없어요</Empty>
      ) : (
        <>
          <ul className="space-y-3">
            {ai.byFeature.map((f) => (
              <BarRow
                key={f.feature}
                label={f.label}
                value={`${krw(f.krw)}`}
                sub={`${num(f.calls)}회${f.failedCalls ? ` (실패 ${f.failedCalls})` : ''} · 1회 ${usd(f.avgUsdPerCall)}`}
                ratio={max ? f.usd / max : 0}
              />
            ))}
          </ul>

          <div className="-mx-1 mt-5 overflow-x-auto">
            <table className="w-full min-w-[420px] text-[12px] tabular-nums">
              <thead>
                <tr className="text-ink-3 text-left">
                  <th className="px-1 pb-1.5 font-medium">모델</th>
                  <th className="px-1 pb-1.5 text-right font-medium">호출</th>
                  <th className="px-1 pb-1.5 text-right font-medium">입력</th>
                  <th className="px-1 pb-1.5 text-right font-medium">출력</th>
                  <th className="px-1 pb-1.5 text-right font-medium">비용</th>
                </tr>
              </thead>
              <tbody className="divide-line divide-y">
                {ai.byModel.map((m) => (
                  <tr key={m.model}>
                    <td className="max-w-[200px] truncate px-1 py-1.5 font-medium">
                      {m.model}
                      {ai.unpricedModels.includes(m.model) && <span className="text-danger ml-1">단가 없음</span>}
                    </td>
                    <td className="px-1 py-1.5 text-right">{num(m.calls)}</td>
                    <td className="px-1 py-1.5 text-right">{compact(m.inputTokens)}</td>
                    <td className="px-1 py-1.5 text-right">{compact(m.outputTokens)}</td>
                    <td className="px-1 py-1.5 text-right font-semibold">{usd(m.usd)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {(ai.cacheReadTokens > 0 || ai.cacheWriteTokens > 0) && (
            <p className="text-ink-3 mt-2 text-[11px]">
              캐시 읽기 {compact(ai.cacheReadTokens)} · 캐시 저장 {compact(ai.cacheWriteTokens)} 토큰 포함
            </p>
          )}
        </>
      )}
    </Panel>
  )
}

/** 상품별 결제 · 서버 비용 항목 */
export function RevenueAndServerPanel({ finance }: { finance: Finance }) {
  const { revenue: r, serverCost: s } = finance
  return (
    <Panel title="상품 · 서버 비용" aside={r.trackingSince ? `결제 금액 ${dateTimeLabel(r.trackingSince)}부터 기록` : '결제 기록 전'}>
      {r.products.length === 0 ? (
        <Empty>결제 · 구독이 아직 없어요</Empty>
      ) : (
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[420px] text-[12px] tabular-nums">
            <thead>
              <tr className="text-ink-3 text-left">
                <th className="px-1 pb-1.5 font-medium">상품</th>
                <th className="px-1 pb-1.5 text-right font-medium">기간 결제</th>
                <th className="px-1 pb-1.5 text-right font-medium">결제 금액</th>
                <th className="px-1 pb-1.5 text-right font-medium">활성 구독</th>
                <th className="px-1 pb-1.5 text-right font-medium">월 환산 가격</th>
              </tr>
            </thead>
            <tbody className="divide-line divide-y">
              {r.products.map((p) => (
                <tr key={p.productId}>
                  <td className="max-w-[180px] truncate px-1 py-1.5 font-medium">{p.productId}</td>
                  <td className="px-1 py-1.5 text-right">{num(p.periodPayments)}</td>
                  <td className="px-1 py-1.5 text-right">{krw(p.periodGrossKrw)}</td>
                  <td className="px-1 py-1.5 text-right">{num(p.activeSubscriptions)}</td>
                  <td className="px-1 py-1.5 text-right">
                    {p.monthlyPriceKrw == null ? <span className="text-danger">가격 미설정</span> : krw(p.monthlyPriceKrw)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3 className="text-ink-2 mt-6 mb-2 text-[13px] font-bold">서버 · 인프라 (월 고정비)</h3>
      {s.items.length === 0 ? (
        <p className="text-ink-3 text-[12px]">설정 전 — 백엔드 application.yml 의 admin.dashboard.server-costs 에 넣어 주세요</p>
      ) : (
        <ul className="divide-line divide-y text-[13px] tabular-nums">
          {s.items.map((i) => (
            <li key={i.name} className="flex justify-between py-1.5">
              <span>{i.name}</span>
              <span>
                <span className="text-ink-3 mr-2 text-[12px]">기간 {krw(i.periodKrw)}</span>
                <b>월 {krw(i.monthlyKrw)}</b>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}
