package com.naleul.naleul.domain.webBilling.support;

import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 웹 구독 결제 일정 */
class WebBillingScheduleTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 18, 0);

    @Test
    void 월간은_1개월_연간은_12개월() {
        assertThat(WebBillingSchedule.periodEnd(NOW, WebPlan.PRO_MONTHLY)).isEqualTo(LocalDateTime.of(2026, 11, 9, 18, 0));
        assertThat(WebBillingSchedule.periodEnd(NOW, WebPlan.PRO_YEARLY)).isEqualTo(LocalDateTime.of(2027, 10, 9, 18, 0));
    }

    @Test
    void 월말에_시작하면_다음_달_마지막_날까지() {
        LocalDateTime jan31 = LocalDateTime.of(2027, 1, 31, 9, 0);
        assertThat(WebBillingSchedule.periodEnd(jan31, WebPlan.PRO_MONTHLY)).isEqualTo(LocalDateTime.of(2027, 2, 28, 9, 0));
    }

    @Test
    void 자동_결제는_기간_끝_하루_전_이미_지났으면_지금() {
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 18, 0);
        assertThat(WebBillingSchedule.firstChargeAt(end, NOW)).isEqualTo(LocalDateTime.of(2026, 11, 8, 18, 0));
        assertThat(WebBillingSchedule.firstChargeAt(NOW.plusHours(3), NOW)).isEqualTo(NOW);
    }

    @Test
    void 실패하면_하루_뒤_다시_세_번째_실패면_그만() {
        assertThat(WebBillingSchedule.retryAt(1, NOW)).isEqualTo(NOW.plusDays(1));
        assertThat(WebBillingSchedule.retryAt(2, NOW)).isEqualTo(NOW.plusDays(1));
        assertThat(WebBillingSchedule.retryAt(3, NOW)).isNull();
    }

    @Test
    void 주문번호는_기간과_시도로_정해진다() {
        assertThat(WebBillingSchedule.orderId(12, 3, 1)).isEqualTo("naleul-12-3-1");
        assertThat(WebBillingSchedule.orderId(12, 3, 1)).isEqualTo(WebBillingSchedule.orderId(12, 3, 1));
    }
}
