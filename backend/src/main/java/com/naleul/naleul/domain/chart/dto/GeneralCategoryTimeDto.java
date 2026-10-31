// domain/chart/dto/GeneralCategoryTimeDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GeneralCategoryTimeDto {
    private Long generalCategoryId;
    private String generalCategoryName;
    private String colorHex;
    private Long totalMinutes;
    private Double percentage;   // 도넛차트용 — goalCategory 총합 대비 비율
    private List<RoutineTimeDto> routines;
}