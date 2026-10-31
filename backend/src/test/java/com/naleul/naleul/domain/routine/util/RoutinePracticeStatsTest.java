package com.naleul.naleul.domain.routine.util;

import com.naleul.naleul.domain.task.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutinePracticeStatsTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 1);

    private static Object[] row(long routineId, long taskId, int day, TaskStatus s) {
        return new Object[]{routineId, taskId, D.plusDays(day), s};
    }

    @Test
    void 실천률과_연속은_최근부터_이어서_한_날만_센다() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(row(1, 10, 0, TaskStatus.COMPLETED));
        rows.add(row(1, 11, 1, TaskStatus.TODO));
        rows.add(row(1, 12, 2, TaskStatus.COMPLETED));
        rows.add(row(1, 13, 3, TaskStatus.COMPLETED));
        RoutinePracticeStats.Stats s = RoutinePracticeStats.of(rows).get(1L);
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.done()).isEqualTo(3);
        assertThat(s.rate()).isEqualTo(75);
        assertThat(s.streak()).isEqualTo(2);
        assertThat(s.rateIfDoneToday()).isEqualTo(80);
    }

    @Test
    void 같은_날_쪼갠_블록은_하나라도_했으면_한_날() {
        List<Object[]> rows = List.of(row(1, 10, 0, TaskStatus.TODO), row(1, 11, 0, TaskStatus.COMPLETED));
        RoutinePracticeStats.Stats s = RoutinePracticeStats.of(rows).get(1L);
        assertThat(s.total()).isEqualTo(1);
        assertThat(s.done()).isEqualTo(1);
    }

    @Test
    void 가장_높은_실천률_루틴() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(row(1, 100 + i, i, TaskStatus.COMPLETED));
        for (int i = 0; i < 4; i++) rows.add(row(2, 200 + i, i, i % 2 == 0 ? TaskStatus.COMPLETED : TaskStatus.SKIPPED));
        Map<Long, RoutinePracticeStats.Stats> all = RoutinePracticeStats.of(rows);
        assertThat(RoutinePracticeStats.isTop(1, all)).isTrue();
        assertThat(RoutinePracticeStats.isTop(2, all)).isFalse();
    }
}
