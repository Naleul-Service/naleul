package com.naleul.naleul.domain.routine.dto.response;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RoutineDetailResponse(
        Long routineId,
        String routineName,
        RoutineStatus routineStatus,
        LocalDate repeatStartDate,
        LocalDate repeatEndDate,
        LocalTime repeatStartTime,
        LocalTime repeatEndTime,
        List<DayOfWeek> repeatDays,
        Long goalCategoryId,
        String goalCategoryName,
        String goalCategoryColorCode,
        Long generalCategoryId,
        String generalCategoryName,
        String generalCategoryColorCode
) {
    public static RoutineDetailResponse from(Routine routine) {
        String goalColorCode = routine.getGoalCategory() != null && routine.getGoalCategory().getUserColor() != null
                ? routine.getGoalCategory().getUserColor().getColorCode()
                : null;

        String generalColorCode = routine.getGeneralCategory() != null && routine.getGeneralCategory().getColor() != null
                ? routine.getGeneralCategory().getColor().getColorCode()
                : null;

        List<DayOfWeek> days = routine.getRepeatDays().stream()
                .map(RoutineRepeatDay::getDayOfWeek)
                .toList();

        return new RoutineDetailResponse(
                routine.getRoutineId(),
                routine.getRoutineName(),
                routine.getRoutineStatus(),
                routine.getRepeatStartDate(),
                routine.getRepeatEndDate(),
                routine.getRepeatStartTime(),
                routine.getRepeatEndTime(),
                days,
                routine.getGoalCategory() != null ? routine.getGoalCategory().getGoalCategoryId() : null,
                routine.getGoalCategory() != null ? routine.getGoalCategory().getGoalCategoryName() : null,
                goalColorCode,
                routine.getGeneralCategory() != null ? routine.getGeneralCategory().getGeneralCategoryId() : null,
                routine.getGeneralCategory() != null ? routine.getGeneralCategory().getGeneralCategoryName() : null,
                generalColorCode
        );
    }
}