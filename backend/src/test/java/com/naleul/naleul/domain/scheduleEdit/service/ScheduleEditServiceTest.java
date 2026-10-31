package com.naleul.naleul.domain.scheduleEdit.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.planner.dto.response.TimeBlockTaskResponse;
import com.naleul.naleul.domain.planner.dto.response.TimetableResponse;
import com.naleul.naleul.domain.planner.service.TaskScheduleService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.dto.request.RoutineUpdateRequest;
import com.naleul.naleul.domain.routine.dto.response.RoutineResponse;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditApplyResponse;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOp;
import com.naleul.naleul.domain.scheduleEdit.dto.ScheduleEditOpType;
import com.naleul.naleul.domain.task.enums.TaskSourceType;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleEditServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // 수요일

    private RoutineService routineService;
    private TaskScheduleService taskScheduleService;
    private PlannerClock clock;
    private ScheduleEditService service;

    @BeforeEach
    void setUp() {
        routineService = mock(RoutineService.class);
        taskScheduleService = mock(TaskScheduleService.class);
        clock = mock(PlannerClock.class);
        when(clock.today()).thenReturn(TODAY);
        when(clock.now()).thenReturn(TODAY.atTime(9, 0));
        service = new ScheduleEditService(null, routineService, null, taskScheduleService, null, null, null, clock);
    }

    private static RoutineResponse routine(long id, String name, String start, String end, DayOfWeek... days) {
        return RoutineResponse.builder()
                .routineId(id).routineName(name).goalCategoryId(1L).goalCategoryName("목표")
                .repeatStartDate(TODAY.minusDays(10)).repeatEndDate(TODAY.plusDays(30))
                .repeatStartTime(LocalTime.parse(start)).repeatEndTime(LocalTime.parse(end))
                .routineStatus(RoutineStatus.IN_PROGRESS)
                .repeatDays(java.util.Arrays.stream(days)
                        .map(d -> RoutineResponse.RepeatDayInfo.builder().dayOfWeek(d).build()).toList())
                .build();
    }

    private static TimeBlockTaskResponse task(long id, String name, Long routineId, LocalDate date, int h, int m, int minutes) {
        LocalDateTime s = date.atTime(h, m);
        return new TimeBlockTaskResponse(id, name, null, TaskStatus.TODO, null, null,
                routineId != null ? TaskSourceType.ROUTINE : TaskSourceType.MANUAL, null, 1L, "목표", null, null, false,
                null, null, null, null, routineId, date, s, s.plusMinutes(minutes), (long) minutes, date, null, false,
                null, null, List.of(), 0, null, null, null, false, false, null, List.of());
    }

    private ScheduleEditService.Snapshot snap(List<RoutineResponse> routines, List<TimeBlockTaskResponse> tasks) {
        Map<Long, RoutineResponse> rm = new LinkedHashMap<>();
        routines.forEach(r -> rm.put(r.getRoutineId(), r));
        Map<Long, TimeBlockTaskResponse> tm = new LinkedHashMap<>();
        tasks.forEach(t -> tm.put(t.taskId(), t));
        return new ScheduleEditService.Snapshot(TODAY, TODAY, TODAY.plusDays(4), rm, tm, new TimetableResponse(List.of()));
    }

    @Test
    void 겹치는_루틴_찾기() {
        RoutineResponse a = routine(1, "기출 풀기", "16:00", "17:00", DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        RoutineResponse b = routine(2, "SQL 문제", "16:30", "17:30", DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY);
        RoutineResponse c = routine(3, "러닝", "21:00", "21:30", DayOfWeek.TUESDAY);

        List<ScheduleEditService.Overlap> o = ScheduleEditService.overlaps(List.of(a, b, c));

        assertThat(o).hasSize(1);
        assertThat(o.get(0).days()).containsExactly(DayOfWeek.TUESDAY);
        assertThat(o.get(0).text()).isEqualTo("화 16:30–17:00 '기출 풀기' · 'SQL 문제'");
    }

    @Test
    void 자정을_넘기는_루틴도_겹침() {
        RoutineResponse a = routine(1, "공부", "23:00", "00:30", DayOfWeek.MONDAY);
        RoutineResponse b = routine(2, "독서", "23:30", "00:00", DayOfWeek.MONDAY);
        assertThat(ScheduleEditService.overlaps(List.of(a, b))).hasSize(1);
    }

    @Test
    void 검증_없는_루틴_그대로인_변경_지난_Task_는_버림() {
        RoutineResponse a = routine(1, "기출 풀기", "16:00", "17:00", DayOfWeek.TUESDAY);
        TimeBlockTaskResponse past = task(10, "어제 일", null, TODAY.minusDays(1), 10, 0, 60);
        var s = snap(List.of(a), List.of(past));

        assertThat(service.sanitize(new ScheduleEditOp(ScheduleEditOpType.MOVE_ROUTINE, 99L, null, LocalTime.of(19, 0), null, null), s)).isNull();
        assertThat(service.sanitize(new ScheduleEditOp(ScheduleEditOpType.MOVE_ROUTINE, 1L, List.of(Weekday.TUE), LocalTime.of(16, 0), 60, null), s)).isNull();
        assertThat(service.sanitize(new ScheduleEditOp(ScheduleEditOpType.MOVE_TASK, 10L, null, LocalTime.of(19, 0), null, null), s)).isNull();

        ScheduleEditOp ok = service.sanitize(new ScheduleEditOp(ScheduleEditOpType.MOVE_ROUTINE, 1L, List.of(Weekday.TUE), LocalTime.of(19, 0), null, null), s);
        assertThat(ok).isNotNull();
        assertThat(ok.days()).isNull();          // 요일은 그대로라 빠져요
        assertThat(ok.startTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void 루틴을_옮기면_요일별_시간도_맞추고_같은_루틴_Task_변경은_건너뜀() {
        RoutineResponse a = routine(1, "기출 풀기", "16:00", "17:00", DayOfWeek.THURSDAY);
        TimeBlockTaskResponse thu = task(10, "기출 풀기", 1L, TODAY.plusDays(1), 16, 0, 60);
        TimeBlockTaskResponse report = task(11, "보고서", null, TODAY.plusDays(1), 14, 0, 60);
        var s = snap(List.of(a), List.of(thu, report));
        ScheduleEditService spy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(s).when(spy).snapshot(any(), any(), any());
        when(taskScheduleService.tryReschedule(eq(7L), eq(11L), any(), any())).thenReturn(null); // 잠긴 일정과 겹침

        ScheduleEditApplyResponse r = spy.apply(7L, List.of(
                new ScheduleEditOp(ScheduleEditOpType.MOVE_TASK, 10L, null, LocalTime.of(20, 0), null, null),
                new ScheduleEditOp(ScheduleEditOpType.MOVE_ROUTINE, 1L, null, LocalTime.of(19, 0), null, null),
                new ScheduleEditOp(ScheduleEditOpType.MOVE_TASK, 11L, null, LocalTime.of(15, 0), null, null)
        ), TODAY, TODAY.plusDays(4));

        ArgumentCaptor<RoutineUpdateRequest> req = ArgumentCaptor.forClass(RoutineUpdateRequest.class);
        verify(routineService).update(eq(7L), eq(1L), req.capture());
        assertThat(req.getValue().getRepeatStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(req.getValue().getRepeatEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(req.getValue().getDayTimes()).isEmpty();
        verify(taskScheduleService, never()).tryReschedule(eq(7L), eq(10L), any(), any());
        assertThat(r.applied()).isEqualTo(1);
        assertThat(r.skipped()).hasSize(2);
    }
}
