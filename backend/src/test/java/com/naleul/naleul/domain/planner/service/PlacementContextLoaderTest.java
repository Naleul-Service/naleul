package com.naleul.naleul.domain.planner.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementContextLoaderTest {

    @Test
    @DisplayName("루틴 소요 시간: 실제 중앙값이 20% 이상 길면 늘리되(5분 단위 올림) 최대 1.5배")
    void adjust() {
        assertThat(PlacementContextLoader.adjust(30, List.of(40L, 42L, 45L))).isEqualTo(45);   // 중앙값 42 → 45
        assertThat(PlacementContextLoader.adjust(30, List.of(32L, 33L, 34L))).isNull();         // 10% 차이 → 그대로
        assertThat(PlacementContextLoader.adjust(30, List.of(90L, 90L, 90L))).isEqualTo(45);    // 최대 1.5배 = 45분
        assertThat(PlacementContextLoader.adjust(30, List.of(60L, 60L))).isNull();              // 기록 3개 미만
        assertThat(PlacementContextLoader.adjust(60, List.of(80L, 75L, 90L, 85L))).isEqualTo(85); // 중앙값 82.5 → 85
    }
}
