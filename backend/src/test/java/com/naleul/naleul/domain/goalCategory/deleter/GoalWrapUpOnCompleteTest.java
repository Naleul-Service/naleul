package com.naleul.naleul.domain.goalCategory.deleter;

import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalWrapUpOnCompleteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 15, 0);

    private static Routine routine(RoutineStatus status) {
        Routine r = org.mockito.Mockito.spy(Routine.class);
        ReflectionTestUtils.setField(r, "routineStatus", status);
        return r;
    }

    private static Task task(LocalDateTime start, TaskStatus status) {
        Task t = Task.builder().taskName("t").taskStatus(status).build();
        ReflectionTestUtils.setField(t, "plannedStartAt", start);
        return t;
    }

    @Test
    void 달성하면_진행중_루틴은_끝내고_앞으로의_Task만_지운다() {
        RoutineRepository routines = mock(RoutineRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        PlannerClock clock = mock(PlannerClock.class);
        when(clock.now()).thenReturn(NOW);
        Routine running = routine(RoutineStatus.IN_PROGRESS);
        Routine stopped = routine(RoutineStatus.STOPPED);
        when(routines.findAllByGoalCategoryId(1L)).thenReturn(List.of(running, stopped));
        Task past = task(NOW.minusHours(3), TaskStatus.TODO);          // 오늘 아침 놓친 일 → 남김
        Task tonight = task(NOW.plusHours(4), TaskStatus.TODO);        // 오늘 밤 → 지움
        Task nextWeek = task(NOW.plusDays(7), TaskStatus.TODO);        // 다음 주 → 지움
        Task doneEarly = task(NOW.plusDays(1), TaskStatus.COMPLETED);  // 미리 해 둔 일 → 남김
        when(tasks.findAllActiveByGoalCategoryId(1L)).thenReturn(List.of(past, tonight, nextWeek, doneEarly));

        GoalCategoryCascadeDeleter d = new GoalCategoryCascadeDeleter(null, null, routines, tasks, clock);
        int[] r = d.wrapUpOnComplete(1L);

        assertThat(r).containsExactly(1, 2);
        assertThat(running.getRoutineStatus()).isEqualTo(RoutineStatus.COMPLETED);
        assertThat(running.getActualEndDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(stopped.getRoutineStatus()).isEqualTo(RoutineStatus.STOPPED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Task>> deleted = ArgumentCaptor.forClass(List.class);
        verify(tasks).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(tonight, nextWeek);
    }
}
