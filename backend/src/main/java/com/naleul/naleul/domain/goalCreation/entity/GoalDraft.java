package com.naleul.naleul.domain.goalCreation.entity;

import com.naleul.naleul.domain.goalCreation.enums.DraftSource;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** AI가 만든 목표 구조 초안(명세 4-4 JSON). 다시 생성할 때마다 version +1 로 새 행이 쌓인다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_draft")
public class GoalDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalDraftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_creation_session_id", nullable = false)
    private GoalCreationSession session;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalDraftStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanningStyle planningStyle;

    @Column(length = 200)
    private String userFeedback;

    /** 4-4 초안 JSON 원문. 생성 중/실패면 null */
    @Column(columnDefinition = "json")
    private String payload;

    @Column(length = 50)
    private String errorCode;

    @Column(length = 60)
    private String aiModel;

    private Integer inputTokens;
    private Integer outputTokens;

    /** V68: 어떤 요청으로 만든 초안인지 (AI 초안 정확도 · 운영 대시보드 전용). 예전 초안은 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DraftSource source;

    /** V68: 말로 고치기 직전 사용자가 손으로 고친 칸 (DraftPlanComparer.Diff 목록 JSON) */
    @Column(columnDefinition = "json")
    private String baseEdits;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static GoalDraft generating(GoalCreationSession session, int version,
                                       PlanningStyle planningStyle, String userFeedback) {
        GoalDraft draft = new GoalDraft();
        draft.session = session;
        draft.version = version;
        draft.planningStyle = planningStyle;
        draft.userFeedback = userFeedback;
        draft.status = GoalDraftStatus.GENERATING;
        return draft;
    }

    /** V68: 측정용 — 실패해도 초안 생성에는 영향 없음 */
    public void markSource(DraftSource source, String baseEditsJson) {
        this.source = source;
        this.baseEdits = baseEditsJson;
    }

    public void markReady(String payloadJson, String aiModel, Integer inputTokens, Integer outputTokens) {
        this.status = GoalDraftStatus.READY;
        this.payload = payloadJson;
        this.aiModel = aiModel;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.errorCode = null;
    }

    /** errorCode 예: AI_PLAN_INVALID, AI_TIMEOUT, AI_UNAVAILABLE */
    public void markFailed(String errorCode) {
        this.status = GoalDraftStatus.FAILED;
        this.errorCode = errorCode;
    }

    public boolean isReady() {
        return status == GoalDraftStatus.READY;
    }

    public boolean isGenerating() {
        return status == GoalDraftStatus.GENERATING;
    }

    /** 생성 중인데 마지막 갱신 후 너무 오래 지났으면 좀비(서버 재시작 등으로 작업이 사라짐) */
    public boolean isZombie(LocalDateTime threshold) {
        return isGenerating() && updatedAt != null && updatedAt.isBefore(threshold);
    }
}
