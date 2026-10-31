package com.naleul.naleul.domain.planner.enums;

/**
 * 캘린더에서 루틴·고정 시간을 바꿀 때 어디까지 적용할지.
 *  DAY       : 이날만
 *  WEEK      : 이날부터 그 주 일요일까지
 *  FOLLOWING : 이날부터 앞으로 계속 (루틴·고정 시간의 기본값 자체를 바꿈)
 *  WEEKDAY   : 이날부터 앞으로 이 요일마다 (고정 시간 요일별 시간. 루틴은 쓰지 않음)
 */
public enum ChangeScope {
    DAY,
    WEEK,
    FOLLOWING,
    WEEKDAY
}
