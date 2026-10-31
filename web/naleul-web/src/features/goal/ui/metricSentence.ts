/**
 * 수치 목표를 한 문장으로 — 숫자 칸만 보면 "무슨 뜻인지" 알기 어려워서 바로 아래에 보여줘요.
 *   "체중 80kg → 72kg, 1월 8일까지 · 주 평균 -0.6kg"
 */
export function metricSentence(v: {
  name: string
  unit: string
  start: number | null
  target: number | null
  startDate: string | null
  endDate: string | null
}): string | null {
  const { start, target } = v
  if (start == null || target == null || Number.isNaN(start) || Number.isNaN(target)) return null
  const unit = v.unit.trim()
  const name = v.name.trim() || '수치'
  const fmt = (n: number) => `${Number(n.toFixed(2))}${unit}`
  let text = `${name} ${fmt(start)} → ${fmt(target)}`
  if (v.endDate) {
    const [, m, d] = v.endDate.split('-').map(Number)
    text += `, ${m}월 ${d}일까지`
  }
  if (v.startDate && v.endDate && v.endDate > v.startDate) {
    const days = (Date.parse(v.endDate) - Date.parse(v.startDate)) / 86_400_000
    const perWeek = (target - start) / Math.max(days / 7, 1)
    if (Number.isFinite(perWeek) && perWeek !== 0) {
      text += ` · 주 평균 ${perWeek > 0 ? '+' : ''}${Number(perWeek.toFixed(1))}${unit}`
    }
  }
  return text
}
