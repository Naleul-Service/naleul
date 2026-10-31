package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalRecommendationItemDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private String reason;
}