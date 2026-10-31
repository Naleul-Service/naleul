package com.naleul.naleul.domain.goalCreation.enums;

/** 초안 화면에서 사용자가 직접 고칠 수 있는 칸 (V68). ITEM = 항목 통째로 추가 · 삭제 */
public enum DraftField {
    TITLE("이름"),
    EMOJI("이모지"),
    START_DATE("시작일"),
    END_DATE("종료일"),
    METRIC_START("현재 수치"),
    METRIC_TARGET("목표 수치"),
    DESCRIPTION("설명"),
    DAYS("요일"),
    START_TIME("시작 시각"),
    DURATION("걸리는 시간"),
    HOW_TO("하는 방법"),
    SUB_GOAL("연결된 세부 목표"),
    MILESTONE("연결된 마일스톤"),
    SCHEDULED_DATE("실행일"),
    DUE_DATE("마감일 · 날짜"),
    TARGET_VALUE("단계 수치"),
    ITEM("항목 통째로");

    private final String label;

    DraftField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
