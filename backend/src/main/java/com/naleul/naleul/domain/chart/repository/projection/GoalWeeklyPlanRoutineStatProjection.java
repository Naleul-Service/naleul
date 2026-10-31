package com.naleul.naleul.domain.chart.repository.projection;

// domain/chart/repository/projection/GoalWeeklyPlanRoutineStatProjection.java

public interface GoalWeeklyPlanRoutineStatProjection {
    Long getGoalCategoryId();
    String getGoalCategoryName();
    Long getPlanTotalCount();
    Long getPlanAchievedCount();
    Long getRoutineTotalCount();
    Long getRoutineAchievedCount();
}