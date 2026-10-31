package com.naleul.naleul.domain.brainDump.support;

import com.naleul.naleul.domain.brainDump.enums.DateType;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BrainDumpRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    @DisplayName("마감일만 있는 일: 계획형은 오늘부터, 즉흥형은 마감 전날부터 (마감이 내일이면 오늘)")
    void firstTryDate() {
        LocalDate fri = TODAY.plusDays(2);
        assertThat(BrainDumpRules.firstTryDate(DateType.DUE, null, fri, PlanningStyle.PLANNER, TODAY)).isEqualTo(TODAY);
        assertThat(BrainDumpRules.firstTryDate(DateType.DUE, null, fri, null, TODAY)).isEqualTo(TODAY);
        assertThat(BrainDumpRules.firstTryDate(DateType.DUE, null, fri, PlanningStyle.SPONTANEOUS, TODAY)).isEqualTo(fri.minusDays(1));
        assertThat(BrainDumpRules.firstTryDate(DateType.DUE, null, TODAY.plusDays(1), PlanningStyle.SPONTANEOUS, TODAY)).isEqualTo(TODAY);
        assertThat(BrainDumpRules.firstTryDate(DateType.ON, TODAY.plusDays(3), null, PlanningStyle.PLANNER, TODAY)).isEqualTo(TODAY.plusDays(3));
        assertThat(BrainDumpRules.firstTryDate(DateType.ON, TODAY.minusDays(1), null, null, TODAY)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("사용자가 정한 시각: 종료 없으면 소요 시간, 종료가 더 이르면 다음 날까지")
    void explicitRange() {
        DateTimeRange a = BrainDumpRules.explicitRange(TODAY, LocalTime.of(15, 0), null, 45);
        assertThat(a.end()).isEqualTo(TODAY.atTime(15, 45));
        DateTimeRange b = BrainDumpRules.explicitRange(TODAY, LocalTime.of(23, 0), LocalTime.of(1, 0), 30);
        assertThat(b.end()).isEqualTo(TODAY.plusDays(1).atTime(1, 0));
    }
}
