package com.naleul.naleul.domain.planner.engine;

/** 중요도 등급 (명세 6-3). HIGH ≥ 60, MID 35~59, LOW < 35 */
public enum ImportanceLevel {
    HIGH, MID, LOW;

    public static ImportanceLevel of(int score) {
        if (score >= 60) return HIGH;
        if (score >= 35) return MID;
        return LOW;
    }
}
