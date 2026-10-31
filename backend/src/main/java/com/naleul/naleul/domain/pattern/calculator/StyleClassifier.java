package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.PartOfDayRates;
import com.naleul.naleul.domain.pattern.enums.StyleRhythm;
import com.naleul.naleul.domain.pattern.enums.StyleTimeStrength;

import java.util.List;
import java.util.Map;

/** 실행 스타일 판정 — 규칙 기반 (명세 4 · 3-1) */
public final class StyleClassifier {

    static final int MIN_EVALUATED = 20;
    static final int MIN_ACTIVE_DAYS = 7;
    static final int PART_MIN_SAMPLES = 8;
    static final int STRENGTH_GAP = 8;
    static final int RHYTHM_GAP = 15;
    static final int GROUP_MIN_SAMPLES = 10;
    static final double STEADY_STD_DEV = 8.0;

    private StyleClassifier() {
    }

    public static StyleResult classify(PatternSampleSet set, PatternStats stats) {
        int evaluated = set.tasks().size();
        long activeDays = Samples.byDate(set).values().stream().filter(c -> c.done() > 0).count();
        StyleResult.Progress progress = new StyleResult.Progress(evaluated, (int) activeDays, MIN_EVALUATED, MIN_ACTIVE_DAYS);
        if (evaluated < MIN_EVALUATED || activeDays < MIN_ACTIVE_DAYS) {
            // Task 는 채웠는데 날짜가 모자라도 1 이상 (예전 앱은 이 값만 봄)
            int remaining = Math.max(MIN_EVALUATED - evaluated, 1);
            return new StyleResult(null, null, null, null, List.of(), remaining, progress);
        }
        StyleTimeStrength strength = timeStrength(stats.heatmap() != null ? stats.heatmap().partOfDay() : null);
        StyleRhythm rhythm = rhythm(stats.weekday(), stats.weekly());
        String title = strength.modifier() + " " + rhythm.title();
        List<StyleResult.Insight> insights = InsightComposer.compose(set, stats);
        return new StyleResult(strength, rhythm, title, strength.modifier(), insights, null, progress);
    }

    /** 아침/낮/저녁 중 2위보다 8%p 이상 높은 구간이 있으면 그 구간 */
    static StyleTimeStrength timeStrength(PartOfDayRates p) {
        if (p == null) return StyleTimeStrength.EVEN;
        Map<StyleTimeStrength, Integer> rates = new java.util.EnumMap<>(StyleTimeStrength.class);
        if (p.morning() != null && p.morningSamples() >= PART_MIN_SAMPLES) rates.put(StyleTimeStrength.MORNING, p.morning());
        if (p.daytime() != null && p.daytimeSamples() >= PART_MIN_SAMPLES) rates.put(StyleTimeStrength.DAYTIME, p.daytime());
        if (p.evening() != null && p.eveningSamples() >= PART_MIN_SAMPLES) rates.put(StyleTimeStrength.EVENING, p.evening());
        if (rates.size() < 2) return StyleTimeStrength.EVEN;
        List<Map.Entry<StyleTimeStrength, Integer>> sorted = rates.entrySet().stream()
                .sorted(Map.Entry.<StyleTimeStrength, Integer>comparingByValue().reversed()).toList();
        if (sorted.get(0).getValue() - sorted.get(1).getValue() >= STRENGTH_GAP) return sorted.get(0).getKey();
        return StyleTimeStrength.EVEN;
    }

    static StyleRhythm rhythm(WeekdayStats weekday, WeeklyTrendStats weekly) {
        Integer gap = weekdayWeekendGap(weekday);
        if (gap != null && gap >= RHYTHM_GAP) return StyleRhythm.WEEKDAY_FOCUSED;
        if (gap != null && gap <= -RHYTHM_GAP) return StyleRhythm.WEEKEND_RECHARGE;
        Double sd = weekly != null ? weekly.rateStdDev() : null;
        if (sd == null || sd <= STEADY_STD_DEV) return StyleRhythm.STEADY;
        return StyleRhythm.BURST;
    }

    /** 평일 − 주말 실행률 (각 표본 10개 이상일 때만) */
    static Integer weekdayWeekendGap(WeekdayStats w) {
        if (w == null || w.weekdayRate() == null || w.weekendRate() == null) return null;
        int weekdaySamples = 0, weekendSamples = 0;
        for (WeekdayStats.Day d : w.days()) {
            if (com.naleul.naleul.domain.pattern.enums.DayGroup.isWeekend(d.dayOfWeek())) weekendSamples += d.samples();
            else weekdaySamples += d.samples();
        }
        if (weekdaySamples < GROUP_MIN_SAMPLES || weekendSamples < GROUP_MIN_SAMPLES) return null;
        return w.weekdayRate() - w.weekendRate();
    }
}
