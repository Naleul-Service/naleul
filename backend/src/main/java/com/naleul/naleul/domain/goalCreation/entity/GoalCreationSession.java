package com.naleul.naleul.domain.goalCreation.entity;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.goalCategory.kind.GoalType;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus.*;

/**
 * AI 인터뷰 ~ 목표 확정까지의 진행 상태.
 * 상태 전이 규칙(명세 2-1)은 전부 이 엔티티 메서드 안에서만 바뀌게 해서
 * 서비스 코드 여러 곳에서 status를 직접 바꾸다 꼬이는 일을 막는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "goal_creation_session")
public class GoalCreationSession {

    public static final int MAX_QUESTIONS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goalCreationSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoalCreationSessionStatus status;

    /** 임시 목표 "구체화하기"로 진입한 경우 원본 목표 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_goal_category_id")
    private GoalCategory sourceGoalCategory;

    @Column(nullable = false)
    private int questionCount;

    /**
     * 슬롯 JSON 원문. 서비스 계층에서 Jackson으로 DTO ↔ 문자열 변환.
     * columnDefinition = "json"이어야 ddl-auto: validate가 DB의 JSON 타입과 일치한다고 판단함.
     */
    @Column(nullable = false, columnDefinition = "json")
    private String slots;

    /** 꼭 하고 싶은 일 JSON 배열 (V42). 예) ["헬스","식단 기록"]. 없으면 null */
    @Column(name = "must_do_items", length = 1000)
    private String mustDoItems;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlanningStyle planningStyleGuess;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_goal_category_id")
    private GoalCategory confirmedGoalCategory;

    // ── 목표 종류 (명세 6.A.1.2, V37) — 시작 화면에서 고른 1·2단계 카테고리. 예전 앱은 null ──
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GoalSubType goalSubType;

    @Column(length = 10)
    private String goalKindLabel;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // ── V67: AI 채팅 정리 정확도 ──
    /** 대화가 끝난 순간의 정리값 스냅샷 (정리 화면에서 고친 것과 비교) */
    @Column(columnDefinition = "json")
    private String aiSlots;

    /** 정리 화면에 "AI 추천"으로 보인 성향 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlanningStyle aiPlanningStyle;

    /** 정리 화면에 들어온 시각 (인터뷰 종료) */
    private LocalDateTime reviewEnteredAt;

    /** 처음 "계획 만들기"를 누른 시각 — 이때 항목별 결과를 기록한다 */
    private LocalDateTime reviewedAt;

    /** 인터뷰 프롬프트 버전 (InterviewPromptVersion) */
    @Column(length = 20)
    private String promptVersion;

    /** 시작 당시 플랜 FREE | PRO (V70) — 나중에 Pro 로 바꿔도 이 시도는 Free 로 센다 */
    @Column(name = "user_plan", length = 4, updatable = false)
    private String userPlan;

    /** 끝내지 못한 이유 (V70) */
    @Enumerated(EnumType.STRING)
    @Column(name = "abandon_reason", length = 20)
    private com.naleul.naleul.domain.goalCreation.enums.AbandonReason abandonReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── create ────────────────────────────────────────────

    public static GoalCreationSession start(User user, GoalCategory sourceGoalCategory,
                                            String initialSlotsJson, LocalDateTime expiresAt) {
        GoalCreationSession session = new GoalCreationSession();
        session.user = user;
        session.sourceGoalCategory = sourceGoalCategory;
        session.slots = initialSlotsJson;
        session.expiresAt = expiresAt;
        session.status = INTERVIEWING;
        session.questionCount = 0;
        session.userPlan = user == null ? null : (user.hasPaidPremium() ? "PRO" : "FREE");
        return session;
    }

    /** 카테고리를 고르고 시작한 세션 */
    public static GoalCreationSession start(User user, GoalCategory sourceGoalCategory, GoalKind kind,
                                            String initialSlotsJson, LocalDateTime expiresAt) {
        GoalCreationSession session = start(user, sourceGoalCategory, initialSlotsJson, expiresAt);
        if (kind != null) {
            session.goalType = kind.type();
            session.goalSubType = kind.subType();
            session.goalKindLabel = kind.customLabel();
        }
        return session;
    }

    /** 고른 카테고리 (없으면 null) */
    public GoalKind kind() {
        return GoalKind.restore(goalType, goalSubType, goalKindLabel);
    }

    // ── 인터뷰 단계 ────────────────────────────────────────

    /** AI가 질문 하나를 던질 때 호출. 5회를 넘으면 예외. */
    public void recordQuestion() {
        requireStatus(INTERVIEWING);
        if (questionCount >= MAX_QUESTIONS) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
        this.questionCount++;
    }

    public boolean isQuestionLimitReached() {
        return questionCount >= MAX_QUESTIONS;
    }

    /** 꼭 하고 싶은 일 바꾸기 (시작 화면 · 정리 확인 화면). 상태 확인은 서비스가 한다 */
    public void updateMustDoItems(String json) {
        this.mustDoItems = json;
    }

    /** 인터뷰 중 또는 정리 확인 화면(READY_TO_GENERATE)에서만 슬롯 수정 가능 */
    public void updateSlots(String slotsJson) {
        requireStatus(INTERVIEWING, READY_TO_GENERATE);
        this.slots = slotsJson;
    }

    public void updatePlanningStyleGuess(PlanningStyle guess) {
        if (guess != null) {
            this.planningStyleGuess = guess;
        }
    }

    /** 필수 정보 충족 / 5문항 소진 / 사용자 조기 종료 */
    public void finishInterview() {
        requireStatus(INTERVIEWING);
        this.status = READY_TO_GENERATE;
        // 정리 화면에 보여줄 AI 정리값 · 추천 성향을 그대로 남겨 둔다 (첫 계획 만들기 때 비교)
        this.aiSlots = this.slots;
        this.aiPlanningStyle = this.planningStyleGuess != null ? this.planningStyleGuess : PlanningStyle.PLANNER;
        this.reviewEnteredAt = LocalDateTime.now();
    }

    public void recordPromptVersion(String version) {
        this.promptVersion = version;
    }

    /** 첫 계획 만들기 — 정리 화면 결과를 기록했다 */
    public void markReviewed(LocalDateTime at) {
        if (this.reviewedAt == null) this.reviewedAt = at;
    }

    // ── 초안 생성 단계 ─────────────────────────────────────

    /** 첫 생성(READY_TO_GENERATE) 또는 다시 생성(DRAFT_READY) */
    public void startGenerating() {
        requireStatus(READY_TO_GENERATE, DRAFT_READY);
        this.status = GENERATING;
    }

    public void markDraftReady() {
        requireStatus(GENERATING);
        this.status = DRAFT_READY;
    }

    /** 생성 실패 시 다시 시도할 수 있도록 되돌림 */
    public void markGenerationFailed() {
        requireStatus(GENERATING);
        this.status = READY_TO_GENERATE;
    }

    // ── 확정 / 취소 ───────────────────────────────────────

    public void confirm(GoalCategory goalCategory) {
        requireStatus(DRAFT_READY);
        this.status = CONFIRMED;
        this.confirmedGoalCategory = goalCategory;
    }

    /** 사용자 취소, 7일 만료, AI REJECT 모두 이 메서드로 처리 */
    public void abandon() {
        abandon(null);
    }

    /** @param reason 운영 대시보드 "AI 관심도"의 중단 이유 (V70) */
    public void abandon(com.naleul.naleul.domain.goalCreation.enums.AbandonReason reason) {
        if (status.isTerminal()) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
        this.status = ABANDONED;
        this.abandonReason = reason;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    // ── 내부 ──────────────────────────────────────────────

    private void requireStatus(GoalCreationSessionStatus first, GoalCreationSessionStatus... rest) {
        Set<GoalCreationSessionStatus> allowed = EnumSet.of(first, rest);
        if (!allowed.contains(this.status)) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
    }
}
