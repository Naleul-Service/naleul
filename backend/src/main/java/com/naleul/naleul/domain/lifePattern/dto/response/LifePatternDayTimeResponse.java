package com.naleul.naleul.domain.lifePattern.dto.response;

import com.naleul.naleul.domain.lifePattern.entity.LifePatternDayTime;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** 요일별 시간 (기본 시간과 다른 요일만 내려간다) */
public record LifePatternDayTimeResponse(DayOfWeek day, LocalTime startTime, LocalTime endTime, int startDayOffset) {
    public static LifePatternDayTimeResponse from(LifePatternDayTime dt) {
        return new LifePatternDayTimeResponse(dt.getDayOfWeek(), dt.getStartTime(), dt.getEndTime(), dt.getStartDayOffset());
    }
}
