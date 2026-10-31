import type { ComponentType, FC, SVGProps } from 'react'
import { ICON_PATHS, type IconName } from './paths'

export type { IconName }

/** 화면 px 기준 선 굵기 — 14·16 은 1.25, 18 이상은 1.5 (명세 3) */
const strokePxFor = (size: number) => (size <= 16 ? 1.25 : 1.5)

/** className 의 size-4 · size-[18px] · h-5 같은 Tailwind 크기에서 실제 px 를 읽어요 (없으면 null) */
function sizeFromClass(className?: string): number | null {
  if (!className) return null
  const m = className.match(/(?:^|\s)(?:size|h)-(\[(\d+(?:\.\d+)?)px\]|(\d+(?:\.5)?))(?=\s|$)/)
  if (!m) return null
  return m[2] ? Number(m[2]) : Number(m[3]) * 4
}

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'ref' | 'name'> {
  /** px (기본 20) — className 의 size-* 가 있으면 그 크기로 선 굵기를 맞춰요 */
  size?: number | string
  /** 화면 px 선 굵기 (기본: 크기에 맞춰 1.25 / 1.5). 24 viewBox 기준으로 바꿔 넣어요 */
  strokeWidth?: number | string
}

/** lucide 와 같은 모양으로 쓰는 아이콘 컴포넌트 타입 (lucide 아이콘도 그대로 들어가요) */
export type IconComponent = ComponentType<{ className?: string; size?: number | string; strokeWidth?: number | string }>

/**
 * Figma 디자인 시스템 아이콘 — currentColor · 기본 20px · aria-hidden.
 *   <Icon name="calendar" className="size-4" />
 */
export function Icon({ name, size, strokeWidth, className, style, ...rest }: IconProps & { name: IconName }) {
  const px = typeof size === 'number' ? size : size ? Number.parseFloat(size) : (sizeFromClass(className) ?? 20)
  const strokePx = strokeWidth != null ? Number(strokeWidth) : strokePxFor(px)
  const sw = Math.round(((strokePx * 24) / (px || 20)) * 1000) / 1000
  // panel-right-open 은 Figma 에서 panel-left-close 와 같은 모양이라 좌우 반전해요 (디자인 수정 전까지)
  const mirrored = name === 'panel-right-open'
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size ?? 20}
      height={size ?? 20}
      viewBox="0 0 24 24"
      fill="none"
      strokeWidth={sw}
      aria-hidden={rest['aria-label'] ? undefined : true}
      focusable="false"
      className={className}
      style={mirrored ? { transform: 'scaleX(-1)', ...style } : style}
      dangerouslySetInnerHTML={{ __html: ICON_PATHS[name] }}
      {...rest}
    />
  )
}

/** 이름이 정해진 아이콘 컴포넌트 (lucide 처럼 <Plus className="size-4" />) */
export function makeIcon(name: IconName, displayName: string): FC<IconProps> {
  const C: FC<IconProps> = (props) => <Icon {...props} name={name} />
  C.displayName = displayName
  return C
}
