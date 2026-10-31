package com.naleul.naleul.domain.planner.listener;

import com.naleul.naleul.domain.lifePattern.event.LifePatternChangedEvent;
import com.naleul.naleul.domain.planner.config.PlannerExecutorConfig;
import com.naleul.naleul.domain.planner.service.DayPlacementService;
import com.naleul.naleul.domain.planner.support.PlannerClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * 생활 패턴이 바뀌면 (커밋 후) 그 기간의 엔진 블록 중 새 고정 시간과 겹치는 것을 다시 배치한다 (명세 3-1).
 *
 * - AFTER_COMMIT: 패턴 변경이 롤백되면 재배치하지 않는다.
 * - 요청 스레드에서 바로 돌리면 패턴 저장 응답이 느려지므로 plannerExecutor 로 넘긴다.
 * - 날짜마다 repairDay 가 각각 트랜잭션을 연다 → 한 날짜가 실패해도 다른 날짜는 반영된다.
 */
@Slf4j
@Component
public class LifePatternChangedListener {

    private final DayPlacementService dayPlacementService;
    private final PlannerClock clock;
    private final TaskExecutor executor;

    public LifePatternChangedListener(DayPlacementService dayPlacementService, PlannerClock clock,
                                      @Qualifier(PlannerExecutorConfig.PLANNER_EXECUTOR) TaskExecutor executor) {
        this.dayPlacementService = dayPlacementService;
        this.clock = clock;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(LifePatternChangedEvent event) {
        executor.execute(() -> repair(event));
    }

    void repair(LifePatternChangedEvent event) {
        LocalDate today = clock.today();
        LocalDate from = event.fromDate().isBefore(today) ? today : event.fromDate();
        for (LocalDate d = from; !d.isAfter(event.toDate()); d = d.plusDays(1)) {
            try {
                dayPlacementService.repairDay(event.userId(), d);
            } catch (RuntimeException e) {
                log.error("[Placement] 패턴 변경 재배치 실패 userId={} date={}", event.userId(), d, e);
            }
        }
    }
}
