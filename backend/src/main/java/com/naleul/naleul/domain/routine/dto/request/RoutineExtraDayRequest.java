package com.naleul.naleul.domain.routine.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 반복 요일이 아닌 날에 루틴을 한 번 더 (예: 평일 루틴을 토요일에도 했어요).
 * { "date": "2026-10-17", "startTime": "10:00", "endTime": "10:30", "done": true }
 *
 * @param startTime · endTime 없으면 루틴 기본 시간 (루틴도 시간이 없으면 시간 미정)
 * @param done      true 면 "이미 했어요" — 그 시간에 한 것으로 완료 (오늘 · 지난날만)
 */
public record RoutineExtraDayRequest(
        @NotNull(message = "날짜를 골라 주세요.") LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean done
) {
}
