package com.naleul.naleul.domain.habit.entity;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HabitTest {

    @Test
    void 반복요일_저장형식() {
        assertThat(Habit.encode(null)).isEmpty();
        assertThat(Habit.encode(List.of())).isEmpty();
        assertThat(Habit.encode(EnumSet.allOf(DayOfWeek.class))).isEmpty();   // 7일 = 매일
        assertThat(Habit.encode(List.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.MONDAY)))
                .isEqualTo("MONDAY,FRIDAY");
        assertThat(Habit.decode("MONDAY,FRIDAY")).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(Habit.decode("")).isEmpty();
    }
}
