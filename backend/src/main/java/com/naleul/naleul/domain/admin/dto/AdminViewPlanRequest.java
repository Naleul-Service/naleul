package com.naleul.naleul.domain.admin.dto;

import com.naleul.naleul.domain.user.enums.AdminViewPlan;
import jakarta.validation.constraints.NotNull;

public record AdminViewPlanRequest(@NotNull AdminViewPlan plan) {
}
