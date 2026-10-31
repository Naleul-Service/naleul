package com.naleul.naleul.domain.purchase.entity;

import com.naleul.naleul.domain.purchase.enums.ProductType;
import com.naleul.naleul.domain.purchase.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_transaction_id", nullable = false, unique = true)
    private String originalTransactionId;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @Column(name = "expires_date")
    private LocalDateTime expiresDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "environment", nullable = false)
    private String environment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PurchaseTransaction(Long userId, String originalTransactionId, String transactionId,
                               String productId, ProductType productType, LocalDateTime purchaseDate,
                               LocalDateTime expiresDate, SubscriptionStatus status, String environment) {
        this.userId = userId;
        this.originalTransactionId = originalTransactionId;
        this.transactionId = transactionId;
        this.productId = productId;
        this.productType = productType;
        this.purchaseDate = purchaseDate;
        this.expiresDate = expiresDate;
        this.status = status;
        this.environment = environment;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(SubscriptionStatus status, LocalDateTime expiresDate) {
        this.status = status;
        if (expiresDate != null) {
            this.expiresDate = expiresDate;
        }
    }

    // PurchaseTransaction.java에 추가
    public void renewFrom(String transactionId, LocalDateTime purchaseDate,
                          LocalDateTime expiresDate, SubscriptionStatus status) {
        this.transactionId = transactionId;
        this.purchaseDate = purchaseDate;
        this.expiresDate = expiresDate;
        this.status = status;
    }
}