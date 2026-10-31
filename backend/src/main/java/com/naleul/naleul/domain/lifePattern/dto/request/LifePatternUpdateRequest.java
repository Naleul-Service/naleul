package com.naleul.naleul.domain.lifePattern.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 고정 시간 수정 (설정 화면) — 이후 모든 날짜의 기본값이 바뀐다.
 * null 인 필드는 그대로 둔다. days 에 빈 배열을 보내면 매일.
 * 종류(patternType)는 바꿀 수 없다 (수면은 하루 범위 계산에 쓰이므로).
 * dayTimes: null 이면 그대로, 빈 배열이면 모든 요일 기본 시간, 값이 있으면 통째로 바꾼다.
 */
public record LifePatternUpdateRequest(
        @Size(min = 1, max = 30, message = "이름은 1~30자로 입력해주세요.")
        String title,

        @Size(max = 16)
        String emoji,

        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> days,

        List<@Valid LifePatternDayTimeRequest> dayTimes
) {
    public LifePatternUpdateRequest(String title, String emoji, LocalTime startTime, LocalTime endTime,
                                    List<DayOfWeek> days) {
        this(title, emoji, startTime, endTime, days, null);
    }
}
