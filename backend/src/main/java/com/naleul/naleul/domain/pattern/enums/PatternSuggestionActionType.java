package com.naleul.naleul.domain.pattern.enums;

/** 제안에 대한 사용자 응답 */
public enum PatternSuggestionActionType {
    APPLIED,    // "TimeBlock에 반영하기"
    DISMISSED   // "다음에" → 7일 동안 같은 제안을 다시 만들지 않음
}
