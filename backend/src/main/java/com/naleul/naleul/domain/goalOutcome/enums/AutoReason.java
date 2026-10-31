package com.naleul.naleul.domain.goalOutcome.enums;

/** 서버 자동 판정에 쓴 근거 (V63) */
public enum AutoReason {
    METRIC,        // 수치 목표의 마지막 기록 진행률
    ROUTINE_RATE,  // 루틴 실천률
    TASK_RATE,     // 루틴이 없어 Task 실천률
    NO_RECORD      // 판정할 기록이 없음
}
