package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.generalCategory.entity.GeneralCategory;
import com.naleul.naleul.domain.generalCategory.repository.GeneralCategoryRepository;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanSubGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.routine.entity.Routine;
import com.naleul.naleul.domain.routine.enums.RoutineStatus;
import com.naleul.naleul.domain.routine.repository.RoutineRepository;
import com.naleul.naleul.domain.routineRepeatDay.entity.RoutineRepeatDay;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LegacyPlanMergerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final RoutineRepository routineRepository = mock(RoutineRepository.class);
    private final GeneralCategoryRepository generalCategoryRepository = mock(GeneralCategoryRepository.class);
    private final LegacyPlanMerger merger = new LegacyPlanMerger(routineRepository, generalCategoryRepository);

    private GoalCategory legacy() {
        GoalCategory g = GoalCategory.create(null, null, "오픽 IH", TODAY.minusDays(34), TODAY.plusDays(36), null);
        org.springframework.test.util.ReflectionTestUtils.setField(g, "goalCategoryId", 7L);
        return g;
    }

    private Routine routine(long id, String name) {
        Routine r = mock(Routine.class);
        RoutineRepeatDay mon = mock(RoutineRepeatDay.class);
        given(mon.getDayOfWeek()).willReturn(DayOfWeek.MONDAY);
        RoutineRepeatDay wed = mock(RoutineRepeatDay.class);
        given(wed.getDayOfWeek()).willReturn(DayOfWeek.WEDNESDAY);
        given(r.getRoutineId()).willReturn(id);
        given(r.getRoutineName()).willReturn(name);
        given(r.getRoutineStatus()).willReturn(RoutineStatus.IN_PROGRESS);
        given(r.getRepeatDays()).willReturn(List.of(wed, mon));
        given(r.getRepeatStartTime()).willReturn(LocalTime.of(7, 0));
        given(r.getRepeatEndTime()).willReturn(LocalTime.of(7, 30));
        return r;
    }

    private GoalPlanDto aiPlan() {
        PlanGoal goal = new PlanGoal("오픽 IH 받기", "🎤", null, null, null, TODAY, TODAY.plusDays(36), null, null);
        return new GoalPlanDto(goal,
                List.of(new PlanSubGoal("sg1", "말하기 연습", null, null, null)),
                List.of(),
                List.of(new PlanTask("t1", TaskType.ROUTINE, "sg1", null, "모의고사 풀기", null, 40,
                                List.of(Weekday.SAT), LocalTime.of(10, 0), TODAY, null, null, null),
                        new PlanTask("t2", TaskType.ROUTINE, "sg1", null, "영어 스피킹 100일의 기적", null, 30,
                                List.of(Weekday.TUE), LocalTime.of(8, 0), TODAY, null, null, null)),
                List.of());
    }

    @Test
    void 기존_루틴을_초안에_넣고_AI가_같은_이름으로_또_만든_루틴은_뺀다() {
        GoalCategory g = legacy();
        Routine speaking = routine(31L, "영어 스피킹 100일의 기적");
        given(routineRepository.findAllByGoalCategoryId(7L)).willReturn(List.of(speaking));
        given(generalCategoryRepository.findAllActiveByGoalCategoryId(7L)).willReturn(List.of());

        GoalPlanDto merged = merger.merge(g, aiPlan());

        assertThat(merged.tasks()).extracting(PlanTask::tempId).containsExactly("existing-routine-31", "t1");
        PlanTask existing = merged.tasks().get(0);
        assertThat(existing.routineDays()).containsExactly(Weekday.MON, Weekday.WED);
        assertThat(existing.durationMinutes()).isEqualTo(30);
        assertThat(existing.subGoalTempId()).isEqualTo("sg1");
        assertThat(LegacyPlanMerger.withoutExisting(merged).tasks()).extracting(PlanTask::tempId).containsExactly("t1");
    }

    @Test
    void 기존_세부_목표가_있으면_그걸_쓰고_할_일을_모두_그리로_모은다() {
        GoalCategory g = legacy();
        GeneralCategory sg = mock(GeneralCategory.class);
        given(sg.getGeneralCategoryId()).willReturn(55L);
        given(sg.getGeneralCategoryName()).willReturn("스피킹");
        given(generalCategoryRepository.findAllActiveByGoalCategoryId(7L)).willReturn(List.of(sg));
        given(routineRepository.findAllByGoalCategoryId(7L)).willReturn(List.of());

        GoalPlanDto merged = merger.merge(g, aiPlan());

        assertThat(merged.subGoals()).extracting(PlanSubGoal::tempId).containsExactly("existing-subgoal-55");
        assertThat(merged.tasks()).extracting(PlanTask::subGoalTempId).containsOnly("existing-subgoal-55");
    }

    @Test
    void 이미_초안에_있는_기존_루틴은_다시_넣지_않는다_다시_만들기() {
        GoalCategory g = legacy();
        Routine speaking = routine(31L, "영어 스피킹 100일의 기적");
        given(routineRepository.findAllByGoalCategoryId(7L)).willReturn(List.of(speaking));
        given(generalCategoryRepository.findAllActiveByGoalCategoryId(7L)).willReturn(List.of());
        GoalPlanDto once = merger.merge(g, aiPlan());

        GoalPlanDto twice = merger.merge(g, once);

        assertThat(twice.tasks()).extracting(PlanTask::tempId).containsExactly("existing-routine-31", "t1");
    }

    @Test
    void 임시_목표나_AI_목표면_그대로() {
        GoalCategory temp = GoalCategory.createTemporary(null, null, "이사 준비", null, TODAY);
        assertThat(merger.merge(temp, aiPlan())).isEqualTo(aiPlan());
    }
}
