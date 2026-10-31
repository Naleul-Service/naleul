package com.naleul.naleul.domain.goalCreation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;

import java.util.List;

/**
 * 세션 시작 / 답변 / 조기 종료 / 슬롯 수정 공통 응답 (명세 7-1 ~ 7-4).
 *  - 인터뷰 계속: message = 다음 질문, summary = null
 *  - 인터뷰 종료(READY_TO_GENERATE): message = null, summary = 요약
 */
@JsonInclude(JsonInclude.Include.ALWAYS)   // iOS가 message == null로 종료를 판단하므로 null도 내려준다
public record GoalSessionTurnResponse(
        Long sessionId,
        GoalCreationSessionStatus status,
        int questionCount,
        int maxQuestions,
        boolean canFinishEarly,
        GoalMessageResponse message,
        GoalSlots slots,
        GoalSummaryResponse summary,
        /** 꼭 하고 싶은 일 (V42) — 없으면 빈 목록 */
        List<String> mustDoItems
) {
}
