package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/timetable/goal-time — 기간 동안 완료한 Task 의 실천 시간을 목표별로 합친 값 (2026-10).
 * 캘린더 일간 · 3일 · 주간 · 월간 오른쪽(아래) 작은 통계에 써요.
 *
 * @param totalMinutes 모든 목표 합계 (목표 없는 Task 포함)
 * @param goals        많이 쓴 순서. 목표 없이 완료한 Task 는 goalCategoryId = null 한 줄로 묶어요
 */
public record GoalTimeResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalMinutes,
        List<GoalTime> goals
) {
    public record GoalTime(
            Long goalCategoryId,
            String goalCategoryName,
            String goalEmoji,
            String goalColorCode,
            long minutes,
            int taskCount
    ) {}
}
