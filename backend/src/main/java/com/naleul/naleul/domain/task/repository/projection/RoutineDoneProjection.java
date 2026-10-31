package com.naleul.naleul.domain.task.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 완료한 루틴 Task 한 건 — 루틴별 "쌓인 시간·횟수" 통계용 (기록형 목표 패턴) */
public interface RoutineDoneProjection {
    Long getRoutineId();

    LocalDate getRoutineDate();

    LocalDateTime getActualStartAt();

    LocalDateTime getActualEndAt();

    Long getPlannedDurationMinutes();
}
