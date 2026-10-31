package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.util.List;

/** Task 처리 습관 — 루틴이 아닌 Task (명세 4 · 2-8). 표본이 부족한 값은 null */
public record TaskHabitStats(
        int taskCount,
        Double deadlineLeadDays, String deadlineLabel,
        Integer postponedRate, String postponedTopGoal,
        Double brainDumpPerWeek, Double brainDumpAvgItems,
        DayOfWeek brainDumpUsualDay, String brainDumpUsualLabel, String brainDumpUsualRange,
        List<String> insufficient
) {
}
