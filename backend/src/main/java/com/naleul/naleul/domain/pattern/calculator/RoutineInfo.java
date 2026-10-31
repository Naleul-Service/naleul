package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 제안 찾기에 필요한 루틴 정보 (엔티티가 아닌 값).
 * @param days   반복 요일. 비어 있으면 매일 (RoutineService 와 같은 규칙)
 * @param active 진행 중(NOT_STARTED / IN_PROGRESS)
 */
public record RoutineInfo(Long routineId, String name, String emoji,
                          LocalTime start, LocalTime end, Integer durationMinutes,
                          Set<DayOfWeek> days, boolean active, LocalDate repeatEndDate) {

    public Set<DayOfWeek> effectiveDays() {
        return days == null || days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(days);
    }

    /** 소요 시간(분): 시작~종료, 없으면 durationMinutes, 그것도 없으면 30 */
    public int duration() {
        if (start != null && end != null) {
            long m = Duration.between(start, end).toMinutes();
            if (m <= 0) m += 24 * 60; // 자정을 넘기는 루틴
            return (int) m;
        }
        return durationMinutes != null && durationMinutes > 0 ? durationMinutes : 30;
    }
}
