package com.naleul.naleul.domain.pattern.calculator;

import java.time.LocalDate;
import java.util.*;

/** 주차별 흐름 (명세 4 · 2-4) — 월~일 단위, 1주 = 기간 시작 주 */
public final class WeeklyTrendCalculator {

    static final int MAX_POINTS = 26;
    static final int MIN_WEEK_SAMPLES = 5;

    private WeeklyTrendCalculator() {
    }

    public static WeeklyTrendStats calculate(PatternSampleSet set) {
        List<Map.Entry<LocalDate, RateCounter>> weeks = new ArrayList<>(Samples.byWeek(set).entrySet());
        if (weeks.size() > MAX_POINTS) weeks = weeks.subList(weeks.size() - MAX_POINTS, weeks.size());

        List<WeeklyTrendStats.Point> points = new ArrayList<>();
        List<Integer> rates = new ArrayList<>();
        Integer maxIndex = null;
        int maxRate = -1;
        int index = 1;
        for (Map.Entry<LocalDate, RateCounter> e : weeks) {
            RateCounter c = e.getValue();
            Integer rate = c.total() >= MIN_WEEK_SAMPLES ? c.percent() : null;
            points.add(new WeeklyTrendStats.Point(index, e.getKey(), rate, c.total()));
            if (rate != null) {
                rates.add(rate);
                if (rate > maxRate) {
                    maxRate = rate;
                    maxIndex = index;
                }
            }
            index++;
        }
        return new WeeklyTrendStats(points, maxIndex, stdDev(rates));
    }

    /** 표본 주가 2개 미만이면 null */
    static Double stdDev(List<Integer> values) {
        if (values.size() < 2) return null;
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        double var = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / values.size();
        return Math.round(Math.sqrt(var) * 10) / 10.0;
    }
}
