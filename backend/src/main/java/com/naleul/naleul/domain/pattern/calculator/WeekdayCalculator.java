package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.DayGroup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/** 요일별 실행률 (명세 4 · 2-3) — 시간 없는 Task 도 포함, 요일 = 평가일의 요일 */
public final class WeekdayCalculator {

    static final int MIN_DAY_SAMPLES = 5;
    static final int LOWEST_GAP = 5;

    private WeekdayCalculator() {
    }

    public static WeekdayStats calculate(PatternSampleSet set) {
        Map<DayOfWeek, RateCounter> byDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) byDay.put(d, new RateCounter());
        RateCounter all = new RateCounter(), weekday = new RateCounter(), weekend = new RateCounter();

        Map<LocalDate, RateCounter> byDate = Samples.byDate(set);
        byDate.forEach((date, c) -> {
            byDay.get(date.getDayOfWeek()).merge(c);
            all.merge(c);
            (DayGroup.isWeekend(date.getDayOfWeek()) ? weekend : weekday).merge(c);
        });

        List<WeekdayStats.Day> days = new ArrayList<>();
        DayOfWeek lowest = null;
        double lowestRatio = 2;
        for (DayOfWeek d : DayOfWeek.values()) {
            RateCounter c = byDay.get(d);
            days.add(new WeekdayStats.Day(d, c.percent(), c.total()));
            if (c.total() >= MIN_DAY_SAMPLES && c.ratio() < lowestRatio) {
                lowest = d;
                lowestRatio = c.ratio();
            }
        }
        Integer avg = all.percent();
        if (lowest != null && (avg == null || byDay.get(lowest).percent() > avg - LOWEST_GAP)) {
            lowest = null;
        }
        String missed = lowest != null ? mostMissedName(set, lowest) : null;
        return new WeekdayStats(avg, days, lowest, missed, weekday.percent(), weekend.percent());
    }

    /** 그 요일에 놓친(TODO) 횟수가 가장 많은 루틴 이름, 루틴이 없으면 목표 이름 */
    static String mostMissedName(PatternSampleSet set, DayOfWeek day) {
        Map<String, Integer> routines = new LinkedHashMap<>();
        Map<String, Integer> goals = new LinkedHashMap<>();
        for (PatternTaskSample t : set.tasks()) {
            if (t.completed() || t.dayOfWeek() != day) continue;
            if (t.routineName() != null) routines.merge(t.routineName(), 1, Integer::sum);
            else if (t.goalName() != null) goals.merge(t.goalName(), 1, Integer::sum);
        }
        Map<String, Integer> pick = !routines.isEmpty() ? routines : goals;
        return pick.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
