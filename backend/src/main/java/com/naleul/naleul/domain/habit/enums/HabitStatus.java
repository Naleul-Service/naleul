package com.naleul.naleul.domain.habit.enums;

/** 습관 상태 (V52) */
public enum HabitStatus {
    /** 체크하는 중 */
    ACTIVE,
    /** 잠시 쉬는 중 — 쉬는 동안은 "안 한 날"로 세지 않는다 (habit_pause 에 기간이 남는다) */
    PAUSED,
    /** 지움 (소프트 삭제 — 지난 체크 기록은 남긴다) */
    DELETED
}
