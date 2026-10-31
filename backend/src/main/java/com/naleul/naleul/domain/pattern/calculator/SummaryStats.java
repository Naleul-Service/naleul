package com.naleul.naleul.domain.pattern.calculator;

import java.util.List;

/**
 * 요약 4칸 (명세 4 · 2-1). 표본이 부족한 값은 null 이고 insufficient 에 필드 이름이 들어간다.
 */
public record SummaryStats(
        Integer executionRate,
        Integer firstWeekDelta,
        int bestStreak,
        int currentStreak,
        Integer avgStartDelayMinutes,
        Integer morningDelayMinutes,
        Integer eveningDelayMinutes,
        Integer aiPlacementEditRate,
        Integer aiPlacementKeptPerTen,
        int engineBlockCount,
        List<String> insufficient
) {
}
