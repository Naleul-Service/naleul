package com.naleul.naleul.domain.pattern.enums;

/** 나의 패턴 분석 기간 (명세 4 · 1-2) */
public enum PatternPeriod {
    RECENT_4W,  // 어제 기준 최근 28일
    ALL;        // 첫 기록 ~ 어제, 최대 365일

    public static final int RECENT_DAYS = 28;
    public static final int ALL_MAX_DAYS = 365;
}
