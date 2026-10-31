package com.naleul.naleul.domain.goalCategory.kind;

import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 목표 카테고리 1단계 (명세 6.A.1.2).
 * WORK 는 업무형(기록형, goal_mode = RECORD) 목표 전용 — AI 설계 화면의 카테고리 고르기에는 나오지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum GoalType {
    HEALTH("건강", GoalTopic.HEALTH),
    LEARNING("학습·자기개발", GoalTopic.STUDY),
    HABIT("생활습관", GoalTopic.LIFE),
    ETC("기타", GoalTopic.ETC),
    WORK("업무", GoalTopic.CAREER);

    private final String label;
    /** 기존 goal_topic 컬럼(통계·iOS 호환)에 같이 넣을 값 */
    private final GoalTopic topic;
}
