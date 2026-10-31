package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;

import java.time.LocalDate;
import java.util.List;

/**
 * 인터뷰 AI 입력 (명세 8-2).
 * @param slots              현재까지 채워진 슬롯
 * @param history            지금까지의 대화 (마지막 USER 메시지 포함)
 * @param remainingQuestions 남은 질문 수 (5 - questionCount)
 * @param today              오늘 날짜 — "6개월" 같은 표현을 날짜로 바꾸는 기준
 * @param sourceContext      임시 목표 구체화일 때 기존 Task 목록 등 (없으면 null)
 * @param kind               사용자가 고른 목표 카테고리 (없으면 null)
 * @param mustDo             시작 화면에서 고른 "꼭 하고 싶은 일" (V42, 없으면 빈 목록)
 * @param capacityNote       이 사용자의 실행 기록 한 줄 (V47) — 기록이 없으면 "현재 수준도 물어"라는 안내 (없으면 null)
 */
public record InterviewTurnRequest(
        GoalSlots slots,
        List<ConversationMessage> history,
        int remainingQuestions,
        LocalDate today,
        String sourceContext,
        GoalKind kind,
        List<String> mustDo,
        String capacityNote
) {
    public InterviewTurnRequest {
        mustDo = mustDo != null ? mustDo : List.of();
    }

    public InterviewTurnRequest(GoalSlots slots, List<ConversationMessage> history, int remainingQuestions,
                                LocalDate today, String sourceContext, GoalKind kind, List<String> mustDo) {
        this(slots, history, remainingQuestions, today, sourceContext, kind, mustDo, null);
    }

    public InterviewTurnRequest(GoalSlots slots, List<ConversationMessage> history, int remainingQuestions,
                                LocalDate today, String sourceContext, GoalKind kind) {
        this(slots, history, remainingQuestions, today, sourceContext, kind, List.of(), null);
    }

    public InterviewTurnRequest(GoalSlots slots, List<ConversationMessage> history, int remainingQuestions,
                                LocalDate today, String sourceContext) {
        this(slots, history, remainingQuestions, today, sourceContext, null, List.of(), null);
    }
}
