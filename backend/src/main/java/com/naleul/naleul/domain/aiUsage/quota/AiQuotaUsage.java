package com.naleul.naleul.domain.aiUsage.quota;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** AI 기능 한 번 사용 = 한 줄 (V55). 하루 · 주 단위 횟수 제한에 쓴다 */
@Entity
@Table(name = "ai_quota_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiQuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_quota_usage_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public static AiQuotaUsage of(Long userId, AiFeature feature, LocalDateTime usedAt) {
        AiQuotaUsage u = new AiQuotaUsage();
        u.userId = userId;
        u.feature = feature;
        u.usedAt = usedAt;
        return u;
    }
}
