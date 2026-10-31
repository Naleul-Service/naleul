package com.naleul.naleul.domain.routine.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 요일별 시간 (V41) — 이 요일만 루틴 기본 시간과 다르게.
 * 예) { "dayOfWeek": "WEDNESDAY", "startTime": "07:00", "endTime": "07:25" }
 * startTime·endTime 은 둘 다 있어야 하고, 시작이 끝보다 빨라야 한다.
 */
public record RoutineDayTime(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
