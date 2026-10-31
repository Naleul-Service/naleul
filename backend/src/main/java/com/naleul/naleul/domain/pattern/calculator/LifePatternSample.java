package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 현재 생활 패턴 (히트맵 빗금, 자주 놓치는 블록 부제용).
 * daysMask: 월=1 화=2 수=4 목=8 금=16 토=32 일=64 (LifePattern 과 같음)
 */
public record LifePatternSample(String title, LifePatternType type, LocalTime start, LocalTime end, int daysMask) {

    public boolean appliesOn(DayOfWeek d) {
        return (daysMask & (1 << (d.getValue() - 1))) != 0;
    }

    public boolean crossesMidnight() {
        return !end.isAfter(start);
    }

    /**
     * 그 요일의 [hour, hour+1) 중 이 패턴이 덮는 분.
     * 자정을 넘기는 패턴(23:00~07:00)은 시작한 요일에 23~24시, 다음 요일에 0~7시를 덮는다.
     */
    public int minutesCovering(DayOfWeek day, int hour) {
        int hs = hour * 60, he = hs + 60;
        int s = start.getHour() * 60 + start.getMinute();
        int e = end.getHour() * 60 + end.getMinute();
        int covered = 0;
        if (!crossesMidnight()) {
            if (appliesOn(day)) covered += overlap(hs, he, s, e);
        } else {
            if (appliesOn(day)) covered += overlap(hs, he, s, 24 * 60);            // 시작한 날 밤
            if (appliesOn(day.minus(1))) covered += overlap(hs, he, 0, e);          // 전날 시작해서 넘어온 새벽
        }
        return Math.min(covered, 60);
    }

    /** 시각(분)이 패턴 구간에서 몇 분 떨어져 있는지 (안이면 0). 요일은 따지지 않는다 */
    public int distanceMinutes(int minuteOfDay) {
        int s = start.getHour() * 60 + start.getMinute();
        int e = end.getHour() * 60 + end.getMinute();
        if (!crossesMidnight()) {
            if (minuteOfDay >= s && minuteOfDay < e) return 0;
            return Math.min(Math.abs(minuteOfDay - s), Math.abs(minuteOfDay - e));
        }
        if (minuteOfDay >= s || minuteOfDay < e) return 0;
        return Math.min(Math.abs(minuteOfDay - s), Math.abs(minuteOfDay - e));
    }

    private static int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }
}
