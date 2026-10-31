package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.enums.DraftFieldResult;
import com.naleul.naleul.domain.goalCreation.enums.DraftSource;
import com.naleul.naleul.domain.goalCreation.support.DraftPlanComparer;
import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContext;
import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContextFactory;
import com.naleul.naleul.domain.goalCreation.personal.PlanIntensity;
import com.naleul.naleul.domain.goalCreation.client.AiCallMeta;
import com.naleul.naleul.domain.goalCreation.client.PlanGenerationRequest;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.config.GoalAiQuota;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalDraftRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationMessageRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.validator.PlanValidationContext;
import com.naleul.naleul.domain.temporaryGoal.service.TemporaryGoalService;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.enums.PlanningStyleSource;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 초안 생성의 DB 작업만 모은 서비스 (메서드 하나 = 짧은 트랜잭션 하나).
 * AI 호출(최대 80초 × 2회)은 GoalDraftWorker가 트랜잭션 밖에서 한다. (이유는 GoalInterviewTxService 주석과 같음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoalDraftTxService {

    /** AI 관심도: 한도에 막힌 시도 (V70) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder limitHits;

    public static final long POLL_AFTER_MS = 2000;

    private final GoalDraftRepository draftRepository;
    private final GoalCreationSessionFinder sessionFinder;
    private final GoalJsonConverter jsonConverter;
    private final GoalAiProperties properties;
    private final GoalClock clock;

    /** 하루 · 주 · 월 한도 (2026-10). 기존 테스트처럼 없으면 예전 하루 한도 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private GoalAiQuota quota;

    /** 예전 목표 AI 설계 — 기존 루틴 · 세부 목표 살리기 (필드 주입: 기존 테스트 생성자 호환) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LegacyPlanMerger legacyPlanMerger;

    /** 임시 목표 구체화 문맥 (필드 주입 이유는 GoalInterviewTxService 와 같음 — 기존 테스트 생성자 호환) */
    /** V67: 정리 화면 항목별 결과 (AI 채팅 정리 정확도) */
    @Autowired(required = false)
    private ChatReviewRecorder chatReviewRecorder;

    @Autowired(required = false)
    private TemporaryGoalService temporaryGoalService;

    /** 개인 맞춤 (V47, 필드 주입 이유는 위와 같음) */
    @Autowired(required = false)
    private PersonalPlanContextFactory personalFactory;

    /** 인터뷰 원문 (필드 주입 이유는 위와 같음) */
    @Autowired(required = false)
    private GoalCreationMessageRepository messageRepository;

    static final int USER_ANSWER_MAX_COUNT = 12;
    static final int USER_ANSWER_MAX_LENGTH = 600;   // 마지막 "더 알려줄 정보" 답이 길 수 있어서 (예전 300)

    /**
     * 사용자가 인터뷰에서 한 말 원문.
     * 슬롯은 "목표·수치·기한·요일"만 담아서 "헬스장 다녀요", "무릎이 안 좋아요", "야식을 자주 먹어요" 같은
     * 계획에 꼭 필요한 정보가 빠진다. 계획 AI 에 원문도 같이 보내 카테고리별 루틴을 맞춤으로 만들게 한다.
     */
    private List<String> userAnswers(Long sessionId) {
        if (messageRepository == null) return List.of();
        List<String> answers = messageRepository
                .findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(sessionId).stream()
                .filter(m -> m.getRole() == MessageRole.USER && !m.isSkip())
                .map(GoalCreationMessage::getContent)
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.length() > USER_ANSWER_MAX_LENGTH ? c.substring(0, USER_ANSWER_MAX_LENGTH) : c.strip())
                .toList();
        return answers.size() > USER_ANSWER_MAX_COUNT
                ? answers.subList(answers.size() - USER_ANSWER_MAX_COUNT, answers.size())
                : answers;
    }

    /** 비동기 작업에 넘길 정보 (엔티티 대신 값만 담아서 트랜잭션 밖으로) */
    /** @param userId 루틴 시각을 실제 하루(고정 시간 · 다른 루틴)에 맞출 때 쓴다 (없으면 맞추지 않음) */
    public record GenerationJob(Long draftId, Long sessionId, int version,
                                PlanGenerationRequest aiRequest, PlanValidationContext validationContext, Long userId) {
        public GenerationJob(Long draftId, Long sessionId, int version,
                             PlanGenerationRequest aiRequest, PlanValidationContext validationContext) {
            this(draftId, sessionId, version, aiRequest, validationContext, null);
        }
    }

    // ───────────────────── 생성 요청 ─────────────────────

    public GenerationJob requestDraft(Long userId, Long sessionId, GoalDraftRequest request) {
        GoalCreationSession session = sessionFinder.findOwned(sessionId, userId);
        if (session.getStatus().isTerminal() || session.isExpired(clock.now())) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
        // 이미 생성 중이면 409 (중복 요청 방지). 아래 startGenerating()도 막지만 의도를 분명히 하기 위해 먼저 확인
        if (session.getStatus() == GoalCreationSessionStatus.GENERATING) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        GoalSlots slots = jsonConverter.toSlots(session.getSlots());
        if (!slots.hasGoalStatement()) {
            throw new CustomException(ErrorCode.REQUIRED_SLOT_MISSING);
        }

        if (draftRepository.countBySession_GoalCreationSessionId(sessionId) >= properties.maxRegenerationsPerSession()) {
            if (limitHits != null) limitHits.record(userId, "GOAL_PLAN", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.REGENERATE);
            throw new CustomException(ErrorCode.DRAFT_REGENERATE_LIMIT);
        }
        boolean premium = session.getUser() != null && session.getUser().isCurrentlyPremium();
        if (quota != null) {
            // 2026-10: 하루 · 주 · 월 한도 (GoalAiQuota) — 다시 만들기도 한 번으로 센다
            GoalAiQuota.Period over = GoalAiQuota.exceeded(quota.drafts(properties.proLimitFor(userId, premium)),
                    since -> draftRepository.countBySession_User_UserIdAndCreatedAtAfter(userId, since),
                    clock.startOfUserToday(), clock.startOfUserWeek(), clock.startOfUserMonth());
            if (over != null) {
                if (limitHits != null) limitHits.record(userId, "GOAL_PLAN", GoalInterviewTxService.hitReason(over));
                throw new CustomException(switch (over) {
                    case DAY -> ErrorCode.GOAL_DRAFT_DAILY_LIMIT_EXCEEDED;
                    case WEEK -> ErrorCode.GOAL_DRAFT_WEEKLY_LIMIT_EXCEEDED;
                    case MONTH -> ErrorCode.GOAL_DRAFT_MONTHLY_LIMIT_EXCEEDED;
                });
            }
        } else if (draftRepository.countBySession_User_UserIdAndCreatedAtAfter(userId, clock.startOfUserToday())
                >= properties.draftLimitFor(userId, premium)) {
            if (limitHits != null) limitHits.record(userId, "GOAL_PLAN", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.DAILY);
            throw new CustomException(ErrorCode.GOAL_DRAFT_DAILY_LIMIT_EXCEEDED);
        }

        // 다시 생성이면 직전 READY 초안을 AI에 같이 보낸다 (명세 4-6)
        GoalDraft latest = draftRepository.findTopBySession_GoalCreationSessionIdOrderByVersionDesc(sessionId).orElse(null);
        GoalPlanDto latestPlan = latest != null && latest.isReady() ? jsonConverter.toPlan(latest.getPayload()) : null;
        GoalPlanDto previousPlan = latestPlan;
        // 말로 고치기: 화면에서 직접 고친 초안을 기준으로 (직전 READY 초안이 있을 때만 — 첫 생성에는 의미가 없다)
        boolean quickFix = false;
        if (previousPlan != null && request.getBasePlan() != null && request.getBasePlan().goal() != null) {
            previousPlan = request.getBasePlan();
            quickFix = true;
        }
        String feedback = previousPlan != null ? trimToNull(request.getFeedback()) : null;

        PlanningStyle style = resolvePlanningStyle(request.getPlanningStyle(), session);
        int version = latest != null ? latest.getVersion() + 1 : 1;

        // V67: 첫 계획 만들기 → 정리 화면 항목별 결과 기록 (운영 대시보드 전용, 실패해도 진행)
        if (chatReviewRecorder != null) chatReviewRecorder.recordFirstReview(session, slots, request.getPlanningStyle());

        session.startGenerating();   // READY_TO_GENERATE / DRAFT_READY → GENERATING
        GoalDraft draft = draftRepository.save(GoalDraft.generating(session, version, style, feedback));
        recordSource(draft, latest, latestPlan, request, quickFix);

        // 개인 맞춤 (V47): 최근 실행 역량 · 강도 · 같은 카테고리 달성자 → 루틴 주간 상한도 이 사람 기준
        PersonalPlanContext personal = personalContext(userId, request, latest, session);
        int weeklyCap = personal != null ? personal.weeklyCapMinutes() : properties.maxWeeklyRoutineMinutes();

        PlanGenerationRequest aiRequest = new PlanGenerationRequest(
                slots, style, clock.today(), weeklyCap,
                previousPlan, feedback, List.of(),
                session.getSourceGoalCategory() != null && temporaryGoalService != null
                        ? temporaryGoalService.buildContext(session.getSourceGoalCategory())
                        : null,
                session.kind(),
                userAnswers(sessionId),
                jsonConverter.toStringList(session.getMustDoItems()),
                personal);
        PlanValidationContext validationContext = new PlanValidationContext(
                clock.today(), style,
                slots.practicePreference().isFilled() ? slots.practicePreference().value() : null,
                weeklyCap,
                slots.metric().isFilled() ? slots.metric().value() : null,
                true,   // AI 초안은 내용 품질 규칙까지 HARD (어기면 AI 에 다시 고쳐 달라고 요청)
                personal);

        return new GenerationJob(draft.getGoalDraftId(), sessionId, version, aiRequest, validationContext, userId);
    }

    /**
     * V68: 이 초안을 만든 요청 종류 + 말로 고치기 직전 손으로 고친 칸 (운영 대시보드 "AI 초안 정확도").
     * 측정용이라 실패해도 초안 생성은 그대로 진행한다.
     */
    private void recordSource(GoalDraft draft, GoalDraft latest, GoalPlanDto latestPlan, GoalDraftRequest request,
                              boolean quickFix) {
        try {
            DraftSource source = draftSource(latest, latestPlan, request, quickFix);
            String baseEdits = null;
            if (source == DraftSource.QUICK_FIX) {
                List<DraftPlanComparer.Diff> edits = DraftPlanComparer.compare(latestPlan, request.getBasePlan()).stream()
                        .filter(d -> d.result() == DraftFieldResult.EDITED || d.result() == DraftFieldResult.DELETED
                                || d.result() == DraftFieldResult.ADDED)
                        .toList();
                if (!edits.isEmpty()) baseEdits = jsonConverter.toJson(edits);
            }
            draft.markSource(source, baseEdits);
        } catch (RuntimeException e) {
            log.warn("[GoalDraft] 초안 요청 종류 기록 실패 draftId={} reason={}", draft != null ? draft.getGoalDraftId() : null, e.getMessage());
        }
    }

    /**
     * 앱이 보낸 값으로 요청 종류를 판단한다 (앱 수정 없음).
     * basePlan → 말로 고치기 · 강도가 직전과 다름 → 강도 변경 · 성향이 직전과 다름 → 성향 변경 · 그 밖 → 다시 생성.
     */
    static DraftSource draftSource(GoalDraft latest, GoalPlanDto latestPlan, GoalDraftRequest request, boolean quickFix) {
        if (latest == null) return DraftSource.INITIAL;
        if (latestPlan == null) return DraftSource.RETRY;   // 직전 생성이 실패해서 다시 시도
        if (quickFix) return DraftSource.QUICK_FIX;
        String prevIntensity = latestPlan.fit() != null && latestPlan.fit().intensity() != null
                ? latestPlan.fit().intensity() : PlanIntensity.STEADY.name();
        if (request.getIntensity() != null && !request.getIntensity().name().equals(prevIntensity)) {
            return DraftSource.INTENSITY;
        }
        if (request.getPlanningStyle() != null && request.getPlanningStyle() != latest.getPlanningStyle()) {
            return DraftSource.STYLE;
        }
        return DraftSource.REGEN;
    }

    /**
     * 개인 맞춤 정보. 강도는 ① 이번 요청 ② 직전 초안 ③ 안정형 순.
     * 계산이 실패하거나 빈이 없으면(기존 테스트) null → 예전처럼 모두 같은 상한으로 만든다.
     */
    private PersonalPlanContext personalContext(Long userId, GoalDraftRequest request, GoalDraft latest,
                                                GoalCreationSession session) {
        if (personalFactory == null) return null;
        PlanIntensity intensity = request.getIntensity();
        if (intensity == null && latest != null && latest.isReady()) {
            GoalPlanDto prev = jsonConverter.toPlan(latest.getPayload());
            if (prev != null && prev.fit() != null && prev.fit().intensity() != null) {
                try {
                    intensity = PlanIntensity.valueOf(prev.fit().intensity());
                } catch (IllegalArgumentException ignored) {
                    // 예전 값이면 기본값
                }
            }
        }
        try {
            return personalFactory.create(userId, intensity, session.kind());
        } catch (RuntimeException e) {
            log.warn("[GoalDraft] 개인 맞춤 정보 계산 실패 → 공통 상한으로 생성 sessionId={}", session.getGoalCreationSessionId(), e);
            return null;
        }
    }

    /**
     * 성향 우선순위 (명세 3-3)
     *  ① 초안 화면에서 사용자가 직접 고른 값
     *  ② 프로필에 사용자가 직접 저장한 값(USER_SET)
     *  ③ 이번 대화에서 AI가 추정한 값
     *  ④ 프로필의 이전 AI 추정값
     *  ⑤ 아무것도 없으면 PLANNER
     */
    PlanningStyle resolvePlanningStyle(PlanningStyle selectedByUser, GoalCreationSession session) {
        if (selectedByUser != null) return selectedByUser;
        User user = session.getUser();
        if (user.getPlanningStyle() != null && user.getPlanningStyleSource() == PlanningStyleSource.USER_SET) {
            return user.getPlanningStyle();
        }
        if (session.getPlanningStyleGuess() != null) return session.getPlanningStyleGuess();
        if (user.getPlanningStyle() != null) return user.getPlanningStyle();
        return PlanningStyle.PLANNER;
    }

    // ───────────────────── 비동기 작업 결과 반영 ─────────────────────

    public void completeDraft(Long draftId, GoalPlanDto plan, AiCallMeta meta) {
        GoalDraft draft = draftRepository.findForUpdate(draftId).orElse(null);
        if (draft == null || !draft.isGenerating()) {
            // 그 사이 좀비로 판정돼 FAILED가 됐거나, 세션이 취소된 경우 → 늦게 온 결과는 버린다
            log.warn("[GoalDraft] 생성 완료됐지만 이미 다른 상태라 무시 draftId={} status={}",
                    draftId, draft != null ? draft.getStatus() : null);
            return;
        }
        // 예전 목표를 AI 로 다시 설계하는 중이면 기존 세부 목표 · 루틴을 초안에 넣는다 (2026-10, 살릴 수 있는 건 살리기)
        if (legacyPlanMerger != null) plan = legacyPlanMerger.merge(draft.getSession().getSourceGoalCategory(), plan);
        draft.markReady(jsonConverter.toJson(plan),
                meta != null ? meta.model() : null,
                meta != null ? meta.inputTokens() : null,
                meta != null ? meta.outputTokens() : null);

        GoalCreationSession session = draft.getSession();
        if (session.getStatus() == GoalCreationSessionStatus.GENERATING) {
            session.markDraftReady();
        }
    }

    /** errorCode: AI_PLAN_INVALID, AI_TIMEOUT, AI_UNAVAILABLE, AI_INVALID_OUTPUT, AI_BUSY */
    public void failDraft(Long draftId, String errorCode) {
        GoalDraft draft = draftRepository.findForUpdate(draftId).orElse(null);
        if (draft == null || !draft.isGenerating()) return;
        markFailed(draft, errorCode);
    }

    // ───────────────────── 폴링 ─────────────────────

    /** 조회하면서 좀비(3분 넘게 GENERATING)면 FAILED(AI_TIMEOUT)로 정리 (명세 7-6) */
    public GoalDraftResponse getDraft(Long userId, Long sessionId, Long draftId) {
        sessionFinder.findOwned(sessionId, userId);   // 소유권 확인 (남의 세션이면 404)
        GoalDraft draft = draftRepository.findByGoalDraftIdAndSession_GoalCreationSessionId(draftId, sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_DRAFT_NOT_FOUND));

        if (draft.isZombie(zombieThreshold())) {
            draft = draftRepository.findForUpdate(draftId).orElseThrow();   // 잠그고 다시 확인
            if (draft.isZombie(zombieThreshold())) {
                log.warn("[GoalDraft] 좀비 초안 정리 draftId={}", draftId);
                markFailed(draft, "AI_TIMEOUT");
            }
        }
        return toResponse(draft);
    }

    /** 스케줄러용: 아무도 폴링하지 않는 좀비도 정리 (앱을 꺼버린 경우) */
    public int failZombies() {
        List<GoalDraft> zombies = draftRepository.findAllByStatusAndUpdatedAtBefore(GoalDraftStatus.GENERATING, zombieThreshold());
        zombies.forEach(z -> markFailed(z, "AI_TIMEOUT"));
        return zombies.size();
    }

    // ───────────────────── 내부 ─────────────────────

    private void markFailed(GoalDraft draft, String errorCode) {
        draft.markFailed(errorCode);
        GoalCreationSession session = draft.getSession();
        if (session.getStatus() == GoalCreationSessionStatus.GENERATING) {
            session.markGenerationFailed();   // READY_TO_GENERATE로 되돌려 다시 시도 가능
        }
    }

    private LocalDateTime zombieThreshold() {
        return clock.now().minusMinutes(properties.draftZombieMinutes());
    }

    private GoalDraftResponse toResponse(GoalDraft draft) {
        return switch (draft.getStatus()) {
            case GENERATING -> new GoalDraftResponse(draft.getGoalDraftId(), draft.getVersion(), draft.getStatus(),
                    draft.getPlanningStyle(), progressStep(draft), POLL_AFTER_MS, null, null, null);
            case READY -> new GoalDraftResponse(draft.getGoalDraftId(), draft.getVersion(), draft.getStatus(),
                    draft.getPlanningStyle(), null, null, jsonConverter.toPlan(draft.getPayload()), null, null);
            case FAILED -> new GoalDraftResponse(draft.getGoalDraftId(), draft.getVersion(), draft.getStatus(),
                    draft.getPlanningStyle(), null, null, null, draft.getErrorCode(), failureMessage(draft.getErrorCode()));
        };
    }

    /**
     * 로딩 문구용 진행 단계 (명세 7-6). AI는 중간 진행률을 알려주지 않으므로 경과 시간으로 추정한다.
     * 실제 진행과 정확히 맞지 않아도 되는, "기다리는 동안 덜 지루하게" 하기 위한 값.
     */
    private String progressStep(GoalDraft draft) {
        if (draft.getCreatedAt() == null) return "ANALYZING";
        long seconds = Duration.between(draft.getCreatedAt(), clock.now()).getSeconds();
        if (seconds < 5) return "ANALYZING";
        if (seconds < 15) return "SUB_GOALS";
        if (seconds < 30) return "MILESTONES";
        if (seconds < 50) return "TASKS";
        return "VALIDATING";
    }

    private String failureMessage(String errorCode) {
        if ("AI_BUSY".equals(errorCode)) {
            return "지금 계획을 만드는 사람이 많아요. 잠시 후 다시 시도해 주세요.";
        }
        return "계획을 만드는 데 실패했어요. 다시 시도해 주세요.";
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
