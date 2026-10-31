package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto.*;
import com.naleul.naleul.domain.goalCreation.dto.plan.TaskType;
import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.enums.DraftField;
import com.naleul.naleul.domain.goalCreation.enums.DraftFieldResult;
import com.naleul.naleul.domain.goalCreation.enums.DraftItemKind;
import com.naleul.naleul.domain.goalCreation.enums.EditDirection;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer.Diff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DraftPlanComparerTest {

    static final LocalDate START = LocalDate.of(2026, 10, 26);
    static final LocalDate END = LocalDate.of(2027, 4, 30);

    static PlanGoal goal(String title, LocalDate end, BigDecimal target) {
        return new PlanGoal(title, "🏋️", null, null,
                new PlanGoalMetric("몸무게", "kg", new BigDecimal("72"), target), START, end, null, null);
    }

    static PlanTask routine(String id, String title, int minutes, LocalTime at, Weekday... days) {
        return new PlanTask(id, TaskType.ROUTINE, "sg1", null, title, null, minutes, List.of(days), at,
                null, null, null, null);
    }

    static PlanTask oneTime(String id, String title, String ms, LocalDate due) {
        return new PlanTask(id, TaskType.ONE_TIME, "sg1", ms, title, null, 30, null, null,
                null, null, null, due);
    }

    static GoalPlanDto plan(PlanGoal g, List<PlanMilestone> ms, PlanTask... tasks) {
        return new GoalPlanDto(g, List.of(new PlanSubGoal("sg1", "운동", null, null, null)), ms, List.of(tasks), List.of());
    }

    static Diff find(List<Diff> diffs, DraftItemKind kind, String key, DraftField field) {
        return diffs.stream().filter(d -> d.kind() == kind && d.key().equals(key) && d.field() == field).findFirst().orElse(null);
    }

    @Test
    @DisplayName("그대로 확정하면 모든 칸이 KEPT, 둘 다 빈 칸은 세지 않는다")
    void asIs() {
        GoalPlanDto ai = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                routine("t1", "헬스", 60, LocalTime.of(7, 0), Weekday.MON, Weekday.WED, Weekday.FRI));
        List<Diff> diffs = DraftPlanComparer.compare(ai, ai);
        assertThat(diffs).allMatch(d -> d.result() == DraftFieldResult.KEPT);
        assertThat(find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.HOW_TO)).isNull();   // 둘 다 비어 있음
        assertThat(find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.SUB_GOAL)).isNull();  // 세부 목표 1개면 연결은 안 셈
        assertThat(find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.DAYS).aiValue()).isEqualTo("월·수·금");
    }

    @Test
    @DisplayName("루틴 시작 시각 늦춤 · 걸리는 시간 줄임 · 요일 줄임은 방향과 크기까지 남긴다")
    void routineDirections() {
        GoalPlanDto ai = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                routine("t1", "헬스", 60, LocalTime.of(7, 0), Weekday.MON, Weekday.WED, Weekday.FRI, Weekday.SAT));
        GoalPlanDto fin = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                routine("t1", "헬스", 40, LocalTime.of(19, 30), Weekday.MON, Weekday.WED, Weekday.FRI));
        List<Diff> diffs = DraftPlanComparer.compare(ai, fin);

        Diff time = find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.START_TIME);
        assertThat(time.result()).isEqualTo(DraftFieldResult.EDITED);
        assertThat(time.direction()).isEqualTo(EditDirection.LATER);
        assertThat(time.delta()).isEqualTo(750);
        assertThat(time.finalValue()).isEqualTo("19:30");

        Diff dur = find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.DURATION);
        assertThat(dur.direction()).isEqualTo(EditDirection.LESS);
        assertThat(dur.delta()).isEqualTo(20);

        Diff days = find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.DAYS);
        assertThat(days.direction()).isEqualTo(EditDirection.LESS);
        assertThat(days.delta()).isEqualTo(1);
        assertThat(find(diffs, DraftItemKind.ROUTINE, "t1", DraftField.TITLE).result()).isEqualTo(DraftFieldResult.KEPT);
    }

    @Test
    @DisplayName("지운 항목은 칸마다 DELETED, 사용자가 더한 항목은 ITEM 한 줄 ADDED")
    void deletedAndAdded() {
        GoalPlanDto ai = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                routine("t1", "헬스", 60, LocalTime.of(7, 0), Weekday.MON),
                routine("t2", "식단 기록", 10, LocalTime.of(21, 0), Weekday.MON));
        GoalPlanDto fin = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                routine("t1", "헬스", 60, LocalTime.of(7, 0), Weekday.MON),
                routine(null, "스트레칭", 10, LocalTime.of(22, 0), Weekday.TUE));
        List<Diff> diffs = DraftPlanComparer.compare(ai, fin);

        assertThat(diffs.stream().filter(d -> d.key().equals("t2")))
                .isNotEmpty().allMatch(d -> d.result() == DraftFieldResult.DELETED && d.finalValue() == null);
        Diff added = find(diffs, DraftItemKind.ROUTINE, "new-t-1", DraftField.ITEM);
        assertThat(added.result()).isEqualTo(DraftFieldResult.ADDED);
        assertThat(added.finalValue()).isEqualTo("스트레칭");
    }

    @Test
    @DisplayName("목표 종료일을 바꿔서 자동으로 다시 맞춰진 마일스톤 날짜 · 단계 수치 · 할 일 마감은 AUTO")
    void autoAdjusted() {
        LocalDate newEnd = END.plusWeeks(3);
        PlanMilestone ai1 = new PlanMilestone("ms1", "중간", null, LocalDate.of(2027, 1, 15), new BigDecimal("67"));
        PlanMilestone ai2 = new PlanMilestone("ms2", "마지막", null, END, new BigDecimal("62"));
        PlanMilestone fin2 = new PlanMilestone("ms2", "마지막", null, newEnd, new BigDecimal("62"));
        PlanMilestone fin1 = new PlanMilestone("ms1", "중간", null, LocalDate.of(2027, 1, 15), new BigDecimal("66.5"));
        GoalPlanDto ai = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(ai1, ai2),
                oneTime("t9", "인바디 측정", "ms2", END));
        GoalPlanDto fin = plan(goal("10kg 감량", newEnd, new BigDecimal("62")), List.of(fin1, fin2),
                oneTime("t9", "인바디 측정", "ms2", newEnd));
        List<Diff> diffs = DraftPlanComparer.compare(ai, fin);

        Diff goalEnd = find(diffs, DraftItemKind.GOAL, "goal", DraftField.END_DATE);
        assertThat(goalEnd.result()).isEqualTo(DraftFieldResult.EDITED);
        assertThat(goalEnd.direction()).isEqualTo(EditDirection.LATER);
        assertThat(goalEnd.delta()).isEqualTo(21);
        assertThat(find(diffs, DraftItemKind.MILESTONE, "ms2", DraftField.DUE_DATE).result()).isEqualTo(DraftFieldResult.AUTO);
        assertThat(find(diffs, DraftItemKind.MILESTONE, "ms1", DraftField.TARGET_VALUE).result()).isEqualTo(DraftFieldResult.AUTO);
        assertThat(find(diffs, DraftItemKind.ONE_TIME, "t9", DraftField.DUE_DATE).result()).isEqualTo(DraftFieldResult.AUTO);
    }

    @Test
    @DisplayName("목표 기간을 안 바꿨는데 할 일 마감을 바꾸면 EDITED")
    void manualDueDate() {
        GoalPlanDto ai = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                oneTime("t9", "헬스장 등록", null, LocalDate.of(2026, 11, 1)));
        GoalPlanDto fin = plan(goal("10kg 감량", END, new BigDecimal("62")), List.of(),
                oneTime("t9", "헬스장 등록", null, LocalDate.of(2026, 11, 6)));
        Diff due = find(DraftPlanComparer.compare(ai, fin), DraftItemKind.ONE_TIME, "t9", DraftField.DUE_DATE);
        assertThat(due.result()).isEqualTo(DraftFieldResult.EDITED);
        assertThat(due.direction()).isEqualTo(EditDirection.LATER);
        assertThat(due.delta()).isEqualTo(5);
    }

    @Test
    @DisplayName("수치 표기는 끝자리 0 을 지우고 단위를 붙인다")
    void numberFormat() {
        assertThat(DraftPlanComparer.number(new BigDecimal("62.00"), "kg")).isEqualTo("62kg");
        assertThat(DraftPlanComparer.number(new BigDecimal("66.50"), null)).isEqualTo("66.5");
        assertThat(DraftPlanComparer.days(List.of(Weekday.FRI, Weekday.MON, Weekday.MON))).isEqualTo("월·금");
    }
}
