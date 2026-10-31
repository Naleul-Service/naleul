package com.naleul.naleul.domain.chart.dto;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PeriodPatternItemDto {
    private int segmentIndex; // 1~7
    private long taskCount;
}