package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

/**
 * 초안 폴링 응답 (명세 7-6).
 *  - GENERATING: progressStep, pollAfterMs
 *  - READY:      plan (warnings 포함)
 *  - FAILED:     errorCode, message
 * 해당 없는 필드는 null (ApiResponse가 NON_NULL이라 data 안의 null도 그대로 직렬화되지만 iOS는 Optional로 받으면 됨)
 */
public record GoalDraftResponse(
        Long draftId,
        int version,
        GoalDraftStatus status,
        PlanningStyle planningStyle,
        String progressStep,
        Long pollAfterMs,
        GoalPlanDto plan,
        String errorCode,
        String message
) {
}
