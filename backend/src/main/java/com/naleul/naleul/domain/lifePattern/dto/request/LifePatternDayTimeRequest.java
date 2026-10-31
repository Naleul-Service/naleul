package com.naleul.naleul.domain.lifePattern.dto.request;

import com.naleul.naleul.domain.lifePattern.entity.LifePatternDayTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 고정 시간 요일별 시간 한 줄.  { "day": "SATURDAY", "startTime": "01:00", "endTime": "09:30", "startDayOffset": 0 }
 * startDayOffset: 이 요일 블록이 하루 앞(-1)/뒤(+1)에 시작할 때. 없으면 0.
 *   예) 수면 00:00~07:00 의 토요일 블록을 "금요일 밤 23:30 취침"으로 → { "day": "SATURDAY", "startTime": "23:30", "endTime": "09:00", "startDayOffset": -1 }
 */
public record LifePatternDayTimeRequest(
        @NotNull(message = "요일은 필수입니다.")
        DayOfWeek day,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime,

        @Min(-1) @Max(1)
        Integer startDayOffset
) {
    public LifePatternDayTime toEntity() {
        return new LifePatternDayTime(day, startTime, endTime, startDayOffset != null ? startDayOffset : 0);
    }
}
