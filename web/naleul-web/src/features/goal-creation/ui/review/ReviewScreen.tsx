'use client'

import { SessionGate } from '../SessionGate'
import { ReviewView } from './ReviewView'

/** /goal/new/[sessionId]/review — G-2 (초안 검토에서 "정보 수정하기"로 돌아와도 사용) */
export function ReviewScreen({ sessionId }: { sessionId: number }) {
  return (
    <SessionGate sessionId={sessionId} allow={['READY_TO_GENERATE', 'DRAFT_READY']}>
      {(session) => <ReviewView session={session} />}
    </SessionGate>
  )
}
