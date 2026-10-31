package com.naleul.naleul.domain.goalCreation.dto.response;

/** 확정 응답. iOS는 이 id로 목표 상세 화면으로 이동 (GET /api/v1/goal-categories/{goalId}) */
public record GoalConfirmResponse(Long goalId) {
}
