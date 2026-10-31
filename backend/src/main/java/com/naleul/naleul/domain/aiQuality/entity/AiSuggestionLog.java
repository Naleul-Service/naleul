package com.naleul.naleul.domain.aiQuality.entity;

import com.naleul.naleul.domain.aiQuality.enums.AiSuggestionStatus;
import com.naleul.naleul.domain.aiUsage.enums.AiFeature;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 제안 1건 = 1행 (V64). "AI 가 낸 결과를 받아들였나(채택) · 얼마나 고쳤나(수정)" 를 재는 재료.
 *
 * 왜 필요한가: 말로 고치기 · AI 일정 조정 · 브레인 덤프는 미리보기(제안)와 적용(확정)이 따로 호출돼서,
 * 지금까지는 제안이 몇 개였고 그중 몇 개가 적용됐는지 로그 파일에만 남았다.
 *
 * 사용자 입력 원문 · AI 응답 내용은 저장하지 않는다 (ai_call_log 와 같은 규칙).
 * 브레인 덤프 비교용 스냅샷은 제목을 해시로만 남긴다.
 */
@Entity
@Table(name = "ai_suggestion_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSuggestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long aiSuggestionLogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    /** 말로 고치기: 고친 목표 */
    private Long targetGoalId;

    /** false: AI 가 실패 · 한도 초과라 규칙으로만 만든 제안 (브레인 덤프) — 품질 집계에서 뺀다 */
    @Column(nullable = false)
    private boolean aiUsed;

    /** AI 가 낸 항목 · 변경 수 */
    @Column(nullable = false)
    private int suggestedCount;

    /** 서버 검증을 통과해 사용자에게 보여 준 수 (suggested − valid = 엉뚱한 제안) */
    @Column(nullable = false)
    private int validCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiSuggestionStatus status;

    /** 적용 · 확정한 수 */
    private Integer appliedCount;

    /** 적용하려 했지만 건너뛴 수 (일정 조정: 잠긴 일정과 겹침 등) */
    private Integer skippedCount;

    /** 브레인 덤프: 확정 전에 사용자가 고친 항목 수 (제목 · 날짜 · 시간 · 목표 중 하나라도) */
    private Integer editedCount;

    /** 브레인 덤프: AI 가 연결한 목표를 사용자가 바꾼 항목 수 */
    private Integer goalChangedCount;

    /** 브레인 덤프: AI 해석 결과 지문 (clientKey · 제목 해시 · 날짜 · 시간 · 목표) — 확정 때 비교용 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String snapshotJson;

    /** 제안 당시 플랜 FREE | PRO (V70, 운영 대시보드 "AI 관심도") */
    @Column(name = "user_plan", length = 4, updatable = false)
    private String userPlan;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    public static AiSuggestionLog suggested(Long userId, AiFeature feature, Long targetGoalId, boolean aiUsed,
                                            int suggestedCount, int validCount, String snapshotJson,
                                            LocalDateTime now) {
        AiSuggestionLog l = new AiSuggestionLog();
        l.userId = userId;
        l.feature = feature;
        l.targetGoalId = targetGoalId;
        l.aiUsed = aiUsed;
        l.suggestedCount = Math.max(0, suggestedCount);
        l.validCount = Math.max(0, validCount);
        l.status = AiSuggestionStatus.SUGGESTED;
        l.snapshotJson = snapshotJson;
        l.createdAt = now;
        return l;
    }

    /** 제안 당시 플랜을 남긴다 (V70) */
    public AiSuggestionLog withPlan(String userPlan) {
        this.userPlan = userPlan;
        return this;
    }

    public void applied(int appliedCount, Integer skippedCount, Integer editedCount, Integer goalChangedCount,
                        LocalDateTime now) {
        this.status = AiSuggestionStatus.APPLIED;
        this.appliedCount = Math.max(0, appliedCount);
        this.skippedCount = skippedCount;
        this.editedCount = editedCount;
        this.goalChangedCount = goalChangedCount;
        this.appliedAt = now;
        this.snapshotJson = null;   // 비교가 끝났으니 지운다
    }
}
