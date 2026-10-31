package com.naleul.naleul.domain.routine.dto.response;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.util.RoutineStreakCalculator;
import com.naleul.naleul.domain.routine.util.RoutineTimeStatsCalculator.TimeStats;

import java.time.LocalDate;
import java.util.List;

public record RoutineHeatmapResponse(
        Long routineId,
        String routineName,
        LocalDate repeatStartDate,
        LocalDate repeatEndDate,
        List<String> repeatDays,
        List<LocalDate> completedDates,
        int currentStreak,
        int longestStreak,
        /** 주로 몇 시부터 몇 시에 했는지 (2026-10). 시각을 알 수 있는 기록이 없으면 null */
        TimeStats timeStats
) {
    public static RoutineHeatmapResponse of(Routine routine, List<LocalDate> completedDates) {
        return of(routine, completedDates, null);
    }

    public static RoutineHeatmapResponse of(Routine routine, List<LocalDate> completedDates, TimeStats timeStats) {
        int currentStreak = RoutineStreakCalculator.calculateCurrentStreak(routine, completedDates);
        int longestStreak = RoutineStreakCalculator.calculateLongestStreak(routine, completedDates);

        return new RoutineHeatmapResponse(
                routine.getRoutineId(),
                routine.getRoutineName(),
                routine.getRepeatStartDate(),
                routine.getRepeatEndDate(),
                routine.getRepeatDays().stream()
                        .map(rd -> rd.getDayOfWeek().name())
                        .toList(),
                completedDates,
                currentStreak,
                longestStreak,
                timeStats
        );
    }
}