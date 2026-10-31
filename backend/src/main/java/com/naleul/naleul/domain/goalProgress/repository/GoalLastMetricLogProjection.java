package com.naleul.naleul.domain.goalProgress.repository;

import java.time.LocalDate;

/** 목표별 마지막 수치 기록일 — 목표 "멈춤" 계산용 */
public interface GoalLastMetricLogProjection {
    Long getGoalCategoryId();

    LocalDate getLastDate();
}
