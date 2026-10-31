package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.TimeWindow;

import java.time.DayOfWeek;

/** 개인 맞춤 문장에 쓰는 작은 표기 도우미 */
final class PersonalText {

    private static final String[] DAY = {"월", "화", "수", "목", "금", "토", "일"};

    private PersonalText() {
    }

    /** 90 → "1시간 30분", 45 → "45분", 120 → "2시간" */
    static String minutes(int m) {
        int h = m / 60, r = m % 60;
        if (h == 0) return r + "분";
        return r == 0 ? h + "시간" : h + "시간 " + r + "분";
    }

    /** "평일 19–22시" */
    static String window(TimeWindow w) {
        return w.dayGroup().label() + " " + pad(w.startHour()) + "–" + pad(w.endHour()) + "시";
    }

    static String day(DayOfWeek d) {
        return DAY[d.getValue() - 1];
    }

    private static String pad(int h) {
        return h < 10 ? "0" + h : String.valueOf(h);
    }
}
