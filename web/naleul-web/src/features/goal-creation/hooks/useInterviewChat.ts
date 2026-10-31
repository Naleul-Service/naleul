'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { isApiError } from '@/lib/client/api'
import { track } from '@/lib/client/analytics'
import { toast } from '@/stores/toastStore'
import { goalCreationApi, goalCreationKeys } from '../api'
import type { GoalMessage, GoalSlots, GoalSummary, SessionDetail, SessionStatus, TurnResponse } from '../types'

/** 화면에 그리는 말풍선 하나 */
export interface ChatItem {
  key: string
  role: 'USER' | 'ASSISTANT'
  content: string | null
  skip: boolean
  quickReplies: string[]
  skippable: boolean
  /** 질문이 채우려는 슬롯 ('extraNote' = 마지막 자유 질문) */
  targetSlot?: string | null
  /** USER 말풍선 전송 상태 */
  sendState?: 'sending' | 'failed'
  /** 실패 시 다시 보낼 내용 */
  failedMessage?: string
}

export interface InterviewMeta {
  status: SessionStatus
  questionCount: number
  maxQuestions: number
  canFinishEarly: boolean
  slots: GoalSlots
  summary: GoalSummary | null
}

/** 다시 보내기가 의미 있는 에러: AI 실패(502), 네트워크/서버 연결 문제 */
const isRetryable = (status: number) => status === 0 || status === 502 || status === 503 || status === 504

const toItem = (m: GoalMessage): ChatItem => ({
  key: `m-${m.messageId}`,
  role: m.role,
  content: m.content,
  skip: !!m.skip,
  quickReplies: m.quickReplies ?? [],
  skippable: !!m.skippable,
  targetSlot: m.targetSlot ?? null,
})

let tempSeq = 0

interface Options {
  initial: SessionDetail
  /** 409 등으로 서버 상태와 어긋났을 때 세션을 다시 불러오기 */
  onResync: () => void
  /** 세션이 없어졌을 때(404) */
  onNotFound: () => void
}

/**
 * G-1 목표 대화 화면의 모든 상태와 동작.
 *
 * 서버가 돌려주는 TurnResponse 를 그대로 화면 상태(meta)로 반영하고,
 * 사용자가 보낸 말풍선은 응답을 기다리지 않고 먼저 그려요(낙관적 업데이트).
 */
