package com.naleul.naleul.domain.pattern.calculator;

import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.PartOfDayRates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 실행 스타일 근거 3줄 (명세 4 · 3-2). 후보를 만들고 "평균과의 차이"(score)가 큰 순으로 3개.
 * 문장은 템플릿이고, BE-6 에서 AI 가 숫자를 바꾸지 않고 다듬는다.
 */
public final class InsightComposer {

    static final int MAX = 3;

    private InsightComposer() {
    }

    public static List<StyleResult.Insight> compose(PatternSampleSet set, PatternStats stats) {
        List<StyleResult.Insight> c = new ArrayList<>();
        timeStrength(stats, c);
        weekendRhythm(stats, c);
        startDelay(stats, c);
        weakDay(stats, c);
        streak(stats, c);
        aiAccept(stats, c);
        c.sort(Comparator.comparingDouble(StyleResult.Insight::score).reversed());
        return c.size() > MAX ? List.copyOf(c.subList(0, MAX)) : c;
    }

    /** 가장 높은 구간과 가장 낮은 구간의 차이가 15%p 이상 */
    private static void timeStrength(PatternStats stats, List<StyleResult.Insight> out) {
        if (stats.heatmap() == null) return;
        PartOfDayRates p = stats.heatmap().partOfDay();
        String[][] parts = {
                {"아침 05–12시", "아침", "sunrise"},
                {"낮 12–18시", "낮", "sun"},
                {"저녁 18–24시", "저녁", "moon"}};
        Integer[] rates = {p.morning(), p.daytime(), p.evening()};
        int[] samples = {p.morningSamples(), p.daytimeSamples(), p.eveningSamples()};
        int hi = -1, lo = -1;
        for (int i = 0; i < 3; i++) {
            if (rates[i] == null || samples[i] < StyleClassifier.PART_MIN_SAMPLES) continue;
            if (hi < 0 || rates[i] > rates[hi]) hi = i;
            if (lo < 0 || rates[i] < rates[lo]) lo = i;
        }
        if (hi < 0 || hi == lo || rates[hi] - rates[lo] < 15) return;
        String text = String.format("%s 실행률이 **%d%%**로 %s(%d%%)보다 훨씬 높아요.",
                parts[hi][0], rates[hi], parts[lo][1], rates[lo]);
        out.add(new StyleResult.Insight("TIME_STRENGTH", parts[hi][2], text, rates[hi] - rates[lo]));
    }

    private static void weekendRhythm(PatternStats stats, List<StyleResult.Insight> out) {
        Integer gap = StyleClassifier.weekdayWeekendGap(stats.weekday());
        if (gap == null) return;
        if (Math.abs(gap) <= 10) {
            out.add(new StyleResult.Insight("WEEKEND_RHYTHM", "calendar",
                    "주말에도 리듬이 유지돼요. 토·일 실행률이 평일과 비슷해요.", 12));
        } else if (gap >= 15) {
            out.add(new StyleResult.Insight("WEEKEND_RHYTHM", "calendar",
                    String.format("주말 실행률이 평일보다 **%d%%p** 낮아요.", gap), gap));
        } else if (gap <= -15) {
            out.add(new StyleResult.Insight("WEEKEND_RHYTHM", "calendar",
                    String.format("주말 실행률이 평일보다 **%d%%p** 높아요.", -gap), -gap));
        }
    }

    private static void startDelay(PatternStats stats, List<StyleResult.Insight> out) {
        SummaryStats s = stats.summary();
        if (s == null || s.avgStartDelayMinutes() == null) return;
        int m = s.avgStartDelayMinutes();
        if (m >= 5) {
            StringBuilder text = new StringBuilder(String.format("블록을 평균 **%d분 늦게** 시작해요.", m));
            if (s.morningDelayMinutes() != null && s.eveningDelayMinutes() != null) {
                text.append(String.format(" 아침은 %+d분, 저녁은 %+d분이에요.", s.morningDelayMinutes(), s.eveningDelayMinutes()));
            }
            out.add(new StyleResult.Insight("START_DELAY", "alarm", text.toString(), m));
        } else if (m <= 2) {
            out.add(new StyleResult.Insight("START_DELAY", "alarm", "계획한 시각에 맞춰 시작하는 편이에요.", 8));
        }
    }

    private static void weakDay(PatternStats stats, List<StyleResult.Insight> out) {
        WeekdayStats w = stats.weekday();
        if (w == null || w.lowest() == null || w.average() == null) return;
        Integer r = w.days().get(w.lowest().getValue() - 1).rate();
        if (r == null || w.average() - r < 10) return;
        out.add(new StyleResult.Insight("WEAK_DAY", "chart-down",
                String.format("%s 실행률이 **%d%%**로 가장 낮아요.", TimeLabels.dayLong(w.lowest()), r), w.average() - r));
    }

    private static void streak(PatternStats stats, List<StyleResult.Insight> out) {
        SummaryStats s = stats.summary();
        if (s == null || s.currentStreak() < 7) return;
        out.add(new StyleResult.Insight("STREAK", "fire",
                String.format("**%d일** 연속 실행 중이에요.", s.currentStreak()), s.currentStreak()));
    }

    private static void aiAccept(PatternStats stats, List<StyleResult.Insight> out) {
        SummaryStats s = stats.summary();
        if (s == null || s.aiPlacementEditRate() == null || s.engineBlockCount() < 10) return;
        out.add(new StyleResult.Insight("AI_ACCEPT", "sparkle",
                String.format("AI 배치를 **%d%%** 그대로 사용하고 있어요.", 100 - s.aiPlacementEditRate()), 10));
    }
}
