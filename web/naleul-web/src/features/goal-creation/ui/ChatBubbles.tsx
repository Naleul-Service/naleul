'use client'

import { AlertCircle, RotateCw } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { ChatItem } from '../hooks/useInterviewChat'
import { AIBadge } from '@/components/ui/AIBadge'

function AiAvatar() {
  return (
    <AIBadge />
  )
}

export function AssistantBubble({ content }: { content: string | null }) {
  return (
    <div className="flex items-start gap-2.5">
      <AiAvatar />
      {/* 디자인 시스템 ChatBubble AI — 말풍선 없이 Body/LG */}
      <p className="text-body-lg text-text-primary max-w-[85%] pt-1 whitespace-pre-wrap sm:max-w-[75%]">
        {content}
      </p>
    </div>
  )
}

export function UserBubble({
  item,
  onRetry,
  onDiscard,
}: {
  item: ChatItem
  onRetry: (key: string) => void
  onDiscard: (key: string) => void
}) {
  const failed = item.sendState === 'failed'
  return (
    <div className="flex flex-col items-end gap-1.5">
      {item.skip ? (
        <p className="border-border-subtle text-text-tertiary text-body-md rounded-[18px] rounded-br-[6px] border border-dashed px-4 py-2.5">
          건너뛸게요
        </p>
      ) : (
        <p
          className={cn(
            // 디자인 시스템 ChatBubble 사용자 — ai/tint-soft · 11/16 · 모서리 18/18/6/18
            'bg-ai-tint-soft text-text-primary text-body-lg max-w-[85%] rounded-[18px] rounded-br-[6px] px-4 py-[11px] whitespace-pre-wrap sm:max-w-[75%]',
            item.sendState === 'sending' && 'opacity-70',
            failed && 'opacity-50'
          )}
        >
          {item.content}
        </p>
      )}

      {failed && (
        <div className="flex items-center gap-2 text-[13px]">
          <span className="text-danger inline-flex items-center gap-1">
            <AlertCircle className="size-3.5" />
            {item.failedMessage ?? '전송하지 못했어요.'}
          </span>
          <button
            type="button"
            onClick={() => onRetry(item.key)}
            className="text-ink inline-flex items-center gap-1 font-semibold hover:underline"
          >
            <RotateCw className="size-3.5" />
            다시 보내기
          </button>
          <button type="button" onClick={() => onDiscard(item.key)} className="text-ink-3 hover:underline">
            지우기
          </button>
        </div>
      )}
    </div>
  )
}

/** AI 가 답변을 만드는 중 (점 3개) */
export function TypingIndicator() {
  return (
    <div className="flex items-start gap-2.5" role="status" aria-label="AI가 답변을 준비하고 있어요">
      <AiAvatar />
      <span className="flex h-[46px] items-center gap-1 px-1">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="bg-ink-4 size-1.5 animate-bounce rounded-full"
            style={{ animationDelay: `${i * 140}ms`, animationDuration: '900ms' }}
          />
        ))}
      </span>
    </div>
  )
}
