package com.naleul.naleul.domain.chart.dto;

// domain/chart/dto/GoalWeeklyRankingDto.java
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalWeeklyRankingDto {
    private GoalRankItemDto mostAchievedGoal;   // Task 개수 최다
    private GoalRankItemDto leastAchievedGoal;  // Task 개수 최소
    private GoalRankItemDto highestRateGoal;    // 실천률(%) 최고
    private GoalRankItemDto lowestRateGoal;     // 실천률(%) 최저
}