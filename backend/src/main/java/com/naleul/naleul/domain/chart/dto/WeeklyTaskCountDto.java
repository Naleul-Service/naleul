package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class WeeklyTaskCountDto {
    private int weekIndex; // 1~4
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private int totalTaskCount;
}