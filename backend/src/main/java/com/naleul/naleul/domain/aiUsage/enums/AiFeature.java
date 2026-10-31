package com.naleul.naleul.domain.aiUsage.enums;

/** AI 를 부르는 기능. 대시보드에서 "어느 기능이 돈을 많이 쓰는지" 나누는 기준 */
public enum AiFeature {
    GOAL_INTERVIEW("AI 목표 인터뷰"),
    GOAL_PLAN("AI 계획 초안"),
    GOAL_PLAN_REVISION("계획 자동 보정"),
    GOAL_EDIT("AI 목표 수정"),
    SCHEDULE_EDIT("AI 일정 조정"),
    BRAIN_DUMP("브레인 덤프"),
    PATTERN_POLISH("패턴 문장 다듬기"),
    REPORT_FEEDBACK("리포트 피드백");

    private final String label;

    AiFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
