package com.naleul.naleul.domain.goalProgress.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 주간 점검 제안 반영 · 미루기 — 진행 응답의 adjustment.key 를 그대로 보낸다 */
public record GoalAdjustmentRequest(
        @NotBlank @Size(max = 100) String key
) {
}
