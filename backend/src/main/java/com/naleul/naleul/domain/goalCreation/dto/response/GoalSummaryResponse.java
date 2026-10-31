package com.naleul.naleul.domain.goalCreation.dto.response;

import com.naleul.naleul.domain.user.enums.PlanningStyle;

/**
 * 정리 확인 화면(G-2)에 보여줄 사람이 읽기 좋은 요약 (명세 3-4, 7-2).
 * 값이 없거나 건너뛴 항목은 null.
 */
public record GoalSummaryResponse(
        String goalStatement,
        String metricText,
        String motivation,
        String deadlineText,
        String preferenceText,
        PlanningStyle planningStyle,
        /** V52: 어떻게 이루고 싶은지 · 정해 둔 계획 (motivation 대신 정리 화면에 보여줄 항목) */
        String approach
) {
}
