package com.naleul.naleul.domain.planner.engine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 최근 28일 기록 → 시간대별 효율 (명세 6-3 ②).
 *
 *  rate_h = (완료_h + K × 기준값) / (계획_h + K),  K = 3
 *  기준값 = 사용자 전체 완료율 (표본이 없으면 0.6)
 *
 * 왜 보정하나: 21시에 Task 를 딱 1번 계획해서 완료했다면 그냥 계산하면 100% 가 되어
 *            모든 중요한 Task 가 21시로 몰린다. K 개의 "평균적인 가짜 기록"을 섞어 튀는 값을 누른다.
 */
public final class HourlyEfficiencyCalculator {

    public static final int COLD_START_MIN_SAMPLES = 14;
    static final int SPLIT_MIN_SAMPLES = 20;
    static final double K = 3.0;
    static final double DEFAULT_PRIOR = 0.6;

    private HourlyEfficiencyCalculator() {
    }

    /** @param plannedStartAt 계획 시작 시각, completed 완료 여부 */
    public record Sample(LocalDateTime plannedStartAt, boolean completed) {
    }

    public static HourlyEfficiency calculate(List<Sample> samples) {
        if (samples.size() < COLD_START_MIN_SAMPLES) {
            return HourlyEfficiency.defaultCurve(samples.size());
        }
        long done = samples.stream().filter(Sample::completed).count();
        double prior = (double) done / samples.size();

        List<Sample> weekday = samples.stream().filter(s -> !isWeekend(s.plannedStartAt().getDayOfWeek())).toList();
        List<Sample> weekend = samples.stream().filter(s -> isWeekend(s.plannedStartAt().getDayOfWeek())).toList();

        double[] all = rates(samples, prior);
        boolean split = weekday.size() >= SPLIT_MIN_SAMPLES && weekend.size() >= SPLIT_MIN_SAMPLES;
        double[] wd = split ? rates(weekday, prior) : all;
        double[] we = split ? rates(weekend, prior) : all;
        return new HourlyEfficiency(wd, we, false, samples.size());
    }

    private static double[] rates(List<Sample> samples, double prior) {
        int[] total = new int[24];
        int[] done = new int[24];
        for (Sample s : samples) {
            int h = s.plannedStartAt().getHour();
            total[h]++;
            if (s.completed()) done[h]++;
        }
        double[] r = new double[24];
        for (int h = 0; h < 24; h++) {
            r[h] = (done[h] + K * prior) / (total[h] + K);
        }
        return r;
    }

    private static boolean isWeekend(DayOfWeek d) {
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    static double defaultPrior() {
        return DEFAULT_PRIOR;
    }
}
