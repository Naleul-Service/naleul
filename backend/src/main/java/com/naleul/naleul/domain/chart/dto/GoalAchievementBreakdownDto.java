package com.naleul.naleul.domain.chart.dto;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * AchievementChartDto.breakdown의 원소 하나 = 목표 하나의 계획/루틴 달성 통계.
 * iOS에서 status로 필터링한 뒤 totalCount/achievedCount를 다시 합산해서
 * 필터된 달성률 도넛을 클라이언트에서 재계산하는 데 씁니다.
 */
@Getter
@Builder
public class GoalAchievementBreakdownDto {
    private Long goalCategoryId;
    private GoalCategoryStatus status;
    private Long totalCount;
    private Long achievedCount;
}