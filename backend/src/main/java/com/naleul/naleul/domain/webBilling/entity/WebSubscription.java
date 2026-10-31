package com.naleul.naleul.domain.webBilling.entity;

import com.naleul.naleul.domain.webBilling.enums.WebPayProvider;
import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import com.naleul.naleul.domain.webBilling.enums.WebSubscriptionStatus;
import com.naleul.naleul.domain.webBilling.support.WebBillingSchedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 웹 구독 (V51) — 사용자당 1행. 다시 구독하면 같은 행을 새로 채운다.
 *
 * billingKey: 토스가 카드 등록 후 준 자동결제 키. 이 키 + 서버 시크릿 키가 있어야 결제할 수 있고,
 * 토스에서 다시 조회할 수 없어서 잃어버리면 카드를 다시 등록해야 한다. 응답 · 로그에 절대 내보내지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "web_subscription",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ws_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_ws_customer_key", columnNames = "customer_key")
        })
public class WebSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long webSubscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebSubscriptionStatus status;

    /** 결제하는 곳 — 카드 정기구독(토스페이먼츠) · 토스페이 정기구독 (V61) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPayProvider provider = WebPayProvider.TOSS_PAYMENTS_CARD;

    /** 구매자 id (결제 창을 열 때마다 새로 만든다). 토스페이는 이 값을 userId 로 쓴다 */
    @Column(name = "customer_key", nullable = false, length = 64)
    private String customerKey;

    @Column(length = 200)
    private String billingKey;

    /** 카드사 (토스페이 토스머니면 은행 이름 또는 "토스머니") */
    @Column(length = 30)
    private String cardCompany;

    /** 마스킹된 카드번호 (예: 43301234****123*) · 토스머니면 마스킹된 계좌번호 */
    @Column(length = 30)
    private String cardNumber;

    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextChargeAt;

    /** 몇 번째 기간인지 (첫 결제 = 1). 주문번호에 쓴다 */
    @Column(nullable = false)
    private int periodNo;

    /** 이번 기간 결제 실패 횟수 */
    @Column(nullable = false)
    private int failedAttempts;

    private LocalDateTime canceledAt;

    @Column(nullable = false)
    private boolean testMode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static WebSubscription pending(Long userId, WebPlan plan, WebPayProvider provider, boolean testMode) {
        WebSubscription s = new WebSubscription();
        s.userId = userId;
        s.restart(plan, provider, testMode);
        return s;
    }

    /** 결제 창을 (다시) 열 때 — 이전 구독 정보는 지우고 새 구매자 키를 만든다 */
    public void restart(WebPlan plan, WebPayProvider provider, boolean testMode) {
        this.plan = plan;
        this.provider = provider;
        this.status = WebSubscriptionStatus.PENDING;
        this.customerKey = UUID.randomUUID().toString();
        this.billingKey = null;
        this.cardCompany = null;
        this.cardNumber = null;
        this.currentPeriodStart = null;
        this.currentPeriodEnd = null;
        this.nextChargeAt = null;
        this.periodNo = 0;
        this.failedAttempts = 0;
        this.canceledAt = null;
        this.testMode = testMode;
    }

    /** 토스페이 — 빌링키는 인증 전에 먼저 받고, 결제수단은 인증이 끝난 뒤 registerCard 로 채운다 */
    public void pendingTossPay(String billingKey) {
        this.billingKey = billingKey;
    }

    public boolean isTossPay() {
        return provider == WebPayProvider.TOSS_PAY;
    }

    public void registerCard(String billingKey, String cardCompany, String cardNumber) {
        this.billingKey = billingKey;
        this.cardCompany = cardCompany;
        this.cardNumber = cardNumber;
    }

    /** 결제 성공 → 다음 기간 시작. 첫 결제면 지금부터, 갱신이면 이전 기간 끝에서 이어서 */
    public void paid(LocalDateTime now) {
        LocalDateTime start = currentPeriodEnd != null && currentPeriodEnd.isAfter(now) ? currentPeriodEnd : now;
        this.periodNo += 1;
        this.currentPeriodStart = start;
        this.currentPeriodEnd = WebBillingSchedule.periodEnd(start, plan);
        this.nextChargeAt = WebBillingSchedule.firstChargeAt(currentPeriodEnd, now);
        this.failedAttempts = 0;
        this.status = WebSubscriptionStatus.ACTIVE;
    }

    /** 자동 결제 실패 → 다시 시도하거나, 더 시도하지 않으면 끝 */
    public void chargeFailed(LocalDateTime now) {
        this.failedAttempts += 1;
        LocalDateTime retry = WebBillingSchedule.retryAt(failedAttempts, now);
        this.nextChargeAt = retry;
        this.status = retry != null ? WebSubscriptionStatus.PAST_DUE : WebSubscriptionStatus.EXPIRED;
    }

    /** 첫 결제 실패 (카드는 등록됨) — 다른 시도 번호로 다시 결제할 수 있게 */
    public void firstChargeFailed() {
        this.failedAttempts += 1;
    }

    /** 결제사 응답을 못 받음 → 결제됐는지 모르니 1시간 뒤 같은 주문번호로 다시 (토스 멱등키라 두 번 결제되지 않음) */
    public void retryLater(LocalDateTime now) {
        this.nextChargeAt = now.plusHours(1);
    }

    /** 해지 신청 — 이번 기간 끝까지 이용 */
    public void cancel(LocalDateTime now) {
        this.status = WebSubscriptionStatus.CANCELED;
        this.canceledAt = now;
        this.nextChargeAt = null;
    }

    /** 해지 철회 (기간이 끝나기 전) */
    public void resume(LocalDateTime now) {
        this.status = WebSubscriptionStatus.ACTIVE;
        this.canceledAt = null;
        this.nextChargeAt = WebBillingSchedule.firstChargeAt(currentPeriodEnd, now);
    }

    public void expire() {
        this.status = WebSubscriptionStatus.EXPIRED;
        this.nextChargeAt = null;
    }

    /** 지금 Pro 를 쓸 수 있는 구독인지 (기간 안 · 해지 신청했어도 기간 끝까지) */
    public boolean coversNow(LocalDateTime now) {
        return (status == WebSubscriptionStatus.ACTIVE || status == WebSubscriptionStatus.PAST_DUE
                || status == WebSubscriptionStatus.CANCELED)
                && currentPeriodEnd != null && currentPeriodEnd.isAfter(now);
    }
}
