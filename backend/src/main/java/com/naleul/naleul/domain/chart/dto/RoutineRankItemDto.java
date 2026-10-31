package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoutineRankItemDto {
    private Long routineId;
    private String routineName;
    private long executedCount;   // 이번 달 실행한 횟수
    private int longestStreak;    // 이번 달 안에서의 최대 연속일수
}