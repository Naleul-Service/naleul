package com.naleul.naleul.domain.aiUsage.entity;

import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Claude API 호출 1번 = 1행 (V48). AI 비용 계산의 재료.
 * 사용자 입력 원문 · AI 응답 내용은 저장하지 않는다.
 */
@Entity
@Table(name = "ai_call_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long aiCallLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    @Column(length = 80)
    private String model;

    @Column(nullable = false)
    private int inputTokens;

    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private int cacheCreationInputTokens;

    @Column(nullable = false)
    private int cacheReadInputTokens;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AiCallLog of(AiFeature feature, String model, int inputTokens, int outputTokens,
                               int cacheCreation, int cacheRead, long latencyMs, boolean success) {
        AiCallLog log = new AiCallLog();
        log.feature = feature;
        log.model = model != null && model.length() > 80 ? model.substring(0, 80) : model;
        log.inputTokens = inputTokens;
        log.outputTokens = outputTokens;
        log.cacheCreationInputTokens = cacheCreation;
        log.cacheReadInputTokens = cacheRead;
        log.latencyMs = latencyMs;
        log.success = success;
        return log;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
