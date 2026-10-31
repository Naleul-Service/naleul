package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.config.GoalAiQuota;
import com.naleul.naleul.domain.goalCreation.personal.CapacityProfileReader;
import com.naleul.naleul.domain.goalCreation.personal.PersonalPrompt;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalAnswerRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSessionStartRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSlotsUpdateRequest;
import com.naleul.naleul.domain.goalCreation.dto.slot.DeadlineSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.AnswerSource;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.support.InterviewPromptVersion;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationMessageRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.support.GoalMustDo;
import com.naleul.naleul.domain.goalCreation.support.GoalSlotMerger;
import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.temporaryGoal.service.TemporaryGoalService;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 인터뷰의 "DB 작업" 부분만 모은 서비스. 메서드 하나 = 짧은 트랜잭션 하나.
 *
 * 왜 GoalInterviewService와 나눴나
 *  - AI 호출은 최대 20초 걸린다. @Transactional 안에서 AI를 부르면 그동안 DB 커넥션을 붙잡고 있어서,
 *    1GB 서버의 작은 커넥션 풀(기본 10개)이 동시 사용자 몇 명만으로 바닥난다.
 *  - 그래서 [트랜잭션1: 저장·AI 입력 준비] → [AI 호출: 트랜잭션 밖] → [트랜잭션2: 결과 반영]으로 나눈다.
 *  - 같은 클래스 안에서 @Transactional 메서드를 호출하면 스프링 프록시를 거치지 않아 트랜잭션이 안 걸린다.
 *    그래서 별도 빈(이 클래스)으로 분리해 GoalInterviewService가 호출하게 했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoalInterviewTxService {

    /** AI 관심도: 한도에 막힌 시도 (V70) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder limitHits;

    /** AI가 READY라고 했는데 목표 문장이 없을 때 서버가 대신 묻는 질문 */
    static final String GOAL_STATEMENT_QUESTION = "어떤 목표를 이루고 싶으세요?";

    /**
     * 인터뷰 마지막 질문 (2026-10). 정보가 다 모였다고 끝내기 전에 한 번 더 열어 둔다.
     * 질문 수(5개 한도)에는 세지 않고, 답(또는 "없어요")을 받으면 바로 인터뷰를 끝낸다.
     * 답한 내용은 다른 답과 함께 계획 AI 에 "사용자가 인터뷰에서 한 말"로 들어간다.
     */
    static final String EXTRA_NOTE_SLOT = "extraNote";
    static final String EXTRA_NOTE_QUESTION =
            "이 밖에 목표를 짤 때 알아둬야 할 정보가 있을까요? 자세하게 적을수록 목표가 명확해져요.";
    static final List<String> EXTRA_NOTE_QUICK_REPLIES = List.of("없어요", "평일엔 시간이 거의 없어요", "주말에 몰아서 할 수 있어요");
    static final List<String> GOAL_STATEMENT_QUICK_REPLIES = List.of("살 빼기", "자격증 따기", "운동 습관 만들기");

    private final GoalCreationSessionRepository sessionRepository;
    private final GoalCreationMessageRepository messageRepository;
    private final GoalCreationSessionFinder sessionFinder;
    private final UserFinder userFinder;
    private final GoalSlotMerger slotMerger;
    private final GoalJsonConverter jsonConverter;
    private final GoalAiProperties properties;
    private final GoalClock clock;

    /**
     * 임시 목표 구체화 (명세 3 · BE-8).
     * 생성자 대신 필드 주입을 쓴 이유: 기존 테스트가 생성자를 직접 호출하고 있어서,
     * 생성자 인자를 늘리면 기존 테스트가 컴파일되지 않는다. 테스트에서는 null 이며 임시 목표를 쓰지 않는 한 영향이 없다.
     */
    @Autowired(required = false)
    private TemporaryGoalService temporaryGoalService;

    /** 하루 · 주 · 월 한도 (2026-10). 기존 테스트처럼 없으면 예전 하루 · 주 한도 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private GoalAiQuota quota;

    /** 실행 역량 (V47, 필드 주입 이유는 위와 같음) */
    @Autowired(required = false)
    private CapacityProfileReader capacityReader;

    /** AI 호출에 필요한 정보 묶음 (트랜잭션 밖으로 엔티티를 들고 나가지 않기 위함) */
    public record PreparedTurn(Long sessionId, SlotKey answeredSlot, InterviewTurnRequest aiRequest) {
    }

    /** 세션 시작 결과. turn == null이면 AI 호출 없이 첫 질문까지 끝난 것 */
    public record StartResult(Long sessionId, PreparedTurn turn) {
    }

    static com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason hitReason(GoalAiQuota.Period p) {
        return switch (p) {
            case DAY -> com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.DAILY;
            case WEEK -> com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.WEEKLY;
            case MONTH -> com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.MONTHLY;
        };
    }

    // ───────────────────── 시작 ─────────────────────

    public StartResult start(Long userId, GoalSessionStartRequest request) {
        User user = userFinder.findUser(userId);

        if (quota != null) {
            // 2026-10: 하루 · 주 · 월 한도 (GoalAiQuota)
            boolean pro = properties.proLimitFor(userId, user.isCurrentlyPremium());
            GoalAiQuota.Period over = GoalAiQuota.exceeded(quota.sessions(pro),
                    since -> sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, since),
                    clock.startOfUserToday(), clock.startOfUserWeek(), clock.startOfUserMonth());
            if (over != null) {
                if (limitHits != null) limitHits.record(userId, "GOAL_INTERVIEW", hitReason(over));
                throw new CustomException(switch (over) {
                    case DAY -> ErrorCode.GOAL_CREATION_DAILY_LIMIT_EXCEEDED;
                    case WEEK -> ErrorCode.GOAL_CREATION_WEEKLY_LIMIT_EXCEEDED;
                    case MONTH -> ErrorCode.GOAL_CREATION_MONTHLY_LIMIT_EXCEEDED;
                });
            }
        } else {
            long todayCount = sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, clock.startOfUserToday());
            if (todayCount >= properties.sessionLimitFor(userId, user.isCurrentlyPremium())) {
                if (limitHits != null) limitHits.record(userId, "GOAL_INTERVIEW", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.DAILY);
                throw new CustomException(ErrorCode.GOAL_CREATION_DAILY_LIMIT_EXCEEDED);
            }
            int weekly = properties.weeklySessionLimitFor(userId, user.isCurrentlyPremium());
            if (weekly != Integer.MAX_VALUE) {
                LocalDateTime weekStart = clock.startOfUserWeek();
                if (weekStart != null
                        && sessionRepository.countByUser_UserIdAndCreatedAtAfter(userId, weekStart) >= weekly) {
                    if (limitHits != null) limitHits.record(userId, "GOAL_INTERVIEW", com.naleul.naleul.domain.aiUsage.limit.AiLimitHitRecorder.Reason.WEEKLY);
                    throw new CustomException(ErrorCode.GOAL_CREATION_WEEKLY_LIMIT_EXCEEDED);
                }
            }
        }

        // 임시 목표 구체화 (명세 3 · BE-8) · 예전 목표 AI 설계 (2026-10): 내 목표인지 확인. 아니면 400 INVALID_SOURCE_GOAL
        GoalCategory source = null;
        if (request.getSourceGoalId() != null) {
            if (temporaryGoalService == null) {
                throw new CustomException(ErrorCode.INVALID_SOURCE_GOAL);
            }
            source = temporaryGoalService.findOwnedSource(userId, request.getSourceGoalId());
        }

        GoalKind kind = GoalKind.of(request.getGoalType(), request.getGoalSubType(), request.getGoalKindLabel());
        GoalCreationSession session = GoalCreationSession.start(
                user, source, kind,
                jsonConverter.toJson(GoalSlots.empty()),
                clock.now().plusDays(properties.sessionExpireDays()));
        // 꼭 하고 싶은 일 (시작 화면, V42) — 인터뷰가 다시 묻지 않고, 계획에 반드시 넣는다
        List<String> mustDo = GoalMustDo.normalize(request.getMustDoItems());
        if (!mustDo.isEmpty()) session.updateMustDoItems(jsonConverter.toJson(mustDo));
        session.recordPromptVersion(InterviewPromptVersion.CURRENT);   // V67: 프롬프트 버전별 정리 정확도 비교
        sessionRepository.save(session);

        String initial = request.getInitialMessage();
        if ((initial == null || initial.isBlank()) && source != null) {
            // 임시 목표 이름("이사 준비")을 첫 답으로 삼아 바로 다음 질문으로 넘어간다
            initial = source.getGoalCategoryName();
        }
        if (initial == null || initial.isBlank()) {
            // 첫 문장이 없으면 AI 없이 바로 "어떤 목표?"를 묻는다 (명세 7-1)
            askQuestion(session, SlotKey.GOAL_STATEMENT, GOAL_STATEMENT_QUESTION, GOAL_STATEMENT_QUICK_REPLIES);
            return new StartResult(session.getGoalCreationSessionId(), null);
        }

        // 첫 문장(시작 화면에서 직접 쓴 목표)은 "목표가 뭐예요?"에 대한 직접 답
        messageRepository.save(GoalCreationMessage.user(session, initial.strip())
                .withAnswerMeta(SlotKey.GOAL_STATEMENT.key(), AnswerSource.TYPED, null));
        // 첫 문장은 "목표가 뭐예요?"에 대한 답으로 취급 → AI가 goalStatement를 채우고 다음 빈 슬롯을 묻는다
        return new StartResult(session.getGoalCreationSessionId(),
                buildTurn(session, SlotKey.GOAL_STATEMENT, GoalSlots.empty()));
    }

    // ───────────────────── 답변 ─────────────────────

    public PreparedTurn prepareAnswer(Long userId, Long sessionId, GoalAnswerRequest request) {
        GoalCreationSession session = findActive(sessionId, userId);
        if (session.getStatus() != GoalCreationSessionStatus.INTERVIEWING) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        // 마지막 "더 알려줄 정보" 질문에 대한 답 → AI 를 다시 부르지 않고 인터뷰를 끝낸다
        if (EXTRA_NOTE_SLOT.equals(lastAskedTarget(sessionId))) {
            String content = request.getContent() == null ? null : request.getContent().strip();
            boolean skip = request.isSkip() || content == null || content.isEmpty();
            if (!isDuplicateOfPendingAnswer(sessionId, skip ? null : content, skip)) {
                AnswerMeta meta = skip ? AnswerMeta.SKIPPED : answerMeta(sessionId, request, content);
                messageRepository.save((skip ? GoalCreationMessage.userSkip(session, EXTRA_NOTE_SLOT)
                        : GoalCreationMessage.user(session, content))
                        .withAnswerMeta(EXTRA_NOTE_SLOT, meta.source(), meta.chipIndex()));
            }
            session.finishInterview();
            return new PreparedTurn(sessionId, null, null);
        }

        SlotKey answeredSlot = lastAskedSlot(sessionId);
        GoalSlots slots = jsonConverter.toSlots(session.getSlots());

        if (request.isSkip()) {
            if (answeredSlot == null || !answeredSlot.isSkippable()) {
                throw new CustomException(ErrorCode.SLOT_NOT_SKIPPABLE);
            }
            if (!isDuplicateOfPendingAnswer(sessionId, null, true)) {
                messageRepository.save(GoalCreationMessage.userSkip(session, answeredSlot.key())
                        .withAnswerMeta(answeredSlot.key(), AnswerSource.SKIP, null));
            }
            if (!slots.get(answeredSlot).isFilled()) {
                slots = slots.skip(answeredSlot);
                session.updateSlots(jsonConverter.toJson(slots));
            }
        } else {
            String content = request.getContent();
            if (content == null || content.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            content = content.strip();
            if (!isDuplicateOfPendingAnswer(sessionId, content, false)) {
                AnswerMeta meta = answerMeta(sessionId, request, content);
                messageRepository.save(GoalCreationMessage.user(session, content)
                        .withAnswerMeta(answeredSlot != null ? answeredSlot.key() : null, meta.source(), meta.chipIndex()));
            }
        }

        return buildTurn(session, answeredSlot, slots);
    }

    /**
     * AI 결과 반영 (트랜잭션2).
     * @return REJECT로 세션이 중단됐으면 true
     */
    public boolean applyTurn(Long sessionId, SlotKey answeredSlot, InterviewTurnResult result) {
        GoalCreationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_SESSION_NOT_FOUND));
        // AI를 기다리는 사이 사용자가 취소했을 수 있음
        if (session.getStatus() != GoalCreationSessionStatus.INTERVIEWING) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        GoalSlots merged = slotMerger.merge(jsonConverter.toSlots(session.getSlots()), result.extracted(), answeredSlot);
        session.updateSlots(jsonConverter.toJson(merged));
        // V67: 이 답에서 AI 가 뽑은 값을 그 답 옆에 남긴다 (정리 화면에서 고친 항목 → 어느 답에서 나왔는지)
        recordExtracted(sessionId, result);
        // 기한 대비 시작 시점으로 정할 수 있으면 그게 먼저 (예: 11/14 시험을 11/1부터 → 즉흥형, 오늘부터 → 계획형)
        PlanningStyle byDeadline = styleFromDeadline(merged, clock.today());
        session.updatePlanningStyleGuess(byDeadline != null ? byDeadline
                : result.planningStyleSignal().style().toPlanningStyleOrNull());

        if (result.nextAction() == NextAction.REJECT) {
            session.abandon(com.naleul.naleul.domain.goalCreation.enums.AbandonReason.AI_REJECT);
            return true;
        }

        // ① 질문 5개를 다 썼으면 AI가 뭐라 하든 마무리 (명세 3-2의 1번) — 마지막 "더 알려줄 정보" 질문은 한 번 더
        if (session.isQuestionLimitReached()) {
            askExtraNoteOrFinish(session, merged);
            return false;
        }

        boolean aiWantsToAsk = result.nextAction() == NextAction.ASK
                && result.targetSlot() != null
                && result.question() != null && !result.question().isBlank();

        if (aiWantsToAsk) {
            askQuestion(session, result.targetSlot(), result.question().strip(), limit(result.quickReplies()));
        } else if (!merged.hasGoalStatement()) {
            // ② AI는 끝내자는데 필수 슬롯이 비어 있음 → 서버가 대신 묻는다
            askQuestion(session, SlotKey.GOAL_STATEMENT, GOAL_STATEMENT_QUESTION, GOAL_STATEMENT_QUICK_REPLIES);
        } else {
            askExtraNoteOrFinish(session, merged);
        }
        return false;
    }

    /** 정보가 다 모이면 바로 끝내지 않고 "이 밖에 알아둬야 할 정보"를 한 번 묻는다. 이미 물었으면 끝 */
    private void askExtraNoteOrFinish(GoalCreationSession session, GoalSlots slots) {
        Long sessionId = session.getGoalCreationSessionId();
        boolean asked = messageRepository.findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(sessionId)
                .stream().anyMatch(m -> EXTRA_NOTE_SLOT.equals(m.getTargetSlot()));
        if (asked || !slots.hasGoalStatement()) {
            session.finishInterview();
            return;
        }
        // 질문 수(5개)에는 세지 않는다 (recordQuestion 을 부르지 않음)
        messageRepository.save(GoalCreationMessage.assistant(
                session, EXTRA_NOTE_QUESTION, EXTRA_NOTE_SLOT, jsonConverter.toJson(EXTRA_NOTE_QUICK_REPLIES)));
    }

    /**
     * 기한 대비 시작 시점으로 실행 성향을 정한다 (2026-10). 판단할 수 없으면 null → AI 추정값을 쓴다.
     *  - 오늘~3일 안에 바로 시작 → 계획형
     *  - 남은 기간의 30% 이상을 미뤘다가 시작 → 즉흥형 (예: 오늘 10/21, 시험 11/14, "11월부터" → 11일/24일)
     */
    static PlanningStyle styleFromDeadline(GoalSlots slots, LocalDate today) {
        if (slots == null || !slots.deadline().isFilled()) return null;
        DeadlineSlot d = slots.deadline().value();
        if (d == null || d.endDate() == null || d.isFlexible()) return null;
        long total = ChronoUnit.DAYS.between(today, d.endDate());
        if (total <= 0) return null;
        LocalDate start = d.startDate() != null ? d.startDate() : today;
        long delay = ChronoUnit.DAYS.between(today, start);
        if (d.startDate() == null) return null;   // 시작 시점을 말하지 않았으면 AI 판단에 맡긴다
        if (delay <= 3) return PlanningStyle.PLANNER;
        return delay * 10 >= total * 3 ? PlanningStyle.SPONTANEOUS : null;
    }

    // ───────────────────── 조기 종료 / 슬롯 수정 / 취소 ─────────────────────

    public void finishInterview(Long userId, Long sessionId) {
        GoalCreationSession session = findActive(sessionId, userId);
        if (session.getStatus() != GoalCreationSessionStatus.INTERVIEWING) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
        if (!jsonConverter.toSlots(session.getSlots()).hasGoalStatement()) {
            throw new CustomException(ErrorCode.REQUIRED_SLOT_MISSING);
        }
        session.finishInterview();
    }

    /** 정리 확인 화면/초안 화면에서 칩으로 수정. 인터뷰 중에는 막는다(AI 병합과 섞이지 않게) */
    public void updateSlots(Long userId, Long sessionId, GoalSlotsUpdateRequest request) {
        GoalCreationSession session = findActive(sessionId, userId);
        GoalCreationSessionStatus status = session.getStatus();
        if (status != GoalCreationSessionStatus.READY_TO_GENERATE && status != GoalCreationSessionStatus.DRAFT_READY) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        GoalSlots slots = jsonConverter.toSlots(session.getSlots());
        if (request.getGoalStatement() != null) {
            if (request.getGoalStatement().isBlank()) throw new CustomException(ErrorCode.REQUIRED_SLOT_MISSING);
            slots = slots.withGoalStatement(Slot.filled(request.getGoalStatement().strip()));
        }
        if (request.getMetric() != null) slots = slots.withMetric(Slot.filled(request.getMetric()));
        if (request.getMotivation() != null) slots = slots.withMotivation(Slot.filled(request.getMotivation().strip()));
        if (request.getApproach() != null) slots = slots.withApproach(Slot.filled(request.getApproach().strip()));
        if (request.getDeadline() != null) {
            DeadlineSlot d = request.getDeadline();
            if (d.endDate() == null || !d.endDate().isAfter(clock.today())
                    || (d.startDate() != null && (d.startDate().isBefore(clock.today()) || !d.endDate().isAfter(d.startDate())))) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            slots = slots.withDeadline(Slot.filled(request.getDeadline()));
        }
        if (request.getPracticePreference() != null) {
            slots = slots.withPracticePreference(Slot.filled(request.getPracticePreference()));
        }
        if (request.getMustDoItems() != null) {
            List<String> mustDo = GoalMustDo.normalize(request.getMustDoItems());
            session.updateMustDoItems(mustDo.isEmpty() ? null : jsonConverter.toJson(mustDo));
        }
        session.updateSlots(jsonConverter.toJson(slots));
    }

    public void cancel(Long userId, Long sessionId) {
        GoalCreationSession session = sessionFinder.findOwned(sessionId, userId);
        session.abandon(com.naleul.naleul.domain.goalCreation.enums.AbandonReason.USER_CANCEL);   // 이미 종료 상태면 409
    }

    /** 시작 직후 AI 호출이 실패하면 클라이언트가 sessionId를 모르므로, 이 세션은 정리해 둔다 */
    public void abandonQuietly(Long sessionId) {
        sessionRepository.findById(sessionId)
                .filter(s -> !s.getStatus().isTerminal())
                .ifPresent(s -> s.abandon(com.naleul.naleul.domain.goalCreation.enums.AbandonReason.AI_ERROR));
    }

    // ───────────────────── 내부 ─────────────────────

    /** 소유권 + 만료 확인. 만료된 세션은 더 이상 진행할 수 없음 */
    private GoalCreationSession findActive(Long sessionId, Long userId) {
        GoalCreationSession session = sessionFinder.findOwned(sessionId, userId);
        if (session.getStatus().isTerminal() || session.isExpired(clock.now())) {
            throw new CustomException(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
        return session;
    }

    private void askQuestion(GoalCreationSession session, SlotKey slot, String question, List<String> quickReplies) {
        session.recordQuestion();
        messageRepository.save(GoalCreationMessage.assistant(
                session, question, slot.key(), jsonConverter.toJson(quickReplies)));
    }

    private PreparedTurn buildTurn(GoalCreationSession session, SlotKey answeredSlot, GoalSlots slots) {
        Long sessionId = session.getGoalCreationSessionId();
        List<ConversationMessage> history = messageRepository
                .findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(sessionId)
                .stream()
                .map(m -> new ConversationMessage(m.getRole(), m.getContent(), m.getTargetSlot(), m.isSkip()))
                .toList();
        int remaining = Math.max(0, properties.maxQuestions() - session.getQuestionCount());

        InterviewTurnRequest aiRequest = new InterviewTurnRequest(slots, history, remaining, clock.today(),
                sourceContext(session), session.kind(), jsonConverter.toStringList(session.getMustDoItems()),
                capacityNote(session));
        return new PreparedTurn(sessionId, answeredSlot, aiRequest);
    }

    /**
     * 실행 기록 한 줄 (V47). 기록이 있으면 "현재 수준은 안 물어도 돼", 없으면 "현재 수준도 물어".
     * 계산이 실패해도 인터뷰는 계속돼야 해서 null 로 넘긴다.
     */
    private String capacityNote(GoalCreationSession session) {
        if (capacityReader == null || session.getUser() == null) return null;
        try {
            return PersonalPrompt.forInterview(capacityReader.read(session.getUser().getUserId()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 임시 목표에서 시작한 세션이면 "이미 있는 할 일" 문맥, 아니면 null */
    private String sourceContext(GoalCreationSession session) {
        if (session.getSourceGoalCategory() == null || temporaryGoalService == null) return null;
        return temporaryGoalService.buildContext(session.getSourceGoalCategory());
    }

    private String lastAskedTarget(Long sessionId) {
        return messageRepository
                .findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(sessionId, MessageRole.ASSISTANT)
                .map(GoalCreationMessage::getTargetSlot)
                .orElse(null);
    }

    private SlotKey lastAskedSlot(Long sessionId) {
        return messageRepository
                .findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(sessionId, MessageRole.ASSISTANT)
                .map(m -> SlotKey.fromKey(m.getTargetSlot()))
                .orElse(null);
    }

    /**
     * 이전 요청에서 사용자 메시지는 저장됐지만 AI 호출이 실패(502)한 경우,
     * 클라이언트가 "다시 보내기"로 같은 답을 보내면 메시지를 또 저장하지 않는다 (명세 8-4).
     */
    private boolean isDuplicateOfPendingAnswer(Long sessionId, String content, boolean skip) {
        Optional<GoalCreationMessage> last =
                messageRepository.findTopBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdDesc(sessionId);
        return last.isPresent()
                && last.get().getRole() == MessageRole.USER
                && last.get().isSkip() == skip
                && Objects.equals(last.get().getContent(), content);
    }

    /** 답 방식 (V67) */
    record AnswerMeta(AnswerSource source, Integer chipIndex) {
        static final AnswerMeta SKIPPED = new AnswerMeta(AnswerSource.SKIP, null);
    }

    /**
     * 앱이 보낸 답 방식을 쓰고, 없으면(예전 버전) 직전 질문의 칩 글자와 똑같은지로 추정한다.
     * 칩 번호도 서버가 칩 목록에서 다시 찾는다 (앱이 보낸 번호와 글자가 어긋나면 글자 기준).
     */
    AnswerMeta answerMeta(Long sessionId, GoalAnswerRequest request, String content) {
        List<String> chips = messageRepository
                .findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(sessionId, MessageRole.ASSISTANT)
                .map(m -> jsonConverter.toStringList(m.getQuickReplies()))
                .orElse(List.of());
        int matched = content == null ? -1 : chips.indexOf(content.strip());
        if (request.getAnswerSource() == AnswerSource.TYPED) return new AnswerMeta(AnswerSource.TYPED, null);
        if (request.getAnswerSource() == AnswerSource.CHIP) {
            Integer idx = matched >= 0 ? Integer.valueOf(matched) : request.getChipIndex();
            return new AnswerMeta(AnswerSource.CHIP, idx);
        }
        return matched >= 0 ? new AnswerMeta(AnswerSource.CHIP, matched) : new AnswerMeta(AnswerSource.TYPED, null);
    }

    /** 가장 최근 사용자 답에 이번 AI 추출값을 붙인다 (실패해도 인터뷰는 계속) */
    private void recordExtracted(Long sessionId, InterviewTurnResult result) {
        try {
            messageRepository
                    .findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(sessionId, MessageRole.USER)
                    .filter(m -> m.getExtracted() == null)
                    .ifPresent(m -> m.recordExtracted(jsonConverter.toJson(result.extracted())));
        } catch (RuntimeException e) {
            log.warn("[GoalInterview] 추출값 기록 실패 sessionId={} reason={}", sessionId, e.getMessage());
        }
    }

    private List<String> limit(List<String> quickReplies) {
        return quickReplies.stream().filter(Objects::nonNull).limit(4).toList();
    }
}
