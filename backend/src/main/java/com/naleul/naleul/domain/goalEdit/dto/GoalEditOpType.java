package com.naleul.naleul.domain.goalEdit.dto;

/** "말로 고치기"가 만들 수 있는 변경 종류 */
public enum GoalEditOpType {
    /** 목표 이름 · 종료일(date) · 시작값 · 목표값 */
    UPDATE_GOAL,
    ADD_ROUTINE,
    UPDATE_ROUTINE,
    DELETE_ROUTINE,
    /** 점검 시점(마일스톤) — 수치는 서버가 자동 계산하므로 이름·날짜만 */
    ADD_MILESTONE,
    UPDATE_MILESTONE,
    DELETE_MILESTONE,
    /** 하루짜리 할 일 추가 (date 에) */
    ADD_TASK
}
