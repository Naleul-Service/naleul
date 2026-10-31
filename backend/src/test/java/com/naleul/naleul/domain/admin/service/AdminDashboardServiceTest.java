package com.naleul.naleul.domain.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 대시보드 계산 규칙 (DB 없이) */
class AdminDashboardServiceTest {

    @Test
    void 분모가_0이면_비율은_null() {
        assertThat(AdminDashboardService.percent(0, 0)).isNull();
        assertThat(AdminDashboardService.percent(1, 3)).isEqualTo(33);
        assertThat(AdminDashboardService.percent(2, 3)).isEqualTo(67);
    }

    @Test
    void 연간_구독은_월로_나누고_월간_구독은_그대로() {
        assertThat(AdminDashboardService.monthlyPrice(4900L, 30.0)).isEqualTo(4900L);
        assertThat(AdminDashboardService.monthlyPrice(4900L, null)).isEqualTo(4900L);
        assertThat(AdminDashboardService.monthlyPrice(49000L, 365.0)).isEqualTo(4086L);
        assertThat(AdminDashboardService.monthlyPrice(null, 30.0)).isNull();
    }
}
