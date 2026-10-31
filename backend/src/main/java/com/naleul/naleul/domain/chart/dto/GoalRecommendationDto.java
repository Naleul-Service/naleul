package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalRecommendationDto {
    private List<GoalRecommendationItemDto> keepGoals;
    private List<GoalRecommendationItemDto> improveGoals;
}