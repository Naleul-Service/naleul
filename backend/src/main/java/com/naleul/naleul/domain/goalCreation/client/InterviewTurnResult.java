package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;

import java.util.List;

/**
 * 인터뷰 AI 출력 (명세 8-2의 submit_interview_turn).
 * nextAction이 ASK일 때만 targetSlot/question/quickReplies가 의미 있음.
 */
public record InterviewTurnResult(
        ExtractedSlots extracted,
        PlanningStyleSignal planningStyleSignal,
        NextAction nextAction,
        SlotKey targetSlot,
        String question,
        List<String> quickReplies,
        AiCallMeta meta
) {
    public InterviewTurnResult {
        extracted = extracted != null ? extracted : ExtractedSlots.none();
        planningStyleSignal = planningStyleSignal != null ? planningStyleSignal : PlanningStyleSignal.unknown();
        nextAction = nextAction != null ? nextAction : NextAction.READY;
        quickReplies = quickReplies != null ? quickReplies : List.of();
    }
}
