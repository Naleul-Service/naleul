package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;

public record PlanGenerationResult(GoalPlanDto plan, AiCallMeta meta) {
}
