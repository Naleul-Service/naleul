import type { CSSProperties } from 'react'

/**
 * 목표 색 (서버 colorCode = 아무 hex) → 디자인 시스템의 목표 팔레트 21종 중 하나로 맞춰요.
 *  - 팔레트의 "라이트 accent" 와 RGB 거리가 가장 가까운 색을 고르고 (거리 ≤ 60)
 *  - 너무 멀면 그 hex 로 바탕(bg)·글자(fg) 를 만들어요 (명세: bg = surface 위 12%/22%, fg = 45% 어둡게 / 60% 밝게)
 *  - 비어 있으면 neutral
 * 화면에서는 CSS 변수(var(--color-goal-<name>-*)) 로 쓰니 다크 모드도 저절로 따라가요.
 * iOS · Android 도 같은 규칙이에요.
 */

type Triple = readonly [bg: string, fg: string, accent: string]

export const GOAL_PALETTE = {
  coral: { light: ['#FDECEC', '#9D4B4B', '#F27474'], dark: ['#47373F', '#FACACA', '#F48989'] },
  orange: { light: ['#FEF0E7', '#935730', '#F59150'], dark: ['#483C38', '#FBD5BC', '#F6A16A'] },
  amber: { light: ['#FEF5E3', '#876420', '#F5B63A'], dark: ['#484335', '#FBE3B4', '#F6C158'] },
  yellow: { light: ['#FEFAE8', '#7B6E2F', '#F5DC5E'], dark: ['#48493B', '#FBF2C1', '#F6E176'] },
  lime: { light: ['#F2FAE8', '#526D2F', '#A3D95E'], dark: ['#3A493B', '#DCF0C1', '#B1DF76'] },
  green: { light: ['#E6F7ED', '#2D764A', '#4BC47C'], dark: ['#2B4540', '#BAE8CD', '#66CD90'] },
  mint: { light: ['#E0F7F3', '#137664', '#1FC4A6'], dark: ['#234547', '#A9E8DD', '#41CDB3'] },
  sky: { light: ['#E4F6FC', '#25738A', '#3DBFE6'], dark: ['#284452', '#B5E7F5', '#5AC9EA'] },
  blue: { light: ['#E7EEFD', '#3C5EA8', '#5586F0'], dark: ['#2D3A54', '#BED1F9', '#6F98F2'] },
  purple: { light: ['#EEEAFD', '#5C49A8', '#8468F0'], dark: ['#343554', '#D0C5F9', '#967FF2'] },
  violet: { light: ['#F5EBFC', '#7F51A2', '#B573E8'], dark: ['#3D3752', '#E3C9F6', '#C088EB'] },
  pink: { light: ['#FDEFFA', '#915383', '#F28BDB'], dark: ['#473B50', '#FAD3F1', '#F49CE0'] },
  rose: { light: ['#FDEBF1', '#9C4967', '#F0709E'], dark: ['#473746', '#F9C8DA', '#F285AD'] },
  teal: { light: ['#E2F5F7', '#1B7078', '#2DBBC8'], dark: ['#26434D', '#AFE5EA', '#4DC5D0'] },
  slate: { light: ['#EAEEF1', '#4A5C6B', '#6A8499'], dark: ['#303A45', '#C6D0D8', '#8096A8'] },
  brown: { light: ['#F4EFEA', '#7B6147', '#B08A66'], dark: ['#3C3B3C', '#E1D2C4', '#BC9C7D'] },
  sand: { light: ['#F8F5EF', '#71654C', '#CDB88B'], dark: ['#414342', '#ECE4D3', '#D4C39C'] },
  sage: { light: ['#EFF4F0', '#56685A', '#8FAE96'], dark: ['#364144', '#D4E0D7', '#A0BAA6'] },
  navy: { light: ['#E6E9F3', '#374576', '#4F63A8'], dark: ['#2B3447', '#BCC3DE', '#697AB5'] },
  plum: { light: ['#F2EAF1', '#744C6D', '#A56C9C'], dark: ['#3A3645', '#DDC7D9', '#B282AB'] },
} as const satisfies Record<string, { light: Triple; dark: Triple }>

export type GoalPaletteName = keyof typeof GOAL_PALETTE
export type GoalToneName = GoalPaletteName | 'neutral'

export interface GoalTone {
  bg: string
  fg: string
  accent: string
}

const NEUTRAL = {
  light: { bg: '#E8EDF3', fg: '#566273', accent: '#7D8898' },
  dark: { bg: '#252D38', fg: '#A3AEBD', accent: '#7D8898' },
}
const SURFACE = { light: '#FFFFFF', dark: '#1C232C' }
const MAX_DISTANCE = 60

