package com.naleul.naleul.domain.planner.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 18);

    private static DateTimeRange r(int h1, int m1, int h2, int m2) {
        return new DateTimeRange(D.atTime(h1, m1), D.atTime(h2, m2));
    }

    @Test
    @DisplayName("겹치는 순간의 최대 개수 (끝과 시작이 같으면 겹치지 않음)")
    void maxOverlap() {
        DateTimeRange window = r(9, 0, 12, 0);
        assertThat(Concurrency.maxOverlap(List.of(), window)).isZero();
        assertThat(Concurrency.maxOverlap(List.of(r(9, 0, 10, 0), r(10, 0, 11, 0)), window)).isEqualTo(1);
        assertThat(Concurrency.maxOverlap(List.of(r(9, 0, 10, 30), r(10, 0, 11, 0)), window)).isEqualTo(2);
        // 창 밖에서만 겹치면 세지 않는다
        assertThat(Concurrency.maxOverlap(List.of(r(8, 0, 9, 30), r(8, 30, 9, 0)), window)).isEqualTo(1);
    }
}
