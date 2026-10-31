package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.repository.GoalCategoryRepository;
import com.naleul.naleul.domain.goalCreation.client.FakeGoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationRequest;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.milestone.entity.Milestone;
import com.naleul.naleul.domain.milestone.repository.MilestoneRepository;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.factory.RoutineTaskFactory;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskSource;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * GoalPlanPersister: 초안 → 엔티티 매핑이 A안대로 되는지 검증.
 * Fake 계획 구성: 세부 목표 2, 마일스톤 4, 루틴 2, 일회성 4
 */
@ExtendWith(MockitoExtension.class)
class GoalPlanPersisterTest {

    @Mock private GoalCategoryRepository goalCategoryRepository;
    @Mock private GeneralCategoryRepository generalCategoryRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private RoutineRepository routineRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private RoutineTaskFactory routineTaskFactory;

    @InjectMocks
    private GoalPlanPersister persister;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private User user;
    private GoalPlanDto plan;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).build();
        GoalSlots slots = GoalSlots.empty()
                .withGoalStatement(Slot.filled("몸무게 10kg 감량"))
                .withMetric(Slot.filled(new MetricSlot("체중", BigDecimal.valueOf(78), BigDecimal.valueOf(68), "kg")));
        plan = new FakeGoalAiClient().generatePlan(new PlanGenerationRequest(
                slots, PlanningStyle.PLANNER, TODAY, 300, null, null, List.of(), null)).plan();
    }

    @Test
    void 초안을_A안_매핑대로_저장한다() {
        given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));
        given(routineTaskFactory.generateTasks(any(Routine.class))).willReturn(List.of(new Task(), new Task()));

        GoalCategory goal = persister.persist(user, null, plan, PlanningStyle.PLANNER, "지방간 개선", false);

        // 목표
        assertThat(goal.isAiGenerated()).isTrue();
        assertThat(goal.getMotive()).isEqualTo("지방간 개선");
        assertThat(goal.getCurrentValue()).isEqualByComparingTo("78");   // 현재값 = 시작값
        assertThat(goal.getPlanningStyle()).isEqualTo(PlanningStyle.PLANNER);

        // 세부 목표 2 → general_category, 정렬 순서 보존
        ArgumentCaptor<GeneralCategory> generals = ArgumentCaptor.forClass(GeneralCategory.class);
        then(generalCategoryRepository).should(times(2)).save(generals.capture());
        assertThat(generals.getAllValues()).extracting(GeneralCategory::getSortOrder).containsExactly(0, 1);
        assertThat(generals.getAllValues()).allSatisfy(gc -> assertThat(gc.getGoalCategory()).isEqualTo(goal));

        // 마일스톤 4
        then(milestoneRepository).should(times(4)).save(any(Milestone.class));

        // 루틴 2 → 각각 RoutineTaskFactory로 날짜별 Task 즉시 생성
        ArgumentCaptor<Routine> routines = ArgumentCaptor.forClass(Routine.class);
        then(routineRepository).should(times(2)).save(routines.capture());
        assertThat(routines.getAllValues()).allSatisfy(r -> {
            assertThat(r.getSource()).isEqualTo(TaskSource.AI_GOAL);
            assertThat(r.getRepeatEndDate()).isEqualTo(plan.goal().endDate());   // endDate null → 목표 종료일
            // BM(2026-10): 무료도 알림 허용 → 시작 시각이 있는 루틴만 알림 ON (시간 없는 루틴은 알릴 시각이 없음)
            assertThat(r.isNotificationEnabled()).isEqualTo(r.getRepeatStartTime() != null);
        });
        then(taskRepository).should(times(2)).saveAll(any());
    }

    @Test
    void 일회성_Task는_마일스톤에_연결되고_권장실행일이_routineDate로_들어간다() {
        given(routineRepository.save(any(Routine.class))).willAnswer(inv -> inv.getArgument(0));
        given(routineTaskFactory.generateTasks(any(Routine.class))).willReturn(List.of());

        persister.persist(user, null, plan, PlanningStyle.PLANNER, null, false);

        ArgumentCaptor<Task> oneTimes = ArgumentCaptor.forClass(Task.class);
        then(taskRepository).should(times(4)).save(oneTimes.capture());
        assertThat(oneTimes.getAllValues()).allSatisfy(t -> {
            assertThat(t.getMilestone()).isNotNull();
            assertThat(t.getPlannedStartAt()).isNull();                     // 시간은 아직 미정
            assertThat(t.getRoutineDate()).isEqualTo(t.getScheduledDate()); // 날짜 화면에 노출되도록
            assertThat(t.getSource()).isEqualTo(TaskSource.AI_GOAL);
            assertThat(t.getRoutine()).isNull();                            // sourceType MANUAL로 분류
        });
    }
}
