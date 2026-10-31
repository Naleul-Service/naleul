package com.naleul.naleul.domain.goalCreation.dto.plan;

/**
 * 검증 규칙 위반 1건 (명세 4-5).
 * 예: { "ruleId": "V5-1", "path": "tasks[1].durationMinutes", "message": "루틴은 30분 이하로 설정해 주세요." }
 */
public record PlanViolation(String ruleId, String path, String message) {
}
