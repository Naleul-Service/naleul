import { kindBody, type GoalKindValue } from '@/features/goal/kind'
import { api } from '@/lib/client/api'
import type {
  ConfirmRequest,
  DraftRequested,
  DraftResponse,
  GoalPlan,
  PlanIntensity,
  PlanningStyle,
  SessionDetail,
  SlotsPatch,
  TurnResponse,
} from './types'

const BASE = '/v1/goal-creation/sessions'

export const goalCreationKeys = {
  all: ['goal-creation'] as const,
  active: () => [...goalCreationKeys.all, 'active'] as const,
  session: (sessionId: number) => [...goalCreationKeys.all, 'session', sessionId] as const,
  draft: (sessionId: number, draftId: number) => [...goalCreationKeys.all, 'draft', sessionId, draftId] as const,
}

export const goalCreationApi = {
  /** 세션 시작 + 첫 질문 (201) */
  /**
   * sourceGoalId: 임시 목표를 구체화할 때 (BE-8). 확정하면 그 임시 목표의 Task·기록이 새 목표로 옮겨져요.
   * 첫 문장을 비우면 서버가 임시 목표 이름으로 시작해요.
   */
  start: (initialMessage?: string, sourceGoalId?: number, kind?: GoalKindValue | null, mustDoItems?: string[]) =>
    api.post<TurnResponse>(BASE, {
      ...(initialMessage ? { initialMessage } : {}),
      ...(sourceGoalId ? { sourceGoalId } : {}),
      // 고른 카테고리 → AI 가 카테고리에 맞는 질문 · 계획 가이드를 써요
      ...kindBody(kind ?? null),
      // 꼭 하고 싶은 일 → 인터뷰가 다시 묻지 않고, 계획에 반드시 루틴으로 넣어요
      ...(mustDoItems?.length ? { mustDoItems } : {}),
    }),

  /** 진행 중인 최신 세션. 없으면 204 → null */
  getActive: () => api.get<SessionDetail | null>(`${BASE}/active`),

  getSession: (sessionId: number) => api.get<SessionDetail>(`${BASE}/${sessionId}`),

  /**
   * 답변 / 건너뛰기.
   * answerSource · chipIndex: 칩을 눌러 답했는지 직접 썼는지 (운영 대시보드 "AI 채팅 정리 정확도" 전용, 화면 변화 없음)
   */
  answer: (
    sessionId: number,
    body: { content: string | null; skip: boolean; answerSource?: 'CHIP' | 'TYPED'; chipIndex?: number }
  ) =>
    api.post<TurnResponse>(`${BASE}/${sessionId}/answers`, body),

  /** "바로 계획 만들기" */
  finishInterview: (sessionId: number) => api.post<TurnResponse>(`${BASE}/${sessionId}/finish-interview`),

  /** 정리 확인 화면에서 슬롯 수정 (바꾼 것만) */
  updateSlots: (sessionId: number, patch: SlotsPatch) => api.patch<TurnResponse>(`${BASE}/${sessionId}/slots`, patch),

  /** 취소 (ABANDONED) */
  cancel: (sessionId: number) => api.delete<null>(`${BASE}/${sessionId}`),

  /** 초안 생성 / 다시 생성 (202, 비동기) */
  /**
   * 초안 생성 / 다시 생성.
   * basePlan 을 같이 보내면 "말로 고치기": 화면에서 직접 고친 지금 초안을 기준으로 feedback 부분만 바꿔요.
   */
  requestDraft: (
    sessionId: number,
    body: {
      feedback?: string
      planningStyle?: PlanningStyle
      intensity?: PlanIntensity
      basePlan?: GoalPlan
    } = {}
  ) => api.post<DraftRequested>(`${BASE}/${sessionId}/drafts`, body),

  /** 초안 폴링 */
  getDraft: (sessionId: number, draftId: number) => api.get<DraftResponse>(`${BASE}/${sessionId}/drafts/${draftId}`),

  /** "이 목표로 시작하기" → 201 { goalId }, 검증 실패 422 + data.violations */
  confirm: (sessionId: number, body: ConfirmRequest) =>
    api.post<{ goalId: number }>(`${BASE}/${sessionId}/confirm`, body),
}
