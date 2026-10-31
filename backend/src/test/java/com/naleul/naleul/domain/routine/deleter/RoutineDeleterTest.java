package com.naleul.naleul.domain.routine.deleter;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutineCascadeDeleterTest {

    @Mock
    private TaskRepository taskRepository;

    private RoutineCascadeDeleter routineCascadeDeleter;

    private Routine createRoutine() {
        return Routine.create(
                mock(User.class), mock(GoalCategory.class), mock(GeneralCategory.class),
                "테스트 루틴",
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(DayOfWeek.MONDAY),
                true
        );
    }

    private Task taskWithStatus(TaskStatus status) {
        Task task = mock(Task.class);
        given(task.getTaskStatus()).willReturn(status);
        return task;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        routineCascadeDeleter = new RoutineCascadeDeleter(taskRepository);
    }

    @Test
    @DisplayName("루틴을 소프트 삭제한다")
    void deleteRoutineAndTasks_softDeletesRoutine() {
        // given
        Routine routine = spy(createRoutine());
        given(taskRepository.findByRoutine(routine)).willReturn(List.of());

        // when
        routineCascadeDeleter.deleteRoutineAndTasks(routine);

        // then
        verify(routine).delete();
    }

    @Test
    @DisplayName("완료되지 않은(TODO) Task만 삭제하고 완료된 Task는 보존한다")
    void deleteRoutineAndTasks_deletesOnlyIncompleteTasks() {
        // given
        Routine routine = spy(createRoutine());
        Task incompleteTask = taskWithStatus(TaskStatus.TODO);
        Task completedTask = taskWithStatus(TaskStatus.COMPLETED);
        given(taskRepository.findByRoutine(routine)).willReturn(List.of(incompleteTask, completedTask));

        // when
        routineCascadeDeleter.deleteRoutineAndTasks(routine);

        // then
        verify(taskRepository).deleteAll(List.of(incompleteTask));
    }

    @Test
    @DisplayName("모든 Task가 완료 상태면 삭제 대상 없이 빈 리스트로 deleteAll이 호출된다")
    void deleteRoutineAndTasks_allCompleted_deletesNothing() {
        // given
        Routine routine = spy(createRoutine());
        Task completedTask1 = taskWithStatus(TaskStatus.COMPLETED);
        Task completedTask2 = taskWithStatus(TaskStatus.COMPLETED);
        given(taskRepository.findByRoutine(routine)).willReturn(List.of(completedTask1, completedTask2));

        // when
        routineCascadeDeleter.deleteRoutineAndTasks(routine);

        // then
        verify(taskRepository).deleteAll(List.of());
    }

    @Test
    @DisplayName("연관된 Task가 없으면 빈 리스트로 deleteAll이 호출된다")
    void deleteRoutineAndTasks_noTasks_deletesEmptyList() {
        // given
        Routine routine = spy(createRoutine());
        given(taskRepository.findByRoutine(routine)).willReturn(List.of());

        // when
        routineCascadeDeleter.deleteRoutineAndTasks(routine);

        // then
        verify(routine).delete();
        verify(taskRepository).deleteAll(List.of());
    }
}