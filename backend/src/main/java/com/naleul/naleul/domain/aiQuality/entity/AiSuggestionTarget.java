package com.naleul.naleul.domain.aiQuality.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적용된 AI 제안이 만든 · 옮긴 Task / 루틴 (V64). "받아들인 뒤 실제로 실천됐나(결과)" 를 재는 재료.
 * FK 없음: Task 가 지워져도 집계는 task 테이블과 조인해서 남은 것만 센다.
 */
@Entity
@Table(name = "ai_suggestion_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSuggestionTarget {

    public enum Type { TASK, ROUTINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long aiSuggestionTargetId;

    @Column(nullable = false)
    private Long aiSuggestionLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type targetType;

    @Column(nullable = false)
    private Long targetId;

    public static AiSuggestionTarget of(Long logId, Type type, Long targetId) {
        AiSuggestionTarget t = new AiSuggestionTarget();
        t.aiSuggestionLogId = logId;
        t.targetType = type;
        t.targetId = targetId;
        return t;
    }
}
