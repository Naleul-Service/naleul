'use client'

import { useEffect, useRef } from 'react'
import Link from 'next/link'
import { Lock } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Chip'
import { Spinner } from '@/components/ui/Spinner'
import { Switch } from '@/components/ui/Switch'
import { errorStatusOf } from '@/features/home/analytics'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'
import {
  MISSION_CATEGORIES,
  useNotificationSettings,
  useUpdateNotificationSetting,
  type NotificationChange,
} from '../api'

/**
 * /settings/notifications — iOS "푸시 알림 설정" 화면을 옮긴 것.
 * 웹에는 푸시가 없어서, 여기서 바꾼 값은 나를 앱의 푸시 알림에 적용돼요.
 *
 * @param locked FREE 플랜이면 true → 스위치 대신 자물쇠 (iOS 와 같은 규칙)
 * @param isPremium Pro 사용자인지 (notification_settings.pageview 속성)
 */
export function NotificationSettingsView({ locked, isPremium }: { locked: boolean; isPremium: boolean }) {
  const { data, isPending, isError, error, errorUpdatedAt, refetch } = useNotificationSettings()
  const update = useUpdateNotificationSetting()
  const errorStatus = isError ? errorStatusOf(error) : undefined

  // pageview: 설정을 처음 불러온 직후 한 번
  const pageviewSent = useRef(false)
  const loaded = !!data
  useEffect(() => {
    if (!loaded || pageviewSent.current) return
    pageviewSent.current = true
    track('notification_settings.pageview', { is_premium: isPremium })
  }, [loaded, isPremium])

  // load_fail: '설정을 불러오지 못했어요' 카드가 뜰 때마다 (실패 1회당 한 번)
  const failSentAt = useRef(0)
  const showFail = isError
  useEffect(() => {
    if (!showFail || failSentAt.current === errorUpdatedAt) return
    failSentAt.current = errorUpdatedAt
    track('notification_settings.load_fail', { error_status: errorStatus })
  }, [showFail, errorUpdatedAt, errorStatus])

  /** 스위치 — 먼저 바뀌고, 실패하면 원래 값으로 되돌려요 */
  const toggle = (change: NotificationChange) => {
    const kind = change.kind === 'task' ? 'task' : change.category
    track('notification_settings.toggle', { notification_kind: kind, checked: change.enabled })
    update.mutate(change, {
      onSuccess: () =>
        track('notification_settings.toggle_result', {
          notification_kind: kind,
          checked: change.enabled,
          result: 'success',
        }),
      onError: (e) =>
        track('notification_settings.toggle_result', {
          notification_kind: kind,
          checked: change.enabled,
          result: 'fail',
          error_status: errorStatusOf(e),
        }),
    })
  }

  return (
    <>
      <PageHeader
        breadcrumb={<Link href="/settings">설정</Link>}
        title="알림 설정"
        description="여기서 바꾼 설정은 나를 앱의 푸시 알림에 적용돼요."
      />

      {isPending ? (
        <div className="grid h-48 place-items-center">
          <Spinner className="text-ink-3 size-6" />
        </div>
      ) : isError || !data ? (
        <Card className="mt-6 p-8 text-center">
          <p className="text-ink-2 text-[15px]">설정을 불러오지 못했어요.</p>
          <Button
            variant="secondary"
            className="mt-4"
            onClick={() => {
              track('notification_settings.retry_click', { error_status: errorStatus })
              void refetch()
            }}
          >
            다시 시도
          </Button>
        </Card>
      ) : (
        <div className="mt-6 max-w-2xl space-y-4">
          <Card>
            <SectionTitle title="Task 알림 설정" locked={locked} />
            <ToggleRow
              title="Task 알림"
              description="개인이 설정한 Task 푸시 알림을 받을 수 있어요."
              checked={data.taskAlertEnabled}
              locked={locked}
              onChange={(enabled) => toggle({ kind: 'task', enabled })}
            />
          </Card>

          <Card>
            <SectionTitle title="미션 알림 설정" locked={locked} />
            <ul className="divide-line divide-y">
              {MISSION_CATEGORIES.map((m) => (
                <li key={m.category}>
                  <ToggleRow
                    title={m.label}
                    description={m.description}
                    checked={data[m.field]}
                    locked={locked}
                    onChange={(enabled) => toggle({ kind: 'category', category: m.category, enabled })}
                  />
                </li>
              ))}
            </ul>
          </Card>

          {locked && (
            <p className="text-ink-3 px-1 text-[13px]">
              푸시 알림은 PRO부터 사용할 수 있어요. PRO로 업그레이드하면 모든 알림을 자유롭게 설정할 수 있어요.
            </p>
          )}
        </div>
      )}
    </>
  )
}

function SectionTitle({ title, locked }: { title: string; locked: boolean }) {
  return (
    <div className="border-line flex items-center gap-2 border-b px-5 py-4">
      <h2 className="text-[16px] font-bold">{title}</h2>
      {locked && <Badge tone="brand">PRO</Badge>}
    </div>
  )
}

function ToggleRow({
  title,
  description,
  checked,
  locked,
  onChange,
}: {
  title: string
  description: string
  checked: boolean
  locked: boolean
  onChange: (next: boolean) => void
}) {
  const text = (
    <div className="min-w-0 flex-1">
      <p className={locked ? 'text-ink-4 text-[15px] font-semibold' : 'text-[15px] font-semibold'}>{title}</p>
      <p className={locked ? 'text-ink-4 mt-0.5 text-[13px]' : 'text-ink-3 mt-0.5 text-[13px]'}>{description}</p>
    </div>
  )

  if (locked) {
    // 잠긴 상태 — 누르면 토글 대신 안내
    return (
      <button
        type="button"
        onClick={() => toast.show('푸시 알림은 PRO부터 사용할 수 있어요. 나를 앱에서 PRO로 업그레이드해 주세요.')}
        className="hover:bg-subtle/60 flex w-full items-center gap-4 px-5 py-4 text-left"
      >
        {text}
        <Lock className="text-ink-4 size-4 shrink-0" />
      </button>
    )
  }

  return (
    <div className="flex items-center gap-4 px-5 py-4">
      {text}
      <Switch checked={checked} onChange={onChange} label={title} />
    </div>
  )
}
