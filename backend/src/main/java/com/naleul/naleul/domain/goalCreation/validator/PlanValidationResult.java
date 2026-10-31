package com.naleul.naleul.domain.goalCreation.validator;

import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;

import java.util.List;

/**
 * @param hardViolations 하나라도 있으면 초안 사용 불가 (AI 수정 요청 / 확정 시 422)
 * @param softWarnings   통과는 시키되 초안 화면 상단에 경고로 보여줌 (GoalPlanDto.warnings)
 */
public record PlanValidationResult(List<PlanViolation> hardViolations, List<PlanViolation> softWarnings) {

    public boolean isValid() {
        return hardViolations.isEmpty();
    }
}
