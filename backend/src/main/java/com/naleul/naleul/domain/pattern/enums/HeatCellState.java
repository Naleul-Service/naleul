package com.naleul.naleul.domain.pattern.enums;

/** 히트맵 칸 상태 (명세 4 · 2-2) */
public enum HeatCellState {
    VALUE,  // 표본 3개 이상 → 실행률 표시
    FIXED,  // 표본이 적고 CUSTOM 생활 패턴(회사 업무 등)이 덮는 시간 → 빗금
    EMPTY   // 블록 없음
}
