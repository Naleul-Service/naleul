package com.naleul.naleul.domain.chart.repository.projection;

public interface GoalGeneralStatProjection {
    Long getGoalCategoryId();
    String getGoalCategoryName();
    String getGoalColorHex();
    Long getGeneralCategoryId();
    String getGeneralCategoryName();
    String getGeneralColorHex();
    Long getTotalMinutes();
}