package com.naleul.naleul.domain.planner.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DayEndTriggerTest {

    static final LocalDate D = LocalDate.of(2026, 10, 13);
    static final DayEndTrigger T = DayEndTrigger.DEFAULT;

    @Test
    void 마지막_일정이_끝나고_10분_뒤() {
        assertThat(T.triggerAt(D, D.atTime(22, 30), 3, 1)).isEqualTo(D.atTime(23, 30));
    }

    @Test
    void 일찍_끝나도_18시_전에는_하지_않는다() {
        assertThat(T.triggerAt(D, D.atTime(15, 0), 2, 1)).isEqualTo(D.atTime(18, 0));
    }

    @Test
    void 다_끝냈으면_18시에_바로() {
        assertThat(T.triggerAt(D, D.atTime(21, 0), 2, 0)).isEqualTo(D.atTime(18, 0));
    }

    @Test
    void 시간_정한_일정이_없으면_22시() {
        assertThat(T.triggerAt(D, null, 2, 2)).isEqualTo(D.atTime(22, 0));
        assertThat(T.triggerAt(D, null, 0, 0)).isEqualTo(D.atTime(22, 0));
    }

    @Test
    void 자정을_넘기는_일정이_있어도_23시50분엔_한다() {
        LocalDateTime nextDay = D.plusDays(1).atTime(1, 0);
        assertThat(T.triggerAt(D, nextDay, 1, 1)).isEqualTo(D.atTime(23, 50));
    }
}
