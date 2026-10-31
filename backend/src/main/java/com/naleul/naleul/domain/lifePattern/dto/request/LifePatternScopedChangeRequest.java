package com.naleul.naleul.domain.lifePattern.dto.request;

import com.naleul.naleul.domain.planner.enums.ChangeScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 캘린더에서 고정 시간을 옮길 때 적용 범위.
 * 예) { "targetDate": "2026-10-06", "startTime": "12:30", "endTime": "13:30", "scope": "WEEK" }
 * scope 가 없으면 DAY (이날만 = 기존 PUT /overrides/{date} 와 같음).
 * scope: DAY 이날만 · WEEK 이번 주 · WEEKDAY 앞으로 이 요일마다 · FOLLOWING 앞으로 매일 (요일별 시간도 이 시간으로 맞춤)
 * startDayOffset: 블록이 targetDate 하루 앞(-1)/뒤(+1)에 시작할 때 (기상·취침 드래그). 없으면 0.
 *   예) 토요일(targetDate) 수면 00:00~07:00 을 금요일 밤 23:30 취침으로 → startTime 23:30, endTime 07:00, startDayOffset -1
 */
public record LifePatternScopedChangeRequest(
        @NotNull(message = "날짜는 필수입니다.")
        LocalDate targetDate,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime,

        ChangeScope scope,

        @Min(-1) @Max(1)
        Integer startDayOffset
) {
    public LifePatternScopedChangeRequest(LocalDate targetDate, LocalTime startTime, LocalTime endTime, ChangeScope scope) {
        this(targetDate, startTime, endTime, scope, null);
    }

    public int offset() {
        return startDayOffset != null ? startDayOffset : 0;
    }
}
