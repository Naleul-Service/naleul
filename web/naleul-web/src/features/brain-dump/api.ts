import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/client/api'
import { goalKeys } from '@/features/goal/api'
import { timetableKeys } from '@/features/timetable/api'
import type { ConfirmRequest, ConfirmResponse, ParseItemRequest, ParseResponse, TaskQuota } from './types'

export const quotaKeys = { today: ['task-quota'] as const }

/** 오늘 남은 Task 추가 개수 (Free 하루 2개) */
export function useTaskQuota() {
  return useQuery({
    queryKey: quotaKeys.today,
    queryFn: () => api.get<TaskQuota>('/v1/tasks/quota'),
    staleTime: 0,
  })
}

/** 1단계 → 2단계: AI 해석 (최대 15초 정도 걸릴 수 있어요) */
export function useParseBrainDump() {
  return useMutation({
    mutationFn: (items: ParseItemRequest[]) => api.post<ParseResponse>('/v1/brain-dump/parse', { items }),
  })
}

/** 2단계 → 3단계: 저장 + 배치 */
export function useConfirmBrainDump() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ConfirmRequest) => api.post<ConfirmResponse>('/v1/brain-dump/confirm', body),
    onSuccess: (res) => {
      if (!res.created) return
      qc.invalidateQueries({ queryKey: timetableKeys.all })
      qc.invalidateQueries({ queryKey: quotaKeys.today })
      // 임시 목표가 생겼으면 사이드바 목표 목록도
      if (res.tempGoals.length) qc.invalidateQueries({ queryKey: goalKeys.all })
    },
  })
}
