package com.naleul.naleul.domain.habit.support;

import com.naleul.naleul.domain.habit.enums.HabitDayState;
import com.naleul.naleul.domain.habit.support.HabitCalendar.HabitView;
import com.naleul.naleul.domain.habit.support.HabitCalendar.Period;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HabitCalendarTest {

    // 2026-10-13 은 화요일
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private static HabitView daily(LocalDate start, Set<LocalDate> checks, List<Period> pauses) {
        return new HabitView(start, EnumSet.noneOf(DayOfWeek.class), checks, pauses);
    }

    @Test
    void 날짜별_상태() {
        HabitView h = new HabitView(TODAY.minusDays(7),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                Set.of(TODAY.minusDays(1), TODAY.minusDays(3)),   // 월 · 토(반복 요일 아님)
                List.of());

        assertThat(HabitCalendar.stateOf(h, TODAY.plusDays(1), TODAY)).isEqualTo(HabitDayState.FUTURE);
        assertThat(HabitCalendar.stateOf(h, TODAY.minusDays(1), TODAY)).isEqualTo(HabitDayState.DONE);
        assertThat(HabitCalendar.stateOf(h, TODAY.minusDays(3), TODAY)).isEqualTo(HabitDayState.DONE);    // 더 한 날
        assertThat(HabitCalendar.stateOf(h, TODAY, TODAY)).isEqualTo(HabitDayState.OFF);                  // 화요일
        assertThat(HabitCalendar.stateOf(h, TODAY.minusDays(4), TODAY)).isEqualTo(HabitDayState.MISSED);  // 금요일
        assertThat(HabitCalendar.stateOf(h, TODAY.minusDays(8), TODAY)).isEqualTo(HabitDayState.BEFORE_START);
    }

    @Test
    void 오늘_아직_안했으면_TODO_이고_연속기록은_어제까지() {
        HabitView h = daily(TODAY.minusDays(10), Set.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3)), List.of());

        assertThat(HabitCalendar.stateOf(h, TODAY, TODAY)).isEqualTo(HabitDayState.TODO);
        assertThat(HabitCalendar.streak(h, TODAY)).isEqualTo(3);
    }

    @Test
    void 오늘_했으면_연속기록에_오늘도_센다() {
        HabitView h = daily(TODAY.minusDays(10), Set.of(TODAY, TODAY.minusDays(1)), List.of());
        assertThat(HabitCalendar.streak(h, TODAY)).isEqualTo(2);
    }

    @Test
    void 쉬는_기간은_연속기록을_끊지_않고_실천률에서도_빠진다() {
        // 4일 전 체크 → 3·2일 전 쉼 → 어제 체크
        HabitView h = daily(TODAY.minusDays(4),
                Set.of(TODAY.minusDays(4), TODAY.minusDays(1)),
                List.of(new Period(TODAY.minusDays(3), TODAY.minusDays(2))));

        assertThat(HabitCalendar.stateOf(h, TODAY.minusDays(2), TODAY)).isEqualTo(HabitDayState.PAUSED);
        assertThat(HabitCalendar.streak(h, TODAY)).isEqualTo(2);
        assertThat(HabitCalendar.rate(h, TODAY.minusDays(4), TODAY)).isEqualTo(100);   // 오늘(아직 안 함)은 빼고
    }

    @Test
    void 반복_요일이_아닌_날은_연속을_끊지_않는다() {
        // 월·수·금 습관: 금(4일 전) · 월(어제) 체크, 사이의 토·일은 OFF
        HabitView h = new HabitView(TODAY.minusDays(10),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                Set.of(TODAY.minusDays(4), TODAY.minusDays(1)), List.of());
        // 수(6일 전)는 놓침 → 연속은 금 · 월 = 2
        assertThat(HabitCalendar.streak(h, TODAY)).isEqualTo(2);
    }

    @Test
    void 실천률은_해야하는_날_기준() {
        HabitView h = daily(TODAY.minusDays(3), Set.of(TODAY.minusDays(3), TODAY.minusDays(1)), List.of());
        // 3·2·1일 전 중 2일 함 (오늘은 아직 안 해서 제외) → 67%
        assertThat(HabitCalendar.rate(h, TODAY.minusDays(3), TODAY)).isEqualTo(67);
        // 해야 하는 날이 없으면 null
        assertThat(HabitCalendar.rate(daily(TODAY, Set.of(), List.of()), TODAY, TODAY)).isNull();
    }

    @Test
    void 잔디_진하기() {
        assertThat(HabitCalendar.level(0, 3)).isZero();
        assertThat(HabitCalendar.level(1, 4)).isEqualTo(1);
        assertThat(HabitCalendar.level(2, 4)).isEqualTo(2);
        assertThat(HabitCalendar.level(3, 4)).isEqualTo(3);
        assertThat(HabitCalendar.level(4, 4)).isEqualTo(4);
        assertThat(HabitCalendar.level(1, 0)).isEqualTo(4);   // 해야 하는 날이 아닌데 함
    }
}
