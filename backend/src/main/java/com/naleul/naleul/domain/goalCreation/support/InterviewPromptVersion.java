package com.naleul.naleul.domain.goalCreation.support;

/**
 * 인터뷰 프롬프트(ai/goal-creation/interview-system.txt · interview-tool.json) 버전 (V67).
 * 세션마다 기록해서, 프롬프트를 고친 전후의 "AI 채팅 정리 정확도"를 대시보드에서 비교한다.
 * ⚠️ 인터뷰 프롬프트나 슬롯 추출 규칙(GoalSlotMerger)을 바꾸면 이 값을 꼭 올린다.
 */
public final class InterviewPromptVersion {

    public static final String CURRENT = "2026-10-25";

    private InterviewPromptVersion() {
    }
}
