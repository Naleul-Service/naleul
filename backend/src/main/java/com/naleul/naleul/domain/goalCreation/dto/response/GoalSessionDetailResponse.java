package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;

import java.util.List;

/** 세션 조회 / 이어하기 (명세 7-7) */
public record GoalSessionDetailResponse(
        Long sessionId,
        GoalCreationSessionStatus status,
        int questionCount,
        int maxQuestions,
        boolean canFinishEarly,
        List<GoalMessageResponse> messages,
        GoalSlots slots,
        GoalSummaryResponse summary,
        GoalDraftBriefResponse latestDraft,
        /** 꼭 하고 싶은 일 (V42) — 없으면 빈 목록 */
        List<String> mustDoItems
) {
}
