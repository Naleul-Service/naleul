package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalWeeklyBreakdownItemDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private long planAchievedCount;
    private long planTotalCount;
    private double planAchievementRate;
    private long routineAchievedCount;
    private long routineTotalCount;
    private double routineAchievementRate;
    private long totalMinutes;
}