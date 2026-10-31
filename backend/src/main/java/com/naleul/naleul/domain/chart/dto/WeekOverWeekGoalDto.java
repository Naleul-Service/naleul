package com.naleul.naleul.domain.chart.dto;

// domain/chart/dto/WeekOverWeekGoalDto.java
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeekOverWeekGoalDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private Double lastWeekRate;
    private Double thisWeekRate;
    private Double rateDeltaPercentPoint; // "+10%p" 계산용
    private Long lastWeekMinutes;
    private Long thisWeekMinutes;
    private Long minutesDelta;
}