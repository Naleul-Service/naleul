import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import type {
  AiInterest,
  AdminDashboard,
  AhaMoment,
  ChatReview,
  ChatReviewField,
  ChatReviewItems,
  DraftItemKind,
  DraftReview,
  DraftReviewItems,
} from './types'

/**
 * 운영 대시보드 (GET /api/v1/admin/dashboard?days=30).
 * 운영진이 아니면 백엔드가 403 을 줘요 — 버튼을 숨기는 것과 별개로 서버에서 막아요.
 */
export const adminDashboardKeys = {
  all: ['admin-dashboard'] as const,
  of: (days: number) => [...adminDashboardKeys.all, days] as const,
}

export function useAdminDashboard(days: number) {
  return useQuery({
    queryKey: adminDashboardKeys.of(days),
    queryFn: () => api.get<AdminDashboard>(`/v1/admin/dashboard?days=${days}`),
    staleTime: 60_000,
    // 기간을 바꾸는 동안 이전 숫자를 그대로 보여줘요 (화면이 깜빡이지 않게)
    placeholderData: keepPreviousData,
    // 403(운영진 아님)은 다시 시도해도 같아요
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

/** AI 채팅 정리 정확도 필터 (카테고리 · 답 방식 · 프롬프트 버전) */
export interface ChatReviewFilter {
  goalType?: string
  answerSource?: 'CHIP' | 'TYPED'
  promptVersion?: string
}

function chatReviewQuery(days: number, f: ChatReviewFilter, extra: Record<string, string | number> = {}) {
  const q = new URLSearchParams({ days: String(days) })
  if (f.goalType) q.set('goalType', f.goalType)
  if (f.answerSource) q.set('answerSource', f.answerSource)
  if (f.promptVersion) q.set('promptVersion', f.promptVersion)
  Object.entries(extra).forEach(([k, v]) => q.set(k, String(v)))
  return q.toString()
}

/** GET /api/v1/admin/dashboard/chat-review — 운영진 전용 (V67) */
export function useChatReview(days: number, filter: ChatReviewFilter) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'chat-review', days, filter] as const,
    queryFn: () => api.get<ChatReview>(`/v1/admin/dashboard/chat-review?${chatReviewQuery(days, filter)}`),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

/** 항목 상세 — 그 항목을 고치거나 직접 채운 세션 전부 (20개씩) */
export function useChatReviewItems(
  field: ChatReviewField | null,
  days: number,
  filter: ChatReviewFilter,
  result: 'CHANGED' | 'EDITED' | 'ADDED',
  page: number
) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'chat-review-items', field, days, filter, result, page] as const,
    queryFn: () =>
      api.get<ChatReviewItems>(
        `/v1/admin/dashboard/chat-review/items?${chatReviewQuery(days, filter, { field: field!, result, page })}`
      ),
    enabled: field != null,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

/** AI 초안 정확도 필터 (카테고리 · 강도 · 성향 · 프롬프트 버전 · 유지 기간) */
export interface DraftReviewFilter {
  goalType?: string
  intensity?: 'STEADY' | 'CHALLENGE'
  planningStyle?: 'PLANNER' | 'SPONTANEOUS'
  promptVersion?: string
  window: 14 | 30
}

function draftReviewQuery(days: number, f: DraftReviewFilter, extra: Record<string, string | number> = {}) {
  const q = new URLSearchParams({ days: String(days), window: String(f.window) })
  if (f.goalType) q.set('goalType', f.goalType)
  if (f.intensity) q.set('intensity', f.intensity)
  if (f.planningStyle) q.set('planningStyle', f.planningStyle)
  if (f.promptVersion) q.set('promptVersion', f.promptVersion)
  Object.entries(extra).forEach(([k, v]) => q.set(k, String(v)))
  return q.toString()
}

/** GET /api/v1/admin/dashboard/draft-review — 운영진 전용 (V68) */
export function useDraftReview(days: number, filter: DraftReviewFilter) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'draft-review', days, filter] as const,
    queryFn: () => api.get<DraftReview>(`/v1/admin/dashboard/draft-review?${draftReviewQuery(days, filter)}`),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

export type DraftCaseFilter = 'ALL' | 'EDITED' | 'DELETED' | 'ADDED' | 'AFTER'

/** 항목 상세 — 칸별 수정률 + 고치거나 지우거나 더한 사례 전부 (20개씩, 확정 전 · 확정 뒤) */
export function useDraftReviewItems(
  kind: DraftItemKind | null,
  days: number,
  filter: DraftReviewFilter,
  result: DraftCaseFilter,
  page: number
) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'draft-review-items', kind, days, filter, result, page] as const,
    queryFn: () =>
      api.get<DraftReviewItems>(
        `/v1/admin/dashboard/draft-review/items?${draftReviewQuery(days, filter, { kind: kind!, result, page })}`
      ),
    enabled: kind != null,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

/** GET /api/v1/admin/dashboard/aha — Aha-moment, 운영진 전용 (V69). 기간 = 가입일 기준 */
export function useAhaMoment(days: number) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'aha', days] as const,
    queryFn: () => api.get<AhaMoment>(`/v1/admin/dashboard/aha?days=${days}`),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}

/** AI 관심도 (V70) — 기능별 시도 · 완료 · 중단, Free / Pro */
export function useAiInterest(days: number) {
  return useQuery({
    queryKey: [...adminDashboardKeys.all, 'ai-interest', days] as const,
    queryFn: () => api.get<AiInterest>(`/v1/admin/dashboard/ai-interest?days=${days}`),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
    retry: (count, e) => !(isApiError(e) && e.httpStatus === 403) && count < 2,
  })
}
