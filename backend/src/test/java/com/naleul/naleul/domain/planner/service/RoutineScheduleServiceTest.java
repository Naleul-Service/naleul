package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.planner.dto.request.RoutineScheduleRequest;
import com.naleul.naleul.domain.planner.dto.request.TaskScheduleRequest;
import com.naleul.naleul.domain.planner.dto.response.RoutineScheduleResponse;
import com.naleul.naleul.domain.planner.dto.response.TaskScheduleResponse;
import com.naleul.naleul.domain.planner.enums.ChangeScope;
import com.naleul.naleul.domain.planner.repository.TimetableQueryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.service.TaskFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/** 루틴 시간 변경 범위 (이날만 / 이번 주 / 앞으로) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutineScheduleServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);       // 화
    private static final LocalDate WED = LocalDate.of(2026, 10, 7);         // 수
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    @Mock TaskScheduleService taskScheduleService;
    @Mock TaskFinder taskFinder;
    @Mock TimetableQueryRepository timetableQueryRepository;
    @Mock PlannerClock clock;
    @Mock Routine routine;

    RoutineScheduleService service;

    @BeforeEach
    void setUp() {
        service = new RoutineScheduleService(taskScheduleService, taskFinder, timetableQueryRepository, clock);
        given(clock.today()).willReturn(TODAY);
        given(routine.getRoutineId()).willReturn(7L);
        given(routine.getRepeatEndDate()).willReturn(LocalDate.of(2026, 12, 31));
        given(taskScheduleService.reschedule(anyLong(), anyLong(), any(TaskScheduleRequest.class)))
                .willReturn(mock(TaskScheduleResponse.class));
    }

    private Task routineTask(long id, LocalDate date) {
        Task t = mock(Task.class);
        given(t.getTaskId()).willReturn(id);
        given(t.getRoutineDate()).willReturn(date);
        given(t.isRoutineTask()).willReturn(true);
        given(t.getRoutine()).willReturn(routine);
        given(t.getPlannedStartAt()).willReturn(date.atTime(8, 0));
        given(t.getPlannedEndAt()).willReturn(date.atTime(8, 30));
        return t;
    }

    private RoutineScheduleRequest req(LocalDate d, LocalTime s, LocalTime e, ChangeScope scope) {
        LocalDateTime start = d.atTime(s);
        LocalDateTime end = e.isAfter(s) ? d.atTime(e) : d.plusDays(1).atTime(e);
        return new RoutineScheduleRequest(start, end, scope);
    }

    @Test
    @DisplayName("이번 주: 다음 날부터 일요일까지 같은 시각으로 옮기고, 겹치는 날은 건너뛴다")
    void week_movesUntilSunday_andSkipsConflicts() {
        Task dragged = routineTask(1L, WED);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);
        Task thu = routineTask(2L, WED.plusDays(1));
        Task sat = routineTask(3L, WED.plusDays(3));
        given(timetableQueryRepository.findRoutineTodoBetween(USER, 7L, WED.plusDays(1), SUNDAY))
                .willReturn(List.of(thu, sat));
        given(taskScheduleService.tryReschedule(eq(USER), eq(2L), any(), any())).willReturn(mock(TaskScheduleResponse.class));
        given(taskScheduleService.tryReschedule(eq(USER), eq(3L), any(), any())).willReturn(null); // 겹침

        RoutineScheduleResponse res = service.change(USER, 1L, req(WED, LocalTime.of(7, 30), LocalTime.of(8, 10), ChangeScope.WEEK));

        verify(taskScheduleService).tryReschedule(USER, 2L, WED.plusDays(1).atTime(7, 30), WED.plusDays(1).atTime(8, 10));
        assertThat(res.changedDates()).containsExactly(WED.plusDays(1));
        assertThat(res.skippedDates()).containsExactly(WED.plusDays(3));
        verify(routine, never()).update(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("앞으로: 루틴 종료일까지 옮기고 루틴 기본 시간도 바꾼다")
    void following_updatesRoutineTime() {
        Task dragged = routineTask(1L, WED);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);
        given(timetableQueryRepository.findRoutineTodoBetween(USER, 7L, WED.plusDays(1), LocalDate.of(2026, 12, 31)))
                .willReturn(List.of());

        service.change(USER, 1L, req(WED, LocalTime.of(7, 30), LocalTime.of(8, 0), ChangeScope.FOLLOWING));

        verify(routine).update(null, null, null, null, null, LocalTime.of(7, 30), LocalTime.of(8, 0), null);
    }

    @Test
    @DisplayName("지난 날짜에서 바꿔도 오늘 이전 Task 는 건드리지 않는다")
    void pastDay_startsFromToday() {
        LocalDate lastSat = LocalDate.of(2026, 10, 3);
        Task dragged = routineTask(1L, lastSat);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);

        service.change(USER, 1L, req(lastSat, LocalTime.of(9, 0), LocalTime.of(9, 30), ChangeScope.FOLLOWING));

        verify(timetableQueryRepository).findRoutineTodoBetween(USER, 7L, TODAY, LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("이날만이면 그 Task 하나만 옮긴다")
    void day_onlyThisTask() {
        Task dragged = routineTask(1L, WED);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);

        RoutineScheduleResponse res = service.change(USER, 1L, req(WED, LocalTime.of(7, 0), LocalTime.of(7, 30), ChangeScope.DAY));

        verify(taskScheduleService).reschedule(eq(USER), eq(1L), any(TaskScheduleRequest.class));
        verifyNoInteractions(timetableQueryRepository);
        assertThat(res.changedDates()).isEmpty();
    }

    @Test
    @DisplayName("다른 날로 옮기면서 이번 주·앞으로를 고르면 400")
    void otherDay_rejected() {
        Task dragged = routineTask(1L, WED);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);

        assertThatThrownBy(() -> service.change(USER, 1L, req(WED.plusDays(1), LocalTime.of(7, 0), LocalTime.of(7, 30), ChangeScope.WEEK)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCOPE_SAME_DAY_ONLY);
        verify(taskScheduleService, never()).reschedule(anyLong(), anyLong(), any(TaskScheduleRequest.class));
    }

    @Test
    @DisplayName("앞으로 + 자정을 넘겨 12시간보다 긴 시간은 400 (23:30 ~ 00:30 처럼 짧은 건 된다)")
    void following_midnight_rejected() {
        Task dragged = routineTask(1L, WED);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(dragged);

        assertThatThrownBy(() -> service.change(USER, 1L, req(WED, LocalTime.of(20, 0), LocalTime.of(9, 0), ChangeScope.FOLLOWING)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCOPE_NO_MIDNIGHT);
    }

    @Test
    @DisplayName("루틴이 아닌 Task 에 이번 주를 고르면 400")
    void nonRoutine_rejected() {
        Task t = mock(Task.class);
        given(t.isRoutineTask()).willReturn(false);
        given(taskFinder.findTaskWithDetails(1L, USER)).willReturn(t);

        assertThatThrownBy(() -> service.change(USER, 1L, req(WED, LocalTime.of(7, 0), LocalTime.of(7, 30), ChangeScope.WEEK)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SCOPE_ROUTINE_ONLY);
    }
}
