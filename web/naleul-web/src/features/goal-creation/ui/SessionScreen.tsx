'use client'

import { useState } from 'react'
import { SessionGate } from './SessionGate'
import { InterviewChatView } from './InterviewChatView'

/** /goal/new/[sessionId] — G-1 목표 대화 */
export function SessionScreen({ sessionId }: { sessionId: number }) {
  // 409 등으로 서버와 어긋나면 이 값을 올려 대화 화면을 새로 그려요
  const [syncKey, setSyncKey] = useState(0)

  return (
    <SessionGate sessionId={sessionId} allow={['INTERVIEWING']}>
      {(session, refetch) => (
        <InterviewChatView
          key={`${sessionId}-${syncKey}`}
          initial={session}
          onResync={async () => {
            await refetch()
            setSyncKey((k) => k + 1)
          }}
        />
      )}
    </SessionGate>
  )
}
