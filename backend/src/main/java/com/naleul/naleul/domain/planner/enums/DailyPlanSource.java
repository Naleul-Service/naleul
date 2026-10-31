package com.naleul.naleul.domain.planner.enums;

/** 그날 TimeTable이 어떤 계기로 (마지막으로) 만들어졌는지 */
public enum DailyPlanSource {
    NIGHTLY,      // 22시 자동 생성
    BRAIN_DUMP,   // Brain dump 확정 직후 배치
    REPLAN,       // 사용자가 "다시 배치"
    GOAL_CREATED  // AI 목표 확정 직후 배치
}
