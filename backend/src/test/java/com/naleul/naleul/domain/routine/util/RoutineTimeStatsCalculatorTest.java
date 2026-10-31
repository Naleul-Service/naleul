package com.naleul.naleul.domain.routine.util;

import com.naleul.naleul.domain.routine.util.RoutineTimeStatsCalculator.Practice;
import com.naleul.naleul.domain.routine.util.RoutineTimeStatsCalculator.TimeStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutineTimeStatsCalculatorTest {

    private static LocalDateTime at(int day, int h, int m) {
        return LocalDateTime.of(2026, 10, day, h, m);
    }

    @Test
    void 가장_많이_한_시간대를_보통_길이만큼_고른다() {
        List<Practice> ps = List.of(
                new Practice(1, 19 * 60, 20 * 60),
                new Practice(2, 19 * 60 + 30, 20 * 60 + 30),
                new Practice(3, 19 * 60, 20 * 60),
                new Practice(4, 7 * 60, 8 * 60));

        TimeStats s = RoutineTimeStatsCalculator.calculate(ps);

        assertThat(s.bestStart()).isEqualTo("19:00");
        assertThat(s.bestEnd()).isEqualTo("20:00");
        assertThat(s.practiceCount()).isEqualTo(4);
        assertThat(s.inWindowCount()).isEqualTo(3);
        assertThat(s.hourly().get(19)).isEqualTo(3);
        assertThat(s.hourly().get(7)).isEqualTo(1);
    }

    @Test
    void 기록이_없으면_null() {
        assertThat(RoutineTimeStatsCalculator.calculate(List.of())).isNull();
    }

    @Test
    void 실제_시각이_있으면_계획보다_먼저() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{10L, 1L, at(20, 7, 0), at(20, 8, 0), at(20, 22, 0), at(20, 21, 0), at(20, 22, 0)});
        rows.add(new Object[]{10L, 2L, at(21, 7, 0), at(21, 8, 0), null, null, null});

        Map<Long, List<Practice>> map = RoutineTimeStatsCalculator.practicesByRoutine(rows);

        assertThat(map.get(10L)).extracting(Practice::startMinute).containsExactly(21 * 60, 7 * 60);
    }

    @Test
    void 시각_없이_완료만_누르면_완료_시각을_끝으로_본다() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{10L, 1L, null, null, at(20, 22, 0), null, null});

        Practice p = RoutineTimeStatsCalculator.practicesByRoutine(rows).get(10L).get(0);

        assertThat(p.startMinute()).isEqualTo(21 * 60 + 30);
        assertThat(p.endMinute()).isEqualTo(22 * 60);
    }

    @Test
    void 요일별로_가장_많이_한_시간대를_많이_한_요일_순으로() {
        var tue = java.time.DayOfWeek.TUESDAY;
        var thu = java.time.DayOfWeek.THURSDAY;
        var sat = java.time.DayOfWeek.SATURDAY;
        var stats = RoutineTimeStatsCalculator.calculate(List.of(
                new Practice(1, 19 * 60, 19 * 60 + 30, tue),
                new Practice(2, 19 * 60, 19 * 60 + 30, tue),
                new Practice(3, 19 * 60, 19 * 60 + 30, tue),
                new Practice(4, 17 * 60, 17 * 60 + 30, thu),
                new Practice(5, 17 * 60, 17 * 60 + 30, thu),
                new Practice(6, 9 * 60, 9 * 60 + 30, sat)));
        assertThat(stats.byDay()).extracting(RoutineTimeStatsCalculator.DayStat::day).containsExactly("TUESDAY", "THURSDAY");
        assertThat(stats.byDay().get(0).start()).isEqualTo("19:00");
        assertThat(stats.byDay().get(0).end()).isEqualTo("19:30");
        assertThat(stats.byDay().get(1).count()).isEqualTo(2);
    }
}
