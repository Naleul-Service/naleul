'use client'

import { useCallback, useEffect, useSyncExternalStore } from 'react'

/**
 * 화면 모드 — 시스템(기본) / 라이트 / 다크.
 * 고른 값은 이 브라우저에만 저장해요 (localStorage). 실제 적용은 <html data-theme="light|dark">.
 */
export type ThemePref = 'system' | 'light' | 'dark'

export const THEME_KEY = 'naleul-theme'
const MEDIA = '(prefers-color-scheme: dark)'

/**
 * 첫 화면을 그리기 "전에" <head> 에서 바로 실행하는 스크립트.
 * React 가 뜬 뒤에 적용하면 라이트로 잠깐 번쩍였다가 어두워져서(깜빡임) 이렇게 미리 정해요.
 */
export const THEME_SCRIPT = `(function(){try{var p=localStorage.getItem('${THEME_KEY}')||'system';var d=p==='dark'||(p!=='light'&&window.matchMedia('${MEDIA}').matches);document.documentElement.dataset.theme=d?'dark':'light'}catch(e){document.documentElement.dataset.theme='light'}})()`

function readPref(): ThemePref {
  try {
    const v = localStorage.getItem(THEME_KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

function apply(pref: ThemePref) {
  const dark = pref === 'dark' || (pref === 'system' && window.matchMedia(MEDIA).matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

function subscribe(cb: () => void) {
  listeners.add(cb)
  const onStorage = (e: StorageEvent) => {
    if (e.key === THEME_KEY) {
      apply(readPref())
      notify()
    }
  }
  window.addEventListener('storage', onStorage) // 다른 탭에서 바꿔도 같이
  return () => {
    listeners.delete(cb)
    window.removeEventListener('storage', onStorage)
  }
}

/** 지금 고른 화면 모드 + 바꾸기 */
export function useThemePref() {
  const pref = useSyncExternalStore(subscribe, readPref, () => 'system' as ThemePref)
  const setPref = useCallback((next: ThemePref) => {
    try {
      if (next === 'system') localStorage.removeItem(THEME_KEY)
      else localStorage.setItem(THEME_KEY, next)
    } catch {
      // 저장이 막힌 브라우저(시크릿 모드 등)여도 이번 화면에는 적용해요
    }
    apply(next)
    notify()
  }, [])
  return [pref, setPref] as const
}

/** "시스템"일 때 OS 설정이 바뀌면(저녁에 자동 다크 등) 바로 따라가요. Providers 에 한 번 둬요 */
export function ThemeSync() {
  useEffect(() => {
    const mq = window.matchMedia(MEDIA)
    const onChange = () => {
      if (readPref() === 'system') apply('system')
    }
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])
  return null
}

/** 지금 고른 화면 모드 (이벤트 속성 theme_pref 용) */
export const readThemePref = (): ThemePref => readPref()
