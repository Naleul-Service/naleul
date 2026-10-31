'use client'

import { Button } from './Button'
import { Modal, type ModalCloseMethod } from './Modal'

/** 확인창을 닫은 방법 (분석용) — X·바깥·Esc 또는 취소 버튼 */
export type ConfirmCancelMethod = ModalCloseMethod | 'cancel_button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  loading?: boolean
  onConfirm: () => void
  /** method: 어떻게 닫혔는지 (필요한 곳만 받아서 써요) */
  onCancel: (method: ConfirmCancelMethod) => void
}

/** "정말 ~할까요?" 확인창 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '확인',
  cancelLabel = '취소',
  tone = 'primary',
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      onClose={(m) => onCancel(m)}
      title={title}
      description={description}
      size="sm"
      dismissible={!loading}
      footer={
        <>
          <Button variant="outline" onClick={() => onCancel('cancel_button')} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    />
  )
}
