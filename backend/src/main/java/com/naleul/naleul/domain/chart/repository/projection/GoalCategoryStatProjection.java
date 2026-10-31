package com.naleul.naleul.domain.chart.repository.projection;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;

/**
 * "전체 목표" 도넛 차트 전용 projection.
 * 기존 CategoryStatProjection에 goalCategoryStatus만 추가한 버전.
 * generalCategory 조회 쿼리들은 status 개념이 없어서 CategoryStatProjection을 그대로 쓰고,
 * goalCategory 조회(findGoalCategoryStats)만 이걸로 교체합니다.
 */
public interface GoalCategoryStatProjection {
    Long getCategoryId();
    String getCategoryName();
    String getColorHex();
    Long getTotalMinutes();
    GoalCategoryStatus getGoalCategoryStatus();
}