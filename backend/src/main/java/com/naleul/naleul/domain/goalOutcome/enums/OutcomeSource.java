package com.naleul.naleul.domain.goalOutcome.enums;

/** 결과를 누가 정했는지 */
public enum OutcomeSource {
    USER,  // 사용자가 직접 골랐다 (목표 마무리 · 끝난 뒤 "어땠나요?")
    AUTO   // 기간이 끝나 서버가 수치·실천률로 판정했다
}
