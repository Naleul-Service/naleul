package com.naleul.naleul.domain.lifePattern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 고정 시간의 요일별 시간 (예: 주말 수면은 01:00~09:30).
 * 이 요일에 시작하는 블록만 기본 시간 대신 이 시간을 쓴다. 없는 요일은 기본 시간.
 *
 * startDayOffset: 블록이 "이 요일 날짜"보다 며칠 앞/뒤에 시작하는지 (-1 / 0 / +1).
 *   수면 00:00~07:00 패턴의 토요일 블록은 "금요일 밤 → 토요일 아침"이다. 금요일 밤 23:30 에 자게 바꾸면
 *   시작이 금요일로 넘어가므로 (토요일, 23:30, -1) 로 저장한다. 블록 소속 날짜(토요일)는 그대로라서
 *   그날만 변경·요일 판정이 꼬이지 않는다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifePatternDayTime {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "start_day_offset", nullable = false)
    private int startDayOffset;

    public LifePatternDayTime(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int startDayOffset) {
        if (dayOfWeek == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("day/startTime/endTime required");
        }
        if (startDayOffset < -1 || startDayOffset > 1) {
            throw new IllegalArgumentException("startDayOffset must be -1..1");
        }
        if (startTime.equals(endTime)) {
            throw new IllegalArgumentException("startTime and endTime must differ");
        }
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startDayOffset = startDayOffset;
    }

    /** 기본 시간과 같은지 (같으면 저장할 필요 없음) */
    boolean sameAs(LocalTime start, LocalTime end) {
        return startDayOffset == 0 && startTime.equals(start) && endTime.equals(end);
    }
}
