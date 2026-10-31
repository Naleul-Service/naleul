package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HourlyPatternDto {
    private int hour;
    private long totalCount;
    private long achievedCount;
    private double achievementRate;
}