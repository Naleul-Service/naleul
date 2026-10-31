package com.naleul.naleul.domain.planner.listener;

import com.naleul.naleul.domain.goalCreation.event.GoalCreatedEvent;
import com.naleul.naleul.domain.planner.config.PlannerExecutorConfig;
import com.naleul.naleul.domain.planner.service.DayPlacementService;
import com.naleul.naleul.domain.planner.service.PlacementMode;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * (BE-7) AI 목표 확정 직후 그 목표의 Task 를 TimeBlock 에 넣는다 (명세 6-7).
 *
 * - AFTER_COMMIT: 확정이 롤백되면 배치하지 않는다 (GoalCreatedEvent 주석의 약속)
 * - 오늘(지금 이후 빈 시간)은 바로 채우고, 22시가 지났으면 내일도 채운다.
 *   그 이후 날짜는 매일 22시 작업이 하루씩 배치한다.
 * - FILL 모드라 이미 배치된 블록은 건드리지 않는다.
 */
@Slf4j
@Component
public class GoalCreatedPlacementListener {

    static final int NIGHTLY_HOUR = 22;

    private final DayPlacementService dayPlacementService;
    private final PlannerClock clock;
    private final TaskExecutor executor;

    public GoalCreatedPlacementListener(DayPlacementService dayPlacementService, PlannerClock clock,
                                        @Qualifier(PlannerExecutorConfig.PLANNER_EXECUTOR) TaskExecutor executor) {
        this.dayPlacementService = dayPlacementService;
        this.clock = clock;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalCreated(GoalCreatedEvent event) {
        executor.execute(() -> place(event.userId()));
    }

    void place(Long userId) {
        LocalDate today = clock.today();
        try {
            dayPlacementService.placeDay(userId, today, PlacementMode.FILL, null, false);
            if (clock.now().getHour() >= NIGHTLY_HOUR) {
                dayPlacementService.placeDay(userId, today.plusDays(1), PlacementMode.FILL, null, false);
            }
        } catch (RuntimeException e) {
            log.error("[Placement] 목표 확정 후 배치 실패 userId={}", userId, e);
        }
    }
}
