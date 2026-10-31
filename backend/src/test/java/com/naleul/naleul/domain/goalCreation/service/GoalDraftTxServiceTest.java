package com.naleul.naleul.domain.goalCreation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalDraftRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftResponse;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.entity.GoalDraft;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import com.naleul.naleul.domain.goalCreation.repository.GoalDraftRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.enums.PlanningStyleSource;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GoalDraftTxServiceTest {

    @Mock
    private GoalDraftRepository draftRepository;
    @Mock
    private GoalCreationSessionFinder sessionFinder;
    @Mock
    private GoalClock clock;

    private GoalDraftTxService txService;
    private GoalJsonConverter jsonConverter;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final Long DRAFT_ID = 555L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    private User user;

    @BeforeEach
    void setUp() {
        jsonConverter = new GoalJsonConverter(new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        GoalAiProperties properties = new GoalAiProperties("fake", null, null, 20, 80, 5, 300, 5, 5, 10, 7, 3, 10, 20, java.util.List.of(), 3);
        txService = new GoalDraftTxService(draftRepository, sessionFinder, jsonConverter, properties, clock);
        user = User.builder().userId(USER_ID).build();
    }

    private GoalCreationSession readySession() {
        GoalCreationSession session = GoalCreationSession.start(user, null,
                jsonConverter.toJson(GoalSlots.empty().withGoalStatement(Slot.filled("감량"))), NOW.plusDays(7));
        session.finishInterview();   // READY_TO_GENERATE
        return session;
    }

    private void givenRequestable(GoalCreationSession session) {
        given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
        given(clock.now()).willReturn(NOW);
    }

    @Nested
    @DisplayName("requestDraft()")
    class RequestDraft {

        @Test
        void 첫_생성이면_version1로_만들고_세션을_GENERATING으로_바꾼다() {
            GoalCreationSession session = readySession();
            givenRequestable(session);
            given(clock.today()).willReturn(NOW.toLocalDate());
            given(clock.startOfUserToday()).willReturn(NOW.toLocalDate().atStartOfDay());
            given(draftRepository.save(any(GoalDraft.class))).willAnswer(inv -> inv.getArgument(0));

            GoalDraftTxService.GenerationJob job = txService.requestDraft(USER_ID, SESSION_ID, new GoalDraftRequest());

            assertThat(job.version()).isEqualTo(1);
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.GENERATING);
            assertThat(job.aiRequest().previousPlan()).isNull();
            assertThat(job.aiRequest().planningStyle()).isEqualTo(PlanningStyle.PLANNER);   // 아무 정보 없으면 기본값
        }

        @Test
        void 이미_생성_중이면_409() {
            GoalCreationSession session = readySession();
            session.startGenerating();
            givenRequestable(session);

            assertThatThrownBy(() -> txService.requestDraft(USER_ID, SESSION_ID, new GoalDraftRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
            then(draftRepository).should(never()).save(any());
        }

        @Test
        void 인터뷰_중이면_409() {
            GoalCreationSession session = GoalCreationSession.start(user, null,
                    jsonConverter.toJson(GoalSlots.empty().withGoalStatement(Slot.filled("감량"))), NOW.plusDays(7));
            givenRequestable(session);
            given(clock.startOfUserToday()).willReturn(NOW.toLocalDate().atStartOfDay());

            assertThatThrownBy(() -> txService.requestDraft(USER_ID, SESSION_ID, new GoalDraftRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        @Test
        void 세션당_생성_한도를_넘으면_429_DRAFT_REGENERATE_LIMIT() {
            givenRequestable(readySession());
            given(draftRepository.countBySession_GoalCreationSessionId(SESSION_ID)).willReturn(5L);

            assertThatThrownBy(() -> txService.requestDraft(USER_ID, SESSION_ID, new GoalDraftRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DRAFT_REGENERATE_LIMIT);
        }
    }

    @Nested
    @DisplayName("resolvePlanningStyle() — 명세 3-3 우선순위")
    class ResolvePlanningStyle {

        @Test
        void 사용자가_초안화면에서_고른_값이_최우선() {
            user.updatePlanningStyle(PlanningStyle.PLANNER, PlanningStyleSource.USER_SET);
            GoalCreationSession session = readySession();
            session.updatePlanningStyleGuess(PlanningStyle.PLANNER);

            assertThat(txService.resolvePlanningStyle(PlanningStyle.SPONTANEOUS, session))
                    .isEqualTo(PlanningStyle.SPONTANEOUS);
        }

        @Test
        void 프로필_USER_SET이_대화_추정보다_우선() {
            user.updatePlanningStyle(PlanningStyle.SPONTANEOUS, PlanningStyleSource.USER_SET);
            GoalCreationSession session = readySession();
            session.updatePlanningStyleGuess(PlanningStyle.PLANNER);

            assertThat(txService.resolvePlanningStyle(null, session)).isEqualTo(PlanningStyle.SPONTANEOUS);
        }

        @Test
        void 대화_추정이_프로필_AI추정보다_우선() {
            user.updatePlanningStyle(PlanningStyle.PLANNER, PlanningStyleSource.AI_INFERRED);
            GoalCreationSession session = readySession();
            session.updatePlanningStyleGuess(PlanningStyle.SPONTANEOUS);

            assertThat(txService.resolvePlanningStyle(null, session)).isEqualTo(PlanningStyle.SPONTANEOUS);
        }
    }

    @Nested
    @DisplayName("getDraft() / completeDraft()")
    class Polling {

        @Test
        void GENERATING이_3분을_넘으면_조회할때_AI_TIMEOUT으로_실패처리하고_세션을_되돌린다() {
            GoalCreationSession session = readySession();
            session.startGenerating();
            GoalDraft draft = GoalDraft.generating(session, 1, PlanningStyle.PLANNER, null);
            ReflectionTestUtils.setField(draft, "updatedAt", NOW.minusMinutes(4));   // @UpdateTimestamp 대신 직접 세팅

            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);
            given(draftRepository.findByGoalDraftIdAndSession_GoalCreationSessionId(DRAFT_ID, SESSION_ID))
                    .willReturn(Optional.of(draft));
            given(draftRepository.findForUpdate(DRAFT_ID)).willReturn(Optional.of(draft));

            GoalDraftResponse response = txService.getDraft(USER_ID, SESSION_ID, DRAFT_ID);

            assertThat(response.status()).isEqualTo(GoalDraftStatus.FAILED);
            assertThat(response.errorCode()).isEqualTo("AI_TIMEOUT");
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.READY_TO_GENERATE);
        }

        @Test
        void 생성_중이면_진행단계와_폴링간격을_내려준다() {
            GoalCreationSession session = readySession();
            session.startGenerating();
            GoalDraft draft = GoalDraft.generating(session, 1, PlanningStyle.PLANNER, null);
            ReflectionTestUtils.setField(draft, "createdAt", NOW.minusSeconds(20));
            ReflectionTestUtils.setField(draft, "updatedAt", NOW.minusSeconds(20));

            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);
            given(draftRepository.findByGoalDraftIdAndSession_GoalCreationSessionId(DRAFT_ID, SESSION_ID))
                    .willReturn(Optional.of(draft));

            GoalDraftResponse response = txService.getDraft(USER_ID, SESSION_ID, DRAFT_ID);

            assertThat(response.status()).isEqualTo(GoalDraftStatus.GENERATING);
            assertThat(response.progressStep()).isEqualTo("MILESTONES");
            assertThat(response.pollAfterMs()).isEqualTo(2000L);
        }

        @Test
        void 좀비로_실패처리된_뒤_늦게_온_결과는_무시한다() {
            GoalCreationSession session = readySession();
            session.startGenerating();
            GoalDraft draft = GoalDraft.generating(session, 1, PlanningStyle.PLANNER, null);
            draft.markFailed("AI_TIMEOUT");
            session.markGenerationFailed();
            given(draftRepository.findForUpdate(DRAFT_ID)).willReturn(Optional.of(draft));

            txService.completeDraft(DRAFT_ID, null, null);

            assertThat(draft.getStatus()).isEqualTo(GoalDraftStatus.FAILED);
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.READY_TO_GENERATE);
        }
    }
}
