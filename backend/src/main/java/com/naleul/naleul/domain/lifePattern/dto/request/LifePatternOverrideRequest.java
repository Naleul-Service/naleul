package com.naleul.naleul.domain.lifePattern.dto.request;

import java.time.LocalTime;

/**
 * 그날만 변경 (드래그).  { "startTime": "13:00", "endTime": "14:00" }
 * 그날만 비우기.         { "skipped": true }
 */
public record LifePatternOverrideRequest(
        LocalTime startTime,
        LocalTime endTime,
        Boolean skipped,
        /** 블록이 그날 하루 앞(-1)/뒤(+1)에 시작할 때. 없으면 0 */
        Integer startDayOffset
) {
    public LifePatternOverrideRequest(LocalTime startTime, LocalTime endTime, Boolean skipped) {
        this(startTime, endTime, skipped, null);
    }

    public int offset() {
        return startDayOffset != null ? startDayOffset : 0;
    }

    public boolean isSkip() {
        return Boolean.TRUE.equals(skipped);
    }
}
