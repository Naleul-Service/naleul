package com.naleul.naleul.domain.pattern.calculator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 22시 이월 기록 1건 = 원래 날짜(·시각)의 놓침 1건 (명세 4 · 1-3).
 * @param startAt 원래 시간이 있던 블록이면 시작 시각, 시간 미정이었으면 null
 */
public record CarryMissSample(Long taskId, Long routineId, LocalDate date, LocalDateTime startAt) {

    public boolean timed() {
        return startAt != null;
    }
}
