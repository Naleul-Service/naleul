package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.client.*;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalAnswerRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSessionStartRequest;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.goalCreation.dto.slot.SlotKey;
import com.naleul.naleul.domain.goalCreation.reader.GoalCreationSessionReader;
import com.naleul.naleul.domain.goalCreation.service.GoalInterviewTxService.PreparedTurn;
import com.naleul.naleul.domain.goalCreation.service.GoalInterviewTxService.StartResult;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * GoalInterviewService는 "트랜잭션 → AI → 트랜잭션" 조립만 하므로
 * 재시도 / 실패 시 정리 / REJECT 처리 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GoalInterviewServiceTest {

    @Mock private GoalInterviewTxService txService;
    @Mock private GoalCreationSessionReader sessionReader;
    @Mock private GoalAiClient aiClient;

    @InjectMocks
    private GoalInterviewService interviewService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 100L;

    private final InterviewTurnRequest aiRequest =
            new InterviewTurnRequest(GoalSlots.empty(), List.of(), 5, LocalDate.of(2026, 10, 2), null);
    private final PreparedTurn turn = new PreparedTurn(SESSION_ID, SlotKey.METRIC, aiRequest);
    private final InterviewTurnResult okResult = new InterviewTurnResult(
            null, null, NextAction.ASK, SlotKey.MOTIVATION, "왜요?", List.of(), null);
    private final GoalAiException timeout = new GoalAiException(GoalAiException.Reason.AI_TIMEOUT, "timeout");

    @Test
    void AI가_한번_실패하면_재시도해서_성공한다() {
        GoalAnswerRequest request = GoalAnswerRequest.builder().content("78에서 68").build();
        given(txService.prepareAnswer(USER_ID, SESSION_ID, request)).willReturn(turn);
        given(aiClient.interviewTurn(aiRequest)).willThrow(timeout).willReturn(okResult);

        interviewService.answer(USER_ID, SESSION_ID, request);

        then(aiClient).should(times(2)).interviewTurn(aiRequest);
        then(txService).should().applyTurn(SESSION_ID, SlotKey.METRIC, okResult);
    }

    @Test
    void AI가_두번_실패하면_502_AI_UNAVAILABLE이고_결과를_반영하지_않는다() {
        GoalAnswerRequest request = GoalAnswerRequest.builder().content("78에서 68").build();
        given(txService.prepareAnswer(USER_ID, SESSION_ID, request)).willReturn(turn);
        given(aiClient.interviewTurn(aiRequest)).willThrow(timeout);

        assertThatThrownBy(() -> interviewService.answer(USER_ID, SESSION_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_UNAVAILABLE);
        then(txService).should(never()).applyTurn(any(), any(), any());
    }

    @Test
    void 예상못한_RuntimeException도_재시도_흐름을_탄다() {
        GoalAnswerRequest request = GoalAnswerRequest.builder().content("답").build();
        given(txService.prepareAnswer(USER_ID, SESSION_ID, request)).willReturn(turn);
        given(aiClient.interviewTurn(aiRequest)).willThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> interviewService.answer(USER_ID, SESSION_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_UNAVAILABLE);
        then(aiClient).should(times(2)).interviewTurn(aiRequest);
    }

    @Test
    void REJECT면_422_GOAL_REJECTED() {
        GoalAnswerRequest request = GoalAnswerRequest.builder().content("[reject]").build();
        given(txService.prepareAnswer(USER_ID, SESSION_ID, request)).willReturn(turn);
        given(aiClient.interviewTurn(aiRequest)).willReturn(okResult);
        given(txService.applyTurn(SESSION_ID, SlotKey.METRIC, okResult)).willReturn(true);

        assertThatThrownBy(() -> interviewService.answer(USER_ID, SESSION_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOAL_REJECTED);
    }

    @Test
    void 시작_직후_AI가_실패하면_세션을_정리하고_502() {
        GoalSessionStartRequest request = GoalSessionStartRequest.builder().initialMessage("살 빼고 싶어").build();
        given(txService.start(USER_ID, request)).willReturn(new StartResult(SESSION_ID, turn));
        given(aiClient.interviewTurn(aiRequest)).willThrow(timeout);

        assertThatThrownBy(() -> interviewService.start(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_UNAVAILABLE);
        then(txService).should().abandonQuietly(SESSION_ID);
    }

    @Test
    void 첫_문장이_없으면_AI를_호출하지_않는다() {
        GoalSessionStartRequest request = new GoalSessionStartRequest();
        given(txService.start(USER_ID, request)).willReturn(new StartResult(SESSION_ID, null));

        interviewService.start(USER_ID, request);

        then(aiClient).should(never()).interviewTurn(any());
        then(sessionReader).should().getTurnResponse(SESSION_ID);
    }
}
