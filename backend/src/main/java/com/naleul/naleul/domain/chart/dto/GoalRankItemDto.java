package com.naleul.naleul.domain.chart.dto;

// domain/chart/dto/GoalRankItemDto.java

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalRankItemDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private long achievedCount;
    private long totalCount;
    private double achievementRate;
    private long totalMinutes;
}