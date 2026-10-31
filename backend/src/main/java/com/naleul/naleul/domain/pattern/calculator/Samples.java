package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.TreeMap;

/** 여러 계산기가 같이 쓰는 묶음 계산 */
final class Samples {

    private Samples() {
    }

    /** 날짜별 실행 집계 (평가 대상 Task + 이월 놓침) */
    static Map<LocalDate, RateCounter> byDate(PatternSampleSet set) {
        Map<LocalDate, RateCounter> map = new TreeMap<>();
        for (PatternTaskSample t : set.tasks()) {
            map.computeIfAbsent(t.date(), k -> new RateCounter()).add(t.completed());
        }
        for (CarryMissSample m : set.carryMisses()) {
            if (set.range().contains(m.date())) {
                map.computeIfAbsent(m.date(), k -> new RateCounter()).add(false);
            }
        }
        return map;
    }

    /** 그 날짜가 속한 주의 월요일 */
    static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 주(월~일)별 집계. key = 월요일. 기간 경계에 걸친 주는 기간 안의 날짜만 */
    static Map<LocalDate, RateCounter> byWeek(PatternSampleSet set) {
        Map<LocalDate, RateCounter> weeks = new TreeMap<>();
        for (LocalDate w = weekStart(set.range().start()); !w.isAfter(set.range().end()); w = w.plusWeeks(1)) {
            weeks.put(w, new RateCounter());
        }
        byDate(set).forEach((date, c) -> weeks.computeIfAbsent(weekStart(date), k -> new RateCounter()).merge(c));
        return weeks;
    }

    static int round(double v) {
        return (int) Math.round(v);
    }
}
