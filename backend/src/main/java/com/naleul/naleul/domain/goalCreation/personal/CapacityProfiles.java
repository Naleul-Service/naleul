package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.pattern.calculator.HeatmapStats;
import com.naleul.naleul.domain.pattern.calculator.PatternSampleSet;
import com.naleul.naleul.domain.pattern.calculator.PatternStats;
import com.naleul.naleul.domain.pattern.calculator.PatternTaskSample;
import com.naleul.naleul.domain.pattern.calculator.WeekdayStats;

import java.time.temporal.ChronoUnit;
import java.util.List;

/** 나의 패턴 재료·계산 결과 → CapacityProfile (DB 없이 순수 계산) */
public final class CapacityProfiles {

    private CapacityProfiles() {
    }

    /**
     * @param routineLoads 지금 진행 중인 루틴들의 주간 분 (루틴마다 하나)
     */
    public static CapacityProfile from(PatternSampleSet set, PatternStats stats, List<Integer> routineLoads) {
        int days = (int) Math.max(7, ChronoUnit.DAYS.between(set.range().start(), set.range().end()) + 1);
        int doneMinutes = set.tasks().stream().mapToInt(PatternTaskSample::spentMinutes).sum();
        int weeklyDone = (int) Math.round(doneMinutes * 7.0 / days);

        HeatmapStats heat = stats != null ? stats.heatmap() : null;
        WeekdayStats weekday = stats != null ? stats.weekday() : null;
        Integer weakDayRate = null;
        if (weekday != null && weekday.lowest() != null) {
            weakDayRate = weekday.days().stream()
                    .filter(d -> d.dayOfWeek() == weekday.lowest())
                    .map(WeekdayStats.Day::rate)
                    .findFirst().orElse(null);
        }

        return new CapacityProfile(
                set.tasks().size(),
                stats != null && stats.summary() != null ? stats.summary().executionRate() : null,
                weeklyDone,
                routineLoads.size(),
                routineLoads.stream().mapToInt(Integer::intValue).sum(),
                heat != null ? heat.goldenTime() : null,
                heat != null ? heat.weakTime() : null,
                weekday != null ? weekday.lowest() : null,
                weakDayRate,
                weekday != null ? weekday.average() : null);
    }
}
