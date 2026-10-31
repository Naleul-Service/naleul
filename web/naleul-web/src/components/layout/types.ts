export interface SessionUserView {
  name: string
  /** GUEST = 로그인 없이 둘러보기 */
  role?: 'FREE' | 'PRO' | 'ADMIN' | 'GUEST'
  /** 운영진이 보고 있는 플랜 (role 이 ADMIN 일 때만, 없으면 PRO) */
  adminViewPlan?: 'FREE' | 'PRO'
}

export const isGuestUser = (u?: SessionUserView) => u?.role === 'GUEST'

/**
 * 화면에 쓰는 플랜 — 운영진은 고른 플랜(기본 PRO)으로 바꿔서 광고 · 배지 · Pro 잠금을 실제 사용자처럼 보여줘요.
 * 운영진인지는 role === 'ADMIN' 으로 따로 봐요.
 */
export const planRole = (u?: SessionUserView): 'FREE' | 'PRO' | 'GUEST' =>
  u?.role === 'ADMIN'
    ? u.adminViewPlan === 'FREE'
      ? 'FREE'
      : 'PRO'
    : u?.role === 'PRO'
      ? 'PRO'
      : u?.role === 'GUEST'
        ? 'GUEST'
        : 'FREE'
