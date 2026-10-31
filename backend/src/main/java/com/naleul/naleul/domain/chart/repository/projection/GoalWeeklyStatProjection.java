package com.naleul.naleul.domain.chart.repository.projection;
// domain/chart/repository/projection/GoalWeeklyStatProjection.java

public interface GoalWeeklyStatProjection {
    Long getGoalCategoryId();
    String getGoalCategoryName();
    Long getTotalCount();
    Long getAchievedCount();
}