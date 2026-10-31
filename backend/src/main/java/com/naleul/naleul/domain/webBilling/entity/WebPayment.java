package com.naleul.naleul.domain.webBilling.entity;

import com.naleul.naleul.domain.webBilling.enums.WebPaymentStatus;
import com.naleul.naleul.domain.webBilling.enums.WebPlan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 웹 구독 결제 1건 (성공 · 실패 모두, V51). 주문번호는 유일 — 같은 결제를 두 번 기록하지 않게 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "web_payment", uniqueConstraints = @UniqueConstraint(name = "uk_wp_order", columnNames = "order_id"))
public class WebPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long webPaymentId;

    @Column(name = "web_subscription_id", nullable = false)
    private Long webSubscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    /** 토스 결제 키 (환불할 때 필요). 실패면 null */
    @Column(length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPlan plan;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WebPaymentStatus status;

    @Column(length = 60)
    private String failureCode;

    @Column(length = 300)
    private String failureMessage;

    private LocalDateTime approvedAt;

    @Column(nullable = false)
    private boolean testMode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WebPayment done(WebSubscription s, String orderId, long amount, String paymentKey,
                                  LocalDateTime approvedAt) {
        WebPayment p = base(s, orderId, amount);
        p.status = WebPaymentStatus.DONE;
        p.paymentKey = paymentKey;
        p.approvedAt = approvedAt;
        return p;
    }

    public static WebPayment failed(WebSubscription s, String orderId, long amount, String code, String message) {
        WebPayment p = base(s, orderId, amount);
        p.status = WebPaymentStatus.FAILED;
        p.failureCode = code;
        p.failureMessage = message != null && message.length() > 300 ? message.substring(0, 300) : message;
        return p;
    }

    private static WebPayment base(WebSubscription s, String orderId, long amount) {
        WebPayment p = new WebPayment();
        p.webSubscriptionId = s.getWebSubscriptionId();
        p.userId = s.getUserId();
        p.orderId = orderId;
        p.plan = s.getPlan();
        p.amount = amount;
        p.testMode = s.isTestMode();
        return p;
    }
}
