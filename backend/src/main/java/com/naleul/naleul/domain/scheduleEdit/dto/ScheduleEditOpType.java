package com.naleul.naleul.domain.scheduleEdit.dto;

/** 캘린더 "AI로 일정 조정"이 만들 수 있는 변경 종류 */
public enum ScheduleEditOpType {
    /** 루틴의 기본 요일 · 시간 바꾸기 — 오늘 이후의 그 루틴 Task 가 모두 새 시간으로 (목표 상세 루틴 수정과 같음) */
    MOVE_ROUTINE,
    /** Task 하나(루틴 Task 면 그날만)를 다른 날 · 시각으로 */
    MOVE_TASK
}
