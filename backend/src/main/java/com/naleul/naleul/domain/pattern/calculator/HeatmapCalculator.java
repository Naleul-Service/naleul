package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.lifePattern.enums.LifePatternType;
import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.*;
import com.naleul.naleul.domain.pattern.enums.DayGroup;
import com.naleul.naleul.domain.pattern.enums.HeatCellState;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 요일 × 시간 히트맵 + 골든 타임 / 취약 시간 (명세 4 · 2-2).
 *
 * 칸 = 그 요일 · 시간에 실제로 한 Task · 루틴 수 (실제 시작 시각, 기록이 없으면 계획 시각). 많이 할수록 진하게.
 * 골든 타임 / 취약 시간 · 아침 · 낮 · 저녁 실행률 = 평가 대상 TimeBlock + 시간이 있던 이월 놓침의 계획 대비 실행률
 * (시간대 = 계획 시작 시각의 시).
 * 배치 엔진(HourlyEfficiencyCalculator)은 배치용이라 표본이 적은 시간대를 보정하지만,
 * 이 화면은 사람이 읽는 값이라 보정 없는 실제 비율을 보여준다.
 */
public final class HeatmapCalculator {

    public static final int FIRST_HOUR = 6;
    public static final int LAST_HOUR = 23;
    static final int MIN_CELL_SAMPLES = 3;
    static final int FIXED_MIN_MINUTES = 30;
    static final int SPLIT_MIN_SAMPLES = 20;
    static final int GOLDEN_MIN_SAMPLES = 10;
    static final int WEAK_MIN_SAMPLES = 5;
    static final int WEAK_GAP = 10;

    private HeatmapCalculator() {
    }

