package com.naleul.naleul.domain.pattern.calculator;

/**
 * 카드별 계산 결과 묶음. 한 카드 계산이 실패하면 그 카드만 null (명세 4 · 10장).
 * 실행 스타일·제안·AI 문장은 BE-4 에서 이 결과를 재료로 만든다.
 */
public record PatternStats(
        PatternRange range,
        int evaluatedCount,
        int timeBlockCount,
        SummaryStats summary,
        HeatmapStats heatmap,
        WeekdayStats weekday,
        WeeklyTrendStats weekly,
        TimeUsageStats timeUsage,
        BlockKeepingStats blockKeeping,
        RoutineRankStats routines,
        TaskHabitStats taskHabits
) {
}
