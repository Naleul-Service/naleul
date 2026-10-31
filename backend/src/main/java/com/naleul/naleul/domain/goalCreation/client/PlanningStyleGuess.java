package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.user.enums.PlanningStyle;

/** AI가 대화에서 추정한 성향. 근거가 없으면 UNKNOWN (DB의 PlanningStyle에는 UNKNOWN이 없음) */
public enum PlanningStyleGuess {
    PLANNER, SPONTANEOUS, UNKNOWN;

    /** UNKNOWN이면 null → 세션의 기존 추정값을 덮어쓰지 않음 */
    public PlanningStyle toPlanningStyleOrNull() {
        return switch (this) {
            case PLANNER -> PlanningStyle.PLANNER;
            case SPONTANEOUS -> PlanningStyle.SPONTANEOUS;
            case UNKNOWN -> null;
        };
    }
}
