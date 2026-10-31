package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoalMetric;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PinUserFactsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    void 사용자가_말한_시작일과_목표수치로_고정한다() {
        LocalDate start = LocalDate.of(2026, 10, 29), end = LocalDate.of(2026, 11, 12);
        GoalSlots slots = GoalSlots.empty()
                .withGoalStatement(Slot.filled("3kg 감량"))
                .withMetric(Slot.filled(new MetricSlot("체중", new BigDecimal("63"), new BigDecimal("60"), "kg")))
                .withDeadline(Slot.filled(new DeadlineSlot(end, false, start)));
        // AI 는 오늘부터, 목표 62.3kg 으로 "조정"해서 짰다
        PlanGoal g = new PlanGoal("감량", "🔥", null, null,
                new PlanGoalMetric("체중", "kg", new BigDecimal("63"), new BigDecimal("62.3")),
                TODAY, end, null, "2주 3kg은 빨라요");
        PlanTask routine = new PlanTask("t1", TaskType.ROUTINE, "sg1", null, "걷기 30분", null, 30,
                null, null, TODAY, TODAY.plusDays(7), null, null);
        GoalPlanDto plan = new GoalPlanDto(g, List.of(), List.of(), List.of(routine), null);

        GoalPlanDto out = GoalDraftWorker.pinUserFacts(plan, slots, TODAY);

        assertThat(out.goal().startDate()).isEqualTo(start);
        assertThat(out.goal().metric().targetValue()).isEqualByComparingTo("60");
        assertThat(out.goal().aiNote()).isEqualTo("2주 3kg은 빨라요");
        assertThat(out.tasks().get(0).startDate()).isEqualTo(start);
        assertThat(out.tasks().get(0).endDate()).isEqualTo(start.plusDays(3));   // 7/30 지점 → 14일 중 3일째
    }
}
