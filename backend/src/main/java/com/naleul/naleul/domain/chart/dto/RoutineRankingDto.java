package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoutineRankingDto {
    private RoutineRankItemDto mostExecutedRoutine;
    private RoutineRankItemDto leastExecutedRoutine;
    private RoutineRankItemDto highestStreakRoutine;
    private RoutineRankItemDto lowestStreakRoutine;
}