package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanFit;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanGoal;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.PlanTask;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.TimeWindow;
import com.naleul.naleul.domain.pattern.enums.DayGroup;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 초안 화면 "나에게 맞춘 점" — 계획을 직접 확인해서만 말한다 */
class PlanFitBuilderTest {

    static final TimeWindow GOLDEN = new TimeWindow(DayGroup.WEEKDAY, 19, 22, 85, 30);

    private static PlanTask routine(String title, int minutes, LocalTime start, Weekday... days) {
        return new PlanTask("t" + title.hashCode(), TaskType.ROUTINE, "sg1", null, title, null, minutes,
                List.of(days), start, null, null, null, null, "방법", "이유");
    }

    private static GoalPlanDto plan(PlanTask... tasks) {
        PlanGoal goal = new PlanGoal("운동", null, null, null, null,
                LocalDate.of(2026, 10, 12), LocalDate.of(2027, 1, 10), null, null);
        return new GoalPlanDto(goal, List.of(), List.of(), List.of(tasks), List.of());
    }

    private static PersonalPlanContext ctx(CapacityProfile p) {
        return PersonalPlanContext.of(p, PlanIntensity.STEADY, null);
    }

    @Test
    void 골든_타임에_실제로_넣은_루틴이_있을_때만_그렇다고_말한다() {
        CapacityProfile p = new CapacityProfile(40, 75, 300, 0, 0, GOLDEN, null, null, null, null);

        PlanFit inGolden = PlanFitBuilder.build(plan(routine("근력", 30, LocalTime.of(19, 30), Weekday.MON, Weekday.WED)), ctx(p));
        assertThat(inGolden.notes().stream().anyMatch(n -> n.contains("평일 19–22시"))).isTrue();

        PlanFit morning = PlanFitBuilder.build(plan(routine("근력", 30, LocalTime.of(7, 0), Weekday.MON, Weekday.WED)), ctx(p));
        assertThat(morning.notes().stream().anyMatch(n -> n.contains("19–22시"))).isFalse();
    }

    @Test
    void 약한_요일을_비웠을_때만_그렇다고_말한다() {
        CapacityProfile p = new CapacityProfile(40, 75, 300, 0, 0, null, null, DayOfWeek.MONDAY, 40, 70);

        PlanFit avoided = PlanFitBuilder.build(plan(routine("근력", 30, null, Weekday.TUE, Weekday.THU)), ctx(p));
        assertThat(avoided.notes().stream().anyMatch(n -> n.startsWith("월요일"))).isTrue();

        PlanFit notAvoided = PlanFitBuilder.build(plan(routine("근력", 30, null, Weekday.MON, Weekday.THU)), ctx(p));
        assertThat(notAvoided.notes().stream().anyMatch(n -> n.startsWith("월요일"))).isFalse();
    }

    @Test
    void 상한을_넘으면_줄이라고_말하고_주간_합계를_남긴다() {
        CapacityProfile p = new CapacityProfile(40, 40, 120, 0, 0, null, null, null, null, null);   // 상한 210분
        PlanFit fit = PlanFitBuilder.build(plan(
                routine("근력", 30, null, Weekday.MON, Weekday.WED, Weekday.FRI),
                routine("유산소", 30, null, Weekday.TUE, Weekday.THU, Weekday.SAT, Weekday.SUN)), ctx(p));

        assertThat(fit.weeklyCapMinutes()).isEqualTo(210);
        assertThat(fit.planWeeklyMinutes()).isEqualTo(210);
        assertThat(fit.notes().get(0).contains("상한(주 3시간 30분) 안에")).isTrue();
        assertThat(fit.basedOnHistory()).isTrue();
        assertThat(fit.intensity()).isEqualTo("STEADY");
    }

    @Test
    void 기록이_없으면_인터뷰_기준이라고_말한다() {
        PlanFit fit = PlanFitBuilder.build(plan(routine("근력", 20, null, Weekday.MON)), ctx(CapacityProfile.empty()));
        assertThat(fit.basedOnHistory()).isFalse();
        assertThat(fit.notes().get(0).startsWith("아직 실행 기록이 적어")).isTrue();
    }

    @Test
    void 달성자_집계가_있으면_한_줄_보태고_기간이_짧으면_알려준다() {
        AchieverSummary a = new AchieverSummary("다이어트", 42, 80, 210, 180, 3);   // 중앙값 30주, 이 계획은 90일(절반 105일보다 짧음)
        PlanFit fit = PlanFitBuilder.build(plan(routine("근력", 20, null, Weekday.MON)),
                PersonalPlanContext.of(CapacityProfile.empty(), PlanIntensity.STEADY, a));
        String last = fit.notes().get(fit.notes().size() - 1);
        assertThat(last.contains("다이어트 목표를 달성한 42명은 보통 30주")).isTrue();
        assertThat(last.contains("기간이 꽤 짧은 편")).isTrue();
    }
}
