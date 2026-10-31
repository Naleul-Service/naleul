package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.util.List;

/**
 * 요일별 실행률 (명세 4 · 2-3)
 * @param lowest       평균보다 5%p 이상 낮은 가장 낮은 요일 (없으면 null)
 * @param lowestMissed lowest 요일에 가장 많이 놓친 루틴(없으면 목표) 이름
 */
public record WeekdayStats(Integer average, List<Day> days, DayOfWeek lowest, String lowestMissed,
                           Integer weekdayRate, Integer weekendRate) {

    public record Day(DayOfWeek dayOfWeek, Integer rate, int samples) {
    }
}
