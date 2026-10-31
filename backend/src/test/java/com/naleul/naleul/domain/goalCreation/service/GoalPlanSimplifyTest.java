package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanMilestone;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanSubGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V52: AI 초안의 마일스톤을 지우고 세부 목표를 하나로 모은다 */
class GoalPlanSimplifyTest {

    @Test
    void 마일스톤을_지우고_세부목표를_하나로_모은다() {
        LocalDate d = LocalDate.of(2026, 11, 14);
        GoalPlanDto plan = new GoalPlanDto(null,
                List.of(new PlanSubGoal("sg1", "이론", "📘", null, null), new PlanSubGoal("sg2", "기출", "📝", null, null)),
                List.of(new PlanMilestone("ms1", "중간 점검", null, d, null)),
                List.of(new PlanTask("t1", TaskType.ROUTINE, "sg2", null, "기출 1회분 30분", "📝", 30, null, null,
                                null, null, null, null),
                        new PlanTask("t2", TaskType.ONE_TIME, "sg1", "ms1", "SQLD 응시", "✏️", 90, null, null,
                                null, null, d, d)),
                null);

        GoalPlanDto out = GoalDraftWorker.simplify(plan);

        assertThat(out.milestones()).isEmpty();
        assertThat(out.subGoals()).extracting(PlanSubGoal::tempId).containsExactly("sg1");
        assertThat(out.tasks()).extracting(PlanTask::subGoalTempId).containsOnly("sg1");
        assertThat(out.tasks()).extracting(PlanTask::milestoneTempId).containsOnlyNulls();
        assertThat(out.tasks().get(1).dueDate()).isEqualTo(d);
    }
}
