'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Chip'
import type { SessionUserView } from '@/components/layout/types'
import { track } from '@/lib/client/analytics'
import { ProfileEditModal } from './ProfileEditModal'
import { isPremiumRole } from '@/features/account/settingsRole'

/** 설정 맨 위 프로필 요약 (iOS SettingProfileSummary) */
export function ProfileSummaryCard({ user }: { user: SessionUserView }) {
  const [editing, setEditing] = useState(false)
  // 모달을 열 때마다 현재 이름으로 입력칸을 새로 채우려고 key 를 바꿔요
  const [modalKey, setModalKey] = useState(0)
  const role = user.role ?? 'FREE'
  // 운영진은 지금 보고 있는 플랜도 같이 (ADMIN · FREE)
  const badge = role === 'ADMIN' ? `ADMIN · ${user.adminViewPlan === 'FREE' ? 'FREE' : 'PRO'}` : role

  return (
    <Card className="flex items-center gap-4 p-5">
      <span className="from-ai-gradient-start to-ai-gradient-end text-h3 grid size-12 shrink-0 place-items-center rounded-full bg-linear-to-br text-white">
        {user.name.slice(0, 1)}
      </span>
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <Badge tone={role === 'FREE' ? 'neutral' : 'brand'}>{badge}</Badge>
        <p className="truncate text-[18px] font-bold">{user.name}</p>
      </div>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => {
          setModalKey((k) => k + 1)
          setEditing(true)
          track('settings.profile_edit_open', {
            is_premium: isPremiumRole(user.role, user.adminViewPlan),
          })
        }}
      >
        프로필 수정
      </Button>
      <ProfileEditModal key={modalKey} open={editing} currentName={user.name} onClose={() => setEditing(false)} />
    </Card>
  )
}
