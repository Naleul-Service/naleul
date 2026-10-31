package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** "다시 배치" 결과 — 화면은 이 결과로 TimeTable 을 다시 그리거나 /timetable 을 다시 조회한다 */
public record ReplanResponse(
        DailyPlanResponse plan,
        List<Block> placed,
        List<Block> movedToOtherDays,
        List<Long> unscheduled
) {
    public record Block(Long taskId, LocalDate date, LocalDateTime start, LocalDateTime end, String reason) {
    }
}
