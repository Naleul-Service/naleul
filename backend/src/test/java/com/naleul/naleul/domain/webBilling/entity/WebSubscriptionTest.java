package com.naleul.naleul.domain.webBilling.entity;

import com.naleul.naleul.domain.webBilling.enums.WebPayProvider;
import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 웹 구독 상태 변화 */
class WebSubscriptionTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 18, 0);

    @Test
    void 첫_결제면_지금부터_한_달_갱신이면_이전_기간_끝에서_이어서() {
        WebSubscription s = WebSubscription.pending(1L, WebPlan.PRO_MONTHLY, WebPayProvider.TOSS_PAYMENTS_CARD, true);
        s.paid(NOW);
        assertThat(s.getStatus()).isEqualTo(WebSubscriptionStatus.ACTIVE);
        assertThat(s.getCurrentPeriodEnd()).isEqualTo(NOW.plusMonths(1));
        assertThat(s.getNextChargeAt()).isEqualTo(NOW.plusMonths(1).minusDays(1));

        s.paid(NOW.plusMonths(1).minusDays(1));   // 기간 끝 하루 전에 자동 결제
        assertThat(s.getPeriodNo()).isEqualTo(2);
        assertThat(s.getCurrentPeriodStart()).isEqualTo(NOW.plusMonths(1));
        assertThat(s.getCurrentPeriodEnd()).isEqualTo(NOW.plusMonths(2));
    }

    @Test
    void 자동_결제가_세_번_실패하면_끝() {
        WebSubscription s = WebSubscription.pending(1L, WebPlan.PRO_MONTHLY, WebPayProvider.TOSS_PAYMENTS_CARD, true);
        s.paid(NOW);
        s.chargeFailed(NOW);
        s.chargeFailed(NOW.plusDays(1));
        assertThat(s.getStatus()).isEqualTo(WebSubscriptionStatus.PAST_DUE);
        s.chargeFailed(NOW.plusDays(2));
        assertThat(s.getStatus()).isEqualTo(WebSubscriptionStatus.EXPIRED);
        assertThat(s.getNextChargeAt()).isNull();
    }

    @Test
    void 해지해도_기간_끝까지_이용하고_해지_취소하면_다시_자동_결제() {
        WebSubscription s = WebSubscription.pending(1L, WebPlan.PRO_YEARLY, WebPayProvider.TOSS_PAYMENTS_CARD, true);
        s.paid(NOW);
        s.cancel(NOW.plusDays(10));
        assertThat(s.coversNow(NOW.plusDays(11))).isTrue();
        assertThat(s.getNextChargeAt()).isNull();
        assertThat(s.coversNow(NOW.plusYears(1).plusMinutes(1))).isFalse();

        s.resume(NOW.plusDays(12));
        assertThat(s.getStatus()).isEqualTo(WebSubscriptionStatus.ACTIVE);
        assertThat(s.getNextChargeAt()).isEqualTo(NOW.plusYears(1).minusDays(1));
    }

    @Test
    void 다시_구독하면_새_구매자_키로_초기화() {
        WebSubscription s = WebSubscription.pending(1L, WebPlan.PRO_MONTHLY, WebPayProvider.TOSS_PAYMENTS_CARD, true);
        String first = s.getCustomerKey();
        s.registerCard("bk", "신한", "1234****");
        s.paid(NOW);
        s.restart(WebPlan.PRO_YEARLY, WebPayProvider.TOSS_PAYMENTS_CARD, false);
        assertThat(s.getCustomerKey()).isNotEqualTo(first);
        assertThat(s.getBillingKey()).isNull();
        assertThat(s.getPeriodNo()).isEqualTo(0);
        assertThat(s.getPlan()).isEqualTo(WebPlan.PRO_YEARLY);
    }
}
