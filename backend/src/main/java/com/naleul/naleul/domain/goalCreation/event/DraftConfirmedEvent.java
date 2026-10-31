package com.naleul.naleul.domain.goalCreation.event;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;

/**
 * V68: AI 초안을 확정했을 때 "AI 초안 정확도" 기록용 (운영 대시보드 전용).
 * DraftReviewRecorder 가 AFTER_COMMIT 으로 받아 별도 트랜잭션에서 기록한다 — 기록이 실패해도 확정에는 영향 없음.
 *
 * @param draftId   확정한 초안 (AI 최신 초안)
 * @param finalPlan 사용자가 확정한 계획 (서버가 지난 날짜를 오늘로 당기기 전, 화면 그대로)
 */
public record DraftConfirmedEvent(Long sessionId, Long goalCategoryId, Long userId, Long draftId, GoalPlanDto finalPlan) {
}
