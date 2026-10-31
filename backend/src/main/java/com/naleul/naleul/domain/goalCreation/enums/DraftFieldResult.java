package com.naleul.naleul.domain.goalCreation.enums;

/** 칸 · 항목 결과 (V68) */
public enum DraftFieldResult {
    KEPT, EDITED, DELETED, ADDED,
    /** 사용자가 다른 값(목표 기간 · 수치 · 마일스톤 날짜)을 바꿔서 화면이 자동으로 다시 맞춘 칸 — 수정률에서 뺀다 */
    AUTO
}
