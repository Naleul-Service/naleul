package com.naleul.naleul.domain.aiQuality.enums;

/** AI 제안 1건의 상태 (V64) */
public enum AiSuggestionStatus {
    SUGGESTED,  // AI 가 제안을 냈다 (미리보기 · 해석 결과)
    APPLIED     // 사용자가 적용 · 확정했다
}
