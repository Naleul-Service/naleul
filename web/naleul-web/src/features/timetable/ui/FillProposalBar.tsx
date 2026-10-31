'use client'

import { Check, X } from '@/components/icons'
import { AIBadge } from '@/components/ui/AIBadge'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'

/**
 * "AI로 배치하기" 미리보기 확인 바 — 점선 블록을 끌어 고친 뒤 [이대로 진행] 하면 저장, [취소] 하면 아무것도 안 바뀌어요.
 */
export function FillProposalBar({
  kind = 'fill',
  sidePanel,
  count,
  edited,
  unscheduled,
  loading,
  onApply,
  onCancel,
}: {
  /** fill = AI로 배치하기 · new = 방금 추가한 Task (Task 는 이미 저장됨, 시간만 묻는 중) */
  kind?: 'fill' | 'new'
  /** 오른쪽 Task 추가 패널이 열려 있으면 그 폭만큼 비켜요 */
  sidePanel?: boolean
  count: number
  /** 사용자가 끌어서 고친 제안 수 */
  edited: number
  /** 빈 시간이 없어 제안하지 못한 수 */
  unscheduled: number
  loading: boolean
  onApply: () => void
  /** method: 버튼('취소'·'아니요'·'닫기') 으로 닫았는지 (Esc 는 부모가 따로 처리) */
  onCancel: (method: 'cancel_button') => void
}) {
  const isNew = kind === 'new'
  return (
    <div
      className={cn(
        'pointer-events-none fixed inset-x-0 bottom-[calc(var(--tabbar-h)+16px)] z-40 flex justify-center px-4',
        sidePanel && 'xl:pr-[436px]'
      )}
    >
      <div
        role="dialog"
        aria-label="AI 배치 확인"
        // AI 제안 바 (명세 6B·6D) — 그라데이션 테두리 · AIBadge · 적용(Primary ✓) / 취소(Secondary ✕)
        className="ai-border rounded-card pointer-events-auto w-full max-w-[640px] animate-[modal-in_160ms_ease-out] px-3.5 py-3 sm:p-4"
      >
        <div className="flex items-start gap-3">
          <AIBadge />
          <div className="min-w-0 flex-1">
            <p className="text-label-lg text-text-primary">
              {isNew
                ? count > 0
                  ? `새 Task ${count}개를 점선 자리에 놓을게요. 이대로 진행할까요?`
                  : '새 Task를 놓을 빈 시간을 찾지 못했어요'
                : count > 0
                  ? `점선 자리에 ${count}개를 배치할게요. 이대로 진행할까요?`
                  : '배치할 빈 시간을 찾지 못했어요'}
            </p>
            <p className="text-caption-md text-text-secondary mt-0.5">
              {count > 0 && '마음에 안 드는 블록은 끌어서 옮겨 보세요. 시간 미정 칸으로 끌면 이번 배치에서 빠져요.'}
              {isNew && ' Task는 이미 추가됐어요 — 아니요를 누르면 시간 미정으로 남아요.'}
              {edited > 0 && ` 직접 옮긴 ${edited}개는 그 시간에 고정돼요.`}
              {unscheduled > 0 && ` 빈 시간이 없어 ${unscheduled}개는 시간 미정으로 남아요.`}
            </p>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className="text-caption-sm text-text-tertiary hidden sm:inline">Esc 취소</span>
          <span className="ml-auto flex flex-wrap justify-end gap-2">
            <Button
              variant="secondary"
              disabled={loading}
              leftIcon={<X className="size-4" />}
              onClick={() => onCancel('cancel_button')}
            >
              {count === 0 ? '닫기' : isNew ? '아니요 (시간 미정으로 두기)' : '취소 (반영 안 함)'}
            </Button>
            {count > 0 && (
              <Button
                variant="primary"
                loading={loading}
                leftIcon={<Check className="size-4" />}
                onClick={onApply}
              >
                이대로 진행
              </Button>
            )}
          </span>
        </div>
      </div>
    </div>
  )
}
