package com.naleul.naleul.domain.goalCreation.service;

import com.naleul.naleul.domain.goalCreation.config.GoalPlanExecutorConfig;
import com.naleul.naleul.domain.goalCreation.dto.request.GoalDraftRequest;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftRequestedResponse;
import com.naleul.naleul.domain.goalCreation.dto.response.GoalDraftResponse;
import com.naleul.naleul.domain.goalCreation.enums.GoalDraftStatus;
import com.naleul.naleul.domain.goalCreation.service.GoalDraftTxService.GenerationJob;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;

/**
 * 초안 생성 요청 / 조회 진입점.
 *
 * ⚠️ @Transactional을 붙이지 않는다.
 *  requestDraft의 트랜잭션이 "커밋된 뒤에" 비동기 작업을 시작해야 한다.
 *  만약 같은 트랜잭션 안에서 작업을 넘기면, 작업 스레드가 아직 커밋 안 된 초안을 조회하지 못해
 *  "초안이 없음"으로 처리되는 경쟁 상태가 생긴다.
 *  → txService.requestDraft(커밋 완료) → executor.execute(...) 순서를 이 클래스가 보장한다.
 *
 * (@Async 대신 executor를 직접 쓰는 이유: 큐가 꽉 찼을 때 거절 예외를 여기서 바로 잡아 처리하기 위함)
 */
@Slf4j
@Service
public class GoalDraftService {

    private final GoalDraftTxService txService;
    private final GoalDraftWorker worker;
    private final TaskExecutor executor;

    // @RequiredArgsConstructor는 @Qualifier를 생성자에 복사하지 않아서 직접 작성
    public GoalDraftService(GoalDraftTxService txService,
                            GoalDraftWorker worker,
                            @Qualifier(GoalPlanExecutorConfig.GOAL_PLAN_EXECUTOR) TaskExecutor executor) {
        this.txService = txService;
        this.worker = worker;
        this.executor = executor;
    }

    public GoalDraftRequestedResponse requestDraft(Long userId, Long sessionId, GoalDraftRequest request) {
        GenerationJob job = txService.requestDraft(userId, sessionId, request);

        try {
            executor.execute(() -> worker.generate(job));
        } catch (RejectedExecutionException e) {
            // 동시 생성 2개 + 대기 10개가 꽉 참 → 초안을 실패 처리해 세션을 되돌리고 바로 알려준다
            log.warn("[GoalDraft] 생성 대기열이 가득 참 draftId={}", job.draftId());
            txService.failDraft(job.draftId(), "AI_BUSY");
            throw new CustomException(ErrorCode.AI_UNAVAILABLE);
        }

        return new GoalDraftRequestedResponse(job.draftId(), job.version(), GoalDraftStatus.GENERATING,
                GoalDraftTxService.POLL_AFTER_MS);
    }

    public GoalDraftResponse getDraft(Long userId, Long sessionId, Long draftId) {
        return txService.getDraft(userId, sessionId, draftId);
    }
}
