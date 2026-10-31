package com.naleul.naleul.domain.goalCreation.entity;

import com.naleul.naleul.domain.goalCreation.enums.AnswerSource;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import com.naleul.naleul.domain.goalCreation.enums.ReviewResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 정리 화면 항목 결과 한 줄 (V67) — 첫 "계획 만들기" 때 세션당 7줄.
 * 운영 대시보드 "AI 채팅 정리 정확도" 전용 (사용자에게 보이지 않음). ai_value · final_value 는 6개월 뒤 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_review_field")
public class GoalReviewField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalReviewFieldId;

    @Column(name = "goal_creation_session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewField field;

    @Column(nullable = false)
    private boolean aiFilled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewResult result;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AnswerSource answerSource;

    private Long questionMessageId;

    private Long answerMessageId;

    @Column(length = 500)
    private String aiValue;

    @Column(length = 500)
    private String finalValue;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static GoalReviewField of(Long sessionId, Long userId, ReviewField field, boolean aiFilled, ReviewResult result,
                                     AnswerSource answerSource, Long questionMessageId, Long answerMessageId,
                                     String aiValue, String finalValue) {
        GoalReviewField f = new GoalReviewField();
        f.sessionId = sessionId;
        f.userId = userId;
        f.field = field;
        f.aiFilled = aiFilled;
        f.result = result;
        f.answerSource = answerSource;
        f.questionMessageId = questionMessageId;
        f.answerMessageId = answerMessageId;
        f.aiValue = cut(aiValue);
        f.finalValue = cut(finalValue);
        return f;
    }

    private static String cut(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
