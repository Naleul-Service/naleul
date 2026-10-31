const DASH = '–'

/** 43900 → "43,900원" · 1250000 → "125만원" (짧게) */
export function krw(n: number | null | undefined, short = false) {
  if (n == null) return DASH
  const sign = n < 0 ? '-' : ''
  const abs = Math.abs(n)
  if (short && abs >= 10_000) {
    const man = abs / 10_000
    return `${sign}${man >= 100 ? Math.round(man).toLocaleString() : man.toFixed(1).replace(/\.0$/, '')}만원`
  }
  return `${sign}${abs.toLocaleString()}원`
}

/** 0.0456 → "$0.046" · 12.3 → "$12.30" */
export function usd(n: number | null | undefined) {
  if (n == null) return DASH
  if (n === 0) return '$0'
  return n < 1 ? `$${n.toFixed(3)}` : `$${n.toFixed(2)}`
}

export const pct = (n: number | null | undefined) => (n == null ? DASH : `${n}%`)

export const num = (n: number | null | undefined) => (n == null ? DASH : n.toLocaleString())

/** 1234567 → "123만" · 12345 → "1.2만" · 999 → "999" (토큰 수) */
export function compact(n: number) {
  if (n >= 10_000) {
    const man = n / 10_000
    return `${man >= 100 ? Math.round(man).toLocaleString() : man.toFixed(1).replace(/\.0$/, '')}만`
  }
  return n.toLocaleString()
}

/** "2026-10-01" → "10.01" (Date 로 파싱하지 않아요 — UTC 로 해석돼 하루 밀리는 문제 방지) */
export const dotDate = (ymd: string) => `${ymd.slice(5, 7)}.${ymd.slice(8, 10)}`

/** "2026-10-09T13:42:10" → "10월 9일 13:42" */
export function dateTimeLabel(iso: string | null | undefined) {
  if (!iso) return DASH
  const [d, t] = iso.split('T')
  if (!d || !t) return iso
  return `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일 ${t.slice(0, 5)}`
}
