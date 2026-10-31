package com.naleul.naleul.domain.goalCreation.enums;

/** 확정 뒤 그 목표의 첫 행동 (V68) — 이때부터 14일 · 30일을 센다 */
public enum FirstActionType {
    /** 루틴 체크 · Task 완료 */
    TASK_DONE,
    /** 실제 한 일 추가 */
    ACTUAL,
    /** 수치 기록 */
    METRIC
}
