package com.naleul.naleul.domain.webBilling.service;

import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.domain.webBilling.client.TossPayClient;
import com.naleul.naleul.domain.webBilling.client.TossPaymentsClient;
import com.naleul.naleul.domain.webBilling.client.TossPaymentsException;
import com.naleul.naleul.domain.webBilling.config.TossPayProperties;
import com.naleul.naleul.domain.webBilling.config.TossPaymentsProperties;
import com.naleul.naleul.domain.webBilling.dto.request.TossPayPrepareRequest;
import com.naleul.naleul.domain.webBilling.dto.request.WebBillingConfirmRequest;
import com.naleul.naleul.domain.webBilling.dto.response.TossPayPrepareResponse;
import com.naleul.naleul.domain.webBilling.dto.response.WebBillingPrepareResponse;
import com.naleul.naleul.domain.webBilling.dto.response.WebBillingStatusResponse;
import com.naleul.naleul.domain.webBilling.entity.WebPayment;
import com.naleul.naleul.domain.webBilling.entity.WebSubscription;
import com.naleul.naleul.domain.webBilling.enums.WebPayProvider;
import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;
import com.naleul.naleul.domain.webBilling.exception.WebBillingPaymentException;
import com.naleul.naleul.domain.webBilling.repository.WebPaymentRepository;
import com.naleul.naleul.domain.webBilling.repository.WebSubscriptionRepository;
import com.naleul.naleul.domain.webBilling.support.WebBillingSchedule;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 웹 Pro 정기구독 — 결제하는 곳이 두 가지 (둘 다 매달 · 매년 자동결제, 해지하면 기간 끝까지 Pro).
 *
 * [카드 정기구독 · 토스페이먼츠]
 *   prepare(구매자 키) → 웹이 토스 카드 등록 창 → successUrl 로 authKey → confirm(billingKey 발급 + 첫 결제 + Pro)
 * [토스페이 정기구독 · pay.toss.im]
 *   prepareTossPay(빌링키 생성 → 인증 주소) → 웹이 토스 앱 인증으로 이동 → returnSuccessUrl 로 복귀 →
 *   confirmTossPay(빌링키 ACTIVE 확인 + 첫 결제 + Pro). 사용자가 토스 앱에서 해지하면 콜백(REMOVED) → 해지 처리
 * [공통]
 *   스케줄러가 기간 끝나기 하루 전 자동 결제(renew) — 결제하는 곳만 다르고 일정 · 재시도 · 해지는 같다
 *
 * 시각은 JVM 시간대 LocalDateTime.now() — Pro 만료 판정(User.isCurrentlyPremium · PremiumExpirationScheduler)과 같은 기준.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebBillingService {

    private final WebSubscriptionRepository subscriptionRepository;
    private final WebPaymentRepository paymentRepository;
    private final TossPaymentsClient tossClient;
    private final TossPaymentsProperties properties;
    private final TossPayClient tossPayClient;
    private final TossPayProperties tossPayProperties;
    private final UserFinder userFinder;

    // ── 조회 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WebBillingStatusResponse status(Long userId) {
        User user = userFinder.findUser(userId);
        LocalDateTime now = LocalDateTime.now();
        WebSubscription sub = subscriptionRepository.findByUserId(userId).orElse(null);
        boolean webCovers = sub != null && sub.coversNow(now);
        boolean shown = sub != null && sub.getStatus() != WebSubscriptionStatus.PENDING && sub.getPeriodNo() > 0;
        return new WebBillingStatusResponse(
                properties.configured(),
                tossPayProperties.configured(),
                properties.testMode() || tossPayProperties.testMode(),
                user.isCurrentlyPremium(),
                user.isCurrentlyPremium() ? user.getPremiumExpiresAt() : null,
                user.isCurrentlyPremium() && !webCovers,
                plans(),
                shown ? WebBillingStatusResponse.Subscription.of(sub) : null,
                paymentRepository.findTop12ByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(WebBillingStatusResponse.Payment::of).toList());
    }

    public List<WebBillingStatusResponse.Plan> plans() {
        return Arrays.stream(WebPlan.values())
                .map(p -> new WebBillingStatusResponse.Plan(p, p.getOrderName(), price(p), p.getMonths()))
                .toList();
    }

    long price(WebPlan plan) {
        return plan == WebPlan.PRO_YEARLY ? properties.yearlyPrice() : properties.monthlyPrice();
    }

    // ── 구독 시작 ────────────────────────────────────────────────

    /** 카드 등록 창을 열기 전 — 구매자 키(customerKey)를 만들어 둔다 */
    @Transactional
    public WebBillingPrepareResponse prepare(Long userId, WebPlan plan) {
        requireConfigured();
        User user = userFinder.findUser(userId);
        WebSubscription sub = startPending(userId, user, plan, WebPayProvider.TOSS_PAYMENTS_CARD, properties.testMode());
        return new WebBillingPrepareResponse(sub.getCustomerKey(), plan.getOrderName(), price(plan),
                user.getUserName(), properties.testMode());
    }

    /** 토스페이 — 빌링키를 만들고 토스 앱 인증 주소를 돌려준다 (구매자 키 = 토스페이 userId) */
    @Transactional
    public TossPayPrepareResponse prepareTossPay(Long userId, TossPayPrepareRequest request) {
        if (!tossPayProperties.configured()) throw new CustomException(ErrorCode.WEB_BILLING_TOSS_PAY_NOT_CONFIGURED);
        String origin = request.returnOrigin().replaceAll("/+$", "");
        // 인증 후 돌아갈 주소는 허용 목록에 있는 origin 만 (다른 사이트로 보내지 않게)
        if (!tossPayProperties.returnOrigins().contains(origin)) {
            throw new CustomException(ErrorCode.WEB_BILLING_INVALID_RETURN_URL);
        }
        User user = userFinder.findUser(userId);
        WebPlan plan = request.plan();
        WebSubscription sub = startPending(userId, user, plan, WebPayProvider.TOSS_PAY, tossPayProperties.testMode());
        try {
            TossPayClient.Created created = tossPayClient.createBillingKey(sub.getCustomerKey(), plan.getOrderName(),
                    origin + "/settings/membership/success?method=toss_pay",
                    origin + "/settings/membership/fail?method=toss_pay");
            sub.pendingTossPay(created.billingKey());
            return new TossPayPrepareResponse(created.checkoutUri(), plan.getOrderName(), price(plan),
                    tossPayProperties.testMode());
        } catch (TossPaymentsException e) {
            throw new WebBillingPaymentException(e.getMessage());
        }
    }

    /** 결제 창을 열기 전 공통 — 이미 Pro 면 막고, 구독 행을 PENDING 으로 (다시) 만든다 */
    private WebSubscription startPending(Long userId, User user, WebPlan plan, WebPayProvider provider, boolean testMode) {
        LocalDateTime now = LocalDateTime.now();
        WebSubscription sub = subscriptionRepository.findByUserIdForUpdate(userId).orElse(null);
        if (sub != null && sub.coversNow(now)) throw new CustomException(ErrorCode.WEB_BILLING_ALREADY_SUBSCRIBED);
        // iOS 앱 구독 등으로 이미 Pro → 두 번 결제되지 않게 막는다
        if (user.hasPaidPremium()) throw new CustomException(ErrorCode.WEB_BILLING_IAP_ACTIVE);   // 운영진 플랜 바꿔 보기와 상관없이 실제 결제로
        if (sub == null) {
            return subscriptionRepository.save(WebSubscription.pending(userId, plan, provider, testMode));
        }
        sub.restart(plan, provider, testMode);
        return sub;
    }

    /**
     * 카드 등록 성공 후 — billingKey 발급 + 첫 결제 + Pro.
     * 결제 실패도 기록이 남아야 해서 WebBillingPaymentException 은 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = WebBillingPaymentException.class)
    public WebBillingStatusResponse confirm(Long userId, WebBillingConfirmRequest request) {
        requireConfigured();
        User user = userFinder.findUser(userId);
        WebSubscription sub = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION));
        if (sub.isTossPay() || !sub.getCustomerKey().equals(request.customerKey())) {
            throw new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION);
        }
        if (sub.getStatus() != WebSubscriptionStatus.PENDING) {
            // 새로고침 등으로 같은 요청이 다시 와도 이미 시작됐으면 그대로 결과만 돌려준다
            if (sub.coversNow(LocalDateTime.now())) return status(userId);
            throw new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION);
        }

        if (sub.getBillingKey() == null) {
            try {
                TossPaymentsClient.BillingKey key = tossClient.issueBillingKey(request.authKey(), sub.getCustomerKey());
                sub.registerCard(key.billingKey(), key.cardCompany(), key.cardNumber());
            } catch (TossPaymentsException e) {
                // 결제사 오류 코드도 같이 보여 줘요 (원인 확인용, 2026-10-28)
                log.warn("[WebBilling] 카드 등록 실패 userId={} code={} message={}", userId, e.getCode(), e.getMessage());
                throw new WebBillingPaymentException("카드를 등록하지 못했어요. " + e.getMessage() + " (" + e.getCode() + ")");
            }
        }

        firstCharge(sub, user);
        return status(userId);
    }

    /**
     * 토스 앱 인증을 마치고 돌아온 뒤 — 빌링키가 ACTIVE 인지 토스페이에 확인하고 첫 결제 + Pro.
     * (돌아온 주소에는 아무 값도 없어서 서버가 직접 확인한다. 콜백보다 먼저 와도 된다)
     */
    @Transactional(noRollbackFor = WebBillingPaymentException.class)
    public WebBillingStatusResponse confirmTossPay(Long userId) {
        if (!tossPayProperties.configured()) throw new CustomException(ErrorCode.WEB_BILLING_TOSS_PAY_NOT_CONFIGURED);
        User user = userFinder.findUser(userId);
        WebSubscription sub = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION));
        if (!sub.isTossPay()) throw new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION);
        if (sub.getStatus() != WebSubscriptionStatus.PENDING) {
            if (sub.coversNow(LocalDateTime.now())) return status(userId);   // 새로고침 → 결과만
            throw new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION);
        }
        if (sub.getBillingKey() == null) throw new CustomException(ErrorCode.WEB_BILLING_INVALID_SESSION);

        if (sub.getCardNumber() == null && sub.getCardCompany() == null) {
            TossPayClient.KeyStatus key;
            try {
                key = tossPayClient.status(sub.getCustomerKey());
            } catch (TossPaymentsException e) {
                throw new WebBillingPaymentException(e.getMessage());
            }
            if (!key.active() || !sub.getBillingKey().equals(key.billingKey())) {
                throw new CustomException(ErrorCode.WEB_BILLING_TOSS_PAY_NOT_AUTHORIZED);
            }
            sub.registerCard(key.billingKey(), key.label(), key.number());
        }
        firstCharge(sub, user);
        return status(userId);
    }

    /** 첫 결제 (카드 · 토스페이 공통). 실패해도 기록은 남기고 다른 시도 번호로 다시 할 수 있게 */
    private void firstCharge(WebSubscription sub, User user) {
        long amount = price(sub.getPlan());
        String orderId = WebBillingSchedule.orderId(sub.getWebSubscriptionId(), sub.getPeriodNo() + 1, sub.getFailedAttempts());
        try {
            TossPaymentsClient.Charge charge = chargeFor(sub, user, amount, orderId);
            LocalDateTime now = LocalDateTime.now();
            paymentRepository.save(WebPayment.done(sub, orderId, amount, charge.paymentKey(), charge.approvedAt()));
            sub.paid(now);
            user.extendPremium(sub.getCurrentPeriodEnd());
            log.info("[WebBilling] 구독 시작 userId={} provider={} plan={} until={} test={}", sub.getUserId(),
                    sub.getProvider(), sub.getPlan(), sub.getCurrentPeriodEnd(), sub.isTestMode());
        } catch (TossPaymentsException e) {
            if (!e.isRetryable()) {
                paymentRepository.save(WebPayment.failed(sub, orderId, amount, e.getCode(), e.getMessage()));
                sub.firstChargeFailed();   // 다음 시도는 다른 주문번호로
            }
            throw new WebBillingPaymentException(e.getMessage());
        }
    }

    /** 결제하는 곳에 맞춰 결제 — 결과 모양은 토스페이먼츠 Charge 로 맞춘다 */
    private TossPaymentsClient.Charge chargeFor(WebSubscription sub, User user, long amount, String orderId) {
        if (sub.isTossPay()) {
            TossPayClient.Bill bill = tossPayClient.bill(sub.getBillingKey(), orderId, sub.getPlan().getOrderName(), amount);
            return new TossPaymentsClient.Charge(bill.payToken(), "DONE", bill.approvedAt());
        }
        return tossClient.charge(sub.getBillingKey(), sub.getCustomerKey(), amount, orderId,
                sub.getPlan().getOrderName(), user.getUserName(), user.getUserEmail());
    }

    /**
     * 토스페이 콜백 — 빌링키가 활성화(ACTIVATED)되거나 삭제(REMOVED)됐을 때.
     * 본문은 서명이 없어 그대로 믿지 않고, 빌링키 상태를 토스페이에 다시 물어본 뒤에만 반영한다.
     *  - ACTIVATED: 할 일 없음 (첫 결제는 사용자가 돌아온 뒤 confirmTossPay 에서)
     *  - REMOVED: 사용자가 토스 앱에서 자동결제를 해지 → 이번 기간 끝까지 Pro, 더 결제하지 않음
     */
    @Transactional
    public void handleTossPayCallback(String action, String billingKey) {
        if (billingKey == null || billingKey.isBlank()) return;
        if (!"REMOVED".equals(action)) {
            log.info("[TossPay] 콜백 action={}", action);
            return;
        }
        WebSubscription sub = subscriptionRepository.findByBillingKeyForUpdate(billingKey).orElse(null);
        if (sub == null || !sub.isTossPay()) return;
        try {
            if (!tossPayClient.status(sub.getCustomerKey()).removed()) return;   // 실제로 지워졌을 때만
        } catch (TossPaymentsException e) {
            log.warn("[TossPay] 콜백 확인 실패 subscriptionId={} code={}", sub.getWebSubscriptionId(), e.getCode());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (sub.getStatus() == WebSubscriptionStatus.ACTIVE || sub.getStatus() == WebSubscriptionStatus.PAST_DUE) {
            sub.cancel(now);
            log.info("[TossPay] 토스 앱에서 해지 → 기간 끝까지 이용 userId={} until={}", sub.getUserId(),
                    sub.getCurrentPeriodEnd());
        } else if (sub.getStatus() == WebSubscriptionStatus.PENDING) {
            sub.expire();
        }
    }

    // ── 해지 · 해지 취소 ──────────────────────────────────────────

    /** 해지 — 이번 기간 끝까지 Pro, 더 결제하지 않음 (환불은 하지 않음) */
    @Transactional
    public WebBillingStatusResponse cancel(Long userId) {
        WebSubscription sub = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.WEB_BILLING_SUBSCRIPTION_NOT_FOUND));
        if (sub.getStatus() != WebSubscriptionStatus.ACTIVE && sub.getStatus() != WebSubscriptionStatus.PAST_DUE) {
            throw new CustomException(ErrorCode.WEB_BILLING_SUBSCRIPTION_NOT_FOUND);
        }
        sub.cancel(LocalDateTime.now());
        log.info("[WebBilling] 해지 userId={} until={}", userId, sub.getCurrentPeriodEnd());
        return status(userId);
    }

    @Transactional
    public WebBillingStatusResponse resume(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        WebSubscription sub = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.WEB_BILLING_SUBSCRIPTION_NOT_FOUND));
        if (sub.getStatus() != WebSubscriptionStatus.CANCELED || !sub.coversNow(now)) {
            throw new CustomException(ErrorCode.WEB_BILLING_SUBSCRIPTION_NOT_FOUND);
        }
        sub.resume(now);
        return status(userId);
    }

    // ── 자동 결제 (스케줄러) ───────────────────────────────────────

    /** 결제할 차례인 구독 하나를 결제. 행을 잠그고 다시 확인해서 서버 두 대가 동시에 돌아도 한 번만 */
    @Transactional
    public void renew(Long subscriptionId) {
        LocalDateTime now = LocalDateTime.now();
        WebSubscription sub = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (sub == null || sub.getNextChargeAt() == null || sub.getNextChargeAt().isAfter(now)) return;
        if (sub.getStatus() != WebSubscriptionStatus.ACTIVE && sub.getStatus() != WebSubscriptionStatus.PAST_DUE) return;
        if (sub.getBillingKey() == null) {
            sub.expire();
            return;
        }

        User user = userFinder.findUser(sub.getUserId());
        long amount = price(sub.getPlan());
        String orderId = WebBillingSchedule.orderId(sub.getWebSubscriptionId(), sub.getPeriodNo() + 1, sub.getFailedAttempts());
        try {
            TossPaymentsClient.Charge charge = chargeFor(sub, user, amount, orderId);
            if (!paymentRepository.existsByOrderId(orderId)) {
                paymentRepository.save(WebPayment.done(sub, orderId, amount, charge.paymentKey(), charge.approvedAt()));
            }
            sub.paid(now);
            user.extendPremium(sub.getCurrentPeriodEnd());
            log.info("[WebBilling] 자동 결제 성공 userId={} plan={} until={}", sub.getUserId(), sub.getPlan(), sub.getCurrentPeriodEnd());
        } catch (TossPaymentsException e) {
            if (e.isRetryable()) {
                sub.retryLater(now);
                log.warn("[WebBilling] 자동 결제 응답 없음 → 1시간 뒤 같은 주문번호로 userId={}", sub.getUserId());
                return;
            }
            if (!paymentRepository.existsByOrderId(orderId)) {
                paymentRepository.save(WebPayment.failed(sub, orderId, amount, e.getCode(), e.getMessage()));
            }
            sub.chargeFailed(now);
            if (sub.getStatus() == WebSubscriptionStatus.EXPIRED) removeTossPayKey(sub);
            log.warn("[WebBilling] 자동 결제 실패 userId={} attempt={} code={} → {}", sub.getUserId(),
                    sub.getFailedAttempts(), e.getCode(), sub.getStatus());
        }
    }

    /** 해지 신청 후 기간이 끝난 구독 → 끝 (Pro 는 자정 만료 배치가 premiumExpiresAt 기준으로 끈다) */
    @Transactional
    public void expireIfEnded(Long subscriptionId) {
        subscriptionRepository.findByIdForUpdate(subscriptionId)
                .filter(s -> s.getStatus() == WebSubscriptionStatus.CANCELED
                        && s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(LocalDateTime.now()))
                .ifPresent(s -> {
                    s.expire();
                    removeTossPayKey(s);
                });
    }

    /** 끝난 토스페이 구독은 빌링키도 지운다 (토스 앱 '자동결제' 목록에서 사라지게). 실패해도 다시 결제하지는 않음 */
    private void removeTossPayKey(WebSubscription sub) {
        if (!sub.isTossPay() || sub.getBillingKey() == null) return;
        try {
            tossPayClient.removeBillingKey(sub.getBillingKey());
        } catch (TossPaymentsException e) {
            log.warn("[TossPay] 빌링키 삭제 실패 subscriptionId={} code={}", sub.getWebSubscriptionId(), e.getCode());
        }
    }

    private void requireConfigured() {
        if (!properties.configured()) throw new CustomException(ErrorCode.WEB_BILLING_NOT_CONFIGURED);
    }
}
