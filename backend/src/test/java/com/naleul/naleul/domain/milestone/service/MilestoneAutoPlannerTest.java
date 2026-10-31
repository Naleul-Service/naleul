package com.naleul.naleul.domain.milestone.service;

import com.naleul.naleul.domain.milestone.entity.Milestone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 점검 시점 자동 보정 (DB 없이) */
class MilestoneAutoPlannerTest {

    static final LocalDate START = LocalDate.of(2026, 10, 1);
    static final LocalDate END = LocalDate.of(2026, 12, 30);   // 90일

    private static Milestone ms(String title, LocalDate due, String value) {
        return Milestone.create(null, title, null, due, value == null ? null : new BigDecimal(value), 0);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void 수치는_시작값에서_목표값까지_날짜_비율대로() {
        Milestone a = ms("1", START.plusDays(30), "99");
        Milestone b = ms("2", START.plusDays(60), null);
        Milestone c = ms("3", END, "70");

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("71"), List.of(a, b, c), false);

        assertThat(a.getTargetValue()).isEqualByComparingTo("77");
        assertThat(b.getTargetValue()).isEqualByComparingTo("74");
        assertThat(c.getTargetValue()).isEqualByComparingTo("71");
    }

    @Test
    void 마지막_시점은_종료일과_목표값으로_맞춘다() {
        Milestone a = ms("1", START.plusDays(30), null);
        Milestone last = ms("2", START.plusDays(70), null);

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("72"), List.of(a, last), false);

        assertThat(last.getDueDate()).isEqualTo(END);
        assertThat(last.getTargetValue()).isEqualByComparingTo("72");
    }

    @Test
    void 직접_정한_값은_유지하고_목표가_바뀌면_다시_자동() {
        Milestone a = ms("1", START.plusDays(30), null);
        a.setManualTargetValue(bd("75"));
        Milestone last = ms("2", END, null);

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("72"), List.of(a, last), false);
        assertThat(a.getTargetValue()).isEqualByComparingTo("75");
        assertThat(a.isTargetValueManual()).isTrue();

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("74"), List.of(a, last), true);
        assertThat(a.isTargetValueManual()).isFalse();
        assertThat(a.getTargetValue()).isEqualByComparingTo("78");
    }

    @Test
    void 범위를_벗어난_직접_값은_자동으로_되돌린다() {
        Milestone a = ms("1", START.plusDays(45), null);
        a.setManualTargetValue(bd("90"));   // 80 → 72 범위 밖
        Milestone last = ms("2", END, null);

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("72"), List.of(a, last), false);

        assertThat(a.isTargetValueManual()).isFalse();
        assertThat(a.getTargetValue()).isEqualByComparingTo("76");
    }

    @Test
    void 날짜는_기간_안으로_겹치면_하루씩_뒤로() {
        Milestone a = ms("1", START.minusDays(3), null);
        Milestone b = ms("2", START.minusDays(1), null);
        Milestone last = ms("3", END.plusDays(10), null);

        MilestoneAutoPlanner.plan(START, END, null, null, List.of(a, b, last), false);

        assertThat(a.getDueDate()).isEqualTo(START.plusDays(1));
        assertThat(b.getDueDate()).isEqualTo(START.plusDays(2));
        assertThat(last.getDueDate()).isEqualTo(END);
        assertThat(a.getTargetValue()).isNull();   // 수치 없는 목표는 수치를 만들지 않는다
    }

    @Test
    void 달성한_시점은_건드리지_않는다() {
        Milestone done = ms("1", START.plusDays(30), "79");
        done.achieve(LocalDateTime.of(2026, 10, 31, 9, 0));
        Milestone last = ms("2", END, null);

        MilestoneAutoPlanner.plan(START, END, bd("80"), bd("72"), List.of(done, last), true);

        assertThat(done.getTargetValue()).isEqualByComparingTo("79");
    }

    @Test
    void 소수_자리는_입력값을_따른다() {
        assertThat(MilestoneAutoPlanner.scaleOf(bd("80"), bd("72"))).isZero();
        assertThat(MilestoneAutoPlanner.scaleOf(bd("80.5"), bd("72"))).isEqualTo(1);
        assertThat(MilestoneAutoPlanner.scaleOf(bd("80.00"), bd("72.0"))).isZero();
    }
}
