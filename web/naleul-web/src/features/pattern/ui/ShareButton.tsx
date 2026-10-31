'use client'

import { useState, type RefObject } from 'react'
import { Download } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { toast } from '@/stores/toastStore'
import { track } from '@/lib/client/analytics'
import { layoutBreakpoint } from '@/features/home/analytics'
import { usePatternAnalytics } from '../analytics'

/**
 * [리포트 공유] — 화면의 리포트 영역을 PNG 로 저장해요 (서버 작업 없음).
 * html-to-image 는 화면을 그릴 때만 필요해서 버튼을 누를 때 불러와요 (첫 화면 용량을 줄이려고).
 * data-share-exclude 가 붙은 요소(기간 선택, 공유 버튼)는 이미지에서 빠져요.
 */
const PAD = 32

export function ShareButton({
  targetRef,
  fileDate,
  evaluatedCount,
  styleReady,
}: {
  targetRef: RefObject<HTMLElement | null>
  fileDate: string
  evaluatedCount: number
  styleReady: boolean
}) {
  const [busy, setBusy] = useState(false)
  const { period } = usePatternAnalytics()

  async function save() {
    track('pattern.share_click', { period, evaluated_count: evaluatedCount, style_ready: styleReady })
    const node = targetRef.current
    if (!node) return
    setBusy(true)
    try {
      const { toPng } = await import('html-to-image')
      // 가장자리에 여백을 두려고 캔버스를 PAD 만큼 키우고 복제본에 padding 을 줘요
      const url = await toPng(node, {
        pixelRatio: 2,
        backgroundColor: getComputedStyle(document.body).backgroundColor, // 라이트/다크 그대로
        cacheBust: true,
        width: node.offsetWidth + PAD * 2,
        height: node.offsetHeight + PAD * 2,
        style: { padding: `${PAD}px`, boxSizing: 'content-box' },
        filter: (el) => !(el instanceof HTMLElement && el.dataset.shareExclude !== undefined),
      })
      const a = document.createElement('a')
      a.href = url
      a.download = `naleul-pattern-${fileDate}.png`
      a.click()
      track('pattern.share_result', { period, result: 'success', layout_breakpoint: layoutBreakpoint() })
      toast.success('리포트 이미지를 저장했어요.')
    } catch {
      track('pattern.share_result', { period, result: 'fail', layout_breakpoint: layoutBreakpoint() })
      toast.error('이미지를 만들지 못했어요. 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button variant="secondary" onClick={save} loading={busy} data-share-exclude>
      {!busy && <Download className="size-4" />}
      리포트 공유
    </Button>
  )
}
