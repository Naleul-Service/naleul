package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.client.GoalAiClient;
import com.naleul.naleul.domain.goalCreation.client.GoalAiException;
import com.naleul.naleul.domain.goalCreation.client.InterviewTurnRequest;
import com.naleul.naleul.domain.goalCreation.client.InterviewTurnResult;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalAnswerRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSessionStartRequest;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalSlotsUpdateRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalSessionTurnResponse;
import com.naleul.naleul.domain.goalCreation.reader.GoalCreationSessionReader;
import com.naleul.naleul.domain.goalCreation.service.GoalInterviewTxService.PreparedTurn;
import com.naleul.naleul.domain.goalCreation.service.GoalInterviewTxService.StartResult;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 인터뷰 흐름 조립 (컨트롤러가 호출하는 진입점).
 *
 * ⚠️ 이 클래스에는 일부러 @Transactional을 붙이지 않는다.
 *    AI 호출(최대 20초) 동안 DB 커넥션을 잡지 않기 위해, DB 작업은 GoalInterviewTxService의
 *    짧은 트랜잭션 메서드로만 한다. (자세한 이유는 GoalInterviewTxService 주석 참고)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalInterviewService {

    private final GoalInterviewTxService txService;
    private final GoalCreationSessionReader sessionReader;
    private final GoalAiClient aiClient;

    public GoalSessionTurnResponse start(Long userId, GoalSessionStartRequest request) {
        StartResult started = txService.start(userId, request);
        Long sessionId = started.sessionId();

        if (started.turn() != null) {
            InterviewTurnResult result;
            try {
                result = callAiWithRetry(sessionId, started.turn().aiRequest());
            } catch (CustomException e) {
                // 클라이언트는 sessionId를 받지 못했으므로 이 세션을 이어갈 방법이 없음 → 정리
                txService.abandonQuietly(sessionId);
                throw e;
            }
            applyOrReject(started.turn(), result);
        }
        return sessionReader.getTurnResponse(sessionId);
    }

    public GoalSessionTurnResponse answer(Long userId, Long sessionId, GoalAnswerRequest request) {
        PreparedTurn turn = txService.prepareAnswer(userId, sessionId, request);
        // 마지막 "더 알려줄 정보" 답은 AI 없이 인터뷰가 끝난다
        if (turn.aiRequest() == null) return sessionReader.getTurnResponse(sessionId);
        // 여기서 실패하면 사용자 메시지는 저장된 채로 502 → 클라이언트 "다시 보내기"로 같은 요청 재전송
        InterviewTurnResult result = callAiWithRetry(sessionId, turn.aiRequest());
        applyOrReject(turn, result);
        return sessionReader.getTurnResponse(sessionId);
    }

    public GoalSessionTurnResponse finishInterview(Long userId, Long sessionId) {
        txService.finishInterview(userId, sessionId);
        return sessionReader.getTurnResponse(sessionId);
    }

    public GoalSessionTurnResponse updateSlots(Long userId, Long sessionId, GoalSlotsUpdateRequest request) {
        txService.updateSlots(userId, sessionId, request);
        return sessionReader.getTurnResponse(sessionId);
    }

    public void cancel(Long userId, Long sessionId) {
        txService.cancel(userId, sessionId);
    }

    // ───────────────────── 내부 ─────────────────────

    private void applyOrReject(PreparedTurn turn, InterviewTurnResult result) {
        boolean rejected = txService.applyTurn(turn.sessionId(), turn.answeredSlot(), result);
        if (rejected) {
            // applyTurn 트랜잭션은 이미 커밋됨(세션 ABANDONED 저장 완료) → 그 다음에 422를 던진다
            throw new CustomException(ErrorCode.GOAL_REJECTED);
        }
    }

    /** 인터뷰 1턴: 실패 시 1회 재시도 후 502 AI_UNAVAILABLE (명세 8-4) */
    private InterviewTurnResult callAiWithRetry(Long sessionId, InterviewTurnRequest request) {
        try {
            return callAi(sessionId, request, 1);
        } catch (GoalAiException first) {
            log.warn("[GoalInterview] AI 호출 실패, 1회 재시도 sessionId={} reason={}", sessionId, first.getReason());
            try {
                return callAi(sessionId, request, 2);
            } catch (GoalAiException second) {
                log.error("[GoalInterview] AI 재시도 실패 sessionId={} reason={}", sessionId, second.getReason(), second);
                throw new CustomException(ErrorCode.AI_UNAVAILABLE);
            }
        }
    }

    private InterviewTurnResult callAi(Long sessionId, InterviewTurnRequest request, int attempt) {
        try {
            InterviewTurnResult result = aiClient.interviewTurn(request);
            // 사용자 원문은 INFO 로그에 남기지 않는다 (명세 8-1)
            log.info("[GoalInterview] AI 응답 sessionId={} attempt={} nextAction={} targetSlot={} model={} latencyMs={}",
                    sessionId, attempt, result.nextAction(), result.targetSlot(),
                    result.meta() != null ? result.meta().model() : null,
                    result.meta() != null ? result.meta().latencyMs() : null);
            return result;
        } catch (GoalAiException e) {
            throw e;
        } catch (RuntimeException e) {
            // 구현체가 예상 못 한 예외를 던져도 같은 재시도 흐름을 타게 감싼다
            throw new GoalAiException(GoalAiException.Reason.AI_UNAVAILABLE, e.getMessage(), e);
        }
    }
}
