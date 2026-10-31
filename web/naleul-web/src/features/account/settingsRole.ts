/**
 * 설정 화면에서 서버 컴포넌트와 클라이언트 컴포넌트가 함께 쓰는 값.
 * ⚠️ 'use client' 파일에 두면 서버(page.tsx)에서 호출할 때 오류가 나요 → 일반 모듈로 분리
 */

/** 설정 메뉴 줄의 destination 값 (settings.menu_click) */
export type SettingsDestination =
  'membership' | 'life_pattern' | 'calendar_import' | 'notifications' | 'terms' | 'withdraw' | 'dashboard'

/**
 * 쿠키의 역할로 Pro 여부 (이벤트 is_premium · 화면 표시).
 * 운영진(ADMIN)은 "플랜 바꿔 보기"로 고른 플랜을 따라요 (기본 PRO) — 2026-10-24
 */
export const isPremiumRole = (role: string | undefined, adminViewPlan?: string) =>
  role === 'PRO' || (role === 'ADMIN' && adminViewPlan !== 'FREE')
