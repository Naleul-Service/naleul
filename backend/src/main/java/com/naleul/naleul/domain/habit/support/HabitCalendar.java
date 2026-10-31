package com.naleul.naleul.domain.habit.support;

import com.naleul.naleul.domain.habit.enums.HabitDayState;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 습관 하나의 날짜별 상태 · 연속 기록 · 실천률 (DB 없이 순수 계산, V52).
 *
 * 규칙
 *  - 해야 하는 날 = 시작일 이후 · 반복 요일 · 쉬는 기간이 아닌 날
 *  - 체크한 날은 반복 요일이 아니어도 DONE (주 3회 습관을 하루 더 한 날)
 *  - 오늘은 아직 안 했어도 MISSED 가 아니라 TODO (하루가 끝나지 않았으니까)
 *  - 연속 기록: 오늘(했으면) 또는 어제부터 거꾸로, 해야 하는 날을 연달아 한 횟수. OFF · 쉬는 날은 건너뛴다
 */
public final class HabitCalendar {

    /** 연속 기록을 거꾸로 셀 때 최대 며칠까지 볼지 (체크 기록을 이 기간만큼만 불러오면 된다) */
    public static final int STREAK_LOOKBACK_DAYS = 400;

    private HabitCalendar() {
    }

    public record Period(LocalDate start, LocalDate end) {
        boolean covers(LocalDate d) {
            return !d.isBefore(start) && (end == null || !d.isAfter(end));
        }
    }

    /**
     * @param repeatDays 비어 있으면 매일
     * @param pauses     쉰 기간 (end 가 null 이면 아직 쉬는 중)
     */
    public record HabitView(LocalDate startDate, Set<DayOfWeek> repeatDays, Set<LocalDate> checks,
                            List<Period> pauses) {

        public boolean checked(LocalDate d) {
            return checks.contains(d);
        }

        public boolean paused(LocalDate d) {
            return pauses.stream().anyMatch(p -> p.covers(d));
        }

        public boolean repeatDay(LocalDate d) {
            return repeatDays.isEmpty() || repeatDays.contains(d.getDayOfWeek());
        }

        /** 해야 하는 날 */
        public boolean scheduled(LocalDate d) {
            return !d.isBefore(startDate) && repeatDay(d) && !paused(d);
        }
    }

    public static HabitDayState stateOf(HabitView h, LocalDate date, LocalDate today) {
        if (date.isAfter(today)) return HabitDayState.FUTURE;
        if (h.checked(date)) return HabitDayState.DONE;
        if (date.isBefore(h.startDate())) return HabitDayState.BEFORE_START;
        if (h.paused(date)) return HabitDayState.PAUSED;
        if (!h.repeatDay(date)) return HabitDayState.OFF;
        return date.equals(today) ? HabitDayState.TODO : HabitDayState.MISSED;
    }

    /** 연속으로 지킨 "해야 하는 날" 수. 오늘을 아직 안 했으면 어제까지로 센다 */
    public static int streak(HabitView h, LocalDate today) {
        int count = 0;
        LocalDate d = today;
        LocalDate limit = today.minusDays(STREAK_LOOKBACK_DAYS);
        while (!d.isBefore(h.startDate()) && d.isAfter(limit)) {
            if (h.checked(d)) {
                if (h.scheduled(d)) count++;   // 반복 요일이 아닌 날 더 한 건 연속을 끊지도, 늘리지도 않는다
            } else if (h.scheduled(d) && !d.equals(today)) {
                break;                          // 해야 하는 날을 놓침
            }
            d = d.minusDays(1);
        }
        return count;
    }

    /**
     * [from, today] 동안 해야 하는 날 중 한 날의 비율(%, 반올림). 해야 하는 날이 없으면 null.
     * 오늘은 이미 했을 때만 센다 (아직 안 한 오늘 때문에 실천률이 떨어지지 않게).
     */
    public static Integer rate(HabitView h, LocalDate from, LocalDate today) {
        return rate(h, from, today, today);
    }

    /** [from, to] 기간 실천률. to 가 오늘보다 앞이면 그날은 이미 끝난 날이라 안 했으면 놓친 날로 센다 */
    public static Integer rate(HabitView h, LocalDate from, LocalDate to, LocalDate today) {
        int scheduled = 0;
        int done = 0;
        LocalDate end = to.isAfter(today) ? today : to;
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            if (!h.scheduled(d)) continue;
            boolean checked = h.checked(d);
            if (d.equals(today) && !checked) continue;
            scheduled++;
            if (checked) done++;
        }
        return scheduled == 0 ? null : (int) Math.round(done * 100.0 / scheduled);
    }

    /**
     * 잔디 진하기 0~4 (여러 습관을 합친 하루). 해야 하는 습관이 없으면 한 것이 있을 때만 4, 없으면 0.
     */
    public static int level(int done, int scheduled) {
        if (done <= 0) return 0;
        if (scheduled <= 0) return 4;
        double r = Math.min(1.0, done / (double) scheduled);
        if (r >= 1.0) return 4;
        if (r >= 0.67) return 3;
        if (r >= 0.34) return 2;
        return 1;
    }
}