export function useInterviewChat({ initial, onResync, onNotFound }: Options) {
  const queryClient = useQueryClient()
  const sessionId = initial.sessionId

  const [items, setItems] = useState<ChatItem[]>(() => initial.messages.map(toItem))
  const [meta, setMeta] = useState<InterviewMeta>(() => ({
    status: initial.status,
    questionCount: initial.questionCount,
    maxQuestions: initial.maxQuestions,
    canFinishEarly: initial.canFinishEarly,
    slots: initial.slots,
    summary: initial.summary,
  }))
  const [input, setInput] = useState('')
  const [pending, setPending] = useState(false)
  /** 부적절한 목표로 중단(422)됐을 때 안내 문구 */
  const [rejectedMessage, setRejectedMessage] = useState<string | null>(null)

  // 같은 렌더 사이클에서 두 번 누르는 것 방지 (state 는 다음 렌더에 반영되기 때문)
  const pendingRef = useRef(false)

  const patchItem = (key: string, patch: Partial<ChatItem>) =>
    setItems((prev) => prev.map((it) => (it.key === key ? { ...it, ...patch } : it)))

  const applyTurn = useCallback(
    (turn: TurnResponse) => {
      setMeta({
        status: turn.status,
        questionCount: turn.questionCount,
        maxQuestions: turn.maxQuestions,
        canFinishEarly: turn.canFinishEarly,
        slots: turn.slots,
        summary: turn.summary,
      })
      if (turn.message) {
        const next = toItem(turn.message)
        setItems((prev) => [...prev, next])
      }
      // 사이드/시작 화면의 "이어서 만들기" 정보도 최신으로
      queryClient.invalidateQueries({ queryKey: goalCreationKeys.active() })
    },
    [queryClient]
  )

  /**
   * 공통 에러 처리.
   * @returns 다시 보내기로 남겨둘지 여부
   */
  // 분석: 마지막 실패를 화면이 어떻게 처리했는지 (goal_chat.answer_result 의 fail_handling)
  const lastHandling = useRef<'retry_shown' | 'dropped' | 'rejected' | 'resynced' | 'not_found'>('dropped')
  const handleError = useCallback(
    (error: unknown): 'retry' | 'drop' => {
      lastHandling.current = 'retry_shown'
      if (!isApiError(error)) {
        toast.error('문제가 생겼어요. 다시 시도해 주세요.')
        return 'retry'
      }
      const { httpStatus, message } = error

      if (isRetryable(httpStatus)) return 'retry'

      lastHandling.current =
        httpStatus === 422 ? 'rejected' : httpStatus === 404 ? 'not_found' : httpStatus === 409 ? 'resynced' : 'dropped'
      if (httpStatus === 422) {
        // 부적절한 목표 → 서버가 세션을 ABANDONED 로 바꿈
        setRejectedMessage(message)
        setMeta((m) => ({ ...m, status: 'ABANDONED' }))
        queryClient.invalidateQueries({ queryKey: goalCreationKeys.active() })
        return 'drop'
      }
      if (httpStatus === 404) {
        toast.error('대화를 찾을 수 없어요.')
        onNotFound()
        return 'drop'
      }
      if (httpStatus === 409) {
        // 다른 탭/기기에서 진행됐거나 이미 끝난 대화 → 서버 상태로 다시 맞춤
        toast.show(message)
        onResync()
        return 'drop'
      }
      // 400(건너뛸 수 없는 질문 등), 429(하루 한도) 등
      toast.error(message)
      return 'drop'
    },
    [onNotFound, onResync, queryClient]
  )

  // 분석: 지금 질문 수 (콜백 안에서 최신 값을 읽으려고 ref)
  const questionCountRef = useRef(initial.questionCount)
  useEffect(() => {
    questionCountRef.current = meta.questionCount
  }, [meta.questionCount])
  // "바로 계획 만들기"로 끝났는지 (goal_chat.interview_complete 의 complete_reason)
  const finishedEarly = useRef(false)

  /** 답변 보내기 / 건너뛰기 / 다시 보내기 */
  const send = useCallback(
    async (
      content: string | null,
      skip: boolean,
      retryKey?: string,
      answerType: 'text' | 'quick_reply' | 'skip' | 'retry' = skip ? 'skip' : 'text',
      chipIndex?: number
    ) => {
      if (pendingRef.current) return
      const text = content?.trim() ?? null
      if (!skip && !text) return

      pendingRef.current = true
      setPending(true)

      const key = retryKey ?? `tmp-${++tempSeq}`
      if (retryKey) {
        patchItem(key, { sendState: 'sending', failedMessage: undefined })
      } else {
        setItems((prev) => [
          ...prev,
          { key, role: 'USER', content: text, skip, quickReplies: [], skippable: false, sendState: 'sending' },
        ])
        if (!skip) setInput('')
      }

      try {
        // 답 방식은 서버의 정리 정확도 측정용 (다시 보내기는 서버가 칩 글자와 비교해 추정)
        const answerSource = answerType === 'quick_reply' ? 'CHIP' : answerType === 'text' ? 'TYPED' : undefined
        const turn = await goalCreationApi.answer(sessionId, {
          content: skip ? null : text,
          skip,
          ...(answerSource ? { answerSource } : {}),
          ...(answerSource === 'CHIP' && chipIndex != null ? { chipIndex } : {}),
        })
        track('goal_chat.answer_result', {
          session_id: sessionId,
          answer_type: answerType,
          result: 'success',
          question_count: turn.questionCount,
        })
        patchItem(key, { sendState: undefined })
        applyTurn(turn)
      } catch (error) {
        const result = handleError(error)
        track('goal_chat.answer_result', {
          session_id: sessionId,
          answer_type: answerType,
          result: 'fail',
          error_status: isApiError(error) ? error.httpStatus : undefined,
          fail_handling: lastHandling.current,
          question_count: questionCountRef.current,
        })
        if (result === 'retry') {
          const msg = isApiError(error) ? error.message : undefined
          patchItem(key, { sendState: 'failed', failedMessage: msg })
        } else {
          // 서버가 받지 않은 말풍선은 지우고, 쓰던 글은 입력창에 돌려놓기
          setItems((prev) => prev.filter((it) => it.key !== key))
          if (!skip && text && !retryKey) setInput((cur) => cur || text)
        }
      } finally {
        pendingRef.current = false
        setPending(false)
      }
    },
    [sessionId, applyTurn, handleError]
  )

  const sendText = useCallback(() => send(input, false), [send, input])
  const sendQuickReply = useCallback(
    (reply: string, index?: number) => send(reply, false, undefined, 'quick_reply', index),
    [send]
  )
  const skip = useCallback(() => send(null, true), [send])

  const retry = useCallback(
    (key: string) => {
      const item = items.find((it) => it.key === key)
      if (!item) return
      // 502 는 서버에 사용자 메시지가 이미 저장된 상태. 같은 내용으로 보내면 중복 저장되지 않아요.
      send(item.content, item.skip, key, 'retry')
    },
    [items, send]
  )

  /** 실패한 말풍선을 지우고 입력창으로 되돌리기 */
  const discardFailed = useCallback(
    (key: string) => {
      const item = items.find((it) => it.key === key)
      setItems((prev) => prev.filter((it) => it.key !== key))
      if (item && !item.skip && item.content) setInput((cur) => cur || item.content || '')
    },
    [items]
  )

  const finishEarly = useCallback(async () => {
    if (pendingRef.current) return
    pendingRef.current = true
    setPending(true)
    try {
      const turn = await goalCreationApi.finishInterview(sessionId)
      finishedEarly.current = true
      applyTurn(turn)
    } catch (error) {
      if (handleError(error) === 'retry') toast.error('계획 단계로 넘어가지 못했어요. 다시 시도해 주세요.')
    } finally {
      pendingRef.current = false
      setPending(false)
    }
  }, [sessionId, applyTurn, handleError])

  // 마지막 AI 질문 (빠른 답변 칩 / 건너뛰기 노출용)
  const lastItem = items[items.length - 1]
  const currentQuestion = lastItem?.role === 'ASSISTANT' ? lastItem : null
  const hasFailed = items.some((it) => it.sendState === 'failed')
  const interviewing = meta.status === 'INTERVIEWING' && !rejectedMessage

  return {
    sessionId,
    items,
    meta,
    input,
    setInput,
    pending,
    rejectedMessage,
    interviewing,
    /** 지금 입력/선택이 가능한지 */
    canInteract: interviewing && !pending && !hasFailed,
    currentQuestion,
    sendText,
    sendQuickReply,
    skip,
    retry,
    discardFailed,
    finishEarly,
    /** 분석: 인터뷰가 "바로 계획 만들기"로 끝났는지 */
    finishedEarly,
  }
}

export type InterviewChat = ReturnType<typeof useInterviewChat>
