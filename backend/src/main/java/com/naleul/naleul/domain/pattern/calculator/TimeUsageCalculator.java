package com.naleul.naleul.domain.pattern.calculator;

import java.util.*;

/**
 * 목표별 시간 사용 (명세 4 · 2-5).
 * 실제 = 완료 Task 의 실제 소요 시간(없으면 계획 시간), 계획 = 평가 대상 Task 의 계획 소요 시간.
 */
public final class TimeUsageCalculator {

    static final int MAX_GOALS = 6;

    private TimeUsageCalculator() {
    }

    public static TimeUsageStats calculate(PatternSampleSet set) {
        Map<Long, long[]> minutes = new LinkedHashMap<>(); // goalId → [실제, 계획]
        Map<Long, PatternTaskSample> info = new HashMap<>();
        long totalSpent = 0;
        for (PatternTaskSample t : set.tasks()) {
            totalSpent += t.spentMinutes();
            if (t.goalId() == null) continue;
            long[] m = minutes.computeIfAbsent(t.goalId(), k -> new long[2]);
            m[0] += t.spentMinutes();
            m[1] += t.plannedMinutes();
            info.putIfAbsent(t.goalId(), t);
        }
        double weeks = set.range().weeks();
        List<TimeUsageStats.Goal> goals = new ArrayList<>();
        minutes.forEach((goalId, m) -> {
            PatternTaskSample g = info.get(goalId);
            Integer ratio = m[1] > 0 ? (int) Math.round(100.0 * m[0] / m[1]) : null;
            boolean newTemp = g.goalTemporary() && set.range().contains(g.goalStartDate());
            goals.add(new TimeUsageStats.Goal(goalId, g.goalName(), g.goalEmoji(), g.goalColorCode(),
                    hours(m[0], weeks), hours(m[1], weeks), ratio, g.goalTemporary(), newTemp));
        });
        goals.sort(Comparator.comparingDouble(TimeUsageStats.Goal::actualHoursPerWeek).reversed()
                .thenComparing(TimeUsageStats.Goal::goalCategoryId));
        return new TimeUsageStats(hours(totalSpent, weeks),
                goals.size() > MAX_GOALS ? List.copyOf(goals.subList(0, MAX_GOALS)) : goals);
    }

    /** 분 → 주 평균 시간 (소수 1자리) */
    static double hours(long minutes, double weeks) {
        return Math.round(minutes / 60.0 / weeks * 10) / 10.0;
    }
}
