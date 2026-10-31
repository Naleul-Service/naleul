package com.naleul.naleul.domain.pattern.service;

import com.naleul.naleul.domain.pattern.calculator.RoutineInfo;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PatternConflictCheckerTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 12, 1); // 화
    private static final LocalDate LAST = FIRST.plusDays(13);
    private static final Set<DayOfWeek> WEEKDAYS =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final RoutineInfo rc = new RoutineInfo(31L, "토익 RC", null, LocalTime.of(8, 0), LocalTime.of(8, 30),
            null, WEEKDAYS, true, LAST.plusMonths(1));

    @Test
    void 고정_시간과_겹치는_날의_비율() {
        // 매일 12~13시 점심
        List<DateTimeRange> lunch = FIRST.datesUntil(LAST.plusDays(1))
                .map(d -> new DateTimeRange(d.atTime(12, 0), d.atTime(13, 0))).toList();
        var checker = new PatternConflictCheckerFactory.Checker(FIRST, LAST, lunch, List.of());

        assertThat(checker.conflictRatio(rc, WEEKDAYS, LocalTime.of(12, 40), LocalTime.of(13, 10))).isEqualTo(1.0);
        assertThat(checker.conflictRatio(rc, WEEKDAYS, LocalTime.of(13, 0), LocalTime.of(13, 30))).isEqualTo(0.0);
    }

    @Test
    void 자기_루틴의_블록은_겹침으로_보지_않는다() {
        LocalDate wed = FIRST.plusDays(1);
        var own = new PatternConflictCheckerFactory.TaskBlock(31L, new DateTimeRange(wed.atTime(19, 0), wed.atTime(19, 30)));
        var other = new PatternConflictCheckerFactory.TaskBlock(null, new DateTimeRange(wed.atTime(20, 0), wed.atTime(21, 0)));
        var checker = new PatternConflictCheckerFactory.Checker(FIRST, LAST, List.of(), List.of(own, other));

        assertThat(checker.conflictRatio(rc, EnumSet.of(DayOfWeek.WEDNESDAY), LocalTime.of(19, 0), LocalTime.of(19, 30)))
                .isEqualTo(0.0);
        assertThat(checker.conflictRatio(rc, EnumSet.of(DayOfWeek.WEDNESDAY), LocalTime.of(20, 0), LocalTime.of(20, 30)))
                .isEqualTo(0.5); // 수요일 2번 중 1번
    }
}
