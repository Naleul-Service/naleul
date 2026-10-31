package com.naleul.naleul.domain.goalCategory.kind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 목표 카테고리 2단계 (명세 6.A.1.2).
 * custom = true 인 "기타"는 사용자가 이름을 직접 적는다 (공백 포함 최대 10자).
 * 계획 AI 는 이 값으로 카테고리별 설계 가이드(ai/goal-creation/playbooks.txt)를 고른다.
 */
@Getter
@RequiredArgsConstructor
public enum GoalSubType {
    // 건강
    EXERCISE(GoalType.HEALTH, "운동", false),
    DIET(GoalType.HEALTH, "다이어트", false),
    WATER(GoalType.HEALTH, "물 마시기", false),
    NO_ALCOHOL(GoalType.HEALTH, "금주", false),
    NO_SMOKING(GoalType.HEALTH, "금연", false),
    HEALTH_OTHER(GoalType.HEALTH, "기타", true),
    // 학습·자기개발
    LANGUAGE(GoalType.LEARNING, "언어", false),
    CERTIFICATE(GoalType.LEARNING, "자격증", false),
    READING(GoalType.LEARNING, "독서", false),
    CODING(GoalType.LEARNING, "코딩", false),
    LEARNING_OTHER(GoalType.LEARNING, "기타", true),
    // 생활습관
    WAKE_UP(GoalType.HABIT, "기상", false),
    SLEEP(GoalType.HABIT, "취침", false),
    MEDITATION(GoalType.HABIT, "명상", false),
    CLEANING(GoalType.HABIT, "청소", false),
    JOURNAL(GoalType.HABIT, "일기", false),
    // 기타
    SAVING(GoalType.ETC, "돈 모으기", false),   // 2026-10-24 AI 목표형 카테고리 추가
    OTHER(GoalType.ETC, "기타", true),
    // 업무 (업무형 목표 전용 — AI 설계 가이드 없음)
    COMPANY_WORK(GoalType.WORK, "회사 업무", false),
    SIDE_PROJECT(GoalType.WORK, "사이드 프로젝트", false);

    private final GoalType type;
    private final String label;
    private final boolean custom;
}
