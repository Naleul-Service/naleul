package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AchievementChartDto {
    private Long totalCount;
    private Long achievedCount;
    private Long unachievedCount;
    private Double achievementRate; // 0~100, 소수점 1자리

    // ⚠️ 신규: 목표별 세부 항목. 필터링 없는 "전체" 응답이며,
    //    iOS가 status로 걸러서 totalCount/achievedCount를 재합산하는 데 사용합니다.
    private List<GoalAchievementBreakdownDto> breakdown;
}