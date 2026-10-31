/** URL 의 "101" → 101. 숫자가 아니면 null */
export function parseSessionId(value: string | undefined): number | null {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}
