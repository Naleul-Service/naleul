'use client'

import { useEffect, useRef, useState } from 'react'
import { Copy, Share2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { track } from '@/lib/client/analytics'
import { errorStatusOf } from '@/features/home/analytics'
import { toast } from '@/stores/toastStore'
import { useWorkShareText, type ShareScope } from '../api'

const SCOPES: { value: ShareScope; label: string; hint: string }[] = [
  { value: 'TODAY', label: '오늘 할 일', hint: '오늘 할 일 + 오늘 끝낸 일' },
  { value: 'REMAINING', label: '남은 일', hint: '진행 중 + 시작 전 (세부 목표별)' },
  { value: 'ALL', label: '전체', hint: '남은 일 + 최근 7일 완료' },
]

/**
 * 공유하기 — 서버가 정리해 준 텍스트를 카톡 · 슬랙에 붙여 넣기 좋게 보여주고 복사 · 공유해요.
 * 휴대폰 브라우저에서는 시스템 공유 창(navigator.share)으로 바로 보낼 수 있어요.
 */
export function WorkShareModal({ goalId, open, onClose }: { goalId: number; open: boolean; onClose: () => void }) {
  const [scope, setScope] = useState<ShareScope>('REMAINING')
  const { data, isFetching, isError, error } = useWorkShareText(goalId, scope, open)
  const text = data?.text ?? ''
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function'
  // 이번에 연 창에서 복사·공유를 했는지 (work_share.close 의 copied)
  const copiedRef = useRef(false)

  // 창 안에 빨간 오류 문구가 보일 때 (실패할 때마다 1회)
  const failSent = useRef(false)
  useEffect(() => {
    if (!open || !isError) {
      failSent.current = false
      return
    }
    if (failSent.current) return
    failSent.current = true
    track('work_share.text_load_fail', {
      goal_category_id: goalId,
      share_scope: scope,
      error_status: errorStatusOf(error),
    })
  }, [open, isError, error, goalId, scope])

  const sendShareResult = (target: 'share_copy' | 'share_native', result: 'success' | 'fail' | 'cancel') =>
    track('work_share.share_result', { goal_category_id: goalId, copy_target: target, share_scope: scope, result })

  const selectScope = (next: ShareScope) => {
    if (next !== scope) track('work_share.scope_select', { goal_category_id: goalId, share_scope: next })
    setScope(next)
  }

  const close = (method: ModalCloseMethod) => {
    track('work_share.close', { goal_category_id: goalId, close_method: method, copied: copiedRef.current })
    copiedRef.current = false
    onClose()
  }

  const copy = async () => {
    track('work_share.copy_click', { goal_category_id: goalId, share_scope: scope, text_length: text.length })
    copiedRef.current = true
    try {
      await navigator.clipboard.writeText(text)
      toast.success('복사했어요. 원하는 곳에 붙여 넣어 주세요.')
      sendShareResult('share_copy', 'success')
    } catch {
      toast.error('복사하지 못했어요. 글을 길게 눌러 직접 복사해 주세요.')
      sendShareResult('share_copy', 'fail')
    }
  }
  const share = async () => {
    track('work_share.native_share_click', { goal_category_id: goalId, share_scope: scope, text_length: text.length })
    copiedRef.current = true
    try {
      await navigator.share({ text })
      sendShareResult('share_native', 'success')
    } catch (e) {
      // 사용자가 공유 창을 닫은 경우(AbortError) — 화면엔 아무것도 안 해요
      sendShareResult('share_native', e instanceof DOMException && e.name === 'AbortError' ? 'cancel' : 'fail')
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Task 공유하기"
      description="팀 채팅방에 붙여 넣기 좋게 정리했어요."
      size="lg"
      footer={
        <>
          {canShare && (
            <Button variant="secondary" onClick={share} disabled={!text}>
              <Share2 className="size-4" />
              공유
            </Button>
          )}
          <Button onClick={copy} disabled={!text}>
            <Copy className="size-4" />
            복사하기
          </Button>
        </>
      }
    >
      <div className={segmentedClass()} role="radiogroup" aria-label="공유할 범위">
        {SCOPES.map((s) => (
          <button
            key={s.value}
            type="button"
            role="radio"
            aria-checked={scope === s.value}
            title={s.hint}
            onClick={() => selectScope(s.value)}
            className={segmentClass(scope === s.value, 'px-3')}
          >
            {s.label}
          </button>
        ))}
      </div>
      <p className="text-ink-3 mt-1.5 text-[12px]">{SCOPES.find((s) => s.value === scope)?.hint}</p>
      <div className="border-line bg-bg-sunken relative mt-3 min-h-[160px] rounded-2xl border p-4">
        {isFetching && !text ? (
          <div className="grid h-32 place-items-center">
            <Spinner className="text-ink-3 size-5" />
          </div>
        ) : isError ? (
          <p className="text-danger text-sm">{error.message}</p>
        ) : (
          <pre className="font-sans text-[13px] leading-relaxed whitespace-pre-wrap select-all">{text}</pre>
        )}
      </div>
    </Modal>
  )
}
