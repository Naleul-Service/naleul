package com.naleul.naleul.domain.goalCreation.enums;

/** AI 목표 만들기를 끝내지 못한 이유 (V70, 운영 대시보드 "AI 관심도") */
public enum AbandonReason {
    /** 사용자가 직접 그만둠 (나가기 · 취소) */
    USER_CANCEL,
    /** 7일 동안 이어서 하지 않아 정리됨 */
    EXPIRED,
    /** AI 가 목표로 보기 어렵다고 판단 */
    AI_REJECT,
    /** 시작 직후 AI 호출 실패 */
    AI_ERROR
}
