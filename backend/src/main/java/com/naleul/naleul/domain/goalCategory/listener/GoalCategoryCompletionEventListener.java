package com.naleul.naleul.domain.goalCategory.listener;

import com.naleul.naleul.domain.goalCategory.event.GoalCategoryCompletedEvent;
import com.naleul.naleul.domain.chart.service.GoalCompletionReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoalCategoryCompletionEventListener {

    private final GoalCompletionReportGenerationService goalCompletionReportGenerationService;

    /**
     * "목표 완료" 트랜잭션이 성공적으로 커밋된 "직후"에만 실행됨.
     * 여기서 예외가 나도 이미 커밋된 목표 완료 처리에는 절대 영향을 주지 않음.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // ✅ 수정: propagation = REQUIRES_NEW 명시 — "합류할 트랜잭션 없음"을 Spring에게 명확히 알려줌
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleGoalCategoryCompleted(GoalCategoryCompletedEvent event) {
        try {
            goalCompletionReportGenerationService.generateReport(event.goalCategoryId());
        } catch (Exception e) {
            // ✅ 여기서 예외를 다시 던지지 않고 로그만 남김.
            // 리포트 생성 실패는 "완료 처리"라는 핵심 기능에 영향을 주면 안 되기 때문.
            log.error("목표 완료 리포트 생성 실패: goalCategoryId={}", event.goalCategoryId(), e);
        }
    }
}