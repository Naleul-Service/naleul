package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GoalDeviationItemDto {
    private Long goalCategoryId;
    private String goalCategoryName;
    private List<Long> weeklyMinutes;      // 1~4주차 투입 시간(분)
    private long averageMinutes;
    private double coefficientOfVariation; // CV = 표준편차/평균, 낮을수록 꾸준함
    private String classification;         // "CONSISTENT" | "BURST"
}