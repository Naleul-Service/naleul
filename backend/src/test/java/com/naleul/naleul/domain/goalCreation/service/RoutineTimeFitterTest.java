package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AI 초안 루틴 시각을 고정 시간 · 다른 루틴에 맞추기 (DB 없이) */
class RoutineTimeFitterTest {

    static final LocalDate MON = LocalDate.of(2026, 11, 16);

    private static Map<DayOfWeek, RoutineTimeFitter.Day> monday(int wakeH, int bedH) {
        RoutineTimeFitter.Day d = new RoutineTimeFitter.Day(MON,
                new DateTimeRange(MON.atTime(wakeH, 0), bedH == 24 ? MON.plusDays(1).atStartOfDay() : MON.atTime(bedH, 0)));
        d.fixed.add(new DateTimeRange(MON.atTime(12, 0), MON.atTime(13, 0)));   // 점심
        Map<DayOfWeek, RoutineTimeFitter.Day> m = new EnumMap<>(DayOfWeek.class);
        m.put(DayOfWeek.MONDAY, d);
        return m;
    }

    private static PlanTask routine(String id, int minutes, LocalTime start) {
        return new PlanTask(id, TaskType.ROUTINE, "sg1", null, "공부 " + minutes + "분", null, minutes,
                List.of(Weekday.MON), start, null, null, null, null, "설명", "이유");
    }

    private static GoalPlanDto plan(PlanTask... tasks) {
        return new GoalPlanDto(null, List.of(), List.of(), List.of(tasks), List.of());
    }

    @Test
    @DisplayName("점심과 겹치는 시각은 가장 가까운 빈 시각으로 옮기고 이유를 덧붙인다")
    void movesOffLunch() {
        GoalPlanDto out = RoutineTimeFitter.fit(plan(routine("r1", 60, LocalTime.of(12, 0))), monday(7, 24), null);
        PlanTask t = out.tasks().get(0);
        assertThat(t.preferredStartTime()).isEqualTo(LocalTime.of(11, 0));   // 11~12시 (13시와 거리가 같으면 이른 쪽)
        assertThat(t.reason()).startsWith("이유 ").contains("11:00로 맞췄어요");
    }

    @Test
    @DisplayName("겹치지 않으면 AI 시각 그대로")
    void keepsWhenFree() {
        GoalPlanDto in = plan(routine("r1", 60, LocalTime.of(19, 0)));
        assertThat(RoutineTimeFitter.fit(in, monday(7, 24), null)).isSameAs(in);
    }

    @Test
    @DisplayName("한 번에 할 연속 시간이 없으면 시각을 비워 자동 배치(나눠 넣기)에 맡긴다")
    void clearsWhenNoContinuousSlot() {
        // 하루 09~15시, 점심 12~13시 → 연속 최대 3시간. 4시간 루틴은 들어갈 곳이 없다
        GoalPlanDto out = RoutineTimeFitter.fit(plan(routine("r1", 240, LocalTime.of(9, 0))), monday(9, 15), null);
        assertThat(out.tasks().get(0).preferredStartTime()).isNull();
        assertThat(out.tasks().get(0).reason()).contains("나눠서");
    }

    @Test
    @DisplayName("같은 초안의 루틴끼리는 쉬는 시간 20분을 두고, 이어지는 시각을 고른다")
    void groupsSameGoalRoutines() {
        GoalPlanDto out = RoutineTimeFitter.fit(plan(
                routine("r1", 60, LocalTime.of(19, 0)),
                routine("r2", 30, LocalTime.of(19, 30))), monday(7, 24), null);
        LocalTime second = out.tasks().get(1).preferredStartTime();
        // 19~20시 뒤 20분 쉬고 20:20, 또는 앞쪽 18:10~18:40 — 어느 쪽이든 이어져 있어야 한다
        assertThat(second).isIn(LocalTime.of(20, 20), LocalTime.of(18, 10));
    }
}
