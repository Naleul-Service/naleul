package com.naleul.naleul.domain.mission.enums;

import java.time.LocalDate;

public enum MissionDuration {
    ONE_WEEK("1주"),
    TWO_WEEKS("2주"),
    THREE_WEEKS("3주"),
    ONE_MONTH("1달"),
    TWO_MONTHS("2달"),
    THREE_MONTHS("3달");

    private final String label;

    MissionDuration(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // ✅ getRequiredAchievedDays() 삭제 — 퍼센트 기반으로 바뀌면서
    //    더 이상 기간별 고정 일수가 필요 없어짐 (TWO_MONTHS/THREE_MONTHS 예외 처리도 자동 해결)

    public LocalDate endDate(LocalDate start) {
        return switch (this) {
            case ONE_WEEK     -> start.plusWeeks(1).minusDays(1);
            case TWO_WEEKS    -> start.plusWeeks(2).minusDays(1);
            case THREE_WEEKS  -> start.plusWeeks(3).minusDays(1);
            case ONE_MONTH    -> start.plusMonths(1).minusDays(1);
            case TWO_MONTHS   -> start.plusMonths(2).minusDays(1);
            case THREE_MONTHS -> start.plusMonths(3).minusDays(1);
        };
    }
}