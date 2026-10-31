package com.naleul.naleul.domain.routine.dto.response;

import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class RoutineResponse {
    private Long routineId;
    private String routineName;
    private Long goalCategoryId;
    private String goalCategoryName;
    private String goalCategoryColorCode;
    private Long generalCategoryId;
    private String generalCategoryName;
    private String generalCategoryColorCode;
    private LocalDate repeatStartDate;
    private LocalDate repeatEndDate;
    private LocalDate actualEndDate;
    private LocalTime repeatStartTime;
    private LocalTime repeatEndTime;
    private RoutineStatus routineStatus;
    private List<RepeatDayInfo> repeatDays;
    private boolean notificationEnabled;

    public static RoutineResponse from(Routine routine) {
        String goalColorCode = routine.getGoalCategory() != null && routine.getGoalCategory().getUserColor() != null
                ? routine.getGoalCategory().getUserColor().getColorCode()
                : null;

        String generalColorCode = routine.getGeneralCategory() != null && routine.getGeneralCategory().getColor() != null
                ? routine.getGeneralCategory().getColor().getColorCode()
                : null;

        return RoutineResponse.builder()
                .routineId(routine.getRoutineId())
                .routineName(routine.getRoutineName())
                .goalCategoryId(routine.getGoalCategory().getGoalCategoryId())
                .goalCategoryName(routine.getGoalCategory().getGoalCategoryName())
                .goalCategoryColorCode(goalColorCode)
                .generalCategoryId(routine.getGeneralCategory().getGeneralCategoryId())
                .generalCategoryName(routine.getGeneralCategory().getGeneralCategoryName())
                .generalCategoryColorCode(generalColorCode)
                .repeatStartDate(routine.getRepeatStartDate())
                .repeatEndDate(routine.getRepeatEndDate())
                .actualEndDate(routine.getActualEndDate())
                .repeatStartTime(routine.getRepeatStartTime())
                .repeatEndTime(routine.getRepeatEndTime())
                .routineStatus(routine.getRoutineStatus())
                .repeatDays(routine.getRepeatDays().stream()
                        .map(RepeatDayInfo::from)
                        .toList())
                .notificationEnabled(routine.isNotificationEnabled())
                .build();
    }

    @Getter
    @Builder
    public static class RepeatDayInfo {
        private Long id;
        private DayOfWeek dayOfWeek;
        /** 이 요일만 다른 시간 (V41). null 이면 루틴 기본 시간 */
        private LocalTime startTime;
        private LocalTime endTime;

        public static RepeatDayInfo from(RoutineRepeatDay repeatDay) {
            return RepeatDayInfo.builder()
                    .id(repeatDay.getId())
                    .dayOfWeek(repeatDay.getDayOfWeek())
                    .startTime(repeatDay.getStartTime())
                    .endTime(repeatDay.getEndTime())
                    .build();
        }
    }
}