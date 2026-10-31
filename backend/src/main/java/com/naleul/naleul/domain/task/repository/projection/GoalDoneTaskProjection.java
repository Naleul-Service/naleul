package com.naleul.naleul.domain.task.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목표의 완료한 Task 한 건 — 기록형 목표 "나의 패턴" 통계 · "업무 일지"용.
 * 엔티티 전체를 불러오지 않고 날짜·시각 계산에 필요한 칸만 꺼낸다.
 */
public interface GoalDoneTaskProjection {
    Long getTaskId();

    String getTaskName();

    String getEmoji();

    /** 루틴 Task 면 루틴 id, 일반 Task 면 null */
    Long getRoutineId();

    LocalDateTime getActualStartAt();

    LocalDateTime getActualEndAt();

    LocalDateTime getPlannedStartAt();

    LocalDateTime getPlannedEndAt();

    Long getPlannedDurationMinutes();

    LocalDate getRoutineDate();

    LocalDate getScheduledDate();

    LocalDateTime getCompletedAt();
}
