package com.naleul.naleul.domain.habit.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.naleul.naleul.domain.habit.enums.HabitDayState;
import com.naleul.naleul.domain.habit.enums.HabitStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 습관 하나 (V52). scheduled · checked · state 는 요청한 날짜(date, 기본 오늘) 기준.
 *
 * @param repeatDays 비어 있으면 매일 (daily = true)
 * @param streak     연속으로 지킨 "해야 하는 날" 수 (오늘을 아직 안 했으면 어제까지)
 * @param rate30     최근 30일 실천률(%) — 해야 하는 날이 없었으면 null
 * @param recent7    최근 7일(오래된 날 → 기준 날짜) 상태 — 목록 카드의 작은 잔디
 */
public record HabitResponse(
        Long habitId,
        String name,
        String emoji,
        List<DayOfWeek> repeatDays,
        boolean daily,
        @JsonFormat(pattern = "HH:mm") LocalTime remindTime,
        HabitStatus status,
        LocalDate startDate,
        int sortOrder,
        boolean scheduled,
        boolean checked,
        HabitDayState state,
        int streak,
        Integer rate30,
        List<DayCell> recent7,
        /** 하는 시간대 (V56, 없으면 null) */
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {
    public record DayCell(LocalDate date, HabitDayState state) {
    }
}
