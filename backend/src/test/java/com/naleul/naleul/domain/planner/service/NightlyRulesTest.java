package com.naleul.naleul.domain.planner.service;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NightlyRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalDateTime AT_22 = TODAY.atTime(22, 0);

    @Test
    @DisplayName("이월: 시간 없는 일 · 이미 지난 일정은 넘기고, 22시 이후 일정은 아직 할 수 있으니 그대로")
    void shouldCarry() {
        Task untimed = Task.createOneTimeFromAi(null, null, null, null, "a", null, 30, TODAY, null);
        Task past = Task.create(null, null, null, "b", TaskPriority.C, TODAY.atTime(15, 0), TODAY.atTime(16, 0));
        Task later = Task.create(null, null, null, "c", TaskPriority.C, TODAY.atTime(22, 30), TODAY.atTime(23, 0));
        assertThat(NightlyPlanWorker.shouldCarry(untimed, AT_22)).isTrue();
        assertThat(NightlyPlanWorker.shouldCarry(past, AT_22)).isTrue();
        assertThat(NightlyPlanWorker.shouldCarry(later, AT_22)).isFalse();
    }

    @Test
    @DisplayName("이월: 끝난 지 1시간이 안 된 일정은 아직 하고 있을 수 있어 그대로 (grace 60분)")
    void graceAfterEnd() {
        Task justEnded = Task.create(null, null, null, "d", TaskPriority.C, TODAY.atTime(20, 0), TODAY.atTime(21, 30));
        LocalDateTime graced = AT_22.minusMinutes(NightlyPlanWorker.CARRY_GRACE_MINUTES);
        assertThat(NightlyPlanWorker.shouldCarry(justEnded, graced)).isFalse();
        assertThat(NightlyPlanWorker.shouldCarry(justEnded, TODAY.atTime(22, 30))).isTrue();
    }

    @Test
    @DisplayName("이월: 삭제된 목표의 일은 넘기지 않는다")
    void closedGoal() {
        GoalCategory goal = GoalCategory.create(null, null, "끝난 목표", TODAY.minusDays(10), TODAY, null);
        goal.delete();
        Task t = Task.createOneTimeFromAi(null, goal, null, null, "a", null, 30, TODAY, null);
        assertThat(NightlyPlanWorker.shouldCarry(t, AT_22)).isFalse();
    }

    @Test
    @DisplayName("요약 문구")
    void summary() {
        assertThat(DailyPlanService.summary(2, 5, 1)).isEqualTo("이월 2개 · 배치 5개 · 시간 미정 1개");
        assertThat(DailyPlanService.summary(0, 3, 0)).isEqualTo("배치 3개");
        assertThat(DailyPlanService.summary(0, 0, 0)).isEqualTo("새로 배치할 일이 없어요");
    }
}
