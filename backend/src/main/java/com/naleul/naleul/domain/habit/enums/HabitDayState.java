package com.naleul.naleul.domain.habit.enums;

/** 잔디맵 한 칸 (V52) */
public enum HabitDayState {
    /** 체크함 (반복 요일이 아닌 날에 해도 DONE) */
    DONE,
    /** 해야 하는 날인데 안 함 (오늘은 아직 안 했어도 MISSED 가 아니라 TODO) */
    MISSED,
    /** 오늘 해야 하는데 아직 안 함 */
    TODO,
    /** 반복 요일이 아님 */
    OFF,
    /** 쉬는 중이던 날 */
    PAUSED,
    /** 습관을 만들기 전 */
    BEFORE_START,
    /** 미래 */
    FUTURE
}
