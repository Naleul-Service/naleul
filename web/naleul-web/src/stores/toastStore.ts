import { create } from 'zustand'
import { COMMON_EVENTS as CE, track } from '@/lib/client/analytics'
import { currentPath } from '@/lib/client/commonEvents'

export type ToastTone = 'default' | 'success' | 'error'

interface ToastItem {
  id: number
  message: string
  tone: ToastTone
  /** 뜬 시각 (toast.dismiss_click 의 seconds_shown) */
  at: number
}

interface ToastState {
  toasts: ToastItem[]
  show: (message: string, tone?: ToastTone) => void
  dismiss: (id: number) => void
}

let seq = 0

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  show: (message, tone = 'default') => {
    const id = ++seq
    set({ toasts: [...get().toasts.slice(-2), { id, message, tone, at: Date.now() }] }) // 최대 3개
    // 문구는 앱이 정한 것만 들어와요 (서버 에러 메시지도 앱 문구, 사용자 입력은 안 들어감 — 노션 toast_message)
    track(CE.toastView, { toast_tone: tone, toast_message: message, current_path: currentPath() })
    setTimeout(() => get().dismiss(id), 2800)
  },
  dismiss: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),
}))

/** 백엔드 · BFF 가 둘러보기 중 저장을 막을 때 쓰는 문구 (401 GUEST_LOGIN_REQUIRED) */
export const GUEST_LOGIN_MESSAGE = '로그인하면 저장할 수 있어요.'

/** 컴포넌트 밖(예: mutation onError)에서도 쓸 수 있는 단축 함수 */
export const toast = {
  show: (message: string) => useToastStore.getState().show(message),
  success: (message: string) => useToastStore.getState().show(message, 'success'),
  // 둘러보기 중 저장 시도는 로그인 안내 창(LoginRequiredDialog)이 대신 알려줘요 → 토스트는 겹치지 않게 생략
  error: (message: string) => {
    if (message === GUEST_LOGIN_MESSAGE) return
    useToastStore.getState().show(message, 'error')
  },
}
