package com.naleul.naleul.domain.task.repository.projection;

import java.time.LocalDateTime;

/** 목표별 마지막으로 Task 완료 버튼을 누른 시각 — 목표 "멈춤" 계산용 */
public interface GoalLastDoneProjection {
    Long getGoalCategoryId();

    LocalDateTime getLastAt();
}
