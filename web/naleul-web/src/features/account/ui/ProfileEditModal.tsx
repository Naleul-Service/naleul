'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Modal, type ModalCloseMethod } from '@/components/ui/Modal'
import { track } from '@/lib/client/analytics'
import { isApiError } from '@/lib/client/api'
import { errorStatusOf } from '@/features/home/analytics'
import { cn } from '@/lib/cn'
import { toast } from '@/stores/toastStore'
import { NICKNAME_HELP, NICKNAME_PATTERN, useUpdateNickname } from '../api'

/** iOS ProfileView(프로필 수정)를 모달로 옮긴 것 — 닉네임만 바꿔요 */
export function ProfileEditModal({
  open,
  currentName,
  onClose,
}: {
  open: boolean
  currentName: string
  onClose: () => void
}) {
  const [nickname, setNickname] = useState(currentName)
  const [error, setError] = useState<string | null>(null)
  const update = useUpdateNickname()

  const trimmed = nickname.trim()
  const valid = NICKNAME_PATTERN.test(trimmed)

  /** 입력칸 아래 빨간 문구 + settings.profile_error_view */
  const showError = (message: string, textLength: number) => {
    setError(message)
    track('settings.profile_error_view', { validation_error: message, text_length: textLength })
  }

  /** 저장하지 않고 닫기 (취소 · X · 바깥 · Esc) — 고친 내용은 확인 없이 사라져요 */
  const close = (method: ModalCloseMethod | 'cancel_button') => {
    track('settings.profile_edit_close', { close_method: method, is_dirty: nickname !== currentName })
    onClose()
  }

  const submit = () => {
    if (!valid) {
      if (error !== NICKNAME_HELP) showError(NICKNAME_HELP, trimmed.length)
      return
    }
    const textLength = trimmed.length
    track('settings.profile_save_click', { text_length: textLength })
    update.mutate(trimmed, {
      onSuccess: () => {
        track('settings.profile_save_result', { result: 'success' })
        toast.success('닉네임을 바꿨어요.')
        onClose()
      },
      onError: (e) => {
        track('settings.profile_save_result', { result: 'fail', error_status: errorStatusOf(e) })
        showError(isApiError(e) ? e.message : '닉네임 수정에 실패했어요. 다시 시도해 주세요.', textLength)
      },
    })
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="프로필 수정"
      dismissible={!update.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={() => close('cancel_button')} disabled={update.isPending}>
            취소
          </Button>
          <Button onClick={submit} disabled={!valid || trimmed === currentName} loading={update.isPending}>
            저장
          </Button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <label className="block">
          <span className="text-ink-3 mb-1 block text-xs font-medium">
            닉네임 <span className="text-danger">*</span>
          </span>
          <input
            value={nickname}
            onChange={(e) => {
              setNickname(e.target.value)
              setError(null)
            }}
            maxLength={10}
            placeholder="이름을 작성해주세요"
            autoFocus
            aria-invalid={!!error}
            className={cn(
              'h-11 w-full rounded-input border bg-surface px-3 text-[15px] outline-none',
              error ? 'border-danger' : 'border-border-subtle focus:border-border-focus'
            )}
          />
        </label>
        <p className={cn('mt-1.5 text-xs', error ? 'text-danger' : 'text-ink-3')}>{error ?? NICKNAME_HELP}</p>
      </form>
    </Modal>
  )
}
