package com.naleul.naleul.domain.goalCreation.personal;

/**
 * 새 목표의 "루틴 주간 상한" — 모두에게 같은 420분 대신 이 사람의 실행 역량으로 정한다.
 *
 *  1) 출발점: 최근 4주 실행률
 *     기록 없음 → 240분 (가볍게 시작, 주간 점검에서 늘린다)
 *     80% 이상 → 420분 / 60~79% → 300분 / 60% 미만 → 210분
 *  2) 이미 진행 중인 루틴이 많으면 줄인다: 주 600분 이상 → 70%, 주 360분 이상 → 85%
 *  3) 강도: 도전형은 ×1.35
 *  4) 30분 단위로 반올림, 90~600분 사이
 */
public final class WeeklyCapPolicy {

    static final int NO_HISTORY = 240;
    static final int MIN = 90;
    static final int MAX = 600;

    private WeeklyCapPolicy() {
    }

    public static int cap(CapacityProfile p, PlanIntensity intensity) {
        double base;
        if (p == null || !p.hasHistory()) {
            base = NO_HISTORY;
        } else if (p.executionRate() >= 80) {
            base = 420;
        } else if (p.executionRate() >= 60) {
            base = 300;
        } else {
            base = 210;
        }

        if (p != null) {
            if (p.activeRoutineWeeklyMinutes() >= 600) base *= 0.70;
            else if (p.activeRoutineWeeklyMinutes() >= 360) base *= 0.85;
        }
        if (intensity == PlanIntensity.CHALLENGE) base *= 1.35;

        int rounded = (int) Math.round(base / 30.0) * 30;
        return Math.max(MIN, Math.min(MAX, rounded));
    }
}
