'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQueryClient } from '@tanstack/react-query'
import { Eye } from '@/components/icons'
import { segmentClass, segmentedClass } from '@/components/ui/SegmentedTabs'
import { api, isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'

type Plan = 'FREE' | 'PRO'

const OPTIONS: { value: Plan; label: string }[] = [
  { value: 'FREE', label: 'Free' },
  { value: 'PRO', label: 'Pro' },
]

/**
 * 설정 > 운영진 > 플랜 바꿔 보기 (운영진만 보여요, 2026-10-24).
 * 결제와 상관없이 Free · Pro 화면을 바꿔 가며 확인해요. 서버의 하루 횟수 제한 · Pro 전용 잠금도 같이 바뀌어요.
 * 바꾸면 화면(서버 컴포넌트)을 새로 그리고, 사용량 · 멤버십 같은 데이터도 다시 불러와요.
 */
export function AdminPlanSwitch({ initial }: { initial: Plan }) {
  const [plan, setPlan] = useState<Plan>(initial)
  const [saving, setSaving] = useState(false)
  const router = useRouter()
  const qc = useQueryClient()

  const change = async (next: Plan) => {
    if (next === plan || saving) return
    const from = plan
    setSaving(true)
    setPlan(next)
    try {
      await api.patch('/admin/view-plan', { plan: next })
      track('settings.admin_view_plan_change', {
        from_plan: from,
        to_plan: next,
        result: 'success',
      })
      toast.success(
        next === 'PRO' ? '이제 Pro 화면으로 보여요.' : '이제 Free 화면으로 보여요. 횟수 제한도 Free 와 같아요.'
      )
      await qc.invalidateQueries()
      router.refresh()
    } catch (e) {
      setPlan(from)
      track('settings.admin_view_plan_change', {
        from_plan: from,
        to_plan: next,
        result: 'fail',
        error_status: isApiError(e) ? e.httpStatus : 0,
      })
      toast.error(isApiError(e) ? e.message : '바꾸지 못했어요. 다시 시도해 주세요.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 px-5 py-4">
      <span className="bg-warning-soft text-warning-ink grid size-10 shrink-0 place-items-center rounded-xl">
        <Eye className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-bold">플랜 바꿔 보기</p>
        <p className="text-ink-3 mt-0.5 text-[13px]">
          결제와 상관없이 Free · Pro 화면과 횟수 제한을 확인해요 (나를 앱에도 적용)
        </p>
      </div>
      <div role="radiogroup" aria-label="보고 있는 플랜" aria-busy={saving} className={segmentedClass()}>
        {OPTIONS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={plan === value}
            disabled={saving}
            onClick={() => change(value)}
            className={segmentClass(plan === value, 'px-4 disabled:opacity-60')}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  )
}
