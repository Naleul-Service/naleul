/**
 * JWT payload 의 exp(만료 시각, 초 단위)를 읽어요.
 * ⚠️ 서명 검증은 하지 않아요. "언제 만료되는지" 판단용이고, 진짜 검증은 백엔드가 해요.
 * Node / Edge 둘 다 동작하도록 atob 를 사용해요.
 */
export function getJwtExp(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = JSON.parse(atob(padded)) as { exp?: unknown }
    return typeof json.exp === 'number' ? json.exp : null
  } catch {
    return null
  }
}

/**
 * 만료됐거나 leewaySec 초 안에 만료되면 true.
 * exp 를 읽을 수 없으면 false (= 판단 불가 → 백엔드에 맡김)
 */
export function isJwtExpired(token: string, leewaySec = 60): boolean {
  const exp = getJwtExp(token)
  if (exp === null) return false
  return exp - leewaySec <= Math.floor(Date.now() / 1000)
}

/** 쿠키 maxAge(초) 계산: 토큰 만료까지 남은 시간. 못 읽으면 fallback */
export function secondsUntilExp(token: string, fallbackSec: number): number {
  const exp = getJwtExp(token)
  if (exp === null) return fallbackSec
  return Math.max(exp - Math.floor(Date.now() / 1000), 0)
}
