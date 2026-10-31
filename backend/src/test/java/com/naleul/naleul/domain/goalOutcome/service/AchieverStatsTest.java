package com.naleul.naleul.domain.goalOutcome.service;

import com.naleul.naleul.domain.goalCreation.personal.AchieverSummary;
import com.naleul.naleul.domain.goalOutcome.enums.OutcomeResult;
import com.naleul.naleul.domain.goalOutcome.service.AchieverStats.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 달성자 익명 집계 — 표본이 적으면 만들지 않는다 */
class AchieverStatsTest {

    private static List<Row> rows(int achieved, int others) {
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < achieved; i++) list.add(new Row(OutcomeResult.ACHIEVED, 70 + i, 120 + i * 2, 2 + i % 3));
        for (int i = 0; i < others; i++) list.add(new Row(OutcomeResult.NOT_ACHIEVED, 30, 400, 6));
        return list;
    }

    @Test
    void 달성자가_30명_미만이면_집계하지_않는다() {
        assertThat(AchieverStats.summarize("다이어트", rows(29, 100)).isPresent()).isFalse();
    }

    @Test
    void 달성자_중앙값만_낸다() {
        Optional<AchieverSummary> s = AchieverStats.summarize("다이어트", rows(31, 9));
        assertThat(s.isPresent()).isTrue();
        assertThat(s.get().achievedCount()).isEqualTo(31);
        assertThat(s.get().totalCount()).isEqualTo(40);
        assertThat(s.get().medianDays()).isEqualTo(85);              // 70~100 의 가운데
        assertThat(s.get().medianWeeklyRoutineMinutes()).isEqualTo(150);
        assertThat(s.get().achievedRatePercent()).isEqualTo(78);
    }

    @Test
    void 중앙값_짝수개는_가운데_두_값의_평균() {
        assertThat(AchieverStats.median(new int[]{10, 20, 30, 40})).isEqualTo(25);
        assertThat(AchieverStats.median(new int[]{})).isEqualTo(0);
    }
}
