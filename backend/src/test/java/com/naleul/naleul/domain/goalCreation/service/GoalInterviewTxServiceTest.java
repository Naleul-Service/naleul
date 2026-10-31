package com.naleul.naleul.domain.goalCreation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.config.GoalAiProperties;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalAnswerRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSessionStartRequest;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.Slot;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage;
import com.naleul.naleul.domain.goalCreation.entity.GoalCreationSession;
import com.naleul.naleul.domain.goalCreation.enums.GoalCreationSessionStatus;
import com.naleul.naleul.domain.goalCreation.enums.MessageRole;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationMessageRepository;
import com.naleul.naleul.domain.goalCreation.repository.GoalCreationSessionRepository;
import com.naleul.naleul.domain.goalCreation.support.GoalClock;
import com.naleul.naleul.domain.goalCreation.support.GoalJsonConverter;
import com.naleul.naleul.domain.goalCreation.support.GoalSlotMerger;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * GoalInterviewTxService 단위 테스트.
 * GoalSlotMerger, GoalJsonConverter는 순수 로직이라 Mock 대신 실제 객체를 써서
 * "슬롯이 실제로 어떻게 저장되는지"까지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class GoalInterviewTxServiceTest {

    @Mock private GoalCreationSessionRepository sessionRepository;
    @Mock private GoalCreationMessageRepository messageRepository;
    @Mock private GoalCreationSessionFinder sessionFinder;
    @Mock private UserFinder userFinder;
    @Mock private GoalClock clock;

    private GoalInterviewTxService txService;
    private GoalJsonConverter jsonConverter;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jsonConverter = new GoalJsonConverter(objectMapper);
        GoalAiProperties properties = new GoalAiProperties(
                "fake", null, null, 20, 80, 5, 300, 5, 5, 10, 7, 3, 10, 20, java.util.List.of(), 3);

        txService = new GoalInterviewTxService(sessionRepository, messageRepository, sessionFinder,
                userFinder, new GoalSlotMerger(), jsonConverter, properties, clock);

        user = User.builder().userId(USER_ID).build();
    }

    private GoalCreationSession interviewingSession(GoalSlots slots) {
        return GoalCreationSession.start(user, null, jsonConverter.toJson(slots), NOW.plusDays(7));
    }

    private InterviewTurnResult ask(SlotKey slot) {
        return new InterviewTurnResult(ExtractedSlots.none(), PlanningStyleSignal.unknown(),
                NextAction.ASK, slot, "질문이에요", List.of("a", "b"), null);
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        void 첫_문장이_없으면_AI없이_goalStatement를_묻는다() {
            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(clock.startOfUserToday()).willReturn(NOW.toLocalDate().atStartOfDay());
            given(clock.now()).willReturn(NOW);
            given(sessionRepository.countByUser_UserIdAndCreatedAtAfter(any(), any())).willReturn(0L);

            GoalInterviewTxService.StartResult result = txService.start(USER_ID, new GoalSessionStartRequest());

            assertThat(result.turn()).isNull();   // AI 호출 불필요
            ArgumentCaptor<GoalCreationMessage> captor = ArgumentCaptor.forClass(GoalCreationMessage.class);
            then(messageRepository).should().save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(captor.getValue().getTargetSlot()).isEqualTo("goalStatement");
        }

        @Test
        void 첫_문장이_있으면_goalStatement에_대한_답으로_AI에_넘긴다() {
            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(clock.startOfUserToday()).willReturn(NOW.toLocalDate().atStartOfDay());
            given(clock.now()).willReturn(NOW);
            given(clock.today()).willReturn(NOW.toLocalDate());
            given(sessionRepository.countByUser_UserIdAndCreatedAtAfter(any(), any())).willReturn(0L);

            GoalInterviewTxService.StartResult result = txService.start(USER_ID,
                    GoalSessionStartRequest.builder().initialMessage("살 10kg 빼고 싶어").build());

            assertThat(result.turn()).isNotNull();
            assertThat(result.turn().answeredSlot()).isEqualTo(SlotKey.GOAL_STATEMENT);
            assertThat(result.turn().aiRequest().remainingQuestions()).isEqualTo(5);
        }

        @Test
        void 하루_한도를_넘으면_429() {
            given(userFinder.findUser(USER_ID)).willReturn(user);
            given(clock.startOfUserToday()).willReturn(NOW.toLocalDate().atStartOfDay());
            given(sessionRepository.countByUser_UserIdAndCreatedAtAfter(any(), any())).willReturn(5L);

            assertThatThrownBy(() -> txService.start(USER_ID, new GoalSessionStartRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_CREATION_DAILY_LIMIT_EXCEEDED);
            then(sessionRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("applyTurn()")
    class ApplyTurn {

        @Test
        void AI가_계속_ASK해도_질문_5개를_다_쓰면_AI_질문_대신_마지막_추가_정보만_묻는다() {
            GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("감량"));
            GoalCreationSession session = interviewingSession(slots);
            for (int i = 0; i < 5; i++) session.recordQuestion();
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

            boolean rejected = txService.applyTurn(SESSION_ID, SlotKey.MOTIVATION, ask(SlotKey.MOTIVATION));

            // 질문 수 한도 뒤에는 AI 의 6번째 질문 대신 "이 밖에 알아둬야 할 정보" 를 한 번 묻는다 (질문 수에는 안 셈)
            assertThat(rejected).isFalse();
            assertThat(session.getQuestionCount()).isEqualTo(5);
            then(messageRepository).should(org.mockito.Mockito.times(1)).save(any());
        }

        @Test
        void 질문_5개를_다_쓰고_추가_정보도_물었으면_READY로_강제_전이한다() {
            GoalSlots slots = GoalSlots.empty().withGoalStatement(Slot.filled("감량"));
            GoalCreationSession session = interviewingSession(slots);
            org.springframework.test.util.ReflectionTestUtils.setField(session, "goalCreationSessionId", SESSION_ID);
            for (int i = 0; i < 5; i++) session.recordQuestion();
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
            given(messageRepository.findAllBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdAsc(SESSION_ID))
                    .willReturn(java.util.List.of(com.naleul.naleul.domain.goalCreation.entity.GoalCreationMessage.assistant(
                            session, "더 알려줄 게 있나요?", GoalInterviewTxService.EXTRA_NOTE_SLOT, null)));

            txService.applyTurn(SESSION_ID, SlotKey.MOTIVATION, ask(SlotKey.MOTIVATION));

            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.READY_TO_GENERATE);
            then(messageRepository).should(never()).save(any());
        }

        @Test
        void ASK면_질문수를_올리고_AI질문을_저장한다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

            txService.applyTurn(SESSION_ID, SlotKey.GOAL_STATEMENT, ask(SlotKey.METRIC));

            assertThat(session.getQuestionCount()).isEqualTo(1);
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.INTERVIEWING);
            then(messageRepository).should().save(any(GoalCreationMessage.class));
        }

        @Test
        void REJECT면_세션을_ABANDONED로_바꾸고_true를_반환한다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
            InterviewTurnResult reject = new InterviewTurnResult(null, null, NextAction.REJECT, null, null, null, null);

            boolean rejected = txService.applyTurn(SESSION_ID, SlotKey.GOAL_STATEMENT, reject);

            assertThat(rejected).isTrue();
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.ABANDONED);
        }

        @Test
        void AI가_READY인데_goalStatement가_비어있으면_서버가_goalStatement를_묻는다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
            InterviewTurnResult ready = new InterviewTurnResult(null, null, NextAction.READY, null, null, null, null);

            txService.applyTurn(SESSION_ID, SlotKey.GOAL_STATEMENT, ready);

            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.INTERVIEWING);
            ArgumentCaptor<GoalCreationMessage> captor = ArgumentCaptor.forClass(GoalCreationMessage.class);
            then(messageRepository).should().save(captor.capture());
            assertThat(captor.getValue().getTargetSlot()).isEqualTo("goalStatement");
        }

        @Test
        void 추출된_슬롯과_성향을_세션에_저장한다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
            InterviewTurnResult result = new InterviewTurnResult(
                    new ExtractedSlots("몸무게 10kg 감량", null, null, null, null, List.of()),
                    new PlanningStyleSignal(PlanningStyleGuess.PLANNER, "미리미리"),
                    NextAction.ASK, SlotKey.METRIC, "지금 몇 kg이세요?", List.of(), null);

            txService.applyTurn(SESSION_ID, SlotKey.GOAL_STATEMENT, result);

            GoalSlots saved = jsonConverter.toSlots(session.getSlots());
            assertThat(saved.goalStatement().value()).isEqualTo("몸무게 10kg 감량");
            assertThat(session.getPlanningStyleGuess()).isEqualTo(PlanningStyle.PLANNER);
        }

        @Test
        void AI를_기다리는_사이_취소된_세션이면_409() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            session.abandon();
            given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

            assertThatThrownBy(() -> txService.applyTurn(SESSION_ID, SlotKey.METRIC, ask(SlotKey.MOTIVATION)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
    }

    @Nested
    @DisplayName("prepareAnswer()")
    class PrepareAnswer {

        @Test
        void 필수_슬롯을_건너뛰면_400_SLOT_NOT_SKIPPABLE() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);
            GoalCreationMessage lastQuestion = GoalCreationMessage.assistant(session, "목표?", "goalStatement", null);
            given(messageRepository.findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(any(), any()))
                    .willReturn(Optional.of(lastQuestion));

            GoalAnswerRequest skip = GoalAnswerRequest.builder().skip(true).build();

            assertThatThrownBy(() -> txService.prepareAnswer(USER_ID, SESSION_ID, skip))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SLOT_NOT_SKIPPABLE);
        }

        @Test
        void 선택_슬롯을_건너뛰면_SKIPPED로_저장한다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty().withGoalStatement(Slot.filled("감량")));
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);
            given(clock.today()).willReturn(NOW.toLocalDate());
            GoalCreationMessage lastQuestion = GoalCreationMessage.assistant(session, "몇 kg?", "metric", null);
            given(messageRepository.findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(any(), any()))
                    .willReturn(Optional.of(lastQuestion));
            given(messageRepository.findTopBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdDesc(any()))
                    .willReturn(Optional.of(lastQuestion));

            GoalInterviewTxService.PreparedTurn turn = txService.prepareAnswer(USER_ID, SESSION_ID,
                    GoalAnswerRequest.builder().skip(true).build());

            assertThat(turn.answeredSlot()).isEqualTo(SlotKey.METRIC);
            assertThat(jsonConverter.toSlots(session.getSlots()).metric().status().name()).isEqualTo("SKIPPED");
        }

        @Test
        void 이전_AI호출이_실패한_뒤_같은_답을_다시_보내면_메시지를_중복_저장하지_않는다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty().withGoalStatement(Slot.filled("감량")));
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);
            given(clock.today()).willReturn(NOW.toLocalDate());
            GoalCreationMessage lastQuestion = GoalCreationMessage.assistant(session, "몇 kg?", "metric", null);
            GoalCreationMessage pendingAnswer = GoalCreationMessage.user(session, "78에서 68");
            given(messageRepository.findTopBySession_GoalCreationSessionIdAndRoleOrderByGoalCreationMessageIdDesc(any(), any()))
                    .willReturn(Optional.of(lastQuestion));
            given(messageRepository.findTopBySession_GoalCreationSessionIdOrderByGoalCreationMessageIdDesc(any()))
                    .willReturn(Optional.of(pendingAnswer));

            txService.prepareAnswer(USER_ID, SESSION_ID, GoalAnswerRequest.builder().content("78에서 68").build());

            then(messageRepository).should(never()).save(any());
        }

        @Test
        void 인터뷰가_끝난_세션에_답하면_409() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty().withGoalStatement(Slot.filled("감량")));
            session.finishInterview();
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);

            assertThatThrownBy(() -> txService.prepareAnswer(USER_ID, SESSION_ID,
                    GoalAnswerRequest.builder().content("답").build()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }

        @Test
        void 만료된_세션에_답하면_409() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW.plusDays(8));

            assertThatThrownBy(() -> txService.prepareAnswer(USER_ID, SESSION_ID,
                    GoalAnswerRequest.builder().content("답").build()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.GOAL_SESSION_INVALID_STATE);
        }
    }

    @Nested
    @DisplayName("finishInterview()")
    class FinishInterview {

        @Test
        void goalStatement_없이_조기종료하면_400_REQUIRED_SLOT_MISSING() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty());
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);

            assertThatThrownBy(() -> txService.finishInterview(USER_ID, SESSION_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.REQUIRED_SLOT_MISSING);
            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.INTERVIEWING);
        }

        @Test
        void goalStatement가_있으면_READY_TO_GENERATE로_전이한다() {
            GoalCreationSession session = interviewingSession(GoalSlots.empty().withGoalStatement(Slot.filled("감량")));
            given(sessionFinder.findOwned(SESSION_ID, USER_ID)).willReturn(session);
            given(clock.now()).willReturn(NOW);

            txService.finishInterview(USER_ID, SESSION_ID);

            assertThat(session.getStatus()).isEqualTo(GoalCreationSessionStatus.READY_TO_GENERATE);
        }
    }
}
