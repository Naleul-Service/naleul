// domain/chart/dto/GoalCategoryTimeStatisticsDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalCategoryTimeStatisticsDto {
    private GoalCategoryHeaderDto header;
    private Long totalMinutes;
    private List<GeneralCategoryTimeDto> generalCategories;
}