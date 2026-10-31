package com.naleul.naleul.domain.goalCreation.reader;

import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.response.*;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationMessageRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.service.GoalCreationSessionFinder;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.support.GoalSummaryBuilder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus.*;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalCreationSessionReader {

    /** 이어하기 대상: 종료(CONFIRMED/ABANDONED)가 아닌 상태 */
    public static final EnumSet<GoalCreationSessionStatus> ACTIVE_STATUSES =
            EnumSet.of(INTERVIEWING, READY_TO_GENERATE, GENERATING, DRAFT_READY);

    private final GoalCreationSessionRepository sessionRepository;
    private final GoalCreationMessageRepository messageRepository;
    private final GoalDraftRepository draftRepository;
    private final GoalCreationSessionFinder sessionFinder;
    private final GoalJsonConverter jsonConverter;
    private final GoalSummaryBuilder summaryBuilder;
    private final GoalAiProperties properties;
    private final GoalClock clock;

    /**
     * ⚠️ 소유권 검증 없이 재조회. 호출 전에 반드시 Service에서 소유권 확인이 끝나 있어야 함.
     * (GoalCategoryReader.getResponse와 같은 규칙) Controller에서 직접 호출 금지.
     */
    public GoalSessionTurnResponse getTurnResponse(Long sessionId) {
        GoalCreationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.GOAL_SESSION_NOT_FOUND));
        GoalSlots slots = jsonConverter.toSlots(session.getSlots());

        GoalMessageResponse lastQuestion = null;
        if (session.getStatus() == INTERVIEWING) {
            lastQuestion = messageRepository
                    .findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(sessionId, MessageRole.ASSISTANT)
                    .map(this::toMessage)
                    .orElse(null);
        }

        return new GoalSessionTurnResponse(
                session.getGoalCreationSessionId(),
                session.getStatus(),
                session.getQuestionCount(),
                properties.maxQuestions(),
                canFinishEarly(session, slots),
                lastQuestion,
                slots,
                summaryOrNull(session, slots),
                jsonConverter.toStringList(session.getMustDoItems())
        );
    }

    public GoalSessionDetailResponse getSession(Long sessionId, Long userId) {
        GoalCreationSession session = sessionFinder.findOwned(sessionId, userId);
        return toDetail(session);
    }

    /** 진행 중인 최신 세션. 없거나 만료됐으면 empty → 컨트롤러가 204 */
    public Optional<GoalSessionDetailResponse> getActiveSession(Long userId) {
        return sessionRepository
                .findFirstByUser_UserIdAndStatusInOrderByCreatedAtDesc(userId, ACTIVE_STATUSES)
                .filter(s -> !s.isExpired(clock.now()))
                .map(this::toDetail);
    }

    private GoalSessionDetailResponse toDetail(GoalCreationSession session) {
        Long sessionId = session.getGoalCreationSessionId();
        GoalSlots slots = jsonConverter.toSlots(session.getSlots());
        List<GoalMessageResponse> messages = messageRepository
                .findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(sessionId)
                .stream()
                .map(this::toMessage)
                .toList();
        GoalDraftBriefResponse latestDraft = draftRepository
                .findTopBySession_GoalCreationSessionIdOrderByVersionDesc(sessionId)
                .map(GoalDraftBriefResponse::from)
                .orElse(null);

        return new GoalSessionDetailResponse(
                sessionId,
                session.getStatus(),
                session.getQuestionCount(),
                properties.maxQuestions(),
                canFinishEarly(session, slots),
                messages,
                slots,
                summaryOrNull(session, slots),
                latestDraft,
                jsonConverter.toStringList(session.getMustDoItems())
        );
    }

    private GoalMessageResponse toMessage(GoalCreationMessage m) {
        return GoalMessageResponse.from(m, jsonConverter.toStringList(m.getQuickReplies()));
    }

    /** "바로 계획 만들기" 버튼: 인터뷰 중이고 목표 문장이 채워졌을 때만 */
    private boolean canFinishEarly(GoalCreationSession session, GoalSlots slots) {
        return session.getStatus() == INTERVIEWING && slots.hasGoalStatement();
    }

    private GoalSummaryResponse summaryOrNull(GoalCreationSession session, GoalSlots slots) {
        if (session.getStatus() == INTERVIEWING || session.getStatus().isTerminal()) return null;
        return summaryBuilder.build(slots, session.getPlanningStyleGuess(), clock.today());
    }
}
