package com.naleul.naleul.domain.goalCreation.enums;

/** 칸 비교 시점 (V68) */
public enum DraftStage {
    /** 확정할 때: AI 최신 초안 ↔ 확정한 계획 */
    CONFIRM,
    /** 말로 고치기 직전: 직전 AI 초안 ↔ 사용자가 손으로 고친 화면 */
    BEFORE_QUICK_FIX
}
