package com.naleul.naleul.domain.task.enums;

/**
 * Task 우선순위 (A 가 가장 중요).
 * TimeBlock 에서 A 는 배지 + 테두리로 강조된다 — 그래서 사용자가 고르지 않은 자동 생성 Task
 * (루틴 · AI 목표 · 목표 직접 추가 · 미션)는 DEFAULT(C)로 시작한다. (2026-10, 이전에는 A)
 */
public enum TaskPriority {
    A, B, C, D, E;

    /** 사용자가 고르지 않았을 때의 기본값 */
    public static final TaskPriority DEFAULT = C;
}
