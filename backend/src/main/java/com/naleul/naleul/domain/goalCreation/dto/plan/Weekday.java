package com.naleul.naleul.domain.goalCreation.dto.plan;

import java.time.DayOfWeek;

/**
 * AI/iOS와 주고받는 요일 표기("MON"). Java DayOfWeek는 "MONDAY"라서
 * 그대로 쓰면 Jackson이 "MON"을 읽지 못해 파싱 에러가 난다. 그래서 별도 enum을 둔다.
 */
public enum Weekday {
    MON(DayOfWeek.MONDAY), TUE(DayOfWeek.TUESDAY), WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY), FRI(DayOfWeek.FRIDAY), SAT(DayOfWeek.SATURDAY), SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    Weekday(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    public static Weekday from(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}
