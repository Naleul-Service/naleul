package com.naleul.naleul.domain.pattern.calculator;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/** 모든 카드 계산 실행. 카드 하나가 예외를 던져도 나머지는 계산한다 */
@Slf4j
public final class PatternCalculator {

    private PatternCalculator() {
    }

    public static PatternStats calculate(PatternSampleSet set) {
        return new PatternStats(
                set.range(),
                set.tasks().size(),
                set.timeBlockCount(),
                safe("summary", set, SummaryCalculator::calculate),
                safe("heatmap", set, HeatmapCalculator::calculate),
                safe("weekday", set, WeekdayCalculator::calculate),
                safe("weekly", set, WeeklyTrendCalculator::calculate),
                safe("timeUsage", set, TimeUsageCalculator::calculate),
                safe("blockKeeping", set, BlockKeepingCalculator::calculate),
                safe("routines", set, RoutineRankCalculator::calculate),
                safe("taskHabits", set, TaskHabitCalculator::calculate)
        );
    }

    private static <T> T safe(String card, PatternSampleSet set, Function<PatternSampleSet, T> f) {
        try {
            return f.apply(set);
        } catch (RuntimeException e) {
            log.error("[Pattern] 카드 계산 실패 card={} range={}", card, set.range(), e);
            return null;
        }
    }
}
