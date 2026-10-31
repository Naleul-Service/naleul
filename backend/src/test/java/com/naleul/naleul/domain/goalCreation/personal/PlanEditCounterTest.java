package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 초안을 얼마나 고쳤는지 (개인 맞춤 효과 측정) */
class PlanEditCounterTest {

    private static PlanTask t(String id, String title, int minutes, Weekday... days) {
        return new PlanTask(id, TaskType.ROUTINE, "sg1", null, title, null, minutes, List.of(days), null,
                null, null, null, null);
    }

    private static GoalPlanDto plan(PlanTask... tasks) {
        return new GoalPlanDto(null, List.of(), List.of(), List.of(tasks), List.of());
    }

    @Test
    void 그대로면_0_요일_순서만_바뀌어도_0() {
        GoalPlanDto draft = plan(t("t1", "근력", 30, Weekday.MON, Weekday.WED));
        assertThat(PlanEditCounter.count(draft, plan(t("t1", "근력 ", 30, Weekday.WED, Weekday.MON)))).isEqualTo(0);
    }

    @Test
    void 바꾼_것_지운_것_더한_것을_센다() {
        GoalPlanDto draft = plan(t("t1", "근력", 30, Weekday.MON), t("t2", "유산소", 20, Weekday.TUE), t("t3", "스트레칭", 10, Weekday.SUN));
        GoalPlanDto confirmed = plan(
                t("t1", "근력", 20, Weekday.MON),          // 시간 바꿈
                t("t3", "스트레칭", 10, Weekday.SUN),      // 그대로
                t("t9", "걷기", 30, Weekday.SAT));          // 새로 추가 (t2 는 삭제)
        assertThat(PlanEditCounter.count(draft, confirmed)).isEqualTo(3);
    }
}
