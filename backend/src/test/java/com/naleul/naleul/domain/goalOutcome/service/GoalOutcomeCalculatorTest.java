package com.naleul.naleul.domain.goalOutcome.service;

import com.naleul.naleul.domain.goalOutcome.entity.OutcomeSnapshot;
import com.naleul.naleul.domain.goalOutcome.enums.AutoReason;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.MetricLogs;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.RoutineDue;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator.TaskDue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 목표 결과 스냅샷 · 자동 판정 */
class GoalOutcomeCalculatorTest {

    static final LocalDate START = LocalDate.of(2026, 7, 1);
    static final LocalDate END = LocalDate.of(2026, 9, 30);

    private static List<RoutineDue> dues(long routineId, int done, int missed) {
        List<RoutineDue> list = new ArrayList<>();
        for (int i = 0; i < done; i++) list.add(new RoutineDue(routineId, START.plusDays(i), true));
        for (int i = 0; i < missed; i++) list.add(new RoutineDue(routineId, START.plusDays(done + i), false));
        return list;
    }

    @Test
    void 수치_목표는_진행률로_판정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("74"), new BigDecimal("72"),   // 6/8 = 75%
                List.of(), List.of(), Map.of());
        assertThat(s.metricPercent()).isEqualTo(75);
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.PARTIAL);
        assertThat(s.plannedDays()).isEqualTo(91);
    }

    @Test
    void 수치가_없으면_루틴_실천률로_판정하고_유지된_루틴을_센다() {
        List<RoutineDue> all = new ArrayList<>(dues(1L, 9, 1));   // 90% → 유지
        all.addAll(dues(2L, 2, 3));                                // 40% → 유지 안 됨
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END, null, null, null,
                all, List.of(), Map.of(1L, 90, 2L, 60));
        assertThat(s.routineRate()).isEqualTo(73);   // 11/15
        assertThat(s.keptRoutineCount()).isEqualTo(1);
        assertThat(s.routineCount()).isEqualTo(2);
        assertThat(s.weeklyRoutineMinutes()).isEqualTo(150);
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.PARTIAL);
    }

    @Test
    void 루틴이_없으면_Task_실천률_끝낸_날_이후_Task는_빼고() {
        List<TaskDue> tasks = List.of(
                new TaskDue(START.plusDays(1), true),
                new TaskDue(START.plusDays(2), true),
                new TaskDue(END.plusDays(10), false));   // 끝낸 뒤로 잡혀 있던 일 → 분모에서 뺀다
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END, null, null, null,
                List.of(), tasks, Map.of());
        assertThat(s.taskRate()).isEqualTo(100);
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.ACHIEVED);
    }

    @Test
    void 기록이_전혀_없으면_미달성() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END, null, null, null,
                List.of(), List.of(), Map.of());
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.NOT_ACHIEVED);
    }

    // ── 수치 미기록 (V49) ─────────────────────────────────────────

    @Test
    void 수치를_한_번도_기록하지_않으면_실천률로_판정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("80"), new BigDecimal("72"),   // 현재값 = 시작값 (0%)
                dues(1L, 9, 1), List.of(), Map.of(1L, 90), MetricLogs.NONE, List.of());
        assertThat(s.metricUnrecorded()).isTrue();
        assertThat(s.metricPercent()).isNull();
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.ACHIEVED);   // 루틴 90%
    }

    @Test
    void 마지막_기록이_오래됐고_미달이면_기록_안_함으로_본다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("78"), new BigDecimal("72"),
                dues(1L, 3, 7), List.of(), Map.of(), new MetricLogs(2, END.minusDays(40)), List.of());
        assertThat(s.metricUnrecorded()).isTrue();
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.NOT_ACHIEVED);   // 루틴 30%
    }

    @Test
    void 일찍_달성하고_기록을_멈췄으면_달성으로_인정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("72"), new BigDecimal("72"),
                List.of(), List.of(), Map.of(), new MetricLogs(5, END.minusDays(60)), List.of());
        assertThat(s.metricUnrecorded()).isFalse();
        assertThat(s.metricPercent()).isEqualTo(100);
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.ACHIEVED);
    }

    @Test
    void 최근에_기록했으면_수치로_판정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("78"), new BigDecimal("72"),   // 25%
                dues(1L, 10, 0), List.of(), Map.of(), new MetricLogs(3, END.minusDays(3)), List.of());
        assertThat(s.metricUnrecorded()).isFalse();
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.NOT_ACHIEVED);
    }

    @Test
    void 기록_현황을_모르면_예전처럼_현재값을_믿는다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("80"), new BigDecimal("72"),
                List.of(), List.of(), Map.of());
        assertThat(s.metricUnrecorded()).isFalse();
        assertThat(s.metricPercent()).isZero();
        assertThat(s.longestIdleDays()).isNull();
    }

    // ── 수치 80% 이상 자동 달성 · 안내 문구 (V63) ───────────────────

    @Test
    void 수치가_목표의_80퍼센트_이상이면_달성으로_판정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("73.5"), new BigDecimal("72"),   // 6.5/8 ≈ 81%
                List.of(), List.of(), Map.of(), new MetricLogs(4, END.minusDays(2)), List.of());
        assertThat(s.metricPercent()).isEqualTo(81);
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.ACHIEVED);
        assertThat(GoalOutcomeCalculator.reasonOf(s)).isEqualTo(AutoReason.METRIC);
        assertThat(GoalOutcomeCalculator.autoMessage(OutcomeResult.ACHIEVED, AutoReason.METRIC, 81, null, null))
                .isEqualTo("마지막 기록이 목표 수치의 81%예요. 80% 이상이라 자동으로 달성 처리했어요.");
    }

    @Test
    void 일찍_80퍼센트를_넘기고_기록을_멈췄어도_달성으로_인정한다() {
        OutcomeSnapshot s = GoalOutcomeCalculator.snapshot(START, END, END,
                new BigDecimal("80"), new BigDecimal("73"), new BigDecimal("72"),     // 7/8 ≈ 88%
                List.of(), List.of(), Map.of(), new MetricLogs(5, END.minusDays(60)), List.of());
        assertThat(s.metricUnrecorded()).isFalse();
        assertThat(GoalOutcomeCalculator.infer(s)).isEqualTo(OutcomeResult.ACHIEVED);
    }

    @Test
    void 실천률_판정_문구와_기록_없음_문구() {
        assertThat(GoalOutcomeCalculator.autoMessage(OutcomeResult.PARTIAL, AutoReason.ROUTINE_RATE, null, 62, null))
                .isEqualTo("루틴을 62% 실천했어요. 50% 이상이라 부분 달성으로 판정했어요.");
        assertThat(GoalOutcomeCalculator.autoMessage(OutcomeResult.NOT_ACHIEVED, AutoReason.NO_RECORD, null, null, null))
                .isEqualTo("기간 동안 남긴 기록이 없어 미달성으로 판정했어요.");
        assertThat(GoalOutcomeCalculator.autoMessage(null, AutoReason.METRIC, 80, null, null)).isNull();
    }

    // ── 활동 없던 가장 긴 구간 (V49) ───────────────────────────────

    @Test
    void 활동_사이_가장_긴_공백을_센다() {
        LocalDate end = LocalDate.of(2026, 7, 31);
        assertThat(GoalOutcomeCalculator.longestIdleDays(START, end,
                List.of(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 20)))).isEqualTo(17);
    }

    @Test
    void 마지막_활동부터_끝난_날까지도_공백이다() {
        LocalDate end = LocalDate.of(2026, 7, 31);
        assertThat(GoalOutcomeCalculator.longestIdleDays(START, end,
                List.of(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 5)))).isEqualTo(26);
    }

    @Test
    void 활동이_없으면_기간_전체가_공백_기간_밖_활동은_무시() {
        LocalDate end = LocalDate.of(2026, 7, 31);
        assertThat(GoalOutcomeCalculator.longestIdleDays(START, end,
                List.of(START.minusDays(3), end.plusDays(2)))).isEqualTo(30);
        assertThat(GoalOutcomeCalculator.longestIdleDays(null, end, List.of())).isNull();
    }
}
