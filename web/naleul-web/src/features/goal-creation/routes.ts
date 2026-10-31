import type { SessionStatus } from './types'

/**
 * 초안을 만들게 한 동작 (goal_generating.view 의 generate_source) — 생성 중 화면 주소에 ?source= 로 실어 보내요.
 * 주소에 없으면(새로고침 · 이어서 보기) resume 으로 봐요.
 */
export type GenerateSource = 'review' | 'regen' | 'quick_fix' | 'style_change' | 'intensity_change' | 'retry' | 'resume'

/** 세션 상태에 맞는 화면 경로 */
export const goalFlowPath = {
  start: () => '/goal/new',
  chat: (id: number) => `/goal/new/${id}`,
  review: (id: number) => `/goal/new/${id}/review`,
  generating: (id: number, draftId?: number, source?: GenerateSource) => {
    const q = new URLSearchParams()
    if (draftId) q.set('draftId', String(draftId))
    if (source) q.set('source', source)
    const qs = q.toString()
    return `/goal/new/${id}/generating${qs ? `?${qs}` : ''}`
  },
  draft: (id: number) => `/goal/new/${id}/draft`,
}

export function pathForStatus(id: number, status: SessionStatus): string | null {
  switch (status) {
    case 'INTERVIEWING':
      return goalFlowPath.chat(id)
    case 'READY_TO_GENERATE':
      return goalFlowPath.review(id)
    case 'GENERATING':
      return goalFlowPath.generating(id)
    case 'DRAFT_READY':
      return goalFlowPath.draft(id)
    default:
      return null // CONFIRMED / ABANDONED → 이어갈 화면 없음
  }
}
