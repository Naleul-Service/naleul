package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalCategoryChartDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private String colorHex;
    private Long totalMinutes;
    private List<ChartSliceDto> generalCategories;
}