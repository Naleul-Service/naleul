package com.naleul.naleul.domain.pattern.enums;

/** task_schedule_log 에 남기는 일정 변경 종류 (명세 4 · 6-2) */
public enum TaskScheduleAction {
    ENGINE_PLACE,     // 배치 엔진이 시간을 정함 (처음 배치 또는 다시 배치)
    USER_MOVE,        // 사용자가 드래그·수정으로 시간을 바꿈
    USER_UNSCHEDULE,  // 사용자가 "시간 미정"으로 되돌림
    CARRY_OVER        // 22시 작업이 못 한 Task 를 내일로 이월 (from = 원래 날짜·시각 → 그날의 놓침 1건)
}
