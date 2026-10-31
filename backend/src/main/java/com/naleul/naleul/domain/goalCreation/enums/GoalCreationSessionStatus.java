package com.naleul.naleul.domain.goalCreation.enums;

/**
 * 목표 생성 세션 상태 (명세 2-1).
 * CONFIRMED, ABANDONED는 종료 상태.
 */
public enum GoalCreationSessionStatus {
    INTERVIEWING,
    READY_TO_GENERATE,
    GENERATING,
    DRAFT_READY,
    CONFIRMED,
    ABANDONED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == ABANDONED;
    }
}
