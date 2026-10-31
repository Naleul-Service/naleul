package com.naleul.naleul.domain.pattern.enums;

/** 하단 제안 종류 (명세 4 · 4-1) */
public enum PatternSuggestionType {
    ROUTINE_TIME,  // 루틴 시작·종료 시각 변경
    ROUTINE_DAY,   // 루틴 반복 요일 교체
    LOAD_DOWN,     // 목표 주간 점검 — 실천률이 낮아 루틴 요일 하나 줄이기 (V47)
    LOAD_UP        // 목표 주간 점검 — 실천률이 높아 루틴 요일 하나 늘리기 (V47)
}
