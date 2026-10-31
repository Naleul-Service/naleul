package com.naleul.naleul.domain.pattern.enums;

import java.time.DayOfWeek;

/** 평일 / 주말 / 매일 묶음 (골든 타임 · 취약 시간 · 자주 옮기는 블록 문구) */
public enum DayGroup {
    WEEKDAY("평일"),
    WEEKEND("주말"),
    ALL("매일");

    private final String label;

    DayGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean contains(DayOfWeek d) {
        return switch (this) {
            case WEEKDAY -> !isWeekend(d);
            case WEEKEND -> isWeekend(d);
            case ALL -> true;
        };
    }

    public static boolean isWeekend(DayOfWeek d) {
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }
}
