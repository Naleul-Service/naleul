package com.naleul.naleul.domain.task.enums;

/**
 * Task/Routine이 어디서 만들어졌는지. 기존 데이터는 모두 USER_INPUT.
 */
public enum TaskSource {
    AI_GOAL, USER_INPUT,
    /** 구글 · 아이폰 캘린더에서 가져온 일정 (externalCalendar) — Free 일일 직접 생성 한도에 세지 않는다 */
    EXTERNAL_CALENDAR
}
