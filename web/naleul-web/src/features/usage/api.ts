import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/client/api'

/**
 * 하루 횟수가 정해진 기능의 오늘 사용량 (GET /api/v1/ai-usage).
 * 한도에 걸린 뒤에야 알리는 대신, 시작 전에 "오늘 3/5회 남음"을 보여주려고 써요.
 */
export interface UsageItem {
  /**
   * GOAL_SESSION(_WEEK · _MONTH) · GOAL_DRAFT(_WEEK · _MONTH) · GOAL_EDIT · BRAIN_DUMP · TASK_PER_DAY · SESSION_REGENERATE
   * (2026-10: AI 목표 만들기 · 계획 초안은 하루 · 주 · 월 한도)
   */
  key: string
  label: string
  description: string
  used: number
  /** null 이면 제한 없음 */
  limit: number | null
  remaining: number | null
}

export interface AiUsage {
  pro: boolean
  /** 다시 채워지는 시각 (내일 0시, 한국 시간) */
  resetsAt: string
  items: UsageItem[]
  /** sessionId 를 넘겼을 때: 그 목표 대화에서 계획을 만들 수 있는 횟수 */
  session: UsageItem | null
}

export const usageKeys = {
  all: ['ai-usage'] as const,
  of: (sessionId?: number) => [...usageKeys.all, sessionId ?? null] as const,
}

export function useAiUsage(sessionId?: number) {
  return useQuery({
    queryKey: usageKeys.of(sessionId),
    queryFn: () => api.get<AiUsage>(`/v1/ai-usage${sessionId ? `?sessionId=${sessionId}` : ''}`),
    staleTime: 30_000,
  })
}

/** 횟수를 쓰는 동작(목표 대화 시작·초안 생성·Task 추가) 뒤에 다시 불러와요 */
export function useRefreshUsage() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: usageKeys.all })
}

export const usageItem = (u: AiUsage | undefined, key: string) => u?.items.find((i) => i.key === key)

/**
 * 하루 · 주 · 월 중 가장 먼저 바닥나는 한도 (남은 횟수가 가장 적은 것). 예) 'GOAL_DRAFT' → GOAL_DRAFT · _WEEK · _MONTH
 * 같은 기능의 한도가 여러 개라 화면에는 이것 하나만 보여 줘요.
 */
export function tightestUsage(u: AiUsage | undefined, baseKey: string): UsageItem | undefined {
  const items = [baseKey, `${baseKey}_WEEK`, `${baseKey}_MONTH`]
    .map((k) => usageItem(u, k))
    .filter((i): i is UsageItem => !!i && i.remaining != null)
  return items.reduce<UsageItem | undefined>((min, i) => (!min || i.remaining! < min.remaining! ? i : min), undefined)
}
