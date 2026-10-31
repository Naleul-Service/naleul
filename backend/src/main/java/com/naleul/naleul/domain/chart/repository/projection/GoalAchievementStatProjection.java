package com.naleul.naleul.domain.chart.repository.projection;

/**
 * ⚠️ goalCategoryStatus 추가.
 * native query라 goal_category_status 컬럼값이 문자열로 그대로 매핑됩니다.
 * (JPQL projection처럼 enum 자동 변환이 안 되므로, 서비스 레이어에서
 *  GoalCategoryStatus.valueOf(...)로 변환해서 씁니다.)
 */
public interface GoalAchievementStatProjection {
    Long getGoalCategoryId();
    String getGoalCategoryStatus();
    Long getTotalCount();
    Long getAchievedCount();
}