/** "3D5AFE" · "#3d5afe" · "#abc" → "#3D5AFE" (못 읽으면 null) */
export function normalizeHex(code?: string | null): string | null {
  if (!code) return null
  let h = code.trim().replace(/^#/, '')
  if (h.length === 3)
    h = h
      .split('')
      .map((c) => c + c)
      .join('')
  if (h.length === 8) h = h.slice(0, 6)
  return /^[0-9a-fA-F]{6}$/.test(h) ? `#${h.toUpperCase()}` : null
}

const rgb = (hex: string) => {
  const n = parseInt(hex.slice(1, 7), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255] as const
}
const toHex = (c: readonly number[]) =>
  `#${c.map((v) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('').toUpperCase()}`
/** a 를 ratio 만큼, 나머지는 b */
const mix = (a: string, b: string, ratio: number) => {
  const x = rgb(a)
  const y = rgb(b)
  return toHex(x.map((v, i) => v * ratio + y[i] * (1 - ratio)))
}

const cache = new Map<string, { name: GoalPaletteName | null; hex: string }>()

/** 가장 가까운 팔레트 이름 (neutral = 색 없음, null = 팔레트와 멀어서 hex 로 만들어야 함) */
export function goalToneName(code?: string | null): GoalToneName | null {
  const hex = normalizeHex(code)
  if (!hex) return 'neutral'
  return match(hex).name
}

function match(hex: string) {
  const hit = cache.get(hex)
  if (hit) return hit
  const [r, g, b] = rgb(hex)
  let best: GoalPaletteName | null = null
  let bestD = Infinity
  for (const name of Object.keys(GOAL_PALETTE) as GoalPaletteName[]) {
    const [ar, ag, ab] = rgb(GOAL_PALETTE[name].light[2])
    const d = Math.hypot(r - ar, g - ag, b - ab)
    if (d < bestD) {
      bestD = d
      best = name
    }
  }
  const res = { name: bestD <= MAX_DISTANCE ? best : null, hex }
  cache.set(hex, res)
  return res
}

/** 실제 hex 값 (캔버스 · 이미지 저장 · SVG 처럼 CSS 변수를 못 쓰는 곳) */
export function goalTone(code?: string | null, isDark = false): GoalTone {
  const mode = isDark ? 'dark' : 'light'
  const hex = normalizeHex(code)
  if (!hex) return NEUTRAL[mode]
  const { name } = match(hex)
  if (name) {
    const [bg, fg, accent] = GOAL_PALETTE[name][mode]
    return { bg, fg, accent }
  }
  return isDark
    ? { accent: hex, bg: mix(hex, SURFACE.dark, 0.22), fg: mix(hex, '#FFFFFF', 0.4) }
    : { accent: hex, bg: mix(hex, SURFACE.light, 0.12), fg: mix(hex, '#000000', 0.55) }
}

/** CSS 값 — 라이트/다크를 저절로 따라가요 (style={{ backgroundColor: goalAccent(code) }}) */
export function goalCss(code?: string | null): GoalTone {
  const hex = normalizeHex(code)
  const name = hex ? match(hex).name : 'neutral'
  if (name) {
    return {
      bg: `var(--color-goal-${name}-bg)`,
      fg: `var(--color-goal-${name}-fg)`,
      accent: `var(--color-goal-${name}-accent)`,
    }
  }
  return {
    accent: hex!,
    bg: `color-mix(in srgb, ${hex} var(--goal-tint), var(--color-bg-surface))`,
    fg: `color-mix(in srgb, ${hex} var(--goal-fg-keep), var(--goal-fg-base))`,
  }
}

/** 점 · 선 · 아이콘 색 */
export const goalAccent = (code?: string | null) => goalCss(code).accent
/** 연한 바탕 */
export const goalBg = (code?: string | null) => goalCss(code).bg
/** 연한 바탕 위 글자 */
export const goalFg = (code?: string | null) => goalCss(code).fg

/**
 * 하위 요소가 쓰도록 CSS 변수로 내려줘요 → 클래스로 bg-(--goal-bg) · text-(--goal-fg) · border-(--goal-accent)
 */
export function goalVars(code?: string | null): CSSProperties {
  const t = goalCss(code)
  return { '--goal-bg': t.bg, '--goal-fg': t.fg, '--goal-accent': t.accent } as CSSProperties
}
