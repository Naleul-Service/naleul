'use client'

import { useEffect } from 'react'
import { Button } from '@/components/ui/Button'
import { COMMON_EVENTS as CE, track } from '@/lib/client/analytics'
import { currentPath } from '@/lib/client/commonEvents'

/**
 * '다시 시도'를 누른 횟수 (같은 화면 · 같은 에러 기준).
 * 다시 시도해도 같은 에러면 이 화면이 새로 그려져서 컴포넌트 상태로는 셀 수 없어요 → 모듈 변수로 세요.
 */
const retries = new Map<string, number>()
const keyOf = (digest?: string) => `${currentPath()}|${digest ?? ''}`

/** 화면 렌더링 중 예상 못 한 에러가 났을 때 */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error)
    // 에러 메시지 원문은 보내지 않아요 (digest 로 서버 로그와 맞춰 봐요)
    track(CE.errorPageView, {
      error_digest: error.digest,
      current_path: currentPath(),
      retry_count: retries.get(keyOf(error.digest)) ?? 0,
    })
  }, [error])

  return (
    <main className="grid min-h-dvh place-items-center px-4 text-center">
      <div>
        <h1 className="text-xl font-bold">문제가 생겼어요</h1>
        <p className="text-ink-3 mt-2 text-sm">잠시 후 다시 시도해 주세요.</p>
        <Button
          className="mt-6"
          onClick={() => {
            const k = keyOf(error.digest)
            const n = (retries.get(k) ?? 0) + 1
            retries.set(k, n)
            track(CE.errorPageRetryClick, { error_digest: error.digest, retry_count: n })
            reset()
          }}
        >
          다시 시도
        </Button>
      </div>
    </main>
  )
}
