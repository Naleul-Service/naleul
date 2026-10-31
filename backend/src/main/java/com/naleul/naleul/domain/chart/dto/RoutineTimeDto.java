// domain/chart/dto/RoutineTimeDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoutineTimeDto {
    private Long routineId;
    private String routineName;
    private Long totalMinutes;
    private Double percentage;   // 막대차트용 — 이 generalCategory 총합 대비 비율
}