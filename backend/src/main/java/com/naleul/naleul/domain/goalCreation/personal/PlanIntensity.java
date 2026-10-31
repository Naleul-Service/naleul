package com.naleul.naleul.domain.goalCreation.personal;

/**
 * 계획 강도 — 초안을 만들기 전에 고른다 (기본 안정형).
 * 두 초안을 동시에 만들면 AI 비용·하루 생성 한도가 두 배라, 하나를 고르고 초안 화면에서 바꾸면 다시 만든다.
 */
public enum PlanIntensity {
    /** 지금 실제로 하는 양 근처에서 시작해 천천히 늘린다 */
    STEADY("안정형"),
    /** 개인 상한을 조금 더 넓게, 첫 주부터 상한 가까이 */
    CHALLENGE("도전형");

    private final String label;

    PlanIntensity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
