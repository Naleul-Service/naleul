package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalWeeklySeriesItemDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private List<Double> weeklyPlanRates;    // 1~4주차 계획 달성률(%)
    private List<Double> weeklyRoutineRates; // 1~4주차 루틴 달성률(%)
    private List<Long> weeklyMinutes;        // 1~4주차 투입 시간(분)
}