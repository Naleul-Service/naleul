package com.naleul.naleul.domain.routine.entity;

import com.naleul.naleul.domain.routine.dto.request.RoutineDayTime;
import com.naleul.naleul.domain.task.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 루틴 요일별 시간 (V41) */
class RoutineDayTimeTest {

    static final LocalDate MON = LocalDate.of(2026, 10, 12);
    static final LocalDate WED = MON.plusDays(2);

    private static Routine routine() {
        return Routine.create(null, null, null, "필기 이론 25분",
                LocalTime.of(20, 0), LocalTime.of(20, 25),
                MON, MON.plusWeeks(4), List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), false);
    }

    @Test
    void 요일별_시간이_없으면_기본_시간() {
        Task task = Task.fromRoutine(routine(), WED);

        assertThat(task.getPlannedStartAt()).isEqualTo(WED.atTime(20, 0));
        assertThat(task.getPlannedEndAt()).isEqualTo(WED.atTime(20, 25));
    }

    @Test
    void 그_요일만_다른_시간으로() {
        Routine r = routine();
        r.applyDayTimes(List.of(new RoutineDayTime(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(7, 30))));

        assertThat(Task.fromRoutine(r, WED).getPlannedStartAt()).isEqualTo(WED.atTime(7, 0));
        assertThat(Task.fromRoutine(r, WED).getPlannedDurationMinutes()).isEqualTo(30L);
        assertThat(Task.fromRoutine(r, MON).getPlannedStartAt()).isEqualTo(MON.atTime(20, 0));
    }

    @Test
    void 목록에서_빠진_요일은_기본_시간으로_되돌린다() {
        Routine r = routine();
        r.applyDayTimes(List.of(new RoutineDayTime(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(7, 30))));
        r.applyDayTimes(List.of());

        assertThat(r.startTimeOn(DayOfWeek.WEDNESDAY)).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void 반복_요일이_아니면_무시하고_요일을_빼면_그_시간도_사라진다() {
        Routine r = routine();
        r.applyDayTimes(List.of(new RoutineDayTime(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(9, 30))));
        assertThat(r.getRepeatDays()).noneMatch(rd -> rd.hasOwnTime());

        r.applyDayTimes(List.of(new RoutineDayTime(DayOfWeek.FRIDAY, LocalTime.of(6, 0), LocalTime.of(6, 20))));
        r.updateRepeatDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        assertThat(r.startTimeOn(DayOfWeek.FRIDAY)).isEqualTo(LocalTime.of(20, 0));
    }
}
