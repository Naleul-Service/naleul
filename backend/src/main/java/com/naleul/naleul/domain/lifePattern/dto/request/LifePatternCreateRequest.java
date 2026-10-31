package com.naleul.naleul.domain.lifePattern.dto.request;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 고정 시간 추가.
 * 예: { "patternType": "CUSTOM", "title": "회사", "emoji": "💼", "startTime": "09:30", "endTime": "18:00",
 *       "days": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"] }
 * days 를 비우거나 보내지 않으면 매일. endTime < startTime 이면 자정을 넘김 (수면 23:00~07:00).
 */
public record LifePatternCreateRequest(
        @NotNull(message = "고정 시간 종류는 필수입니다.")
        LifePatternType patternType,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 30, message = "이름은 30자 이하로 입력해주세요.")
        String title,

        @Size(max = 16)
        String emoji,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalTime endTime,

        List<DayOfWeek> days,

        /** 요일별로 다른 시간 (없으면 모든 요일 기본 시간) */
        List<@Valid LifePatternDayTimeRequest> dayTimes,

        /** 이날만 (2026-10). 값이 있으면 days · dayTimes 는 무시하고 이 날짜에만 블록이 생긴다 */
        java.time.LocalDate onlyDate
) {
    public LifePatternCreateRequest(LifePatternType patternType, String title, String emoji,
                                    LocalTime startTime, LocalTime endTime, List<DayOfWeek> days) {
        this(patternType, title, emoji, startTime, endTime, days, null, null);
    }

    public LifePatternCreateRequest(LifePatternType patternType, String title, String emoji,
                                    LocalTime startTime, LocalTime endTime, List<DayOfWeek> days,
                                    List<LifePatternDayTimeRequest> dayTimes) {
        this(patternType, title, emoji, startTime, endTime, days, dayTimes, null);
    }
}
