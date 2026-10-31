package com.naleul.naleul.domain.goalCreation.entity;

import com.naleul.naleul.domain.goalCreation.enums.AnswerSource;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 인터뷰 대화 한 줄. 이어하기 화면 복원과 AI 컨텍스트 구성에 사용. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_creation_message")
public class GoalCreationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalCreationMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_creation_session_id", nullable = false)
    private GoalCreationSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    /** 건너뛰기면 null */
    @Column(columnDefinition = "text")
    private String content;

    /** ASSISTANT 질문이 겨냥한 슬롯 key (예: "metric") */
    @Column(length = 30)
    private String targetSlot;

    /** ["3개월","6개월"] 형태 JSON 원문 */
    @Column(columnDefinition = "json")
    private String quickReplies;

    @Column(nullable = false)
    private boolean isSkip;

    // ── V67: AI 채팅 정리 정확도 (USER 줄만) ──
    /** 어느 질문(슬롯 key)에 대한 답인지 */
    @Column(length = 30)
    private String answeredSlot;

    /** 칩을 눌렀는지 · 직접 썼는지 · 건너뛰었는지 */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AnswerSource answerSource;

    /** 고른 칩 번호 (0부터) */
    private Integer chipIndex;

    /** 이 답에서 AI 가 뽑은 값 (ExtractedSlots JSON) */
    @Column(columnDefinition = "json")
    private String extracted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static GoalCreationMessage assistant(GoalCreationSession session, String content,
                                                String targetSlot, String quickRepliesJson) {
        GoalCreationMessage m = new GoalCreationMessage();
        m.session = session;
        m.role = MessageRole.ASSISTANT;
        m.content = content;
        m.targetSlot = targetSlot;
        m.quickReplies = quickRepliesJson;
        m.isSkip = false;
        return m;
    }

    public static GoalCreationMessage user(GoalCreationSession session, String content) {
        GoalCreationMessage m = new GoalCreationMessage();
        m.session = session;
        m.role = MessageRole.USER;
        m.content = content;
        m.isSkip = false;
        return m;
    }

    /** 답 정보 기록 (저장 전에 부른다) */
    public GoalCreationMessage withAnswerMeta(String answeredSlot, AnswerSource source, Integer chipIndex) {
        this.answeredSlot = answeredSlot;
        this.answerSource = source;
        this.chipIndex = source == AnswerSource.CHIP ? chipIndex : null;
        return this;
    }

    /** 이 답에서 AI 가 뽑은 값 (AI 응답을 받은 뒤) */
    public void recordExtracted(String extractedJson) {
        this.extracted = extractedJson;
    }

    public static GoalCreationMessage userSkip(GoalCreationSession session, String targetSlot) {
        GoalCreationMessage m = new GoalCreationMessage();
        m.session = session;
        m.role = MessageRole.USER;
        m.content = null;
        m.targetSlot = targetSlot;
        m.isSkip = true;
        return m;
    }
}
