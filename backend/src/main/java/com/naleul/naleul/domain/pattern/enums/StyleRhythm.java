package com.naleul.naleul.domain.pattern.enums;

/** 실행 스타일 ② 리듬 (명세 4 · 3-1) */
public enum StyleRhythm {
    WEEKDAY_FOCUSED("평일 집중형"),
    WEEKEND_RECHARGE("주말 몰입형"),
    STEADY("꾸준형"),
    BURST("몰아치기형");

    private final String title;

    StyleRhythm(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
