package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeekdayCountDto {
    private String weekday; // "MONDAY" ~ "SUNDAY"
    private long count;
}