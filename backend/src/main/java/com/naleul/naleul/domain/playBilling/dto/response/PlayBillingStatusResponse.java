package com.naleul.naleul.domain.playBilling.dto.response;

import com.naleul.naleul.domain.playBilling.entity.PlaySubscription;

import java.time.LocalDateTime;

/**
 * Android 멤버십 화면 — 지금 Pro 인지, Play 구독이 있는지
 * @param premium      지금 Pro 인지 (웹 · iOS · Play 어디서든)
 * @param premiumUntil Pro 가 언제까지인지
 * @param subscription 지금 Pro 근거인 Play 구독 (없으면 null → 웹 · iOS · 운영진 Pro)
 */
public record PlayBillingStatusResponse(boolean configured, boolean premium, LocalDateTime premiumUntil,
                                        Subscription subscription) {

    /** @param canceled 자동 갱신을 끔 (기간 끝까지 이용) */
    public record Subscription(String productId, LocalDateTime expiryTime, boolean autoRenewing, boolean canceled,
                               boolean inGracePeriod, boolean testPurchase) {
        public static Subscription of(PlaySubscription s) {
            return new Subscription(s.getProductId(), s.getExpiryTime(), s.isAutoRenewing(),
                    "SUBSCRIPTION_STATE_CANCELED".equals(s.getState()),
                    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD".equals(s.getState()), s.isTestPurchase());
        }
    }
}
