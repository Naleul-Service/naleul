// domain/chart/dto/GoalCategoryAchievementStatisticsDto.java
package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalCategoryAchievementStatisticsDto {
    private GoalCategoryHeaderDto header;
    private List<GeneralCategoryAchievementDto> generalCategories;
}