package com.naleul.naleul.domain.planner.engine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 시간대(0~23시)별 효율 = 계획한 Task 를 완료한 비율 (명세 6-3 ②).
 * 평일/주말 표본이 충분하면 따로, 아니면 같은 값을 쓴다.
 *
 * @param coldStart 기록이 부족해 기본 곡선을 쓰는 중인지 (이유 문구 DEFAULT_CURVE)
 */
public record HourlyEfficiency(double[] weekday, double[] weekend, boolean coldStart, int sampleCount) {

    /** 기록이 부족할 때 쓰는 기본 곡선: 오전 집중, 오후 보통, 저녁 약간 높음, 밤 낮음 */
    public static HourlyEfficiency defaultCurve(int sampleCount) {
        double[] curve = new double[24];
        for (int h = 0; h < 24; h++) {
            if (h >= 9 && h < 12) curve[h] = 0.75;
            else if (h >= 14 && h < 17) curve[h] = 0.60;
            else if (h >= 19 && h < 21) curve[h] = 0.65;
            else if (h >= 21 && h < 23) curve[h] = 0.50;
            else curve[h] = 0.55;
        }
        return new HourlyEfficiency(curve, curve.clone(), true, sampleCount);
    }

    public double at(LocalDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        double[] arr = (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) ? weekend : weekday;
        return arr[t.getHour()];
    }

    /** [start, end) 를 10분 단위로 훑어 평균 효율 */
    public double average(LocalDateTime start, LocalDateTime end) {
        double sum = 0;
        int n = 0;
        for (LocalDateTime t = start; t.isBefore(end); t = t.plusMinutes(10)) {
            sum += at(t);
            n++;
        }
        return n == 0 ? at(start) : sum / n;
    }

    @Override
    public String toString() {
        return "HourlyEfficiency{coldStart=" + coldStart + ", samples=" + sampleCount
                + ", weekday=" + Arrays.toString(weekday) + "}";
    }
}
