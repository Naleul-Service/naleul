package com.naleul.naleul.domain.planner.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/timetable/monthly — 월간 요약 (명세 5-2).
 * 날짜를 누르면 GET /timetable?startDate=d&endDate=d 로 상세를 가져온다.
 */
public record MonthlyTimetableResponse(
        int year,
        int month,
        List<Day> days,
        List<Deadline> deadlinesThisWeek
) {
    /**
     * rate      : 그날 실행률 (Task 없으면 null)
     * chips     : 루틴이 아닌 Task 최대 3개 (루틴은 실행률로만 요약)
     * moreCount : chips 에 못 넣은 루틴 아닌 Task 수 ("+n개 더보기")
     */
    public record Day(LocalDate date, Integer rate, int taskCount, int completedCount,
                      List<Chip> chips, int moreCount, List<MilestoneChip> milestones) {
    }

    public record Chip(Long taskId, String title, String colorCode, boolean completed, boolean temporaryGoal) {
    }

    public record MilestoneChip(Long milestoneId, String title, String colorCode, Long goalCategoryId) {
    }

    /** type: TASK | MILESTONE */
    public record Deadline(String type, Long id, String title, LocalDate date, String colorCode) {
    }
}
