package com.naleul.naleul.domain.pattern.entity;

import com.naleul.naleul.domain.pattern.enums.PatternSuggestionActionType;
import com.naleul.naleul.domain.pattern.enums.PatternSuggestionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제안 반영 / 미루기 기록 (명세 4 · 4-3, 4-4).
 * - APPLIED  : 같은 제안을 다시 만들지 않음 + 바꾸기 전/후 값을 남겨 나중에 되돌리기·효과 측정에 사용
 * - DISMISSED: 7일 동안 같은 제안을 다시 만들지 않음
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pattern_suggestion_action")
public class PatternSuggestionAction {

    public static final int DISMISS_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long patternSuggestionActionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String suggestionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternSuggestionType suggestionType;

    @Column(name = "routine_id")
    private Long routineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatternSuggestionActionType action;

    @Column(length = 500)
    private String beforeJson;

    @Column(length = 500)
    private String afterJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PatternSuggestionAction applied(Long userId, String key, PatternSuggestionType type,
                                                  Long routineId, String beforeJson, String afterJson,
                                                  LocalDateTime now) {
        PatternSuggestionAction a = base(userId, key, type, routineId, now);
        a.action = PatternSuggestionActionType.APPLIED;
        a.beforeJson = beforeJson;
        a.afterJson = afterJson;
        return a;
    }

    public static PatternSuggestionAction dismissed(Long userId, String key, PatternSuggestionType type,
                                                    Long routineId, LocalDateTime now) {
        PatternSuggestionAction a = base(userId, key, type, routineId, now);
        a.action = PatternSuggestionActionType.DISMISSED;
        return a;
    }

    private static PatternSuggestionAction base(Long userId, String key, PatternSuggestionType type,
                                                Long routineId, LocalDateTime now) {
        if (userId == null || key == null || key.isBlank() || type == null || now == null) {
            throw new IllegalArgumentException("userId, key, type, now 는 필수입니다");
        }
        if (key.length() > 100) {
            throw new IllegalArgumentException("suggestionKey 는 100자 이하여야 합니다");
        }
        PatternSuggestionAction a = new PatternSuggestionAction();
        a.userId = userId;
        a.suggestionKey = key;
        a.suggestionType = type;
        a.routineId = routineId;
        a.createdAt = now;
        return a;
    }
}