    public static HeatmapStats calculate(PatternSampleSet set) {
        // [요일][시] 집계
        Map<DayOfWeek, RateCounter[]> grid = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            RateCounter[] hours = new RateCounter[24];
            for (int h = 0; h < 24; h++) hours[h] = new RateCounter();
            grid.put(d, hours);
        }
        for (PatternTaskSample t : set.tasks()) {
            if (!t.timed()) continue;
            grid.get(t.plannedStart().getDayOfWeek())[t.startHour()].add(t.completed());
        }
        for (CarryMissSample m : set.carryMisses()) {
            if (!m.timed() || !set.range().contains(m.date())) continue;
            grid.get(m.startAt().getDayOfWeek())[m.startAt().getHour()].add(false);
        }
        // 그 시간에 실제로 한 수: 실제 시작 시각(없으면 계획 시각)에 +1
        Map<DayOfWeek, int[]> done = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) done.put(d, new int[24]);
        for (PatternTaskSample t : set.tasks()) {
            if (!t.completed()) continue;
            LocalDateTime at = t.actualStart() != null ? t.actualStart() : t.plannedStart();
            if (at == null) continue;
            done.get(at.getDayOfWeek())[at.getHour()]++;
        }
        int maxCount = 0;
        for (int[] hs : done.values()) {
            for (int h = FIRST_HOUR; h <= LAST_HOUR; h++) maxCount = Math.max(maxCount, hs[h]);
        }

        // 칸
        List<LifePatternSample> custom = set.lifePatterns().stream()
                .filter(p -> p.type() == LifePatternType.CUSTOM).toList();
        Map<String, Integer> fixedCount = new LinkedHashMap<>();
        List<Integer> hours = new ArrayList<>();
        for (int h = FIRST_HOUR; h <= LAST_HOUR; h++) hours.add(h);
        List<Row> rows = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            List<Cell> cells = new ArrayList<>();
            for (int h = FIRST_HOUR; h <= LAST_HOUR; h++) {
                RateCounter c = grid.get(d)[h];
                Integer rate = c.total() >= MIN_CELL_SAMPLES ? c.percent() : null;
                Integer samples = c.total() >= MIN_CELL_SAMPLES ? c.total() : null;
                int count = done.get(d)[h];
                if (count > 0) {
                    cells.add(new Cell(h, HeatCellState.VALUE, rate, samples, countLevel(count, maxCount), count));
                    continue;
                }
                LifePatternSample cover = coveringPattern(custom, d, h);
                if (cover != null) {
                    fixedCount.merge(cover.title(), 1, Integer::sum);
                    cells.add(new Cell(h, HeatCellState.FIXED, null, null, null, 0));
                } else {
                    cells.add(new Cell(h, HeatCellState.EMPTY, rate, samples, null, 0));
                }
            }
            rows.add(new Row(d, cells));
        }
        String fixedLabel = fixedCount.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        // 골든 타임 / 취약 시간
        List<DayGroup> groups = dayGroups(grid);
        TimeWindow golden = bestWindow(grid, groups, 2, 4, GOLDEN_MIN_SAMPLES, true);
        TimeWindow weak = bestWindow(grid, groups, 1, 2, WEAK_MIN_SAMPLES, false);
        Integer overall = overall(grid).percent();
        if (weak != null && (overall == null || weak.rate() > overall - WEAK_GAP)) weak = null;
        if (golden != null && weak != null && golden.rate() <= weak.rate()) weak = null;

        return new HeatmapStats(hours, rows, maxCount, fixedLabel, golden, weak, partOfDay(grid));
    }

    /** 한 수 → 색 단계 1~5 (가장 많이 한 칸 = 5, 그 비율로 나눠요. 1번이라도 했으면 최소 1) */
    static int countLevel(int count, int max) {
        if (count <= 0 || max <= 0) return 0;
        return Math.max(1, Math.min(5, (int) Math.ceil(5.0 * count / max)));
    }

    /** 실행률 → 색 단계 1~5 (≤60 / ≤70 / ≤80 / ≤90 / >90) */
    static int level(int rate) {
        if (rate <= 60) return 1;
        if (rate <= 70) return 2;
        if (rate <= 80) return 3;
        if (rate <= 90) return 4;
        return 5;
    }

    private static LifePatternSample coveringPattern(List<LifePatternSample> patterns, DayOfWeek d, int h) {
        LifePatternSample best = null;
        int bestMinutes = FIXED_MIN_MINUTES - 1;
        for (LifePatternSample p : patterns) {
            int m = p.minutesCovering(d, h);
            if (m > bestMinutes) {
                best = p;
                bestMinutes = m;
            }
        }
        return best;
    }

    /** 평일·주말 표본이 각각 20개 이상이면 따로, 아니면 전체 */
    static List<DayGroup> dayGroups(Map<DayOfWeek, RateCounter[]> grid) {
        int weekday = sum(grid, DayGroup.WEEKDAY, 0, 24).total();
        int weekend = sum(grid, DayGroup.WEEKEND, 0, 24).total();
        if (weekday >= SPLIT_MIN_SAMPLES && weekend >= SPLIT_MIN_SAMPLES) {
            return List.of(DayGroup.WEEKDAY, DayGroup.WEEKEND);
        }
        return List.of(DayGroup.ALL);
    }

    /**
     * 길이 minLen~maxLen 시간 구간 중 실행률이 가장 높은(highest=true) / 낮은 구간.
     * 같은 실행률이면 표본이 많은 쪽, 그다음 이른 시각.
     */
    static TimeWindow bestWindow(Map<DayOfWeek, RateCounter[]> grid, List<DayGroup> groups,
                                 int minLen, int maxLen, int minSamples, boolean highest) {
        TimeWindow best = null;
        double bestRatio = 0;
        for (DayGroup g : groups) {
            for (int len = minLen; len <= maxLen; len++) {
                for (int s = 0; s + len <= 24; s++) {
                    RateCounter c = sum(grid, g, s, s + len);
                    if (c.total() < minSamples) continue;
                    double r = c.ratio();
                    boolean better = best == null
                            || (highest ? r > bestRatio : r < bestRatio)
                            || (r == bestRatio && c.total() > best.samples());
                    if (better) {
                        best = new TimeWindow(g, s, s + len, c.percent(), c.total());
                        bestRatio = r;
                    }
                }
            }
        }
        return best;
    }

    private static RateCounter sum(Map<DayOfWeek, RateCounter[]> grid, DayGroup g, int fromHour, int toHour) {
        RateCounter c = new RateCounter();
        grid.forEach((d, hours) -> {
            if (!g.contains(d)) return;
            for (int h = fromHour; h < toHour; h++) c.merge(hours[h]);
        });
        return c;
    }

    private static RateCounter overall(Map<DayOfWeek, RateCounter[]> grid) {
        return sum(grid, DayGroup.ALL, 0, 24);
    }

    static PartOfDayRates partOfDay(Map<DayOfWeek, RateCounter[]> grid) {
        RateCounter m = sum(grid, DayGroup.ALL, 5, 12);
        RateCounter d = sum(grid, DayGroup.ALL, 12, 18);
        RateCounter e = sum(grid, DayGroup.ALL, 18, 24);
        return new PartOfDayRates(m.percent(), m.total(), d.percent(), d.total(), e.percent(), e.total());
    }
}
