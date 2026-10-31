package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/v1/daily-plans/fill — 날짜별로 "시간 미정 Task 를 빈 시간에 채운" 결과.
 *
 * @param placedCount      새로 시간이 정해진 Task 수 (전체)
 * @param unscheduledCount 빈 시간이 없어 시간 미정으로 남은 Task 수 (전체)
 */
public record FillResponse(
        List<Day> days,
        int placedCount,
        int unscheduledCount
) {
    public record Day(LocalDate date, int placed, int unscheduled) {
    }
}
