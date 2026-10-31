package com.naleul.naleul.domain.goalProgress.dto.request;

import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import jakarta.validation.constraints.NotNull;

/** 끝난 목표의 결과 다시 고르기 ("이 목표 어땠나요?") */
public record GoalOutcomeRequest(
        @NotNull(message = "결과를 골라 주세요.") OutcomeResult result
) {
}
