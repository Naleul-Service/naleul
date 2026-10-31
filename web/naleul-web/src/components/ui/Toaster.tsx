'use client'

import { CheckCircle2, AlertCircle } from 'lucide-react'
import { cn } from '@/lib/cn'
import { useToastStore } from '@/stores/toastStore'
import { COMMON_EVENTS as CE, track } from '@/lib/client/analytics'

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts)
  const dismiss = useToastStore((s) => s.dismiss)

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--tabbar-h)+24px)] z-[60] flex flex-col items-center gap-2 px-4"
    >
      {toasts.map((t) => (
        <button
          key={t.id}
          type="button"
          onClick={() => {
            track(CE.toastDismissClick, { toast_tone: t.tone, seconds_shown: Math.round((Date.now() - t.at) / 1000) })
            dismiss(t.id)
          }}
          // 디자인 시스템 Toast — bg/inverse · 모서리 12 · 12/16 · 간격 10 · Body/SM text/inverse
          className={cn(
            'bg-bg-inverse text-text-inverse rounded-toast text-body-sm pointer-events-auto flex max-w-md items-center gap-2.5 px-4 py-3 text-left',
            'shadow-pop animate-[toast-in_280ms_var(--ease-spring)]'
          )}
        >
          {t.tone === 'success' && <CheckCircle2 className="text-accent-mint size-4 shrink-0" strokeWidth={1.5} />}
          {t.tone === 'error' && <AlertCircle className="text-action-danger-bg size-4 shrink-0" strokeWidth={1.5} />}
          {t.message}
        </button>
      ))}
    </div>
  )
}
