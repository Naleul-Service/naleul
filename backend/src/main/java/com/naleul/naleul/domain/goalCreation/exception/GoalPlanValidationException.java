package com.naleul.naleul.domain.goalCreation.exception;

import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.Getter;

import java.util.List;

/**
 * 확정 요청의 초안이 HARD 규칙을 위반 → 422 DRAFT_VALIDATION_FAILED + 위반 목록.
 * CustomException을 상속해서, 별도 핸들러가 없더라도 최소한 422 + 메시지는 내려가게 했다.
 */
@Getter
public class GoalPlanValidationException extends CustomException {

    private final List<PlanViolation> violations;

    public GoalPlanValidationException(List<PlanViolation> violations) {
        super(ErrorCode.DRAFT_VALIDATION_FAILED);
        this.violations = List.copyOf(violations);
    }
}
