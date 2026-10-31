package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoalMetric;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanMilestone;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalPlanRepairerTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 1);

    private static PlanTask routine(String id, String title) {
        return new PlanTask(id, TaskType.ROUTINE, "sg1", null, title, null, 20,
                List.of(Weekday.MON), null, null, null, null, null);
    }

    private static PlanTask oneTime(String id, String milestoneId) {
        return new PlanTask(id, TaskType.ONE_TIME, "sg1", milestoneId, "모의고사", null, 60,
                null, null, null, null, D, null);
    }

    private static PlanMilestone ms(String id, LocalDate due) {
        return new PlanMilestone(id, id, null, due, null);
    }

    @Test
    void 마일스톤을_날짜순으로_정렬하고_같은_날짜는_합친다() {
        GoalPlanDto plan = new GoalPlanDto(null, List.of(),
                List.of(ms("m1", D.plusDays(30)), ms("m2", D.plusDays(10)), ms("m3", D.plusDays(10))),
                List.of(routine("t1", "걷기"), oneTime("t2", "m3")), null);

        GoalPlanDto fixed = GoalPlanRepairer.repair(plan);

        assertThat(fixed.milestones()).extracting(PlanMilestone::tempId).containsExactly("m2", "m1");
        // 지워진 m3 에 연결돼 있던 할 일은 같은 날짜의 m2 로 옮겨진다
        assertThat(fixed.tasks().get(1).milestoneTempId()).isEqualTo("m2");
    }

    @Test
    void 같은_활동_루틴은_첫번째만_남긴다() {
        GoalPlanDto plan = new GoalPlanDto(null, List.of(), List.of(),
                List.of(routine("t1", "데이터 분석 강의 세션 (1)"), routine("t2", "데이터 분석 강의 세션 (2)"),
                        routine("t3", "복습")), null);

        assertThat(GoalPlanRepairer.repair(plan).tasks()).extracting(PlanTask::tempId).containsExactly("t1", "t3");
    }

    @Test
    void 루틴은_10개까지만_남긴다() {
        List<PlanTask> many = new ArrayList<>();
        String[] names = {"가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타"};
        for (int i = 0; i < names.length; i++) many.add(routine("t" + i, names[i]));

        GoalPlanDto fixed = GoalPlanRepairer.repair(new GoalPlanDto(null, List.of(), List.of(), many, null));

        assertThat(fixed.tasks()).hasSize(GoalPlanRepairer.MAX_ROUTINES);
    }

    private static PlanGoal goal(LocalDate end, PlanGoalMetric metric) {
        return new PlanGoal("목표", null, null, null, metric, D, end, null, null, null);
    }

    private static PlanMilestone ms(String id, LocalDate due, String value) {
        return new PlanMilestone(id, id, null, due, value == null ? null : new BigDecimal(value));
    }

    @Test
    void 마지막_마일스톤은_목표_종료일과_목표값으로_맞춘다() {
        LocalDate end = D.plusDays(60);
        PlanGoalMetric metric = new PlanGoalMetric("체중", "kg", new BigDecimal("70"), new BigDecimal("64"));
        GoalPlanDto plan = new GoalPlanDto(goal(end, metric), List.of(),
                List.of(ms("m1", D.plusDays(20), "72"),      // 거꾸로 감 → 시작값 유지
                        ms("m2", D.plusDays(40), null),      // 비어 있음 → 2/3 지점
                        ms("m3", D.plusDays(50), "65")),     // 마지막인데 종료일·목표값과 다름
                List.of(routine("t1", "걷기")), null);

        List<PlanMilestone> fixed = GoalPlanRepairer.repair(plan).milestones();

        assertThat(fixed.get(0).targetValue()).isEqualByComparingTo("70");
        assertThat(fixed.get(1).targetValue()).isEqualByComparingTo("66");
        assertThat(fixed.get(2).targetValue()).isEqualByComparingTo("64");
        assertThat(fixed.get(2).dueDate()).isEqualTo(end);
    }

    @Test
    void 종료일을_넘는_마일스톤은_앞_마일스톤으로_합친다() {
        LocalDate end = D.plusDays(30);
        GoalPlanDto plan = new GoalPlanDto(goal(end, null), List.of(),
                List.of(ms("m1", end, null), ms("m2", end.plusDays(10), null)),
                List.of(oneTime("t1", "m2")), null);

        GoalPlanDto fixed = GoalPlanRepairer.repair(plan);

        assertThat(fixed.milestones()).extracting(PlanMilestone::tempId).containsExactly("m1");
        assertThat(fixed.tasks().get(0).milestoneTempId()).isEqualTo("m1");
    }
}
