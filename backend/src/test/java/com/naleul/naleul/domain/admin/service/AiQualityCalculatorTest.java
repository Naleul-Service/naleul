package com.naleul.naleul.domain.admin.service;

import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse.OutcomeCheck;
import com.naleul.naleul.domain.admin.repository.AiQualityQueryRepository.OutcomeCheckRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 자동 판정 신뢰도 합산 (DB 없이) */
class AiQualityCalculatorTest {

    @Test
    void 판정_근거별_일치율과_모달_응답률을_합산한다() {
        OutcomeCheck c = AiQualityCalculator.outcomeCheck(List.of(
                new OutcomeCheckRow("METRIC", 10, 6, 2, 2, 8, 6, 3),
                new OutcomeCheckRow("ROUTINE_RATE", 10, 4, 1, 5, 4, 2, 0),
                new OutcomeCheckRow("NO_RECORD", 2, 0, 0, 2, 0, 0, 0)));
        assertThat(c.autoJudged()).isEqualTo(22);
        assertThat(c.responseRate()).isEqualTo(45);          // 10 / 22
        assertThat(c.matchRate()).isEqualTo(67);             // 8 / 12
        assertThat(c.metric80()).isEqualTo(3);
        assertThat(c.byReason()).hasSize(2);                 // 비교할 게 없는 근거는 빼요
        assertThat(c.byReason().get(0).matchRate()).isEqualTo(75);
    }

    @Test
    void 비교할_답이_없으면_일치율은_null() {
        OutcomeCheck c = AiQualityCalculator.outcomeCheck(List.of());
        assertThat(c.matchRate()).isNull();
        assertThat(c.responseRate()).isNull();
    }
}
