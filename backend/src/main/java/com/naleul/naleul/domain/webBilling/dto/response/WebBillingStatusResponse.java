package com.naleul.naleul.domain.webBilling.dto.response;

import com.naleul.naleul.domain.webBilling.entity.WebPayment;
import com.naleul.naleul.domain.webBilling.entity.WebSubscription;
import com.naleul.naleul.domain.webBilling.enums.WebPayProvider;
import com.naleul.naleul.domain.webBilling.enums.WebPaymentStatus;
import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/web-billing — 멤버십 화면
 *
 * @param configured   서버에 토스페이먼츠 키가 있는지 (없으면 카드 정기구독 "준비 중")
 * @param tossPayConfigured 서버에 토스페이 키가 있는지 (없으면 토스페이 정기구독 "준비 중")
 * @param testMode     테스트 키 — 실제 돈이 나가지 않아요
 * @param premium      지금 Pro 인지 (웹 · iOS 어디서든)
 * @param premiumUntil Pro 가 언제까지인지 (null = 기한 없음 또는 Pro 아님)
 * @param otherPremium 웹 구독이 아닌 다른 경로(iOS 앱 구독 등)로 Pro — 이때는 웹에서 새로 구독하지 못하게
 * @param subscription 웹 구독 (없거나 PENDING 이면 null)
 */
public record WebBillingStatusResponse(
        boolean configured,
        boolean tossPayConfigured,
        boolean testMode,
        boolean premium,
        LocalDateTime premiumUntil,
        boolean otherPremium,
        List<Plan> plans,
        Subscription subscription,
        List<Payment> payments
) {

    public record Plan(WebPlan plan, String name, long amount, int months) {
    }

    /** @param provider 카드 정기구독(TOSS_PAYMENTS_CARD) · 토스페이 정기구독(TOSS_PAY) */
    public record Subscription(WebPlan plan, WebPayProvider provider, WebSubscriptionStatus status,
                               LocalDateTime currentPeriodEnd, LocalDateTime nextChargeAt, String cardCompany,
                               String cardNumber, int failedAttempts, boolean testMode) {
        public static Subscription of(WebSubscription s) {
            return new Subscription(s.getPlan(), s.getProvider(), s.getStatus(), s.getCurrentPeriodEnd(), s.getNextChargeAt(),
                    s.getCardCompany(), s.getCardNumber(), s.getFailedAttempts(), s.isTestMode());
        }
    }

    public record Payment(WebPlan plan, long amount, WebPaymentStatus status, LocalDateTime at,
                          String failureMessage, boolean testMode) {
        public static Payment of(WebPayment p) {
            return new Payment(p.getPlan(), p.getAmount(), p.getStatus(),
                    p.getApprovedAt() != null ? p.getApprovedAt() : p.getCreatedAt(), p.getFailureMessage(), p.isTestMode());
        }
    }
}
