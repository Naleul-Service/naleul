package com.naleul.naleul.domain.purchase.entity;

import com.naleul.naleul.domain.purchase.enums.RevenueEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 1건 = 1행 (V48). 매출 대시보드의 재료.
 *
 * purchase_transaction 은 "지금 구독 상태"를 담는 테이블이라 갱신 때마다 같은 행을 덮어쓴다.
 * 그래서 매출(언제 · 얼마 결제됐는지)은 이 테이블에 따로 쌓는다.
 */
@Entity
@Table(name = "iap_revenue_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapRevenueEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long iapRevenueEventId;

    private Long userId;

    @Column(nullable = false, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 100)
    private String originalTransactionId;

    @Column(nullable = false, length = 150)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevenueEventType eventType;

    @Column(precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false)
    private LocalDateTime eventAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static IapRevenueEvent of(Long userId, String transactionId, String originalTransactionId,
                                     String productId, RevenueEventType eventType, BigDecimal priceAmount,
                                     String currency, String environment, LocalDateTime eventAt) {
        IapRevenueEvent e = new IapRevenueEvent();
        e.userId = userId;
        e.transactionId = transactionId;
        e.originalTransactionId = originalTransactionId;
        e.productId = productId;
        e.eventType = eventType;
        e.priceAmount = priceAmount;
        e.currency = currency;
        e.environment = environment;
        e.eventAt = eventAt;
        return e;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
