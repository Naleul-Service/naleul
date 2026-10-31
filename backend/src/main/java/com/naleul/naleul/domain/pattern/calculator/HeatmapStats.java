package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.enums.DayGroup;
import com.naleul.naleul.domain.pattern.enums.HeatCellState;

import java.time.DayOfWeek;
import java.util.List;

/**
 * 요일 × 시간 히트맵 (명세 4 · 2-2).
 * 칸 = 그 요일 · 시간에 실제로 한 Task · 루틴 수 (계획대로 했는지가 아니라 "그때 했다"면 +1).
 * maxCount = 가장 많이 한 칸의 수 (색 단계 기준)
 */
public record HeatmapStats(
        List<Integer> hours,
        List<Row> rows,
        int maxCount,
        String fixedLabel,
        TimeWindow goldenTime,
        TimeWindow weakTime,
        /** 아침(05~12) / 낮(12~18) / 저녁(18~24) 실행률 — 실행 스타일 판정용 */
        PartOfDayRates partOfDay
) {
    public record Row(DayOfWeek dayOfWeek, List<Cell> cells) {
    }

    /**
     * VALUE = 그 시간에 한 게 있어요 (count ≥ 1, level 1~5 = 가장 많이 한 칸 대비 진하기).
     * rate · samples = 그 칸의 계획 대비 실행률 (계획한 블록이 3개 이상일 때만, 참고용)
     */
    public record Cell(int hour, HeatCellState state, Integer rate, Integer samples, Integer level, int count) {
    }

    /** endHour 는 포함하지 않음 (19~22 = 19, 20, 21시) */
    public record TimeWindow(DayGroup dayGroup, int startHour, int endHour, int rate, int samples) {
    }

    /** 각 구간 [rate, samples]. 표본이 없으면 rate null */
    public record PartOfDayRates(Integer morning, int morningSamples,
                                 Integer daytime, int daytimeSamples,
                                 Integer evening, int eveningSamples) {
    }
}
