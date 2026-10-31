'use client'

import { Monitor, Moon, Sun } from 'lucide-react'
import { type IconComponent } from '@/components/icons'
import { track } from '@/lib/client/analytics'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { useThemePref, type ThemePref } from './theme'

const OPTIONS: { value: ThemePref; label: string; icon: IconComponent }[] = [
  { value: 'system', label: '시스템', icon: Monitor },
  { value: 'light', label: '라이트', icon: Sun },
  { value: 'dark', label: '다크', icon: Moon },
]

/** 설정 > 화면 모드 */
export function ThemePicker() {
  const [pref, setPref] = useThemePref()
  return (
    <div className="flex flex-wrap items-center gap-3 px-5 py-4">
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-bold">화면 모드</p>
        <p className="text-ink-3 mt-0.5 text-[13px]">시스템을 고르면 기기 설정(라이트·다크)을 따라가요</p>
      </div>
      <div role="radiogroup" aria-label="화면 모드" className={segmentedClass()}>
        {OPTIONS.map(({ value, label, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={pref === value}
            onClick={() => {
              if (value === pref) return
              setPref(value)
              track('settings.theme_change', { theme_pref: value })
            }}
            className={segmentClass(pref === value, 'px-3')}
          >
            <Icon className="size-3.5" />
            {label}
          </button>
        ))}
      </div>
    </div>
  )
}
