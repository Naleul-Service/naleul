package com.naleul.naleul.domain.playBilling.entity;

import com.naleul.naleul.domain.playBilling.client.PlayDeveloperClient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Android Google Play 구독 1건 (V62) — 구매 토큰마다 1행 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "play_subscription", uniqueConstraints = @UniqueConstraint(name = "uk_ps_token", columnNames = "purchase_token"))
public class PlaySubscription {

    /** Pro 를 쓸 수 있는 Play 상태 — 해지(CANCELED)해도 기간 끝까지, 결제 유예(IN_GRACE_PERIOD) 중에도 이용 */
    public static boolean entitles(String state) {
        return "SUBSCRIPTION_STATE_ACTIVE".equals(state)
                || "SUBSCRIPTION_STATE_CANCELED".equals(state)
                || "SUBSCRIPTION_STATE_IN_GRACE_PERIOD".equals(state);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playSubscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "purchase_token", nullable = false, length = 512)
    private String purchaseToken;

    @Column(nullable = false, length = 100)
    private String productId;

    @Column(length = 100)
    private String basePlanId;

    @Column(length = 100)
    private String latestOrderId;

    @Column(nullable = false, length = 40)
    private String state;

    private LocalDateTime expiryTime;

    @Column(nullable = false)
    private boolean autoRenewing;

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(nullable = false)
    private boolean testPurchase;

    @Column(length = 512)
    private String linkedPurchaseToken;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PlaySubscription of(Long userId, String purchaseToken) {
        PlaySubscription s = new PlaySubscription();
        s.userId = userId;
        s.purchaseToken = purchaseToken;
        return s;
    }

    /** Play 에서 받은 최신 상태로 맞춘다 */
    public void apply(PlayDeveloperClient.SubscriptionPurchaseV2 p, String fallbackProductId) {
        PlayDeveloperClient.LineItem line = p.line();
        this.productId = line != null && line.productId() != null ? line.productId() : fallbackProductId;
        this.basePlanId = line != null ? line.basePlanId() : null;
        this.expiryTime = line != null ? line.expiry() : null;
        this.autoRenewing = line != null && line.autoRenewing();
        this.state = p.subscriptionState() != null ? p.subscriptionState() : "SUBSCRIPTION_STATE_UNSPECIFIED";
        this.latestOrderId = p.latestOrderId();
        this.linkedPurchaseToken = p.linkedPurchaseToken();
        this.testPurchase = p.isTest();
        if (p.acknowledged()) this.acknowledged = true;
    }

    public void markAcknowledged() {
        this.acknowledged = true;
    }

    /** 요금제를 바꿔 새 토큰으로 넘어간 이전 구독 — 더 이상 Pro 근거가 아님 */
    public void supersede() {
        this.state = "SUBSCRIPTION_STATE_EXPIRED";
        this.autoRenewing = false;
    }

    public boolean entitlesNow(LocalDateTime now) {
        return entitles(state) && expiryTime != null && expiryTime.isAfter(now);
    }
}
