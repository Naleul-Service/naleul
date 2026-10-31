import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'

/** GET /api/v1/notification-settings 응답 (백엔드 NotificationSettingsResponse) */
export interface NotificationSettings {
  missionAlertEnabled: boolean
  missionCertificationEnabled: boolean
  wishlistEnabled: boolean
  missionProgressEnabled: boolean
  missionEditEnabled: boolean
  badgeEnabled: boolean
  pokeEnabled: boolean
  taskAlertEnabled: boolean
}

type SettingField = keyof NotificationSettings

/** 미션 알림 종류 — 문구는 iOS NotificationCategory 와 같아요 */
export const MISSION_CATEGORIES = [
  {
    category: 'MISSION_ALERT',
    field: 'missionAlertEnabled',
    label: '미션 알림',
    description: '미션 신청 현황과 승인이 필요할 때 알려드려요',
  },
  {
    category: 'MISSION_CERTIFICATION',
    field: 'missionCertificationEnabled',
    label: '미션 인증',
    description: '팀원들이 미션을 인증할 때마다 알려드려요',
  },
  {
    category: 'WISHLIST',
    field: 'wishlistEnabled',
    label: '찜하기',
    description: '찜한 미션의 중요한 소식을 알려드려요',
  },
  {
    category: 'MISSION_PROGRESS',
    field: 'missionProgressEnabled',
    label: '미션 진행',
    description: '미션이 시작되거나 완료되면 알려드려요.',
  },
  {
    category: 'MISSION_EDIT',
    field: 'missionEditEnabled',
    label: '미션 내용 수정',
    description: '미션 일정이나 내용이 변경되면 알려드려요',
  },
  { category: 'BADGE', field: 'badgeEnabled', label: '뱃지 획득', description: '새로운 뱃지를 획득하면 알려드려요.' },
  { category: 'POKE', field: 'pokeEnabled', label: '쪼기', description: '팀원이 미션 참여를 독려하면 알려드려요' },
] as const satisfies readonly { category: string; field: SettingField; label: string; description: string }[]

export type NotificationCategory = (typeof MISSION_CATEGORIES)[number]['category']

/** Task 알림은 카테고리가 아니라 별도 엔드포인트(/task-alert)로 바꿔요 */
export type NotificationChange =
  { kind: 'task'; enabled: boolean } | { kind: 'category'; category: NotificationCategory; enabled: boolean }

const BASE = '/v1/notification-settings'
export const notificationSettingsKey = ['notification-settings'] as const

const fieldOf = (c: NotificationChange): SettingField =>
  c.kind === 'task' ? 'taskAlertEnabled' : MISSION_CATEGORIES.find((m) => m.category === c.category)!.field

export function useNotificationSettings() {
  return useQuery({ queryKey: notificationSettingsKey, queryFn: () => api.get<NotificationSettings>(BASE) })
}

/**
 * 토글 하나 바꾸기.
 * 낙관적 업데이트: 스위치를 먼저 바꿔서 바로 반응하고, 서버가 실패하면 원래 값으로 되돌려요.
 * (iOS NotificationSettingsViewModel.toggle 과 같은 방식)
 */
export function useUpdateNotificationSetting() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (c: NotificationChange) =>
      c.kind === 'task'
        ? api.patch<NotificationSettings>(`${BASE}/task-alert`, { enabled: c.enabled })
        : api.patch<NotificationSettings>(BASE, { category: c.category, enabled: c.enabled }),
    onMutate: async (c) => {
      await qc.cancelQueries({ queryKey: notificationSettingsKey })
      const prev = qc.getQueryData<NotificationSettings>(notificationSettingsKey)
      if (prev) qc.setQueryData(notificationSettingsKey, { ...prev, [fieldOf(c)]: c.enabled })
      return { prev }
    },
    onError: (_e, _c, ctx) => {
      if (ctx?.prev) qc.setQueryData(notificationSettingsKey, ctx.prev)
      toast.error('설정 변경에 실패했어요. 다시 시도해 주세요.')
    },
    onSuccess: (data) => qc.setQueryData(notificationSettingsKey, data),
  })
}
