'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { CheckCircle2 } from 'lucide-react'
import { Sparkles } from '@/components/icons'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { track } from '@/lib/client/analytics'
import { LEAVE_NOTICE } from '../constants'
import { goalFlowPath } from '../routes'
import { useInterviewChat } from '../hooks/useInterviewChat'
import type { SessionDetail } from '../types'
import { AssistantBubble, TypingIndicator, UserBubble } from './ChatBubbles'
import { ChatComposer } from './ChatComposer'
import { FlowHeader } from './FlowHeader'
import { InterviewProgress } from './InterviewProgress'

interface Props {
  initial: SessionDetail
  onResync: () => void
}

/** G-1 목표 대화 */
export function InterviewChatView({ initial, onResync }: Props) {
  const router = useRouter()
  const chat = useInterviewChat({
    initial,
    onResync,
    onNotFound: () => router.replace('/goal/new'),
  })
  const { items, meta, pending, interviewing, canInteract, currentQuestion, rejectedMessage } = chat

  const [leaveOpen, setLeaveOpen] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  // ── 분석 (노션 「목표 만들기」 goal_chat.*) ──
  const sid = chat.sessionId
  const qc = meta.questionCount
  useEffect(() => {
    track('goal_chat.pageview', {
      session_id: sid,
      question_count: initial.questionCount,
      max_questions: initial.maxQuestions,
      // 전에 나눈 대화가 있는 세션을 다시 연 건지 (사용자 답이 하나라도 있으면)
      is_resume: initial.messages.some((m) => m.role === 'USER'),
    })
    // 화면이 그려질 때 한 번
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 새 말풍선이 생기면 맨 아래로
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [items.length, pending, meta.status, rejectedMessage])

  const leave = () => router.push('/goal')
  const onClose = () => {
    track('goal_chat.close_click', { session_id: sid, has_confirm: interviewing, question_count: qc })
    if (interviewing) setLeaveOpen(true)
    else leave()
  }

  const done = meta.status === 'READY_TO_GENERATE' && !rejectedMessage
  const goReview = () => {
    track('goal_chat.review_click', { session_id: sid })
    router.replace(goalFlowPath.review(chat.sessionId))
  }

  // 인터뷰 완료 · 거절은 화면에 보이는 순간 한 번
  useEffect(() => {
    if (!done) return
    track('goal_chat.interview_complete', {
      session_id: sid,
      complete_reason: chat.finishedEarly.current ? 'finish_early' : 'all_collected',
      question_count: qc,
    })
    // 완료되는 순간 한 번
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [done])
  useEffect(() => {
    if (rejectedMessage) track('goal_chat.goal_rejected_view', { session_id: sid, question_count: qc })
    // 거절 안내가 뜨는 순간 한 번 (문구 원문은 보내지 않아요)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [!!rejectedMessage])

  // 인터뷰가 끝나면 마지막 말풍선을 잠깐 보여준 뒤 정리 확인 화면(G-2)으로 이동
  useEffect(() => {
    if (!done) return
    const timer = setTimeout(() => router.replace(goalFlowPath.review(chat.sessionId)), 1200)
    return () => clearTimeout(timer)
  }, [done, router, chat.sessionId])

  return (
    <div className="bg-surface flex h-dvh flex-col">
      <FlowHeader
        title="새 목표"
        onClose={onClose}
        center={interviewing && <InterviewProgress current={meta.questionCount} max={meta.maxQuestions} />}
        action={
          interviewing &&
          meta.canFinishEarly && (
            <Button
              variant="ai"
              size="sm"
              onClick={() => {
                track('goal_chat.finish_early_click', {
                  session_id: sid,
                  question_count: qc,
                  max_questions: meta.maxQuestions,
                })
                chat.finishEarly()
              }}
              disabled={!canInteract}
            >
              <Sparkles className="size-3.5" />
              <span className="hidden sm:inline">바로 계획 만들기</span>
              <span className="sm:hidden">바로 만들기</span>
            </Button>
          )
        }
      />

      {/* 대화 영역 */}
      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-6 sm:px-6" role="log" aria-live="polite">
          {items.map((item) =>
            item.role === 'ASSISTANT' ? (
              <AssistantBubble key={item.key} content={item.content} />
            ) : (
              <UserBubble
                key={item.key}
                item={item}
                onRetry={(key) => {
                  track('goal_chat.retry_click', { session_id: sid })
                  chat.retry(key)
                }}
                onDiscard={(key) => {
                  track('goal_chat.discard_click', { session_id: sid })
                  chat.discardFailed(key)
                }}
              />
            )
          )}

          {/* 마지막 질문의 빠른 답변 */}
          {interviewing && currentQuestion && currentQuestion.quickReplies.length > 0 && !pending && (
            <div className="-mt-1 flex flex-wrap gap-2 pl-[42px]">
              {currentQuestion.quickReplies.map((reply, i) => (
                <Chip
                  key={reply}
                  onClick={() => {
                    // 칩 문장은 AI 가 만든 거라 원문 대신 순서만 보내요
                    track('goal_chat.quick_reply_click', { session_id: sid, question_index: qc, reply_index: i })
                    chat.sendQuickReply(reply, i)
                  }}
                  disabled={!canInteract}
                >
                  {reply}
                </Chip>
              ))}
            </div>
          )}

          {pending && <TypingIndicator />}

          {rejectedMessage && (
            <div className="border-line bg-bg-sunken mt-2 rounded-2xl border p-5">
              <p className="text-[15px] leading-relaxed whitespace-pre-wrap">{rejectedMessage}</p>
              <Button
                className="mt-4"
                onClick={() => {
                  track('goal_chat.restart_click', { session_id: sid })
                  router.replace('/goal/new')
                }}
              >
                다른 목표로 시작하기
              </Button>
            </div>
          )}

          <div ref={bottomRef} />
        </div>
      </div>

      {/* 하단: 입력창 또는 인터뷰 완료 패널 */}
      <div className="border-line bg-surface border-t pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto max-w-3xl px-4 py-3 sm:px-6 sm:py-4">
          {interviewing && (
            <>
              {currentQuestion?.skippable && (
                <div className="mb-2 flex justify-end">
                  <button
                    type="button"
                    onClick={() => {
                      track('goal_chat.skip_click', {
                        session_id: sid,
                        question_index: qc,
                        extra_note: currentQuestion?.targetSlot === 'extraNote',
                      })
                      chat.skip()
                    }}
                    disabled={!canInteract}
                    className="text-ink-3 hover:text-ink rounded-lg px-2 py-1 text-[13px] font-medium disabled:opacity-40"
                  >
                    건너뛰기
                  </button>
                </div>
              )}
              <ChatComposer
                value={chat.input}
                onChange={chat.setInput}
                onSubmit={(method) => {
                  track('goal_chat.answer_send', {
                    session_id: sid,
                    question_index: qc,
                    answer_length: chat.input.trim().length,
                    submit_method: method,
                    extra_note: currentQuestion?.targetSlot === 'extraNote',
                  })
                  chat.sendText()
                }}
                disabled={!canInteract}
                autoFocus
                placeholder={
                  currentQuestion?.targetSlot === 'extraNote'
                    ? '예) 평일엔 저녁 9시 이후만 가능해요, 지난번 시험은 60점이었어요'
                    : undefined
                }
              />
            </>
          )}

          {done && (
            <div className="flex flex-col items-center gap-3 py-3 text-center sm:flex-row sm:justify-between sm:text-left">
              <p className="flex items-center gap-1.5 text-[15px] font-bold">
                <CheckCircle2 className="text-success size-5" />
                필요한 정보를 모두 모았어요
              </p>
              <Button variant="brand" onClick={goReview}>
                정리 확인하기
              </Button>
            </div>
          )}

          {!interviewing && !done && !rejectedMessage && (
            <p className="text-ink-3 py-2 text-center text-sm">대화가 끝났어요.</p>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={leaveOpen}
        title="대화를 잠시 멈출까요?"
        description={LEAVE_NOTICE}
        confirmLabel="나가기"
        cancelLabel="계속 만들기"
        onConfirm={() => {
          track('goal_chat.leave_confirm_click', { session_id: sid, question_count: qc })
          leave()
        }}
        onCancel={(method) => {
          track('goal_chat.leave_cancel_click', { session_id: sid, close_method: method })
          setLeaveOpen(false)
        }}
      />
    </div>
  )
}
