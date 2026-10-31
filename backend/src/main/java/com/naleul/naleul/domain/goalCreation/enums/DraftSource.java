package com.naleul.naleul.domain.goalCreation.enums;

/** 초안이 어떤 요청으로 만들어졌는지 (V68, AI 초안 정확도). 앱이 보내는 값으로 서버가 판단한다. */
public enum DraftSource {
    /** 대화 뒤 첫 초안 */
    INITIAL,
    /** 다시 생성 — 직전 초안을 버리고 처음부터 (진짜 마음에 안 듦) */
    REGEN,
    /** 말로 고치기 — 화면의 지금 초안을 기준으로 말한 부분만 */
    QUICK_FIX,
    /** 성향 변경 → 다시 만들기 (조건 변경) */
    STYLE,
    /** 강도 변경 → 다시 만들기 (조건 변경) */
    INTENSITY,
    /** 직전 생성이 실패해서 다시 시도 */
    RETRY
}
