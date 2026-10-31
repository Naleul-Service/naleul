package com.naleul.naleul.domain.habit.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 기본 생활 습관 화면 (V52) — GET /api/v1/habits?date=
 *
 * @param goalCategoryId 기본 생활 습관 그릇(goalMode = HABIT)
 * @param scheduledCount 이 날 해야 하는 습관 수 (쉬는 습관 제외)
 * @param doneCount      이 날 체크한 습관 수
 */
public record HabitHomeResponse(
        Long goalCategoryId,
        String goalName,
        String emoji,
        LocalDate date,
        LocalDate today,
        int scheduledCount,
        int doneCount,
        List<HabitResponse> habits
) {
}
