package com.naleul.naleul.domain.generalCategory.dto.response;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class GeneralCategoryResponse {

    private Long generalCategoryId;
    private String generalCategoryName;
    private Long goalCategoryId;
    private String goalCategoryName;
    private Long colorId;
    private String colorCode;
    private LocalDate generalCategoryStartDate;
    private LocalDate generalCategoryEndDate;
    private LocalDate actualEndDate;
    private GoalCategoryStatus generalCategoryStatus;
    private String achievement;
    private boolean isDefault;
    private List<RoutineSummary> routines;

    public static GeneralCategoryResponse from(GeneralCategory generalCategory) {
        return GeneralCategoryResponse.builder()
                .generalCategoryId(generalCategory.getGeneralCategoryId())
                .generalCategoryName(generalCategory.getGeneralCategoryName())
                .goalCategoryId(generalCategory.getGoalCategory().getGoalCategoryId())
                .goalCategoryName(generalCategory.getGoalCategory().getGoalCategoryName())
                .generalCategoryStatus(generalCategory.getGeneralCategoryStatus())
                .achievement(generalCategory.getAchievement())
                .generalCategoryStartDate(generalCategory.getGeneralCategoryStartDate())
                .generalCategoryEndDate(generalCategory.getGeneralCategoryEndDate())
                .actualEndDate(generalCategory.getActualEndDate())
                .colorCode(generalCategory.getColor() != null
                        ? generalCategory.getColor().getColorCode()
                        : null)
                .colorId(generalCategory.getColor() != null
                        ? generalCategory.getColor().getUserColorId()
                        : null)
                // ✅ 추가: DELETED 상태 루틴 제외
                .routines(generalCategory.getRoutines().stream()
                        .filter(r -> r.getRoutineStatus() != RoutineStatus.DELETED)
                        .map(RoutineSummary::from)
                        .toList())
                .build();
    }

    // ✅ 루틴 요약 정보 (Response 안에 중첩 클래스로)
    @Getter
    @Builder
    public static class RoutineSummary {
        private Long routineId;
        private String routineName;
        private RoutineStatus routineStatus;
        private LocalDate repeatStartDate;
        private LocalDate repeatEndDate;
        private LocalTime repeatStartTime;
        private LocalTime repeatEndTime;
        private List<DayOfWeek> repeatDays;
        private boolean notificationEnabled;
        /** 구체적인 방법 (AI 목표 루틴). 없으면 null */
        private String description;
        private Integer durationMinutes;
        /** 기본 시간과 다른 요일만 (V41). 비어 있으면 모든 요일이 repeatStartTime~repeatEndTime */
        private List<DayTimeInfo> dayTimes;

        public static RoutineSummary from(Routine routine) {
            return RoutineSummary.builder()
                    .routineId(routine.getRoutineId())
                    .routineName(routine.getRoutineName())
                    .routineStatus(routine.getRoutineStatus())
                    .repeatStartDate(routine.getRepeatStartDate())
                    .repeatEndDate(routine.getRepeatEndDate())
                    .repeatStartTime(routine.getRepeatStartTime())
                    .repeatEndTime(routine.getRepeatEndTime())
                    .repeatDays(routine.getRepeatDays().stream()
                            .map(rd -> rd.getDayOfWeek())
                            .toList())
                    .notificationEnabled(routine.isNotificationEnabled())
                    .description(routine.getDescription())
                    .durationMinutes(routine.getDurationMinutes())
                    .dayTimes(routine.getRepeatDays().stream()
                            .filter(rd -> rd.hasOwnTime())
                            .map(rd -> new DayTimeInfo(rd.getDayOfWeek(), rd.getStartTime(), rd.getEndTime()))
                            .toList())
                    .build();
        }
    }

    /** 요일별 시간 (V41) */
    public record DayTimeInfo(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}