package com.naleul.naleul.domain.goalCategory.deleter;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import com.naleul.naleul.domain.routine.service.RoutineService;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskStatus;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalCategoryCascadeDeleterTest {

    /** 지금 = 10/6 17:30 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 17, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock
    private GeneralCategoryRepository generalCategoryRepository;

    @Mock
    private RoutineService routineService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PlannerClock clock;

    @InjectMocks
    private GoalCategoryCascadeDeleter goalCategoryCascadeDeleter;

    private static Task task(TaskStatus status, LocalDateTime startAt, LocalDate date) {
        Task t = mock(Task.class);
        lenient().when(t.getTaskStatus()).thenReturn(status);
        lenient().when(t.getPlannedStartAt()).thenReturn(startAt);
        lenient().when(t.getRoutineDate()).thenReturn(date);
        return t;
    }

    @Test
    void 세부목표를_지우고_루틴은_멈추기만_한다() {
        GeneralCategory gc1 = mock(GeneralCategory.class);
        GeneralCategory gc2 = mock(GeneralCategory.class);
        given(gc1.getGeneralCategoryId()).willReturn(100L);
        given(gc2.getGeneralCategoryId()).willReturn(200L);
        given(generalCategoryRepository.findAllActiveByGoalCategoryId(10L)).willReturn(List.of(gc1, gc2));
        given(clock.now()).willReturn(NOW);

        goalCategoryCascadeDeleter.deleteCascade(10L);

        verify(gc1).delete();
        verify(gc2).delete();
        verify(routineService).stopAllByGeneralCategoryId(100L);
        verify(routineService).stopAllByGeneralCategoryId(200L);
        // 루틴 삭제 경로(지난 미완료 Task 까지 지우는)는 쓰지 않는다
        verify(routineService, never()).deleteAllByGeneralCategoryId(any());
    }

    @Test
    void 지금_이후_Task만_지우고_지난_Task와_완료한_Task는_남긴다() {
        Task pastMissed = task(TaskStatus.TODO, NOW.minusHours(3), TODAY);              // 오늘 14:30 — 지났음 → 남김
        Task startedNow = task(TaskStatus.TODO, NOW.minusMinutes(10), TODAY);           // 진행 중 → 남김
        Task laterToday = task(TaskStatus.TODO, NOW.plusHours(2), TODAY);               // 오늘 19:30 → 삭제
        Task tomorrow = task(TaskStatus.TODO, null, TODAY.plusDays(1));                 // 내일 시간 미정 → 삭제
        Task todayNoTime = task(TaskStatus.TODO, null, TODAY);                          // 오늘 시간 미정 → 삭제
        Task yesterdayNoTime = task(TaskStatus.TODO, null, TODAY.minusDays(1));         // 어제 놓친 것 → 남김
        Task doneEarly = task(TaskStatus.COMPLETED, NOW.plusDays(1), TODAY.plusDays(1)); // 미리 완료 → 남김
        given(generalCategoryRepository.findAllActiveByGoalCategoryId(10L)).willReturn(List.of());
        given(clock.now()).willReturn(NOW);
        given(taskRepository.findAllActiveByGoalCategoryId(10L)).willReturn(List.of(
                pastMissed, startedNow, laterToday, tomorrow, todayNoTime, yesterdayNoTime, doneEarly));

        goalCategoryCascadeDeleter.deleteCascade(10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Task>> deleted = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactlyInAnyOrder(laterToday, tomorrow, todayNoTime);
    }

    @Test
    void 미션_Task는_지우지_않는다() {
        Task mission = task(TaskStatus.TODO, NOW.plusDays(1), TODAY.plusDays(1));
        when(mission.isMissionTask()).thenReturn(true);

        assertThat(GoalCategoryCascadeDeleter.isUpcoming(mission, NOW)).isFalse();
    }
}
