package com.naleul.naleul.domain.chart.repository.projection;

public interface WeekdayCountProjection {
    Integer getWeekday();     // 0=월요일 ~ 6=일요일
    Long getCount();
}